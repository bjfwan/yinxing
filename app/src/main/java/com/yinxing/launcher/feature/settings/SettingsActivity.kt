package com.yinxing.launcher.feature.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ai.AiDeviceCredentials
import com.yinxing.launcher.common.ai.AiProStatusClient
import com.yinxing.launcher.data.home.LauncherPreferences
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.feature.incoming.IncomingGuardReadiness
import kotlinx.coroutines.Job

class SettingsActivity : AppCompatActivity() {

    internal lateinit var launcherPreferences: LauncherPreferences
    internal lateinit var weatherPreferences: WeatherPreferences
    internal lateinit var aiDeviceCredentials: AiDeviceCredentials
    internal lateinit var aiProStatusClient: AiProStatusClient

    internal lateinit var tvIncomingGuardStatus: TextView
    internal lateinit var tvIncomingGuardProgress: TextView
    internal lateinit var tvIncomingGuardSummary: TextView
    internal lateinit var tvIncomingGuardAction: TextView
    internal lateinit var btnIncomingGuardAction: View

    internal lateinit var tvContactsHubSummary: TextView
    internal lateinit var tvAiProHubStatus: TextView
    internal lateinit var tvAiProHubSummary: TextView
    internal lateinit var tvAutoAnswerHubStatus: TextView
    internal lateinit var tvAutoAnswerHubSummary: TextView
    internal lateinit var tvPermissionHubStatus: TextView
    internal lateinit var tvPermissionHubSummary: TextView
    internal lateinit var tvDeviceHubStatus: TextView
    internal lateinit var tvDeviceHubSummary: TextView
    internal lateinit var tvSystemHubSummary: TextView

    internal var incomingGuardReadiness = IncomingGuardReadiness(emptyList())
    internal var permissionEntryStates = emptyMap<PermissionEntry, PermissionEntryState>()
    internal var contactsSummaryJob: Job? = null
    internal var aiProStatusJob: Job? = null
    internal val mainHandler = Handler(Looper.getMainLooper())
    internal var overviewRefreshQueued = false
    internal val overviewRefreshRunnable = Runnable {
        overviewRefreshQueued = false
        overviewController.performOverviewRefresh()
    }

    internal lateinit var overviewController: SettingsOverviewController
    internal lateinit var sheetController: SettingsSheetController
    internal lateinit var actionController: SettingsActionController

    internal val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> actionController.onPhonePermissionResult(results) }

    internal val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> actionController.onNotificationPermissionResult(granted) }

    internal val defaultLauncherRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { actionController.onDefaultLauncherRoleResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        launcherPreferences = LauncherPreferences.getInstance(this)
        weatherPreferences = WeatherPreferences.getInstance(this)
        aiDeviceCredentials = AiDeviceCredentials.getInstance(this)
        aiProStatusClient = AiProStatusClient(this)

        overviewController = SettingsOverviewController(this)
        actionController = SettingsActionController(this)
        sheetController = SettingsSheetController(this)

        overviewController.bindViews()
        overviewController.bindActions(
            onBack = ::finish,
            onShowIncomingGuardSheet = sheetController::showIncomingGuardSheet,
            onShowContactsSheet = sheetController::showContactsSheet,
            onShowAiProSheet = sheetController::showAiProSheet,
            onShowAutoAnswerSheet = sheetController::showAutoAnswerSheet,
            onShowPermissionGroupsSheet = sheetController::showPermissionGroupsSheet,
            onShowDeviceSettingsSheet = sheetController::showDeviceSettingsSheet,
            onShowSystemSheet = sheetController::showSystemSheet
        )
        sheetController.playEntryAnimation()
    }

    override fun onResume() {
        super.onResume()
        overviewController.refreshOverviewUi()
    }

    override fun onDestroy() {
        overviewController.onDestroy()
        super.onDestroy()
    }
}
