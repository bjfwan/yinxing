package com.yinxing.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.data.settings.LauncherSettingsDataStore
import com.yinxing.launcher.feature.incoming.IncomingCallDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBuild
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivitySmokeTest {
    @Test
    fun darkModeChooserUsesTheUnifiedDialogSurface() {
        val activity = buildActivity()
        val dialog = activity.detailController.showDarkModeDialog()

        assertEquals(
            activity.getString(R.string.settings_dark_mode_title),
            dialog.findViewById<TextView>(R.id.tv_dialog_title).text.toString()
        )
        val choices = dialog.findViewById<LinearLayout>(R.id.layout_permission_items)
        assertEquals(LinearLayout.HORIZONTAL, choices.orientation)
        assertEquals(3, choices.childCount)
        assertNotNull(dialog.findViewById<View>(R.id.btn_close))
        dialog.dismiss()
    }

    @Test
    fun displayScreenOffersFourAppFontSizeChoices() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.Display)

        assertTrue(activity.findDetailText("字体大小").isNotEmpty())
        assertEquals("跟随系统", activity.detailRows().getChildAt(1).summaryText())

        val dialog = activity.detailController.showFontScaleDialog()
        val choices = dialog.findViewById<LinearLayout>(R.id.layout_permission_items)
        assertEquals(LinearLayout.HORIZONTAL, choices.orientation)
        assertEquals(4, choices.childCount)
        dialog.dismiss()
    }

    @Test
    fun selectedAppFontSizeAppliesToTheWholeActivityContext() {
        LauncherPreferences.getInstance(context)
            .setFontScaleMode(LauncherPreferences.FONT_SCALE_EXTRA_LARGE)

        val activity = buildActivity()

        assertEquals(1.30f, activity.resources.configuration.fontScale, 0.001f)
    }

    @Test
    fun selectingAnAppFontSizePersistsAndReturnsToDisplaySettings() {
        val activity = buildActivity()
        val dialog = activity.detailController.showFontScaleDialog()
        val choices = dialog.findViewById<LinearLayout>(R.id.layout_permission_items)

        choices.getChildAt(2).performClick()

        assertEquals(
            LauncherPreferences.FONT_SCALE_LARGE,
            LauncherPreferences.getInstance(context).getFontScaleMode()
        )
        assertEquals(
            SettingsScreen.Display.key,
            activity.intent.getStringExtra(SettingsActivity.EXTRA_SECTION)
        )
    }

    @Test
    fun defaultLauncherDialogShowsTheDeviceManufacturerIcon() {
        val activity = buildActivity()
        val dialog = activity.showSetDefaultLauncherDialog()
        val icon = requireNotNull(dialog.findViewById<ImageView>(R.id.iv_dialog_vendor_icon))

        assertEquals(View.VISIBLE, icon.visibility)
        assertNotNull(icon.drawable)
    }

    @Test
    fun defaultLauncherRowUsesAnUntintedManufacturerIcon() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.Device)
        val firstRow = activity.findViewById<LinearLayout>(R.id.settings_detail_rows).getChildAt(0)
        val icon = firstRow.findViewById<ImageView>(R.id.detail_row_icon)

        assertNotNull(icon.drawable)
        assertEquals(null, icon.imageTintList)
    }

    @Test
    fun vivoDefaultLauncherDialogOffersBothRequiredSystemSteps() {
        val originalManufacturer = Build.MANUFACTURER
        ShadowBuild.setManufacturer("vivo")
        try {
            val activity = buildActivity()
            val dialog = activity.showSetDefaultLauncherDialog()
            val actions = requireNotNull(
                dialog.findViewById<LinearLayout>(R.id.layout_dialog_actions)
            )
            val defaultAction = requireNotNull(
                dialog.findViewById<TextView>(R.id.tv_cancel_label)
            )
            val securityAction = requireNotNull(
                dialog.findViewById<TextView>(R.id.tv_primary_label)
            )
            val defaultButton = requireNotNull(dialog.findViewById<View>(R.id.btn_cancel))
            val securityButton = requireNotNull(dialog.findViewById<View>(R.id.btn_open_settings))
            val defaultParams = defaultButton.layoutParams as LinearLayout.LayoutParams
            val securityParams = securityButton.layoutParams as LinearLayout.LayoutParams
            val message = requireNotNull(dialog.findViewById<TextView>(R.id.tv_dialog_message))

            assertEquals(LinearLayout.VERTICAL, actions.orientation)
            assertEquals(defaultParams.width, securityParams.width)
            assertEquals(0, defaultParams.marginStart)
            assertEquals(0, defaultParams.marginEnd)
            assertEquals(0, securityParams.marginStart)
            assertEquals(0, securityParams.marginEnd)
            assertEquals(
                activity.getString(R.string.set_default_launcher_vivo_default_action),
                defaultAction.text.toString()
            )
            assertEquals(
                activity.getString(R.string.set_default_launcher_vivo_security_action),
                securityAction.text.toString()
            )
            assertTrue(message.text.contains("安全 → 更多安全设置 → 更换系统桌面"))
        } finally {
            ShadowBuild.setManufacturer(originalManufacturer)
        }
    }
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetLauncherPreferencesSingleton()
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        LauncherSettingsDataStore.getInstance(context).clear()
        IncomingCallDiagnostics.clear(context)
        registerSettingsActivity()
        registerHomeActivity("com.android.launcher3")
    }

    @Test
    fun standardOverviewContainsAllSecondaryPageEntries() {
        val activity = buildActivity()
        listOf(
            R.id.btn_detail_contacts,
            R.id.btn_detail_calls,
            R.id.btn_detail_diagnostics,
            R.id.btn_detail_safety,
            R.id.btn_detail_permissions,
            R.id.btn_detail_background,
            R.id.btn_detail_device,
            R.id.btn_detail_display,
            R.id.btn_detail_weather,
            R.id.btn_detail_system
        ).forEach { assertNotNull(activity.findViewById<View>(it)) }
    }

    @Test
    fun incomingGuardShowsSummaryAndAction() {
        val activity = buildActivity()
        idle()
        assertTrue(activity.findViewById<TextView>(R.id.tv_incoming_guard_summary).text.isNotEmpty())
        assertTrue(activity.findViewById<TextView>(R.id.tv_incoming_guard_action).text.isNotEmpty())
    }

    @Test
    fun autoAnswerSummaryReflectsEnabledState() {
        val activity = buildActivity()
        idle()
        activity.showScreen(SettingsScreen.Calls)
        assertTrue(
            activity.findDetailText(
                activity.getString(
                    R.string.settings_auto_answer_delay_summary,
                    LauncherPreferences.DEFAULT_AUTO_ANSWER_DELAY_SECONDS
                )
            ).isNotEmpty()
        )
    }

    @Test
    fun autoAnswerSummaryReflectsDisabledState() {
        LauncherPreferences.getInstance(context).setAutoAnswerEnabled(false)
        val activity = buildActivity()
        idle()
        activity.showScreen(SettingsScreen.Calls)
        assertTrue(
            activity.findDetailText(
                activity.getString(R.string.settings_auto_answer_summary_off)
            ).isNotEmpty()
        )
    }

    @Test
    fun secondaryPageSummariesArePopulated() {
        val activity = buildActivity()
        idle()
        listOf(
            R.id.btn_detail_contacts,
            R.id.btn_detail_permissions,
            R.id.btn_detail_background,
            R.id.btn_detail_device,
            R.id.btn_detail_display,
            R.id.btn_detail_weather,
            R.id.btn_detail_system
        ).forEach { rootId ->
            val root = activity.findViewById<View>(rootId)
            assertTrue(root.findViewById<TextView>(R.id.navigation_summary).text.isNotEmpty())
        }
    }

    @Test
    fun permissionGroupsShowProminentColoredStatus() {
        val activity = buildActivity()
        idle()
        activity.showScreen(SettingsScreen.Permissions)

        val firstRow = activity.findViewById<LinearLayout>(R.id.settings_detail_rows).getChildAt(0)
        val status = firstRow.findViewById<TextView>(R.id.detail_row_action)
        val expected = activity.permissionEntryBadge(
            activity.permissionEntryStates.getValue(PermissionEntry.PhonePermission)
        )

        assertEquals(View.VISIBLE, status.visibility)
        assertEquals(expected.text, status.text.toString())
        assertEquals(activity.getColor(expected.textColorResId), status.currentTextColor)
    }

    @Test
    fun denseSettingsAreSplitIntoFocusedCards() {
        val activity = buildActivity()

        activity.showScreen(SettingsScreen.Calls)
        assertEquals(1, activity.detailRows().childCount)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_detail_card_secondary).visibility)
        assertEquals(2, activity.detailRows(R.id.settings_detail_rows_secondary).childCount)

        activity.showScreen(SettingsScreen.CallDiagnostics)
        assertEquals(1, activity.detailRows().childCount)
        assertEquals(1, activity.detailRows(R.id.settings_detail_rows_secondary).childCount)

        activity.showScreen(SettingsScreen.Permissions)
        assertEquals(2, activity.detailRows().childCount)
        assertEquals(2, activity.detailRows(R.id.settings_detail_rows_secondary).childCount)
    }

    @Test
    fun navigableSettingRowsUseConciseSummariesAndChevrons() {
        val activity = buildActivity()

        activity.showScreen(SettingsScreen.Display)
        val displayRows = activity.detailRows()
        assertEquals("跟随系统", displayRows.getChildAt(1).summaryText())
        assertEquals("跟随系统", displayRows.getChildAt(2).summaryText())
        assertRowsUseChevronOnly(displayRows)

        activity.showScreen(SettingsScreen.System)
        assertRowsUseChevronOnly(activity.detailRows())

        activity.showScreen(SettingsScreen.Weather)
        assertRowsUseChevronOnly(activity.detailRows())

        activity.showScreen(SettingsScreen.Safety)
        val emergencyContactRow = activity.detailRows().getChildAt(1)
        assertEquals(View.GONE, emergencyContactRow.findViewById<View>(R.id.detail_row_action).visibility)
        assertEquals(View.VISIBLE, emergencyContactRow.findViewById<View>(R.id.detail_row_chevron).visibility)
    }

    @Test
    fun callDiagnosticsKeepFullDetailsBehindACompactRow() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.CallDiagnostics)

        val traceRow = activity.detailRows(R.id.settings_detail_rows_secondary).getChildAt(0)

        assertEquals("暂无记录，来电后显示链路结果", traceRow.summaryText())
        assertEquals(View.VISIBLE, traceRow.findViewById<View>(R.id.detail_row_chevron).visibility)
        assertTrue(traceRow.findViewById<View>(R.id.detail_row_click_target).isClickable)
    }

    @Test
    fun aboutScreenUsesTabsAndKeepsNavigationRowsCompact() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.About)

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_about_tabs).visibility)
        assertEquals(3, activity.detailRows().childCount)
        assertRowsUseChevronOnly(activity.detailRows())

        activity.findViewById<View>(R.id.settings_about_tab_service).performClick()
        assertEquals(4, activity.detailRows().childCount)
        assertRowsUseChevronOnly(activity.detailRows())
    }

    @Test
    fun incomingGuardDialogGroupsCallAndKeepAliveEntries() {
        val activity = buildActivity()
        idle()
        val dialog = activity.showIncomingGuardDialog()
        val groups = dialog.findViewById<LinearLayout>(R.id.layout_permission_items)
        val callEntries = groups.getChildAt(0).findViewById<LinearLayout>(R.id.layout_section_items)
        val keepAliveEntries = groups.getChildAt(1).findViewById<LinearLayout>(R.id.layout_section_items)

        assertEquals(2, groups.childCount)
        assertEquals(3, callEntries.childCount)
        assertEquals(3, keepAliveEntries.childCount)
    }

    @Test
    fun callsAndDiagnosticsScreensKeepTheirOwnFunctions() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.Calls)
        val matches = arrayListOf<View>()

        activity.findViewById<View>(android.R.id.content).findViewsWithText(
            matches,
            activity.getString(R.string.settings_default_phone_title),
            View.FIND_VIEWS_WITH_TEXT
        )

        assertTrue(matches.isNotEmpty())
        val adaptationMatches = arrayListOf<View>()
        activity.findViewById<View>(android.R.id.content).findViewsWithText(
            adaptationMatches,
            activity.getString(R.string.settings_incoming_vendor_title),
            View.FIND_VIEWS_WITH_TEXT
        )
        assertTrue(adaptationMatches.isEmpty())

        activity.showScreen(SettingsScreen.CallDiagnostics)
        val diagnosticsMatches = arrayListOf<View>()
        activity.findViewById<View>(android.R.id.content).findViewsWithText(
            diagnosticsMatches,
            activity.getString(R.string.settings_incoming_vendor_title),
            View.FIND_VIEWS_WITH_TEXT
        )
        assertTrue(diagnosticsMatches.isNotEmpty())
    }

    @Test
    fun safetyScreenOffersFallDetectionAndFamilyContactSetup() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.Safety)

        val fallDetectionMatches = arrayListOf<View>()
        activity.findViewById<View>(android.R.id.content).findViewsWithText(
            fallDetectionMatches,
            activity.getString(R.string.settings_fall_detection_title),
            View.FIND_VIEWS_WITH_TEXT
        )
        val contactMatches = arrayListOf<View>()
        activity.findViewById<View>(android.R.id.content).findViewsWithText(
            contactMatches,
            activity.getString(R.string.settings_fall_contact_title),
            View.FIND_VIEWS_WITH_TEXT
        )

        assertTrue(fallDetectionMatches.isNotEmpty())
        assertTrue(contactMatches.isNotEmpty())
    }

    @Test
    fun elderOverviewExposesFallDetectionSwitch() {
        val activity = buildActivity()
        activity.showScreen(SettingsScreen.ElderOverview)

        assertNotNull(activity.findViewById<View>(R.id.switch_elder_fall_detection))
    }

    @Test
    fun elderOverviewUsesOneCardHeightAcrossTheFourMainActions() {
        val activity = buildActivity()
        idle()
        activity.showScreen(SettingsScreen.ElderOverview)

        val heights = listOf(
            R.id.btn_elder_guard,
            R.id.btn_elder_contacts,
            R.id.btn_elder_system,
            R.id.btn_elder_device
        ).map { activity.findViewById<View>(it).layoutParams.height }

        assertEquals(1, heights.distinct().size)
    }

    @Test
    fun deviceSettingsDoNotOfferTheRemovedKioskMode() {
        val activity = buildActivity()
        idle()
        activity.showScreen(SettingsScreen.Device)
        val matches = arrayListOf<View>()

        activity.findViewById<View>(android.R.id.content).findViewsWithText(
            matches,
            "防退出模式",
            View.FIND_VIEWS_WITH_TEXT
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun lifecycleTransitionsDoNotFinishActivity() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        idle()
        controller.pause().resume()
        idle()
        assertFalse(controller.get().isFinishing)
        controller.destroy()
    }

    private fun buildActivity() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Suppress("DEPRECATION")
    private fun registerSettingsActivity() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        val applicationInfo = ApplicationInfo().apply {
            packageName = "com.android.settings"
            nonLocalizedLabel = "Settings"
        }
        val activityInfo = ActivityInfo().apply {
            packageName = "com.android.settings"
            name = "com.android.settings.Settings"
            this.applicationInfo = applicationInfo
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            intent,
            ResolveInfo().apply { this.activityInfo = activityInfo }
        )
    }

    @Suppress("DEPRECATION")
    private fun registerHomeActivity(packageName: String) {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = "OldLauncher"
        }
        val activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "$packageName.feature.home.MainActivity"
            this.applicationInfo = applicationInfo
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            intent,
            ResolveInfo().apply { this.activityInfo = activityInfo }
        )
    }

    private fun resetLauncherPreferencesSingleton() {
        val field = Class.forName("com.yinxing.launcher.data.home.LauncherPreferences")
            .getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun SettingsActivity.findDetailText(text: String): List<View> = arrayListOf<View>().also {
        findViewById<View>(R.id.settings_detail_rows)
            .findViewsWithText(it, text, View.FIND_VIEWS_WITH_TEXT)
    }

    private fun SettingsActivity.detailRows(
        containerId: Int = R.id.settings_detail_rows
    ): LinearLayout = findViewById(containerId)

    private fun View.summaryText(): String =
        findViewById<TextView>(R.id.detail_row_summary).text.toString()

    private fun assertRowsUseChevronOnly(rows: LinearLayout) {
        repeat(rows.childCount) { index ->
            val row = rows.getChildAt(index)
            assertEquals(View.GONE, row.findViewById<View>(R.id.detail_row_action).visibility)
            assertEquals(View.VISIBLE, row.findViewById<View>(R.id.detail_row_chevron).visibility)
        }
    }
}
