package com.bajianfeng.launcher.feature.videocall

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.data.contact.Contact

class VideoCallContactAdapter(
    private val contacts: List<Contact>,
    private val onContactClick: (Contact) -> Unit
) : RecyclerView.Adapter<VideoCallContactAdapter.ViewHolder>() {

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
        val contact = contacts[position]
        val context = holder.itemView.context
        holder.name.text = contact.name
        holder.photo.contentDescription = context.getString(R.string.contact_photo_description, contact.name)
        holder.card.contentDescription = context.getString(R.string.video_contact_action_description, contact.name)

        if (contact.avatarUri != null) {
            try {
                val uri = Uri.parse(contact.avatarUri)

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                options.inSampleSize = calculateInSampleSize(options, 100, 100)
                options.inJustDecodeBounds = false

                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                if (bitmap != null) {
                    holder.photo.setImageBitmap(bitmap)
                    holder.photo.clearColorFilter()
                } else {
                    setDefaultAvatar(holder)
                }
            } catch (_: Exception) {
                setDefaultAvatar(holder)
            }
        } else {
            setDefaultAvatar(holder)
        }

        holder.card.setOnClickListener { onContactClick(contact) }
    }

    override fun getItemCount() = contacts.size

    private fun setDefaultAvatar(holder: ViewHolder) {
        holder.photo.setImageResource(android.R.drawable.ic_menu_call)
        holder.photo.setColorFilter(Color.parseColor("#2C3E50"))
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
