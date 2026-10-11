#!/usr/bin/env python3
"""Assert the two mic gestures remain distinct and dictation is actually wired."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
dir=root/"app/src/main/java/com/cleo/cleos/ui/chat"
chat=(dir/"ChatScreen.kt").read_text(encoding="utf-8")
plus=(dir/"ChatWalletPanel.kt").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle.kts").read_text(encoding="utf-8")
for file, dest in [
    ("VoiceDictationDraft_03830.kt", dir/"VoiceDictationDraft.kt"),
    ("VoiceDictationDraftTest_03830.kt",root/"app/src/test/java/com/cleo/cleos/ui/chat/VoiceDictationDraftTest.kt"),
]:
    assert (here/file).read_bytes()==dest.read_bytes(), dest
for marker in [
    'val dictationRecorder = remember { VoiceRecorder(c.images.dir) }',
    'var dictating by remember { mutableStateOf(false) }',
    'fun startDictationGranted(): Boolean',
    'fun finishDictation(cancel: Boolean)',
    'requestDictationMic.launch(Manifest.permission.RECORD_AUDIO)',
    'dictationRecorder.start()',
    'val clip = dictationRecorder.stop()',
    'c.transcriber.transcribe(',
    'c.images.delete(listOf(clip.file))',
    'input = VoiceDictationDraft.append(input, recognized)',
    'vm.state.value.conversationId == originalConversationId',
    'voiceInputRecording = dictating',
    'voiceInputBusy = dictationBusy',
    'dictationBusy = false',
    'tapToFinish = dictating',
    'if (dictating || dictationBusy) false else startVoice()',
    'if (plusOpen && dictating) finishDictation(cancel = true)',
    'onVoiceEnd = { endVoice(it) }',
    'onVoiceMove = { cancelling = it }',
    'endVoice(cancel = true)',
    'dictationRecorder.cancel()',
    '松开发送，上滑取消',
]:
    assert marker in chat, marker
assert 'onVoiceInput = {' in chat
for marker in [
    'onVoiceInput: () -> Unit',
    'voiceInputRecording: Boolean',
    'voiceInputBusy: Boolean',
    'voiceInputRecording -> "结束录音"',
    'voiceInputBusy -> "转文字中"',
    'else -> "语音输入"',
    '!voiceInputBusy,',
    'onVoiceInput,',
    '点击结束并转文字',
    'Action("语音通话", Icons.Rounded.Call, true, onCall)',
    'Action("相册", Icons.Rounded.PhotoLibrary, true, onAlbum)',
    'Action("拍摄", Icons.Rounded.CameraAlt, true, onCamera)',
    'Action("红包", Icons.Rounded.CardGiftcard, true, onRedPacket)',
    'Action("转账", Icons.Rounded.CurrencyExchange, true, onTransfer)',
]:
    assert marker in plus,marker
assert 'Action("语音输入", Icons.Rounded.Mic, false, {})' not in plus
assert 'versionName = "0.38.30"' in gradle and 'versionCode = 62096' in gradle
print("PASS: + tap-to-dictate and cancel behavior; existing hold mic, payments, album, camera, call preserved")
