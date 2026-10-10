#!/usr/bin/env python3
"""v0.38.6: prompt AI immediately after a committed wallet transfer or red packet."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
base=Path("app/src/main/java/com/cleo/cleos")
def once(relative,old,new,label):
    p=root/relative
    source=p.read_text(encoding="utf-8")
    found=source.count(old)
    if found != 1: raise RuntimeError(f"{label}: {found} anchors in {relative}")
    p.write_text(source.replace(old,new,1),encoding="utf-8")

dest=root/base/"ai/WalletReactiveEvents.kt"
dest.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"WalletReactiveEvents_0386.kt",dest)
test=root/"app/src/test/java/com/cleo/cleos/ai/WalletReactiveEventsTest.kt"
test.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"WalletReactiveEventsTest_0386.kt",test)

chat=base/"ai/ChatRepository.kt"
ui=base/"ui/chat/ChatScreen.kt"

once(chat,
'''    /** A sticker keeps its token for display; Prompt resolves its artwork for the model. */''',
'''    /**
     * A successful virtual-money action is a real user interaction, just like sending a
     * chat message. It starts the existing serialized conversation reply pipeline.
     * Enqueue only AFTER AtomicFile wallet commit; never charge AI tokens on failed sends.
     * The synthetic row is durable for model context but hidden from UI (the orange ledger
     * card is its single visible representation).
     */
    suspend fun walletTransaction(
        conversationId: Long, packet: Boolean, receiptId: String, recipients: List<Long>,
    ) {
        val room = db.conversations().get(conversationId)
            ?: throw IllegalStateException("会话不存在")
        val authorized = if (room.isGroup) db.groupMembers().idsFor(conversationId).toSet()
            else setOf(room.companionId)
        val target = recipients.toSet()
        require(target.isNotEmpty() && target.size == recipients.size && target.all { it > 0L && it in authorized }) {
            "当前会话收款人不匹配"
        }
        if (!packet) require(target.size == 1) { "单次转账只能有一个收款对象" }
        val wallet = WalletChatBridge.store?.state?.value ?: error("钱包未初始化")
        if (packet) {
            val saved = wallet.packets.singleOrNull { it.id == receiptId && it.conversationId == conversationId &&
                it.sender == 0L } ?: error("没有找到已经发送的真实红包")
            require(saved.recipients.toSet() == target) { "红包目标已变化" }
        } else {
            val saved = wallet.movements.singleOrNull { it.id == receiptId &&
                it.conversationId == conversationId && it.kind == "transfer" && it.from == 0L }
                ?: error("没有找到真实转账流水")
            require(saved.to in target) { "转账收款对象不匹配" }
        }
        val at = stamp()
        db.messages().insert(MessageEntity(
            conversationId = conversationId,
            role = "user",
            content = WalletReactiveEvents.messageText(packet, receiptId),
            createdAt = at,
            mentionedCompanionIds = target.sorted().joinToString(","),
        ))
        db.conversations().touch(conversationId, at)
        answerSoon(conversationId)
        // Wallet actions are not held behind the ordinary typing debounce. A queued network
        // request still follows existing per-conversation exclusivity and group quota.
        lastSent[conversationId] = at - REPLY_WAIT
    }

    /** A sticker keeps its token for display; Prompt resolves its artwork for the model. */''',
"notify AI after real wallet operation")

once(chat,
'''            val held = (conversationId in typingIn || (holds[conversationId] ?: 0) > 0) &&
                db.messages().newest(conversationId, 1).none { Recalls.isEvent(it) || ReactionEvents.isEvent(it) }''',
'''            val held = (conversationId in typingIn || (holds[conversationId] ?: 0) > 0) &&
                db.messages().newest(conversationId, 1).none {
                    Recalls.isEvent(it) || ReactionEvents.isEvent(it) || WalletReactiveEvents.isEvent(it)
                }''',
"skip keyboard waiting for actual wallet events")

once(chat,
'''        val userBurst = if (continued || trigger?.role != "user") emptyList()
            else GroupTurnRecovery.activeUserMessages(firstHistory)''',
'''        val moneyEvent = if (continued) null else trigger?.takeIf(WalletReactiveEvents::isEvent)
        // A wallet event is an independent trigger: never inherit an older @ mention burst.
        val userBurst = if (moneyEvent != null) listOf(moneyEvent)
            else if (continued || trigger?.role != "user") emptyList()
            else GroupTurnRecovery.activeUserMessages(firstHistory)''',
"isolate group transaction from preceding chat mentions")

once(chat,
'''        val mentions = if (continued) emptyList() else GroupChats.targeted(
            latestText, GroupTurnRecovery.mentionIds(userBurst) ?: trigger?.mentionedCompanionIds, members,
        )''',
'''        val mentions = if (continued) emptyList() else if (moneyEvent != null) {
            val allowed = WalletReactiveEvents.targetIds(moneyEvent.mentionedCompanionIds,
                members.map { it.id }.toSet())
            members.filter { it.id in allowed }
        } else GroupChats.targeted(
            latestText, GroupTurnRecovery.mentionIds(userBurst) ?: trigger?.mentionedCompanionIds, members,
        )''',
"target only actual wallet recipients in group")

once(chat,
'''            if (said >= if (background) 1 else conversation.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)) break''',
'''            val replyLimit = if (background) 1 else if (moneyEvent != null) {
                // A group red packet addressed to three AIs invites three genuine immediate
                // reactions, subject to existing daily AI-call budget and muted members.
                mentions.size.coerceIn(1, GroupChats.MAX_MEMBERS)
            } else conversation.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)
            if (said >= replyLimit) break''',
"respond from each eligible recipient in group, bounded by quota")

once(chat,
'''                it.role == "user" && it.note == null && it.error == null
            } == true''',
'''                it.role == "user" && it.note == null && it.error == null &&
                    !WalletReactiveEvents.isEvent(it)
            } == true''',
"avoid extra autonomous group chain after wallet event")

once(ui,
'''import com.cleo.cleos.ai.WalletChatBridge''',
'''import com.cleo.cleos.ai.WalletChatBridge
import com.cleo.cleos.ai.WalletReactiveEvents''',
"UI helper import")

once(ui,
'''        buildRows(state.messages, state.recapUntil, eachFace = state.avatarEachMessage)''',
'''        buildRows(state.messages.filterNot(WalletReactiveEvents::isEvent),
            state.recapUntil, eachFace = state.avatarEachMessage)''',
"hide internal event row, keep original orange transaction card")

once(ui,
'''                            if (kind == ChatMoneyKind.PACKET) {
                                c.wallet.sendPacket(0L, ids, cents, random, conversation)
                            } else {
                                require(ids.size == 1) { "一次只能转账给一位 AI" }
                                c.wallet.transfer(0L, ids.single(), cents, conversation)
                            }
                            walletAction = null
                            sentCount++''',
'''                            val receipt = if (kind == ChatMoneyKind.PACKET) {
                                c.wallet.sendPacket(0L, ids, cents, random, conversation)
                            } else {
                                require(ids.size == 1) { "一次只能转账给一位 AI" }
                                c.wallet.transfer(0L, ids.single(), cents, conversation)
                            }
                            // Payment already committed. Do not throw a misleading failure message
                            // if only the follow-up AI request fails.
                            walletAction = null
                            sentCount++
                            try {
                                c.chat.walletTransaction(conversation, kind == ChatMoneyKind.PACKET,
                                    receipt, ids)
                            } catch (notificationError: Exception) {
                                voiceHint = "虚拟交易已经成功，但 AI 即时通知失败：" +
                                    (notificationError.message ?: "请稍后发消息提醒")
                            }''',
"wake actual AI after successful virtual transaction")

once("app/build.gradle.kts",'versionName = "0.38.5"','versionName = "0.38.6"',"version")
once("app/build.gradle.kts",'versionCode = 62071','versionCode = 62072',"code")
print("v0.38.6/62072: successful wallet sends trigger immediate real AI reply in 1:1 and recipient-targeted groups")
