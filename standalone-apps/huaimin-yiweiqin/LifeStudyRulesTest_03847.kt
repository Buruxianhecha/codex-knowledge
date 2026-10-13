package com.cleo.cleos.ai

import com.cleo.cleos.data.LifeLesson
import org.junit.Assert.*
import org.junit.Test

class LifeStudyRulesTest {
    private val lesson = LifeLesson("segment", "测试片段", "用一个变量排除故障，重复验证后记录证据。", 0, 20,
        "diagnosis", "故障分析", "应该怎么排查？", listOf("一起改所有项", "一次只改一项"), 1,
        "只有隔离变量才可判断原因", "新的设备故障怎么办？", listOf("逐项验证", "直接假定修好"), 0)
    @Test fun realNotesRequired() {
        assertThrows(IllegalArgumentException::class.java) { LifeStudyRules.summaryFrom("{\"summary\":\"已学会\"}") }
    }
    @Test fun markdownJsonAccepted() {
        val note = "这段材料要求逐项隔离变量，通过重复验证确定原因；应该保存实际证据，不能一次改动多个条件就声称定位成功。"
        assertEquals(note, LifeStudyRules.summaryFrom("```json\n{\"summary\":\"" + note + "\"}\n```"))
    }
    @Test fun bothAnswersRequiredForApplicationLesson() {
        assertThrows(IllegalArgumentException::class.java) { LifeStudyRules.answersFrom("{\"answerIndex\":1}", lesson) }
    }
    @Test fun outOfRangeAnswersRejected() {
        assertThrows(IllegalArgumentException::class.java) { LifeStudyRules.answersFrom("{\"answerIndex\":22,\"applicationAnswerIndex\":0}", lesson) }
    }
    @Test fun validTwoAnswersPreserved() {
        assertEquals(1 to 0, LifeStudyRules.answersFrom("{\"answerIndex\":1,\"applicationAnswerIndex\":0}", lesson))
    }
    @Test fun gradingSecretNeverInModelPrompt() {
        val prompt = LifeStudyRules.testPrompt(lesson, "实际学习笔记")
        assertFalse(prompt.contains(lesson.explanation))
        assertFalse(prompt.contains("correctIndex"))
        assertFalse(prompt.contains("transferCorrectIndex"))
    }
}
