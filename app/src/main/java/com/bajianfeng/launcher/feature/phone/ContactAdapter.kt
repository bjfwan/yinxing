package com.bajianfeng.launcher.feature.phone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.contact.PhoneContact

class ContactAdapter(
    private val onContactClick: (PhoneContact) -> Unit,
    private val onContactLongClick: (PhoneContact) -> Unit
) : ListAdapter<PhoneContact, ContactAdapter.ContactViewHolder>(DiffCallback) {
    private var cachedCardHeight = 0

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PhoneContact>() {
            override fun areItemsTheSame(oldItem: PhoneContact, newItem: PhoneContact): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: PhoneContact, newItem: PhoneContact): Boolean {
                return oldItem == newItem
            }
        }
    }

    class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_contact)
        val photo: ImageView = view.findViewById(R.id.iv_contact_photo)
        val name: TextView = view.findViewById(R.id.tv_contact_name)
        val number: TextView = view.findViewById(R.id.tv_contact_number)
        val callArea: View = view.findViewById(R.id.call_area)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        if (cachedCardHeight == 0) {
            cachedCardHeight = (parent.context.resources.displayMetrics.heightPixels * 0.75f).toInt()
        }
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = getItem(position)
        val context = holder.itemView.context

        val layoutParams = holder.card.layoutParams as ViewGroup.MarginLayoutParams
        if (layoutParams.height != cachedCardHeight) {
            layoutParams.height = cachedCardHeight
            holder.card.layoutParams = layoutParams
        }

        holder.name.text = contact.name
        holder.number.text = contact.phoneNumber
        holder.photo.contentDescription = context.getString(R.string.contact_photo_description, contact.name)
        holder.callArea.contentDescription = context.getString(R.string.contact_call_description, contact.name)
        holder.card.contentDescription = context.getString(R.string.contact_call_description, contact.name)

        if (contact.photo != null) {
            holder.photo.setImageBitmap(contact.photo)
            holder.photo.clearColorFilter()
        } else {
            holder.photo.setImageResource(android.R.drawable.ic_menu_call)
            holder.photo.setColorFilter("#2C3E50".toColorInt())
        }

        holder.photo.setOnClickListener { showPhotoDialog(holder.itemView.context, contact) }
        holder.photo.setOnLongClickListener {
            onContactLongClick(contact)
            true
        }
        holder.callArea.setOnClickListener { onContactClick(contact) }
        holder.callArea.setOnLongClickListener {
            onContactLongClick(contact)
            true
        }
        holder.card.setOnLongClickListener {
            onContactLongClick(contact)
            true
        }
    }

    private fun showPhotoDialog(context: android.content.Context, contact: PhoneContact) {
        val dialog = android.app.AlertDialog.Builder(context).create()
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(32, 32, 32, 32)
            if (contact.photo != null) {
                setImageBitmap(contact.photo)
            } else {
                setImageResource(android.R.drawable.ic_menu_call)
            }
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setView(imageView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
