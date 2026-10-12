package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class SelfNameRulesTest {
    @Test fun userExplicitlyAsksAiToChooseAndSaveName() {
        assertTrue(SelfNameRules.explicitlyAsked("你自己选个喜欢的名字，然后直接换上"))
    }
    @Test fun userDirectsAiToRenameItself() {
        assertTrue(SelfNameRules.explicitlyAsked("那你给自己改个名字吧"))
    }
    @Test fun userDelegatesNameDecision() {
        assertTrue(SelfNameRules.explicitlyAsked("名字由你自己决定，选好就换上"))
    }
    @Test fun abilityQuestionIsNotChangeAuthorization() {
        assertFalse(SelfNameRules.explicitlyAsked("你可以给自己换名字吗？"))
    }
    @Test fun thirdPersonNameChatCannotAuthorizeWrite() {
        assertFalse(SelfNameRules.explicitlyAsked("这个园区叫什么名字？"))
    }
    @Test fun mereMentionOfAnotherPersonCannotRename() {
        assertFalse(SelfNameRules.explicitlyAsked("帮小艺改名，叫小月"))
    }
    @Test fun trimsChosenNameWithoutTouchingMeaning() {
        assertEquals("星野", SelfNameRules.cleanName("  星野  "))
    }
    @Test fun rejectsBlankName() {
        assertNull(SelfNameRules.cleanName("   "))
    }
    @Test fun rejectsNewlinesAndControlCharacters() {
        assertEquals("星 野", SelfNameRules.cleanName("星\n野"))
        assertNull(SelfNameRules.cleanName("坏\u0000名字"))
    }
    @Test fun rejectsTooLongName() {
        assertNull(SelfNameRules.cleanName("abcdefghijklmnop"))
    }
    @Test fun acceptsShortEmojiContainingName() {
        assertEquals("小星🌙", SelfNameRules.cleanName("小星🌙"))
    }
    @Test fun rejectsBidiOverride() {
        assertNull(SelfNameRules.cleanName("星\u202E野"))
    }
}
