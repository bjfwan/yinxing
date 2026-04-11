package com.bajianfeng.launcher.testutil

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
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
        resetSingleton("com.bajianfeng.launcher.data.home.LauncherPreferences", "instance")
        resetSingleton("com.bajianfeng.launcher.data.home.LauncherAppRepository", "instance")
        resetSingleton("com.bajianfeng.launcher.data.contact.ContactManager", "instance")
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

    private fun clearPreferences(name: String) {
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun resetSingleton(className: String, fieldName: String) {
        val field = Class.forName(className).getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, null)
    }
}
