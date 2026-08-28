package com.yinxing.launcher.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmergencyContactNumberTest {

    @Test
    fun normalizesCommonPhoneFormatting() {
        assertEquals("13812345678", EmergencyContactNumber.normalize("138 1234-5678"))
        assertEquals("+8613812345678", EmergencyContactNumber.normalize("+86 138 1234 5678"))
    }

    @Test
    fun rejectsTextAndImplausibleNumbers() {
        assertNull(EmergencyContactNumber.normalize("张阿姨13812345678"))
        assertNull(EmergencyContactNumber.normalize("1234"))
    }

    @Test
    fun rejectsPublicEmergencyNumbersForAutomaticFamilyCall() {
        assertNull(EmergencyContactNumber.normalize("110"))
        assertNull(EmergencyContactNumber.normalize("119"))
        assertNull(EmergencyContactNumber.normalize("120"))
        assertNull(EmergencyContactNumber.normalize("122"))
    }
}
