#!/usr/bin/env python3
"""0.38.18: Chat settings -> custom recall preview/presets, persisted in existing DataStore.
The real recall transaction and AI event format remain unchanged.
"""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
pending = {}

def replace(rel, old, new, label):
    file = root / rel
    s = pending.get(rel, file.read_text(encoding="utf-8"))
    n = s.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: expected 1 anchor got {n} in {rel}: {old[:75]!r}")
    pending[rel] = s.replace(old, new, 1)

settings = "app/src/main/java/com/cleo/cleos/data/SettingsRepository.kt"
replace(settings,
    '    val chatTextSize: Int = 15,\n',
    '    val chatTextSize: Int = 15,\n    /** Visible text stored on future recalled messages; historical notices are unchanged. */\n    val recallDisplayText: String = RecallDisplayText.DEFAULT,\n',
    "app settings field")
replace(settings,
    '        "chat_text_size" to "int",\n',
    '        "chat_text_size" to "int",\n        "recall_display_text" to "string",\n',
    "portable backup type")
replace(settings,
    '        val chatTextSize = intPreferencesKey("chat_text_size")\n',
    '        val chatTextSize = intPreferencesKey("chat_text_size")\n        val recallDisplayText = stringPreferencesKey("recall_display_text")\n',
    "preferences key")
replace(settings,
    '            chatTextSize = this[Keys.chatTextSize] ?: d.chatTextSize,\n',
    '            chatTextSize = this[Keys.chatTextSize] ?: d.chatTextSize,\n            recallDisplayText = RecallDisplayText.clean(this[Keys.recallDisplayText] ?: d.recallDisplayText),\n',
    "settings decoding")
replace(settings,
    '            prefs[Keys.chatTextSize] = next.chatTextSize\n',
    '            prefs[Keys.chatTextSize] = next.chatTextSize\n            prefs[Keys.recallDisplayText] = RecallDisplayText.clean(next.recallDisplayText)\n',
    "settings write")

vm = "app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt"
replace(vm,
    '    fun setChatAvatars(on: Boolean) {\n',
    '''    fun setRecallDisplayText(text: String) {
        viewModelScope.launch {
            c.settings.update {
                it.copy(recallDisplayText = com.cleo.cleos.data.RecallDisplayText.clean(text))
            }
        }
    }

    fun setChatAvatars(on: Boolean) {
''',
    "settings view model setter")

page_enum = "app/src/main/java/com/cleo/cleos/ui/settings/SettingsPages.kt"
replace(page_enum, '    Chat("聊天"),\n', '    Chat("聊天"),\n    Recall("自定义撤回消息"),\n', "nested recall page enum")

screen = "app/src/main/java/com/cleo/cleos/ui/settings/SettingsScreen.kt"
# Added *after* existing BackHandler(s), so the nested subpage takes precedence
# without changing earlier keyboard/upgrade handler logic.
replace(screen,
    '    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()\n',
    '    BackHandler(enabled = page == SettingsPage.Recall) { page = SettingsPage.Chat }\n    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()\n',
    "nested hardware back")
replace(screen,
    'GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", { if (page != null) page = null else onBack() }, backdrop)',
    'GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", { if (page == SettingsPage.Recall) page = SettingsPage.Chat else if (page != null) page = null else onBack() }, backdrop)',
    "nested top back")
replace(screen,
    '                    SettingsPage.Chat -> ChatPage(vm)\n',
    '''                    SettingsPage.Chat -> ChatPage(vm, onOpenRecall = { page = SettingsPage.Recall })
                    SettingsPage.Recall -> RecallMessageSettingsPage(vm)
''',
    "nested UI route")

app = "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt"
replace(app,
    'internal fun ChatPage(vm: SettingsViewModel) {\n',
    'internal fun ChatPage(vm: SettingsViewModel, onOpenRecall: () -> Unit) {\n',
    "chat page receives nav")
replace(app,
    '    // Moved here from the home page, where they sat among the TA\'s things.\n',
    '''    // Single / group recall wording is shared; open the settings subpage.
    Section("互动功能") {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = null, indication = null, onClick = onOpenRecall)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("自定义撤回消息", color = palette.content, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("预设提示语 · 自己写一句 · 单聊和群聊共用", color = palette.contentSecondary, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "打开自定义撤回消息", tint = palette.contentSecondary)
        }
    }

    // Moved here from the home page, where they sat among the TA's things.
''',
    "entry nested in chat page")

recall = "app/src/main/java/com/cleo/cleos/data/Recalls.kt"
replace(recall,
    '    fun withdrawn(message: MessageEntity): MessageEntity {\n',
    '    fun withdrawn(message: MessageEntity, displayText: String = MARKER): MessageEntity {\n',
    "recall takes display notice")
replace(recall,
    'return message.copy(role = WITHDRAWN, content = "", note = MARKER, error = null,',
    'return message.copy(role = WITHDRAWN, content = "", note = RecallDisplayText.clean(displayText), error = null,',
    "display notice stored per withdrawal")

repository = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
replace(repository,
    '                try {\n                    recaps.withoutFolding(conversationId) {\n                        db.withTransaction {\n',
    '''                try {
                    // Snapshot the chosen wording before opening the recall transaction.
                    val recallNotice = settings.current().recallDisplayText
                    recaps.withoutFolding(conversationId) {
                        db.withTransaction {
''',
    "recall action snapshot",)
replace(repository,
    'db.messages().updateForRecall(Recalls.withdrawn(current))',
    'db.messages().updateForRecall(Recalls.withdrawn(current, recallNotice))',
    "persist recall chosen display")

chat = "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
replace(chat,
    'm.role == Recalls.WITHDRAWN -> ToolNote(Recalls.MARKER, Icons.Rounded.Info)',
    'm.role == Recalls.WITHDRAWN -> ToolNote(m.note?.takeIf { it.isNotBlank() } ?: Recalls.MARKER, Icons.Rounded.Info)',
    "render the stored notice")

build = "app/build.gradle.kts"
replace(build,
    'versionName = "0.38.17"', 'versionName = "0.38.18"',
    "new version")
replace(build,
    "versionCode = 62083", "versionCode = 62084",
    "new build code")

for rel, text in pending.items():
    (root / rel).write_text(text, encoding="utf-8")

def install(name, path):
    dest = root / "app/src" / path / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    copyfile(here / name.replace(".kt", "_03818.kt"), dest)

install("RecallDisplayText.kt", "main/java/com/cleo/cleos/data")
install("RecallMessageSettings.kt", "main/java/com/cleo/cleos/ui/settings")
install("RecallDisplayTextTest.kt", "test/java/com/cleo/cleos/data")
print("0.38.18/62084: settings > chat > custom recall text, persisted/backup-safe and snapshot per withdrawal")
