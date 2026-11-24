package com.yinxing.launcher.common.lobster

import android.content.Context
import java.util.UUID

/** Consent generation is independent from the user preference and stored in no-backup app data. */
internal class LobsterDeliveryConsent(
    private val context: Context,
    private val databaseName: String = "lobster-diagnostics.db",
    private val preference: () -> Boolean
) {
    @Volatile private var generation: String? = null
    @Volatile private var initialized = false
    @Volatile private var rotateOnSync = false

    @Synchronized
    fun initialize() {
        if (initialized) return
        val raw = LobsterAtomicStorage.read(context, KEY_GENERATION, databaseName)?.trim()?.take(80)
        generation = raw?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) }
        synchronize()
    }

    fun current(): String? = generation?.takeIf { initialized && runCatching(preference).getOrDefault(false) }

    @Synchronized
    fun invalidateImmediately() {
        generation = null
        rotateOnSync = true
        initialized = true
    }

    @Synchronized
    fun synchronize() {
        val allowed = runCatching(preference).getOrDefault(false)
        if (!allowed) {
            generation = null
            LobsterAtomicStorage.write(context, KEY_GENERATION, null, databaseName)
            rotateOnSync = false
            initialized = true
            return
        }
        if (generation == null || rotateOnSync) {
            val nextGeneration = UUID.randomUUID().toString()
            LobsterAtomicStorage.write(context, KEY_GENERATION, nextGeneration, databaseName)
            generation = nextGeneration
            rotateOnSync = false
        }
        initialized = true
    }

    @Synchronized
    fun change(enabled: Boolean) {
        invalidateImmediately()
        synchronize()
    }

    private companion object {
        const val KEY_GENERATION = "consent_generation"
    }
}
