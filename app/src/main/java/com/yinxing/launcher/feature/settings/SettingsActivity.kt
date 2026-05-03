package com.yinxing.launcher.feature.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

    internal lateinit var binding: SettingsViewBinding
    internal val runtime = SettingsRuntimeState()

    // 以下字段代理到 [binding]/[runtime]，保证现有扩展函数 零修改：
    internal val tvIncomingGuardStatus: TextView get() = binding.tvIncomingGuardStatus
    internal val tvIncomingGuardProgress: TextView get() = binding.tvIncomingGuardProgress
    internal val tvIncomingGuardSummary: TextView get() = binding.tvIncomingGuardSummary
    internal val tvIncomingGuardAction: TextView get() = binding.tvIncomingGuardAction
    internal val btnIncomingGuardAction: View get() = binding.btnIncomingGuardAction
    internal val tvContactsHubSummary: TextView get() = binding.tvContactsHubSummary
    internal val tvAutoAnswerHubStatus: TextView get() = binding.tvAutoAnswerHubStatus
    internal val tvAutoAnswerHubSummary: TextView get() = binding.tvAutoAnswerHubSummary
    internal val tvPermissionHubStatus: TextView get() = binding.tvPermissionHubStatus
    internal val tvPermissionHubSummary: TextView get() = binding.tvPermissionHubSummary
    internal val tvDeviceHubStatus: TextView get() = binding.tvDeviceHubStatus
    internal val tvDeviceHubSummary: TextView get() = binding.tvDeviceHubSummary
    internal val tvSystemHubSummary: TextView get() = binding.tvSystemHubSummary

    internal var incomingGuardReadiness: IncomingGuardReadiness
        get() = runtime.incomingGuardReadiness
        set(value) { runtime.incomingGuardReadiness = value }

    internal var permissionEntryStates: Map<PermissionEntry, PermissionEntryState>
        get() = runtime.permissionEntryStates
        set(value) { runtime.permissionEntryStates = value }

    internal var contactsSummaryJob: Job?
        get() = runtime.contactsSummaryJob
        set(value) { runtime.contactsSummaryJob = value }

    /**
     * 代替原先的 [android.os.Handler] + [Runnable] debouncing。
     * Activity 任何地方 emit 一次，收到多个事件后仅需要在下一个帧跑一次 [SettingsOverviewController.performOverviewRefresh]。
     */
    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

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
        binding = SettingsViewBinding(this)

        overviewController = SettingsOverviewController(this)
        actionController = SettingsActionController(this)
        sheetController = SettingsSheetController(this)

        overviewController.bindActions(
            onBack = ::finish,
            onShowIncomingGuardSheet = sheetController::showIncomingGuardSheet,
            onShowContactsSheet = sheetController::showContactsSheet,
            onShowAutoAnswerSheet = sheetController::showAutoAnswerSheet,
            onShowPermissionGroupsSheet = sheetController::showPermissionGroupsSheet,
            onShowDeviceSettingsSheet = sheetController::showDeviceSettingsSheet,
            onShowSystemSheet = sheetController::showSystemSheet
        )
        sheetController.playEntryAnimation()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                refreshSignal.collectLatest {
                    overviewController.performOverviewRefresh()
                }
            }
        }
    }

    /** 后台请求下一帧刷新；collectLatest 会合并连续事件。 */
    internal fun postOverviewRefresh() {
        refreshSignal.tryEmit(Unit)
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
