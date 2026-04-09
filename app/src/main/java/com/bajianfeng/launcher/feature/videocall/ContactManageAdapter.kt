package com.bajianfeng.launcher.feature.videocall

import android.graphics.Color
import android.text.format.DateFormat
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
    private var lowPerformanceMode: Boolean,
    private val onPinClick: (Contact) -> Unit,
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

    init {
        setHasStableIds(true)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view as CardView
        val tvName: TextView = view.findViewById(R.id.tv_contact_name)
        val tvMeta: TextView = view.findViewById(R.id.tv_contact_meta)
        val btnPin: CardView = view.findViewById(R.id.btn_pin)
        val tvPinAction: TextView = view.findViewById(R.id.tv_pin_action)
        val btnDelete: CardView = view.findViewById(R.id.btn_delete)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_manage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = getItem(position)
        val context = holder.itemView.context
        holder.card.cardElevation = context.dpToPx(if (lowPerformanceMode) 2 else 4).toFloat()
        holder.tvName.text = contact.name
        holder.tvMeta.text = buildMetaText(context, contact)
        holder.tvPinAction.text = context.getString(if (contact.isPinned) R.string.action_unpin else R.string.action_pin)
        holder.btnPin.setCardBackgroundColor(
            Color.parseColor(if (contact.isPinned) "#F5A623" else "#2C3E50")
        )
        holder.btnPin.contentDescription = context.getString(
            if (contact.isPinned) R.string.video_contact_unpin_description else R.string.video_contact_pin_description,
            contact.name
        )
        holder.btnDelete.contentDescription = context.getString(R.string.video_contact_delete_description, contact.name)
        holder.btnPin.setOnClickListener {
            onPinClick(contact)
        }
        holder.btnDelete.setOnClickListener {
            onDeleteClick(contact)
        }
    }

    fun setLowPerformanceMode(enabled: Boolean) {
        if (lowPerformanceMode == enabled) {
            return
        }
        lowPerformanceMode = enabled
        notifyItemRangeChanged(0, itemCount)
    }

    private fun buildMetaText(context: android.content.Context, contact: Contact): String {
        return if (contact.lastCallTime > 0L) {
            context.getString(
                R.string.video_contact_last_call_summary,
                contact.callCount,
                DateFormat.format("MM-dd HH:mm", contact.lastCallTime)
            )
        } else {
            context.getString(R.string.video_contact_call_count, contact.callCount)
        }
    }

    private fun android.content.Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
