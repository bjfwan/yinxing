package com.google.android.accessibility.selecttospeak

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatServiceConnectionRegistryTest {

    @Test
    fun hostIsAvailableOnlyAfterConnectionCallback() {
        val registry = WeChatServiceConnectionRegistry()
        val host = FakeHost()

        assertFalse(registry.isConnected())

        registry.onConnected(host)

        assertTrue(registry.isConnected())
        assertSame(host, registry.currentHost())
    }

    @Test
    fun staleDisconnectCannotClearNewerConnection() {
        val registry = WeChatServiceConnectionRegistry()
        val oldHost = FakeHost()
        val newHost = FakeHost()
        registry.onConnected(oldHost)
        registry.onConnected(newHost)

        registry.onDisconnected(oldHost)

        assertTrue(registry.isConnected())
        assertSame(newHost, registry.currentHost())
    }

    @Test
    fun matchingDisconnectClearsConnection() {
        val registry = WeChatServiceConnectionRegistry()
        val host = FakeHost()
        registry.onConnected(host)

        registry.onDisconnected(host)

        assertFalse(registry.isConnected())
    }

    private class FakeHost : WeChatRequestHost {
        override fun hasActiveSession(): Boolean = false
        override fun consumePendingRequest() = Unit
    }
}
