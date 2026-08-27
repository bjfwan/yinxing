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
import androidx.appcompat.app.AppCompatActivity
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
import com.yinxing.launcher.databinding.ActivityMainBinding
import com.yinxing.launcher.data.weather.WeatherLocationResolver
import com.yinxing.launcher.data.weather.WeatherPreferences
import com.yinxing.launcher.data.weather.WeatherRepository
import com.yinxing.launcher.feature.settings.SettingsActivity
import com.yinxing.launcher.feature.settings.SettingsReturnCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

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
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
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
    }

    override fun onPause() {
        tickerJob?.cancel()
        tickerJob = null
        viewModel.cancelPendingWeatherRefresh()
        super.onPause()
    }

    override fun onDestroy() {
        binding.root.removeCallbacks(returnToDeviceSettings)
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
            onItemClick = navigator::openHomeItem,
            onOrderChanged = viewModel::saveAppOrder
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
        binding.cardWeather.root.setOnClickListener { navigator.openWeatherEntry() }
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
                state?.let(headerController::renderWeather)
            }
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
