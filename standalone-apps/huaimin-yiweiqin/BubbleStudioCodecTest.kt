package com.cleo.cleos.data

import org.junit.Assert.*
import org.junit.Test

class BubbleStudioCodecTest {
    @Test fun newInstallKeepsOldGlassBubblesUntouched() {
        val state = BubbleStudioCodec.decode(null)
        assertFalse(state.enabled)
        assertTrue(state.perCompanion.isEmpty())
    }

    @Test fun rolesHaveIndependentStyles() {
        val left = BubblePresets.green
        val right = BubblePresets.peach
        val state = BubbleStudioConfig().withSkin("me", left)
            .withSkin("ta", BubblePresets.milk)
            .withSkin("ta:23", right)
        assertEquals(left, state.skinFor(true, null))
        assertEquals(right, state.skinFor(false, 23))
        assertEquals(BubblePresets.milk, state.skinFor(false, 24))
        assertEquals(BubblePresets.milk, state.skinFor(false, null))
    }

    @Test fun savedThemesRoundtripThroughBackupJson() {
        val before = BubbleStudioConfig()
            .withSkin("ta:88", BubblePresets.clear)
            .savePreset("我的自定义", BubblePresets.milk)
        val after = BubbleStudioCodec.decode(BubbleStudioCodec.encode(before))
        assertTrue(after.enabled)
        assertEquals(BubblePresets.clear, after.skinFor(false, 88))
        assertEquals(BubblePresets.milk, after.saved["我的自定义"])
    }

    @Test fun corruptJsonNeverBreaksSettingsScreen() {
        assertFalse(BubbleStudioCodec.decode("{bad").enabled)
    }

    @Test fun unknownFutureFieldsDoNotBreakOlderSettings() {
        val raw = BubbleStudioCodec.encode(BubbleStudioConfig())
        val future = raw.dropLast(1) + ",\"futureField\":\"new\"}"
        assertNotNull(BubbleStudioCodec.decode(future))
    }

    @Test fun dangerousSlidersAreConstrained() {
        val bad = BubbleSkin(opacity = 0.02f, radius = -5f, blur = 200f,
            paddingHorizontal = 120f, paddingVertical = -50f,
            rim = 30f, shadow = 1f)
        val actual = bad.safe()
        assertTrue(actual.opacity >= 0.65f)
        assertTrue(actual.radius >= 6f)
        assertTrue(actual.blur <= 36f)
        assertTrue(actual.paddingHorizontal <= 28f)
        assertTrue(actual.paddingVertical >= 4f)
        assertTrue(actual.rim <= 3f)
        assertTrue(actual.shadow <= 0.36f)
    }

    @Test fun noInvalidCompanionKeysSurviveDecode() {
        val state = BubbleStudioConfig(perCompanion=mapOf("not-id" to BubblePresets.peach, "-5" to BubblePresets.milk, "23" to BubblePresets.clear))
        val reloaded = BubbleStudioCodec.decode(BubbleStudioCodec.encode(state))
        assertEquals(setOf("23"), reloaded.perCompanion.keys)
    }

    @Test fun renamingPersonDoesNotResetThemeBecauseIdsAreStable() {
        val state = BubbleStudioConfig().withSkin("ta:783", BubblePresets.peach)
        assertEquals(BubblePresets.peach, state.skinFor(false, 783))
    }

    @Test fun invalidCustomProfileDoesNotChangeOtherRoles() {
        val original = BubbleStudioConfig().withSkin("me", BubblePresets.nightMine)
        assertEquals(original, original.withSkin("ta:-33", BubblePresets.peach))
        assertEquals(original, original.withSkin("bad", BubblePresets.milk))
    }

    @Test fun presetNamesAndSizesAreBounded() {
        val original = BubbleStudioConfig()
        val named = original.savePreset("x".repeat(100), BubblePresets.green)
        assertEquals(1, named.saved.size)
        assertEquals(24, named.saved.keys.single().length)
        var next = named
        for (i in 0..35) next = next.savePreset("preset-$i", BubblePresets.peach)
        assertTrue(next.saved.size <= 24)
    }
}
