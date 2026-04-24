package com.yinxing.launcher.feature.phone

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.data.contact.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PhoneContactAdapter(
    private val scope: CoroutineScope,
    private val onCallClick: (Contact) -> Unit,
    private val onEditClick: (Contact) -> Unit
) : ListAdapter<Contact, PhoneContactAdapter.ViewHolder>(DIFF) {

    private var isManageMode = false

    init {
        setHasStableIds(true)
    }

    fun setManageMode(manage: Boolean) {
        if (isManageMode == manage) return
        isManageMode = manage
        notifyItemRangeChanged(0, itemCount)
    }

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val avatar: ImageView = itemView.findViewById(R.id.iv_avatar)
        val name: TextView = itemView.findViewById(R.id.tv_contact_name)
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
        val context = holder.itemView.context
        holder.name.text = contact.name
        if (isManageMode) {
            holder.btnCall.isVisible = false
            holder.manageHint.isVisible = true
            holder.manageHint.text = context.getString(R.string.contact_manage_hint_edit)
        } else {
            holder.btnCall.isVisible = true
            holder.manageHint.isVisible = false
        }
        bindAvatar(holder, contact)
        val clickListener = android.view.View.OnClickListener {
            if (isManageMode) onEditClick(contact) else onCallClick(contact)
        }
        holder.itemView.setOnClickListener(clickListener)
        holder.btnCall.setOnClickListener(clickListener)
    }

    override fun onViewRecycled(holder: ViewHolder) {
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
            val bitmap = MediaThumbnailLoader.loadBitmap(context, Uri.parse(avatarUri), 240, 240)
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
        private val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(a: Contact, b: Contact) = a.id == b.id
            override fun areContentsTheSame(a: Contact, b: Contact) = a == b
        }
    }
}
