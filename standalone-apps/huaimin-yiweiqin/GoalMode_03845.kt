package com.cleo.cleos.ai

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Durable, scoped, fail-closed goal state. No credentials or raw tool arguments are logged. */
@Serializable
data class GoalCriterion(val text: String, val status: String = "UNVERIFIED",
                         val evidence: String = "", val source: String = "")
@Serializable
data class GoalStep(val title: String, val status: String = "PENDING",
                    val result: String = "", val evidence: String = "")
@Serializable
data class GoalReceipt(val id: String, val tool: String, val at: Long, val version: Int,
                       val verified: Boolean, val summary: String)
@Serializable
data class GoalAction(val name: String, val fingerprint: String, val at: Long)
@Serializable
data class GoalEvent(val at: Long, val version: Int, val type: String, val detail: String)
@Serializable
data class GoalBudget(val maxRounds: Int = 18, val maxMinutes: Int = 25) {
    fun safe() = copy(maxRounds=maxRounds.coerceIn(1,200), maxMinutes=maxMinutes.coerceIn(1,240))
}
@Serializable
data class GoalRecord(
    val id: String,
    val conversationId: Long,
    val companionId: Long,
    val version: Int = 1,
    val description: String,
    val constraints: String,
    val criteria: List<GoalCriterion>,
    val steps: List<GoalStep> = emptyList(),
    val status: String = "PENDING",
    val budget: GoalBudget = GoalBudget(),
    val rounds: Int = 0,
    val elapsedMs: Long = 0,
    val runningSince: Long? = null,
    val lastError: String = "",
    val pending: GoalAction? = null,
    val approvedFingerprint: String? = null,
    val inFlight: GoalAction? = null,
    /** Never automatically replay operations with ambiguous crash outcomes. */
    val uncertainFingerprints: List<String> = emptyList(),
    val receipts: List<GoalReceipt> = emptyList(),
    val events: List<GoalEvent> = emptyList(),
    val noProgress: Int = 0,
    val delivery: GoalDelivery = GoalDelivery(),
    val updatedAt: Long = System.currentTimeMillis(),
)
@Serializable private data class GoalArchive(val schema: Int = 1, val goals: List<GoalRecord> = emptyList())

object GoalRules {
    val terminal = setOf("COMPLETED","CANCELED","FAILED")
    val active = setOf("PENDING","RUNNING","VERIFYING","WAITING_INPUT","PAUSED","BLOCKED")
    val states = terminal + active
    val evidence = setOf("PASSED","FAILED","UNVERIFIED")
    fun fingerprint(tool: String, args: String): String {
        val hash=MessageDigest.getInstance("SHA-256").digest(("$tool\u0000$args").toByteArray())
        return hash.take(12).joinToString("") { "%02x".format(it) }
    }
    fun sideEffect(tool: String, external: Boolean = false): Boolean {
        if (external) return true // MCP: unknown external effects need explicit approval.
        return tool !in setOf(
            "goal_checkpoint", "get_weather", "get_location", "read_diary", "read_calendar",
            "get_next_alarm", "show_alarms", "list_todos", "read_todos", "search_chat_history",
            "read_chat_message", "count_gallery_photos", "list_gallery_photos",
            "inspect_gallery_photos", "search_web_avatar_images", "read_letters",
        )
    }
    /** A generic tool result proves only that a tool responded; not semantic correctness.
     * Automatically pass only an explicitly checkable "工具调用：name" criterion.
     * Other acceptance rules need a stronger typed verifier or user approval.
     */
    fun toolReceiptProves(criterion: String, tool: String): Boolean =
        criterion.trim() == "工具调用：$tool" ||
            criterion.trim().equals("tool-called:$tool",ignoreCase=true)
    fun canComplete(criteria: List<GoalCriterion>): Boolean =
        criteria.isNotEmpty() && criteria.all { it.status == "PASSED" && it.evidence.isNotBlank() }
    fun resumeState(record: GoalRecord): GoalRecord = when {
        record.inFlight != null -> record.copy(
            status="BLOCKED", runningSince=null, approvedFingerprint=null,
            lastError="上次执行中断，工具「${record.inFlight.name}」是否生效尚不确定。请核实外部效果后再继续。",
        )
        record.status in setOf("RUNNING","VERIFYING") -> record.copy(
            status="PAUSED",runningSince=null,
            lastError="应用进程中断，检查点已保留。请点击继续以恢复；不会自动重做上一项外部操作。",
        )
        else -> record
    }
}

/**
 * AtomicFile replaces the complete small goal journal in one fsync-ed transaction.
 * The data survives UI destruction/process death. It is NOT a cloud worker:
 * on process restart incomplete goals are paused or blocked for reconciliation.
 */
class GoalStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "huaimin-goals-v1.json"))
    private val mutex=Mutex()
    private val json=Json { ignoreUnknownKeys=true; encodeDefaults=true }
    private fun load(): List<GoalRecord> = runCatching {
        json.decodeFromString<GoalArchive>(file.openRead().bufferedReader().use { it.readText() })
            .goals.map(GoalRules::resumeState).map { g ->
                if (g.status=="WAITING_INPUT" && g.pending==null &&
                    GoalDeliveryRules.formatGap(g.lastError))
                    g.copy(status="PAUSED",
                        lastError="检测到旧版本将文件能力缺口错误当作关键授权。原要求保留；点继续可先完成其他可执行步骤，或明确改成交付正文。")
                else g
            }
    }.getOrElse { emptyList() }
    private val _records=MutableStateFlow(load())
    val records: StateFlow<List<GoalRecord>> = _records

    private suspend fun write(all: List<GoalRecord>) = withContext(Dispatchers.IO) {
        val stream=file.startWrite()
        try {
            stream.write(json.encodeToString(GoalArchive(goals=all)).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    private suspend fun change(id: String, apply: (GoalRecord) -> GoalRecord): GoalRecord? =
        mutex.withLock {
            val previous=_records.value
            val index=previous.indexOfFirst { it.id == id }
            if(index<0) return@withLock null
            val updated=apply(previous[index])
            if(updated==previous[index]) return@withLock updated
            val all=previous.toMutableList().also { it[index]=updated.copy(updatedAt=System.currentTimeMillis()) }
            write(all)
            _records.value=all
            all[index]
        }

    fun active(conversationId: Long, companionId: Long): GoalRecord? =
        _records.value.lastOrNull { it.conversationId==conversationId &&
            it.companionId==companionId && it.status !in GoalRules.terminal }

    fun byId(id: String) = _records.value.firstOrNull { it.id==id }

    suspend fun create(conversationId: Long, companionId: Long, description: String,
        constraints: String, criteria: List<String>, steps: List<String>, budget: GoalBudget, replaceActive: Boolean = false): GoalRecord? =
        mutex.withLock {
            if (description.isBlank() || criteria.isEmpty() || criteria.any { it.isBlank() })
                return@withLock null
            val old=_records.value.filter { it.conversationId==conversationId &&
                it.companionId==companionId && it.status !in GoalRules.terminal }
            if(old.isNotEmpty() && !replaceActive) return@withLock null
            val now=System.currentTimeMillis()
            val goal=GoalRecord(
                id=UUID.randomUUID().toString(),conversationId=conversationId,companionId=companionId,
                description=description.trim().take(3000),constraints=constraints.take(3000),
                criteria=criteria.take(20).map { GoalCriterion(it.trim().take(350)) },
                steps=steps.take(30).map { GoalStep(it.trim().take(220)) }.filter { it.title.isNotBlank() },
                budget=budget.safe(),events=listOf(GoalEvent(now,1,"created","已保存目标和验收项")),
                updatedAt=now,
            )
            val updated=_records.value.map { g -> if(old.any { it.id==g.id })
                g.copy(status="CANCELED",runningSince=null,approvedFingerprint=null,
                    events=ev(g,"replaced","用户明确选择终止旧目标并创建新目标"))
                else g } + goal
            write(updated);_records.value=updated;goal
        }

    private fun ev(g: GoalRecord, type: String, detail: String) =
        (g.events+GoalEvent(System.currentTimeMillis(),g.version,type,detail.take(500))).takeLast(250)
    private fun spent(g: GoalRecord): Long = g.elapsedMs +
        (g.runningSince?.let { (System.currentTimeMillis()-it).coerceAtLeast(0L) } ?: 0L)
    suspend fun pause(id: String): GoalRecord? = change(id) { g ->
        if(g.status in GoalRules.terminal) g else g.copy(status="PAUSED",
            elapsedMs=spent(g),runningSince=null,approvedFingerprint=null,
            events=ev(g,"paused","用户暂停"))
    }
    suspend fun cancel(id: String): GoalRecord? = change(id) { g ->
        if(g.status in GoalRules.terminal) g else g.copy(status="CANCELED",
            elapsedMs=spent(g),runningSince=null,approvedFingerprint=null,
            events=ev(g,"canceled","用户取消，已生效操作不自动撤销"))
    }
    suspend fun clear(id: String): GoalRecord? = cancel(id) // History deliberately kept.
    suspend fun edit(id: String, description: String, constraints: String,
                     criteria: List<String>, steps: List<String>, budget: GoalBudget): GoalRecord? =
        change(id) { g ->
            if (g.status in GoalRules.terminal || description.isBlank() || criteria.isEmpty()) g
            else g.copy(version=g.version+1,description=description.trim().take(3000),
                constraints=constraints.take(3000),
                criteria=criteria.map { GoalCriterion(it.trim().take(350)) }.filter { it.text.isNotBlank() }.take(20),
                steps=steps.map { GoalStep(it.trim().take(220)) }.filter { it.title.isNotBlank() }.take(30),
                budget=budget.safe(),status="PAUSED",rounds=0,noProgress=0,
                elapsedMs=spent(g),runningSince=null,pending=null,approvedFingerprint=null,
                inFlight=null,lastError="",events=ev(g,"edited","目标变更，旧验收作废"))
        }
    suspend fun resume(id: String): GoalRecord? = change(id) { g ->
        if(g.status !in setOf("PENDING","PAUSED","BLOCKED","WAITING_INPUT","VERIFYING") || g.inFlight!=null || g.pending!=null) g
        else g.copy(status="RUNNING",runningSince=System.currentTimeMillis(),
            lastError="",noProgress=0,events=ev(g,"resumed","恢复目标执行"))
    }
    /** User reviewed an interrupted operation; it remains unverified, never replayed as done. */
    suspend fun reconcile(id: String): GoalRecord? = change(id) { g ->
        if(g.inFlight==null || g.status in GoalRules.terminal) g
        else g.copy(inFlight=null,status="PAUSED",pending=null,approvedFingerprint=null,
            uncertainFingerprints=(g.uncertainFingerprints+g.inFlight.fingerprint).distinct().takeLast(80),
            lastError="请检查历史消息或外部服务确认上一操作结果；此处未将它标记为成功。",
            events=ev(g,"reconciled","用户确认已检查中断操作；外部效果仍需证据"))
    }
    suspend fun adjustBudget(id: String, newBudget: GoalBudget): GoalRecord? = change(id) { g ->
        if(g.status in GoalRules.terminal) g
        else g.copy(budget=newBudget.safe(),events=ev(g,"budget_changed","用户调整预算"))
    }
    suspend fun userNote(id: String, note: String): GoalRecord? = change(id) { g ->
        if(g.status in GoalRules.terminal) g
        else g.copy(version=g.version+1, constraints=(g.constraints+"\\n补充："+note.take(1000)).takeLast(3000),
            criteria=g.criteria.map { it.copy(status="UNVERIFIED",evidence="",source="") },
            status="PAUSED",runningSince=null,elapsedMs=spent(g),noProgress=0,
            pending=null,approvedFingerprint=null,inFlight=null,
            events=ev(g,"updated","用户补充了约束；旧验收状态作废"))
    }
    /** An explicit fallback is a USER change to required acceptance, never model self-authorization. */
    suspend fun chooseDelivery(id: String, selected: String): GoalRecord? = change(id) { g ->
        if (g.status in GoalRules.terminal || selected !in setOf("CHAT_TEXT","MARKDOWN")) g
        else g.copy(
            version=g.version+1,
            criteria=GoalDeliveryRules.replacedCriteria(g,selected),
            delivery=GoalDelivery(selected=selected,userConfirmed=true),
            steps=g.steps.map { step ->
                if (Regex("(?i)(PDF|Word|DOCX?|生成文档|导出|字体)").containsMatchIn(step.title))
                    step.copy(title=if(selected=="MARKDOWN") "以 Markdown 形式交付已整理内容" else "在聊天正文中交付已整理内容",
                        status="PENDING",result="",evidence="")
                else step
            },
            status="PAUSED", elapsedMs=spent(g), runningSince=null, noProgress=0,
            pending=null,approvedFingerprint=null,inFlight=null,lastError="",
            events=ev(g,"delivery_changed","用户明确将交付及验收要求改为 $selected；所有旧验收状态重新评估")
        )
    }

    suspend fun approve(id: String): GoalRecord? = change(id) { g ->
        if(g.status!="WAITING_INPUT"||g.pending==null) g
        else g.copy(approvedFingerprint=g.pending.fingerprint,status="RUNNING",
            pending=null,runningSince=System.currentTimeMillis(),
            events=ev(g,"approved","用户针对一次指定工具操作授权"))
    }
    suspend fun verify(id: String, criterionIndex: Int, passed: Boolean): GoalRecord? = change(id) { g ->
        if(g.status in GoalRules.terminal || criterionIndex !in g.criteria.indices) g
        else {
            val next=g.criteria.toMutableList()
            next[criterionIndex]=next[criterionIndex].copy(status=if(passed) "PASSED" else "FAILED",
                evidence=if(passed) "用户手动确认" else "用户未通过",source="user")
            val status=if(GoalRules.canComplete(next)) "COMPLETED" else "VERIFYING"
            g.copy(criteria=next,status=status,elapsedMs=if(status=="COMPLETED") spent(g) else g.elapsedMs,
                runningSince=if(status=="COMPLETED") null else g.runningSince,
                events=ev(g,"verification","第${criterionIndex+1}项由用户${if(passed) "通过" else "否决"}"))
        }
    }

    suspend fun startCall(id: String, version: Int): Boolean {
        val after=change(id) { g ->
            if(g.version!=version || g.status!="RUNNING") return@change g
            if(g.rounds>=g.budget.maxRounds || spent(g)>=g.budget.maxMinutes*60_000L)
                g.copy(status="PAUSED",elapsedMs=spent(g),runningSince=null,
                    lastError="已达到模型轮数或执行时长预算，请调整目标预算后继续。",
                    events=ev(g,"budget","到达预算，已保存检查点"))
            else g.copy(rounds=g.rounds+1, events=ev(g,"model_call","开始模型调用 #${g.rounds+1}"))
        }
        return after?.version==version && after.status=="RUNNING"
    }

    suspend fun beginTool(id: String,version: Int,callId: String,tool: String,args: String,
        external: Boolean): Boolean {
        val fp=GoalRules.fingerprint(tool,args)
        var allowed=false
        change(id) { g ->
            if(g.version!=version || g.status!="RUNNING") return@change g
            if(fp in g.uncertainFingerprints) {
                g.copy(status="BLOCKED",elapsedMs=spent(g),runningSince=null,
                    approvedFingerprint=null,lastError="该工具参数与中断时结果不明的操作完全相同，为防止重复外部效果，拒绝再次调用。请修改行动方案或提供真实核验依据。",
                    events=ev(g,"duplicate_protected","拒绝重放中断操作 $tool"))
            } else if(GoalRules.sideEffect(tool,external) && g.approvedFingerprint!=fp) {
                g.copy(status="WAITING_INPUT",elapsedMs=spent(g),runningSince=null,
                    pending=GoalAction(tool,fp,System.currentTimeMillis()),approvedFingerprint=null,
                    lastError="工具「$tool」可能产生外部效果。需要你核对后授权；未执行。",
                    events=ev(g,"approval_required","等待确认工具「$tool」"))
            } else {
                allowed=true
                g.copy(inFlight=GoalAction(tool,fp,System.currentTimeMillis()),
                    approvedFingerprint=if(g.approvedFingerprint==fp) null else g.approvedFingerprint,
                    events=ev(g,"tool_started","工具 $tool，请求 $callId"))
            }
        }
        return allowed
    }
    suspend fun toolResult(id: String,version: Int,callId: String,tool: String,
        success: Boolean, verified: Boolean, summary: String): GoalRecord? = change(id) { g ->
        if(g.version!=version || g.status in GoalRules.terminal) g
        else g.copy(
            inFlight=null,
            receipts=(g.receipts+GoalReceipt(callId,tool,System.currentTimeMillis(),version,
                verified && success, summary.take(350))).takeLast(100),
            events=ev(g,"tool_result","工具 $tool 返回，${if(success) "收到响应" else "未成功"}（${callId.take(40)}）"),
        )
    }

    suspend fun checkpoint(id: String,version: Int,args: JsonObject): String {
        var feedback="目标已变更或暂停，此检查点已被丢弃。"
        change(id) { g ->
            if(g.version!=version || g.status !in setOf("RUNNING","VERIFYING")) return@change g
            fun field(k:String)=args[k]?.jsonPrimitive?.contentOrNull.orEmpty()
            fun index(k:String)=args[k]?.jsonPrimitive?.intOrNull
            var steps=g.steps
            val plan=(args["plan"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.take(220) }
                ?.filter { it.isNotBlank() }?.take(30).orEmpty()
            if(plan.isNotEmpty() && (steps.isEmpty() || field("replace_plan")=="true"))
                steps=plan.map { GoalStep(it) }
            val si=index("step_index")
            val receipt=field("evidence_id")
            val verified=g.receipts.any { it.id==receipt && it.version==version && it.verified }
            if(si!=null && si in steps.indices) {
                steps=steps.toMutableList().also { ls ->
                    ls[si]=ls[si].copy(status=when(field("step_state")) {
                        "DONE" -> if(verified) "DONE" else "VERIFYING"
                        "BLOCKED" -> "BLOCKED"
                        "ACTIVE" -> "ACTIVE"
                        else -> ls[si].status
                    },result=field("result").take(1500),evidence=if(verified) receipt else "")
                }
            }
            val criteria=g.criteria.toMutableList()
            val ci=index("criterion_index")
            if(ci!=null && ci in criteria.indices) {
                val candidate=g.receipts.firstOrNull { it.id==receipt && it.version==version && it.verified }
                val pass=field("criterion_state")=="PASSED" && candidate!=null &&
                    GoalRules.toolReceiptProves(criteria[ci].text,candidate.tool)
                val fail=field("criterion_state")=="FAILED"
                criteria[ci]=criteria[ci].copy(
                    status=if(pass) "PASSED" else if(fail) "FAILED" else "UNVERIFIED",
                    evidence=if(pass) receipt else if(fail) field("result").take(350) else "",
                    source=if(pass) "verified_tool" else "model_unverified",
                )
            }
            val reason=field("blocking_reason").take(400)
            val wait=field("needs_input")=="true"
            val done=GoalRules.canComplete(criteria)
            val decision=if(done) null else GoalDeliveryRules.decide(g,steps,reason,wait)
            val status=when {
                done -> "COMPLETED"
                decision!=null -> decision.status
                wait -> "WAITING_INPUT"
                reason.isNotBlank() -> "BLOCKED"
                field("verification_phase")=="true" -> "VERIFYING"
                else -> "RUNNING"
            }
            val newDelivery=when {
                decision==null -> g.delivery
                else -> g.delivery.copy(
                    selected=decision.selected ?: g.delivery.selected,
                    capabilityGap=decision.notice,
                    deferredCount=g.delivery.deferredCount+if(decision.deferred) 1 else 0,
                )
            }
            val progressed=steps!=g.steps || criteria!=g.criteria
            val stalls=if(progressed) 0 else g.noProgress+1
            feedback=if(field("criterion_state")=="PASSED" && !verified)
"没有与该验收标准严格对应的程序可验证依据；该验收项仍为未验证。需用户核实或使用专用验证器。"
                else "检查点已持久保存；${criteria.count { it.status=="PASSED" }}/${criteria.size}项有证据通过。"
            g.copy(steps=steps,criteria=criteria,status=status,delivery=newDelivery,
                lastError=if(status in setOf("WAITING_INPUT","BLOCKED"))
                    decision?.notice ?: reason.ifBlank { "等待用户提供必要信息" } else "",
                noProgress=stalls,
                elapsedMs=if(status!="RUNNING") spent(g) else g.elapsedMs,
                runningSince=if(status!="RUNNING") null else g.runningSince,
                events=ev(g,"checkpoint","步骤/验收状态已更新；${feedback.take(100)}"))
        }
        return feedback
    }
    suspend fun noProgress(id:String,version:Int): GoalRecord? = change(id) { g ->
        if(g.version!=version || g.status!="RUNNING") g
        else if(g.noProgress>=3) g.copy(status="BLOCKED",
            elapsedMs=spent(g),runningSince=null,
            lastError="连续多轮没有有效进展，需要检查工具、资料或修改目标。",
            events=ev(g,"stalled","连续无进展，停止自动调度"))
        else g.copy(noProgress=g.noProgress+1)
    }

    fun context(g: GoalRecord): String = buildString {
        appendLine("【GOAL MODE 专用系统规则】目标编号 ${g.id} 版本 ${g.version}。每轮必须以结构化 goal_checkpoint 上报真实进展。")
        appendLine("只围绕当前目标；使用现有已授权工具，缺能力直说。给出阶段结果后仍需继续；不要靠‘完成’等自然语言结束。")
        appendLine("外部操作必须按真实工具结果判断，先申请必要授权；不要编造检索/文件/测试/部署；未经验证不可以宣称通过。")
        appendLine("用户暂停/取消/修改优先。失败有限重试且不重复未知是否成功的不可逆操作。")
        appendLine("目标：${g.description}")
        appendLine("约束：${g.constraints}")
        appendLine("交付策略：用户选定=${g.delivery.selected}；原目标强制文件=${GoalDeliveryRules.fileIsRequired(g)}；用户确认变更=${g.delivery.userConfirmed}。")
        if (g.delivery.capabilityGap.isNotBlank())
            appendLine("已识别能力缺口：${g.delivery.capabilityGap}。缺少 Word/PDF 时，优先做可完成的检索、核实和正文；不能凭空通过文件/字体验收。")
        if (g.delivery.selected in setOf("CHAT_TEXT","MARKDOWN"))
            appendLine("已选定 ${g.delivery.selected} 交付。不要再次请求允许改成聊天正文，不可声称生成了文件。")
        appendLine("验收：${g.criteria.mapIndexed { i,c -> "${i}: ${c.text} [${c.status}] ${c.evidence}" }.joinToString(" ; ")}")
        appendLine("步骤：${g.steps.mapIndexed { i,s -> "${i}: ${s.title} [${s.status}] ${s.result.take(140)}" }.joinToString(" ; ")}")
        appendLine("最近工具收据：${g.receipts.takeLast(7).joinToString(" ; ") { "${it.id}: ${it.tool}, verified=${it.verified}, ${it.summary.take(100)}" }}")
        appendLine("轮数：${g.rounds}/${g.budget.maxRounds}；时间预算：${g.budget.maxMinutes}分钟；实际费用/Token未报告，不作精确估计。")
        appendLine("下一步：如还没有步骤，先调用goal_checkpoint(plan=...).然后执行下一可行步骤并汇报step_index、step_state和证据ID。")
        appendLine("仅缺少非必需的文件导出或字体工具时不要请求用户许可，先给出可验证的正文。若文件是必需验收，继续其他未完成步骤；只在已无可执行步骤时上报blocking_reason，等待用户亲自变更交付要求。")
        appendLine("需要关键外部信息、真实授权或已无可执行步骤时才上报needs_input/blocking_reason；状态始终由程序控制。")
    }
}
