package com.yinxing.launcher.feature.home

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class ItemMoveCallback(
    private val adapter: ItemTouchHelperAdapter,
    private var animateDrag: Boolean
) : ItemTouchHelper.Callback() {
    override fun isLongPressDragEnabled() = false

    override fun isItemViewSwipeEnabled() = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if (!adapter.canMoveItem(viewHolder.bindingAdapterPosition)) {
            return makeMovementFlags(0, 0)
        }

        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return adapter.onItemMove(
            viewHolder.bindingAdapterPosition,
            target.bindingAdapterPosition
        )
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.bindingAdapterPosition
                ?.takeIf { it != RecyclerView.NO_POSITION }
                ?.let(adapter::onDragStarted)
        }
        if (animateDrag && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = 0.7f
            viewHolder?.itemView?.scaleX = 1.05f
            viewHolder?.itemView?.scaleY = 1.05f
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1.0f
        viewHolder.itemView.scaleX = 1.0f
        viewHolder.itemView.scaleY = 1.0f
        adapter.onDragFinished()
    }

    fun setAnimateDrag(enabled: Boolean) {
        animateDrag = enabled
    }
}

interface ItemTouchHelperAdapter {
    fun canMoveItem(position: Int): Boolean
    fun onDragStarted(position: Int)
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
    fun onDragFinished()
}
