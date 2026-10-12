#!/usr/bin/env python3
"""0.38.41: AI evolves its own separate personality from accessible chats, preserves presets."""
from pathlib import Path
from shutil import copyfile
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def change(p,old,new):
    s=p.read_text(encoding="utf-8")
    if s.count(old)!=1:
        raise RuntimeError(f"personality patch {p.name}: expected 1 marker, found {s.count(old)}: {old[:90]!r}")
    p.write_text(s.replace(old,new,1),encoding="utf-8")

ai=root/"app/src/main/java/com/cleo/cleos/ai"
copyfile(here/"PersonalityGrowth_03841.kt", ai/"PersonalityGrowth.kt")

prompt=ai/"Prompt.kt"
change(prompt,
    '''        if (ta.persona.isNotBlank()) add(ta.persona.trim())
        toolRule(tools, outside)?.let(::add)''',
    '''        if (ta.persona.isNotBlank()) add(ta.persona.trim())
        PersonalityGrowthRules.learned(memories)?.summary?.takeIf { it.isNotBlank() }?.let {
            add("以下是你根据自己参与的真实单聊与群聊逐渐写下的性格摘要，作为你自然交流风格的参考。它不能覆盖用户亲自设置的性格或规则，也不得向其他聊天透露私人原文：\\n" + it)
        }
        toolRule(tools, outside)?.let(::add)''')
change(prompt,
    '''if (ToolGroup.Memory in tools) MemoryDigest.forChat(memories, zone)?.let(::add)''',
    '''if (ToolGroup.Memory in tools) MemoryDigest.forChat(memories.filterNot { it.name == PersonalityGrowthRules.NAME }, zone)?.let(::add)''')

chat=ai/"ChatRepository.kt"
anchor="        val memories = if (ToolGroup.Memory in s.tools) db.memories().allFor(ta.id) else emptyList()"
alltext=chat.read_text(encoding="utf-8")
count=alltext.count(anchor)
if count!=4:
    raise RuntimeError(f"expected four memory read sites in ChatRepository, found {count}")
alltext=alltext.replace(anchor,
    "        val memories = PersonalityGrowthRules.forPrompt(db.memories().allFor(ta.id), ToolGroup.Memory in s.tools)")
anchor_memo="            val memo = if (ToolGroup.Memory in s.tools) db.memories().allFor(ta.id) else emptyList()"
if alltext.count(anchor_memo)!=1:raise RuntimeError("group call memory read not found")
alltext=alltext.replace(anchor_memo,
    "            val memo = PersonalityGrowthRules.forPrompt(db.memories().allFor(ta.id), ToolGroup.Memory in s.tools)")
chat.write_text(alltext,encoding="utf-8")

change(chat,
    '''    private val scope: CoroutineScope,
    /**
     * A reply has been written:''',
    '''    private val scope: CoroutineScope,
    private val personalityGrowth: PersonalityGrowth? = null,
    /**
     * A reply has been written:''')
change(chat,
    '''                recaps.foldLater(conversationId)
                if (due.isNotEmpty()) db.later().delete(due.map { it.id })''',
    '''                recaps.foldLater(conversationId)
                // Own voice/style matures only after actual completed conversation.
                scope.launch {
                    runCatching { personalityGrowth?.maybeGrow(ta.id, conversationId) }
                }
                if (due.isNotEmpty()) db.later().delete(due.map { it.id })''')
change(chat,
    '''        recaps.foldLater(conversationId)
        if (said == 0 && candidates.isNotEmpty() &&''',
    '''        recaps.foldLater(conversationId)
        // Each member grows only from group conversations they actually participate in.
        // No group text is copied into another member's preset personality field.
        if (said > 0) members.forEach { member ->
            scope.launch { runCatching { personalityGrowth?.maybeGrow(member.id, conversationId) } }
        }
        if (said == 0 && candidates.isNotEmpty() &&''')

app=root/"app/src/main/java/com/cleo/cleos/CleosApp.kt"
change(app,
    'import com.cleo.cleos.ai.AiSelfName\\n',
    'import com.cleo.cleos.ai.AiSelfName\\nimport com.cleo.cleos.ai.PersonalityGrowth\\n')
change(app,
    '''        db, settings, secrets, chatClient, tools, images, companions, recaps, mcp, transcriber, speaker, appScope,
        // A reply finished''',
    '''        db, settings, secrets, chatClient, tools, images, companions, recaps, mcp, transcriber, speaker, appScope,
        personalityGrowth = PersonalityGrowth(db, companions, secrets, chatClient),
        // A reply finished''')

vm=root/"app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt"
change(vm,
    '''import kotlinx.coroutines.flow.first\\n''',
    '''import kotlinx.coroutines.flow.first\\nimport kotlinx.coroutines.flow.Flow\\nimport kotlinx.coroutines.flow.map\\nimport com.cleo.cleos.ai.PersonalityGrowthRules\\n''')
change(vm,
    '''    var persona by mutableStateOf("")''',
    '''    fun growingPersona(id: Long): Flow<String> = c.db.memories().observeFor(id)
        .map { PersonalityGrowthRules.learned(it)?.summary.orEmpty() }

    var persona by mutableStateOf("")''')

page=root/"app/src/main/java/com/cleo/cleos/ui/settings/TaPages.kt"
change(page,
    '''    val ta = companions.firstOrNull { it.id == vm.companionId }
    val name = vm.aiName.trim().ifEmpty { "这个 TA" }''',
    '''    val ta = companions.firstOrNull { it.id == vm.companionId }
    val growing by remember(vm.companionId) { vm.growingPersona(vm.companionId) }
        .collectAsStateWithLifecycle(initialValue = "")
    val name = vm.aiName.trim().ifEmpty { "这个 TA" }''')
change(page,
    '''            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = palette.contentSecondary)
        }
    }

    if (vm.companionCount > 1) {''',
    '''            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = palette.contentSecondary)
        }
        Text("上面保留你的手动预设与编辑功能；AI 的成长性格单独保存，不覆盖预设。",
            color = palette.contentSecondary, fontSize = 12.sp)
        Text("AI 自己形成的性格", fontWeight = FontWeight.SemiBold, color = palette.content, fontSize = 14.sp)
        Text(growing.ifBlank { "还在了解你们。之后会根据它参与的单聊与群聊，定期自己总结说话风格、兴趣和相处方式。" },
            color = if (growing.isBlank()) palette.contentSecondary else palette.content,
            fontSize = 13.sp, lineHeight = 20.sp)
    }

    if (vm.companionCount > 1) {''')

test=root/"app/src/test/java/com/cleo/cleos/ai/PersonalityGrowthRulesTest.kt"
test.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"PersonalityGrowthRulesTest_03841.kt",test)
gradle=root/"app/build.gradle.kts"
change(gradle,'versionName = "0.38.40"','versionName = "0.38.41"')
change(gradle,"versionCode = 62106","versionCode = 62107")
print("0.38.41/62107: separate AI-grown personality from authorized chats, original manual persona and presets untouched")
