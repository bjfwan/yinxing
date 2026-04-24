package com.yinxing.launcher.feature.videocall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yinxing.launcher.R
import com.yinxing.launcher.data.contact.Contact
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class ContactManageAdapter(
    private var lowPerformanceMode: Boolean,
    private val onEditClick: (Contact) -> Unit,
    private val onDeleteClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactManageAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
                return oldItem == newItem
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val tvName: TextView = view.findViewById(R.id.tv_contact_name)
        val tvMeta: TextView = view.findViewById(R.id.tv_contact_meta)
        val btnEdit: MaterialButton = view.findViewById(R.id.btn_edit)
        val btnDelete: MaterialButton = view.findViewById(R.id.btn_delete)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_manage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = getItem(position)
        val context = holder.itemView.context
        holder.tvName.text = contact.displayName
        holder.tvMeta.text = buildMetaText(context, contact)
        holder.tvMeta.maxLines = if (lowPerformanceMode) 2 else 3
        holder.btnEdit.contentDescription = context.getString(R.string.action_edit) + contact.displayName
        holder.btnDelete.contentDescription = context.getString(R.string.video_contact_delete_description, contact.displayName)

        holder.btnEdit.setOnClickListener {
            onEditClick(contact)
        }
        holder.btnDelete.setOnClickListener {
            onDeleteClick(contact)
        }
    }

    fun setLowPerformanceMode(enabled: Boolean) {
        if (lowPerformanceMode == enabled) {
            return
        }
        lowPerformanceMode = enabled
        notifyItemRangeChanged(0, itemCount)
    }

    private fun buildMetaText(context: android.content.Context, contact: Contact): String {
        val summary = mutableListOf(
            context.getString(
                if (contact.preferredAction == Contact.PreferredAction.WECHAT_VIDEO) {
                    R.string.contact_manage_default_wechat
                } else {
                    R.string.contact_manage_default_phone
                }
            )
        )
        contact.phoneNumber?.takeIf { it.isNotBlank() }?.let {
            summary += context.getString(R.string.contact_manage_phone_value, it)
        }
        val wechatName = contact.wechatSearchName?.takeIf { it.isNotBlank() }

        if (!wechatName.isNullOrBlank()) {
            summary += context.getString(R.string.contact_manage_wechat_value, wechatName)
        }
        if (summary.size == 1) {
            summary += context.getString(R.string.contact_manage_empty_detail)
        }
        return summary.joinToString(" · ")
    }

}
