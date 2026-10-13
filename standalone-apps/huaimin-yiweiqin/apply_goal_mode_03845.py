#!/usr/bin/env python3
"""0.38.45: bind persistent, versioned Goal Mode to the EXISTING ChatRepository tool runner."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve(); here=Path(__file__).resolve().parent
def one(p, old, new):
    s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1: raise RuntimeError(f"goal patch precondition {p.name}, count={n}: {old[:110]!r}")
    p.write_text(s.replace(old,new,1),encoding="utf-8")

ai=root/"app/src/main/java/com/cleo/cleos/ai"
copyfile(here/"GoalMode_03845.kt",ai/"GoalMode.kt")
copyfile(here/"GoalCheckpointTool_03845.kt",ai/"GoalCheckpointTool.kt")

app=root/"app/src/main/java/com/cleo/cleos/CleosApp.kt"
one(app,"import com.cleo.cleos.ai.ChatRepository\n",
    "import com.cleo.cleos.ai.ChatRepository\nimport com.cleo.cleos.ai.GoalStore\n")
one(app,"    val chat: ChatRepository = ChatRepository(\n",
    "    val goalStore = GoalStore(context.applicationContext)\n    val chat: ChatRepository = ChatRepository(\n")
one(app,"        personalityGrowth = personalityGrowth,\n",
    "        personalityGrowth = personalityGrowth,\n        goalStore = goalStore,\n")

chat=ai/"ChatRepository.kt"
one(chat,'    private val personalityGrowth: PersonalityGrowth? = null,\n',
    '''    private val personalityGrowth: PersonalityGrowth? = null,
    private val goalStore: GoalStore? = null,
''')
one(chat,'    private val jobs = ConcurrentHashMap<Long, Job>()\n',
    '''    private val jobs = ConcurrentHashMap<Long, Job>()
    /** Which exact goal VERSION is executing this conversation; stale callbacks cannot commit. */
    private val goalExecuting = ConcurrentHashMap<Long, Pair<String, Int>>()
''')
one(chat,'    suspend fun newConversation(companionId: Long): Long {',
    '''    /** Public UI/command API. Commands never create fake user messages. */
    fun goalCreate(conversationId: Long, companionId: Long, description: String,
        constraints: String, criteria: List<String>, steps: List<String>, budget: GoalBudget) {
        scope.launch {
            val store=goalStore ?: return@launch
            val room=db.conversations().get(conversationId)
            if(room==null || (room.isGroup && companionId !in db.groupMembers().idsFor(conversationId)) ||
                (!room.isGroup && room.companionId!=companionId)) {
                note(conversationId,"目标未创建：角色不属于这个会话。")
                return@launch
            }
            val made=store.create(conversationId,companionId,description,constraints,criteria,steps,budget)
            if(made==null) note(conversationId,"目标未创建：请填写目标与至少一项验收标准；如已有活动目标，请先暂停、取消或清除。")
            else {
                note(conversationId,"目标模式已创建：${made.description.take(100)}。已保存检查点，开始执行。")
                store.resume(made.id)
                scheduleGoal(conversationId)
            }
        }
    }

    fun goalControl(conversationId: Long, companionId: Long, action: String,
        description: String = "", constraints: String = "",
        criteria: List<String> = emptyList(), steps: List<String> = emptyList(),
        budget: GoalBudget = GoalBudget(), criterionIndex: Int = -1) {
        scope.launch {
            val store=goalStore ?: return@launch
            val g=store.active(conversationId,companionId) ?: return@launch
            when(action) {
                "pause" -> { store.pause(g.id); jobs[conversationId]?.cancel() }
                "cancel","clear" -> { store.cancel(g.id); jobs[conversationId]?.cancel() }
                "edit" -> { store.edit(g.id,description,constraints,criteria,steps,budget);
                    jobs[conversationId]?.cancel() }
                "resume" -> { if(store.resume(g.id)?.status=="RUNNING") scheduleGoal(conversationId) }
                "approve" -> { if(store.approve(g.id)?.status=="RUNNING") scheduleGoal(conversationId) }
                "reconcile" -> store.reconcile(g.id)
                "budget" -> store.adjustBudget(g.id,budget)
                "pass" -> { store.verify(g.id,criterionIndex,true); jobs[conversationId]?.cancel() }
                "fail" -> { store.verify(g.id,criterionIndex,false); jobs[conversationId]?.cancel() }
            }
        }
    }

    fun goalCommand(conversationId: Long, companionId: Long, input: String): Boolean {
        if (!input.startsWith("/goal") || (input.length>5 && !input[5].isWhitespace())) return false
        val tail=input.removePrefix("/goal").trim()
        val current=goalStore?.active(conversationId,companionId)
        when {
            tail.isBlank() -> scope.launch {
                note(conversationId, current?.let {
                    "目标：${it.description.take(100)} · ${it.status} · ${it.rounds}/${it.budget.maxRounds}轮；验收${it.criteria.count { c -> c.status=="PASSED" }}/${it.criteria.size}项通过。"
                } ?: "目前没有活动目标。点聊天栏的「目标」可以创建。")
            }
            tail=="pause" -> goalControl(conversationId,companionId,"pause")
            tail=="resume" -> goalControl(conversationId,companionId,"resume")
            tail=="clear" -> goalControl(conversationId,companionId,"clear")
            tail.startsWith("edit ") -> {
                if(current!=null) goalControl(conversationId,companionId,"edit",tail.removePrefix("edit "),
                    current.constraints,current.criteria.map { it.text },current.steps.map { it.title },current.budget)
            }
            current!=null -> scope.launch { note(conversationId,"已有活动目标，请先在目标卡片中处理原目标，避免同时执行两个任务。") }
            else -> goalCreate(conversationId,companionId,tail,"",
                listOf("交付与目标相符的结果，并提供可检查的证据。"),emptyList(),GoalBudget())
        }
        return true
    }

    private fun scheduleGoal(conversationId: Long) {
        scope.launch {
            // Wait for any previous reply/tool to stop before the new execution lease starts.
            jobs[conversationId]?.join()
            start(conversationId) { goalLoop(conversationId) }
        }
    }

    private suspend fun goalLoop(conversationId: Long) {
        val store=goalStore ?: return
        var previous: GoalRecord?=null
        while (true) {
            currentCoroutineContext().ensureActive()
            val room=db.conversations().get(conversationId) ?: return
            val goal=store.records.value.lastOrNull { it.conversationId==conversationId &&
                it.status=="RUNNING" && (room.isGroup || it.companionId==room.companionId) } ?: return
            if(room.isGroup && goal.companionId !in db.groupMembers().idsFor(conversationId)) {
                store.pause(goal.id);return
            }
            val sig=goal.version to (goal.steps to goal.criteria)
            try {
                goalExecuting[conversationId]=goal.id to goal.version
                goalTurn(conversationId,goal)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.checkpoint(goal.id,goal.version,
                    kotlinx.serialization.json.buildJsonObject {
                        put("blocking_reason","模型或网络执行失败：${e.javaClass.simpleName}")
                    })
            } finally {
                goalExecuting.remove(conversationId,goal.id to goal.version)
            }
            val after=store.byId(goal.id) ?: return
            if(after.version!=goal.version || after.status!="RUNNING") return
            // Progress means plan/criteria changed or a real tool receipt was recorded.
            val progressed=after.steps!=goal.steps || after.criteria!=goal.criteria ||
                after.receipts.size!=goal.receipts.size
            if(!progressed) store.noProgress(goal.id,goal.version)
            if(store.byId(goal.id)?.status!="RUNNING") return
            delay(700)
        }
    }

    /** Deliberately reuses the real chat model / tool / MCP permission path. No fake user turn. */
    private suspend fun goalTurn(conversationId: Long, goal: GoalRecord) {
        val store=goalStore ?: return
        val room=db.conversations().get(conversationId) ?: return
        val ta=companions.get(goal.companionId) ?: return
        val s=settings.current()
        val key=secrets.key(ta.apiBaseUrl)
        if(key.isNullOrBlank()) {
            store.checkpoint(goal.id,goal.version,
                kotlinx.serialization.json.buildJsonObject { put("blocking_reason","当前角色没有可用模型密钥") })
            return
        }
        val endpoint=ApiEndpoint(ta.apiBaseUrl,key,ta.apiModel)
        val groups=groupsFor(s,ta)
        val outside=mcp.tools()
        val history=Recap.sent(recaps.live(room),s.historySize)
        val (stickers, sendStickers)=stickersFor(s)
        val memories=PersonalityGrowthRules.forPrompt(db.memories().allFor(ta.id),ToolGroup.Memory in s.tools)
        val withImages=MessagePictures.hasImages(history,stickers)
        val prompt=Prompt.messages(
            s,ta,history,ZonedDateTime.now(),groups,withImages,memories,room.recap,outside,
            emptyList(),null,stickers,sendStickers,
            extraContext=store.context(goal),
        )
        var messages=prepare(prompt)
        val specs=tools.specs(groups)+outside.map { it.spec }+GoalCheckpointTool.spec
        for (round in 0 until 4) {
            currentCoroutineContext().ensureActive()
            if(!store.startCall(goal.id,goal.version)) return
            when(val step=step(conversationId,endpoint,messages,specs,
                mayRefuse=false,thinking=false,showThought=ta.deepThinking,
                speakerCompanionId=ta.id)) {
                is Step.Ended -> return
                is Step.Said -> return
                Step.Refused -> {
                    store.checkpoint(goal.id,goal.version,
                        kotlinx.serialization.json.buildJsonObject {
                            put("blocking_reason","当前模型不支持目标执行所需的工具调用。")
                        })
                    return
                }
                is Step.Called -> {
                    if(store.byId(goal.id)?.status!="RUNNING") return
                    val results=runTools(conversationId,step,s,ta.id,outside,ta.name)
                    if(store.byId(goal.id)?.status!="RUNNING") return
                    if(step.message.toolCalls.all { it.name in ToolSpecs.speaking }) return
                    messages=messages+step.message+results
                }
            }
        }
    }

    suspend fun newConversation(companionId: Long): Long {''')

# Prevent chat reply generation from racing the goal job, while direct user messages safely pause it.
one(chat,
    '''            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "user",
                    content = content,''',
    '''            goalStore?.active(conversationId, convoForMentions?.companionId ?: -1L)?.let { g ->
                if(g.status=="RUNNING") {
                    goalStore.pause(g.id)
                    jobs[conversationId]?.cancel()
                }
                goalStore.userNote(g.id,content)
            }
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "user",
                    content = content,''')

# Intercept goal checkpoint with typed validation, and preflight external tool effects.
one(chat,
    '''            var outcome = try {
                if (call.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name))''',
    '''            val goalRun=goalExecuting[conversationId]
            val goalToken=goalRun?.let { goalStore?.byId(it.first)?.takeIf { row ->
                row.version==it.second && row.status=="RUNNING" } }
            val canExecute=if(goalToken!=null && call.name!=GoalCheckpointTool.spec.name) {
                goalStore?.beginTool(goalToken.id,goalToken.version,call.id,call.name,
                    call.arguments,outer!=null)==true
            } else true
            var success=true
            var outcome = try {
                if(goalToken!=null && call.name==GoalCheckpointTool.spec.name) {
                    val args=ToolArgs.parse(call.arguments)
                    ToolOutcome(if(args==null) "检查点 JSON 无效，尚未更新目标。" else
                        goalStore?.checkpoint(goalToken.id,goalToken.version,args).orEmpty(),
                        "更新目标检查点")
                } else if(!canExecute) {
                    success=false
                    ToolOutcome("该目标操作尚未授权，未执行。请用户核对目标卡片中的待授权工具。", "等待目标授权")
                } else if (call.name in setOf(ToolSpecs.searchChatHistory.name, ToolSpecs.readChatMessage.name))''')
one(chat,
    '''            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToolOutcome("工具出错了：${e.message ?: e.javaClass.simpleName}", "${outer?.spec?.action ?: tools.action(call.name)}出错了")
            }
            // A web link is not a sent image.''',
    '''            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                success=false
                ToolOutcome("工具出错了：${e.message ?: e.javaClass.simpleName}", "${outer?.spec?.action ?: tools.action(call.name)}出错了")
            }
            // A web link is not a sent image.''')
one(chat,
    '''            // The tool has acted (a todo exists now), so its result is stored even if
            // stop was pressed meanwhile:''',
    '''            if(goalToken!=null && call.name!=GoalCheckpointTool.spec.name && canExecute) {
                // Read-only responses prove a tool was invoked; side effects stay unverified
                // until independently verified by user or a typed verification tool.
                goalStore?.toolResult(goalToken.id,goalToken.version,call.id,call.name,
                    success,verified=success && !GoalRules.sideEffect(call.name,outer!=null),
                    summary=if(success) "实际工具调用有返回，详见会话工具记录" else "工具执行失败")
            }
            // The tool has acted (a todo exists now), so its result is stored even if
            // stop was pressed meanwhile:''')

# Speaking tools do not enter 'others', so guard before any send in a goal loop.
one(chat,
    '''        for (call in sends) {
            val args = ToolArgs.parse(call.arguments)''',
    '''        for (call in sends) {
            val goalRun=goalExecuting[conversationId]
            val goal=goalRun?.let { goalStore?.byId(it.first)?.takeIf { row ->
                row.version==it.second && row.status=="RUNNING" } }
            if (goal!=null) {
                val allowed=goalStore?.beginTool(goal.id,goal.version,call.id,call.name,call.arguments,false)==true
                if(!allowed) {
                    results[call.id]=ApiMessage("tool","发送动作未执行，正在等待用户确认。",toolCallId=call.id)
                    continue
                }
            } else if(goalRun!=null) {
                results[call.id]=ApiMessage("tool","目标已停止或变更；动作未执行。",toolCallId=call.id)
                continue
            }
            val args = ToolArgs.parse(call.arguments)''')
one(chat,
    '''            results[call.id] = ApiMessage("tool", result, toolCallId = call.id)
        }
        if (sends.isNotEmpty()''',
    '''            if(goal!=null) goalStore?.toolResult(goal.id,goal.version,call.id,call.name,
                success=result==ToolSpecs.SENT,verified=false,
                summary="发送操作有结果；请在聊天记录确认")
            results[call.id] = ApiMessage("tool", result, toolCallId = call.id)
        }
        if (sends.isNotEmpty()''')

# For paused/edited/cancelled goals, do not let later tool calls in the SAME batch execute.
one(chat,
    '''            val canExecute=if(goalToken!=null && call.name!=GoalCheckpointTool.spec.name) {
                goalStore?.beginTool(goalToken.id,goalToken.version,call.id,call.name,
                    call.arguments,outer!=null)==true
            } else true''',
    '''            val canExecute=if(goalRun!=null && goalToken==null) false
            else if(goalToken!=null && call.name!=GoalCheckpointTool.spec.name) {
                goalStore?.beginTool(goalToken.id,goalToken.version,call.id,call.name,
                    call.arguments,outer!=null)==true
            } else true''')
one(chat,
    '''                if(goalToken!=null && call.name==GoalCheckpointTool.spec.name) {''',
    '''                if(!canExecute) {
                    success=false
                    ToolOutcome("目标已经暂停、取消、变更或等待授权；工具未执行。", "目标未执行")
                } else if(goalToken!=null && call.name==GoalCheckpointTool.spec.name) {''')

# turn goal stats into true checks: no extra provider usage unless supplied by API
gradle=root/"app/build.gradle.kts"
one(gradle,'versionName = "0.38.44"','versionName = "0.38.45"')
one(gradle,"versionCode = 62110","versionCode = 62111")
print("0.38.45: programmatic goal scheduling shares existing model, permission gates and real MCP tools")
