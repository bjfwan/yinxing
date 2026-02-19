package com.bajianfeng.launcher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(
    private val contacts: List<ContactInfo>,
    private val onContactClick: (ContactInfo) -> Unit,
    private val onContactLongClick: (ContactInfo) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_contact)
        val photo: ImageView = view.findViewById(R.id.iv_contact_photo)
        val name: TextView = view.findViewById(R.id.tv_contact_name)
        val number: TextView = view.findViewById(R.id.tv_contact_number)
        val callArea: View = view.findViewById(R.id.call_area)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        
        val displayMetrics = holder.itemView.context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val cardHeight = (screenHeight * 0.75).toInt()
        
        val layoutParams = holder.card.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.height = cardHeight
        holder.card.layoutParams = layoutParams
        
        holder.name.text = contact.name
        holder.number.text = contact.phoneNumber
        
        if (contact.photo != null) {
            holder.photo.setImageBitmap(contact.photo)
        } else {
            holder.photo.setImageResource(android.R.drawable.ic_menu_call)
            holder.photo.setColorFilter(Color.parseColor("#2C3E50"))
        }

        holder.photo.setOnClickListener {
            showPhotoDialog(holder.itemView.context, contact)
        }
        
        holder.photo.setOnLongClickListener {
            onContactLongClick(contact)
            true
        }

        holder.callArea.setOnClickListener {
            onContactClick(contact)
        }
        
        holder.callArea.setOnLongClickListener {
            onContactLongClick(contact)
            true
        }

        holder.card.setOnLongClickListener {
            onContactLongClick(contact)
            true
        }
    }

    private fun showPhotoDialog(context: android.content.Context, contact: ContactInfo) {
        val dialog = android.app.AlertDialog.Builder(context)
            .create()

        val imageView = ImageView(context)
        imageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setPadding(32, 32, 32, 32)
        
        if (contact.photo != null) {
            imageView.setImageBitmap(contact.photo)
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_call)
        }

        imageView.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setView(imageView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun getItemCount() = contacts.size
}
