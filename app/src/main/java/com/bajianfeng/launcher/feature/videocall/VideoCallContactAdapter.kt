package com.bajianfeng.launcher.feature.videocall

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
import com.bajianfeng.launcher.data.contact.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class VideoCallContactAdapter(
    private val scope: CoroutineScope,
    private var lowPerformanceMode: Boolean,
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

    init {
        setHasStableIds(true)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_video_contact)
        val photo: ImageView = view.findViewById(R.id.iv_video_contact_photo)
        val name: TextView = view.findViewById(R.id.tv_video_contact_name)
        var photoJob: Job? = null
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        bind(holder, getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
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

    private fun bind(holder: ViewHolder, contact: Contact) {
        val context = holder.itemView.context
        holder.card.cardElevation = context.dpToPx(if (lowPerformanceMode) 2 else 4).toFloat()
        holder.name.text = contact.name
        holder.photo.contentDescription = context.getString(R.string.contact_photo_description, contact.name)
        holder.card.contentDescription = context.getString(R.string.video_contact_action_description, contact.name)

        val photoSize = context.dpToPx(if (lowPerformanceMode) 80 else 100)
        holder.photo.layoutParams = holder.photo.layoutParams.apply {
            width = photoSize
            height = photoSize
        }

        holder.photo.setDefaultAvatar()
        holder.photoJob?.cancel()
        if (!contact.avatarUri.isNullOrBlank()) {
            holder.photoJob = scope.launch {
                val bitmap = MediaThumbnailLoader.loadBitmap(
                    context,
                    Uri.parse(contact.avatarUri),
                    photoSize,
                    photoSize
                )
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) {
                    return@launch
                }
                val currentItem = currentList.getOrNull(currentPosition)
                if (currentItem?.id == contact.id && bitmap != null) {
                    holder.photo.setImageBitmap(bitmap)
                    holder.photo.clearColorFilter()
                }
            }
        }

        holder.card.setOnClickListener { onContactClick(contact) }
    }

    private fun ImageView.setDefaultAvatar() {
        setImageResource(android.R.drawable.ic_menu_call)
        setColorFilter(Color.parseColor("#2C3E50"))
    }

    private fun android.content.Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
