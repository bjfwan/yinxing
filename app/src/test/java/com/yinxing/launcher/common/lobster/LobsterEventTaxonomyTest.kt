package com.yinxing.launcher.common.lobster

import org.junit.Assert.assertEquals
import org.junit.Test

class LobsterEventTaxonomyTest {
    @Test
    fun `infers legacy WeChat incoming call and errors`() {
        assertEquals(
            LobsterEventTaxonomy(LobsterLogCategory.WECHAT_VIDEO, LobsterEventType.LIFECYCLE, "close_video_page"),
            LobsterEventTaxonomy.infer("微信视频页面", LobsterReportStatus.REPORTED, "微信视频页面关闭")
        )
        assertEquals(
            LobsterEventTaxonomy(LobsterLogCategory.INCOMING_CALL, LobsterEventType.OPERATION, "answer_call"),
            LobsterEventTaxonomy.infer("来电已接听", LobsterReportStatus.SUCCESS, null)
        )
        assertEquals(
            LobsterEventType.ERROR,
            LobsterEventTaxonomy.infer("天气查询", LobsterReportStatus.ERROR, "天气更新失败").eventType
        )
    }
}
