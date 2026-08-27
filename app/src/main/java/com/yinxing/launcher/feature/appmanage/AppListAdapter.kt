package com.yinxing.launcher.feature.appmanage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yinxing.launcher.R
import com.yinxing.launcher.common.media.MediaThumbnailLoader
import com.yinxing.launcher.feature.settings.SettingsToggle
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
        val checkbox: SettingsToggle = view.findViewById(R.id.app_checkbox)
        val divider: View = view.findViewById(R.id.app_row_divider)
        var iconJob: Job? = null
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        bind(holder, getItem(position), position == itemCount - 1)
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

    private fun bind(holder: ViewHolder, appInfo: AppInfo, isLast: Boolean) {
        val context = holder.itemView.context
        holder.name.text = appInfo.appName
        holder.divider.visibility = if (isLast) View.GONE else View.VISIBLE
        val selectionState = context.getString(
            if (appInfo.isSelected) R.string.app_manage_selected else R.string.app_manage_unselected
        )
        val selectionDescription = context.getString(
            R.string.app_manage_selection_content_description,
            appInfo.appName,
            selectionState
        )

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = appInfo.isSelected
        holder.checkbox.contentDescription = selectionDescription
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != appInfo.isSelected) {
                onCheckChanged(appInfo, isChecked)
            }
        }

        holder.itemView.findViewById<View>(R.id.app_row_click_target).apply {
            contentDescription = selectionDescription
            setOnClickListener {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
            }
        }

        val iconSize = context.dpToPx(if (lowPerformanceMode) 32 else 40)
        holder.icon.setImageDrawable(null)
        holder.icon.alpha = 0f
        holder.iconJob?.cancel()
        holder.iconJob = scope.launch {
            val bitmap = MediaThumbnailLoader.loadAppIcon(context, appInfo.packageName, iconSize)
            if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) {
                return@launch
            }
            val currentItem = currentList.getOrNull(holder.bindingAdapterPosition)
            if (currentItem?.packageName == appInfo.packageName && bitmap != null) {
                holder.icon.setImageBitmap(bitmap)
                holder.icon.alpha = 1f
            }
        }
    }

    private fun android.content.Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
