package com.yinxing.launcher.feature.setup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ui.FontScaleActivity
import com.yinxing.launcher.common.util.PermissionUtil
import com.yinxing.launcher.databinding.ActivityFamilySetupBinding
import com.yinxing.launcher.feature.phone.PhoneContactActivity
import com.yinxing.launcher.feature.phone.PhoneContactManager
import com.yinxing.launcher.feature.settings.SettingsActivity
import com.yinxing.launcher.feature.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FamilySetupActivity : FontScaleActivity() {
    private lateinit var binding: ActivityFamilySetupBinding
    private lateinit var setupPreferences: FamilySetupPreferences
    private var phoneContactCount: Int? = null
    private var contactRefreshJob: Job? = null
    private var currentReadiness = familySetupReadiness(
        phoneContactCount = 0,
        phonePermissionGranted = false,
        defaultLauncher = false,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFamilySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupPreferences = FamilySetupPreferences(this)
        applySystemInsets()
        bindActions()
        onBackPressedDispatcher.addCallback(this) {
            if (setupPreferences.isCompleted()) finish() else moveTaskToBack(true)
        }
        renderChecklist()
    }

    override fun onResume() {
        super.onResume()
        updateReadiness()
        refreshContactCount()
    }

    override fun onDestroy() {
        contactRefreshJob?.cancel()
        super.onDestroy()
    }

    private fun bindActions() {
        binding.btnSetupContacts.setOnClickListener {
            startActivity(PhoneContactActivity.createIntent(this, startInManageMode = true))
        }
        binding.btnSetupPermission.setOnClickListener {
            openSettings(SettingsScreen.Permissions)
        }
        binding.btnSetupLauncher.setOnClickListener {
            openSettings(SettingsScreen.Device)
        }
        binding.btnSetupFinish.setOnClickListener {
            if (currentReadiness.canFinish) completeSetup()
        }
    }

    private fun refreshContactCount() {
        contactRefreshJob?.cancel()
        contactRefreshJob = lifecycleScope.launch {
            phoneContactCount = runCatching {
                withContext(Dispatchers.IO) {
                    PhoneContactManager.getInstance(this@FamilySetupActivity).getContactCount()
                }
            }.getOrNull()
            updateReadiness()
        }
    }

    private fun updateReadiness() {
        currentReadiness = familySetupReadiness(
            phoneContactCount = phoneContactCount ?: 0,
            phonePermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE,
            ) == PackageManager.PERMISSION_GRANTED,
            defaultLauncher = PermissionUtil.isDefaultLauncher(this),
        )
        renderChecklist()
    }

    private fun renderChecklist() {
        binding.progressSetup.max = REQUIRED_ITEM_COUNT
        binding.progressSetup.progress = currentReadiness.completedCount
        binding.tvSetupProgress.text = getString(
            R.string.family_setup_progress,
            currentReadiness.completedCount,
            REQUIRED_ITEM_COUNT,
        )

        bindStatus(
            binding.tvSetupContactsStatus,
            currentReadiness.hasPhoneContact,
            if (phoneContactCount == null) {
                getString(R.string.family_setup_contacts_checking)
            } else if (currentReadiness.hasPhoneContact) {
                getString(R.string.family_setup_contacts_ready, phoneContactCount)
            } else {
                getString(R.string.family_setup_contacts_pending)
            },
        )
        bindStatus(
            binding.tvSetupPermissionStatus,
            currentReadiness.phonePermissionGranted,
            getString(
                if (currentReadiness.phonePermissionGranted) {
                    R.string.family_setup_permission_ready
                } else {
                    R.string.family_setup_permission_pending
                }
            ),
        )
        bindStatus(
            binding.tvSetupLauncherStatus,
            currentReadiness.defaultLauncher,
            getString(
                if (currentReadiness.defaultLauncher) {
                    R.string.family_setup_launcher_ready
                } else {
                    R.string.family_setup_launcher_pending
                }
            ),
        )

        binding.btnSetupFinish.isEnabled = currentReadiness.canFinish
        binding.tvSetupFinishHint.setText(
            if (currentReadiness.canFinish) {
                R.string.family_setup_finish_ready
            } else {
                R.string.family_setup_finish_pending
            }
        )
    }

    private fun bindStatus(view: TextView, ready: Boolean, text: String) {
        view.text = text
        view.setTextColor(
            ContextCompat.getColor(
                this,
                if (ready) R.color.launcher_action_dark else R.color.launcher_text_secondary,
            )
        )
    }

    private fun applySystemInsets() {
        val initialTop = binding.root.paddingTop
        val initialBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = initialTop + bars.top, bottom = initialBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun openSettings(screen: SettingsScreen) {
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION, screen.key)
                .putExtra(SettingsActivity.EXTRA_RETURN_TO_CALLER, true)
        )
    }

    private fun completeSetup() {
        setupPreferences.markCompleted()
        setResult(Activity.RESULT_OK)
        finish()
    }

    companion object {
        private const val REQUIRED_ITEM_COUNT = 2

        fun createIntent(context: Context): Intent = Intent(context, FamilySetupActivity::class.java)
    }
}

internal data class FamilySetupReadiness(
    val hasPhoneContact: Boolean,
    val phonePermissionGranted: Boolean,
    val defaultLauncher: Boolean,
) {
    val completedCount: Int
        get() = listOf(hasPhoneContact, phonePermissionGranted).count { it }

    val canFinish: Boolean
        get() = hasPhoneContact && phonePermissionGranted
}

internal fun familySetupReadiness(
    phoneContactCount: Int,
    phonePermissionGranted: Boolean,
    defaultLauncher: Boolean,
): FamilySetupReadiness = FamilySetupReadiness(
    hasPhoneContact = phoneContactCount > 0,
    phonePermissionGranted = phonePermissionGranted,
    defaultLauncher = defaultLauncher,
)
