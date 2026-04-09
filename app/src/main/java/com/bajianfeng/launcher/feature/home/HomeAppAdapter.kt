package com.bajianfeng.launcher.feature.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R
import com.bajianfeng.launcher.common.media.MediaThumbnailLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Collections

class HomeAppAdapter(
    private val scope: CoroutineScope,
    private var lowPerformanceMode: Boolean,
    private val onItemClick: (HomeAppItem) -> Unit,
    private val onItemLongClick: (HomeAppItem) -> Boolean,
    private val onOrderChanged: (List<HomeAppItem>) -> Unit
) : ListAdapter<HomeAppItem, HomeAppAdapter.ViewHolder>(DiffCallback), ItemTouchHelperAdapter {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<HomeAppItem>() {
            override fun areItemsTheSame(oldItem: HomeAppItem, newItem: HomeAppItem): Boolean {
                return oldItem.stableId == newItem.stableId
            }

            override fun areContentsTheSame(oldItem: HomeAppItem, newItem: HomeAppItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    private var touchHelper: ItemTouchHelper? = null

    init {
        setHasStableIds(true)
    }

    fun setTouchHelper(helper: ItemTouchHelper) {
        touchHelper = helper
    }

    fun setLowPerformanceMode(enabled: Boolean) {
        if (lowPerformanceMode == enabled) {
            return
        }
        lowPerformanceMode = enabled
        notifyItemRangeChanged(0, itemCount)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_item)
        val icon: ImageView = view.findViewById(R.id.icon)
        val name: TextView = view.findViewById(R.id.name)
        var iconJob: Job? = null
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        bind(holder, getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.iconJob?.cancel()
        super.onViewRecycled(holder)
    }

    override fun canMoveItem(position: Int): Boolean {
        return getItem(position).type == HomeAppItem.Type.APP
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!canMoveItem(fromPosition) || !canMoveItem(toPosition)) {
            return false
        }

        val reordered = currentList.toMutableList()
        if (fromPosition < toPosition) {
            for (index in fromPosition until toPosition) {
                Collections.swap(reordered, index, index + 1)
            }
        } else {
            for (index in fromPosition downTo toPosition + 1) {
                Collections.swap(reordered, index, index - 1)
            }
        }

        submitList(reordered)
        onOrderChanged(reordered)
        return true
    }

    private fun bind(holder: ViewHolder, item: HomeAppItem) {
        val context = holder.itemView.context
        holder.card.cardElevation = context.dpToPx(if (lowPerformanceMode) 2 else 6).toFloat()
        holder.name.text = item.appName
        holder.card.contentDescription = item.appName

        val iconSize = context.dpToPx(if (lowPerformanceMode) 80 else 96)
        holder.icon.layoutParams = holder.icon.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        holder.icon.setPadding(
            context.dpToPx(if (lowPerformanceMode) 12 else 16),
            context.dpToPx(if (lowPerformanceMode) 12 else 16),
            context.dpToPx(if (lowPerformanceMode) 12 else 16),
            context.dpToPx(if (lowPerformanceMode) 12 else 16)
        )

        holder.iconJob?.cancel()
        if (item.type == HomeAppItem.Type.APP) {
            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
            holder.iconJob = scope.launch {
                val bitmap = MediaThumbnailLoader.loadAppIcon(context, item.packageName, iconSize)
                if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
                    return@launch
                }
                val currentItem = currentList.getOrNull(holder.bindingAdapterPosition)
                if (currentItem?.stableId == item.stableId && bitmap != null) {
                    holder.icon.setImageBitmap(bitmap)
                }
            }
        } else {
            holder.icon.setImageResource(item.iconResId ?: android.R.drawable.sym_def_app_icon)
        }

        holder.icon.setOnLongClickListener(null)
        holder.card.setOnClickListener { onItemClick(item) }
        if (item.type == HomeAppItem.Type.APP) {
            holder.card.setOnLongClickListener { onItemLongClick(item) }
            holder.icon.setOnLongClickListener {
                touchHelper?.startDrag(holder)
                true
            }
        } else {
            holder.card.setOnLongClickListener(null)
        }
    }

    private fun android.content.Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
