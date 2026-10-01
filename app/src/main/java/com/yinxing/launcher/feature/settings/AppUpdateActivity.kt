package com.yinxing.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.BuildConfig
import com.yinxing.launcher.R
import com.yinxing.launcher.common.lobster.LobsterClient
import com.yinxing.launcher.common.lobster.LobsterSettingEventFactory
import com.yinxing.launcher.common.ui.FontScaleActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class AppUpdateActivity : FontScaleActivity() {

    private enum class Phase {
        CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, DOWNLOADED, INSTALL_BLOCKED, FAILED
    }

    private lateinit var store: AppUpdateStore
    private var updateInfo: AppUpdateInfo? = null
    private var phase = Phase.CHECKING
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var installLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_update)
        store = AppUpdateStore(this)

        findViewById<View>(R.id.app_update_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tv_version_name).text = "v${BuildConfig.VERSION_NAME}"
        findViewById<TextView>(R.id.tv_version_code).text = BuildConfig.VERSION_CODE.toString()

        findViewById<View>(R.id.btn_update_action).setOnClickListener { onAction() }
        applySystemInsets()
        performCheck()
    }

    override fun onResume() {
        super.onResume()
        val info = updateInfo ?: return
        if (phase == Phase.INSTALL_BLOCKED && canInstallPackages()) {
            launchInstall(info)
            return
        }
        if (installLaunched && BuildConfig.VERSION_CODE < info.versionCode) {
            installLaunched = false
            findViewById<TextView>(R.id.tv_update_hint).setText(R.string.settings_update_install_pending)
        }
    }

    private fun performCheck() {
        setPhase(Phase.CHECKING)
        checkJob?.cancel()
        checkJob = lifecycleScope.launch {
            when (val state = AppUpdateChecker().check()) {
                is AppUpdateState.Available -> {
                    store.saveAvailable(state.info)
                    LobsterClient.reportUsage(
                        this@AppUpdateActivity,
                        LobsterSettingEventFactory.appUpdateCheckResult(foundUpdate = true)
                    )
                    updateInfo = state.info
                    if (AppUpdateDownloader.isDownloaded(this@AppUpdateActivity, state.info)) {
                        setPhase(Phase.DOWNLOADED)
                    } else {
                        setPhase(Phase.AVAILABLE)
                    }
                }
                is AppUpdateState.UpToDate -> {
                    LobsterClient.reportUsage(
                        this@AppUpdateActivity,
                        LobsterSettingEventFactory.appUpdateCheckResult(foundUpdate = false)
                    )
                    updateInfo = null
                    showAnnouncement(state.announcementTitle, state.announcementBody)
                    setPhase(Phase.UP_TO_DATE)
                }
                is AppUpdateState.Failed -> {
                    LobsterClient.reportUsage(
                        this@AppUpdateActivity,
                        LobsterSettingEventFactory.appUpdateFailed(
                            step = "check_update",
                            errorCode = "UPDATE_CHECK_FAILED"
                        )
                    )
                    updateInfo = null
                    setPhase(Phase.FAILED)
                }
            }
        }
    }

    private fun onAction() {
        when (phase) {
            Phase.CHECKING, Phase.DOWNLOADING -> Unit
            Phase.AVAILABLE -> startDownload()
            Phase.DOWNLOADED -> prepareInstall()
            Phase.INSTALL_BLOCKED -> openInstallPermissionSettings()
            Phase.UP_TO_DATE -> performCheck()
            Phase.FAILED -> if (updateInfo == null) performCheck() else startDownload()
        }
    }

    private fun startDownload() {
        val info = updateInfo ?: return
        setPhase(Phase.DOWNLOADING)
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            val result = try {
                AppUpdateDownloader.download(this@AppUpdateActivity, info) { downloaded, total ->
                    runOnUiThread { renderProgress(downloaded, total) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppUpdateDownloader.Result.Failure(e.message ?: "unknown")
            }
            when (result) {
                is AppUpdateDownloader.Result.Success -> {
                    LobsterClient.reportUsage(
                        this@AppUpdateActivity,
                        LobsterSettingEventFactory.appUpdateDownloaded()
                    )
                    setPhase(Phase.DOWNLOADED)
                }
                is AppUpdateDownloader.Result.Failure -> {
                    LobsterClient.reportUsage(
                        this@AppUpdateActivity,
                        LobsterSettingEventFactory.appUpdateFailed(
                            step = "download_update",
                            errorCode = "UPDATE_DOWNLOAD_FAILED"
                        )
                    )
                    setPhase(Phase.FAILED)
                }
            }
        }
    }

    private fun prepareInstall() {
        val info = updateInfo ?: return
        if (!canInstallPackages()) {
            LobsterClient.reportUsage(
                this,
                LobsterSettingEventFactory.appUpdateFailed(
                    step = "install_update",
                    errorCode = "UPDATE_INSTALL_BLOCKED"
                )
            )
            setPhase(Phase.INSTALL_BLOCKED)
            return
        }
        launchInstall(info)
    }

    private fun launchInstall(info: AppUpdateInfo) {
        val file: File = AppUpdateDownloader.targetFile(this, info.versionCode)
        if (!file.isFile) {
            setPhase(Phase.AVAILABLE)
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onSuccess {
                installLaunched = true
                LobsterClient.reportUsage(
                    this,
                    LobsterSettingEventFactory.appUpdateInstallLaunched()
                )
                findViewById<TextView>(R.id.tv_update_hint)
                    .setText(R.string.settings_update_install_system_hint)
            }
            .onFailure {
                LobsterClient.reportUsage(
                    this,
                    LobsterSettingEventFactory.appUpdateFailed(
                        step = "install_update",
                        errorCode = "UPDATE_INSTALL_FAILED"
                    )
                )
                findViewById<TextView>(R.id.tv_update_hint)
                    .setText(R.string.settings_update_open_failed)
            }
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun openInstallPermissionSettings() {
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
            .onFailure {
                runCatching {
                    startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
                }
            }
    }

    private fun showAnnouncement(title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return
        findViewById<View>(R.id.update_announcement_card).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_update_announcement_title).text =
            title.ifBlank { getString(R.string.settings_update_announcement_title) }
        findViewById<TextView>(R.id.tv_update_announcement_body).text = body
    }

    private fun setPhase(next: Phase) {
        phase = next
        val status = findViewById<TextView>(R.id.tv_version_update_status)
        val latest = findViewById<TextView>(R.id.tv_update_latest)
        val notesCard = findViewById<View>(R.id.update_notes_card)
        val progressBar = findViewById<ProgressBar>(R.id.update_progress)
        val progressText = findViewById<TextView>(R.id.tv_update_progress)
        val hint = findViewById<TextView>(R.id.tv_update_hint)
        val actionButton = findViewById<View>(R.id.btn_update_action)
        val actionLabel = findViewById<TextView>(R.id.tv_update_action_label)

        val info = updateInfo
        latest.text = info?.let { "v${it.versionName}" }
            ?: getString(R.string.settings_update_latest_version_unknown)
        notesCard.visibility = if (info != null) View.VISIBLE else View.GONE
        info?.let {
            findViewById<TextView>(R.id.tv_update_notes).text = it.releaseNotes.ifBlank {
                getString(R.string.settings_update_available_message)
            }
            showAnnouncement(it.announcementTitle, it.announcementBody)
        }

        when (next) {
            Phase.CHECKING -> {
                status.setText(R.string.settings_update_checking)
                progressBar.visibility = View.GONE
                progressText.visibility = View.GONE
                hint.setText(R.string.settings_update_caregiver_hint)
                actionLabel.setText(R.string.settings_update_checking)
                actionButton.isEnabled = false
                actionButton.alpha = 0.65f
            }
            Phase.UP_TO_DATE -> {
                status.setText(R.string.settings_update_latest)
                progressBar.visibility = View.GONE
                progressText.visibility = View.GONE
                hint.setText(R.string.settings_update_caregiver_hint)
                actionLabel.setText(R.string.settings_update_recheck)
                actionButton.isEnabled = true
                actionButton.alpha = 1f
            }
            Phase.AVAILABLE -> {
                status.setText(
                    if (info?.isForced(BuildConfig.VERSION_CODE) == true) {
                        R.string.settings_update_found_important
                    } else {
                        R.string.settings_update_found_new_short
                    }
                )
                progressBar.visibility = View.GONE
                progressText.visibility = View.GONE
                hint.setText(R.string.settings_update_download_hint)
                actionLabel.setText(R.string.settings_update_download)
                actionButton.isEnabled = true
                actionButton.alpha = 1f
            }
            Phase.DOWNLOADING -> {
                status.setText(R.string.settings_update_downloading_status)
                progressBar.visibility = View.VISIBLE
                progressText.visibility = View.VISIBLE
                hint.setText(R.string.settings_update_download_hint)
                actionLabel.setText(R.string.settings_update_downloading_status)
                actionButton.isEnabled = false
                actionButton.alpha = 0.65f
            }
            Phase.DOWNLOADED -> {
                status.setText(R.string.settings_update_downloaded_status)
                progressBar.visibility = View.GONE
                progressText.visibility = View.GONE
                hint.setText(R.string.settings_update_install_hint)
                actionLabel.setText(R.string.settings_update_install)
                actionButton.isEnabled = true
                actionButton.alpha = 1f
            }
            Phase.INSTALL_BLOCKED -> {
                status.setText(R.string.settings_update_install_blocked_status)
                progressBar.visibility = View.GONE
                progressText.visibility = View.GONE
                hint.setText(R.string.settings_update_install_blocked_hint)
                actionLabel.setText(R.string.settings_update_open_install_settings)
                actionButton.isEnabled = true
                actionButton.alpha = 1f
            }
            Phase.FAILED -> {
                status.setText(
                    if (info == null) R.string.settings_update_failed
                    else R.string.settings_update_download_failed
                )
                progressBar.visibility = View.GONE
                progressText.visibility = View.GONE
                hint.setText(R.string.settings_update_retry_hint)
                actionLabel.setText(R.string.settings_update_retry)
                actionButton.isEnabled = true
                actionButton.alpha = 1f
            }
        }
    }

    private fun renderProgress(downloaded: Long, total: Long) {
        val progressBar = findViewById<ProgressBar>(R.id.update_progress)
        val progressText = findViewById<TextView>(R.id.tv_update_progress)
        if (total > 0) {
            val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
            progressBar.isIndeterminate = false
            progressBar.progress = percent
            progressText.text = getString(R.string.settings_update_downloading, percent)
        } else {
            progressBar.isIndeterminate = true
            progressText.text = getString(
                R.string.settings_update_downloading_size,
                downloaded / 1024f / 1024f
            )
        }
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.app_update_root)) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, AppUpdateActivity::class.java)
    }
}
