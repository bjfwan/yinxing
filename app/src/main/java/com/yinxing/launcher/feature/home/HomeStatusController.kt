package com.yinxing.launcher.feature.home

import android.view.View
import com.yinxing.launcher.R
import com.yinxing.launcher.databinding.ActivityMainBinding

class HomeStatusController(
    private val binding: ActivityMainBinding,
    private val onRetry: () -> Unit,
    private val onOpenSettings: () -> Unit
) {
    fun render(state: HomeUiState) {
        when (state) {
            is HomeUiState.Loading -> show(
                titleRes = R.string.home_status_loading_title,
                messageRes = R.string.home_status_loading_message,
                showProgress = true
            )
            is HomeUiState.Empty -> show(
                titleRes = R.string.home_status_empty_title,
                messageRes = R.string.home_status_empty_message,
                showProgress = false,
                actionTextRes = R.string.action_go_to_settings,
                action = onOpenSettings
            )
            is HomeUiState.Error -> show(
                titleRes = R.string.home_status_error_title,
                messageRes = R.string.home_status_error_message,
                showProgress = false,
                actionTextRes = R.string.action_retry,
                action = onRetry
            )
            is HomeUiState.Success -> hide()
        }
    }

    private fun show(
        titleRes: Int,
        messageRes: Int,
        showProgress: Boolean,
        actionTextRes: Int? = null,
        action: (() -> Unit)? = null
    ) {
        binding.cardHomeStatus.visibility = View.VISIBLE
        binding.progressHomeStatus.visibility = if (showProgress) View.VISIBLE else View.GONE
        binding.tvHomeStatusTitle.setText(titleRes)
        binding.tvHomeStatusMessage.setText(messageRes)
        if (actionTextRes != null && action != null) {
            binding.btnHomeStatusAction.visibility = View.VISIBLE
            binding.btnHomeStatusAction.setText(actionTextRes)
            binding.btnHomeStatusAction.setOnClickListener { action() }
        } else {
            binding.btnHomeStatusAction.visibility = View.GONE
            binding.btnHomeStatusAction.setOnClickListener(null)
        }
    }

    private fun hide() {
        binding.cardHomeStatus.visibility = View.GONE
        binding.progressHomeStatus.visibility = View.GONE
        binding.btnHomeStatusAction.visibility = View.GONE
        binding.btnHomeStatusAction.setOnClickListener(null)
    }
}
