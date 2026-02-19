package com.bajianfeng.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class HomeAppAdapter(
    private val appList: MutableList<HomeAppItem>,
    private val onItemClick: (HomeAppItem) -> Unit,
    private val onItemLongClick: (HomeAppItem) -> Boolean,
    private val onOrderChanged: () -> Unit
) : RecyclerView.Adapter<HomeAppAdapter.ViewHolder>(), ItemTouchHelperAdapter {

    private lateinit var touchHelper: ItemTouchHelper
    
    fun setTouchHelper(helper: ItemTouchHelper) {
        touchHelper = helper
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.card_item)
        val icon: ImageView = view.findViewById(R.id.icon)
        val name: TextView = view.findViewById(R.id.name)
        val deleteBtn: ImageView = view.findViewById(R.id.btn_delete)
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
        
        if (item.type == HomeAppItem.Type.APP) {
            holder.deleteBtn.visibility = View.GONE
            
            holder.icon.setOnClickListener {
                onItemClick(item)
            }
            
            holder.icon.setOnLongClickListener {
                holder.deleteBtn.visibility = View.VISIBLE
                true
            }
            
            holder.deleteBtn.setOnClickListener {
                holder.deleteBtn.visibility = View.GONE
                onItemLongClick(item)
            }
            
            holder.card.setOnTouchListener { v, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    if (::touchHelper.isInitialized) {
                        touchHelper.startDrag(holder)
                    }
                }
                false
            }
        } else {
            holder.deleteBtn.visibility = View.GONE
            
            holder.card.setOnClickListener {
                onItemClick(item)
            }
            
            holder.icon.setOnClickListener {
                onItemClick(item)
            }
            
            holder.icon.setOnLongClickListener(null)
            holder.card.setOnTouchListener(null)
        }
    }

    override fun getItemCount() = appList.size

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
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
    }
}
