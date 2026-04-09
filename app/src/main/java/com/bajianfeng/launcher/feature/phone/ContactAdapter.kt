package com.bajianfeng.launcher.feature.phone

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.media.MediaThumbnailLoader
import com.bajianfeng.launcher.data.contact.PhoneContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ContactAdapter(
    private val scope: CoroutineScope,
    private var lowPerformanceMode: Boolean,
    private val onContactClick: (PhoneContact) -> Unit,
    private val onContactLongClick: (PhoneContact) -> Unit
) : ListAdapter<PhoneContact, ContactAdapter.ContactViewHolder>(DiffCallback) {

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

    private var cachedCardHeight = 0

    init {
        setHasStableIds(true)
    }

    class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_contact)
        val photo: ImageView = view.findViewById(R.id.iv_contact_photo)
        val name: TextView = view.findViewById(R.id.tv_contact_name)
        val number: TextView = view.findViewById(R.id.tv_contact_number)
        val callArea: View = view.findViewById(R.id.call_area)
        var photoJob: Job? = null
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)

        if (cachedCardHeight == 0) {
            cachedCardHeight = (parent.context.resources.displayMetrics.heightPixels * 0.75f).toInt()
        }

        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        bind(holder, getItem(position))
    }

    override fun onViewRecycled(holder: ContactViewHolder) {
        holder.photoJob?.cancel()
        super.onViewRecycled(holder)
    }

    fun setLowPerformanceMode(enabled: Boolean) {
        if (lowPerformanceMode == enabled) {
            return
        }
        lowPerformanceMode = enabled
        notifyItemRangeChanged(0, itemCount)
    }

    private fun bind(holder: ContactViewHolder, contact: PhoneContact) {
        val context = holder.itemView.context
        val layoutParams = holder.card.layoutParams as ViewGroup.MarginLayoutParams
        if (layoutParams.height != cachedCardHeight) {
            layoutParams.height = cachedCardHeight
            holder.card.layoutParams = layoutParams
        }

        holder.card.cardElevation = context.dpToPx(if (lowPerformanceMode) 2 else 8).toFloat()
        holder.name.text = contact.name
        holder.number.text = contact.phoneNumber
        holder.photo.contentDescription = context.getString(R.string.contact_photo_description, contact.name)
        holder.callArea.contentDescription = context.getString(R.string.contact_call_description, contact.name)
        holder.card.contentDescription = context.getString(R.string.contact_call_description, contact.name)

        holder.photo.setDefaultAvatar()
        holder.photoJob?.cancel()
        if (contact.photoUri != null) {
            val targetSize = context.dpToPx(if (lowPerformanceMode) 160 else 320)
            holder.photoJob = scope.launch {
                val bitmap = MediaThumbnailLoader.loadBitmap(context, Uri.parse(contact.photoUri), targetSize, targetSize)
                if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
                    return@launch
                }
                val currentItem = currentList.getOrNull(holder.bindingAdapterPosition)
                if (currentItem?.id == contact.id && bitmap != null) {
                    holder.photo.setImageBitmap(bitmap)
                    holder.photo.clearColorFilter()
                }
            }
        }

        holder.photo.setOnClickListener { showPhotoDialog(context, contact) }
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

    private fun showPhotoDialog(context: Context, contact: PhoneContact) {
        val dialog = android.app.AlertDialog.Builder(context).create()
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(context.dpToPx(24), context.dpToPx(24), context.dpToPx(24), context.dpToPx(24))
            setDefaultAvatar()
            setOnClickListener { dialog.dismiss() }
        }

        if (contact.photoUri != null) {
            scope.launch {
                val previewSize = context.dpToPx(if (lowPerformanceMode) 320 else 720)
                val bitmap = MediaThumbnailLoader.loadBitmap(context, Uri.parse(contact.photoUri), previewSize, previewSize)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.clearColorFilter()
                }
            }
        }

        dialog.setView(imageView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun ImageView.setDefaultAvatar() {
        setImageResource(android.R.drawable.ic_menu_call)
        setColorFilter(Color.parseColor("#2C3E50"))
    }

    private fun Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
