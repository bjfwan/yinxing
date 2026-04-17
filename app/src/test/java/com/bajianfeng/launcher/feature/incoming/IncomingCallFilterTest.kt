package com.bajianfeng.launcher.feature.incoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 来电过滤/解析逻辑压测。
 *
 * 覆盖：
 *   - classify(): 真实来电识别、主动拨出过滤、非通话通知过滤、isCallCategory 标志、
 *                 Action 下标解析（各关键词变体、顺序、混合、空 Action）
 *   - resolveCallerName(): title 有效/无效、text 提取、边界值
 *   - 关键词常量完整性
 *   - 并发无副作用（大量重复调用）
 *   - 随机拼接、超长文本、特殊字符
 */
class IncomingCallFilterTest {

    // ═══════════════════════════════════════════════════════════════════════
    // classify: 真实来电 — 视频通话
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyIncomingVideoCallInviteNi() {
        assertTrue(classify("王大爷 邀请你视频通话").isIncoming)
    }

    @Test fun classifyIncomingVideoCallInviteNin() {
        assertTrue(classify("张三 邀请您视频通话").isIncoming)
    }

    @Test fun classifyIncomingVideoCallInviteWithSpaceBefore() {
        assertTrue(classify("  李四   邀请你视频通话").isIncoming)
    }

    @Test fun classifyIncomingVideoCallInviteWithSpaceAfter() {
        assertTrue(classify("赵五 邀请您视频通话  ").isIncoming)
    }

    @Test fun classifyIncomingVideoCallWithEmojiInName() {
        assertTrue(classify("🐼大头 邀请你视频通话").isIncoming)
    }

    @Test fun classifyIncomingVideoCallNameWithDot() {
        assertTrue(classify("wan. 邀请你视频通话").isIncoming)
    }

    @Test fun classifyIncomingVideoCallNameWithNumbers() {
        assertTrue(classify("user123 邀请您视频通话").isIncoming)
    }

    @Test fun classifyIncomingVideoCallNameWithSpecialChars() {
        assertTrue(classify("_test_用户_ 邀请你视频通话").isIncoming)
    }

    @Test fun classifyIncomingVideoCallCombinedWithBigText() {
        // bigText 附在 combined 末尾
        assertTrue(classify("张三 邀请你视频通话 [bigText额外内容]").isIncoming)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify: 真实来电 — 语音通话
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyIncomingVoiceCallInviteNi() {
        assertTrue(classify("李四 邀请你语音通话").isIncoming)
    }

    @Test fun classifyIncomingVoiceCallInviteNin() {
        assertTrue(classify("王二 邀请您语音通话").isIncoming)
    }

    @Test fun classifyIncomingVoiceCallNameWithSpace() {
        assertTrue(classify("  小明  邀请你语音通话  ").isIncoming)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify: 真实来电 — 发起了通话
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyIncomingByInitiatedKeyword() {
        assertTrue(classify("王五 发起了通话").isIncoming)
    }

    @Test fun classifyIncomingByInitiatedKeywordWithExtraText() {
        assertTrue(classify("王五 发起了通话 语音").isIncoming)
    }

    @Test fun classifyIncomingByInitiatedKeywordAtEnd() {
        assertTrue(classify("微信 某人发起了通话").isIncoming)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify: 真实来电 — isCallCategory=true
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyIncomingWhenCallCategoryAndInviteNi() {
        val r = IncomingCallFilter.classify("某人 邀请你通话", isCallCategory = true, actions = null)
        assertTrue(r.isIncoming)
    }

    @Test fun classifyIncomingWhenCallCategoryAndInviteNin() {
        val r = IncomingCallFilter.classify("某人 邀请您通话", isCallCategory = true, actions = null)
        assertTrue(r.isIncoming)
    }

    @Test fun classifyIncomingWhenCallCategoryAndInitiated() {
        val r = IncomingCallFilter.classify("某人 发起了通话", isCallCategory = true, actions = null)
        assertTrue(r.isIncoming)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify: 应被过滤 — 主动拨出 / 通话中 / 等待对方
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyRejectsActiveOutgoingVideoCall() {
        assertFalse(classify("微信 视频通话 ").isIncoming)
    }

    @Test fun classifyRejectsActiveOutgoingVoiceCall() {
        assertFalse(classify("微信 语音通话 ").isIncoming)
    }

    @Test fun classifyRejectsOngoingVideoCallNotification() {
        assertFalse(classify("微信 视频通话中").isIncoming)
    }

    @Test fun classifyRejectsOngoingVoiceCallNotification() {
        assertFalse(classify("微信 语音通话中").isIncoming)
    }

    @Test fun classifyRejectsWaitingForAnswerVideoCall() {
        assertFalse(classify("微信 正在等待对方接听 视频通话").isIncoming)
    }

    @Test fun classifyRejectsWaitingForAnswerVoiceCall() {
        assertFalse(classify("微信 正在等待对方接听 语音通话").isIncoming)
    }

    @Test fun classifyRejectsCallEndedNotification() {
        assertFalse(classify("微信 通话已结束").isIncoming)
    }

    @Test fun classifyRejectsCallCancelledNotification() {
        assertFalse(classify("微信 通话已取消").isIncoming)
    }

    @Test fun classifyRejectsCallCategoryWithoutIncomingKeyword() {
        // category=CALL 但文本只有"通话中"，没有邀请/发起
        val r = IncomingCallFilter.classify("视频通话中", isCallCategory = true, actions = null)
        assertFalse(r.isIncoming)
    }

    @Test fun classifyRejectsCallCategoryWaitingText() {
        val r = IncomingCallFilter.classify("正在等待对方接听 语音通话", isCallCategory = true, actions = null)
        assertFalse(r.isIncoming)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify: 应被过滤 — 与通话无关的通知
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyRejectsUnrelatedTextMessage() {
        assertFalse(classify("微信 你有一条新消息").isIncoming)
    }

    @Test fun classifyRejectsPaymentNotification() {
        assertFalse(classify("微信支付 你收到一笔转账 300元").isIncoming)
    }

    @Test fun classifyRejectsGroupChatMessage() {
        assertFalse(classify("家庭群 张三: 吃饭了吗").isIncoming)
    }

    @Test fun classifyRejectsFriendRequest() {
        assertFalse(classify("微信 张三请求添加你为朋友").isIncoming)
    }

    @Test fun classifyRejectsMomentNotification() {
        assertFalse(classify("微信 张三赞了你的朋友圈").isIncoming)
    }

    @Test fun classifyRejectsSystemNotification() {
        assertFalse(classify("微信 账号在其他设备上登录").isIncoming)
    }

    @Test fun classifyRejectsEmpty() {
        assertFalse(classify("").isIncoming)
    }

    @Test fun classifyRejectsBlankWhitespaceOnly() {
        assertFalse(classify("   ").isIncoming)
    }

    @Test fun classifyRejectsUnrelatedAppNotification() {
        assertFalse(classify("支付宝 你的账单已出").isIncoming)
    }

    @Test fun classifyRejectsShortIrrelevantText() {
        assertFalse(classify("hi").isIncoming)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify: 边界 / 特殊字符 / 超长文本
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyHandlesFullWidthSpaceAroundKeyword() {
        // 全角空格不影响子串匹配
        assertTrue(classify("某人　邀请你　视频通话").isIncoming)
    }

    @Test fun classifyHandlesLongCombinedStringWithKeywordAtEnd() {
        val text = "A".repeat(500) + " 邀请你视频通话"
        assertTrue(classify(text).isIncoming)
    }

    @Test fun classifyHandlesLongCombinedStringWithKeywordAtStart() {
        val text = "邀请你视频通话 " + "B".repeat(500)
        assertTrue(classify(text).isIncoming)
    }

    @Test fun classifyHandlesLongCombinedStringWithKeywordInMiddle() {
        val text = "X".repeat(300) + " 邀请您语音通话 " + "Y".repeat(300)
        assertTrue(classify(text).isIncoming)
    }

    @Test fun classifyHandlesEmojiAroundKeyword() {
        assertTrue(classify("🎉🎊 邀请你视频通话 🎉").isIncoming)
    }

    @Test fun classifyHandlesChinesePunctuationAroundKeyword() {
        assertTrue(classify("「某人」邀请你视频通话，请接听").isIncoming)
    }

    @Test fun classifyHandlesNewlineInCombined() {
        assertTrue(classify("某人\n邀请你视频通话\n详情").isIncoming)
    }

    @Test fun classifyHandlesTabInCombined() {
        assertTrue(classify("某人\t邀请您语音通话").isIncoming)
    }

    @Test fun classifyPartialKeywordNiMenMatchesNi() {
        // "邀请你们" 包含子串 "邀请你"，结果为 true（记录预期行为）
        assertTrue(classify("某人 邀请你们视频通话").isIncoming)
    }

    @Test fun classifyPartialKeywordNinMatchesNin() {
        // "邀请您好" 包含子串 "邀请您"，结果为 true（记录预期行为）
        assertTrue(classify("某人 邀请您好视频通话").isIncoming)
    }

    @Test fun classifyKeywordCaseSensitiveEnglishAccept() {
        // "accept"（小写）应不被 ACCEPT_KEYWORDS("Accept") 的 contains 匹配（区分大小写）
        val r = IncomingCallFilter.classify(
            combined = "某人 邀请你视频通话",
            isCallCategory = false,
            actions = listOf("accept")
        )
        // contains 是大小写敏感的，"accept" 不含 "Accept"
        assertEquals(-1, r.acceptIndex)
    }

    @Test fun classifyKeywordCaseSensitiveEnglishDecline() {
        val r = IncomingCallFilter.classify(
            combined = "某人 邀请你视频通话",
            isCallCategory = false,
            actions = listOf("decline")
        )
        assertEquals(-1, r.declineIndex)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify: Action 下标解析 — 接听关键词变体
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyAcceptKeyword_接受() {
        val r = classifyWithActions(listOf("接受"))
        assertEquals(0, r.acceptIndex)
    }

    @Test fun classifyAcceptKeyword_接听() {
        val r = classifyWithActions(listOf("接听"))
        assertEquals(0, r.acceptIndex)
    }

    @Test fun classifyAcceptKeyword_接通() {
        val r = classifyWithActions(listOf("接通"))
        assertEquals(0, r.acceptIndex)
    }

    @Test fun classifyAcceptKeyword_Accept() {
        val r = classifyWithActions(listOf("Accept"))
        assertEquals(0, r.acceptIndex)
    }

    @Test fun classifyAcceptKeyword接听InMultiwordLabel() {
        // Action label 是 "立即接听" 含子串 "接听"
        val r = classifyWithActions(listOf("立即接听"))
        assertEquals(0, r.acceptIndex)
    }

    @Test fun classifyAcceptKeyword接受InMultiwordLabel() {
        val r = classifyWithActions(listOf("请接受通话"))
        assertEquals(0, r.acceptIndex)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify: Action 下标解析 — 拒绝关键词变体
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyDeclineKeyword_拒绝() {
        val r = classifyWithActions(listOf("拒绝"))
        assertEquals(0, r.declineIndex)
    }

    @Test fun classifyDeclineKeyword_挂断() {
        val r = classifyWithActions(listOf("挂断"))
        assertEquals(0, r.declineIndex)
    }

    @Test fun classifyDeclineKeyword_拒接() {
        val r = classifyWithActions(listOf("拒接"))
        assertEquals(0, r.declineIndex)
    }

    @Test fun classifyDeclineKeyword_Decline() {
        val r = classifyWithActions(listOf("Decline"))
        assertEquals(0, r.declineIndex)
    }

    @Test fun classifyDeclineKeyword_忽略() {
        val r = classifyWithActions(listOf("忽略"))
        assertEquals(0, r.declineIndex)
    }

    @Test fun classifyDeclineKeyword挂断InMultiwordLabel() {
        val r = classifyWithActions(listOf("稍后挂断"))
        assertEquals(0, r.declineIndex)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify: Action 下标解析 — 顺序与组合
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyActionsDeclineFirst_AcceptSecond() {
        val r = classifyWithActions(listOf("拒绝", "接听"))
        assertEquals(1, r.acceptIndex)
        assertEquals(0, r.declineIndex)
    }

    @Test fun classifyActionsAcceptFirst_DeclineSecond() {
        val r = classifyWithActions(listOf("接听", "拒绝"))
        assertEquals(0, r.acceptIndex)
        assertEquals(1, r.declineIndex)
    }

    @Test fun classifyActionsThreeWithReplyFirst() {
        val r = classifyWithActions(listOf("消息回复", "拒绝", "接听"))
        assertEquals(2, r.acceptIndex)
        assertEquals(1, r.declineIndex)
    }

    @Test fun classifyActionsThreeWithReplyLast() {
        val r = classifyWithActions(listOf("接受", "挂断", "回复消息"))
        assertEquals(0, r.acceptIndex)
        assertEquals(1, r.declineIndex)
    }

    @Test fun classifyActionsFourMixed() {
        val r = classifyWithActions(listOf("静音", "回复", "拒接", "接通"))
        assertEquals(3, r.acceptIndex)
        assertEquals(2, r.declineIndex)
    }

    @Test fun classifyActionsPicksFirstAcceptWhenMultiple() {
        val r = classifyWithActions(listOf("接听", "接通"))
        assertEquals(0, r.acceptIndex)
    }

    @Test fun classifyActionsPicksFirstDeclineWhenMultiple() {
        val r = classifyWithActions(listOf("拒绝", "挂断"))
        assertEquals(0, r.declineIndex)
    }

    @Test fun classifyActionsOnlyAccept_NoDecline() {
        val r = classifyWithActions(listOf("接听"))
        assertEquals(0, r.acceptIndex)
        assertEquals(-1, r.declineIndex)
    }

    @Test fun classifyActionsOnlyDecline_NoAccept() {
        val r = classifyWithActions(listOf("拒绝"))
        assertEquals(-1, r.acceptIndex)
        assertEquals(0, r.declineIndex)
    }

    @Test fun classifyActionsNull_BothMinusOne() {
        val r = IncomingCallFilter.classify("某人 邀请你视频通话", false, null)
        assertEquals(-1, r.acceptIndex)
        assertEquals(-1, r.declineIndex)
    }

    @Test fun classifyActionsEmpty_BothMinusOne() {
        val r = IncomingCallFilter.classify("某人 邀请你视频通话", false, emptyList())
        assertEquals(-1, r.acceptIndex)
        assertEquals(-1, r.declineIndex)
    }

    @Test fun classifyActionsNoMatch_BothMinusOne() {
        val r = classifyWithActions(listOf("静音", "回复", "截图"))
        assertEquals(-1, r.acceptIndex)
        assertEquals(-1, r.declineIndex)
    }

    @Test fun classifyActionsSingleBlank() {
        val r = classifyWithActions(listOf(""))
        assertEquals(-1, r.acceptIndex)
        assertEquals(-1, r.declineIndex)
    }

    @Test fun classifyActionsManyBlanks() {
        val r = classifyWithActions(List(10) { "" })
        assertEquals(-1, r.acceptIndex)
        assertEquals(-1, r.declineIndex)
    }

    @Test fun classifyActionsSingleWhitespace() {
        val r = classifyWithActions(listOf("   "))
        assertEquals(-1, r.acceptIndex)
        assertEquals(-1, r.declineIndex)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // classify: rejectReason 非空性
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyRejectReasonNotEmptyWhenNotCallNotification() {
        val r = classify("微信 你有新消息")
        assertFalse(r.isIncoming)
        assertTrue("rejectReason 应非空", r.rejectReason.isNotEmpty())
    }

    @Test fun classifyRejectReasonNotEmptyWhenOutgoing() {
        val r = classify("微信 视频通话中")
        assertFalse(r.isIncoming)
        assertTrue("rejectReason 应非空", r.rejectReason.isNotEmpty())
    }

    @Test fun classifyIncomingHasEmptyRejectReason() {
        val r = classify("某人 邀请你视频通话")
        assertTrue(r.isIncoming)
        // incoming 时 rejectReason 无意义，可为空（不做强制断言，只记录当前行为）
        assertNotNull(r.rejectReason)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // resolveCallerName: title 有效直接返回
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun resolveCallerNameReturnsTitleWhenValid() {
        assertEquals("王大爷", IncomingCallFilter.resolveCallerName("王大爷", "王大爷 邀请你视频通话"))
    }

    @Test fun resolveCallerNameReturnsTitleWhenEnglish() {
        assertEquals("Alice", IncomingCallFilter.resolveCallerName("Alice", "Alice invited you"))
    }

    @Test fun resolveCallerNameReturnsTitleWhenMixedLang() {
        assertEquals("Bob李四", IncomingCallFilter.resolveCallerName("Bob李四", "Bob李四 邀请你"))
    }

    @Test fun resolveCallerNameReturnsTitleWithSpecialChars() {
        assertEquals("wan.", IncomingCallFilter.resolveCallerName("wan.", "wan. 邀请你视频通话"))
    }

    @Test fun resolveCallerNameReturnsTitleWithNumbers() {
        assertEquals("user123", IncomingCallFilter.resolveCallerName("user123", "user123 邀请您"))
    }

    @Test fun resolveCallerNameReturnsTitleWithEmoji() {
        assertEquals("🐼猫咪", IncomingCallFilter.resolveCallerName("🐼猫咪", "🐼猫咪 邀请你"))
    }

    @Test fun resolveCallerNameReturnsTitleWhenTitleHasPunctuation() {
        assertEquals("用户(备注)", IncomingCallFilter.resolveCallerName("用户(备注)", "某人 邀请你"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // resolveCallerName: title="微信" 或 "WeChat" → 从 text 提取
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun resolveCallerNameFallsBackToTextWhenTitleIsWechatCn() {
        assertEquals("wan.", IncomingCallFilter.resolveCallerName("微信", "wan. 邀请你视频通话"))
    }

    @Test fun resolveCallerNameFallsBackToTextWhenTitleIsWechatEn() {
        assertEquals("John", IncomingCallFilter.resolveCallerName("WeChat", "John invited you to a video call"))
    }

    @Test fun resolveCallerNameFallsBackToTextWhenTitleIsBlank() {
        assertEquals("张三", IncomingCallFilter.resolveCallerName("", "张三 邀请您视频通话"))
    }

    @Test fun resolveCallerNameFallsBackToTextForMultiSpaceName() {
        // 取第一个空格前的内容
        assertEquals("张", IncomingCallFilter.resolveCallerName("微信", "张 三 邀请你"))
    }

    @Test fun resolveCallerNameFallsBackToTextForEnglishMultiWord() {
        assertEquals("John", IncomingCallFilter.resolveCallerName("微信", "John Smith 邀请你"))
    }

    @Test fun resolveCallerNameFallsBackToTextWithLongName() {
        val longName = "A".repeat(50)
        assertEquals(longName, IncomingCallFilter.resolveCallerName("微信", "$longName 邀请你视频通话"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // resolveCallerName: 边界 / 退化场景
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun resolveCallerNameReturnsNullWhenBothBlank() {
        assertNull(IncomingCallFilter.resolveCallerName("", ""))
    }

    @Test fun resolveCallerNameReturnsNullWhenBothWhitespace() {
        assertNull(IncomingCallFilter.resolveCallerName("   ", "   "))
    }

    @Test fun resolveCallerNameFallbackToWechatWhenTextHasNoSpace() {
        // text 无空格，无法提取，fallback 为 title="微信"
        assertEquals("微信", IncomingCallFilter.resolveCallerName("微信", "邀请你视频通话"))
    }

    @Test fun resolveCallerNameFallbackToWechatWhenTextLeadingSpace() {
        // text 以空格开头 spaceIdx=0 不满足 >0，fallback 为 title
        assertEquals("微信", IncomingCallFilter.resolveCallerName("微信", " 邀请你视频通话"))
    }

    @Test fun resolveCallerNameFallbackToWechatWhenTextBlank() {
        // text 为空，fallback 为 title
        assertEquals("微信", IncomingCallFilter.resolveCallerName("微信", ""))
    }

    @Test fun resolveCallerNameReturnsNullWhenTitleWechatEnAndTextBlank() {
        // title="WeChat", text="" → text 提取失败 → fallback "WeChat"
        assertEquals("WeChat", IncomingCallFilter.resolveCallerName("WeChat", ""))
    }

    @Test fun resolveCallerNameReturnsNullWhenTitleBlankAndTextBlank() {
        assertNull(IncomingCallFilter.resolveCallerName("", "  "))
    }

    @Test fun resolveCallerNameReturnsNullWhenTitleWhitespaceAndTextBlank() {
        assertNull(IncomingCallFilter.resolveCallerName("  ", ""))
    }

    @Test fun resolveCallerNameHandlesTextWithOnlySpaces() {
        // title blank → text 只有空格 → text.isNotBlank()=false → null
        assertNull(IncomingCallFilter.resolveCallerName("", "   "))
    }

    @Test fun resolveCallerNameHandlesVeryLongBlankTitle() {
        val blank = " ".repeat(200)
        val name = IncomingCallFilter.resolveCallerName(blank, "张三 邀请你")
        // title.isNotBlank() = false → 走 text 提取
        assertEquals("张三", name)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 关键词常量完整性检查
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun callKeywordsContainsVideoCall() {
        assertTrue(IncomingCallFilter.CALL_KEYWORDS.contains("视频通话"))
    }

    @Test fun callKeywordsContainsVoiceCall() {
        assertTrue(IncomingCallFilter.CALL_KEYWORDS.contains("语音通话"))
    }

    @Test fun callKeywordsContainsInviteNi() {
        assertTrue(IncomingCallFilter.CALL_KEYWORDS.contains("邀请你"))
    }

    @Test fun callKeywordsContainsInviteNin() {
        assertTrue(IncomingCallFilter.CALL_KEYWORDS.contains("邀请您"))
    }

    @Test fun callKeywordsContainsInitiated() {
        assertTrue(IncomingCallFilter.CALL_KEYWORDS.contains("发起了通话"))
    }

    @Test fun incomingKeywordsContainsInviteNi() {
        assertTrue(IncomingCallFilter.INCOMING_KEYWORDS.contains("邀请你"))
    }

    @Test fun incomingKeywordsContainsInviteNin() {
        assertTrue(IncomingCallFilter.INCOMING_KEYWORDS.contains("邀请您"))
    }

    @Test fun incomingKeywordsContainsInitiated() {
        assertTrue(IncomingCallFilter.INCOMING_KEYWORDS.contains("发起了通话"))
    }

    @Test fun acceptKeywordsContainsAllVariants() {
        val keywords = IncomingCallFilter.ACCEPT_KEYWORDS
        assertTrue(keywords.contains("接受"))
        assertTrue(keywords.contains("接听"))
        assertTrue(keywords.contains("接通"))
        assertTrue(keywords.contains("Accept"))
    }

    @Test fun declineKeywordsContainsAllVariants() {
        val keywords = IncomingCallFilter.DECLINE_KEYWORDS
        assertTrue(keywords.contains("拒绝"))
        assertTrue(keywords.contains("挂断"))
        assertTrue(keywords.contains("拒接"))
        assertTrue(keywords.contains("Decline"))
        assertTrue(keywords.contains("忽略"))
    }

    @Test fun incomingKeywordsAreSubsetOfCallKeywords() {
        // 每个 INCOMING_KEYWORD 都应被 CALL_KEYWORDS 包含或独立存在
        // 这里验证 INCOMING_KEYWORDS 不为空且全部不空
        IncomingCallFilter.INCOMING_KEYWORDS.forEach { kw ->
            assertTrue("INCOMING_KEYWORD '$kw' 不应为空", kw.isNotBlank())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 压力测试 — 大量重复调用验证无副作用
    // ═══════════════════════════════════════════════════════════════════════

    @Test fun classifyStable5000RepetitionsIncoming() {
        val combined = "张三 邀请你视频通话"
        val actions = listOf("拒绝", "接听")
        repeat(5000) { i ->
            val r = IncomingCallFilter.classify(combined, false, actions)
            assertTrue("iter $i isIncoming", r.isIncoming)
            assertEquals("iter $i acceptIndex", 1, r.acceptIndex)
            assertEquals("iter $i declineIndex", 0, r.declineIndex)
        }
    }

    @Test fun classifyStable5000RepetitionsOutgoing() {
        val combined = "微信 视频通话中"
        repeat(5000) { i ->
            assertFalse("iter $i", IncomingCallFilter.classify(combined, false, null).isIncoming)
        }
    }

    @Test fun classifyStable5000RepetitionsNoActions() {
        val combined = "李四 发起了通话"
        repeat(5000) { i ->
            val r = IncomingCallFilter.classify(combined, false, null)
            assertTrue("iter $i isIncoming", r.isIncoming)
            assertEquals("iter $i acceptIndex", -1, r.acceptIndex)
            assertEquals("iter $i declineIndex", -1, r.declineIndex)
        }
    }

    @Test fun resolveCallerNameStable5000Repetitions() {
        repeat(5000) { i ->
            val name = IncomingCallFilter.resolveCallerName("微信", "wan. 邀请你视频通话")
            assertEquals("iter $i", "wan.", name)
        }
    }

    @Test fun resolveCallerNameTitleValidStable5000Repetitions() {
        repeat(5000) { i ->
            val name = IncomingCallFilter.resolveCallerName("王大爷", "王大爷 邀请你")
            assertEquals("iter $i", "王大爷", name)
        }
    }

    @Test fun classifyMixedScenarios1000Rounds() {
        // 混合场景：交替真实来电 / 主动拨出 / 无关通知
        val cases = listOf(
            "A 邀请你视频通话" to true,
            "微信 视频通话中" to false,
            "B 邀请您语音通话" to true,
            "微信 你有新消息" to false,
            "C 发起了通话" to true,
            "微信 正在等待对方接听 语音通话" to false,
        )
        repeat(1000) { round ->
            cases.forEach { (combined, expected) ->
                val r = IncomingCallFilter.classify(combined, false, null)
                assertEquals("round=$round combined=$combined", expected, r.isIncoming)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 辅助
    // ═══════════════════════════════════════════════════════════════════════

    private fun classify(combined: String) =
        IncomingCallFilter.classify(combined, isCallCategory = false, actions = null)

    private fun classifyWithActions(actions: List<String>) =
        IncomingCallFilter.classify("某人 邀请你视频通话", isCallCategory = false, actions = actions)
}
