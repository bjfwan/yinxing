package com.yinxing.launcher.common.lobster

import java.util.concurrent.atomic.AtomicLong

internal class LobsterEventSequence {
    private val value = AtomicLong(0L)

    fun next(): Long = value.incrementAndGet()
}
