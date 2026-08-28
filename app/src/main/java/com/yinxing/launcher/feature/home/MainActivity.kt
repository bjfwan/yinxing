package com.yinxing.launcher.feature.home

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.yinxing.launcher.common.ui.FontScaleActivity
import com.yinxing.launcher.common.ui.AnchoredHintAlignment
import com.yinxing.launcher.common.ui.AnchoredHintPopup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.yinxing.launcher.R
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterPermissionTarget
import com.yinxing.launcher.common.lobster.LobsterSettingEventFactory
import com.yinxing.launcher.common.lobster.LobsterTrace
import com.yinxing.launcher.common.lobster.withTrace
import com.yinxing.launcher.databinding.ActivityMainBinding
import com.yinxing.launcher.data.weather.WeatherLocationResolver
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.data.weather.WeatherRepository
import com.yinxing.launcher.feature.settings.SettingsActivity
import com.yinxing.launcher.feature.settings.SettingsReturnCoordinator
import com.yinxing.launcher.feature.setup.FamilySetupActivity
import com.yinxing.launcher.feature.setup.FamilySetupPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : FontScaleActivity() {

    private val returnToDeviceSettings = Runnable {
        if (!isFinishing && !isDestroyed) {
            startActivity(SettingsActivity.deviceSettingsIntent(this))
        }
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: HomeAppAdapter
    private lateinit var itemMoveCallback: ItemMoveCallback
    private lateinit var headerController: WeatherHeaderController
    private lateinit var statusController: HomeStatusController
    private lateinit var navigator: HomeNavigator
    private lateinit var viewModel: HomeViewModel
    private val timeTicker = TimeTicker()
    private var packageReceiverRegistered = false
    private var tickerJob: Job? = null
    private var fullyDrawnReported = false
    private val weatherPreferences by lazy { WeatherPreferences.getInstance(this) }
    private var locationPermissionTraceId: String? = null
    private var initialWeatherFlowStarted = false
    private var homeResumed = false
    private var weatherAvailableForHint = false
    private var weatherDetailHintScheduled = false
    private val familySetupPreferences by lazy { FamilySetupPreferences(this) }
    private val weatherDetailHintPreferences by lazy { WeatherDetailHintPreferences(this) }
    private lateinit var weatherDetailHintPopup: AnchoredHintPopup
    private val showWeatherDetailHintRunnable = Runnable {
        weatherDetailHintScheduled = false
        showWeatherDetailHintAfterLayout()
    }
    private val familySetupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (FamilySetupPreferences(this).isCompleted()) {
            startInitialWeatherLocationFlowOnce()
        } else if (FamilySetupPreferences(this).shouldLaunchAutomatically()) {
            binding.root.post(::launchFamilySetup)
        }
    }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val traceId = locationPermissionTraceId ?: LobsterTrace.newId()
        locationPermissionTraceId = null
        LobsterClient.reportUsage(
            this,
            LobsterSettingEventFactory.permissionResult(LobsterPermissionTarget.LOCATION, granted)
                .withTrace(traceId)
        )
        if (granted) resolveInitialWeatherLocation() else viewModel.maybeRefreshWeather()
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.onPackageChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupSafeArea()
        viewModel = ViewModelProvider(this, HomeViewModel.Factory(this))[HomeViewModel::class.java]
        navigator = HomeNavigator(this)
        weatherDetailHintPopup = AnchoredHintPopup(
            activity = this,
            anchor = binding.cardWeather.root,
            textRes = R.string.home_weather_detail_hint,
            alignment = AnchoredHintAlignment.End,
            onClick = ::openWeatherDetails,
        )
        headerController = WeatherHeaderController(binding)
        statusController = HomeStatusController(
            binding = binding,
            onRetry = viewModel::refreshApps,
            onOpenSettings = navigator::showCaregiverEntryDialog
        )
        setupBackPress()
        setupRecycler()
        setupActions()
        observeViewModel()
        registerPackageReceiver()
        playEntryAnimation()
        binding.recyclerHome.post { viewModel.refreshApps() }
        if (FamilySetupPreferences(this).shouldLaunchAutomatically()) {
            binding.root.post(::launchFamilySetup)
        } else {
            startInitialWeatherLocationFlowOnce()
        }
    }

    private fun launchFamilySetup() {
        if (!isFinishing && !isDestroyed) {
            familySetupLauncher.launch(FamilySetupActivity.createIntent(this))
        }
    }

    private fun startInitialWeatherLocationFlowOnce() {
        if (initialWeatherFlowStarted) return
        initialWeatherFlowStarted = true
        binding.root.post(::startInitialWeatherLocationFlow)
    }

    private fun startInitialWeatherLocationFlow() {
        val permissionGranted = hasCoarseLocationPermission()
        when (
            initialWeatherLocationAction(
                hasCity = weatherPreferences.hasCity(),
                permissionGranted = permissionGranted,
                permissionRequested = weatherPreferences.wasInitialLocationPermissionRequested(),
            )
        ) {
            InitialWeatherLocationAction.RequestPermission -> {
                weatherPreferences.markInitialLocationPermissionRequested()
                val traceId = LobsterTrace.newId()
                locationPermissionTraceId = traceId
                LobsterClient.reportUsage(
                    this,
                    LobsterSettingEventFactory.permissionRequested(LobsterPermissionTarget.LOCATION)
                        .withTrace(traceId)
                )
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            InitialWeatherLocationAction.ResolveLocation -> resolveInitialWeatherLocation()
            InitialWeatherLocationAction.None -> Unit
        }
    }

    private fun resolveInitialWeatherLocation() {
        lifecycleScope.launch {
            val location = WeatherLocationResolver.resolve(this@MainActivity)
            if (location == null) {
                viewModel.maybeRefreshWeather()
                return@launch
            }
            weatherPreferences.setCurrentLocation(
                location.cityName,
                location.latitude,
                location.longitude,
            )
            WeatherRepository.clearCache()
            viewModel.refreshWeatherNow()
        }
    }

    private fun hasCoarseLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun setupSafeArea() {
        val baseTopPadding = binding.root.paddingTop
        val baseBottomPadding = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                top = baseTopPadding + systemInsets.top,
                bottom = baseBottomPadding + systemInsets.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        homeResumed = true
        if (SettingsReturnCoordinator.consumeDeviceSettingsReturn(this)) {
            binding.root.postDelayed(returnToDeviceSettings, DEFAULT_LAUNCHER_SETTLE_DELAY_MS)
        }
        startTimeTicker()
        if (
            shouldRefreshWeatherOnResume(
                hasCity = weatherPreferences.hasCity(),
                permissionGranted = hasCoarseLocationPermission(),
                permissionRequested = weatherPreferences.wasInitialLocationPermissionRequested(),
            )
        ) {
            viewModel.maybeRefreshWeather()
        }
        maybeShowWeatherDetailHint()
    }

    override fun onPause() {
        homeResumed = false
        weatherDetailHintScheduled = false
        binding.cardWeather.root.removeCallbacks(showWeatherDetailHintRunnable)
        weatherDetailHintPopup.dismiss()
        tickerJob?.cancel()
        tickerJob = null
        viewModel.cancelPendingWeatherRefresh()
        super.onPause()
    }

    override fun onDestroy() {
        binding.root.removeCallbacks(returnToDeviceSettings)
        binding.cardWeather.root.removeCallbacks(showWeatherDetailHintRunnable)
        weatherDetailHintPopup.dismiss()
        if (packageReceiverRegistered) {
            unregisterReceiver(packageChangeReceiver)
        }
        super.onDestroy()
    }

    companion object {
        private const val DEFAULT_LAUNCHER_SETTLE_DELAY_MS = 300L
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.home_toast_already_here),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun setupRecycler() {
        val settings = viewModel.settings.value
        adapter = HomeAppAdapter(
            scope = lifecycleScope,
            lowPerformanceMode = settings.lowPerformanceMode,
            iconScale = settings.iconScale,
            homeLayoutLocked = settings.homeLayoutLocked,
            homeLongPressResponse = settings.homeLongPressResponse,
            onItemClick = navigator::openHomeItem,
            onOrderChanged = { items ->
                viewModel.saveAppOrder(items)
                LobsterClient.reportUsage(
                    this,
                    LobsterSettingEventFactory.homeAppsReordered().withTrace(LobsterTrace.newId())
                )
            }
        )
        binding.recyclerHome.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerHome.setHasFixedSize(false)
        binding.recyclerHome.adapter = adapter
        itemMoveCallback = ItemMoveCallback(adapter, !settings.lowPerformanceMode)
        ItemTouchHelper(itemMoveCallback).also {
            it.attachToRecyclerView(binding.recyclerHome)
            adapter.setTouchHelper(it)
        }
        adapter.submitList(viewModel.homeUiState.value.items)
        applySettings(settings)
    }

    private fun setupActions() {
        binding.cardWeather.root.setOnClickListener { openWeatherDetails() }
        binding.btnFamilySettings.setOnClickListener { navigator.showCaregiverEntryDialog() }
        binding.btnAdjustHome.setOnClickListener { navigator.openAppManager() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.homeUiState.collect(::renderHomeState)
        }
        lifecycleScope.launch {
            viewModel.settings.collect(::applySettings)
        }
        lifecycleScope.launch {
            viewModel.weatherState.collect { state ->
                state?.let {
                    headerController.renderWeather(it)
                    weatherAvailableForHint = it.now != null
                    maybeShowWeatherDetailHint()
                }
            }
        }
    }

    private fun openWeatherDetails() {
        weatherDetailHintPopup.dismiss()
        navigator.openWeatherEntry()
    }

    private fun maybeShowWeatherDetailHint() {
        if (
            !shouldRevealWeatherDetailHint(
                weatherAvailable = weatherAvailableForHint,
                hostResumed = homeResumed,
                familySetupPending = familySetupPreferences.shouldLaunchAutomatically(),
                alreadyShown = weatherDetailHintPreferences.hasBeenShown(),
            ) || weatherDetailHintScheduled || weatherDetailHintPopup.isShowing
        ) {
            return
        }
        weatherDetailHintScheduled = true
        binding.cardWeather.root.post(showWeatherDetailHintRunnable)
    }

    private fun showWeatherDetailHintAfterLayout() {
        if (
            !shouldRevealWeatherDetailHint(
                weatherAvailable = weatherAvailableForHint,
                hostResumed = homeResumed,
                familySetupPending = familySetupPreferences.shouldLaunchAutomatically(),
                alreadyShown = weatherDetailHintPreferences.hasBeenShown(),
            ) || !binding.cardWeather.root.isAttachedToWindow || binding.cardWeather.root.width == 0
        ) {
            return
        }
        if (weatherDetailHintPreferences.markShownIfFirstTime()) {
            weatherDetailHintPopup.show(viewModel.settings.value.lowPerformanceMode)
        }
    }

    private fun renderHomeState(state: HomeUiState) {
        adapter.submitList(state.items) {
            maybeReportFullyDrawn(state)
        }
        statusController.render(state)
    }

    private fun applySettings(settings: HomeSettingsState) {
        binding.recyclerHome.setItemViewCacheSize(if (settings.lowPerformanceMode) 4 else 10)
        binding.recyclerHome.itemAnimator = if (settings.lowPerformanceMode) null else DefaultItemAnimator()
        adapter.setLowPerformanceMode(settings.lowPerformanceMode)
        adapter.setIconScale(settings.iconScale)
        adapter.setHomeLayoutLocked(settings.homeLayoutLocked)
        adapter.setHomeLongPressResponse(settings.homeLongPressResponse)
        itemMoveCallback.setAnimateDrag(!settings.lowPerformanceMode)
        headerController.applyScale(settings.iconScale)
    }

    private fun startTimeTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            timeTicker.run { snapshot ->
                headerController.renderTime(snapshot, viewModel.settings.value.lowPerformanceMode)
            }
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageChangeReceiver, filter)
        }
        packageReceiverRegistered = true
    }

    private fun maybeReportFullyDrawn(state: HomeUiState) {
        if (fullyDrawnReported || state is HomeUiState.Loading) {
            return
        }
        fullyDrawnReported = true
        binding.recyclerHome.post {
            reportFullyDrawn()
        }
    }

    private fun playEntryAnimation() {
        if (viewModel.settings.value.lowPerformanceMode) {
            return
        }
        binding.layoutHomeRoot.alpha = 0f
        binding.layoutHomeRoot.translationY = 18f
        binding.layoutHomeRoot.post {
            binding.layoutHomeRoot.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}

internal enum class InitialWeatherLocationAction {
    None,
    RequestPermission,
    ResolveLocation,
}

internal fun initialWeatherLocationAction(
    hasCity: Boolean,
    permissionGranted: Boolean,
    permissionRequested: Boolean,
): InitialWeatherLocationAction = when {
    hasCity -> InitialWeatherLocationAction.None
    permissionGranted -> InitialWeatherLocationAction.ResolveLocation
    !permissionRequested -> InitialWeatherLocationAction.RequestPermission
    else -> InitialWeatherLocationAction.None
}

internal fun shouldRefreshWeatherOnResume(
    hasCity: Boolean,
    permissionGranted: Boolean,
    permissionRequested: Boolean,
): Boolean = hasCity || permissionRequested && !permissionGranted
