package com.yinxing.launcher.common.lobster

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle

/** 进程级注册，只持有 Application；服务退出不影响其它事件的投递。 */
internal class LobsterDeliveryTriggers(context: Context, private val wake: () -> Unit) {
    private val app = context.applicationContext as? Application
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var registered = false
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) wake()
        }
    }
    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) { wake() }
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    fun start() {
        if (registered) return
        registered = true
        runCatching { manager?.registerDefaultNetworkCallback(network) }
        app?.registerActivityLifecycleCallbacks(lifecycle)
        wake()
    }

    fun online(): Boolean = runCatching {
        manager?.getNetworkCapabilities(manager.activeNetwork)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
    }.getOrDefault(false)

    fun close() {
        if (!registered) return
        registered = false
        runCatching { manager?.unregisterNetworkCallback(network) }
        app?.unregisterActivityLifecycleCallbacks(lifecycle)
    }
}
