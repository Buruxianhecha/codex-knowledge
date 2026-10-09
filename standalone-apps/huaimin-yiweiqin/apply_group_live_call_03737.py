#!/usr/bin/env python3
"""v0.37.37 multi-companion voice conversation: one microphone, distinct live AI voices.

Applies AFTER the 0.37.36 patches to the generated Android app.
Keeps the existing Calls mic/ASR/AudioTrack/foreground-service/call log and makes
group-specific turns additive. No permanent recordings or new Room tables.
"""
from pathlib import Path
import sys
from shutil import copyfile
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
def patch(rel,old,new,what):
    p=root/rel
    s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n != 1: raise RuntimeError(f"{what}: wanted one exact anchor; found {n}")
    p.write_text(s.replace(old,new,1),encoding="utf-8")

chat="app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
call="app/src/main/java/com/cleo/cleos/ai/Call.kt"
screen="app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"

patch(chat,
'''    suspend fun callLine(conversationId: Long, callId: Long, role: String, text: String) {
        withContext(NonCancellable) {
            val at = stamp()
            db.messages().insert(MessageEntity(conversationId = conversationId, role = role, content = text, createdAt = at, call = callId))
''',
'''    suspend fun callLine(conversationId: Long, callId: Long, role: String, text: String,
                         speakerCompanionId: Long? = null) {
        withContext(NonCancellable) {
            val at = stamp()
            db.messages().insert(MessageEntity(conversationId = conversationId, role = role,
                content = text, createdAt = at, call = callId, senderCompanionId = speakerCompanionId))
''',"store speaker on real call transcript")

patch(chat,
'''    suspend fun callReply(conversationId: Long, callId: Long, instruction: String? = null, say: (String) -> Unit): String {
''',
'''    /** A genuine multi-person telephone turn: each eligible TA reads the current group
     * transcript, chooses to speak or stay quiet, and responds with its own CALL model.
     * onLine returns only complete, non-SKIP text for ordered TTS playback. Phone-side
     * actions/MCP are never offered implicitly during an ambient group conversation.
     * Each network request is counted against the group's configured daily budget.
     */
    suspend fun groupCallReply(
        conversationId: Long, callId: Long, instruction: String? = null,
        onLine: suspend (CompanionEntity, String) -> Unit,
    ): Int {
        val conversation = db.conversations().get(conversationId) ?: return 0
        if (!conversation.isGroup) return 0
        val s = settings.current()
        val all = db.groupMembers().idsFor(conversationId).mapNotNull { companions.get(it) }
        val muted = conversation.groupMutedIds.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        val eligible = all.filterNot { it.id in muted }
        if (eligible.isEmpty()) return 0
        val initial = Recap.sent(recaps.live(conversation), s.historySize)
        val latestText = initial.lastOrNull { it.role == "user" && it.call == callId }?.content.orEmpty()
        val mentioned = GroupChats.mentioned(latestText, eligible)
        val last = initial.lastOrNull { it.role == "assistant" }?.senderCompanionId
        val startAt = eligible.indexOfFirst { it.id == last }
            .let { if (it < 0) 0 else (it + 1) % eligible.size }
        val rotated = eligible.drop(startAt) + eligible.take(startAt)
        val candidates = GroupLiveCall.select(rotated, mentioned,
            conversation.groupMaxReplies, conversation.groupMode, initial.isEmpty())
        val names = all.associate { it.id to it.name.trim().ifBlank { "TA" } }
        var count = 0
        for (ta in candidates) {
            currentCoroutineContext().ensureActive()
            val fresh = db.conversations().get(conversationId) ?: break
            if (!fresh.isGroup || ta.id in fresh.groupMutedIds.split(",").mapNotNull { it.toLongOrNull() })
                continue
            if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) == 0)
                break
            val voiceModel = ta.modelFor(heard = true)
            val key = secrets.key(voiceModel.baseUrl)?.takeIf { it.isNotBlank() } ?: continue
            val endpoint = ApiEndpoint(voiceModel.baseUrl, key, voiceModel.model)
            val current = db.conversations().get(conversationId) ?: break
            val history = GroupChats.historyFor(
                Recap.sent(recaps.live(current), s.historySize), ta.id, names, current.companionId)
            val memo = if (ToolGroup.Memory in s.tools) db.memories().allFor(ta.id) else emptyList()
            val stickers = StickerBook(db.stickers().all())
            val turnRule = GroupLiveCall.instruction(ta.name, eligible.map { it.name },
                instruction, mentioned.any { it.id == ta.id })
            val prompt = Prompt.messages(s, ta, history, ZonedDateTime.now(),
                tools = emptySet(), images = false, memories = memo, recap = current.recap,
                stickers = stickers, call = callId, calls = callsOutside(history), extraContext = turnRule)
            try {
                val buffer = StringBuilder()
                val result = callStep(conversationId, callId, endpoint, prepare(prompt),
                    emptyList(), mayRefuse = false) { buffer.append(it) }
                val text = buffer.toString().trim()
                if (result is Step.Said && text.isNotBlank() && !GroupChats.isSkip(text)) {
                    onLine(ta, text.take(GroupLiveCall.MAX_SPOKEN_CHARS))
                    count++
                    if (count >= fresh.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)) break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One failed model does not kill the whole call or fabricate speech for it.
                android.util.Log.w("GroupLiveCall", "model request failed for group role", e)
            }
        }
        return count
    }

    suspend fun callReply(conversationId: Long, callId: Long, instruction: String? = null, say: (String) -> Unit): String {
''', "add real group-model call replies")

patch(call,
'''        val ta = db.conversations().get(conversationId)?.companionId?.let { companions.get(it) }
        if (ta == null) {
''',
'''        val room = db.conversations().get(conversationId)
        val ta = room?.companionId?.let { companions.get(it) }
        if (ta == null) {
''',"resolve room type for call")

patch(call,
'''        val name = ta.name.trim().ifEmpty { "TA" }
        _state.update { it?.copy(name = name, avatar = ta.avatar, avatarEmoji = ta.avatarEmoji) }
''',
'''        val name = if (room?.isGroup == true) "群聊 · " + room.title else ta.name.trim().ifEmpty { "TA" }
        _state.update {
            it?.copy(name = name, avatar = if (room?.isGroup == true) null else ta.avatar,
                avatarEmoji = if (room?.isGroup == true) "👥" else ta.avatarEmoji)
        }
''',"show group phone identity")

patch(call,
'''            val t = launch { speak(conversationId, callId, s, name, player, ringing, since, instruction) }
''',
'''            val group = db.conversations().get(conversationId)?.isGroup == true
            val t = launch {
                if (group) speakGroup(conversationId, callId, s, name, player, ringing, since, instruction)
                else speak(conversationId, callId, s, name, player, ringing, since, instruction)
            }
''',"dispatch live phone stream for groups")

patch(call,
'''    private suspend fun speak(
''',
'''    /** The caller and multiple real AI companions share one live call session.
     * The microphone's existing half-duplex turn detection remains active:
     * after each user utterance, participating TAs speak in turn through their own TTS.
     * Interruption and hang-up cancel the group immediately, and only played speech
     * is entered into the actual call transcript under its real AI identity.
     */
    private suspend fun speakGroup(
        conversationId: Long, callId: Long, s: AppSettings, roomName: String,
        player: CallPlayer, ringing: Job?, since: Long, instruction: String?,
    ) {
        try {
            val count = chat.groupCallReply(conversationId, callId, instruction) { ta, text ->
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val actual = ta.name.trim().ifEmpty { "TA" }
                _state.update { it?.copy(name = "群聊 · $actual", avatar = ta.avatar,
                    avatarEmoji = ta.avatarEmoji, phase = CallPhase.Speaking, saying = text) }
                val override = ta.speechVoiceOverride?.trim().orEmpty()
                val voiceSettings = if (override.isEmpty()) s else s.copy(
                    speechVoices = s.speechVoices + (s.speechEngine to override),
                    speechVoice = override)
                var played = false
                try {
                    val piece = voiced(voiceSettings, CallSpeech.clean(text))
                    if (ringing != null && ringing.isActive) {
                        delay((since + MIN_RING_MS - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                        ringing.cancelAndJoin()
                        player.stop()
                        answered(callId)
                    }
                    if (piece.pcm != null) {
                        player.play(piece.pcm, piece.rate)
                        played = true
                    } else {
                        problem("$actual 的语音合成失败：" + (piece.why ?: "未知原因"))
                    }
                } finally {
                    player.stop()
                    // A cancelled/incomplete sound is not recorded as if fully spoken.
                    if (played) chat.callLine(conversationId, callId, "assistant", text,
                        speakerCompanionId = ta.id)
                    _state.update { it?.copy(saying = null) }
                }
            }
            if (count == 0) problem("本轮没有角色回应。可在群设置检查禁言、模型与调用额度。")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problem("群电话本轮出错：" + (e.message ?: e.javaClass.simpleName))
        } finally {
            player.stop()
            _state.update { it?.copy(saying = null, name = roomName) }
        }
    }

    private suspend fun speak(
''',"multiple per-role ASR->TTS call turns")

patch(screen,
'''if (!state.isGroup) GlassIconButton(Icons.Rounded.Call, "打电话", { startCall() }, page)''',
'''GlassIconButton(Icons.Rounded.Call, if (state.isGroup) "多人语音群电话" else "打电话", { startCall() }, page)''',
"bring back actual group phone UI")

build = root / "app/build.gradle.kts"
contents=build.read_text(encoding="utf-8")
for before,after in [('versionName = "0.37.36"','versionName = "0.37.37"'),
                     ('versionCode = 62058','versionCode = 62059')]:
    if contents.count(before)!=1: raise RuntimeError("version mismatch: "+before)
    contents=contents.replace(before,after)
build.write_text(contents,encoding="utf-8")
for name,dest in (
    ("GroupLiveCall.kt","app/src/main/java/com/cleo/cleos/ai/GroupLiveCall.kt"),
    ("GroupLiveCallTest.kt","app/src/test/java/com/cleo/cleos/ai/GroupLiveCallTest.kt"),
):
    out=root/dest
    out.parent.mkdir(parents=True,exist_ok=True)
    copyfile(here/name,out)
print("0.37.37 multi-companion live voice call integrated")
