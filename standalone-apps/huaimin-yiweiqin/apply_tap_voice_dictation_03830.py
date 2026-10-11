#!/usr/bin/env python3
"""0.38.30: + voice input is tap-to-start/tap-to-stop speech-to-text.
Keep the composer microphone unchanged: hold-to-talk sends a voice message.
"""
from pathlib import Path
from shutil import copyfile
import sys
root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
chat_dir = root / "app/src/main/java/com/cleo/cleos/ui/chat"
chat_file = chat_dir / "ChatScreen.kt"
plus_file = chat_dir / "ChatWalletPanel.kt"

copyfile(here/"VoiceDictationDraft_03830.kt",chat_dir/"VoiceDictationDraft.kt")
test_dir=root/"app/src/test/java/com/cleo/cleos/ui/chat"
test_dir.mkdir(parents=True,exist_ok=True)
copyfile(here/"VoiceDictationDraftTest_03830.kt",test_dir/"VoiceDictationDraftTest.kt")

def modify(s,old,new,what):
    count=s.count(old)
    if count!=1:
        raise RuntimeError(f"{what}: expected 1 match, found {count}: {old[:110]!r}")
    return s.replace(old,new,1)

plus=plus_file.read_text(encoding="utf-8")
plus=modify(plus,
'''    onRedPacket: () -> Unit,
    onTransfer: () -> Unit,
) {''',
'''    onRedPacket: () -> Unit,
    onTransfer: () -> Unit,
    onVoiceInput: () -> Unit,
    voiceInputRecording: Boolean,
    voiceInputBusy: Boolean,
) {''',"plus panel callbacks")
plus=modify(plus,
'''        Action("语音输入", Icons.Rounded.Mic, false, {}),''',
'''        Action(
            when {
                voiceInputRecording -> "结束录音"
                voiceInputBusy -> "转文字中"
                else -> "语音输入"
            },
            Icons.Rounded.Mic,
            !voiceInputBusy,
            onVoiceInput,
        ),''',"enable real plus voice input")
plus=modify(plus,
'''                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (action.enabled) 0.9f else 0.4f)''',
'''                        color = when {
                            action.label == "结束录音" -> MaterialTheme.colorScheme.primaryContainer
                            action.enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        }''',"recording indicator tile")
plus=modify(plus,
'''                    if (!action.enabled) Text("待接入", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)''',
'''                    if (!action.enabled && !voiceInputBusy) Text("待接入", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (voiceInputRecording && action.label == "结束录音")
                        Text("点击结束并转文字", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)''',"recording state hint")
plus_file.write_text(plus,encoding="utf-8")

chat=chat_file.read_text(encoding="utf-8")
chat=modify(chat,
'''    val recorder = remember { VoiceRecorder(c.images.dir) }
    var recording by remember { mutableStateOf(false) }''',
'''    val recorder = remember { VoiceRecorder(c.images.dir) }
    // Separate mic lifetime: composer keeps hold-to-send, + is tap-to-dictate.
    val dictationRecorder = remember { VoiceRecorder(c.images.dir) }
    var dictating by remember { mutableStateOf(false) }
    var dictationBusy by remember { mutableStateOf(false) }
    var dictationMs by remember { mutableLongStateOf(0L) }
    var dictationLevel by remember { mutableFloatStateOf(0f) }
    var recording by remember { mutableStateOf(false) }''',"separate dictation state")
chat=modify(chat,
'''            recorder.cancel()
            // Off to another screen:''',
'''            recorder.cancel()
            dictationRecorder.cancel()
            // Off to another screen:''',"release voice recorder on dispose")
chat=modify(chat,
'''            stopPlaying()
            endVoice(cancel = true)
        }
    }
    val palette''',
'''            stopPlaying()
            endVoice(cancel = true)
            dictationRecorder.cancel()
            dictating = false
        }
    }
    val palette''',"cancel dictation on leaving screen")
chat=modify(chat,
'''    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // The message a quote''',
'''    val inputFocus = remember { FocusRequester() }
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
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                    c.images.delete(listOf(clip.file))
                }
                dictationBusy = false
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

    // The message a quote''',"tap dictation controls")
chat=modify(chat,
'''    BackHandler(enabled = (drawerOpen || plusOpen) && pageShown) { drawerOpen = false; plusOpen = false }''',
'''    BackHandler(enabled = (drawerOpen || plusOpen) && pageShown) {
        drawerOpen = false
        plusOpen = false
        if (dictating) {
            dictationRecorder.cancel()
            dictating = false
        }
    }''',"cancel active dictation when closing with back")
chat=modify(chat,
'''                onPlus = {
                    plusOpen = !plusOpen
                    drawerOpen = false
                    if (plusOpen) {''',
'''                onPlus = {
                    if (plusOpen && dictating) finishDictation(cancel = true)
                    plusOpen = !plusOpen
                    drawerOpen = false
                    if (plusOpen) {''',"cancel on plus close")
chat=modify(chat,
'''                        onTransfer = { plusOpen = false; walletAction = ChatMoneyKind.TRANSFER },
                    )''',
'''                        onTransfer = { plusOpen = false; walletAction = ChatMoneyKind.TRANSFER },
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
                    )''',"bind + voice input")
chat=modify(chat,
'''                onVoiceStart = { startVoice() },
                onVoiceMove = { cancelling = it },''',
'''                onVoiceStart = { if (dictating || dictationBusy) false else startVoice() },
                onVoiceMove = { cancelling = it },''',"keep and isolate hold mic")
chat=modify(chat,
'''                onFieldFocus = { drawerOpen = false; plusOpen = false },''',
'''                onFieldFocus = {
                    if (dictating) finishDictation(cancel = true)
                    drawerOpen = false
                    plusOpen = false
                },''',"typing cancels unsent dictation")
chat=modify(chat,
'''            if (recording || voiceHint != null) {
                RecordingPill(
                    backdrop = page,
                    recording = recording,
                    ms = recordedMs,
                    level = loudness,
                    cancelling = cancelling,
                    hint = voiceHint,''',
'''            if (recording || dictating || dictationBusy || voiceHint != null) {
                RecordingPill(
                    backdrop = page,
                    recording = recording || dictating,
                    ms = if (dictating) dictationMs else recordedMs,
                    level = if (dictating) dictationLevel else loudness,
                    cancelling = cancelling && !dictating,
                    hint = if (dictationBusy) "正在转成文字，请稍候…" else voiceHint,
                    tapToFinish = dictating,''',"recording status for tapped dictation")
chat=modify(chat,
'''    hint: String?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current''',
'''    hint: String?,
    tapToFinish: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current''',"status hint parameter")
chat=modify(chat,
'''        Text(if (cancelling) "松开取消" else "松开发送，上滑取消", color = if (cancelling) palette.error else palette.contentSecondary, fontSize = 14.sp)''',
'''        Text(
            if (tapToFinish) "再点语音输入结束并转文字"
            else if (cancelling) "松开取消" else "松开发送，上滑取消",
            color = if (cancelling) palette.error else palette.contentSecondary,
            fontSize = 14.sp,
        )''',"clear tapped versus held directions")
chat_file.write_text(chat,encoding="utf-8")

gradle=root/"app/build.gradle.kts"
s=gradle.read_text(encoding="utf-8")
for old,new in [
    ('versionName = "0.38.29"','versionName = "0.38.30"'),
    ('versionCode = 62095','versionCode = 62096'),
]:
    s=modify(s,old,new,"version")
gradle.write_text(s,encoding="utf-8")
print("0.38.30/62096: + one-tap voice dictation to unsent text, hold-to-send voice preserved")
