package com.bajianfeng.launcher

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
import com.bajianfeng.launcher.model.Contact
import java.io.InputStream

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

        holder.name.text = contact.name

        if (contact.avatarUri != null) {
            try {
                val inputStream: InputStream? = holder.itemView.context.contentResolver
                    .openInputStream(Uri.parse(contact.avatarUri))
                val bitmap = BitmapFactory.decodeStream(inputStream)
                holder.photo.setImageBitmap(bitmap)
            } catch (e: Exception) {
                holder.photo.setImageResource(android.R.drawable.ic_menu_call)
                holder.photo.setColorFilter(Color.parseColor("#2C3E50"))
            }
        } else {
            holder.photo.setImageResource(android.R.drawable.ic_menu_call)
            holder.photo.setColorFilter(Color.parseColor("#2C3E50"))
        }

        holder.card.setOnClickListener {
            onContactClick(contact)
        }
    }

    override fun getItemCount() = contacts.size
}
