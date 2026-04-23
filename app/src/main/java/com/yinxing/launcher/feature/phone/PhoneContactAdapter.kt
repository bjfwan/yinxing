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
import com.yinxing.launcher.data.contact.Contact

class PhoneContactAdapter(
    private val onCallClick: (Contact) -> Unit,
    private val onEditClick: (Contact) -> Unit
) : ListAdapter<Contact, PhoneContactAdapter.ViewHolder>(DIFF) {

    private var isManageMode = false

    fun setManageMode(manage: Boolean) {
        if (isManageMode == manage) return
        isManageMode = manage
        notifyItemRangeChanged(0, itemCount)
    }

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val avatar: ImageView = itemView.findViewById(R.id.iv_avatar)
        val name: TextView = itemView.findViewById(R.id.tv_contact_name)
        val manageHint: TextView = itemView.findViewById(R.id.tv_manage_hint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phone_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = getItem(position)
        val context = holder.itemView.context
        holder.name.text = contact.name
        holder.manageHint.isVisible = true
        holder.manageHint.text = context.getString(
            if (isManageMode) R.string.contact_manage_hint_edit else R.string.contact_card_action_phone_compact
        )

        val uri = contact.avatarUri?.takeIf { it.isNotBlank() }

        if (uri != null) {
            holder.avatar.setPadding(0, 0, 0, 0)
            holder.avatar.setImageURI(null)
            holder.avatar.setImageURI(Uri.parse(uri))
        } else {
            val padding = (24 * holder.itemView.context.resources.displayMetrics.density).toInt()
            holder.avatar.setPadding(padding, padding, padding, padding)
            holder.avatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        }

        holder.itemView.setOnClickListener {
            if (isManageMode) onEditClick(contact) else onCallClick(contact)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(a: Contact, b: Contact) = a.id == b.id
            override fun areContentsTheSame(a: Contact, b: Contact) = a == b
        }
    }
}
