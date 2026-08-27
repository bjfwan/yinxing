package com.yinxing.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yinxing.launcher.R
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.feature.incoming.IncomingGuardReadiness
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    internal lateinit var launcherPreferences: LauncherPreferences
    internal lateinit var weatherPreferences: WeatherPreferences

    internal val runtime = SettingsRuntimeState()

    internal val tvIncomingGuardStatus: TextView get() = findViewById(R.id.tv_incoming_guard_status)
    internal val tvIncomingGuardProgress: TextView get() = findViewById(R.id.tv_incoming_guard_progress)
    internal val tvIncomingGuardSummary: TextView get() = findViewById(R.id.tv_incoming_guard_summary)
    internal val tvIncomingGuardAction: TextView get() = findViewById(R.id.tv_incoming_guard_action)
    internal val btnIncomingGuardAction: View get() = findViewById(R.id.btn_incoming_guard_action)
    internal val tvAutoAnswerHubStatus: TextView get() = findViewById(R.id.tv_auto_answer_hub_status)
    internal val tvAutoAnswerHubSummary: TextView get() = findViewById(R.id.tv_auto_answer_hub_summary)

    internal var incomingGuardReadiness: IncomingGuardReadiness
        get() = runtime.incomingGuardReadiness
        set(value) { runtime.incomingGuardReadiness = value }

    internal var permissionEntryStates: Map<PermissionEntry, PermissionEntryState>
        get() = runtime.permissionEntryStates
        set(value) { runtime.permissionEntryStates = value }

    internal var contactsSummaryJob: Job?
        get() = runtime.contactsSummaryJob
        set(value) { runtime.contactsSummaryJob = value }

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    internal lateinit var overviewController: SettingsOverviewController
    internal lateinit var dialogController: SettingsDialogController
    internal lateinit var actionController: SettingsActionController
    internal lateinit var screenController: SettingsScreenController
    internal lateinit var detailController: SettingsDetailController
    internal var currentScreen = SettingsScreen.StandardOverview
        private set

    internal val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> actionController.onPhonePermissionResult(results) }

    internal val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> actionController.onNotificationPermissionResult(granted) }

    internal val defaultLauncherRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { scheduleDefaultLauncherRefresh() }

    internal val defaultPhoneRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { actionController.onDefaultPhoneRoleResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        launcherPreferences = LauncherPreferences.getInstance(this)
        weatherPreferences = WeatherPreferences.getInstance(this)

        overviewController = SettingsOverviewController(this)
        actionController = SettingsActionController(this)
        dialogController = SettingsDialogController(this)
        screenController = SettingsScreenController(this)
        detailController = SettingsDetailController(this)

        overviewController.bindActions(
            onBack = ::finish,
            onShowIncomingGuard = dialogController::showIncomingGuardDialog,
            onShowContacts = { showScreen(SettingsScreen.Contacts) },
            onShowCalls = { showScreen(SettingsScreen.Calls) },
            onShowPermissions = { showScreen(SettingsScreen.Permissions) },
            onShowDevice = { showScreen(SettingsScreen.Device) },
            onShowSystem = { showScreen(SettingsScreen.System) }
        )
        screenController.bindStandard()
        dialogController.playEntryAnimation()
        applySystemInsets()

        onBackPressedDispatcher.addCallback(this) {
            if (currentScreen == SettingsScreen.StandardOverview) finish()
            else showScreen(SettingsScreen.StandardOverview)
        }

        currentScreen = SettingsScreen.from(
            intent.getStringExtra(EXTRA_MODE),
            intent.getStringExtra(EXTRA_SECTION)
        )
        if (currentScreen != SettingsScreen.StandardOverview) showScreen(currentScreen)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                refreshSignal.collectLatest {
                    overviewController.performOverviewRefresh()
                    screenController.refreshActive()
                }
            }
        }
    }

    internal fun postOverviewRefresh() {
        refreshSignal.tryEmit(Unit)
    }

    override fun onResume() {
        super.onResume()
        SettingsReturnCoordinator.consumeDeviceSettingsReturn(this)
        actionController.continueDefaultPhoneRoleIfReady()
        overviewController.refreshOverviewUi()
        if (currentScreen !in setOf(SettingsScreen.StandardOverview, SettingsScreen.ElderOverview)) {
            detailController.bind(currentScreen)
        }
    }

    private fun scheduleDefaultLauncherRefresh() {
        window.decorView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            overviewController.refreshOverviewUi()
            if (currentScreen !in setOf(SettingsScreen.StandardOverview, SettingsScreen.ElderOverview)) {
                detailController.bind(currentScreen)
            }
        }, DEFAULT_LAUNCHER_SETTLE_DELAY_MS)
    }

    override fun onDestroy() {
        overviewController.onDestroy()
        super.onDestroy()
    }

    internal fun showScreen(screen: SettingsScreen) {
        currentScreen = screen
        val overlay = findViewById<FrameLayout>(R.id.settings_overlay)
        findViewById<TextView>(R.id.settings_page_title).text = getString(screen.titleRes)
        if (screen == SettingsScreen.StandardOverview) {
            overlay.removeAllViews()
            overlay.visibility = View.GONE
            screenController.refreshActive()
            return
        }

        overlay.removeAllViews()
        overlay.visibility = View.VISIBLE
        when (screen) {
            SettingsScreen.ElderOverview -> {
                layoutInflater.inflate(R.layout.screen_settings_elder, overlay, true)
                screenController.bindElder()
            }
            else -> {
                layoutInflater.inflate(R.layout.screen_settings_detail, overlay, true)
                detailController.bind(screen)
            }
        }
        screenController.refreshActive()
    }

    private fun applySystemInsets() {
        val root = findViewById<View>(R.id.settings_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    companion object {
        private const val DEFAULT_LAUNCHER_SETTLE_DELAY_MS = 300L
        const val EXTRA_MODE = "settings_mode"
        const val EXTRA_SECTION = "settings_section"

        internal fun deviceSettingsIntent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
                .putExtra(EXTRA_SECTION, SettingsScreen.Device.key)
        }
    }
}
