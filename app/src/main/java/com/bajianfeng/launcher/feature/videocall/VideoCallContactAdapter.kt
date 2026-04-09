package com.bajianfeng.launcher.feature.videocall

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.contact.Contact

class VideoCallContactAdapter(
    private val onContactClick: (Contact) -> Unit
) : ListAdapter<Contact, VideoCallContactAdapter.ViewHolder>(DiffCallback) {

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
        val card: CardView = view.findViewById(R.id.card_video_contact)
        val photo: ImageView = view.findViewById(R.id.iv_video_contact_photo)
        val name: TextView = view.findViewById(R.id.tv_video_contact_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = getItem(position)
        val context = holder.itemView.context
        holder.name.text = contact.name
        holder.photo.contentDescription = context.getString(R.string.contact_photo_description, contact.name)
        holder.card.contentDescription = context.getString(R.string.video_contact_action_description, contact.name)

        if (contact.avatarUri != null) {
            runCatching {
                val uri = contact.avatarUri.toUri()
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                options.inSampleSize = calculateInSampleSize(options, 100, 100)
                options.inJustDecodeBounds = false
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }.getOrNull()?.let { bitmap ->
                holder.photo.setImageBitmap(bitmap)
                holder.photo.clearColorFilter()
            } ?: setDefaultAvatar(holder)
        } else {
            setDefaultAvatar(holder)
        }

        holder.card.setOnClickListener { onContactClick(contact) }
    }

    private fun setDefaultAvatar(holder: ViewHolder) {
        holder.photo.setImageResource(android.R.drawable.ic_menu_call)
        holder.photo.setColorFilter("#2C3E50".toColorInt())
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
