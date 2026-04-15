package com.bajianfeng.launcher.feature.videocall

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
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
        val action: TextView = view.findViewById(R.id.tv_video_contact_action)
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
        holder.name.text = contact.displayName

        holder.action.text = context.getString(
            if (contact.preferredAction == Contact.PreferredAction.WECHAT_VIDEO) {
                R.string.contact_card_action_wechat
            } else {
                R.string.contact_card_action_phone
            }
        )
        holder.photo.contentDescription = context.getString(R.string.contact_photo_description, contact.displayName)
        holder.card.contentDescription = context.getString(R.string.video_contact_action_description, contact.displayName)


        val photoSize = context.dpToPx(if (lowPerformanceMode) 88 else 112)
        holder.photo.layoutParams = holder.photo.layoutParams.apply {
            width = photoSize
            height = photoSize
        }

        holder.photo.setDefaultAvatar(context, contact.preferredAction)
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
        holder.photo.setOnClickListener { onContactClick(contact) }
        holder.name.setOnClickListener { onContactClick(contact) }
        holder.action.setOnClickListener { onContactClick(contact) }
    }

    private fun ImageView.setDefaultAvatar(context: android.content.Context, action: Contact.PreferredAction) {
        setImageResource(
            if (action == Contact.PreferredAction.WECHAT_VIDEO) {
                android.R.drawable.ic_menu_camera
            } else {
                android.R.drawable.ic_menu_call
            }
        )
        setColorFilter(ContextCompat.getColor(context, R.color.launcher_primary_dark))
    }

    private fun android.content.Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
