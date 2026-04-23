package com.yinxing.launcher.feature.appmanage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppListAdapter(
    private val scope: CoroutineScope,
    private var lowPerformanceMode: Boolean,
    private val onCheckChanged: (AppInfo, Boolean) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem.packageName == newItem.packageName
            }

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem == newItem
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val checkbox: CheckBox = view.findViewById(R.id.app_checkbox)
        var iconJob: Job? = null
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        bind(holder, getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.iconJob?.cancel()
        super.onViewRecycled(holder)
    }

    fun updateSelection(packageName: String, isSelected: Boolean) {
        submitList(
            currentList.map { app ->
                if (app.packageName == packageName) app.copy(isSelected = isSelected) else app
            }
        )
    }

    fun setLowPerformanceMode(enabled: Boolean) {
        if (lowPerformanceMode == enabled) {
            return
        }
        lowPerformanceMode = enabled
        notifyItemRangeChanged(0, itemCount)
    }

    private fun bind(holder: ViewHolder, appInfo: AppInfo) {
        val context = holder.itemView.context
        holder.name.text = appInfo.appName

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = appInfo.isSelected
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != appInfo.isSelected) {
                onCheckChanged(appInfo, isChecked)
            }
        }

        holder.itemView.setOnClickListener {
            holder.checkbox.isChecked = !holder.checkbox.isChecked
        }

        val iconSize = context.dpToPx(if (lowPerformanceMode) 48 else 56)
        holder.icon.layoutParams = holder.icon.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
        holder.iconJob?.cancel()
        holder.iconJob = scope.launch {
            val bitmap = MediaThumbnailLoader.loadAppIcon(context, appInfo.packageName, iconSize)
            if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
                return@launch
            }
            val currentItem = currentList.getOrNull(holder.bindingAdapterPosition)
            if (currentItem?.packageName == appInfo.packageName && bitmap != null) {
                holder.icon.setImageBitmap(bitmap)
            }
        }
    }

    private fun android.content.Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
