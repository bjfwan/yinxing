package com.bajianfeng.launcher.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.bajianfeng.launcher.R

class PageStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val titleView: TextView
    private val messageView: TextView
    private val actionButton: CardView
    private val actionTextView: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_page_state, this, true)
        titleView = findViewById(R.id.tv_page_state_title)
        messageView = findViewById(R.id.tv_page_state_message)
        actionButton = findViewById(R.id.btn_page_state_action)
        actionTextView = findViewById(R.id.tv_page_state_action)
    }

    fun show(
        title: String,
        message: String,
        actionText: String? = null,
        action: (() -> Unit)? = null
    ) {
        isVisible = true
        titleView.text = title
        messageView.text = message
        val showAction = !actionText.isNullOrBlank() && action != null
        actionButton.isVisible = showAction
        if (showAction) {
            actionTextView.text = actionText
            actionButton.setOnClickListener { action?.invoke() }
        } else {
            actionButton.setOnClickListener(null)
        }
    }

    fun hide() {
        isVisible = false
        actionButton.setOnClickListener(null)
    }
}
