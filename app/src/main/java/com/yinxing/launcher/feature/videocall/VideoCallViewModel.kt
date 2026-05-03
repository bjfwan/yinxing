package com.yinxing.launcher.feature.videocall

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.data.contact.Contact
import com.yinxing.launcher.data.contact.ContactManager
import com.yinxing.launcher.data.contact.ContactPersistenceUseCase
import com.yinxing.launcher.data.contact.ContactStorage
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

/**
 * 微信视频联系人页面的状态容器。
 *
 * 与 Activity 的契约：
 * - 全部 IO/数据流都在这里完成；Activity 只 collect 三个 StateFlow + 监听 [events]。
 * - 选过新头像 + 落盘失败回滚的复杂语义封装在 [ContactPersistenceUseCase] 里。
 */
class VideoCallViewModel(
    private val contactManager: ContactManager,
    private val contactPersistence: ContactPersistenceUseCase
) : ViewModel() {
    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isManageMode = MutableStateFlow(false)
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val isManageMode: StateFlow<Boolean> = _isManageMode.asStateFlow()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    /** 经过搜索过滤后的可见联系人列表。仅在 manage 模式下生效搜索。 */
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

    /** 总联系人数（用于状态视图判断"搜索为空"还是"无任何联系人"）。 */
    val totalContactsCount: StateFlow<Int> =
        _allContacts.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private var loadJob: Job? = null

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
                val contacts = withContext(Dispatchers.IO) { contactManager.getContacts() }
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
        wechatId: String,
        avatarUri: String?
    ) {
        viewModelScope.launch {
            try {
                contactPersistence.savePersisted(
                    original = original,
                    selectedAvatarUri = avatarUri
                ) { resolvedAvatarUri, contactId ->
                    Contact(
                        id = contactId,
                        name = name,
                        wechatId = wechatId.trim().takeIf { it.isNotBlank() },
                        avatarUri = resolvedAvatarUri,
                        preferredAction = Contact.PreferredAction.WECHAT_VIDEO,
                        isPinned = original?.isPinned ?: false,
                        callCount = original?.callCount ?: 0,
                        lastCallTime = original?.lastCallTime ?: 0,
                        searchKeywords = original?.searchKeywords ?: emptyList()
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

    /** 一次性 UI 事件（toast、错误提示）。 */
    sealed class Event {
        data class LoadError(val throwable: Throwable) : Event()
        data class ContactAdded(val name: String) : Event()
        object ContactUpdated : Event()
        object ContactDeleted : Event()
        data class SaveError(val isAdd: Boolean, val throwable: Throwable) : Event()
        data class DeleteError(val throwable: Throwable) : Event()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val manager = ContactManager.getInstance(appContext)
            return VideoCallViewModel(
                contactManager = manager,
                contactPersistence = ContactPersistenceUseCase.forWechat(appContext, manager)
            ) as T
        }
    }
}
