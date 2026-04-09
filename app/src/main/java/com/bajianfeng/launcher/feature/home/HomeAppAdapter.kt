package com.bajianfeng.launcher.feature.home

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bajianfeng.launcher.R

class HomeAppAdapter(
    private val appList: MutableList<HomeAppItem>,
    private val onItemClick: (HomeAppItem) -> Unit,
    private val onItemLongClick: (HomeAppItem) -> Boolean,
    private val onOrderChanged: () -> Unit
) : RecyclerView.Adapter<HomeAppAdapter.ViewHolder>(), ItemTouchHelperAdapter {

    private var touchHelper: ItemTouchHelper? = null

    fun setTouchHelper(helper: ItemTouchHelper) {
        touchHelper = helper
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_item)
        val icon: ImageView = view.findViewById(R.id.icon)
        val name: TextView = view.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = appList[position]
        holder.icon.setImageDrawable(item.icon)
        holder.name.text = item.appName

        holder.card.setOnTouchListener(null)
        holder.icon.setOnLongClickListener(null)
        holder.icon.setOnTouchListener(null)

        if (item.type == HomeAppItem.Type.APP) {
            holder.card.setOnClickListener { onItemClick(item) }

            holder.card.setOnLongClickListener {
                onItemLongClick(item)
            }

            holder.icon.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper?.startDrag(holder)
                }
                false
            }
        } else {
            holder.card.setOnClickListener { onItemClick(item) }
            holder.card.setOnLongClickListener(null)
        }
    }

    override fun getItemCount() = appList.size

    override fun canMoveItem(position: Int): Boolean {
        return appList.getOrNull(position)?.type == HomeAppItem.Type.APP
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!canMoveItem(fromPosition) || !canMoveItem(toPosition)) {
            return false
        }

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                java.util.Collections.swap(appList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                java.util.Collections.swap(appList, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onOrderChanged()
        return true
    }
}
