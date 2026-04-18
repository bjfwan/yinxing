package com.bajianfeng.launcher.common.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.bajianfeng.launcher.R
import com.google.android.material.card.MaterialCardView

class PageStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val titleView: TextView
    private val messageView: TextView
    private val actionButton: MaterialCardView
    private val actionTextView: TextView
    private var contentView: View? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_page_state, this, true)
        titleView = findViewById(R.id.tv_page_state_title)
        messageView = findViewById(R.id.tv_page_state_message)
        actionButton = findViewById(R.id.btn_page_state_action)
        actionTextView = findViewById(R.id.tv_page_state_action)
        isVisible = false
    }

    fun attachContent(contentView: View) {
        this.contentView = contentView
    }

    fun show(
        title: CharSequence,
        message: CharSequence,
        actionText: CharSequence? = null,
        action: (() -> Unit)? = null
    ) {
        contentView?.isVisible = false
        isVisible = true
        titleView.text = title
        messageView.text = message
        val hasAction = !actionText.isNullOrBlank() && action != null
        actionButton.isVisible = hasAction
        actionButton.setOnClickListener(null)
        actionTextView.setOnClickListener(null)
        if (hasAction) {
            actionTextView.text = actionText
            val clickAction = View.OnClickListener { action?.invoke() }
            actionButton.setOnClickListener(clickAction)
            actionTextView.setOnClickListener(clickAction)
        } else {
            actionTextView.text = ""
        }
    }

    fun hide() {
        actionButton.setOnClickListener(null)
        actionTextView.setOnClickListener(null)
        isVisible = false
        contentView?.isVisible = true
    }

}
