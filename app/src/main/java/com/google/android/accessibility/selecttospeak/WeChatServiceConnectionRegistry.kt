package com.google.android.accessibility.selecttospeak

internal class WeChatServiceConnectionRegistry {
    @Volatile
    private var connectedHost: WeChatRequestHost? = null

    fun onConnected(host: WeChatRequestHost) {
        synchronized(this) {
            connectedHost = host
        }
    }

    fun onDisconnected(host: WeChatRequestHost) {
        synchronized(this) {
            if (connectedHost === host) connectedHost = null
        }
    }

    fun currentHost(): WeChatRequestHost? = connectedHost

    fun isConnected(): Boolean = connectedHost != null

    fun resetForTesting() {
        synchronized(this) {
            connectedHost = null
        }
    }
}
