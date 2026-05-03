package com.yinxing.launcher.data.contact

import android.content.Context
import android.net.Uri
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 联系人新增/更新/删除的原子化业务封装。
 *
 * 之所以把这一层抽出来，是因为 [com.yinxing.launcher.feature.phone.PhoneContactActivity] 与
 * [com.yinxing.launcher.feature.videocall.VideoCallActivity] 在保存/删除联系人时需要执行几乎完全相同的
 * "新头像保存 + 旧头像清理 + 失败回滚" 流程，把它们留在 Activity 里既冗余又难测。
 *
 * 所有 IO 都在 [Dispatchers.IO] + [NonCancellable] 上下文里完成，调用方仅需 launch 协程即可。
 */
class ContactPersistenceUseCase(
    private val context: Context,
    private val manager: ContactRepositoryHandle
) {
    /** 抽象出 phone/video 两端各自的 [ContactManager]/[com.yinxing.launcher.feature.phone.PhoneContactManager] 调用差异。 */
    interface ContactRepositoryHandle {
        suspend fun add(contact: Contact)
        suspend fun update(contact: Contact)
        suspend fun remove(contactId: String)
    }

    /**
     * 写入或更新一条联系人，处理新旧头像的原子接管。
     *
     * 行为：
     * - 若 [selectedAvatarUri] 与原头像不同，则先把它保存到本地受管目录；
     * - 调用方提供的 [build] 接收解析后的最终 avatarUri、解析得到的 contactId，并返回 [Contact]；
     * - 写入 [manager] 失败时，会清理刚刚创建的头像副本，避免脏数据；
     * - 写入成功且原头像与新头像不同，则删除原头像。
     */
    suspend fun savePersisted(
        original: Contact?,
        selectedAvatarUri: String?,
        build: (avatarUri: String?, contactId: String) -> Contact
    ) = withContext(Dispatchers.IO + NonCancellable) {
        val previousAvatar = original?.avatarUri
        val contactId = original?.id ?: UUID.randomUUID().toString()
        var createdAvatar: String? = null
        val resolvedAvatar = when {
            selectedAvatarUri.isNullOrBlank() -> previousAvatar
            selectedAvatarUri == previousAvatar -> previousAvatar
            else -> ContactAvatarStore.saveFromUri(context, Uri.parse(selectedAvatarUri), contactId)
                ?.also { if (it != previousAvatar) createdAvatar = it }
                ?: previousAvatar
        }
        try {
            val contact = build(resolvedAvatar, contactId)
            if (original == null) {
                manager.add(contact)
            } else {
                manager.update(contact)
            }
            if (!previousAvatar.isNullOrBlank() && previousAvatar != resolvedAvatar) {
                ContactAvatarStore.deleteOwnedAvatar(context, previousAvatar)
            }
        } catch (throwable: Throwable) {
            if (!createdAvatar.isNullOrBlank()) {
                ContactAvatarStore.deleteOwnedAvatar(context, createdAvatar)
            }
            throw throwable
        }
    }

    /** 删除联系人 + 受管头像，单一 IO 上下文，整体不可取消。 */
    suspend fun deletePersisted(contact: Contact) = withContext(Dispatchers.IO + NonCancellable) {
        manager.remove(contact.id)
        ContactAvatarStore.deleteOwnedAvatar(context, contact.avatarUri)
    }

    companion object {
        /** 微信视频联系人专用：桥接 [ContactManager]。 */
        fun forWechat(context: Context, manager: ContactManager): ContactPersistenceUseCase {
            return ContactPersistenceUseCase(
                context = context,
                manager = object : ContactRepositoryHandle {
                    override suspend fun add(contact: Contact) = manager.addContact(contact)
                    override suspend fun update(contact: Contact) = manager.updateContact(contact)
                    override suspend fun remove(contactId: String) = manager.removeContact(contactId)
                }
            )
        }

        /** 通讯录电话联系人专用：桥接 [com.yinxing.launcher.feature.phone.PhoneContactManager]。 */
        fun forPhone(
            context: Context,
            adder: suspend (Contact) -> Unit,
            updater: suspend (Contact) -> Unit,
            remover: suspend (String) -> Unit
        ): ContactPersistenceUseCase {
            return ContactPersistenceUseCase(
                context = context,
                manager = object : ContactRepositoryHandle {
                    override suspend fun add(contact: Contact) = adder(contact)
                    override suspend fun update(contact: Contact) = updater(contact)
                    override suspend fun remove(contactId: String) = remover(contactId)
                }
            )
        }
    }
}
