package com.yinxing.launcher.feature.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
        private const val MAX_ANIMATED_ITEMS = 6

        private val DiffCallback = object : DiffUtil.ItemCallback<HomeAppItem>() {
            override fun areItemsTheSame(oldItem: HomeAppItem, newItem: HomeAppItem) =
                oldItem.stableId == newItem.stableId

            override fun areContentsTheSame(oldItem: HomeAppItem, newItem: HomeAppItem) =
                oldItem == newItem
        }
    }

    private var touchHelper: ItemTouchHelper? = null
    private var maxAnimatedPosition = -1
    private var dragItems: MutableList<HomeAppItem>? = null
    private var dragChanged = false

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

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_item)
        val icon: ImageView = view.findViewById(R.id.icon)
        val name: TextView = view.findViewById(R.id.name)
        var iconJob: Job? = null
        var uiKey: Int = Int.MIN_VALUE
    }

    override fun getItemCount(): Int = displayedItems().size

    override fun getItemViewType(position: Int): Int = VIEW_TYPE_APP

    override fun getItemId(position: Int): Long = itemAt(position).stableId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return AppViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_home_app, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = itemAt(position)
        if (holder is AppViewHolder) {
            applyUi(holder)
            bindApp(holder, item)
            animateIn(holder.itemView, position)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AppViewHolder) holder.iconJob?.cancel()
        super.onViewRecycled(holder)
    }

    private fun displayedItems(): List<HomeAppItem> = dragItems ?: currentList

    private fun itemAt(position: Int): HomeAppItem = displayedItems()[position]

    private fun itemAtOrNull(position: Int): HomeAppItem? = displayedItems().getOrNull(position)

    private fun applyUi(holder: AppViewHolder) {
        val context = holder.itemView.context
        val uiKey = iconScale * 10 + if (lowPerformanceMode) 1 else 0
        if (holder.uiKey == uiKey) {
            return
        }
        holder.uiKey = uiKey
        holder.card.cardElevation = context.dpToPx(if (lowPerformanceMode) 2 else 6).toFloat()
        val baseIconDp = if (lowPerformanceMode) 80 else 96
        val iconSize = context.dpToPx((baseIconDp * iconScale / 100f).toInt().coerceAtLeast(48))
        val iconLp = holder.icon.layoutParams
        if (iconLp.width != iconSize || iconLp.height != iconSize) {
            iconLp.width = iconSize
            iconLp.height = iconSize
            holder.icon.layoutParams = iconLp
        }
        val basePadDp = if (lowPerformanceMode) 12 else 16
        val pad = context.dpToPx((basePadDp * iconScale / 100f).toInt().coerceAtLeast(8))
        if (holder.icon.paddingLeft != pad) {
            holder.icon.setPadding(pad, pad, pad, pad)
        }
        val cardHeight = context.dpToPx((200 * iconScale / 100f).toInt().coerceAtLeast(120))
        val cardLp = holder.card.layoutParams
        if (cardLp.height != cardHeight) {
            cardLp.height = cardHeight
            holder.card.layoutParams = cardLp
        }
        holder.name.textSize = (24f * iconScale / 100f).coerceAtLeast(16f)
    }

    private fun bindApp(holder: AppViewHolder, item: HomeAppItem) {
        val context = holder.itemView.context
        holder.name.text = item.appName
        holder.card.contentDescription = item.appName
        holder.icon.setBackgroundResource(
            when (item.type) {
                HomeAppItem.Type.PHONE -> R.drawable.icon_background_phone
                HomeAppItem.Type.WECHAT_VIDEO -> R.drawable.icon_background_wechat
                else -> R.drawable.icon_background
            }
        )
        holder.iconJob?.cancel()
        if (item.type == HomeAppItem.Type.APP) {
            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
            val iconSize = holder.icon.layoutParams.width.coerceAtLeast(1)
            holder.iconJob = scope.launch {
                val bitmap = MediaThumbnailLoader.loadAppIcon(context, item.packageName, iconSize)
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@launch
                val currentItem = itemAtOrNull(currentPosition)
                if (currentItem?.stableId == item.stableId && bitmap != null) {
                    holder.icon.setImageBitmap(bitmap)
                }
            }
        } else {
            holder.icon.setImageResource(item.iconResId ?: android.R.drawable.sym_def_app_icon)
        }
        holder.icon.setOnLongClickListener(null)
        val clickListener = View.OnClickListener { onItemClick(item) }
        holder.card.setOnClickListener(clickListener)
        holder.itemView.setOnClickListener(clickListener)
        holder.icon.setOnClickListener(clickListener)
        holder.name.setOnClickListener(clickListener)
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

    private fun animateIn(view: View, position: Int) {
        if (lowPerformanceMode || position <= maxAnimatedPosition || position >= MAX_ANIMATED_ITEMS) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        maxAnimatedPosition = position
        view.alpha = 0f
        view.translationY = 40f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(position * 30L)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun canMoveItem(position: Int): Boolean =
        itemAtOrNull(position)?.type == HomeAppItem.Type.APP

    override fun onDragStarted(position: Int) {
        if (!canMoveItem(position) || dragItems != null) {
            return
        }
        dragItems = currentList.toMutableList()
        dragChanged = false
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!canMoveItem(fromPosition) || !canMoveItem(toPosition)) return false
        val reordered = dragItems ?: currentList.toMutableList().also {
            dragItems = it
            dragChanged = false
        }
        if (fromPosition !in reordered.indices || toPosition !in reordered.indices) {
            return false
        }
        if (fromPosition < toPosition) {
            for (index in fromPosition until toPosition) Collections.swap(reordered, index, index + 1)
        } else {
            for (index in fromPosition downTo toPosition + 1) Collections.swap(reordered, index, index - 1)
        }
        dragChanged = true
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onDragFinished() {
        val reordered = dragItems ?: return
        if (!dragChanged) {
            dragItems = null
            return
        }
        val finalItems = reordered.toList()
        dragChanged = false
        maxAnimatedPosition = Int.MAX_VALUE  // 拖拽后不再触发入场动画
        submitList(finalItems) {
            dragItems = null
            onOrderChanged(finalItems)
        }
    }

    private fun android.content.Context.dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
