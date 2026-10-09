package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class GroupWeatherRulesTest {
    @Test fun localizedShanghaiWeatherWithPriorPhonePosition() {
        val r = GroupWeatherRules.parse("按刚才的手机定位，要看上海浦东金桥一带的天气。")!!
        assertEquals("上海", r.city)
        assertTrue(r.usePhoneLocation)
        assertEquals(3, r.days)
        val args = GroupWeatherRules.arguments(r)
        assertTrue(args.contains("use_phone_location"))
        assertTrue(args.contains("上海"))
    }
    @Test fun askForCurrentWeatherUsesOneDay() {
        val r = GroupWeatherRules.parse("@小艺 帮我查一下现在的天气")!!
        assertEquals(1, r.days)
        assertNull(r.city)
        assertFalse(r.usePhoneLocation)
    }
    @Test fun cityOnlyRequestDoesNotTriggerGps() {
        val r = GroupWeatherRules.parse("你能告诉我杭州明天会不会下雨吗？")!!
        assertEquals("杭州", r.city)
        assertFalse(r.usePhoneLocation)
        assertEquals(2, r.days)
    }
    @Test fun sevenDaysIsBounded() {
        assertEquals(7, GroupWeatherRules.parse("查一下南京未来一周天气预报")!!.days)
    }
    @Test fun noAccidentalWeatherLookupForDiscussion() {
        for (text in listOf("", "天气真好啊", "他们说天气工具不可用", "昨天的天气记录",
            "你怎么看天气这个功能的设计", "你们聊聊心情"))
            assertNull("should not fetch for $text", GroupWeatherRules.parse(text))
    }
    @Test fun explicitWeatherRefusalsAreRespected() {
        for (text in listOf("不要查天气", "别查询天气了", "不用查天气预报", "我不想查天气"))
            assertNull("must not fetch for $text", GroupWeatherRules.parse(text))
    }
    @Test fun locationRequiresExplicitCue() {
        assertTrue(GroupWeatherRules.parse("帮我查我这里会不会下雨")!!.usePhoneLocation)
        assertFalse(GroupWeatherRules.parse("帮我查上海市今天会不会下雨")!!.usePhoneLocation)
    }
    @Test fun successAndFailureAreClearlyDifferent() {
        val req = GroupWeatherRules.parse("帮我查现在的天气")!!
        val success = GroupWeatherRules.context(ToolOutcome("现在：阴，20°C", "查了定位的天气"), true, req)
        val fail = GroupWeatherRules.context(ToolOutcome("没有定位权限", "查天气没成：没有权限"), false, req)
        assertTrue(success.contains("实际调用 get_weather"))
        assertTrue(success.contains("20°C"))
        assertFalse(fail.contains("实际调用 get_weather"))
        assertTrue(fail.contains("没有定位权限"))
        assertTrue(fail.contains("不得编造"))
    }
    @Test fun notAStatementAboutWeatherIsNotARequest() {
        assertNull(GroupWeatherRules.parse("这个软件的天气功能为什么总是查不了"))
    }
}
