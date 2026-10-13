package com.cleo.cleos.ui.chat
import com.cleo.cleos.ui.theme.LocalDisplayFontFamily

import android.Manifest
import android.os.SystemClock
import android.content.ClipData
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.cleo.cleos.ai.ChatRepository
import com.cleo.cleos.ai.WalletReactiveEvents
import com.cleo.cleos.ai.GroupChats
import com.cleo.cleos.ai.LyricLine
import com.cleo.cleos.ai.MusicAction
import com.cleo.cleos.ai.NowPlaying
import com.cleo.cleos.ai.ToolGroup
import com.cleo.cleos.ui.LocalPageShown
import com.cleo.cleos.ui.LifeChatBanner
import com.cleo.cleos.data.AppSettings
import com.cleo.cleos.data.BubbleStudioCodec
import com.cleo.cleos.ui.bubbles.BubbleSurface
import com.cleo.cleos.ui.bubbles.LocalBubbleCompanion
import com.cleo.cleos.ui.bubbles.LocalBubbleStudio
import com.cleo.cleos.ui.bubbles.bubbleTextColor
import com.cleo.cleos.data.CallRecords
import com.cleo.cleos.ai.McpAsk
import com.cleo.cleos.ai.Prompt
import com.cleo.cleos.ai.Recap
import com.cleo.cleos.ai.SecretRequest
import com.cleo.cleos.ai.SecretRequests
import com.cleo.cleos.ai.Speech
import com.cleo.cleos.ai.SpeechException
import com.cleo.cleos.ai.StreamingReply
import com.cleo.cleos.ai.Voice
import com.cleo.cleos.data.MessageAudio
import com.cleo.cleos.data.MessageAudios
import com.cleo.cleos.data.MessageImage
import com.cleo.cleos.data.MessageImages
import com.cleo.cleos.data.ChatTables
import com.cleo.cleos.data.ChatTablePart
import com.cleo.cleos.data.MessageQuote
import com.cleo.cleos.data.MessageQuotes
import com.cleo.cleos.data.MessageReactions
import com.cleo.cleos.data.MessageThoughts
import com.cleo.cleos.data.Pats
import com.cleo.cleos.data.Recalls
import com.cleo.cleos.data.MessageEdits
import com.cleo.cleos.data.ReactionEvents
import com.cleo.cleos.data.StickerBook
import com.cleo.cleos.data.StickerText
import com.cleo.cleos.data.db.MessageEntity
import com.cleo.cleos.data.db.StickerEntity
import com.cleo.cleos.glass.Backdrop
import com.cleo.cleos.glass.GlassButton
import com.cleo.cleos.glass.GlassIconButton
import com.cleo.cleos.glass.GlassShape
import com.cleo.cleos.glass.GlassSurface
import com.cleo.cleos.glass.LocalGlassPalette
import com.cleo.cleos.glass.liquidGlass
import com.cleo.cleos.ui.common.Avatar
import com.cleo.cleos.ui.common.Dates
import com.cleo.cleos.ui.common.GlassPage
import com.cleo.cleos.ui.common.GlassTopBar
import com.cleo.cleos.ui.common.TopBarHeight
import com.cleo.cleos.ui.common.appContainer
import com.cleo.cleos.ui.common.appViewModel
import com.cleo.cleos.ui.common.avatarLetter
import com.cleo.cleos.ui.common.fadeUnderTopBar
import com.cleo.cleos.ui.settings.SettingsPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** A gap longer than this between two messages gets a time line between them. */
private const val TIME_GAP_MS = 10 * 60 * 1000L

private data class Face(val file: String?, val letter: String)

/** Whose picture goes beside which bubbles; null when the chat shows no avatars. */
private data class Faces(val me: Face, val ai: Face)

private val LocalFaces = compositionLocalOf<Faces?> { null }

/** The chat's text: its size from settings, and the line height that goes with it. */
private val LocalChatType = compositionLocalOf { ChatType(ChatType.DEFAULT) }
private val AvatarSize = 34.dp
private val AvatarGap = 8.dp

/** What a row gives up on the avatar's side, so lines without one still line up. */
private val AvatarSlot = AvatarSize + AvatarGap

private val MaxPictureHeight = 260.dp

/** The input bar's height on one line; its round ends have half this as radius. */
private val BarHeight = 50.dp

/** Pictures in one message: as many as are sent along with a request. */
private const val MAX_ATTACHMENTS = Prompt.MAX_IMAGES

private sealed interface ChatRow {
    val key: Any

    /** These cards are reconstructed from actual persistent wallet events. */
    data class Wallet(val event: ChatWalletItem) : ChatRow {
        override val key: Any get() = event.id
    }

    data class Stamp(val at: Long) : ChatRow {
        override val key: Any get() = "t$at"
    }

    /** Where the messages sent verbatim begin: before it, the TA has only the recap. */
    data object RecapMark : ChatRow {
        override val key: Any get() = "recap"
    }

    /** [showFace]: the newest of a run of messages from one side, which alone gets the avatar. */
    data class Message(val message: MessageEntity, val isLast: Boolean, val showFace: Boolean = true) : ChatRow {
        override val key: Any get() = message.id
    }

    /** Above what the TA said on its own when something it noted came due (ai/Later.kt): nobody asked. */
    data class Woke(val at: Long) : ChatRow {
        override val key: Any get() = "w$at"
    }

    /** Tool calls that follow one another, folded into one line that opens: [lines] oldest first. */
    data class ToolGroup(val lines: List<MessageEntity>) : ChatRow {
        override val key: Any get() = "g${lines.first().id}"
    }
}

/** A tool call's line in the chat (one without a line is silent). */
private fun MessageEntity.isToolLine() = role == "tool" && !note.isNullOrBlank()

/**
 * Two or more tool lines in a row (nothing between them in the chat but what is silent) become one
 * ToolGroup; one alone stays as it is. [rows] is newest first, as buildRows makes them.
 */
private fun foldTools(rows: List<ChatRow>): List<ChatRow> {
    val out = ArrayList<ChatRow>(rows.size)
    var i = 0
    while (i < rows.size) {
        var j = i
        while (j < rows.size && (rows[j] as? ChatRow.Message)?.message?.isToolLine() == true) j++
        if (j - i >= 2) {
            out += ChatRow.ToolGroup(rows.subList(i, j).map { (it as ChatRow.Message).message }.reversed())
            i = j
        } else {
            out += rows[i]
            i++
        }
    }
    return out
}

/**
 * Rows with nothing to draw: an assistant turn that only called tools, without a thought to
 * show (the results have their own lines), a tool result without a line (a request shows its
 * card instead), and what was said or done in a phone call (the call's card stands for it).
 */
private fun MessageEntity.silent() = role == Recalls.EVENT || role == ReactionEvents.EVENT ||
    (thoughtOnly() && thought == null) || (role == "tool" && note.isNullOrBlank()) || call != null

/** An assistant turn that only called tools: at most its thinking is drawn, on a line of its own. */
private fun MessageEntity.thoughtOnly() = role == "assistant" && content.isEmpty() &&
    error == null && images == null && audio == null

/** Which side a bubble is on: true for the person's, false for the TA's, null for lines that aren't bubbles. */
private fun MessageEntity.side(): Boolean? = when {
    role == "user" && note == null -> true
    role == "assistant" && !thoughtOnly() -> false
    else -> null
}

/** Whether the message is folded into a recap that ends at [until] (a time, then an id). */
private fun MessageEntity.foldedBy(until: Pair<Long, Long>) = createdAt < until.first || (createdAt == until.first && id <= until.second)

/**
 * Newest first, because the list is laid out bottom-up. Several messages in a row from one
 * side show the avatar once, beside the newest, the way chat apps stack them; a line or a
 * time between them starts a new run. With [eachFace], every message shows it, the way
 * WeChat does. With a recap, its mark goes between the last message folded into it and the
 * first one after. What the TA said on its own gets a line above the first of it.
 */
private fun buildRows(messages: List<MessageEntity>, recapUntil: Pair<Long, Long>? = null, eachFace: Boolean = false): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size + 8)
    var newerSide: Boolean? = null
    var marked = false
    // A pat after the TA's last reply must not take its retry button away.
    val last = messages.indexOfLast { it.role != "pat" && it.role != Recalls.EVENT && it.role != ReactionEvents.EVENT }
    for (i in messages.indices.reversed()) {
        val m = messages[i]
        if (recapUntil != null && !marked && m.foldedBy(recapUntil)) {
            rows += ChatRow.RecapMark
            marked = true
            newerSide = null
        }
        if (m.silent()) continue
        val side = m.side()
        rows += ChatRow.Message(m, isLast = i == last, showFace = eachFace || side == null || side != newerSide)
        newerSide = side
        val prev = messages.getOrNull(i - 1)
        val gap = prev == null || m.createdAt - prev.createdAt > TIME_GAP_MS
        if (m.proactive) {
            // The first of a run: what is drawn just before it is not the TA's own too (or is, but long before).
            val older = (i - 1 downTo 0).firstOrNull { !messages[it].silent() }?.let { messages[it] }
            if (gap || older == null || !older.proactive) {
                rows += ChatRow.Woke(m.createdAt)
                newerSide = null
            }
        }
        if (gap) {
            rows += ChatRow.Stamp(m.createdAt)
            newerSide = null
        }
    }
    return foldTools(rows)
}

@Composable
fun ChatTab(
    bottomInset: Dp,
    onOpenSettings: () -> Unit,
    onOpenSettingsPage: (SettingsPage) -> Unit,
    onOpenConversations: () -> Unit,
    onOpenImage: (String) -> Unit,
) {
    val vm = appViewModel { ChatViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = appContainer()
    val allGoals by remember { c.goalStore.records }.collectAsStateWithLifecycle()
    val currentGoal = allGoals.lastOrNull { it.conversationId==state.conversationId &&
        it.companionId==state.companionId && it.status !in com.cleo.cleos.ai.GoalRules.terminal }
    var goalPanelOpen by remember { mutableStateOf(false) }
    var lifeHeaderHeight by remember { mutableIntStateOf(0) }
    val goalHistory = allGoals.filter { it.conversationId==state.conversationId &&
        it.companionId==state.companionId }
    // Whether this is the tab on screen: the page stays composed while another tab shows (MainScreen).
    val pageShown = LocalPageShown.current
    // This chat on screen: what a TA says on its own here needs no notification (see Later). Off
    // again when the app goes to the back, another tab or screen comes up, or another conversation
    // is opened. A notification already up stays: it goes when the person swipes it away or taps it,
    // not because the chat was opened (they asked to keep them).
    LifecycleResumeEffect(state.conversationId, pageShown) {
        // Null until the conversation has loaded.
        val shown = state.conversationId?.takeIf { pageShown }
        if (shown != null) c.chatOnScreen = shown
        onPauseOrDispose { if (shown != null && c.chatOnScreen == shown) c.chatOnScreen = null }
    }
    val companions by remember { c.companions.all }.collectAsStateWithLifecycle(emptyList())
    // 一起听歌: what the phone plays, followed only while this screen is up and the switch is on.
    val appSettings by remember { c.settings.settings }.collectAsStateWithLifecycle(AppSettings())
    val listeningOn = ToolGroup.Music in appSettings.tools
    val nowPlaying by remember(listeningOn, pageShown) {
        if (listeningOn && pageShown) c.music.watch() else flowOf(null)
    }.collectAsStateWithLifecycle(null)
    // Up while a song plays, and a while after it is paused; the last song stays drawn as the bar goes.
    var barShown by remember { mutableStateOf(false) }
    var barSong by remember { mutableStateOf<NowPlaying?>(null) }
    LaunchedEffect(nowPlaying) {
        val np = nowPlaying
        if (np != null) barSong = np
        val left = when {
            np == null -> 0L
            np.playing -> Long.MAX_VALUE
            else -> PAUSED_SHOWN_MS - (SystemClock.elapsedRealtime() - np.at)
        }
        barShown = left > 0
        if (left in 1 until Long.MAX_VALUE) {
            delay(left)
            barShown = false
        }
    }
    // The player's own lyrics when it gives them (they can come a moment after the song does); LRCLIB's otherwise.
    val fetched by produceState<List<LyricLine>?>(null, barSong?.song, barShown) {
        value = null
        val np = barSong
        if (np != null && barShown && np.words == null) value = c.lyrics.of(np.title, np.artist, np.durationMs)
    }
    val words = barSong?.words ?: fetched
    var switching by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf(false) }
    var managingGroup by remember { mutableStateOf(false) }
    var groupOptionsOpen by remember { mutableStateOf(false) }
    var readingRecap by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Voice messages: the recording under way, what the indicator shows, and the one playing.
    val recorder = remember { VoiceRecorder(c.images.dir) }
    // Separate mic lifetime: composer keeps hold-to-send, + is tap-to-dictate.
    val dictationRecorder = remember { VoiceRecorder(c.images.dir) }
    var dictating by remember { mutableStateOf(false) }
    var dictationBusy by remember { mutableStateOf(false) }
    var dictationMs by remember { mutableLongStateOf(0L) }
    var dictationLevel by remember { mutableFloatStateOf(0f) }
    var recording by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var recordedMs by remember { mutableLongStateOf(0L) }
    var loudness by remember { mutableFloatStateOf(0f) }
    var voiceHint by remember { mutableStateOf<String?>(null) }
    var askVoiceSetup by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<String?>(null) }
    // Counts what the person sends: sending takes the chat down to the newest, wherever it was.
    var sentCount by remember { mutableIntStateOf(0) }
    // Review is attached to the chat that requested it, not to unrelated TAs.
    val galleryReviewIds by com.cleo.cleos.ai.GalleryReviewQueue.ids.collectAsStateWithLifecycle()
    val galleryReviewOwner by com.cleo.cleos.ai.GalleryReviewQueue.owner.collectAsStateWithLifecycle()
    var galleryReviewOpen by remember { mutableStateOf(false) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voiceHint = if (granted) "可以了，再按住说话" else "要给 Cleos 用话筒的权限，才能发语音"
    }
    // Calls: the settings page that has what is missing before one can begin, and the call whose words are open.
    var askCallSetup by remember { mutableStateOf<SettingsPage?>(null) }
    var readingCall by remember { mutableStateOf<Long?>(null) }
    // The "tool" row whose call is open: what was asked of the tool, and what it answered.
    var readingTool by remember { mutableStateOf<Long?>(null) }
    val askMicForCall = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.call() else voiceHint = "要给 Cleos 用话筒的权限，才能打电话"
    }

    // A TA's voice message being made ready for the ear (EarVoice) before it plays.
    val preparing = remember { arrayOfNulls<Job>(1) }
    val playScope = rememberCoroutineScope()

    fun stopPlaying() {
        preparing[0]?.cancel()
        preparing[0] = null
        player[0]?.let {
            runCatching { it.stop() }
            it.release()
        }
        player[0] = null
        playing = null
    }

    /**
     * Plays [file]. One of the TA's ([theirs]) plays beside the ear when headphones are on and that
     * is switched on: made the first time it is played, which takes a moment; it shows as playing
     * meanwhile, and a tap then stops it as usual.
     */
    fun play(file: String, theirs: Boolean) {
        val again = playing == file
        stopPlaying()
        if (again) return
        playing = file
        preparing[0] = playScope.launch {
            val ear = if (theirs && appSettings.earVoice && c.ear.headphones()) c.ear.prepared(file) else null
            preparing[0] = null
            val p = MediaPlayer()
            runCatching {
                p.setDataSource((ear ?: c.images.file(file)).path)
                p.setOnCompletionListener { stopPlaying() }
                p.prepare()
                p.start()
            }.onSuccess {
                player[0] = p
            }.onFailure {
                p.release()
                playing = null
                voiceHint = "这段语音放不了"
            }
        }
    }

    // 朗读: a message read aloud in the TA's voice. Asked for a voice first when there is none.
    var askRead by remember { mutableStateOf(false) }

    /**
     * Reads [text] aloud, a piece at a time: the next is being made while one plays. Made once and
     * kept (Speaker.reading), so a second time is instant. While it goes, [playing] is the
     * message's mark, so the menu says 停止朗读 and another tap stops it.
     */
    fun readAloud(messageId: Long, text: String) {
        val mark = readingMark(messageId)
        val again = playing == mark
        stopPlaying()
        if (again) return
        if (!state.speechReady) {
            askRead = true
            return
        }
        val parts = Speech.readParts(text)
        if (parts.pieces.isEmpty()) return
        val dir = File(context.cacheDir, "read")
        val settingsNow = appSettings
        val roleVoice = if (state.isGroup) {
            state.messages.firstOrNull { it.id == messageId }?.senderCompanionId
                ?.let { id -> state.groupSpeakers.firstOrNull { it.id == id }?.voiceOverride }
        } else null
        val preferred = roleVoice?.trim().orEmpty()
        val voiceSettings = if (preferred.isNotEmpty()) settingsNow.copy(
            speechVoice = preferred,
            speechVoices = settingsNow.speechVoices + (settingsNow.speechEngine to preferred),
        ) else settingsNow
        playing = mark
        preparing[0] = playScope.launch {
            try {
                if (parts.cut) voiceHint = "太长了，只读前面一部分"
                coroutineScope {
                    var next = async { c.speaker.reading(voiceSettings, parts.pieces[0], dir) }
                    for (i in parts.pieces.indices) {
                        val file = next.await()
                        if (i + 1 < parts.pieces.size) next = async { c.speaker.reading(voiceSettings, parts.pieces[i + 1], dir) }
                        playToEnd(file) { player[0] = it }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpeechException) {
                voiceHint = e.message ?: "没读出来"
            } catch (e: Exception) {
                voiceHint = "这段读不出来"
            } finally {
                if (playing == mark) {
                    playing = null
                    preparing[0] = null
                }
            }
        }
    }

    /** A call needs the model, ears (voice to text) and a voice (TA 的声音); what is missing is asked for first. */
    fun startCall() {
        when {
            !state.hasApiKey -> onOpenSettingsPage(SettingsPage.Model)
            !state.voiceReady -> askCallSetup = SettingsPage.VoiceInput
            !state.speechReady -> askCallSetup = SettingsPage.Voice
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                askMicForCall.launch(Manifest.permission.RECORD_AUDIO)
            else -> {
                stopPlaying()
                vm.call()
            }
        }
    }

    fun startVoice(): Boolean {
        when {
            !state.voiceReady -> askVoiceSetup = true
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                askMic.launch(Manifest.permission.RECORD_AUDIO)
            !recorder.start() -> voiceHint = "话筒打不开，可能正被别的应用用着"
            else -> {
                stopPlaying()
                recordedMs = 0
                cancelling = false
                recording = true
                return true
            }
        }
        return false
    }

    fun endVoice(cancel: Boolean) {
        if (!recording) return
        recording = false
        cancelling = false
        if (cancel) {
            recorder.cancel()
            return
        }
        val clip = recorder.stop()
        when {
            clip == null -> voiceHint = "说话时间太短了"
            !vm.sendVoice(clip) -> voiceHint = "没发出去，再试一次"
            else -> sentCount++
        }
    }

    LaunchedEffect(recording) {
        val t0 = System.currentTimeMillis()
        while (recording) {
            recordedMs = System.currentTimeMillis() - t0
            loudness = recorder.level
            // A minute is as long as one goes; it is sent as it is.
            if (recordedMs >= Voice.MAX_MS) endVoice(cancel = false)
            delay(100)
        }
    }
    LaunchedEffect(voiceHint) {
        if (voiceHint != null) {
            delay(2200)
            voiceHint = null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            stopPlaying()
            recorder.cancel()
            dictationRecorder.cancel()
            // Off to another screen: nothing is being typed here any more.
            vm.typing(false)
        }
    }
    // Off to another tab, the page staying as it is: the same, but for what is in the box, which waits here.
    LaunchedEffect(pageShown) {
        if (!pageShown) {
            stopPlaying()
            endVoice(cancel = true)
            dictationRecorder.cancel()
            dictating = false
        }
    }
    val palette = LocalGlassPalette.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    // Picker selections are semantic IDs, separate from editable human-readable @ text.
    var chosenMentionIds by remember(state.conversationId) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var inputHeight by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS)) {
        vm.attach(it)
    }
    // Stickers: the collection, the drawer inside the input's glass, and a picture picked to add.
    val stickers by remember { c.stickers.all }.collectAsStateWithLifecycle(emptyList())
    val stickerBook = remember(stickers) { StickerBook(stickers) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var plusOpen by rememberSaveable { mutableStateOf(false) }
    var locationPickerOpen by remember(state.conversationId) { mutableStateOf(false) }
    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { locationPickerOpen = true }
    var walletAction by remember { mutableStateOf<ChatMoneyKind?>(null) }
    var walletBusy by remember { mutableStateOf(false) }
    val walletBook by remember { c.wallet.state }.collectAsStateWithLifecycle()
    LaunchedEffect(state.conversationId) {
        if (state.conversationId != null) runCatching { c.wallet.settleExpired() }
    }
    var deletingSticker by remember { mutableStateOf<StickerEntity?>(null) }
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickSticker(uri) { voiceHint = it }
    }
    var cameraOutput by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraBusy by remember { mutableStateOf(false) }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraOutput?.let(android.net.Uri::parse)
        if (taken && uri != null) {
            vm.attach(listOf(uri)) // preview the captured photo; sending remains user-confirmed
        } else {
            cameraFilePath?.let { java.io.File(it).delete() }
        }
        cameraOutput = null
        cameraFilePath = null
        cameraBusy = false
    }
    val focusManager = LocalFocusManager.current
    var selectionMode by remember(state.conversationId) { mutableStateOf(false) }
    var selectedMessageIds by remember(state.conversationId) { mutableStateOf(setOf<Long>()) }
    var batchBusy by remember { mutableStateOf(false) }
    var askingDeleteBatch by remember { mutableStateOf(false) }
    var choosingForwardMode by remember { mutableStateOf(false) }
    var choosingForwardTarget by remember { mutableStateOf(false) }
    var mergeForward by remember { mutableStateOf(false) }
    var forwardConversations by remember { mutableStateOf(emptyList<com.cleo.cleos.data.db.ConversationEntity>()) }
    fun leaveSelection() {
        selectedMessageIds = emptySet()
        selectionMode = false
        askingDeleteBatch = false
        choosingForwardMode = false
        choosingForwardTarget = false
    }
    fun toggleSelection(id: Long) {
        selectedMessageIds = if (id in selectedMessageIds) selectedMessageIds - id
        else if (selectedMessageIds.size < BatchMessageActions.MAX_SELECTED) selectedMessageIds + id
        else {
            voiceHint = "每次最多选择100条消息"
            selectedMessageIds
        }
    }
    fun beginSelection(id: Long) {
        selectionMode = true
        selectedMessageIds = setOf(id)
        drawerOpen = false
        plusOpen = false
        // Focus loss dismisses IME; keyboard controller is declared later.
        focusManager.clearFocus()
    }
    LaunchedEffect(state.conversationId, state.messages) {
        if (selectionMode) {
            val valid = state.messages.filter(BatchMessageActions::selectable).mapTo(HashSet()) { it.id }
            selectedMessageIds = selectedMessageIds.intersect(valid)
        }
    }
    LaunchedEffect(choosingForwardTarget) {
        if (choosingForwardTarget) forwardConversations =
            c.db.conversations().all().sortedByDescending { it.updatedAt }
    }
    BackHandler(enabled = selectionMode && pageShown) { leaveSelection() }
    BackHandler(enabled = (drawerOpen || plusOpen) && pageShown) {
        drawerOpen = false
        plusOpen = false
        if (dictating) {
            dictationRecorder.cancel()
            dictating = false
        }
    }

    if(goalPanelOpen) {
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
        conversationId = state.conversationId,
        visible = pageShown,
        onStatus = { voiceHint = it },
    )
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val inputBottom = if (imeBottom > bottomInset) imeBottom + 8.dp else bottomInset
    val rows = remember(state.messages, state.recapUntil, state.avatarEachMessage,
        walletBook, state.conversationId) {
        buildRows(state.messages.filterNot(WalletReactiveEvents::isEvent),
            state.recapUntil, eachFace = state.avatarEachMessage)
            .toMutableList().also { merged ->
                // Merge by timestamp: later text messages must appear AFTER older wallet cards.
                chatWalletItems(walletBook, state.conversationId).forEach { event ->
                    val index = merged.indexOfFirst {
                        it is ChatRow.Message && it.message.createdAt <= event.at
                    }
                    merged.add(if (index < 0) merged.size else index, ChatRow.Wallet(event))
                }
            }
    }
    // The calls something was said in: only those have words to open.
    val callsSaid = remember(state.messages) { state.messages.mapNotNullTo(HashSet()) { it.call } }
    // The chat's text, for the bubbles and for what is being typed alike.
    val displayFont = LocalDisplayFontFamily.current
    val chatType = remember(state.chatTextSize, displayFont) { ChatType(state.chatTextSize, displayFont) }
    // 拍一拍: double tap an avatar. Long-press the TA avatar to edit how it reads.
    val haptics = LocalHapticFeedback.current
    var editingPat by remember { mutableStateOf(false) }
    val buzz = state.patBuzz
    // A pat sent back by the TA buzzes only when it has just arrived, not when old chat is reopened.
    val lastPat = state.messages.lastOrNull { it.role == "pat" }
    var buzzedPat by remember { mutableStateOf(-1L) }
    LaunchedEffect(lastPat?.id) {
        val p = lastPat ?: return@LaunchedEffect
        val fresh = System.currentTimeMillis() - p.createdAt < 5_000
        if (buzz && fresh && p.id != buzzedPat && Pats.decode(p.content)?.who == Pats.FROM_AI) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            buzzedPat = p.id
        }
    }
    val patActions = remember(buzz) {
        PatActions(
            pat = { ai, targetCompanionId ->
                if (buzz) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.pat(ai, targetCompanionId)
            },
            edit = { editingPat = true },
        )
    }
    val faces = if (!state.chatAvatars) {
        null
    } else {
        Faces(
            me = Face(state.userAvatar, avatarLetter(state.userName, "我")),
            ai = Face(state.aiAvatar, state.aiAvatarEmoji ?: avatarLetter(state.aiName, "TA")),
        )
    }

    // Follow new messages, unless the user has scrolled up to read something older. Judged by
    // where the newest message from before this update is now, not by the bottom row's index:
    // several rows can arrive at once (what a TA says on its own comes with a line and a time
    // above it, often while the app was in the background), and the list keeps what was in view
    // where it was, so the index alone would read "scrolled up".
    var newestBefore by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.messages.lastOrNull()?.id, state.streaming?.text?.isEmpty(), state.streaming?.activity) {
        val live = if (state.streaming != null) 1 else 0
        val was = rows.indexOfFirst { it is ChatRow.Message && it.message.id == newestBefore }.coerceAtLeast(0) + live
        newestBefore = state.messages.lastOrNull()?.id
        // Not while a searched-for message is being brought into view.
        if (c.chat.focus.value == null && listState.firstVisibleItemIndex <= was + 2) listState.animateScrollToItem(0)
    }
    // Their own message is always followed down to, like in any chat.
    LaunchedEffect(sentCount) {
        if (sentCount > 0) listState.animateScrollToItem(0)
    }
    // Something in the box: the TA waits for it before answering.
    LaunchedEffect(input.isNotBlank(), pageShown, cameraBusy) {
        vm.typing(pageShown && (input.isNotBlank() || cameraBusy))
    }

    val scope = rememberCoroutineScope()
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun startDictationGranted(): Boolean {
        if (dictating || dictationBusy || recording) return false
        if (!state.voiceReady) { askVoiceSetup = true; return false }
        if (!dictationRecorder.start()) {
            voiceHint = "话筒暂时打不开，请关闭占用麦克风的其他应用后重试"
            return false
        }
        stopPlaying()
        dictationMs = 0L
        dictationLevel = 0f
        dictating = true
        keyboard?.hide()
        focusManager.clearFocus()
        return true
    }

    val requestDictationMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startDictationGranted()
        else voiceHint = "请先允许使用麦克风，才能通过＋进行语音输入"
    }

    fun finishDictation(cancel: Boolean) {
        if (!dictating) return
        dictating = false
        if (cancel) {
            dictationRecorder.cancel()
            return
        }
        val clip = dictationRecorder.stop()
        if (clip == null) {
            voiceHint = "说话时间太短了，请再试一次"
            return
        }
        val originalConversationId = state.conversationId
        dictationBusy = true
        scope.launch {
            try {
                val settings = c.settings.current()
                if (settings.voiceBaseUrl.isBlank() || settings.voiceModel.isBlank()) {
                    voiceHint = "请先在设置中配置语音转文字服务"
                    return@launch
                }
                val recognized = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    c.transcriber.transcribe(
                        settings.voiceBaseUrl, settings.voiceModel, c.images.file(clip.file)
                    ).trim()
                }
                if (recognized.isBlank()) {
                    voiceHint = "没有识别出文字，请重试"
                } else if (pageShown && vm.state.value.conversationId == originalConversationId) {
                    input = VoiceDictationDraft.append(input, recognized)
                    plusOpen = false
                    inputFocus.requestFocus()
                    keyboard?.show()
                } else {
                    voiceHint = "已经切换会话，未将录音内容写入其他聊天"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                voiceHint = "语音转文字失败：" + (e.message ?: "请检查网络和服务配置")
            } finally {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                        c.images.delete(listOf(clip.file))
                    }
                } finally {
                    dictationBusy = false
                }
            }
        }
    }

    LaunchedEffect(dictating) {
        val start = android.os.SystemClock.elapsedRealtime()
        while (dictating) {
            dictationMs = android.os.SystemClock.elapsedRealtime() - start
            dictationLevel = dictationRecorder.level
            if (dictationMs >= Voice.MAX_MS) {
                finishDictation(cancel = false)
                break
            }
            delay(100)
        }
    }

    LaunchedEffect(state.conversationId) {
        if (dictating) {
            finishDictation(cancel = true)
            voiceHint = "切换聊天后已取消本次语音输入"
        }
    }

    // The message a quote was tapped to find, lit up for a moment.
    var flashed by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(flashed) {
        if (flashed != null) {
            delay(1400)
            flashed = null
        }
    }

    fun quoteLabel(q: MessageQuote): String {
        val who = if (q.role == "user") state.userName.ifBlank { "我" } else state.aiName.ifBlank { "TA" }
        return "$who：${q.text}"
    }

    /** Where message [id] is drawn: its own row, or the card of the call it was said in. */
    fun rowOf(id: Long): Int {
        val own = rows.indexOfFirst { it is ChatRow.Message && it.message.id == id }
        if (own >= 0) return own
        val call = state.messages.firstOrNull { it.id == id }?.call ?: return -1
        return rows.indexOfFirst { it is ChatRow.Message && it.message.id == call }
    }

    /** Scrolls to the message a quote is of; one no longer in the chat is said to be gone. */
    fun jumpTo(id: Long) {
        val index = rowOf(id)
        if (index < 0) {
            voiceHint = "原消息已经不在了"
            return
        }
        // The live reply, while there is one, is the list's first item.
        val at = index + if (state.streaming != null) 1 else 0
        scope.launch { listState.animateScrollToItem(at) }
        flashed = id
    }

    // A message found by a search: once its conversation is the one on screen, straight to it,
    // lit up. One that is gone by then is said to be.
    val focus by remember { c.chat.focus }.collectAsStateWithLifecycle()
    LaunchedEffect(focus, rows, state.conversationId) {
        val f = focus ?: return@LaunchedEffect
        if (state.conversationId != f.conversationId) return@LaunchedEffect
        val index = rowOf(f.messageId)
        if (index < 0) {
            voiceHint = "那条消息已经不在了"
        } else {
            listState.scrollToItem(index + if (state.streaming != null) 1 else 0)
            flashed = f.messageId
            // Said on the phone: what was said in that call, opened.
            state.messages.firstOrNull { it.id == f.messageId }?.call?.let { readingCall = it }
        }
        c.chat.shown()
    }

    GlassPage(
        overlay = { page ->
            // The title is the TA; tapping it switches to another one or adds one.
            GlassTopBar(
                title = if (selectionMode) "已选择 " + selectedMessageIds.size + " 条消息" else if (state.isGroup) "${state.groupAvatarEmoji} ${state.aiName.ifBlank { "群聊" }}" else state.aiName.ifBlank { "聊天" },
                subtitle = if (selectionMode) null else state.model.takeIf { it.isNotBlank() }?.let { "$it ▾" },
                backdrop = page,
                leading = { if (selectionMode) TextButton(onClick = { leaveSelection() }) { Text("取消") } else GlassIconButton(Icons.Rounded.Forum, "对话记录", onOpenConversations, page) },
                trailing = {
                    TextButton(onClick={goalPanelOpen=true}) {
                        Text("◎ 目标",fontSize=13.sp)
                    }
                },
                onTitleClick = { if (!selectionMode) switching = true },
                titleMenu = {
                    DropdownMenu(expanded = switching, onDismissRequest = { switching = false }) {
                        companions.forEach { ta ->
                            val here = ta.id == state.companionId
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.isGroup) "和${ta.name.ifBlank { "TA" }}单聊" else ta.name.ifBlank { "TA" },
                                        fontWeight = if (!state.isGroup && here) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = { Avatar(ta.avatar, ta.avatarEmoji ?: avatarLetter(ta.name, "TA"), 28.dp) },
                                trailingIcon = if (!state.isGroup && here) ({ Icon(Icons.Rounded.Check, contentDescription = "正在聊") }) else null,
                                onClick = {
                                    switching = false
                                    if (state.isGroup) vm.openSingle(ta.id) else if (!here) vm.switchTo(ta.id)
                                },
                            )
                        }
                        if (state.isGroup) {
                            DropdownMenuItem(
                                text = { Text("群聊设置与声音") },
                                onClick = { switching = false; groupOptionsOpen = true },
                            )
                            DropdownMenuItem(
                                text = { Text("让他们继续聊") },
                                onClick = { switching = false; vm.continueGroup() },
                            )
                            DropdownMenuItem(
                                text = { Text("管理群成员") },
                                leadingIcon = { Icon(Icons.Rounded.Forum, contentDescription = null) },
                                onClick = {
                                    switching = false
                                    managingGroup = true
                                },
                            )
                        }
                        if (companions.size >= 2) {
                            DropdownMenuItem(
                                text = { Text(if (state.isGroup) "创建另一个群聊" else "创建群聊") },
                                leadingIcon = { Icon(Icons.Rounded.Forum, contentDescription = null) },
                                onClick = {
                                    switching = false
                                    creatingGroup = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("添加一个 TA") },
                            leadingIcon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                            onClick = {
                                switching = false
                                vm.addCompanion(onOpenSettings)
                            },
                        )
                    }
                },
            )
            Column(
                modifier=Modifier.align(Alignment.TopCenter)
                    .padding(top=statusTop + TopBarHeight + 8.dp,start=16.dp,end=16.dp)
                    .onSizeChanged { lifeHeaderHeight = it.height },
                verticalArrangement=Arrangement.spacedBy(6.dp),
            ) {
                if(currentGoal!=null && !selectionMode) {
                    GoalStatusPill(currentGoal, onOpen={goalPanelOpen=true})
                }
                if (!selectionMode) LifeChatBanner(
                    people = if (state.isGroup) state.groupMembers.map { it.id to it.name }
                        else listOf(state.companionId to state.aiName),
                    conversationId = state.conversationId,
                )
            }
            AnimatedVisibility(
                visible = barShown && !selectionMode,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusTop + TopBarHeight +
                        with(density) { lifeHeaderHeight.toDp() } + 10.dp,
                        start = 16.dp, end = 16.dp),
            ) {
                barSong?.let { np ->
                    ListeningBar(
                        backdrop = page,
                        np = np,
                        words = words,
                        who = state.aiName.ifBlank { "TA" },
                        onToggle = { runCatching { c.music.control(if (np.playing) MusicAction.Pause else MusicAction.Play) } },
                        onPrevious = { runCatching { c.music.control(MusicAction.Previous) } },
                        onNext = { runCatching { c.music.control(MusicAction.Next) } },
                        onOpen = {
                            if (!c.music.open()) voiceHint =
                                "无法跳转当前播放器，请确认音乐 App 仍在运行且已安装"
                        },
                    )
                }
            }
            if (selectionMode) {
                ChatMultiSelectBottomBar(
                    backdrop = page, bottomPadding = inputBottom,
                    count = selectedMessageIds.size, busy = batchBusy,
                    onForward = { if (selectedMessageIds.isNotEmpty()) choosingForwardMode = true },
                    onFavorite = {
                        if (selectedMessageIds.isNotEmpty() && !batchBusy) {
                            batchBusy = true
                            val sourceId = state.conversationId
                            val chosen = selectedMessageIds.toSet()
                            val known = state.groupSpeakers.associate { it.id to it.name }
                            val aiName = state.aiName
                            scope.launch {
                                var done = 0
                                try {
                                    require(sourceId != null) { "当前会话无效" }
                                    val rows = BatchMessageActions.ordered(
                                        c.db.messages().byIds(chosen), chosen, sourceId)
                                    for (m in rows) {
                                        val name = if (m.role == "user") "我"
                                            else known[m.senderCompanionId] ?: aiName.ifBlank { "TA" }
                                        c.moments.favorite(m, name)
                                        done++
                                    }
                                    voiceHint = "已收藏 " + done + " 条消息"
                                    leaveSelection()
                                } catch (e: Exception) {
                                    voiceHint = "已收藏" + done + "条；" + (e.message ?: "部分收藏失败")
                                } finally { batchBusy = false }
                            }
                        }
                    },
                    onDelete = { if (selectedMessageIds.isNotEmpty()) askingDeleteBatch = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = inputBottom)
                        .onSizeChanged { inputHeight = it.height },
                )
            } else {
            if (galleryReviewIds.isNotEmpty() && galleryReviewOwner == state.conversationId) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = inputBottom +
                            with(density) { inputHeight.toDp() } + 8.dp)
                        .background(Color(0xFFE6F0FA), RoundedCornerShape(18.dp))
                        .clickable { galleryReviewOpen = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "AI 建议检查 " + galleryReviewIds.size + " 张照片 · 查看并决定是否删除",
                        fontSize = 13.sp,
                        color = Color(0xFF294765),
                    )
                }
            }
            ChatInputBar(
                backdrop = page,
                type = chatType,
                text = input,
                onTextChange = { input = it },
                isGroup = state.isGroup,
                groupMembers = state.groupMembers,
                onMentionChosen = { memberId, shownName ->
                    chosenMentionIds = chosenMentionIds + (memberId to shownName)
                },
                attachments = vm.attachments,
                attaching = vm.attaching,                plusOpen = plusOpen,
                onPlus = {
                    if (plusOpen && dictating) finishDictation(cancel = true)
                    plusOpen = !plusOpen
                    drawerOpen = false
                    if (plusOpen) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                },
                plusPanel = {
                    ChatPlusPanel(
                        onAlbum = {
                            plusOpen = false
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onCamera = {
                            plusOpen = false
                            try {
                                val directory = java.io.File(context.cacheDir, "huaimin-camera")
                                if (!directory.exists() && !directory.mkdirs()) error("拍摄缓存创建失败")
                                directory.listFiles()?.filter {
                                    System.currentTimeMillis() - it.lastModified() > 172_800_000L
                                }?.forEach { it.delete() }
                                val file = java.io.File(directory,
                                    "photo-" + java.util.UUID.randomUUID().toString() + ".jpg")
                                if (!file.createNewFile()) error("照片文件创建失败")
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, context.packageName + ".huaimin.camera", file)
                                cameraFilePath = file.absolutePath
                                cameraOutput = uri.toString()
                                cameraBusy = true
                                cameraCapture.launch(uri)
                            } catch (e: Exception) {
                                cameraFilePath?.let { java.io.File(it).delete() }
                                cameraFilePath = null
                                cameraOutput = null
                                cameraBusy = false
                                voiceHint = "无法打开手机相机：" + (e.message ?: "相机不可用")
                            }
                        },
                        onCall = { plusOpen = false; startCall() },
                        onRedPacket = { plusOpen = false; walletAction = ChatMoneyKind.PACKET },
                        onTransfer = { plusOpen = false; walletAction = ChatMoneyKind.TRANSFER },
                        onLocation = {
                            plusOpen = false
                            val fine = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val coarse = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (fine || coarse) locationPickerOpen = true
                            else requestLocation.launch(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ))
                        },
                        onVoiceInput = {
                            when {
                                dictating -> finishDictation(cancel = false)
                                dictationBusy -> Unit
                                recording -> voiceHint = "请先结束长按语音，再开始语音输入"
                                !state.voiceReady -> askVoiceSetup = true
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                                    requestDictationMic.launch(Manifest.permission.RECORD_AUDIO)
                                else -> startDictationGranted()
                            }
                        },
                        voiceInputRecording = dictating,
                        voiceInputBusy = dictationBusy,
                    )
                },
                onRemove = vm::detach,
                quote = vm.quoting?.let { quoteLabel(it) },
                onDropQuote = vm::unquote,
                focus = inputFocus,
                busy = state.replying,
                onSend = {
                    if (vm.send(input, chosenMentionIds)) {
                        input = ""
                        chosenMentionIds = emptyMap()
                        sentCount++
                    }
                },
                onStop = vm::stop,
                recording = recording,
                onVoiceStart = { if (dictating || dictationBusy) false else startVoice() },
                onVoiceMove = { cancelling = it },
                onVoiceEnd = { endVoice(it) },
                drawerOpen = drawerOpen,
                onDrawer = {
                    drawerOpen = !drawerOpen
                    plusOpen = false
                    // The drawer takes the keyboard's place; typing again closes it (onFieldFocus).
                    if (drawerOpen) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                },
                onFieldFocus = {
                    if (dictating) finishDictation(cancel = true)
                    drawerOpen = false
                    plusOpen = false
                },
                drawer = {
                    StickerDrawer(
                        stickers = stickers,
                        onSend = { if (vm.sendSticker(it)) sentCount++ },
                        onAdd = { stickerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onRename = vm::renameSticker,
                        onDelete = { deletingSticker = it },
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = inputBottom)
                    .onSizeChanged { inputHeight = it.height },
            )
            }
            if (recording || dictating || dictationBusy || voiceHint != null) {
                RecordingPill(
                    backdrop = page,
                    recording = recording || dictating,
                    ms = if (dictating) dictationMs else recordedMs,
                    level = if (dictating) dictationLevel else loudness,
                    cancelling = cancelling && !dictating,
                    hint = if (dictationBusy) "正在转成文字，请稍候…" else voiceHint,
                    tapToFinish = dictating,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = inputBottom + with(density) { inputHeight.toDp() } + 10.dp),
                )
            }
        },
    ) {
        val inputTop = inputBottom + with(density) { inputHeight.toDp() }
        CompositionLocalProvider(
            LocalFaces provides faces, LocalStickers provides stickerBook,
            LocalChatType provides chatType, LocalPat provides patActions,
            LocalBubbleStudio provides remember(appSettings.bubbleStudio) { BubbleStudioCodec.decode(appSettings.bubbleStudio) },
            LocalBubbleCompanion provides state.companionId,
        ) {
            if (editingPat) {
                PatDialog(
                    aiName = state.aiName,
                    verb = state.patVerb,
                    suffix = state.patSuffix,
                    buzz = state.patBuzz,
                    onSave = { v, s, b ->
                        vm.savePat(v, s, b)
                        editingPat = false
                    },
                    onDismiss = { editingPat = false },
                )
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .fadeUnderTopBar(statusTop + TopBarHeight, bottom = inputTop),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = statusTop + TopBarHeight + 8.dp + with(density) { lifeHeaderHeight.toDp() } +
                        (if (barShown) ListeningBarSpace else 0.dp),
                    bottom = inputTop + 12.dp,
                ),
                // Bottom: a short conversation should sit just above the input, where the
                // newest message is, not float up under the title. spacedBy without an
                // alignment would put it at the top even in a reversed list.
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
            ) {
                state.streaming?.let { live ->
                    item(key = "live") { LiveBubble(live, state.aiName, vm::answerAsk) }
                }
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is ChatRow.Wallet -> ChatWalletCard(
                            row.event,
                            onClaim = { packetId ->
                                if (!walletBusy) {
                                    walletBusy = true
                                    scope.launch {
                                        try {
                                            val conversation = state.conversationId ?: error("当前会话不存在")
                                            val p = c.wallet.state.value.packets.singleOrNull {
                                                it.id == packetId && it.conversationId == conversation
                                            } ?: error("当前会话没有该红包")
                                            if (p.sender == 0L) {
                                                val live = c.db.conversations().get(conversation)
                                                    ?: error("会话已删除")
                                                require(live.isGroup && p.random && p.allowSenderClaim) {
                                                    "只允许领取自己发的拼手气群红包"
                                                }
                                            }
                                            c.wallet.claimPacket(packetId, 0L)
                                        } catch (error: Exception) {
                                            voiceHint = error.message ?: "红包领取失败"
                                        } finally { walletBusy = false }
                                    }
                                }
                            },
                            onReceiveTransfer = { id, accept ->
                                if (!walletBusy) {
                                    walletBusy = true
                                    scope.launch {
                                        try {
                                            val conversation = state.conversationId ?: error("会话不存在")
                                            require(c.wallet.state.value.transfers.any {
                                                it.id == id && it.conversationId == conversation && it.to == 0L
                                            }) { "该转账不属于当前会话" }
                                            c.wallet.decideTransfer(id, 0L, accept)
                                        } catch (error: Exception) {
                                            voiceHint = error.message ?: "操作失败"
                                        } finally { walletBusy = false }
                                    }
                                }
                            }
                        )
                        is ChatRow.Stamp -> TimeStamp(row.at)
                        ChatRow.RecapMark -> RecapMark(state.aiName) { readingRecap = true }
                        is ChatRow.Woke -> ToolNote("${state.aiName.ifBlank { "TA" }}自己想起来的", Icons.Rounded.Lightbulb)
                        is ChatRow.ToolGroup -> ToolGroupNote(row.key, row.lines) { readingTool = it }
                        is ChatRow.Message -> {
                            val m = row.message
                            val note = m.note
                            when {
                                m.role == "call" -> CallNote(m, said = m.id in callsSaid) { readingCall = m.id }
                                m.role == "tool" || m.role == "note" ->
                                    ToolNote(
                                        note.orEmpty(),
                                        if (m.role == "note") Icons.Rounded.Info else Icons.Rounded.AutoAwesome,
                                        failed = m.role == "tool" && ToolRuns.failed(note.orEmpty()),
                                        onClick = if (m.role == "tool") ({ readingTool = m.id }) else null,
                                    )
                                m.role == "request" -> RequestCard(m, state.aiName, enabled = !state.replying) { grant ->
                                    vm.answerSecret(m.id, grant)
                                }
                                // The person's answer to a request: their turn, drawn as a line on their side.
                                m.role == "user" && note != null -> ToolNote(note, Icons.Rounded.Key, mine = true)
                                m.role == Recalls.WITHDRAWN -> ToolNote(m.note?.takeIf { it.isNotBlank() } ?: Recalls.MARKER, Icons.Rounded.Info)
                                m.role == "pat" -> {
                                    val pat = Pats.decode(m.content)
                                    val name = when {
                                        !state.isGroup -> state.aiName
                                        pat?.who == Pats.AI -> state.groupSpeakers.firstOrNull { it.id == pat.targetCompanionId }?.name ?: "TA"
                                        pat?.who == Pats.FROM_AI -> state.groupSpeakers.firstOrNull { it.id == pat.sourceCompanionId }?.name ?: "TA"
                                        else -> state.aiName
                                    }
                                    PatLine(pat, name)
                                }
                                m.thoughtOnly() -> {
                                    val thought = remember(m.thought) { MessageThoughts.decode(m.thought) }
                                    if (thought != null) ThoughtLine(thought.text, thought.ms, key = m.id)
                                }
                                else -> {
                                    val quote = remember(m.quote) { MessageQuotes.decode(m.quote) }
                                    val speaker = if (state.isGroup && m.role == "assistant") {
                                        state.groupSpeakers.firstOrNull { it.id == (m.senderCompanionId ?: state.companionId) }
                                    } else null
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        if (selectionMode) ChatMessageCheck(
                                            checked = m.id in selectedMessageIds,
                                            onClick = { toggleSelection(m.id) },
                                        )
                                        Box(Modifier.weight(1f)) {
                                    MessageBubble(
                                        message = m,
                                        onMultiSelect = { beginSelection(m.id) },
                                        showFace = row.showFace,
                                        aiFace = speaker?.let { Face(it.avatar, it.avatarEmoji ?: avatarLetter(it.name, "TA")) },
                                        aiLabel = speaker?.name?.trim()?.ifEmpty { "TA" },
                                        aiLabelId = speaker?.id,
                                        patEnabled = true,
                                        patTargetCompanionId = if (state.isGroup) speaker?.id else null,
                                        editEnabled = true,
                                        retryEnabled = true,
                                        readEnabled = true,
                                        reactEnabled = true,
                                        canRetry = row.isLast && !state.replying,
                                        onRetry = { vm.retry(m.id) },
                                        onDelete = { vm.delete(m.id) },
                                        onEdit = { vm.edit(m) },
                                        onRecall = {
                                            if (playing != null && playing == MessageAudios.decode(m.audio)?.file) stopPlaying()
                                            vm.recall(m.id)
                                        },
                                        onOpenImage = onOpenImage,
                                        transcribing = m.id in state.transcribing,
                                        playingFile = playing,
                                        onPlay = { play(it, theirs = m.role == "assistant") },
                                        onRetryVoice = { vm.retryVoice(m.id) },
                                        quote = quote?.let { quoteLabel(it) },
                                        onOpenQuote = { quote?.let { jumpTo(it.id) } },
                                        onQuote = {
                                            vm.quote(m)
                                            inputFocus.requestFocus()
                                            keyboard?.show()
                                        },
                                        highlighted = m.id == flashed,
                                        onReact = { vm.react(m.id, it) },
                                        reactionAvatar = state.userAvatar,
                                        reactionLetter = avatarLetter(state.userName, "我"),
                                        reactionUserName = state.userName,
                                        reactionSpeakers = state.groupSpeakers.associate {
                                            it.id to ReactionPerson(it.name, it.avatar,
                                                it.avatarEmoji ?: avatarLetter(it.name, "TA"))
                                        },
                                        onRead = { readAloud(m.id, it) },
                                        onMention = { memberId, name ->
                                            val gap = if (input.isNotEmpty() && !input.last().isWhitespace()) " " else ""
                                            input += "$gap@$name "
                                            if (memberId != null) chosenMentionIds = chosenMentionIds + (memberId to name)
                                            inputFocus.requestFocus()
                                            keyboard?.show()
                                        },
                                    )
                                            if (selectionMode) Box(
                                                Modifier.matchParentSize().clickable {
                                                    toggleSelection(m.id)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.loaded && !state.hasApiKey && state.messages.isEmpty()) {
            NoKeyCard({ onOpenSettingsPage(SettingsPage.Model) }, Modifier.align(Alignment.Center).padding(horizontal = 28.dp))
        } else if (state.loaded && state.messages.isEmpty() && state.streaming == null) {
            GlassSurface(
                modifier = Modifier.align(Alignment.Center),
                style = palette.notice,
                shape = GlassShape.Capsule,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    if (state.isGroup) "和大家说点什么吧" else if (state.aiName.isBlank()) "说点什么吧" else "和${state.aiName}说点什么吧",
                    color = palette.contentSecondary,
                    fontSize = 15.sp,
                )
            }
        }
    }

    if (choosingForwardMode) {
        ChooseForwardMode(
            onSeparate = { mergeForward = false; choosingForwardMode = false; choosingForwardTarget = true },
            onMerge = { mergeForward = true; choosingForwardMode = false; choosingForwardTarget = true },
            onDismiss = { choosingForwardMode = false },
        )
    }
    if (choosingForwardTarget) {
        ChooseForwardTarget(
            candidates = forwardConversations,
            sourceId = state.conversationId ?: -1L,
            count = selectedMessageIds.size,
            onTarget = { target ->
                if (!batchBusy) {
                    choosingForwardTarget = false
                    batchBusy = true
                    val sourceId = state.conversationId
                    val chosen = selectedMessageIds.toSet()
                    val merged = mergeForward
                    val names = if (state.isGroup) state.groupSpeakers.associate { it.id to it.name }
                        else mapOf(state.companionId to state.aiName)
                    val title = state.aiName.ifBlank { "聊天" }
                    scope.launch {
                        try {
                            require(sourceId != null) { "会话不存在" }
                            val n = BatchMessageActions.forward(c, sourceId, target, chosen, merged, names, title)
                            voiceHint = "已转发 " + n + " 条消息"
                            leaveSelection()
                        } catch (e: Exception) {
                            voiceHint = e.message ?: "转发失败"
                            choosingForwardTarget = true
                        } finally { batchBusy = false }
                    }
                }
            },
            onDismiss = { choosingForwardTarget = false },
        )
    }
    if (askingDeleteBatch) {
        AlertDialog(
            onDismissRequest = { if (!batchBusy) askingDeleteBatch = false },
            title = { Text("删除 " + selectedMessageIds.size + " 条消息？") },
            text = { Text("仅删除当前会话的选中消息，删除后无法恢复。") },
            confirmButton = {
                TextButton(enabled = !batchBusy, onClick = {
                    batchBusy = true
                    val sourceId = state.conversationId
                    val chosen = selectedMessageIds.toSet()
                    askingDeleteBatch = false
                    scope.launch {
                        try {
                            require(sourceId != null) { "会话不存在" }
                            val n = BatchMessageActions.delete(c, sourceId, chosen)
                            voiceHint = "已删除 " + n + " 条消息"
                            leaveSelection()
                        } catch (e: Exception) {
                            voiceHint = e.message ?: "删除失败"
                        } finally { batchBusy = false }
                    }
                }) { Text("删除", color = palette.error) }
            },
            dismissButton = { TextButton(onClick = { askingDeleteBatch = false }) { Text("取消") } },
        )
    }

    if (galleryReviewOpen) {
        com.cleo.cleos.ui.settings.GalleryManagerDialog {
            galleryReviewOpen = false
        }
    }
    if (locationPickerOpen) {
        ChatLocationPicker(
            onDismiss = { locationPickerOpen = false },
            onSend = { point ->
                if (vm.sendLocation(point)) {
                    locationPickerOpen = false
                    sentCount++
                } else voiceHint = "位置没有发送成功，请重新打开聊天后再试"
            },
        )
    }
    walletAction?.let { kind ->
        ChatWalletActionDialog(
            kind = kind,
            members = if (state.isGroup) state.groupMembers else listOf(
                GroupMemberUi(state.companionId, state.aiName, state.aiAvatar, state.aiAvatarEmoji)
            ),
            isGroup = state.isGroup,
            balance = walletBook.balances[0L] ?: 0L,
            initialized = walletBook.initialized,
            onStarter = {
                if (!walletBusy && !walletBook.initialized) {
                    walletBusy = true
                    scope.launch {
                        try {
                            c.wallet.starter()
                        } catch (error: Exception) {
                            voiceHint = error.message ?: "体验币领取失败"
                        } finally {
                            walletBusy = false
                        }
                    }
                }
            },
            onDismiss = { if (!walletBusy) walletAction = null },
            onConfirm = { ids, cents, random, selfJoin ->
                val conversation = state.conversationId
                if (!walletBusy && conversation != null && ids.isNotEmpty()) {
                    walletBusy = true
                    scope.launch {
                        try {
                            // Recheck membership and conversation at commit time.
                            val live = c.db.conversations().get(conversation)
                                ?: throw IllegalStateException("会话已删除，请重新打开")
                            val currentMembers = if (live.isGroup) {
                                c.db.groupMembers().idsFor(conversation).toSet()
                            } else setOf(live.companionId)
                            require(ids.all { it in currentMembers ||
                                (it == 0L && live.isGroup && selfJoin && random) }) {
                                "红包领取对象已变化，请重新选择"
                            }
                            val receipt = if (kind == ChatMoneyKind.PACKET) {
                                c.wallet.sendPacket(0L, ids, cents, random, conversation,
                                    allowSenderClaim = selfJoin)
                            } else {
                                require(ids.size == 1) { "一次只能转账给一位 AI" }
                                c.wallet.sendTransfer(0L, ids.single(), cents, conversation)
                            }
                            // Payment already committed. Do not throw a misleading failure message
                            // if only the follow-up AI request fails.
                            walletAction = null
                            sentCount++
                            try {
                                if (ids.any { it > 0L }) {
                                    c.chat.walletTransaction(conversation, kind == ChatMoneyKind.PACKET,
                                        receipt, ids)
                                }
                            } catch (notificationError: Exception) {
                                voiceHint = "虚拟交易已经成功，但 AI 即时通知失败：" +
                                    (notificationError.message ?: "请稍后发消息提醒")
                            }
                        } catch (error: Exception) {
                            voiceHint = error.message ?: "操作未完成，未确认扣款"
                        } finally {
                            walletBusy = false
                        }
                    }
                }
            },
        )
    }
    if (creatingGroup) {
        GroupCreateDialog(
            companions = companions,
            currentId = state.companionId,
            onCreate = {
                vm.createGroup(it)
                creatingGroup = false
            },
            onDismiss = { creatingGroup = false },
        )
    }
    if (groupOptionsOpen && state.isGroup) {
        GroupOptionsDialog(
            state = state,
            onSave = { mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices, avatar ->
                vm.updateGroupOptions(mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices, avatar)
                groupOptionsOpen = false
            },
            onDismiss = { groupOptionsOpen = false },
        )
    }
    if (managingGroup) {
        GroupMembersDialog(
            companions = companions,
            initial = state.groupMembers.map { it.id }.toSet(),
            onSave = {
                vm.updateGroupMembers(it)
                managingGroup = false
            },
            onDismiss = { managingGroup = false },
        )
    }

    vm.editingMessage?.let { original ->
        MessageEditDialog(
            source = original, text = vm.editText, sending = vm.editSending, problem = vm.editProblem,
            onChange = vm::changeEdit, onSend = vm::resendEdited, onDismiss = vm::cancelEdit,
        )
    }

    if (askVoiceSetup) {
        AlertDialog(
            onDismissRequest = { askVoiceSetup = false },
            title = { Text("先接一个转文字的服务") },
            text = { Text("语音要先转成文字再给${state.aiName.ifBlank { "TA" }}看。在设置「发语音」里选一个服务、填上 Key 就能用。") },
            confirmButton = {
                TextButton(onClick = {
                    askVoiceSetup = false
                    onOpenSettingsPage(SettingsPage.VoiceInput)
                }) { Text("去设置") }
            },
            dismissButton = { TextButton(onClick = { askVoiceSetup = false }) { Text("算了") } },
        )
    }

    if (askRead) {
        val ai = state.aiName.ifBlank { "TA" }
        AlertDialog(
            onDismissRequest = { askRead = false },
            title = { Text("先给${ai}选一个声音") },
            text = { Text("朗读要用${ai}的声音。在设置「TA 的声音」里选一个服务和音色就能用。") },
            confirmButton = {
                TextButton(onClick = {
                    askRead = false
                    onOpenSettingsPage(SettingsPage.Voice)
                }) { Text("去设置") }
            },
            dismissButton = { TextButton(onClick = { askRead = false }) { Text("算了") } },
        )
    }

    askCallSetup?.let { page ->
        val ai = state.aiName.ifBlank { "TA" }
        AlertDialog(
            onDismissRequest = { askCallSetup = null },
            title = { Text(if (page == SettingsPage.VoiceInput) "先接一个转文字的服务" else "先给${ai}选一个声音") },
            text = {
                Text(
                    if (page == SettingsPage.VoiceInput) {
                        "电话里你说的话要先转成文字，${ai}才听得懂。在设置「发语音」里选一个服务、填上 Key 就能用。"
                    } else {
                        "电话里${ai}要用声音回你。在设置「TA 的声音」里选一个服务和音色就能用。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askCallSetup = null
                    onOpenSettingsPage(page)
                }) { Text("去设置") }
            },
            dismissButton = { TextButton(onClick = { askCallSetup = null }) { Text("算了") } },
        )
    }

    readingCall?.let { id ->
        state.messages.firstOrNull { it.id == id && it.role == "call" }?.let { record ->
            CallTranscript(
                record = record,
                lines = state.messages.filter { it.call == id },
                aiName = state.aiName.ifBlank { "TA" },
                userName = state.userName.ifBlank { "我" },
                onDelete = {
                    readingCall = null
                    vm.delete(id)
                },
                onDismiss = { readingCall = null },
            )
        }
    }

    readingTool?.let { id ->
        state.messages.firstOrNull { it.id == id && it.role == "tool" }?.let { row ->
            val detail = remember(id, state.messages) { ToolDetails.find(state.messages, row) }
            ToolDetailDialog(detail, onDismiss = { readingTool = null })
        }
    }

    if (readingRecap) {
        RecapDialog(
            aiName = state.aiName,
            recap = state.recap,
            onSave = {
                vm.saveRecap(it)
                readingRecap = false
            },
            onDismiss = { readingRecap = false },
        )
    }

    vm.stickerDraft?.let { draft ->
        StickerDialog(draft, vm.stickerProblem, onSave = vm::saveSticker, onDismiss = vm::dropStickerDraft)
    }
    deletingSticker?.let { s ->
        DeleteStickerDialog(
            s,
            onConfirm = {
                vm.deleteSticker(s)
                deletingSticker = null
            },
            onDismiss = { deletingSticker = null },
        )
    }
}

@Composable
private fun bubbleMaxWidth(): Dp {
    val width = LocalConfiguration.current.screenWidthDp
    // At most 70% of the screen: a bubble running nearly edge to edge reads like a page, not a
    // message. With avatars, also no wider than leaves an avatar's room on the far side.
    val most = (width * BUBBLE_SHARE).dp
    return if (LocalFaces.current != null) minOf(most, width.dp - 24.dp - AvatarSlot * 2) else most
}

/** How much of the screen's width a bubble may take. */
private const val BUBBLE_SHARE = 0.7f

/** Room inside a bubble around its words. */
private val BubblePadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

@Composable
private fun TimeStamp(at: Long) {
    val palette = LocalGlassPalette.current
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        GlassSurface(
            style = palette.bar,
            shape = GlassShape.Capsule,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(Dates.chatStamp(at), color = palette.contentSecondary, fontSize = 12.sp)
        }
    }
}

/** Where the messages sent verbatim begin; tapping it shows the recap. */
@Composable
private fun RecapMark(aiName: String, onClick: () -> Unit) {
    val palette = LocalGlassPalette.current
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        GlassSurface(
            modifier = Modifier.clickable(interactionSource = null, indication = null, onClick = onClick),
            style = palette.bar,
            shape = GlassShape.Capsule,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text("往上的，${aiName.ifBlank { "TA" }}记成了前情提要 ›", color = palette.contentSecondary, fontSize = 12.sp)
        }
    }
}

/** The recap, to read and to put right. */
@Composable
private fun RecapDialog(aiName: String, recap: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(recap.orEmpty()) }
    val name = aiName.ifBlank { "TA" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("前情提要") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "往上的聊天不再原样发给$name，${name}靠这段记着；聊得越多，它会自己往下续。哪里记得不对，可以改。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(Recap.MAX_STORED) },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    showFace: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onMultiSelect: () -> Unit = {},
    onRecall: () -> Unit = {},
    onEdit: () -> Unit = {},
    onOpenImage: (String) -> Unit,
    transcribing: Boolean = false,
    playingFile: String? = null,
    onPlay: (String) -> Unit = {},
    onRetryVoice: () -> Unit = {},
    quote: String? = null,
    onOpenQuote: () -> Unit = {},
    onQuote: () -> Unit = {},
    highlighted: Boolean = false,
    onReact: (String) -> Unit = {},
    onRead: (String) -> Unit = {},
    reactionAvatar: String? = null,
    reactionLetter: String = "我",
    reactionUserName: String = "我",
    reactionSpeakers: Map<Long, ReactionPerson> = emptyMap(),
    aiFace: Face? = null,
    aiLabel: String? = null,
    aiLabelId: Long? = null,
    onMention: (Long?, String) -> Unit = { _, _ -> },
    patEnabled: Boolean = true,
    patTargetCompanionId: Long? = null,
    editEnabled: Boolean = true,
    retryEnabled: Boolean = true,
    readEnabled: Boolean = true,
    reactEnabled: Boolean = true,
) {
    val palette = LocalGlassPalette.current
    val favoritesContainer = appContainer()
    val favoritesUiContext = androidx.compose.ui.platform.LocalContext.current
    val mine = message.role == "user"
    val glow by animateColorAsState(if (highlighted) palette.accent.copy(alpha = 0.14f) else Color.Transparent, label = "found")
    val audio = remember(message.audio) { MessageAudios.decode(message.audio) }
    var menu by remember { mutableStateOf(false) }
    var mergedOpen by remember { mutableStateOf(false) }
    var shownLocation by remember { mutableStateOf<ChatLocationPoint?>(null) }
    var bubbleTopPx by remember { mutableIntStateOf(0) }
    var bubbleBottomPx by remember { mutableIntStateOf(0) }
    var bubbleRootHeightPx by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val faces = LocalFaces.current
    val pictures = remember(message.images) { MessageImages.decode(message.images) }
    val thought = remember(message.thought) { MessageThoughts.decode(message.thought) }
    // Words and stickers, in order; the words alone are what 复制 copies.
    val book = LocalStickers.current
    val pieces = remember(message.content, book) { StickerText.split(message.content, book) }
    val locationPoint = remember(message.content) { ChatLocationPayload.decode(message.content) }
    // Old Markdown table messages immediately become proper grids after upgrading.
    val tableParts = remember(message.content) { ChatTables.parts(message.content) }
    val hasTables = tableParts.any { it is ChatTablePart.Grid }
    val words = remember(pieces) { pieces.filterIsInstance<StickerText.Piece.Words>().joinToString("\n") { it.text } }
    val reactions = remember(message.reactions) { MessageReactions.decode(message.reactions) }

    Row(
        Modifier.fillMaxWidth().background(glow, RoundedCornerShape(18.dp)),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        // Without its face, the bubble still keeps the face's room: a run lines up.
        if (faces != null && !mine) {
            if (showFace) {
                val face = aiFace ?: faces.ai
                Avatar(
                    face.file,
                    face.letter,
                    AvatarSize,
                    if (patEnabled) Modifier.pattable(ai = true, targetCompanionId = patTargetCompanionId) else Modifier,
                )
                Spacer(Modifier.width(AvatarGap))
            } else {
                Spacer(Modifier.width(AvatarSlot))
            }
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            // One menu for the whole message, so a message that is only pictures has one too.
            Box(
                Modifier.onGloballyPositioned { coordinates ->
                    bubbleTopPx = coordinates.positionInRoot().y.toInt()
                    bubbleBottomPx = bubbleTopPx + coordinates.size.height
                    bubbleRootHeightPx = coordinates.findRootCoordinates().size.height
                },
            ) {
                Column(
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!mine && !aiLabel.isNullOrBlank()) {
                        Text(
                            aiLabel,
                            color = palette.contentSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clickable { onMention(aiLabelId, aiLabel) },
                        )
                    }
                    if (thought != null && !mine) ThoughtBlock(thought.text, thought.ms, key = message.id)
                    if (pictures.isNotEmpty()) {
                        PictureGroup(pictures, onOpen = onOpenImage, onLongPress = { menu = true })
                    }
                    if (audio != null) {
                        VoiceBubble(
                            audio = audio,
                            mine = mine,
                            playing = playingFile == audio.file,
                            transcript = message.content,
                            transcribing = transcribing,
                            onClick = { onPlay(audio.file) },
                            onLongClick = { menu = true },
                            companionId = aiLabelId ?: LocalBubbleCompanion.current,
                        )
                    } else if (locationPoint != null) {
                        ChatLocationCard(
                            point = locationPoint,
                            onClick = { shownLocation = locationPoint },
                            onLongClick = { menu = true },
                        )
                    } else if (hasTables) {
                        tableParts.forEach { part ->
                            when (part) {
                                is ChatTablePart.Grid -> ChatTableCard(part.table, onLongClick = { menu = true })
                                is ChatTablePart.Words -> if (part.text.isNotBlank()) {
                                    BubbleSurface(
                                        mine = mine, companionId = aiLabelId ?: LocalBubbleCompanion.current,
                                        modifier = Modifier.widthIn(max = bubbleMaxWidth())
                                            .combinedClickable(onClick = {}, onLongClick = { menu = true }),
                                    ) {
                                        Text(part.text,
                                            color = bubbleTextColor(mine, aiLabelId ?: LocalBubbleCompanion.current),
                                            style = LocalChatType.current.body)
                                    }
                                }
                            }
                        }
                    } else if (BatchMessageActions.isMerged(message.content)) {
                        BubbleSurface(
                            mine = mine, companionId = aiLabelId ?: LocalBubbleCompanion.current,
                            modifier = Modifier.widthIn(max = bubbleMaxWidth()).combinedClickable(
                                onClick = { mergedOpen = true }, onLongClick = { menu = true },
                            ),
                        ) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text("聊天记录", fontWeight = FontWeight.SemiBold,
                                    color = bubbleTextColor(mine, aiLabelId ?: LocalBubbleCompanion.current))
                                Text(message.content.lineSequence().drop(1).firstOrNull().orEmpty(),
                                    color = bubbleTextColor(mine, aiLabelId ?: LocalBubbleCompanion.current))
                                Text("点击查看完整记录", fontSize = 12.sp, color = palette.contentSecondary)
                            }
                        }
                    } else {
                        pieces.forEach { piece ->
                            when (piece) {
                                // A sticker stands on its own, outside any bubble, as chat apps draw them.
                                is StickerText.Piece.Sticker -> StickerView(piece.sticker) { menu = true }
                                is StickerText.Piece.Words -> BubbleSurface(
                                    mine = mine, companionId = aiLabelId ?: LocalBubbleCompanion.current,
                                    modifier = Modifier
                                        .widthIn(max = bubbleMaxWidth())
                                        .combinedClickable(
                                            interactionSource = null,
                                            indication = null,
                                            onClick = {},
                                            onLongClick = { menu = true },
                                        ),
                                ) {
                                    Text(
                                        piece.text,
                                        color = bubbleTextColor(mine, aiLabelId ?: LocalBubbleCompanion.current),
                                        style = LocalChatType.current.body,
                                    )
                                }
                            }
                        }
                    }
                    // The message this one answers, under it like in WeChat; a tap finds it.
                    if (quote != null) QuoteBox(quote, onOpenQuote)
                    if (reactions.isNotEmpty()) ReactionChips(reactions, reactionAvatar, reactionLetter, reactionUserName, reactionSpeakers, onReact)
                }
                // Same behavior as 0.38.14; compact icon grid replaces only the vertical menu.
                shownLocation?.let { point ->
                    ChatLocationViewer(point) { shownLocation = null }
                }
                if (mergedOpen) {
                    AlertDialog(
                        onDismissRequest = { mergedOpen = false },
                        title = { Text("合并转发聊天记录") },
                        text = {
                            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                Text(message.content.removePrefix(BatchMessageActions.MERGED_PREFIX))
                            }
                        },
                        confirmButton = { TextButton(onClick = { mergedOpen = false }) { Text("关闭") } },
                    )
                }
                val canReactInMenu = reactEnabled && !mine && message.error == null
                val actions = buildList {
                    if (BatchMessageActions.selectable(message)) {
                        add(MessageMenuAction("多选", MessageMenuGlyph.SELECT) {
                            menu = false
                            onMultiSelect()
                        })
                    }
                    if (message.role in listOf("user", "assistant") &&
                        (words.isNotBlank() || audio != null)) {
                        add(MessageMenuAction("收藏", MessageMenuGlyph.FAVORITE) {
                            menu = false
                            scope.launch {
                                try {
                                    favoritesContainer.moments.favorite(
                                        message, if (mine) "我" else aiLabel ?: "TA")
                                    android.widget.Toast.makeText(favoritesUiContext,
                                        "已添加到收藏", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(favoritesUiContext,
                                        e.message ?: "收藏失败", android.widget.Toast.LENGTH_SHORT).show()
                                    android.util.Log.w("Favorites", "保存消息失败", e)
                                }
                            }
                        })
                    }
                    if (words.isNotBlank()) {
                        add(MessageMenuAction("复制", MessageMenuGlyph.COPY) {
                            menu = false
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", words))) }
                        })
                    }
                    if (readEnabled && !mine && message.error == null && audio == null && words.isNotBlank()) {
                        add(MessageMenuAction(
                            if (playingFile == readingMark(message.id)) "停止朗读" else "朗读",
                            MessageMenuGlyph.READ,
                        ) {
                            menu = false
                            onRead(words)
                        })
                    }
                    if (message.error == null && MessageQuotes.of(message) != null) {
                        add(MessageMenuAction("引用", MessageMenuGlyph.QUOTE) {
                            menu = false
                            onQuote()
                        })
                    }
                    if (audio != null && message.content.isBlank() && !transcribing) {
                        add(MessageMenuAction("重新转文字", MessageMenuGlyph.TRANSCRIBE) {
                            menu = false
                            onRetryVoice()
                        })
                    }
                    if (retryEnabled && !mine && canRetry) {
                        add(MessageMenuAction("重新回答", MessageMenuGlyph.RETRY) {
                            menu = false
                            onRetry()
                        })
                    }
                    if (editEnabled && MessageEdits.canEdit(message)) {
                        add(MessageMenuAction("编辑", MessageMenuGlyph.EDIT) {
                            menu = false
                            onEdit()
                        })
                    }
                    if (Recalls.canRecall(message)) {
                        add(MessageMenuAction("撤回", MessageMenuGlyph.RECALL) {
                            menu = false
                            onRecall()
                        })
                    }
                    add(MessageMenuAction("删除", MessageMenuGlyph.DELETE) {
                        menu = false
                        onDelete()
                    })
                }
                val reactionContent: (@Composable () -> Unit)? = if (canReactInMenu) {
                    {
                        ReactionPicker(on = remember(reactions) { reactions.map { it.emoji }.toSet() }) { emoji ->
                            menu = false
                            onReact(emoji)
                        }
                    }
                } else null
                val rootHeight = bubbleRootHeightPx.coerceAtLeast(1)
                val belowPx = (rootHeight - bubbleBottomPx).coerceAtLeast(0)
                val compactMenuRows = (actions.size + MESSAGE_MENU_COLUMNS - 1) / MESSAGE_MENU_COLUMNS
                val estimatedMenuHeight = with(LocalDensity.current) {
                    (18.dp + (compactMenuRows * MESSAGE_MENU_ITEM_HEIGHT_DP).dp +
                        ((compactMenuRows - 1).coerceAtLeast(0) * MESSAGE_MENU_ROW_GAP_DP).dp +
                        (if (canReactInMenu) 65.dp else 0.dp) + 7.dp).roundToPx()
                }
                val preferMenuAbove = bubbleTopPx >= estimatedMenuHeight || bubbleTopPx > belowPx
                WechatMessagePopup(
                    expanded = menu,
                    mine = mine,
                    preferAbove = preferMenuAbove,
                    actions = actions,
                    onDismiss = { menu = false },
                    reactionContent = reactionContent,
                )
            }
            val error = message.error
            if (error != null) {
                // On its own capsule: this line sits between bubbles, i.e. straight on the
                // wallpaper, and red text over a dark photo is unreadable.
                GlassSurface(
                    modifier = Modifier.padding(top = 4.dp).widthIn(max = bubbleMaxWidth()),
                    style = palette.notice,
                    shape = GlassShape.Rounded(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            error,
                            color = if (error == ChatRepository.STOPPED) palette.contentSecondary else palette.error,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // A reply that failed is asked again; a voice message that wasn't transcribed, transcribed again.
                        if (canRetry && (!mine || audio != null)) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "重试",
                                color = palette.accentContent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.combinedClickable(onClick = if (mine) onRetryVoice else onRetry),
                            )
                        }
                        if (message.content.isEmpty()) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "删除",
                                color = palette.contentSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.combinedClickable(onClick = onDelete),
                            )
                        }
                    }
                }
            }
        }
        if (faces != null && mine) {
            if (showFace) {
                Spacer(Modifier.width(AvatarGap))
                Avatar(faces.me.file, faces.me.letter, AvatarSize, Modifier.pattable(ai = false))
            } else {
                Spacer(Modifier.width(AvatarSlot))
            }
        }
    }
}

@Composable
private fun GroupCreateDialog(
    companions: List<com.cleo.cleos.data.db.CompanionEntity>,
    currentId: Long,
    onCreate: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(companions, currentId) { mutableStateOf(setOf(currentId)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建群聊") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("选择至少两个角色。进入群聊后，他们能看到彼此在群里的发言，也能参考其他真实聊天记录。", fontSize = 13.sp)
                companions.forEach { ta ->
                    val checked = ta.id in selected
                    val fixed = ta.id == currentId
                    val canToggle = !fixed && (checked || selected.size < GroupChats.MAX_MEMBERS)
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = canToggle) {
                            selected = if (checked) selected - ta.id else selected + ta.id
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            enabled = canToggle,
                            onCheckedChange = { on -> selected = if (on) selected + ta.id else selected - ta.id },
                        )
                        Spacer(Modifier.width(8.dp))
                        Avatar(ta.avatar, ta.avatarEmoji ?: avatarLetter(ta.name, "TA"), 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(ta.name.ifBlank { "TA" })
                    }
                }
                if (selected.size >= GroupChats.MAX_MEMBERS || companions.size > GroupChats.MAX_MEMBERS) Text("一次群聊最多 ${GroupChats.MAX_MEMBERS} 个角色。", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(selected) }, enabled = selected.size >= 2) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun GroupMembersDialog(
    companions: List<com.cleo.cleos.data.db.CompanionEntity>,
    initial: Set<Long>,
    onSave: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(companions, initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理群成员") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("可以随时添加或移除角色，历史消息不会被删除。群聊至少保留两个角色。", fontSize = 13.sp)
                companions.forEach { ta ->
                    val checked = ta.id in selected
                    val canToggle = checked || selected.size < GroupChats.MAX_MEMBERS
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = canToggle) {
                            selected = if (checked) selected - ta.id else selected + ta.id
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            enabled = canToggle,
                            onCheckedChange = { on -> selected = if (on) selected + ta.id else selected - ta.id },
                        )
                        Spacer(Modifier.width(8.dp))
                        Avatar(ta.avatar, ta.avatarEmoji ?: avatarLetter(ta.name, "TA"), 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(ta.name.ifBlank { "TA" })
                    }
                }
                if (selected.size >= GroupChats.MAX_MEMBERS || companions.size > GroupChats.MAX_MEMBERS) {
                    Text("一次群聊最多 ${GroupChats.MAX_MEMBERS} 个角色。", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }, enabled = selected.size >= 2 && selected != initial) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * A voice message: its length, which it plays on a tap, and underneath, what it said once it
 * has been turned into text. A longer recording draws a longer bubble, the way chat apps do.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceBubble(
    audio: MessageAudio,
    mine: Boolean,
    playing: Boolean,
    transcript: String,
    transcribing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    companionId: Long? = LocalBubbleCompanion.current,
) {
    val palette = LocalGlassPalette.current
    val ink = bubbleTextColor(mine, companionId)
    val length = 92.dp + 150.dp * (audio.ms.toFloat() / Voice.MAX_MS).coerceIn(0f, 1f)
    BubbleSurface(
        mine = mine, companionId = companionId,
        modifier = Modifier
            .widthIn(min = length, max = bubbleMaxWidth())
            .combinedClickable(interactionSource = null, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "停下" else "播放语音",
                    tint = ink,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(Voice.duration(audio.ms), color = ink, fontSize = 15.sp)
            }
            when {
                transcript.isNotBlank() -> Text(transcript, color = ink.copy(alpha = 0.85f), style = LocalChatType.current.small)
                transcribing -> Text("转文字中…", color = ink.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
    }
}

/** Above the input while recording: how long, how loud, and what letting go does; or a short hint. */
@Composable
private fun RecordingPill(
    backdrop: Backdrop,
    recording: Boolean,
    ms: Long,
    level: Float,
    cancelling: Boolean,
    hint: String?,
    tapToFinish: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier
            .liquidGlass(backdrop, palette.input, GlassShape.Capsule)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!recording) {
            Text(hint.orEmpty(), color = palette.content, fontSize = 14.sp)
            return@Row
        }
        val tint = if (cancelling) palette.error else palette.accent
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp + 12.dp * level).background(tint, CircleShape))
        }
        Spacer(Modifier.width(8.dp))
        val s = ms / 1000
        Text("${s / 60}:%02d".format(s % 60), color = palette.content, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        Text(
            if (tapToFinish) "再点「结束录音」完成转文字"
            else if (cancelling) "松开取消" else "松开发送，上滑取消",
            color = if (cancelling) palette.error else palette.contentSecondary,
            fontSize = 14.sp,
        )
    }
}

/**
 * Hold to talk: pressing starts a recording, letting go sends it, and sliding up first then
 * letting go throws it away. The pointer stays this button's until it is lifted, wherever it goes.
 */
@Composable
private fun MicButton(button: Modifier, recording: Boolean, onStart: () -> Boolean, onMove: (Boolean) -> Unit, onEnd: (Boolean) -> Unit) {
    val palette = LocalGlassPalette.current
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    Box(
        button
            .background(if (recording) palette.accent else palette.content.copy(alpha = 0.1f))
            .semantics { contentDescription = "按住说话" }
            .pointerInput(Unit) {
                val away = 72.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (!start()) return@awaitEachGesture
                    var cancel = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val up = down.position.y - change.position.y > away
                        if (up != cancel) {
                            cancel = up
                            move(cancel)
                        }
                        change.consume()
                    }
                    end(cancel)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Mic, contentDescription = null, tint = if (recording) Color.White else palette.content, modifier = Modifier.size(22.dp))
    }
}

/**
 * Pictures sent with a message, outside the bubble: a glass pane behind a photo would only
 * blur its edges. One picture keeps its shape; several become a grid of squares.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PictureGroup(pictures: List<MessageImage>, onOpen: (String) -> Unit, onLongPress: () -> Unit) {
    val c = appContainer()
    val maxWidth = minOf(bubbleMaxWidth(), 240.dp)
    fun Modifier.picture(file: String) = clip(RoundedCornerShape(18.dp)).combinedClickable(
        onClick = { onOpen(file) },
        onLongClick = onLongPress,
    )
    if (pictures.size == 1) {
        val p = pictures[0]
        val ratio = (p.width.toFloat() / p.height.coerceAtLeast(1)).coerceIn(0.6f, 1.8f)
        AsyncImage(
            model = c.images.file(p.file),
            contentDescription = "图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                // A tall photo is held to a height, so one picture doesn't fill the screen.
                .width(minOf(maxWidth, MaxPictureHeight * ratio))
                .aspectRatio(ratio)
                .picture(p.file),
        )
    } else {
        val cell = (maxWidth - 4.dp) / 2
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            pictures.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { p ->
                        AsyncImage(
                            model = c.images.file(p.file),
                            contentDescription = "图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(cell).picture(p.file),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveBubble(live: StreamingReply, aiName: String, onAnswer: (ChatRepository.Answer) -> Unit) {
    val palette = LocalGlassPalette.current
    val faces = LocalFaces.current
    // Thinking with words to show: those stand in for the typing dots.
    val thinkingAloud = live.text.isEmpty() && live.thought.isNotBlank()
    // What has come in so far, stickers drawn as they complete; one still being written isn't shown yet.
    val book = LocalStickers.current
    val pieces = remember(live.text, book) { StickerText.split(StickerText.finishedPart(live.text), book) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (live.thought.isNotBlank()) ThoughtLine(live.thought, live.thoughtMs, key = "live")
        if (live.text.isNotEmpty() || (!thinkingAloud && live.activity == null && live.asking == null)) {
            Row {
                if (faces != null) {
                    Avatar(faces.ai.file, faces.ai.letter, AvatarSize)
                    Spacer(Modifier.width(AvatarGap))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (pieces.isEmpty()) {
                        BubbleSurface(mine = false) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypingDots()
                                if (live.thinking) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("在想", color = palette.contentSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    pieces.forEach { piece ->
                        when (piece) {
                            is StickerText.Piece.Sticker -> StickerView(piece.sticker) {}
                            is StickerText.Piece.Words -> BubbleSurface(
                                mine = false, modifier = Modifier.widthIn(max = bubbleMaxWidth()),
                            ) {
                                Text(piece.text, color = bubbleTextColor(false), style = LocalChatType.current.body)
                            }
                        }
                    }
                }
            }
        }
        live.asking?.let { AskCard(it, aiName, onAnswer) }
        live.activity?.let { ToolNote(it + "…", Icons.Rounded.AutoAwesome, running = true) }
    }
}

/**
 * What the TA thought before replying, in the notes' glass: folded into one line (想了 12 秒)
 * that opens on a tap. While it is still thinking ([ms] null), the newest lines show as they
 * come in.
 */
@Composable
private fun ThoughtBlock(text: String, ms: Long?, key: Any) {
    val palette = LocalGlassPalette.current
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val thinking = ms == null
    GlassSurface(
        modifier = Modifier
            .widthIn(max = bubbleMaxWidth())
            .clickable(interactionSource = null, indication = null) { open = !open },
        style = palette.notice,
        shape = GlassShape.Rounded(14.dp),
        contentPadding = PaddingValues(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Psychology, contentDescription = null, tint = palette.accentContent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (ms == null) "在想…" else thoughtFor(ms), color = palette.contentSecondary, fontSize = 13.sp)
                Spacer(Modifier.width(2.dp))
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (open) "收起" else "展开",
                    tint = palette.contentSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (open) {
                Text(text.trim(), color = palette.contentSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            } else if (thinking) {
                // The newest lines: a window three lines high onto the bottom of the text.
                Box(Modifier.heightIn(max = 57.dp).clipToBounds()) {
                    Text(
                        text.takeLast(THOUGHT_TAIL).trimStart(),
                        color = palette.contentSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true),
                    )
                }
            }
        }
    }
}

/** The message a bubble answers: who said it and what, in a grey box under the bubble. */
@Composable
private fun QuoteBox(label: String, onOpen: () -> Unit) {
    val palette = LocalGlassPalette.current
    GlassSurface(
        modifier = Modifier
            .widthIn(max = bubbleMaxWidth())
            .clickable(interactionSource = null, indication = null, onClick = onOpen),
        style = palette.notice,
        shape = GlassShape.Rounded(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label.replace('\n', ' '),
            color = palette.contentSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A thought on a line of its own: before the tools the TA used, or while it is still thinking. */
@Composable
private fun ThoughtLine(text: String, ms: Long?, key: Any) {
    val slot = if (LocalFaces.current != null) AvatarSlot else 0.dp
    Box(Modifier.fillMaxWidth().padding(start = slot)) { ThoughtBlock(text, ms, key) }
}

/** 想了 12 秒, 想了 1 分 5 秒; under a second, it only thought for a moment. */
private fun thoughtFor(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 1 -> "想了一下"
        s < 60 -> "想了 $s 秒"
        s % 60 == 0L -> "想了 ${s / 60} 分钟"
        else -> "想了 ${s / 60} 分 ${s % 60} 秒"
    }
}

/** How much of a thought still coming in is laid out: more than the three lines shown. */
private const val THOUGHT_TAIL = 400

/** A TA's call to an outside service, waiting for the person to allow it. */
@Composable
private fun AskCard(ask: McpAsk, aiName: String, onAnswer: (ChatRepository.Answer) -> Unit) {
    val palette = LocalGlassPalette.current
    val who = aiName.ifBlank { "TA" }
    GlassSurface(
        modifier = Modifier
            .padding(start = if (LocalFaces.current != null) AvatarSlot else 0.dp)
            .widthIn(max = bubbleMaxWidth()),
        style = palette.bubble,
        shape = GlassShape.Rounded(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Extension, contentDescription = null, tint = palette.accentContent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("${who}想用${ask.service}的「${ask.tool}」", color = palette.content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(ask.arguments, color = palette.contentSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Pill("允许", accent = true, enabled = true) { onAnswer(ChatRepository.Answer.Yes) }
                Pill("以后都允许", accent = false, enabled = true) { onAnswer(ChatRepository.Answer.Always) }
                Pill("不允许", accent = false, enabled = true) { onAnswer(ChatRepository.Answer.No) }
            }
            Text("「以后都允许」只管这一个工具，在设置里能改回来。", color = palette.contentSecondary, fontSize = 12.sp)
        }
    }
}

/**
 * One line for something done on the way to a reply (记下了待办「交报告」), a notice from
 * the app, or the person's answer to a request ([mine], on their side). A capsule of its
 * own, like the error lines: it sits on the wallpaper.
 */
@Composable
private fun ToolNote(text: String, icon: ImageVector, running: Boolean = false, mine: Boolean = false, failed: Boolean = false, onClick: (() -> Unit)? = null) {
    val palette = LocalGlassPalette.current
    val slot = if (LocalFaces.current != null) AvatarSlot else 0.dp
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = if (mine) 0.dp else slot, end = if (mine) slot else 0.dp),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        GlassSurface(
            modifier = Modifier.widthIn(max = bubbleMaxWidth()),
            style = palette.notice,
            shape = GlassShape.Rounded(14.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(
                modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (failed) Icons.Rounded.Warning else icon,
                    contentDescription = null,
                    tint = if (failed) palette.error else if (running) palette.contentSecondary else palette.accentContent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(text, color = palette.contentSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                if (onClick != null) Text("  ›", color = palette.contentSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

/**
 * Tool calls in a row, as a small pill that opens: the icons of the calls, overlapping, and a
 * red warning when one didn't go through. No words until it is opened; then the lines are what
 * they were, one each, and a tap on one opens its call.
 */
@Composable
private fun ToolGroupNote(key: Any, lines: List<MessageEntity>, onOpenCall: (Long) -> Unit) {
    val palette = LocalGlassPalette.current
    val slot = if (LocalFaces.current != null) AvatarSlot else 0.dp
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val failed = lines.any { ToolRuns.failed(it.note.orEmpty()) }
    // Solid under the tint, so the icons that overlap hide each other instead of showing through.
    val base = if (palette.dark) Color(0xFF26262A) else Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = slot),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = bubbleMaxWidth())
                .clickable(interactionSource = null, indication = null) { open = !open },
            style = palette.notice,
            shape = GlassShape.Rounded(14.dp),
            contentPadding = PaddingValues(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
                    repeat(minOf(lines.size, TOOL_STACK_MAX)) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .background(base, CircleShape)
                                .background(palette.accentContent.copy(alpha = 0.2f), CircleShape)
                                .border(1.5.dp, base, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = palette.accentContent, modifier = Modifier.size(11.dp))
                        }
                    }
                }
                if (lines.size > TOOL_STACK_MAX) {
                    Spacer(Modifier.width(4.dp))
                    Text("+${lines.size - TOOL_STACK_MAX}", color = palette.contentSecondary, fontSize = 12.sp)
                }
                if (failed) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Warning, contentDescription = "有没成的", tint = palette.error, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(2.dp))
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "用了 ${lines.size} 个工具，" + if (open) "收起" else "展开",
                    tint = palette.contentSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (open) {
            lines.forEach { line ->
                val note = line.note.orEmpty()
                ToolNote(note, Icons.Rounded.AutoAwesome, failed = ToolRuns.failed(note), onClick = { onOpenCall(line.id) })
            }
        }
    }
}

/** How many icons a folded run of tool calls shows; the rest are a number. */
private const val TOOL_STACK_MAX = 3

/** A tool call opened from its line in the chat: what was asked of the tool, and what it answered, to read and copy. */
@Composable
private fun ToolDetailDialog(detail: ToolDetail, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调用工具: ${detail.name}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (detail.arguments.isNotBlank()) ToolDetailBlock("参数", detail.arguments)
                ToolDetailBlock("结果", detail.result.ifBlank { "（空）" })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关上") } },
    )
}

/** One labelled piece of text with a copy button; long ones are cut on screen, and copied whole. */
@Composable
private fun ToolDetailBlock(label: String, text: String) {
    val palette = LocalGlassPalette.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shown = if (text.length > TOOL_DETAIL_SHOWN) text.take(TOOL_DETAIL_SHOWN) + "\n…（太长，这里只显示前 $TOOL_DETAIL_SHOWN 字，复制的是全文）" else text
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = palette.accentContent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        TextButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text))) } }) { Text("复制") }
    }
    SelectionContainer {
        Text(
            shown,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.contentSecondary.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .padding(10.dp),
        )
    }
}

private const val TOOL_DETAIL_SHOWN = 20_000

/**
 * A phone call in the chat: one line where it began, saying how long it went on, in place of
 * everything said in it. Tapped, what was said ([said]: anything was), and a way to delete it.
 */
@Composable
private fun CallNote(message: MessageEntity, said: Boolean, onOpen: () -> Unit) {
    val palette = LocalGlassPalette.current
    val record = remember(message.content) { CallRecords.decode(message.content) } ?: return
    val talked = record.talkedMs
    val text = when {
        record.endedAt == null -> "通话中"
        talked == null -> "已取消"
        else -> "语音通话 ${CallRecords.clock(talked)}"
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        GlassSurface(
            modifier = Modifier.clickable(interactionSource = null, indication = null, onClick = onOpen),
            style = palette.notice,
            shape = GlassShape.Capsule,
            contentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Call, contentDescription = null, tint = palette.accentContent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(text, color = palette.contentSecondary, fontSize = 13.sp)
                if (talked != null && said) {
                    Spacer(Modifier.width(6.dp))
                    Text("看说了什么", color = palette.accentContent, fontSize = 12.sp)
                }
            }
        }
    }
}

/** What was said in a call, in order, with a way to delete the call and all of it. */
@Composable
private fun CallTranscript(
    record: MessageEntity,
    lines: List<MessageEntity>,
    aiName: String,
    userName: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    var deleting by remember { mutableStateOf(false) }
    val talked = remember(record.content) { CallRecords.decode(record.content)?.talkedMs }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deleting) "删除这通电话？" else "电话里说的") },
        text = {
            if (deleting) {
                Text("电话里说的话会一起删掉，${aiName}也就不记得这通电话了。")
            } else {
                Column(
                    Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        Dates.chatStamp(record.createdAt) + (talked?.let { " · 通话 ${CallRecords.clock(it)}" } ?: ""),
                        color = palette.contentSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    var any = false
                    for (m in lines) {
                        val note = m.note
                        when {
                            (m.role == "user" || m.role == "assistant") && m.content.isNotBlank() -> {
                                any = true
                                Text(
                                    if (m.role == "user") userName else aiName,
                                    color = palette.accentContent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(m.content, fontSize = 15.sp, lineHeight = 22.sp)
                                Spacer(Modifier.height(10.dp))
                            }
                            m.role == "tool" && !note.isNullOrBlank() -> {
                                Text("（$note）", color = palette.contentSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                    if (!any) Text("这通电话里没说上话。")
                }
            }
        },
        confirmButton = {
            if (deleting) {
                TextButton(onClick = onDelete) { Text("删除", color = palette.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("关上") }
            }
        },
        dismissButton = {
            if (deleting) {
                TextButton(onClick = { deleting = false }) { Text("算了") }
            } else {
                TextButton(onClick = { deleting = true }) { Text("删除") }
            }
        },
    )
}

/**
 * The model asking to see a little secret. Only the person ever sees this card, so it can
 * show which entry (date and title) even though the model was never told the title.
 */
@Composable
private fun RequestCard(message: MessageEntity, aiName: String, enabled: Boolean, onAnswer: (Boolean) -> Unit) {
    val palette = LocalGlassPalette.current
    val request = remember(message.content) { SecretRequests.decode(message.content) } ?: return
    val who = aiName.ifBlank { "TA" }
    GlassSurface(
        modifier = Modifier
            .padding(start = if (LocalFaces.current != null) AvatarSlot else 0.dp)
            .widthIn(max = bubbleMaxWidth()),
        style = palette.bubble,
        shape = GlassShape.Rounded(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = palette.accentContent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("${who}想看你的小秘密", color = palette.content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                Dates.monthDay(LocalDate.ofEpochDay(request.day)) + " · " + request.title.ifBlank { "没有标题" },
                color = palette.contentSecondary,
                fontSize = 13.sp,
            )
            if (request.reason.isNotBlank()) {
                Text("「${request.reason}」", color = palette.content, fontSize = 15.sp, lineHeight = 21.sp)
            }
            when (request.status) {
                SecretRequest.PENDING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Pill("给${who}看", accent = true, enabled = enabled) { onAnswer(true) }
                        Pill("不给", accent = false, enabled = enabled) { onAnswer(false) }
                    }
                    Text("给看只是这一次，日记还是锁着的。", color = palette.contentSecondary, fontSize = 12.sp)
                }
                SecretRequest.GRANTED -> Text("给${who}看了", color = palette.contentSecondary, fontSize = 13.sp)
                SecretRequest.DECLINED -> Text("没给${who}看", color = palette.contentSecondary, fontSize = 13.sp)
                else -> Text("这个小秘密已经不在了", color = palette.contentSecondary, fontSize = 13.sp)
            }
        }
    }
}

/** A plain pill, not glass: it sits on a glass card, where glass would look like a hole. */
@Composable
private fun Pill(text: String, accent: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalGlassPalette.current
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .background(if (accent) palette.accent else palette.content.copy(alpha = 0.08f), CircleShape)
            .clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (accent) Color.White else palette.content, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TypingDots() {
    val palette = LocalGlassPalette.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(520, delayMillis = i * 160), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(7.dp).alpha(a).background(palette.content, CircleShape))
        }
    }
}

/**
 * One piece of glass holds everything: the picture button, the text, and send. While a
 * reply is being written, an empty box offers stop instead; with something typed it is
 * still send, since the person can go on talking. The message being quoted and pictures
 * waiting to go sit above the text, inside the same glass. A round send button beside the
 * field would be a second glass pane, rendered offscreen on every frame, for one button.
 */
@Composable
private fun ChatInputBar(
    backdrop: Backdrop,
    type: ChatType,
    text: String,
    onTextChange: (String) -> Unit,
    isGroup: Boolean = false,
    groupMembers: List<GroupMemberUi> = emptyList(),
    onMentionChosen: (Long, String) -> Unit = { _, _ -> },
    attachments: List<MessageImage>,
    attaching: Boolean,
    plusOpen: Boolean = false,
    onPlus: () -> Unit = {},
    plusPanel: @Composable () -> Unit = {},
    onRemove: (MessageImage) -> Unit,
    quote: String?,
    onDropQuote: () -> Unit,
    focus: FocusRequester,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    recording: Boolean = false,
    onVoiceStart: () -> Boolean = { false },
    onVoiceMove: (Boolean) -> Unit = {},
    onVoiceEnd: (Boolean) -> Unit = {},
    drawerOpen: Boolean = false,
    onDrawer: () -> Unit = {},
    onFieldFocus: () -> Unit = {},
    drawer: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val c = appContainer()
    val canSend = text.isNotBlank() || attachments.isNotEmpty()

    val mentionStart = if (isGroup) maxOf(text.lastIndexOf('@'), text.lastIndexOf('＠')) else -1
    val mentionTail = if (mentionStart >= 0) text.substring(mentionStart + 1) else ""
    val mentionActive = mentionStart >= 0 && mentionTail.none { ch ->
        ch.isWhitespace() || ch in "，。！？,.!?；;：:"
    }
    val mentionQuery = if (mentionActive) mentionTail.trim() else ""
    val mentionMembers = if (mentionActive) {
        groupMembers.filter { member ->
            mentionQuery.isEmpty() || member.name.contains(mentionQuery, ignoreCase = true)
        }
    } else emptyList()

    fun insertMention(name: String, memberId: Long? = null) {
        if (mentionStart < 0) return
        onTextChange(text.substring(0, mentionStart) + "@$name ")
        if (memberId != null) onMentionChosen(memberId, name)
        focus.requestFocus()
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .heightIn(min = BarHeight)
            .liquidGlass(backdrop, palette.input, GlassShape.Rounded(BarHeight / 2)),
    ) {
        // Both panels share the existing input measurement and keyboard avoidance path.
        if (plusOpen) plusPanel()
        if (drawerOpen) drawer()
        if (quote != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp)
                    .background(palette.content.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    quote,
                    color = palette.contentSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDropQuote),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "不引用了", tint = palette.contentSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (attachments.isNotEmpty() || attaching) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { img -> AttachmentThumb(c.images.file(img.file)) { onRemove(img) } }
                if (attaching) {
                    Box(Modifier.size(64.dp).background(palette.content.copy(alpha = 0.08f), RoundedCornerShape(14.dp)))
                }
            }
        }
        if (isGroup && mentionActive) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mentionQuery.isEmpty() || "所有人".contains(mentionQuery, ignoreCase = true)) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.content.copy(alpha = 0.08f))
                            .clickable { insertMention("所有人") }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("@所有人", color = palette.content, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                mentionMembers.forEach { member ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.content.copy(alpha = 0.08f))
                            .clickable { insertMention(member.name.ifBlank { "TA" }, member.id) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Avatar(member.avatar, member.avatarEmoji ?: avatarLetter(member.name, "TA"), 24.dp)
                        Text("@${member.name.ifBlank { "TA" }}", color = palette.content, fontSize = 13.sp)
                    }
                }
                if (mentionMembers.isEmpty() && !("所有人".contains(mentionQuery, ignoreCase = true))) {
                    Text("没有匹配的群成员", color = palette.contentSecondary, fontSize = 12.sp)
                }
            }
        }
        // Each button sits in the middle of a square as tall as the bar: on one line it is
        // centred, concentric with the capsule's round end; as the text grows to more lines
        // the squares stay at the bottom. (Padding the buttons by hand left them 4dp from
        // the top and 8dp from the bottom: the text row was 48dp in a 50dp bar.)
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(BarHeight), contentAlignment = Alignment.Center) {
                // Recording remains a hold-to-talk gesture with the existing
                // slide-up-to-cancel behaviour. This replaces the duplicate photo shortcut.
                MicButton(
                    Modifier.size(38.dp).clip(CircleShape),
                    recording,
                    onVoiceStart,
                    onVoiceMove,
                    onVoiceEnd,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = BarHeight)
                    .padding(end = 4.dp, top = 13.dp, bottom = 13.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text("说点什么…", color = palette.contentSecondary, style = type.body)
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = type.body.copy(color = palette.content),
                    cursorBrush = SolidColor(palette.accentContent),
                    maxLines = 6,
                    // The placeholder above is drawn beside the field, so a screen reader
                    // would otherwise announce an unnamed text box.
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged { if (it.isFocused) onFieldFocus() }
                        .semantics { contentDescription = "输入消息" },
                )
            }
            if (isGroup) {
                Box(Modifier.size(width = 36.dp, height = BarHeight), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable {
                                val gap = if (text.isNotEmpty() && !text.last().isWhitespace()) " " else ""
                                onTextChange(text + gap + "@")
                                focus.requestFocus()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("@", color = palette.accentContent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Narrower than the squares either side: the text keeps as much of the bar as it can.
            Box(Modifier.size(width = 40.dp, height = BarHeight), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (drawerOpen) palette.content.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable(onClick = onDrawer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.EmojiEmotions,
                        contentDescription = if (drawerOpen) "收起表情包" else "表情包",
                        tint = if (drawerOpen) palette.accentContent else palette.contentSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Box(Modifier.size(width = 40.dp, height = BarHeight), contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).clip(CircleShape)
                    .background(if (plusOpen) palette.content.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(onClick = onPlus), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add,
                        contentDescription = if (plusOpen) "收起更多功能" else "更多功能",
                        tint = if (plusOpen) palette.accentContent else palette.contentSecondary,
                        modifier = Modifier.size(25.dp))
                }
            }
            if (busy || canSend) {
                Box(Modifier.size(BarHeight), contentAlignment = Alignment.Center) {
                    // Plain fills inside the glass, like the chips on a card: glass in glass reads as a hole.
                val button = Modifier.size(38.dp).clip(CircleShape)
                if (busy && !canSend) {
                    Box(
                        button.background(palette.content.copy(alpha = 0.1f)).clickable(onClick = onStop),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "停止", tint = palette.content, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Box(
                        button
                            .background(palette.accent)
                            .clickable(onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "发送", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun AttachmentThumb(file: File, onRemove: () -> Unit) {
    Box(Modifier.size(64.dp)) {
        AsyncImage(
            model = file,
            contentDescription = "要发的图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(22.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "去掉这张", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun NoKeyCard(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    GlassSurface(modifier = modifier, shape = GlassShape.Rounded(28.dp), contentPadding = PaddingValues(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有接上模型", color = palette.content, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(8.dp))
            Text(
                "在设置里填一个 API 地址和 Key（DeepSeek、OpenAI 这类兼容接口都可以），就能开始聊了。",
                color = palette.contentSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(16.dp))
            GlassButton(
                onClick = onOpenSettings,
                backdrop = com.cleo.cleos.glass.LocalWallpaperBackdrop.current,
                style = palette.accentSurface,
                contentColor = Color.White,
            ) {
                Text("去设置", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
