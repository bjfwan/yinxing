package com.bajianfeng.launcher.feature.videocall

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.contact.Contact

class ContactManageAdapter(
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

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_contact_name)
        val callCount: TextView = view.findViewById(R.id.tv_call_count)
        val deleteButton: CardView = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_manage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = getItem(position)
        val context = holder.itemView.context
        holder.name.text = contact.name
        holder.callCount.text = context.getString(R.string.video_contact_call_count, contact.callCount)
        holder.deleteButton.contentDescription =
            context.getString(R.string.video_contact_delete_description, contact.name)
        holder.deleteButton.setOnClickListener {
            onDeleteClick(contact)
        }
    }
}
