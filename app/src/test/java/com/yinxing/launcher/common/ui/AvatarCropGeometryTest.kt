package com.yinxing.launcher.common.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarCropGeometryTest {
    @Test
    fun baseScaleAlwaysFillsSquareCropArea() {
        assertEquals(2f, AvatarCropGeometry.baseScale(200, 100, 200, 200), 0.001f)
        assertEquals(2f, AvatarCropGeometry.baseScale(100, 200, 200, 200), 0.001f)
    }

    @Test
    fun offsetIsClampedSoCropAreaNeverShowsBlankSpace() {
        assertEquals(100f, AvatarCropGeometry.clampOffset(250f, 400f, 200f), 0.001f)
        assertEquals(-100f, AvatarCropGeometry.clampOffset(-250f, 400f, 200f), 0.001f)
        assertEquals(0f, AvatarCropGeometry.clampOffset(80f, 180f, 200f), 0.001f)
    }
}
