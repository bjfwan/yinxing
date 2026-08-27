package com.yinxing.launcher.feature.videocall

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yinxing.launcher.R
import com.yinxing.launcher.data.contact.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ElderContactActionUiTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val themedContext: Context = ContextThemeWrapper(context, R.style.Theme_OldLauncher)

    @Test
    fun phoneContactActionButtonUsesLargeGreenTouchTarget() {
        val view = LayoutInflater.from(themedContext)
            .inflate(R.layout.item_phone_contact, FrameLayout(themedContext), false)
        val button = view.findViewById<MaterialButton>(R.id.btn_call)

        assertTrue(view.layoutParams.height >= 232.dp)
        assertTrue(button.layoutParams.height >= 76.dp)
        assertTrue(button.textSize >= 22.sp)
        assertEquals(
            ContextCompat.getColor(context, R.color.launcher_phone_action),
            button.backgroundTintList?.defaultColor
        )
    }

    @Test
    fun phoneContactCardUsesSettingsSurfaceAndIconBadge() {
        val view = LayoutInflater.from(themedContext)
            .inflate(R.layout.item_phone_contact, FrameLayout(themedContext), false)
        val card = view as MaterialCardView
        val badge = view.findViewById<TextView>(R.id.tv_auto_answer_badge)

        assertEquals(18.dp.toFloat(), card.radius, 0.5f)
        assertTrue(card.cardElevation <= 1.dp)
        assertTrue(badge.text.none { it == '⚡' })
        assertTrue(badge.compoundDrawablesRelative[0] != null)
    }

    @Test
    fun videoContactPhoneFallbackUsesPhoneTextAndGreenAction() {
        val owner = TestOwner()
        val adapter = VideoCallContactAdapter(
            scope = owner.lifecycleScope,
            lowPerformanceMode = true,
            onContactClick = {},
            onEditClick = {}
        )
        val parent = FrameLayout(themedContext)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.submitList(
            listOf(
                Contact(
                    id = "phone",
                    name = "爸爸",
                    phoneNumber = "13800138000",
                    preferredAction = Contact.PreferredAction.PHONE
                )
            )
        )
        shadowOf(Looper.getMainLooper()).idle()

        adapter.onBindViewHolder(holder, 0)

        val button = holder.itemView.findViewById<MaterialButton>(R.id.btn_video_call)
        assertEquals(context.getString(R.string.contact_card_action_phone_v2), button.text.toString())
        assertEquals(
            ContextCompat.getColor(context, R.color.launcher_phone_action),
            button.backgroundTintList?.defaultColor
        )
    }

    @Test
    fun videoContactWechatActionUsesLargeGreenTouchTarget() {
        val view = LayoutInflater.from(themedContext)
            .inflate(R.layout.item_video_contact, FrameLayout(themedContext), false)
        val button = view.findViewById<MaterialButton>(R.id.btn_video_call)

        assertTrue(button.layoutParams.height >= 76.dp)
        assertTrue(button.textSize >= 22.sp)
        assertEquals(
            ContextCompat.getColor(context, R.color.launcher_phone_action),
            button.backgroundTintList?.defaultColor
        )
    }

    private class TestOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.STARTED
        }

        override val lifecycle: Lifecycle
            get() = registry
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    private val Int.sp: Float
        get() = this * context.resources.displayMetrics.scaledDensity
}
