package com.cleo.cleos.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupLocationRulesTest {
    @Test fun explicitGroupLocationRequestsAreRecognized() {
        for (text in listOf(
            "你们看定位", "我是让你们看定位，不是图片",
            "帮我查一下现在的位置", "给我发一下手机定位",
            "@阿弦 你能读取我的位置吗？", "我现在在哪儿？",
            "我的坐标是多少", "我附近有什么医院？", "定位",
        )) {
            assertTrue("should locate for: $text", GroupLocationRules.explicitlyRequested(text))
        }
    }

    @Test fun casualConversationNeverTriggersLocation() {
        for (text in listOf(
            "", "晚安", "看一下图片", "以前你们聊过定位问题",
            "你们觉得呢", "今天的日记",
        )) {
            assertFalse("unexpected locate: $text", GroupLocationRules.explicitlyRequested(text))
        }
    }

    @Test fun denialsAndDisablingRequestsNeverTriggerLocation() {
        for (text in listOf(
            "不要看我的定位", "别读取我的位置", "关闭定位",
            "取消位置共享", "我不想给你们看定位",
            "不用获取GPS了", "停止读取坐标",
        )) {
            assertFalse("must not locate for: $text", GroupLocationRules.explicitlyRequested(text))
        }
    }

    @Test fun verifiedLocationMustBeDistinguishedFromImagesAndGuessing() {
        val result = GroupLocationRules.context("杭州市拱墅区，坐标 30.3, 120.1", verified = true)
        assertTrue(result.contains("实际查询"))
        assertTrue(result.contains("手机定位，不是图片"))
        assertTrue(result.contains("杭州市拱墅区"))
        assertTrue(result.contains("不得声称自己再次查过"))
    }

    @Test fun unavailableLocationNeverLooksSuccessful() {
        val result = GroupLocationRules.context("没有定位权限", verified = false)
        assertTrue(result.contains("没有获得手机位置"))
        assertTrue(result.contains("没有定位权限"))
        assertTrue(result.contains("不得猜测"))
        assertFalse(result.contains("已由 Android get_location 工具实际查询"))
    }
}
