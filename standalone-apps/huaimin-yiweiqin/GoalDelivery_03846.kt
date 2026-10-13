package com.cleo.cleos.ai

import kotlinx.serialization.Serializable

/**
 * This is a delivery policy, NOT evidence that a file or a chat message exists.
 * The user can request a fallback explicitly; automatic fallback is allowed only
 * when the original description/constraints/acceptance do not require a file.
 */
@Serializable
data class GoalDelivery(
    val selected: String = "AUTO",
    val userConfirmed: Boolean = false,
    val capabilityGap: String = "",
    val deferredCount: Int = 0,
)

data class GoalDeliveryDecision(
    val status: String,
    val notice: String,
    val selected: String? = null,
    val deferred: Boolean = false,
)

object GoalDeliveryRules {
    val types = setOf("AUTO", "CHAT_TEXT", "MARKDOWN", "WORD", "PDF")
    private val fileWords = Regex("(?i)(PDF|DOCX?|Word|\\.docx?|\\.pdf|文档|文件|附件|字体)")
    private val requiredAction = Regex("(必须|务必|需要|要求|交付|导出|生成|提供|下载|保存|校验|检查|验收|可读)")
    private val contentWords = Regex("(检索|搜索|查询|调查|来源|核实|整理|汇总|分析|摘要|内容|资料|新闻|撰写|事实|引用)")
    private val missingWords = Regex("(缺少|没有|无.{0,8}工具|无法|不能|不可用|不支持|未接入|需要工具|等待确认|没有权限)")
    private val forbiddenWords = Regex("(缺少密钥|无效密钥|权限拒绝|支付|转账|发送|发布|上传|账户登录|凭据|访问令牌|网络失败|限流)")

    /** Old persisted goals have no delivery field; infer requirements from THEIR criteria. */
    fun fileIsRequired(g: GoalRecord): Boolean {
        val acceptance = g.criteria.any { fileWords.containsMatchIn(it.text) &&
            requiredAction.containsMatchIn(it.text) }
        val explicit = listOf(g.description, g.constraints).any {
            fileWords.containsMatchIn(it) && requiredAction.containsMatchIn(it)
        }
        return acceptance || explicit
    }

    /** Requires both an actual delivery-capability failure and format keywords.
     * It must never swallow missing credentials, payment or external authorization.
     */
    fun formatGap(reason: String): Boolean =
        reason.isNotBlank() && fileWords.containsMatchIn(reason) &&
        missingWords.containsMatchIn(reason) && !forbiddenWords.containsMatchIn(reason)

    fun pendingContent(steps: List<GoalStep>): Boolean =
        steps.any { it.status !in setOf("DONE") && contentWords.containsMatchIn(it.title) }

    fun decide(g: GoalRecord, steps: List<GoalStep>, reason: String, needsInput: Boolean): GoalDeliveryDecision? {
        if (!formatGap(reason)) return null
        // A model may use needs_input=true even when nothing essential is missing.
        val required = fileIsRequired(g) && !g.delivery.userConfirmed
        if (!required) {
            return GoalDeliveryDecision(
                status = "RUNNING",
                notice = "当前没有必须导出 Word/PDF 的验收要求，改用聊天正文整理；文件和字体检查未完成，不会伪造为已完成。",
                selected = g.delivery.selected.takeIf { it in setOf("CHAT_TEXT", "MARKDOWN") } ?: "CHAT_TEXT",
            )
        }
        if (pendingContent(steps) && g.delivery.deferredCount < 2) {
            return GoalDeliveryDecision(
                status = "RUNNING",
                notice = "先继续检索与正文整理；文件交付及字体检查依然是必需验收项，尚未通过。",
                deferred = true,
            )
        }
        return GoalDeliveryDecision(
            status = if (needsInput) "WAITING_INPUT" else "BLOCKED",
            notice = "当前目标明确要求文件交付或字体校验，但本机没有对应工具。内容整理结果可保留；如要改为正文/Markdown，必须由你明确确认变更验收要求。",
        )
    }

    /** Explicit choice changes required acceptance; it must NOT carry old PASSED marks. */
    fun replacedCriteria(g: GoalRecord, selected: String): List<GoalCriterion> {
        val label = when (selected) {
            "MARKDOWN" -> "Markdown 正文已实际交付，内容可阅读且符合要求"
            else -> "聊天正文已实际交付，内容可阅读且符合要求"
        }
        val updated = g.criteria.map {
            if (fileWords.containsMatchIn(it.text) && requiredAction.containsMatchIn(it.text))
                GoalCriterion(label)
            else it.copy(status = "UNVERIFIED", evidence = "", source = "")
        }
        return if (updated.none { it.text == label }) updated + GoalCriterion(label) else updated
    }
}
