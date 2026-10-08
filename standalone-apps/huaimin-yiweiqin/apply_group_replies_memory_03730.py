#!/usr/bin/env python3
"""0.37.30: reply reliably to multi-message @ turns; retrieve permitted direct-chat history."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
base = Path("app/src/main/java/com/cleo/cleos/")
chat = root / base / "ai/ChatRepository.kt"
dao = root / base / "data/db/Daos.kt"

def once(path: Path, old: str, new: str):
    content = path.read_text(encoding="utf-8")
    count = content.count(old)
    if count != 1:
        raise SystemExit(f"0.37.30 patch: {path.name} expected 1 match, got {count}: {old[:140]!r}")
    path.write_text(content.replace(old, new, 1), encoding="utf-8")

once(dao,
'''    suspend fun sharedForCompanion(excludeConversationId: Long, companionId: Long, limit: Int): List<SharedMessageRow>

    /** Bounded keyword retrieval across authorized conversations. No full-history prompt dump. */''',
'''    suspend fun sharedForCompanion(excludeConversationId: Long, companionId: Long, limit: Int): List<SharedMessageRow>

    /** Private 1:1 records for a specifically named TA. Respect per-conversation sharing. */
    @Query("SELECT m.id AS id, m.conversationId AS conversationId, c.title AS conversationTitle, c.companionId AS ownerCompanionId, m.senderCompanionId AS senderCompanionId, m.role AS role, m.content AS content, m.createdAt AS createdAt FROM messages m JOIN conversations c ON c.id=m.conversationId WHERE m.conversationId != :excludeConversationId AND c.isGroup=0 AND c.companionId=:companionId AND c.historyShareAllowed=1 AND m.role IN ('user','assistant') AND m.note IS NULL AND m.error IS NULL AND m.content != '' ORDER BY m.createdAt DESC, m.id DESC LIMIT :limit")
    suspend fun sharedDirectForCompanion(excludeConversationId: Long, companionId: Long, limit: Int): List<SharedMessageRow>

    /** Bounded keyword retrieval across authorized conversations. No full-history prompt dump. */''')

once(chat,
'''        if (owner?.isGroup == true && !owner.groupShareOutside) return null
        val all = db.companions().all()
        val named = all.filter { ta -> ta.name.trim().takeIf { it.isNotEmpty() }?.let { latestText.contains(it) } == true }.take(3)''',
'''        if (owner?.isGroup == true && !owner.groupShareOutside) {
            return if (GroupMemoryBridge.isHistoryQuestion(latestText))
                "这个群的「共享其他会话记录」已关闭，无法读取别的私聊。若用户希望互通，可在群聊设置里自行开启；不要谎称看过未授权内容。"
            else null
        }
        val all = db.companions().all()
        val named = all.filter { ta -> ta.name.trim().takeIf { it.isNotEmpty() }?.let { latestText.contains(it) } == true }.take(3)''')

once(chat,
'''        return GroupChats.sharedContext(rows, all, s.userName)
    }

    /** Allow one deliberate continuation''',
'''        // A directly named private conversation takes priority over general recently shared
        // messages. Group members may read real excerpts from other chats when the original
        // conversation and this group both permit sharing. Do not infer hidden records.
        val directSections = if (GroupMemoryBridge.isHistoryQuestion(latestText) && named.isNotEmpty()) {
            GroupMemoryBridge.orderedTargets(latestText, named).mapNotNull { ta ->
                GroupMemoryBridge.directExcerpt(
                    ta, db.messages().sharedDirectForCompanion(conversationId, ta.id, 20), all, s.userName,
                )
            }
        } else emptyList()
        val ordinary = GroupChats.sharedContext(rows, all, s.userName)
        val missing = if (GroupMemoryBridge.isHistoryQuestion(latestText) && named.isNotEmpty() && directSections.isEmpty())
            "当前没有检索到这些角色允许共享的私聊记录（可能未有私聊、原会话禁止共享或内容不在记录中），不要编造，也不要说永远不能跨会话读取。"
            else null
        return (directSections + listOfNotNull(missing, ordinary)).joinToString("\\n\\n").ifBlank { null }
    }

    /** Allow one deliberate continuation''')

once(chat,
'''        val latestText = if (continued) latestAssistant?.content.orEmpty()
            else trigger?.takeIf { it.role == "user" }?.content.orEmpty()
        val mentions = if (continued) emptyList() else GroupChats.targeted(latestText, trigger?.mentionedCompanionIds, members)''',
'''        val userBurst = if (continued) emptyList() else GroupTurnRecovery.activeUserMessages(firstHistory)
        val latestText = if (continued) latestAssistant?.content.orEmpty()
            else GroupTurnRecovery.text(userBurst).ifBlank { trigger?.takeIf { it.role == "user" }?.content.orEmpty() }
        val mentions = if (continued) emptyList() else GroupChats.targeted(
            latestText, GroupTurnRecovery.mentionIds(userBurst) ?: trigger?.mentionedCompanionIds, members,
        )
        val isQuestion = !continued && GroupTurnRecovery.isQuestion(latestText)''')

once(chat,
'''        var said = 0
        for (ta in candidates.take(GroupChats.MAX_MEMBERS)) {
            currentCoroutineContext().ensureActive()
            if (said >= conversation.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)) break
            val latestConversation = db.conversations().get(conversationId) ?: return''',
'''        if (candidates.isEmpty() && !continued && conversation.groupMode == 2 && mentions.isEmpty()) {
            note(conversationId, "这个群开启了「仅被 @ 时回复」，请 @ 一位群成员，或在群设置切换为自然聊天。")
            return
        }
        if (candidates.isEmpty() && mentions.isNotEmpty()) {
            note(conversationId, "被 @ 的角色已被设为静音，或者不在这个群中，请检查群成员设置。")
            return
        }
        var said = 0
        for (ta in candidates.take(GroupChats.MAX_MEMBERS)) {
            currentCoroutineContext().ensureActive()
            if (said >= conversation.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)) break
            val latestConversation = db.conversations().get(conversationId) ?: return
            if (latestConversation.groupUsedDay == java.time.LocalDate.now().toEpochDay() &&
                latestConversation.groupCallsToday >= latestConversation.groupDailyLimit) {
                note(conversationId, "群聊今天已达到模型调用上限（${latestConversation.groupDailyLimit} 次），可以在群设置调整限额或明天再试。")
                break
            }''')

once(chat,
'''if (groupTurn(conversationId, ta, members, shaped, context, targeted = patTarget != null || mentions.isNotEmpty() || conversation.groupMode == 1 || groupLocation != null)) said++''',
'''if (groupTurn(conversationId, ta, members, shaped, context,
                    targeted = patTarget != null || mentions.isNotEmpty() || conversation.groupMode == 1 || groupLocation != null || (isQuestion && said == 0))) said++''')

once(chat,
'''            } finally {
                hide(conversationId)
            }
        }
    }

    private suspend fun groupTurn(''',
'''            } finally {
                hide(conversationId)
            }
        }
        if (said == 0 && candidates.isNotEmpty() &&
            (mentions.isNotEmpty() || isQuestion || groupLocation != null)) {
            note(conversationId, "本轮群聊没有生成有效回复。可点名重试；若频繁出现，请确认角色模型与 API Key 可用，以及群聊调用额度未用完。")
        }
    }

    private suspend fun groupTurn(''')

once(chat,
'''        var thinking = ta.deepThinking && endpointKey !in refusesThinking
        val groupRule = GroupChats.turnInstruction(ta, members, targeted)''',
'''        var thinking = ta.deepThinking && endpointKey !in refusesThinking
        var retryTargeted = false
        val groupRule = GroupChats.turnInstruction(ta, members, targeted)''')

once(chat,
'''            recap = null, stickers = stickers, sendStickers = false, extraContext = extra,
        )
        var messages = prepare(build())
        while (true) {
            // Count actual outgoing attempts''',
'''            recap = null, stickers = stickers, sendStickers = false,
            extraContext = if (retryTargeted) extra +
                "\\n\\n刚刚是用户在群里的明确提问或 @，上次没有发出有效文本。请像真人直接回应对方的问题；不能确定就说明不知道，不要再写 SKIP。" else extra,
        )
        var messages = prepare(build())
        while (true) {
            // Count actual outgoing attempts''')

once(chat,
'''                    if (body.isEmpty() || GroupChats.isSkip(body)) return false
                    val groupEmoji = GroupReactionRules.parse(body)''',
'''                    if (body.isEmpty() || GroupChats.isSkip(body)) {
                        if (targeted && !retryTargeted) {
                            retryTargeted = true
                            messages = prepare(build())
                            continue
                        }
                        return false
                    }
                    val groupEmoji = GroupReactionRules.parse(body)''')

once(chat,
'''                is Step.Called -> return false
                is Step.Ended -> return false
            }
        }
    }

    private suspend fun reply(conversationId: Long) {''',
'''                is Step.Called -> {
                    // Some models invoke send_message even without a tool spec (habit from
                    // 1:1 chat). Never silently drop their actual words. Discard the pending
                    // tool-call row: no tool was executed, so it has no matching tool result.
                    step.savedId?.let { db.messages().delete(it) }
                    val spoken = buildList {
                        step.message.content.trim().takeIf { it.isNotEmpty() }?.let(::add)
                        for (call in step.message.toolCalls) {
                            if (call.name == ToolSpecs.sendMessage.name || call.name == ToolSpecs.sendVoice.name) {
                                val value = ToolArgs.parse(call.arguments)?.let { ToolArgs.text(it, "text") }?.trim().orEmpty()
                                if (value.isNotEmpty()) add(value)
                            }
                        }
                    }.take(GroupChats.MAX_BUBBLES)
                    if (spoken.isEmpty()) return false
                    db.conversations().recordGroupText(conversationId, 0, spoken.sumOf { it.length }.toLong())
                    storeAssistantBubbles(conversationId, spoken, step.thought?.let(MessageThoughts::encode),
                        quiet = true, speakerCompanionId = ta.id)
                    return true
                }
                is Step.Ended -> return false
            }
        }
    }

    private suspend fun reply(conversationId: Long) {''')

for source, target in (
    ("GroupTurnRecovery.kt", base / "ai/GroupTurnRecovery.kt"),
    ("GroupMemoryBridge.kt", base / "ai/GroupMemoryBridge.kt"),
    ("GroupReplyMemoryTest.kt", Path("app/src/test/java/com/cleo/cleos/ai/GroupReplyMemoryTest.kt")),
):
    dest = root / target
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(here / source, dest)

gradle = root / "app/build.gradle.kts"
source = gradle.read_text(encoding="utf-8")
for old, new in (('versionName = "0.37.29"', 'versionName = "0.37.30"'), ('versionCode = 62051', 'versionCode = 62052')):
    if source.count(old) != 1:
        raise SystemExit(f"0.37.30 unexpected version anchor: {old}")
    source = source.replace(old, new, 1)
gradle.write_text(source, encoding="utf-8")
print("0.37.30 / 62052: real cross-chat memory and reliable @ group reply; Room schema unchanged")
