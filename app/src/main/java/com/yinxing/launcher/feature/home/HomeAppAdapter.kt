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
import com.yinxing.launcher.R
import com.bajianfeng.launcher.common.media.MediaThumbnailLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Collections

class HomeAppAdapter(
    private val scope: CoroutineScope,
    private var lowPerformanceMode: Boolean,
    private var iconScale: Int = 100,
    private val onItemClick: (HomeAppItem) -> Unit,
    private val onItemLongClick: (HomeAppItem) -> Boolean,
    private val onOrderChanged: (List<HomeAppItem>) -> Unit
) : ListAdapter<HomeAppItem, RecyclerView.ViewHolder>(DiffCallback), ItemTouchHelperAdapter {

    companion object {
        const val VIEW_TYPE_APP = 0

        private val DiffCallback = object : DiffUtil.ItemCallback<HomeAppItem>() {
            override fun areItemsTheSame(oldItem: HomeAppItem, newItem: HomeAppItem) =
                oldItem.stableId == newItem.stableId

            override fun areContentsTheSame(oldItem: HomeAppItem, newItem: HomeAppItem) =
                oldItem == newItem
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
        if (lowPerformanceMode == enabled) return
        lowPerformanceMode = enabled
        notifyItemRangeChanged(0, itemCount)
    }

    fun setIconScale(scale: Int) {
        if (iconScale == scale) return
        iconScale = scale
        notifyItemRangeChanged(0, itemCount)
    }

    // ── ViewHolder 定义 ──────────────────────────

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_item)
        val icon: ImageView = view.findViewById(R.id.icon)
        val name: TextView = view.findViewById(R.id.name)
        var iconJob: Job? = null
    }

    // ── ListAdapter 接口 ─────────────────────────

    override fun getItemViewType(position: Int): Int = VIEW_TYPE_APP

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return AppViewHolder(inflater.inflate(R.layout.item_home_app, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is AppViewHolder) bindApp(holder, item)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AppViewHolder) holder.iconJob?.cancel()
        super.onViewRecycled(holder)
    }

    // ── 绑定普通卡片 ─────────────────────────────

    private fun bindApp(holder: AppViewHolder, item: HomeAppItem) {
        val context = holder.itemView.context
        holder.card.cardElevation = context.dpToPx(if (lowPerformanceMode) 2 else 6).toFloat()
        holder.name.text = item.appName
        holder.card.contentDescription = item.appName

        val baseIconDp = if (lowPerformanceMode) 80 else 96
        val scaledIconDp = (baseIconDp * iconScale / 100f).toInt().coerceAtLeast(48)
        val iconSize = context.dpToPx(scaledIconDp)
        holder.icon.layoutParams = holder.icon.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        val basePadDp = if (lowPerformanceMode) 12 else 16
        val scaledPadDp = (basePadDp * iconScale / 100f).toInt().coerceAtLeast(8)
        val pad = context.dpToPx(scaledPadDp)
        holder.icon.setPadding(pad, pad, pad, pad)

        val baseCardDp = 200
        val scaledCardDp = (baseCardDp * iconScale / 100f).toInt().coerceAtLeast(120)
        holder.card.layoutParams = holder.card.layoutParams.apply {
            height = context.dpToPx(scaledCardDp)
        }

        // 根据类型设置图标圆形背景色
        val iconBgRes = when (item.type) {
            HomeAppItem.Type.PHONE -> R.drawable.icon_background_phone
            HomeAppItem.Type.WECHAT_VIDEO -> R.drawable.icon_background_wechat
            else -> R.drawable.icon_background
        }
        holder.icon.setBackgroundResource(iconBgRes)

        holder.iconJob?.cancel()
        if (item.type == HomeAppItem.Type.APP) {
            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
            holder.iconJob = scope.launch {
                val bitmap = MediaThumbnailLoader.loadAppIcon(context, item.packageName, iconSize)
                if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) return@launch
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
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.icon.setOnClickListener { onItemClick(item) }
        holder.name.setOnClickListener { onItemClick(item) }
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

    // ── ItemTouchHelper（拖拽，内置卡片不参与）──

    override fun canMoveItem(position: Int): Boolean =
        getItem(position).type == HomeAppItem.Type.APP

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!canMoveItem(fromPosition) || !canMoveItem(toPosition)) return false
        val reordered = currentList.toMutableList()
        if (fromPosition < toPosition) {
            for (index in fromPosition until toPosition) Collections.swap(reordered, index, index + 1)
        } else {
            for (index in fromPosition downTo toPosition + 1) Collections.swap(reordered, index, index - 1)
        }
        submitList(reordered)
        onOrderChanged(reordered)
        return true
    }

    private fun android.content.Context.dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
