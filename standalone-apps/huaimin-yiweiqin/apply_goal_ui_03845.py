#!/usr/bin/env python3
"""Goal Mode UI: entry on chat header, live scoped card, form, controls and /goal commands."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve();here=Path(__file__).resolve().parent
def one(path,old,new):
    content=path.read_text(encoding="utf-8")
    n=content.count(old)
    if n!=1: raise RuntimeError(f"goal ui baseline mismatch {path.name}: {old[:110]!r}; n={n}")
    path.write_text(content.replace(old,new,1),encoding="utf-8")

ai=root/"app/src/main/java/com/cleo/cleos/ai"
ui=root/"app/src/main/java/com/cleo/cleos/ui/chat"
copyfile(here/"GoalModeUi_03845.kt",ui/"GoalModeUi.kt")
chat=ai/"ChatRepository.kt"
one(chat,
    '''        constraints: String, criteria: List<String>, steps: List<String>, budget: GoalBudget) {
        scope.launch {''',
    '''        constraints: String, criteria: List<String>, steps: List<String>, budget: GoalBudget,
        replaceActive: Boolean = false) {
        scope.launch {''')
one(chat,
    'val made=store.create(conversationId,companionId,description,constraints,criteria,steps,budget)',
    'val made=store.create(conversationId,companionId,description,constraints,criteria,steps,budget,replaceActive)')
one(chat,
    '''                store.resume(made.id)
                scheduleGoal(conversationId)''',
    '''                if(replaceActive) jobs[conversationId]?.cancel()
                store.resume(made.id)
                scheduleGoal(conversationId)''')
one(chat,
    'import kotlinx.serialization.json.JsonObject\n',
    'import kotlinx.serialization.json.JsonObject\nimport kotlinx.serialization.json.put\n')
one(chat,
    '    private suspend fun goalLoop(conversationId: Long) {\n        val store=goalStore ?: return\n        var previous: GoalRecord?=null\n',
    '    private suspend fun goalLoop(conversationId: Long) {\n        val store=goalStore ?: return\n')
one(chat,
    '            val sig=goal.version to (goal.steps to goal.criteria)\n',
    '')

vm=ui/"ChatViewModel.kt"
one(vm,
    '''        val id = conversationId.value ?: return false
        if (!c.chat.send(id, text, attachments.toList(), quoting, mentions)) return false''',
    '''        val id = conversationId.value ?: return false
        if(attachments.isEmpty() && c.chat.goalCommand(id,state.value.companionId,text.trim())) return true
        if (!c.chat.send(id, text, attachments.toList(), quoting, mentions)) return false''')

screen=ui/"ChatScreen.kt"
one(screen,
    '''    val c = appContainer()
    // Whether this is the tab on screen:''',
    '''    val c = appContainer()
    val allGoals by remember { c.goalStore.records }.collectAsStateWithLifecycle()
    val currentGoal = allGoals.lastOrNull { it.conversationId==state.conversationId &&
        it.companionId==state.companionId && it.status !in com.cleo.cleos.ai.GoalRules.terminal }
    var goalPanelOpen by remember { mutableStateOf(false) }
    val goalHistory = allGoals.filter { it.conversationId==state.conversationId &&
        it.companionId==state.companionId }
    // Whether this is the tab on screen:''')
one(screen,
    '''                trailing = {
                },
                onTitleClick =''',
    '''                trailing = {
                    TextButton(onClick={goalPanelOpen=true}) {
                        Text("◎ 目标",fontSize=13.sp)
                    }
                },
                onTitleClick =''')
one(screen,
    '''            AnimatedVisibility(
                visible = barShown && !selectionMode,''',
    '''            if(currentGoal!=null && !selectionMode) {
                GoalStatusPill(
                    currentGoal, onOpen={goalPanelOpen=true},
                    modifier=Modifier.align(Alignment.TopCenter)
                        .padding(top=statusTop + TopBarHeight + 8.dp,start=20.dp,end=20.dp),
                )
            }
            AnimatedVisibility(
                visible = barShown && !selectionMode && currentGoal==null,''')
# Place the editor in ChatTab, outside overlay side-effects and after state is available.
one(screen,
    '''    MusicSongRequestDialogs(
        conversationId = state.conversationId,''',
    '''    if(goalPanelOpen) {
        GoalModeDialog(
            current=currentGoal,
            history=goalHistory,
            onDismiss={goalPanelOpen=false},
            onCreate={ description,constraints,criteria,steps,budget,replace ->
                state.conversationId?.let { id ->
                    c.chat.goalCreate(id,state.companionId,description,constraints,criteria,steps,budget,replace)
                }
                goalPanelOpen=false
            },
            onControl={ action,description,constraints,criteria,steps,budget,criterionIndex ->
                state.conversationId?.let { id ->
                    c.chat.goalControl(id,state.companionId,action,description,constraints,
                        criteria,steps,budget,criterionIndex)
                }
                if(action in setOf("edit","cancel","clear")) goalPanelOpen=false
            },
        )
    }
    MusicSongRequestDialogs(
        conversationId = state.conversationId,''')
print("0.38.45: ChatTab displays interactive goal card and editor, /goal commands intercepted before normal send")
