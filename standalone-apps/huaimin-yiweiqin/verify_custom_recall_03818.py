#!/usr/bin/env python3
"""0.38.18 regression for settings routing, persistence and AI-safe withdrawal."""
from pathlib import Path
import sys
root = Path(sys.argv[1]).resolve()
src = root / "app/src/main/java/com/cleo/cleos"
read = lambda rel: (src / rel).read_text(encoding="utf-8")
repo = read("data/SettingsRepository.kt")
vm = read("ui/settings/SettingsViewModel.kt")
screen = read("ui/settings/SettingsScreen.kt")
pages = read("ui/settings/SettingsPages.kt")
app = read("ui/settings/AppPages.kt")
recalls = read("data/Recalls.kt")
messages = read("data/RecallDisplayText.kt")
chat_repo = read("ai/ChatRepository.kt")
chat = read("ui/chat/ChatScreen.kt")
ui = read("ui/settings/RecallMessageSettings.kt")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
assert 'versionName = "0.38.18"' in gradle and "versionCode = 62084" in gradle
for needle in ('recallDisplayText: String = RecallDisplayText.DEFAULT', '"recall_display_text" to "string"',
               'stringPreferencesKey("recall_display_text")',
               'recallDisplayText = RecallDisplayText.clean(this[Keys.recallDisplayText]',
               'prefs[Keys.recallDisplayText] = RecallDisplayText.clean(next.recallDisplayText)'):
    assert needle in repo, "settings persistence or backup: " + needle
assert 'fun setRecallDisplayText(text: String)' in vm
assert 'it.copy(recallDisplayText = com.cleo.cleos.data.RecallDisplayText.clean(text))' in vm
assert 'Recall("自定义撤回消息")' in pages
assert 'SettingsPage.Chat -> ChatPage(vm, onOpenRecall' in screen
assert 'SettingsPage.Recall -> RecallMessageSettingsPage(vm)' in screen
assert 'BackHandler(enabled = page == SettingsPage.Recall) { page = SettingsPage.Chat }' in screen
assert 'internal fun ChatPage(vm: SettingsViewModel, onOpenRecall: () -> Unit)' in app
assert '"自定义撤回消息"' in app
for needle in ('"预览"', '"选择撤回消息"', 'RecallDisplayText.presets.forEach',
               'vm.setRecallDisplayText(option)', 'OutlinedTextField(', 'vm.setRecallDisplayText(draft)',
               'RadioButton(', 'val current = RecallDisplayText.clean(settings.recallDisplayText)'):
    assert needle in ui, "recall settings UI: " + needle
assert 'MAX_LENGTH = 80' in messages
assert 'DEFAULT = "你撤回了一条消息"' in messages
assert 'fun withdrawn(message: MessageEntity, displayText: String = MARKER)' in recalls
assert 'note = RecallDisplayText.clean(displayText)' in recalls
assert 'message.copy(role = WITHDRAWN, content = "", note =' in recalls
assert 'fun event(message: MessageEntity, at: Long)' in recalls
assert 'content = json.encodeToString(RecallRecord(' in recalls
assert 'val recallNotice = settings.current().recallDisplayText' in chat_repo
assert 'Recalls.withdrawn(current, recallNotice)' in chat_repo
assert 'Recalls.event(current, at)' in chat_repo
assert 'Recalls.redactQuote(' in chat_repo
assert 'ToolNote(m.note?.takeIf { it.isNotBlank() } ?: Recalls.MARKER' in chat
print("PASS 0.38.18: persistent settings, chat route, displayed recall and AI event preserved")
