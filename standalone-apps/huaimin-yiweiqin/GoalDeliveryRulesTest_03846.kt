package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test

class GoalDeliveryRulesTest {
    private fun goal(
        description: String = "整理今天国内新闻，提供可靠摘要",
        criteria: List<GoalCriterion> = listOf(GoalCriterion("新闻摘要内容准确")),
        steps: List<GoalStep> = listOf(
            GoalStep("搜索并核实新闻来源"),
            GoalStep("整理摘要"),
            GoalStep("向用户交付内容"),
        ),
        delivery: GoalDelivery = GoalDelivery(),
    ) = GoalRecord(
        id="g-demo",conversationId=3,companionId=5,
        description=description,constraints="",
        criteria=criteria,steps=steps,status="RUNNING",delivery=delivery,
    )
    private val error="等待确认是否接受聊天正文交付；当前无 Word/PDF 文件生成及字体检查工具。"

    @Test fun ordinaryNewsSummaryDoesNotRequireFile() {
        assertFalse(GoalDeliveryRules.fileIsRequired(goal()))
    }

    @Test fun theScreenshotOriginalAcceptanceRequiresDocument() {
        assertTrue(GoalDeliveryRules.fileIsRequired(goal(
            criteria=listOf(GoalCriterion("生成文档并校验字体可读性")))))
    }

    @Test fun explicitPdfRequirementCannotBeSilentlyRelaxed() {
        assertTrue(GoalDeliveryRules.fileIsRequired(goal(description="必须生成 PDF 附件")))
    }

    @Test fun irrelevantPermissionErrorsAreNotMisclassifiedAsFormatGaps() {
        assertFalse(GoalDeliveryRules.formatGap("当前模型缺少密钥，无法生成 PDF"))
        assertFalse(GoalDeliveryRules.formatGap("发布权限拒绝，无法上传 PDF"))
        assertFalse(GoalDeliveryRules.formatGap("API 触发限流"))
    }

    @Test fun screenshotGapIsRecognized() {
        assertTrue(GoalDeliveryRules.formatGap(error))
    }

    @Test fun nonmandatoryFileGapDoesNotRequireApproval() {
        val g=goal()
        val d=GoalDeliveryRules.decide(g,g.steps,error,true)
        assertEquals("RUNNING",d?.status)
        assertEquals("CHAT_TEXT",d?.selected)
        assertFalse(d?.deferred ?: true)
    }

    @Test fun mandatoryFileButResearchStillPendingContinuesFirst() {
        val g=goal(criteria=listOf(GoalCriterion("生成文档并校验字体可读性")))
        val d=GoalDeliveryRules.decide(g,g.steps,error,true)
        assertEquals("RUNNING",d?.status)
        assertTrue(d?.deferred == true)
        assertNull(d?.selected)
    }

    @Test fun mandatoryDocumentAfterResearchIsDoneMustStop() {
        val steps=listOf(GoalStep("搜索并核实新闻来源","DONE"),
            GoalStep("整理摘要","DONE"),GoalStep("生成 PDF 文档"))
        val g=goal(criteria=listOf(GoalCriterion("生成文档并校验字体可读性")),steps=steps)
        val d=GoalDeliveryRules.decide(g,steps,error,true)
        assertEquals("WAITING_INPUT",d?.status)
    }

    @Test fun repetitiveFormatBlockersAreBounded() {
        val g=goal(criteria=listOf(GoalCriterion("生成 Word 文件")),
            delivery=GoalDelivery(deferredCount=2))
        assertEquals("WAITING_INPUT",GoalDeliveryRules.decide(g,g.steps,error,true)?.status)
    }

    @Test fun noInputFlagMeansBlockedWhenMandatoryAndExhausted() {
        val g=goal(criteria=listOf(GoalCriterion("导出 PDF 文件")),
            steps=listOf(GoalStep("整理摘要","DONE"),GoalStep("生成 PDF 文件")))
        assertEquals("BLOCKED",GoalDeliveryRules.decide(g,g.steps,error,false)?.status)
    }

    @Test fun unrelatedBlockerKeepsOriginalGoalBehavior() {
        val g=goal()
        assertNull(GoalDeliveryRules.decide(g,g.steps,"联网搜索失败：服务器拒绝连接",true))
    }

    @Test fun userConfirmedMarkdownRemovesOldFileObligationOnlyByExplicitAction() {
        val g=goal(criteria=listOf(GoalCriterion("新闻摘要准确"),
            GoalCriterion("生成文档并校验字体可读性")))
        val newCriteria=GoalDeliveryRules.replacedCriteria(g,"MARKDOWN")
        assertEquals(2,newCriteria.size)
        assertEquals("UNVERIFIED",newCriteria[0].status)
        assertTrue(newCriteria[1].text.contains("Markdown"))
        assertFalse(GoalDeliveryRules.fileIsRequired(g.copy(criteria=newCriteria,
            delivery=GoalDelivery("MARKDOWN",userConfirmed=true))))
    }

    @Test fun userSelectedChatTextMustStillBeVerified() {
        val g=goal(criteria=listOf(GoalCriterion("生成 PDF 文档")))
        val next=GoalDeliveryRules.replacedCriteria(g,"CHAT_TEXT")
        assertTrue(next.all { it.status=="UNVERIFIED" && it.evidence.isBlank() })
        assertFalse(GoalRules.canComplete(next))
    }

    @Test fun userConfirmedMarkdownRetainsSelectionWithoutAnotherPrompt() {
        val g=goal(delivery=GoalDelivery(selected="MARKDOWN",userConfirmed=true))
        val d=GoalDeliveryRules.decide(g,g.steps,error,true)
        assertEquals("MARKDOWN",d?.selected)
        assertEquals("RUNNING",d?.status)
    }

    @Test fun pendingContentDetectionExcludesCompletedResearch() {
        assertFalse(GoalDeliveryRules.pendingContent(
            listOf(GoalStep("搜索来源",status="DONE"),GoalStep("生成 PDF",status="PENDING"))))
        assertTrue(GoalDeliveryRules.pendingContent(
            listOf(GoalStep("搜索来源",status="ACTIVE"),GoalStep("生成 PDF",status="PENDING"))))
    }
}
