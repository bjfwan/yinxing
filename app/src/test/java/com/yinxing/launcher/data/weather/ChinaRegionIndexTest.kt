package com.yinxing.launcher.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ChinaRegionIndexTest {

    private val index = ChinaRegionIndex.parse(
        File("src/main/assets/china_regions.json").readText()
    )

    @Test
    fun `exact city name resolves to prefecture city`() {
        val match = index.resolve("杭州")
        assertEquals(330100, match?.entry?.adcode)
        assertEquals("杭州市", match?.entry?.name)
    }

    @Test
    fun `district with suffix resolves`() {
        val match = index.resolve("朝阳区")
        assertEquals(110105, match?.entry?.adcode)
    }

    @Test
    fun `bare district name prefers prefecture city when both exist`() {
        val match = index.resolve("朝阳")
        assertEquals(211300, match?.entry?.adcode)
        assertEquals("朝阳市", match?.entry?.name)
    }

    @Test
    fun `concatenated province district picks context match`() {
        assertEquals(110105, index.resolve("北京朝阳")?.entry?.adcode)
        assertEquals(220104, index.resolve("长春朝阳")?.entry?.adcode)
        assertEquals(110105, index.resolve("北京市朝阳区")?.entry?.adcode)
        assertEquals(330106, index.resolve("杭州市西湖区")?.entry?.adcode)
    }

    @Test
    fun `typo within edit distance one resolves`() {
        val match = index.resolve("杭洲")
        assertNotNull(match)
        assertEquals(330100, match?.entry?.adcode)
    }

    @Test
    fun `autonomous prefecture short alias resolves`() {
        val match = index.resolve("湘西")
        assertNotNull(match)
        assertEquals(433100, match?.entry?.adcode)
    }

    @Test
    fun `common nickname resolves`() {
        val match = index.resolve("哈市")
        assertNotNull(match)
        assertEquals(230100, match?.entry?.adcode)
    }

    @Test
    fun `county level city resolves`() {
        val match = index.resolve("义乌")
        assertNotNull(match)
        assertEquals(330782, match?.entry?.adcode)
        assertEquals("义乌市", match?.entry?.name)
    }

    @Test
    fun `province name is not hijacked by same-name autonomous county`() {
        // "河南蒙古族自治县"的别名是"河南"，但正名匹配必须优先于别名匹配
        val match = index.resolve("河南")
        assertNotNull(match)
        assertEquals(410000, match?.entry?.adcode)
    }

    @Test
    fun `province name resolves to province capital`() {
        val match = index.resolve("四川")
        assertNotNull(match)
        assertEquals(510000, match?.entry?.adcode)
        // 省会成都坐标
        assertEquals(104.0, match!!.entry.longitude, 2.0)
    }

    @Test
    fun `unrelated input returns null`() {
        assertNull(index.resolve("帕罗斯岛"))
        assertNull(index.resolve(""))
        assertNull(index.resolve("   "))
    }

    @Test
    fun `nearestCity finds municipality for metro coordinate`() {
        // 上海人民广场附近
        val entry = index.nearestCity(31.2304, 121.4737)
        assertNotNull(entry)
        assertEquals("上海市", entry?.name)
    }

    @Test
    fun `nearestCity returns null in the ocean`() {
        assertNull(index.nearestCity(20.0, 130.0))
    }

    @Test
    fun `index covers province city district levels`() {
        val match = index.resolve("万州")
        assertNotNull(match)
        assertEquals(500101, match?.entry?.adcode)
    }
}
