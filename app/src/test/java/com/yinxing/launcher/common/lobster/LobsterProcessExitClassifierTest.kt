package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LobsterProcessExitClassifierTest {
    @Test
    fun `classifies actionable previous exits without platform description`() {
        val anr = LobsterProcessExitClassifier.classify(LobsterProcessExitReasons.ANR)
        val lowMemory = LobsterProcessExitClassifier.classify(LobsterProcessExitReasons.LOW_MEMORY)

        assertEquals("PROCESS_EXIT_ANR", anr?.details?.errorCode)
        assertEquals("previous_process_exit", anr?.action)
        assertEquals(LobsterEventType.ERROR, anr?.eventType)
        assertEquals("PROCESS_EXIT_LOW_MEMORY", lowMemory?.details?.errorCode)
    }

    @Test
    fun `ignores normal and user requested exits`() {
        assertNull(LobsterProcessExitClassifier.classify(LobsterProcessExitReasons.EXIT_SELF))
        assertNull(LobsterProcessExitClassifier.classify(LobsterProcessExitReasons.USER_REQUESTED))
    }
}
