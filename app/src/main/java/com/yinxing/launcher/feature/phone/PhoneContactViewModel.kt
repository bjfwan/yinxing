package com.yinxing.launcher.feature.phone

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactPersistenceUseCase
import com.yinxing.launcher.data.contact.ContactStorage
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 系统通讯录里筛出的导入候选项。 */
data class PhoneImportCandidate(
    val name: String,
    val phone: String,
    val alreadyImported: Boolean
)

/**
 * 电话联系人页面的状态容器。
 *
 * 与 Activity 的契约：
 * - 全部 IO（保存/删除/刷新/系统通讯录读取/批量导入）都在这里完成；
 * - Activity 通过 [searchQuery]、[isManageMode]、[visibleContacts]、[totalContactsCount] 渲染状态；
 * - Activity 通过 [events] 接受一次性 UI 反馈（toast、错误提示、对话框触发）；
 * - 拨号、权限请求等仍由 Activity 自身处理（与 [Context] 强绑定）。
 */
class PhoneContactViewModel(
    private val contentResolver: ContentResolver,
    private val manager: PhoneContactManager,
    private val contactPersistence: ContactPersistenceUseCase
) : ViewModel() {

    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isManageMode = MutableStateFlow(false)
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val isManageMode: StateFlow<Boolean> = _isManageMode.asStateFlow()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    val visibleContacts: StateFlow<List<Contact>> = combine(
        _allContacts,
        _searchQuery,
        _isManageMode
    ) { contacts, query, manageMode ->
        if (manageMode && query.isNotBlank()) {
            ContactStorage.filter(contacts, query)
        } else {
            contacts
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalContactsCount: StateFlow<Int> =
        _allContacts.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private var loadJob: Job? = null
    private var importJob: Job? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setManageMode(manage: Boolean) {
        if (_isManageMode.value == manage) return
        _isManageMode.value = manage
        if (!manage) {
            _searchQuery.value = ""
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val contacts = withContext(Dispatchers.IO) { manager.getContacts() }
                _allContacts.value = contacts
                contacts.forEach { contact ->
                    contact.avatarUri
                        ?.takeIf { it.isNotBlank() }
                        ?.let { runCatching { MediaThumbnailLoader.evictFailedUri(Uri.parse(it)) } }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _events.tryEmit(Event.LoadError(throwable))
            }
        }
    }

    fun saveContact(
        original: Contact?,
        name: String,
        phone: String,
        autoAnswer: Boolean
    ) {
        viewModelScope.launch {
            try {
                contactPersistence.savePersisted(
                    original = original,
                    selectedAvatarUri = pendingAvatarUri
                ) { resolvedAvatarUri, contactId ->
                    Contact(
                        id = contactId,
                        name = name,
                        phoneNumber = phone,
                        avatarUri = resolvedAvatarUri,
                        preferredAction = Contact.PreferredAction.PHONE,
                        isPinned = original?.isPinned ?: false,
                        callCount = original?.callCount ?: 0,
                        lastCallTime = original?.lastCallTime ?: 0,
                        autoAnswer = autoAnswer
                    )
                }
                refresh()
                _events.tryEmit(
                    if (original == null) Event.ContactAdded(name) else Event.ContactUpdated
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _events.tryEmit(Event.SaveError(isAdd = original == null, throwable = throwable))
            } finally {
                pendingAvatarUri = null
            }
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            try {
                contactPersistence.deletePersisted(contact)
                refresh()
                _events.tryEmit(Event.ContactDeleted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _events.tryEmit(Event.DeleteError(throwable))
            }
        }
    }

    fun incrementCallCountAsync(contactId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { manager.incrementCallCount(contactId) }
        }
    }

    /** 异步读取系统通讯录候选项。完成后通过 [Event.ImportCandidatesReady] 推送 UI。 */
    fun loadImportCandidates() {
        importJob?.cancel()
        _events.tryEmit(Event.ImportLoading)
        importJob = viewModelScope.launch {
            try {
                val candidates = withContext(Dispatchers.IO) { readImportCandidates() }
                _events.tryEmit(Event.ImportCandidatesReady(candidates))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _events.tryEmit(Event.LoadError(throwable))
            }
        }
    }

    /** 批量导入系统通讯录候选项。完成后通过 [Event.ImportCompleted] / [Event.SaveError] 推送。 */
    fun importContacts(selected: List<PhoneImportCandidate>) {
        if (selected.isEmpty()) return
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val contacts = selected.map { candidate ->
                        Contact(
                            id = UUID.randomUUID().toString(),
                            name = candidate.name,
                            phoneNumber = candidate.phone,
                            preferredAction = Contact.PreferredAction.PHONE
                        )
                    }
                    manager.addContacts(contacts)
                    contacts.size
                }
                refresh()
                _events.tryEmit(Event.ImportCompleted(count))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _events.tryEmit(Event.SaveError(isAdd = true, throwable = throwable))
            }
        }
    }

    private fun readImportCandidates(): List<PhoneImportCandidate> {
        val entries = mutableListOf<Pair<String, String>>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = hashSetOf<String>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.trim() ?: continue
                val phone = cursor.getString(phoneIdx)?.trim() ?: continue
                if (name.isBlank() || phone.isBlank()) continue
                val digits = phone.filter(Char::isDigit)
                if (digits.isEmpty()) continue
                if (seen.add("${name}_$digits")) {
                    entries += name to phone
                }
            }
        }
        if (entries.isEmpty()) {
            return emptyList()
        }
        val existingPhones = manager.getContacts()
            .mapNotNull { it.phoneNumber?.filter(Char::isDigit) }
            .toHashSet()
        return entries.map { (name, phone) ->
            PhoneImportCandidate(
                name = name,
                phone = phone,
                alreadyImported = phone.filter(Char::isDigit) in existingPhones
            )
        }
    }

    /**
     * 当前对话框中用户挑选的新头像 Uri 字符串。
     * Activity 在 image picker 回调里设置；ViewModel 在 [saveContact] 用完后清空。
     */
    var pendingAvatarUri: String? = null

    /** 一次性 UI 事件。 */
    sealed class Event {
        data class LoadError(val throwable: Throwable) : Event()
        data class ContactAdded(val name: String) : Event()
        object ContactUpdated : Event()
        object ContactDeleted : Event()
        data class SaveError(val isAdd: Boolean, val throwable: Throwable) : Event()
        data class DeleteError(val throwable: Throwable) : Event()
        object ImportLoading : Event()
        data class ImportCandidatesReady(val candidates: List<PhoneImportCandidate>) : Event()
        data class ImportCompleted(val count: Int) : Event()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val manager = PhoneContactManager.getInstance(appContext)
            return PhoneContactViewModel(
                contentResolver = appContext.contentResolver,
                manager = manager,
                contactPersistence = ContactPersistenceUseCase.forPhone(
                    context = appContext,
                    adder = manager::addContact,
                    updater = manager::updateContact,
                    remover = manager::removeContact
                )
            ) as T
        }
    }
}
