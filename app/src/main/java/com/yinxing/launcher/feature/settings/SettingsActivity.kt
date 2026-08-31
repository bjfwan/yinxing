package com.yinxing.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.addCallback
import com.yinxing.launcher.common.ui.FontScaleActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yinxing.launcher.R
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterSettingEventFactory
import com.yinxing.launcher.common.lobster.LobsterTrace
import com.yinxing.launcher.common.lobster.withTrace
import com.yinxing.launcher.common.util.HomeRedirectPreferences
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.feature.incoming.IncomingGuardReadiness
import com.yinxing.launcher.feature.setup.FamilySetupActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : FontScaleActivity() {

    internal lateinit var launcherPreferences: LauncherPreferences
    internal lateinit var weatherPreferences: WeatherPreferences
    internal lateinit var homeRedirectPreferences: HomeRedirectPreferences

    internal val runtime = SettingsRuntimeState()

    internal val tvIncomingGuardStatus: TextView get() = findViewById(R.id.tv_incoming_guard_status)
    internal val tvIncomingGuardProgress: TextView get() = findViewById(R.id.tv_incoming_guard_progress)
    internal val tvIncomingGuardSummary: TextView get() = findViewById(R.id.tv_incoming_guard_summary)
    internal val tvIncomingGuardAction: TextView get() = findViewById(R.id.tv_incoming_guard_action)
    internal val btnIncomingGuardAction: View get() = findViewById(R.id.btn_incoming_guard_action)
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
        homeRedirectPreferences = HomeRedirectPreferences(this)

        overviewController = SettingsOverviewController(this)
        actionController = SettingsActionController(this)
        dialogController = SettingsDialogController(this)
        screenController = SettingsScreenController(this)
        detailController = SettingsDetailController(this)

        overviewController.bindActions(
            onBack = ::finish,
            onShowFamilySetup = {
                startActivity(FamilySetupActivity.createIntent(this))
            },
            onShowIncomingGuard = dialogController::showIncomingGuardDialog,
            onShowContacts = { showScreen(SettingsScreen.Contacts) },
            onShowCalls = { showScreen(SettingsScreen.Calls) },
            onShowDiagnostics = { showScreen(SettingsScreen.CallDiagnostics) },
            onShowSafety = { showScreen(SettingsScreen.Safety) },
            onShowPermissions = { showScreen(SettingsScreen.Permissions) },
            onShowBackground = { showScreen(SettingsScreen.Background) },
            onShowDevice = { showScreen(SettingsScreen.Device) },
            onShowDisplay = { showScreen(SettingsScreen.Display) },
            onShowWeather = { showScreen(SettingsScreen.Weather) },
            onShowSystem = { showScreen(SettingsScreen.System) },
            onShowAbout = { showScreen(SettingsScreen.About) }
        )
        screenController.bindStandard()
        dialogController.playEntryAnimation()
        applySystemInsets()

        onBackPressedDispatcher.addCallback(this) {
            navigateBack()
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
        if (runtime.awaitingDefaultLauncherResult) scheduleDefaultLauncherRefresh()
        actionController.continueDefaultPhoneRoleIfReady()
        overviewController.refreshOverviewUi()
        if (currentScreen !in setOf(SettingsScreen.StandardOverview, SettingsScreen.ElderOverview)) {
            detailController.bind(currentScreen)
        }
    }

    private fun scheduleDefaultLauncherRefresh() {
        window.decorView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            if (runtime.awaitingDefaultLauncherResult) {
                runtime.awaitingDefaultLauncherResult = false
                val traceId = runtime.defaultLauncherTraceId ?: LobsterTrace.newId()
                runtime.defaultLauncherTraceId = null
                LobsterClient.reportUsage(
                    this,
                    LobsterSettingEventFactory.defaultLauncherResult(isDefaultLauncher())
                        .withTrace(traceId)
                )
            }
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
        LobsterClient.reportUsage(this, LobsterSettingEventFactory.screenOpened(screen.name))
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

    internal fun navigateBack() {
        if (currentScreen == SettingsScreen.WeChatRules) {
            showScreen(SettingsScreen.Contacts)
            return
        }
        if (
            currentScreen == SettingsScreen.StandardOverview ||
            intent.getBooleanExtra(EXTRA_RETURN_TO_CALLER, false)
        ) {
            finish()
        } else {
            showScreen(SettingsScreen.StandardOverview)
        }
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
        const val EXTRA_RETURN_TO_CALLER = "settings_return_to_caller"

        internal fun deviceSettingsIntent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
                .putExtra(EXTRA_SECTION, SettingsScreen.Device.key)
        }
    }
}
