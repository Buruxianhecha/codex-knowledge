package com.cleo.cleos.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceDictationDraftTest {
    @Test fun emptyDraftGetsRecognizedText() {
        assertEquals("你好，今天天气如何？", VoiceDictationDraft.append("", " 你好，今天天气如何？ "))
    }

    @Test fun existingChineseDraftIsNotOverwrittenOrSent() {
        assertEquals("已经写过的话继续补充", VoiceDictationDraft.append("已经写过的话", "继续补充"))
    }

    @Test fun englishWordsRemainSeparated() {
        assertEquals("hello world", VoiceDictationDraft.append("hello", "world"))
    }

    @Test fun blankRecognitionPreservesDraft() {
        assertEquals("草稿内容", VoiceDictationDraft.append("草稿内容", "  "))
    }

    @Test fun whitespaceBetweenPhrasesIsNotDuplicated() {
        assertEquals("hello world", VoiceDictationDraft.append("hello ", "world"))
    }
}
