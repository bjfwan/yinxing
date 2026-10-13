package com.yinxing.launcher.feature.phone

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yinxing.launcher.R
import com.yinxing.launcher.data.contact.ContactSqliteStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneContactActivitySmokeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetContactManagerSingleton()
        ContactSqliteStore.deleteDatabase(context)
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun emptyCallStateGuidesFamilyIntoManageMode() {
        val activity = Robolectric.buildActivity(PhoneContactActivity::class.java).setup().get()
        val stateView = activity.findViewById<View>(R.id.view_page_state)
        val stateAction = activity.findViewById<TextView>(R.id.tv_page_state_action)
        val modeAction = activity.findViewById<TextView>(R.id.tv_mode_action)
        waitUntil { stateView.visibility == View.VISIBLE && stateAction.text.isNotBlank() }

        assertEquals("管理联系人", stateAction.text.toString())
        activity.findViewById<View>(R.id.btn_page_state_action).performClick()
        waitUntil {
            modeAction.text.toString() == activity.getString(R.string.action_done) &&
                stateAction.text.toString() == activity.getString(R.string.state_phone_empty_action)
        }

        assertEquals(activity.getString(R.string.action_done), modeAction.text.toString())
        assertEquals(activity.getString(R.string.state_phone_empty_action), stateAction.text.toString())
    }

    @Test
    fun headerKeepsTitleOnOneLine() {
        val activity = Robolectric.buildActivity(PhoneContactActivity::class.java).setup().get()

        assertEquals(1, activity.findViewById<TextView>(R.id.tv_page_title).maxLines)
    }

    @Test
    fun contactDialogGroupsSecondaryActionsSideBySide() {
        val intent = PhoneContactActivity.createIntent(context, startInManageMode = true)
        val activity = Robolectric.buildActivity(PhoneContactActivity::class.java, intent).setup().get()
        val addContact = activity.findViewById<View>(R.id.btn_add_contact)
        waitUntil {
            activity.findViewById<TextView>(R.id.tv_mode_action).text.toString() ==
                activity.getString(R.string.action_done)
        }

        addContact.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = requireNotNull(ShadowDialog.getLatestDialog())
        val pickContact = dialog.findViewById<View>(R.id.btn_pick_contact)
        val selectPhoto = dialog.findViewById<View>(R.id.btn_select_photo)
        val actionRow = pickContact.parent as LinearLayout

        assertSame(actionRow, selectPhoto.parent)
        assertEquals(LinearLayout.HORIZONTAL, actionRow.orientation)
        assertEquals(0, pickContact.layoutParams.width)
        assertEquals(0, selectPhoto.layoutParams.width)
    }

    @Test
    fun layoutSegmentAppliesImmediatelyAndPersists() {
        val intent = PhoneContactActivity.createIntent(context, startInManageMode = true)
        val activity = Robolectric.buildActivity(PhoneContactActivity::class.java, intent).setup().get()
        waitUntil {
            activity.findViewById<TextView>(R.id.tv_mode_action).text.toString() ==
                activity.getString(R.string.action_done)
        }

        activity.findViewById<View>(R.id.btn_layout_grid).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val recycler = activity.findViewById<RecyclerView>(R.id.recycler_phone_contacts)
        assertEquals(GridLayoutManager::class.java, recycler.layoutManager?.javaClass)
        assertEquals(PhoneContactLayoutStyle.GRID, PhoneContactLayoutPreferences(context).get())
        assertTrue(activity.findViewById<View>(R.id.btn_layout_grid).isSelected)
        assertFalse(activity.findViewById<View>(R.id.btn_layout_large).isSelected)
    }

    @Test
    fun layoutSegmentReflectsCurrentStyle() {
        PhoneContactLayoutPreferences(context).set(PhoneContactLayoutStyle.LARGE)
        val intent = PhoneContactActivity.createIntent(context, startInManageMode = true)
        val activity = Robolectric.buildActivity(PhoneContactActivity::class.java, intent).setup().get()
        waitUntil {
            activity.findViewById<TextView>(R.id.tv_mode_action).text.toString() ==
                activity.getString(R.string.action_done)
        }

        assertTrue(activity.findViewById<View>(R.id.btn_layout_large).isSelected)
        assertFalse(activity.findViewById<View>(R.id.btn_layout_grid).isSelected)
    }

    private fun resetContactManagerSingleton() {
        val field = Class.forName("com.yinxing.launcher.data.contact.ContactManager")
            .getDeclaredField("instance")
        field.isAccessible = true
        (field.get(null) as? com.yinxing.launcher.data.contact.ContactManager)?.close()
        field.set(null, null)
    }

    private fun waitUntil(timeoutMs: Long = 2_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
