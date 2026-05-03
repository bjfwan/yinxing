package com.yinxing.launcher.feature.phone

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.data.contact.Contact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PhoneContactAdapter(
    private val scope: LifecycleCoroutineScope,
    private val onCallClick: (Contact) -> Unit,
    private val onEditClick: (Contact) -> Unit
) : ListAdapter<Contact, PhoneContactAdapter.ViewHolder>(DIFF) {

    private var isManageMode = false
    private var fullCardTapEnabled = false
    private var animationsEnabled = true
    private val animatedIds = HashSet<Long>()

    init {
        setHasStableIds(true)
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
    }

    fun setManageMode(manage: Boolean) {
        if (isManageMode == manage) return
        isManageMode = manage
        notifyItemRangeChanged(0, itemCount)
    }

    fun setFullCardTapEnabled(enabled: Boolean) {
        if (fullCardTapEnabled == enabled) return
        fullCardTapEnabled = enabled
        notifyItemRangeChanged(0, itemCount)
    }

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val avatar: ImageView = itemView.findViewById(R.id.iv_avatar)
        val name: TextView = itemView.findViewById(R.id.tv_contact_name)
        val phone: TextView = itemView.findViewById(R.id.tv_contact_phone)
        val autoAnswerBadge: TextView = itemView.findViewById(R.id.tv_auto_answer_badge)
        val btnCall: android.view.View = itemView.findViewById(R.id.btn_call)
        val manageHint: TextView = itemView.findViewById(R.id.tv_manage_hint)
        var avatarJob: Job? = null
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phone_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = getItem(position)
        holder.name.text = contact.name
        bindPhoneNumber(holder, contact)
        bindAutoAnswerBadge(holder, contact)
        bindAvatar(holder, contact)

        if (isManageMode) {
            holder.btnCall.isVisible = false
            holder.manageHint.isVisible = true
            holder.itemView.setOnClickListener { onEditClick(contact) }
        } else {
            holder.btnCall.isVisible = true
            holder.manageHint.isVisible = false
            holder.btnCall.setOnClickListener { onCallClick(contact) }
            if (fullCardTapEnabled) {
                holder.itemView.setOnClickListener { onCallClick(contact) }
            } else {
                holder.itemView.setOnClickListener(null)
                holder.itemView.isClickable = false
            }
        }

        animateInIfFirstShow(holder, position)
    }

    private fun animateInIfFirstShow(holder: ViewHolder, position: Int) {
        val view = holder.itemView
        val id = holder.itemId
        if (!animationsEnabled || position >= ENTRY_ANIMATION_LIMIT || !animatedIds.add(id)) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = 24f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(position * 35L)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun bindPhoneNumber(holder: ViewHolder, contact: Contact) {
        val number = contact.phoneNumber?.takeIf { it.isNotBlank() }
        if (number != null) {
            holder.phone.text = formatPhoneNumber(number)
            holder.phone.isVisible = true
        } else {
            holder.phone.isVisible = false
        }
    }

    private fun bindAutoAnswerBadge(holder: ViewHolder, contact: Contact) {
        holder.autoAnswerBadge.isVisible = !isManageMode && contact.autoAnswer
    }

    private fun formatPhoneNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 11 -> "${digits.substring(0, 3)} ${digits.substring(3, 7)} ${digits.substring(7)}"
            digits.length in 7..10 && raw.contains('-') -> raw
            digits.length == 7 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
            digits.length == 8 -> "${digits.substring(0, 4)}-${digits.substring(4)}"
            else -> raw
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.itemView.animate().cancel()
        holder.itemView.alpha = 1f
        holder.itemView.translationY = 0f
        holder.avatarJob?.cancel()
        super.onViewRecycled(holder)
    }

    private fun bindAvatar(holder: ViewHolder, contact: Contact) {
        val context = holder.itemView.context
        val avatarUri = contact.avatarUri?.takeIf { it.isNotBlank() }
        holder.avatarJob?.cancel()
        holder.avatar.setPadding(0, 0, 0, 0)
        holder.avatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        if (avatarUri == null) {
            return
        }
        holder.avatarJob = scope.launch {
            val bitmap = try {
                MediaThumbnailLoader.loadBitmap(context, Uri.parse(avatarUri), 240, 240)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) {
                return@launch
            }
            val currentItem = currentList.getOrNull(currentPosition)
            if (currentItem?.id == contact.id && bitmap != null) {
                holder.avatar.setImageBitmap(bitmap)
            }
        }
    }

    companion object {
        private const val ENTRY_ANIMATION_LIMIT = 8

        private val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(a: Contact, b: Contact) = a.id == b.id
            override fun areContentsTheSame(a: Contact, b: Contact) = a == b
        }
    }
}
