package com.bajianfeng.launcher.testutil

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.bajianfeng.launcher.data.contact.Contact
import com.bajianfeng.launcher.data.contact.ContactManager
import com.bajianfeng.launcher.data.home.LauncherAppRepository
import com.bajianfeng.launcher.data.home.LauncherPreferences

import org.junit.Assert.assertTrue

object InstrumentationTestEnvironment {
    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    fun resetAppState() {
        clearPreferences("launcher_prefs")
        clearPreferences("wechat_contacts")
        clearPreferences("phone_contacts")
        clearPreferences("incoming_call_diagnostics")
        resetSingleton("com.bajianfeng.launcher.data.home.LauncherPreferences", "instance")
        resetSingleton("com.bajianfeng.launcher.data.home.LauncherAppRepository", "instance")
        resetSingleton("com.bajianfeng.launcher.data.contact.ContactManager", "instance")
        resetSingleton("com.bajianfeng.launcher.feature.phone.PhoneContactManager", "instance")
        LauncherPreferences.getInstance(appContext).setLowPerformanceModeEnabled(false)
        LauncherAppRepository.getInstance(appContext).invalidateInstalledApps()
        LauncherAppRepository.getInstance(appContext).invalidateSelections()
    }

    fun seedVideoContacts(vararg contacts: Contact) {
        clearPreferences("wechat_contacts")
        resetSingleton("com.bajianfeng.launcher.data.contact.ContactManager", "instance")
        val manager = ContactManager.getInstance(appContext)
        contacts.forEach(manager::addContact)
    }

    fun primeLauncherRepositoryWithBuiltInOnlyHome() {
        val repository = LauncherAppRepository.getInstance(appContext)
        setField(repository, "installedAppsCache", emptyList<Any>())
        setField(repository, "installedAppsDirty", false)
        setField(repository, "homeItemsCache", null)
        setField(repository, "homeItemsDirty", true)
    }

    fun <T : Activity> waitUntil(
        scenario: ActivityScenario<T>,

        message: String,
        timeoutMs: Long = 3_000,
        condition: (T) -> Boolean
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var matched = false
        while (!matched && SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                matched = condition(activity)
            }
            if (!matched) {
                SystemClock.sleep(50)
            }
        }
        assertTrue(message, matched)
    }

    fun waitForResumedActivity(
        expectedActivity: Class<out Activity>,
        message: String,
        timeoutMs: Long = 3_000
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var matched = false
        while (!matched && SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                matched = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .any { activity -> expectedActivity.isInstance(activity) }
            }
            if (!matched) {
                SystemClock.sleep(50)
            }
        }
        assertTrue(message, matched)
    }

    fun waitForActivityInAnyStage(
        expectedActivity: Class<out Activity>,
        message: String,
        timeoutMs: Long = 3_000
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var matched = false
        while (!matched && SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                matched = Stage.values().any { stage ->
                    ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(stage)
                        .any { activity -> expectedActivity.isInstance(activity) }
                }
            }
            if (!matched) {
                SystemClock.sleep(50)
            }
        }
        assertTrue(message, matched)
    }

    private fun clearPreferences(name: String) {

        appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun resetSingleton(className: String, fieldName: String) {
        val field = Class.forName(className).getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, null)
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}

