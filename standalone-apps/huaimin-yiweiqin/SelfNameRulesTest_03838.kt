package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

/** Tests data integrity only; natural-language intent belongs to the model's chat context. */
class SelfNameRulesTest {
    @Test fun trimsAChosenName() {
        assertEquals("知晴", SelfNameRules.cleanName("  知晴  "))
    }
    @Test fun emptyNameIsNotARealName() {
        assertNull(SelfNameRules.cleanName("   "))
    }
    @Test fun collapsesWhitespaceInName() {
        assertEquals("晴 星", SelfNameRules.cleanName("晴     星"))
    }
    @Test fun newlineIsNormalizedRatherThanStoredInContactName() {
        assertEquals("小 晴", SelfNameRules.cleanName("小\n晴"))
    }
    @Test fun controlCharacterIsRejected() {
        assertNull(SelfNameRules.cleanName("小\u0000晴"))
    }
    @Test fun longNameIsRejectedToFitExistingUi() {
        assertNull(SelfNameRules.cleanName("abcdefghijklmnop"))
    }
    @Test fun emojiAndChineseNameAreAllowed() {
        assertEquals("晴晴🌙", SelfNameRules.cleanName("晴晴🌙"))
    }
    @Test fun bidiOverrideIsRejected() {
        assertNull(SelfNameRules.cleanName("晴\u202E星"))
    }
}
