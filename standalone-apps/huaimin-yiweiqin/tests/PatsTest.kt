package com.cleo.cleos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatsTest {
    @Test
    fun aPatReadsTheWayItDoesInAChatApp() {
        assertEquals("我拍了拍自己", Pats.line(PatRecord(Pats.ME), "星"))
        assertEquals("我拍了拍“星”", Pats.line(PatRecord(Pats.AI), "星"))
        assertEquals("我拍了拍“星”的小脑袋", Pats.line(PatRecord(Pats.AI, suffix = "的小脑袋"), "星"))
        assertEquals("我抱了抱“TA”", Pats.line(PatRecord(Pats.AI, verb = "抱"), ""))
    }

    @Test
    fun pats_inARowAreOneLineThatCounts() {
        assertEquals("我连拍了“星”的脸蛋 3 下", Pats.line(PatRecord(Pats.AI, 3, suffix = "的脸蛋"), "星"))
        assertEquals("我连戳了自己 2 下", Pats.line(PatRecord(Pats.ME, 2, "戳"), "星"))
        assertTrue(Pats.line(PatRecord(Pats.AI, 8), "星").endsWith("（别拍啦，要晕了）"))
        assertEquals("我连拍了自己 8 下", Pats.line(PatRecord(Pats.ME, 8), "星"))
    }

    @Test
    fun aPatFollowsTheLastOneOnlyWhenItIsTheSameSideAndStillWarm() {
        val first = Pats.again(null, 0, Pats.AI, "拍", "", 1_000)
        assertEquals(1, first.count)
        assertEquals(2, Pats.again(first, 1_000, Pats.AI, "拍", "", 1_000 + Pats.STREAK_MS).count)
        // Too long after, or the other avatar: a new line.
        assertEquals(1, Pats.again(first, 1_000, Pats.AI, "拍", "", 1_001 + Pats.STREAK_MS).count)
        assertEquals(1, Pats.again(first, 1_000, Pats.ME, "拍", "", 2_000).count)
        // The words are the latest ones, so a changed verb shows from the next pat on.
        assertEquals("戳", Pats.again(first, 1_000, Pats.AI, "戳", "", 2_000).verb)
    }

    @Test
    fun theTaIsToldInItsOwnPerson() {
        assertEquals("（对方拍了拍你的小脑袋。请自然回应这次互动，简短一点也可以）", Pats.forModel(PatRecord(Pats.AI, suffix = "的小脑袋")))
        assertEquals("（对方连摸了你 4 下。请自然回应这次互动）", Pats.forModel(PatRecord(Pats.AI, 4, "摸")))
        assertEquals("（对方拍了拍自己）", Pats.forModel(PatRecord(Pats.ME)))
    }

    @Test
    fun theWordsPeopleTypeAreTidied() {
        assertEquals("的小脑袋", Pats.cleanSuffix("  的小脑袋\n"))
        assertEquals(Pats.SUFFIX_MAX, Pats.cleanSuffix("一二三四五六七八九十一二三四五").length)
        assertEquals("戳", Pats.cleanVerb("戳了戳"))
        assertEquals("拍", Pats.cleanVerb("  "))
        assertEquals("🫶", Pats.cleanVerb("🫶好"))
    }

    @Test
    fun aRecordSurvivesTheDatabase() {
        val r = PatRecord(Pats.AI, 3, "抱", "的肩膀")
        assertEquals(r, Pats.decode(Pats.encode(r)))
        assertNull(Pats.decode("not json"))
        assertNull(Pats.decode(null))
    }

    @Test
    fun theTaPattingBackReadsFromItsSide() {
        assertEquals("“星”拍了拍我", Pats.line(PatRecord(Pats.FROM_AI), "星"))
        assertEquals("“星”戳了戳我的头", Pats.line(PatRecord(Pats.FROM_AI, 1, "戳", "的头"), "星"))
    }

    @Test
    fun manyPatsInARowAskForAnAnswer() {
        assertTrue(!Pats.heavy(PatRecord(Pats.AI, Pats.HEAVY_AT - 1)))
        assertTrue(Pats.heavy(PatRecord(Pats.AI, Pats.HEAVY_AT)))
        // Only the person's pats on the TA count; their own, and the TA's, never ask for one.
        assertTrue(!Pats.heavy(PatRecord(Pats.ME, 50)))
        assertTrue(!Pats.heavy(PatRecord(Pats.FROM_AI, 50)))
        assertEquals("（对方连拍了你 12 下，拍个不停。自然回一两句就好）", Pats.forModel(PatRecord(Pats.AI, 12)))
    }
}
