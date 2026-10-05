"""Backport Cleos 0.36.0/0.36.1 拍一拍 to the 怀民亦未寝 pinned source."""
from pathlib import Path
import shutil


def apply_pat(root: Path):
    source = Path(__file__).resolve().parent

    def replace(rel: str, old: str, new: str):
        path = root / rel
        text = path.read_text(encoding="utf-8")
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{rel}: expected exactly one match, found {count}: {old[:100]!r}")
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    # Upstream's self-contained pat model + UI.
    shutil.copy2(source / "src/Pats.kt", root / "app/src/main/java/com/cleo/cleos/data/Pats.kt")
    shutil.copy2(source / "src/Pat.kt", root / "app/src/main/java/com/cleo/cleos/ui/chat/Pat.kt")

    tests_data = root / "app/src/test/java/com/cleo/cleos/data"
    tests_ai = root / "app/src/test/java/com/cleo/cleos/ai"
    tests_data.mkdir(parents=True, exist_ok=True)
    tests_ai.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source / "tests/PatsTest.kt", tests_data / "PatsTest.kt")
    shutil.copy2(source / "tests/PatPromptTest.kt", tests_ai / "PatPromptTest.kt")

    # Persistent global pat settings.
    settings = "app/src/main/java/com/cleo/cleos/data/SettingsRepository.kt"
    replace(
        settings,
        """    /** How big the chat's text is, in sp (ChatType.SIZES). */
    val chatTextSize: Int = 15,
    /** The colour of the person's own bubbles (ARGB), still glass; null follows the wallpaper. */
""",
        """    /** How big the chat's text is, in sp (ChatType.SIZES). */
    val chatTextSize: Int = 15,
    /** 拍一拍: the verb, what follows the TA's name, and whether the phone buzzes. */
    val patVerb: String = Pats.VERB,
    val patSuffix: String = "",
    val patBuzz: Boolean = true,
    /** The colour of the person's own bubbles (ARGB), still glass; null follows the wallpaper. */
""",
    )
    replace(
        settings,
        """        val chatAvatars = booleanPreferencesKey("chat_avatars")
        val avatarEachMessage = booleanPreferencesKey("avatar_each_message")
        val chatTextSize = intPreferencesKey("chat_text_size")
        val myBubble = intPreferencesKey("my_bubble")
""",
        """        val chatAvatars = booleanPreferencesKey("chat_avatars")
        val avatarEachMessage = booleanPreferencesKey("avatar_each_message")
        val chatTextSize = intPreferencesKey("chat_text_size")
        val patVerb = stringPreferencesKey("pat_verb")
        val patSuffix = stringPreferencesKey("pat_suffix")
        val patBuzz = booleanPreferencesKey("pat_buzz")
        val myBubble = intPreferencesKey("my_bubble")
""",
    )
    replace(
        settings,
        """            chatAvatars = this[Keys.chatAvatars] ?: d.chatAvatars,
            avatarEachMessage = this[Keys.avatarEachMessage] ?: d.avatarEachMessage,
            chatTextSize = this[Keys.chatTextSize] ?: d.chatTextSize,
            myBubble = this[Keys.myBubble],
""",
        """            chatAvatars = this[Keys.chatAvatars] ?: d.chatAvatars,
            avatarEachMessage = this[Keys.avatarEachMessage] ?: d.avatarEachMessage,
            chatTextSize = this[Keys.chatTextSize] ?: d.chatTextSize,
            patVerb = this[Keys.patVerb]?.takeIf { it.isNotBlank() } ?: d.patVerb,
            patSuffix = this[Keys.patSuffix] ?: d.patSuffix,
            patBuzz = this[Keys.patBuzz] ?: d.patBuzz,
            myBubble = this[Keys.myBubble],
""",
    )
    replace(
        settings,
        """            prefs[Keys.chatAvatars] = next.chatAvatars
            prefs[Keys.avatarEachMessage] = next.avatarEachMessage
            prefs[Keys.chatTextSize] = next.chatTextSize
            if (next.myBubble != null) prefs[Keys.myBubble] = next.myBubble else prefs.remove(Keys.myBubble)
""",
        """            prefs[Keys.chatAvatars] = next.chatAvatars
            prefs[Keys.avatarEachMessage] = next.avatarEachMessage
            prefs[Keys.chatTextSize] = next.chatTextSize
            prefs[Keys.patVerb] = next.patVerb
            prefs[Keys.patSuffix] = next.patSuffix
            prefs[Keys.patBuzz] = next.patBuzz
            if (next.myBubble != null) prefs[Keys.myBubble] = next.myBubble else prefs.remove(Keys.myBubble)
""",
    )

    # One database row is reused while the user keeps patting within the streak window.
    replace(
        "app/src/main/java/com/cleo/cleos/data/db/Daos.kt",
        """    @Query("UPDATE messages SET reactions = :reactions WHERE id = :id")
    suspend fun setReactions(id: Long, reactions: String?)

    /** The newest [limit] messages, newest first. Callers reverse them for the API. */
""",
        """    @Query("UPDATE messages SET reactions = :reactions WHERE id = :id")
    suspend fun setReactions(id: Long, reactions: String?)

    /** A run of pats grown by one: its record, and the time it was last patted. */
    @Query("UPDATE messages SET content = :content, createdAt = :at WHERE id = :id")
    suspend fun setPat(id: Long, content: String, at: Long)

    /** The newest [limit] messages, newest first. Callers reverse them for the API. */
""",
    )

    # Recording a pat is intentionally quiet: it leaves a chat line but does not start a reply.
    repository = "app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
    replace(
        repository,
        "import com.cleo.cleos.data.MessageThoughts\n",
        "import com.cleo.cleos.data.MessageThoughts\nimport com.cleo.cleos.data.Pats\n",
    )
    replace(
        repository,
        """    /** Throw away [assistantMessageId] (a failed or unwanted reply) and ask again. */
    fun retry(conversationId: Long, assistantMessageId: Long) {
""",
        """    private val patting = Mutex()

    /**
     * 拍一拍: leaves a line in [conversationId], or adds one to the run of pats just before it.
     * Nothing is answered: the TA hears of it with the person's next message (Prompt).
     */
    fun pat(conversationId: Long, who: String, verb: String, suffix: String) {
        scope.launch {
            patting.withLock {
                val last = db.messages().newest(conversationId, 1).firstOrNull()?.takeIf { it.role == "pat" }
                val now = stamp()
                val record = Pats.again(Pats.decode(last?.content), last?.createdAt ?: 0L, who, verb, suffix, now)
                if (last != null && record.count > 1) {
                    db.messages().setPat(last.id, Pats.encode(record), now)
                } else {
                    db.messages().insert(
                        MessageEntity(conversationId = conversationId, role = "pat", content = Pats.encode(record), createdAt = now),
                    )
                }
            }
        }
    }

    /** Throw away [assistantMessageId] (a failed or unwanted reply) and ask again. */
    fun retry(conversationId: Long, assistantMessageId: Long) {
""",
    )

    # The model learns about the pat only together with the user's next real message.
    prompt = "app/src/main/java/com/cleo/cleos/ai/Prompt.kt"
    replace(
        prompt,
        "import com.cleo.cleos.data.MessageReactions\n",
        "import com.cleo.cleos.data.MessageReactions\nimport com.cleo.cleos.data.Pats\n",
    )
    replace(
        prompt,
        """        return out
    }

    private fun reactedLine""",
        """        for (m in history) {
            if (m.role != "pat") continue
            val record = Pats.decode(m.content) ?: continue
            val next = theirs.firstOrNull { it.createdAt > m.createdAt }?.id ?: continue
            out.getOrPut(next) { mutableListOf() } += Pats.forModel(record)
        }
        return out
    }

    private fun reactedLine""",
    )
    replace(
        prompt,
        '// "note" lines and "request" cards are for the person reading the chat, not for the model.',
        '// "note" lines, "request" cards and raw "pat" rows are for the person; pats are attached to the next user message above.',
    )

    # Chat state and actions.
    vm = "app/src/main/java/com/cleo/cleos/ui/chat/ChatViewModel.kt"
    replace(
        vm,
        "import com.cleo.cleos.data.MessageQuotes\n",
        "import com.cleo.cleos.data.MessageQuotes\nimport com.cleo.cleos.data.Pats\n",
    )
    replace(
        vm,
        """    /** How big the chat's text is (ChatType). */
    val chatTextSize: Int = ChatType.DEFAULT,
    val model: String = "",
""",
        """    /** How big the chat's text is (ChatType). */
    val chatTextSize: Int = ChatType.DEFAULT,
    /** 拍一拍: the verb, what follows the TA's name, and whether the phone buzzes. */
    val patVerb: String = Pats.VERB,
    val patSuffix: String = "",
    val patBuzz: Boolean = true,
    val model: String = "",
""",
    )
    replace(
        vm,
        """                chatAvatars = s.chatAvatars,
                avatarEachMessage = s.avatarEachMessage,
                chatTextSize = s.chatTextSize,
                model = ta.apiModel,
""",
        """                chatAvatars = s.chatAvatars,
                avatarEachMessage = s.avatarEachMessage,
                chatTextSize = s.chatTextSize,
                patVerb = s.patVerb,
                patSuffix = s.patSuffix,
                patBuzz = s.patBuzz,
                model = ta.apiModel,
""",
    )
    replace(
        vm,
        """    /** Puts [emoji] on one of the TA's messages, or takes it off again. */
    fun react(messageId: Long, emoji: String) = c.chat.react(messageId, emoji)
""",
        """    /** 拍一拍: pats the TA ([ai]) or the person themself. A line in the chat, and no answer. */
    fun pat(ai: Boolean) {
        val s = state.value
        val id = s.conversationId ?: return
        c.chat.pat(id, if (ai) Pats.AI else Pats.ME, s.patVerb, s.patSuffix)
    }

    fun savePat(verb: String, suffix: String, buzz: Boolean) {
        viewModelScope.launch {
            c.settings.update { it.copy(patVerb = Pats.cleanVerb(verb), patSuffix = Pats.cleanSuffix(suffix), patBuzz = buzz) }
        }
    }

    /** Puts [emoji] on one of the TA's messages, or takes it off again. */
    fun react(messageId: Long, emoji: String) = c.chat.react(messageId, emoji)
""",
    )

    # Double tap avatars, haptic feedback, the pat line and edit dialog.
    screen = "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
    replace(
        screen,
        "import androidx.compose.ui.graphics.vector.ImageVector\n",
        "import androidx.compose.ui.graphics.vector.ImageVector\nimport androidx.compose.ui.hapticfeedback.HapticFeedbackType\n",
    )
    replace(
        screen,
        "import androidx.compose.ui.platform.LocalContext\n",
        "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalHapticFeedback\n",
    )
    replace(
        screen,
        "import com.cleo.cleos.data.MessageThoughts\n",
        "import com.cleo.cleos.data.MessageThoughts\nimport com.cleo.cleos.data.Pats\n",
    )
    replace(
        screen,
        """    val rows = ArrayList<ChatRow>(messages.size + 8)
    var newerSide: Boolean? = null
    var marked = false
    for (i in messages.indices.reversed()) {
""",
        """    val rows = ArrayList<ChatRow>(messages.size + 8)
    var newerSide: Boolean? = null
    var marked = false
    // A pat after the TA's last reply must not take its retry button away.
    val last = messages.indexOfLast { it.role != "pat" }
    for (i in messages.indices.reversed()) {
""",
    )
    replace(
        screen,
        "rows += ChatRow.Message(m, isLast = i == messages.lastIndex, showFace = eachFace || side == null || side != newerSide)",
        "rows += ChatRow.Message(m, isLast = i == last, showFace = eachFace || side == null || side != newerSide)",
    )
    replace(
        screen,
        """    // The chat's text, for the bubbles and for what is being typed alike.
    val chatType = remember(state.chatTextSize) { ChatType(state.chatTextSize) }
    val faces = if (!state.chatAvatars) {
""",
        """    // The chat's text, for the bubbles and for what is being typed alike.
    val chatType = remember(state.chatTextSize) { ChatType(state.chatTextSize) }
    // 拍一拍: double tap an avatar. Long-press the TA avatar to edit how it reads.
    val haptics = LocalHapticFeedback.current
    var editingPat by remember { mutableStateOf(false) }
    val buzz = state.patBuzz
    val patActions = remember(buzz) {
        PatActions(
            pat = { ai ->
                if (buzz) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.pat(ai)
            },
            edit = { editingPat = true },
        )
    }
    val faces = if (!state.chatAvatars) {
""",
    )
    replace(
        screen,
        "CompositionLocalProvider(LocalFaces provides faces, LocalStickers provides stickerBook, LocalChatType provides chatType) {",
        """CompositionLocalProvider(LocalFaces provides faces, LocalStickers provides stickerBook, LocalChatType provides chatType, LocalPat provides patActions) {
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
            }""",
    )
    replace(
        screen,
        """                                // The person's answer to a request: their turn, drawn as a line on their side.
                                m.role == "user" && note != null -> ToolNote(note, Icons.Rounded.Key, mine = true)
                                m.thoughtOnly() -> {
""",
        """                                // The person's answer to a request: their turn, drawn as a line on their side.
                                m.role == "user" && note != null -> ToolNote(note, Icons.Rounded.Key, mine = true)
                                m.role == "pat" -> PatLine(Pats.decode(m.content), state.aiName)
                                m.thoughtOnly() -> {
""",
    )
    replace(
        screen,
        "Avatar(faces.ai.file, faces.ai.letter, AvatarSize)",
        "Avatar(faces.ai.file, faces.ai.letter, AvatarSize, Modifier.pattable(ai = true))",
    )
    replace(
        screen,
        "Avatar(faces.me.file, faces.me.letter, AvatarSize)",
        "Avatar(faces.me.file, faces.me.letter, AvatarSize, Modifier.pattable(ai = false))",
    )

    # The same options are discoverable in Settings -> Chat, not only via long-press.
    svm = "app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt"
    replace(
        svm,
        "import com.cleo.cleos.data.ImportPlan\n",
        "import com.cleo.cleos.data.ImportPlan\nimport com.cleo.cleos.data.Pats\n",
    )
    replace(
        svm,
        """    /** Removes this TA with their conversations and diary; [then] leaves the screen. */
    fun deleteCompanion(then: () -> Unit) {
""",
        """    /** 拍一拍 settings shared with the chat's long-press dialog. */
    fun setPat(verb: String, suffix: String, buzz: Boolean) {
        viewModelScope.launch {
            c.settings.update {
                it.copy(patVerb = Pats.cleanVerb(verb), patSuffix = Pats.cleanSuffix(suffix), patBuzz = buzz)
            }
        }
    }

    fun setPatBuzz(on: Boolean) {
        viewModelScope.launch { c.settings.update { it.copy(patBuzz = on) } }
    }

    /** Removes this TA with their conversations and diary; [then] leaves the screen. */
    fun deleteCompanion(then: () -> Unit) {
""",
    )

    page = "app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt"
    replace(page, "import androidx.compose.foundation.layout.Row\n", "import androidx.compose.foundation.layout.Row\nimport androidx.compose.foundation.layout.Spacer\n")
    replace(page, "import androidx.compose.foundation.layout.heightIn\n", "import androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.width\n")
    replace(page, "import com.cleo.cleos.data.GlassMode\n", "import com.cleo.cleos.data.GlassMode\nimport com.cleo.cleos.data.PatRecord\nimport com.cleo.cleos.data.Pats\n")
    replace(page, "import com.cleo.cleos.ui.chat.ChatType\n", "import com.cleo.cleos.ui.chat.ChatType\nimport com.cleo.cleos.ui.chat.PatDialog\n")
    replace(
        page,
        """    ListCard("头像") {
        ExplainedSwitch("聊天里显示头像", "消息旁边放上各自的头像", null, settings.chatAvatars) { vm.setChatAvatars(it) }
        if (settings.chatAvatars) {
            RowDivider(inset = 0.dp)
            ExplainedSwitch(
                "每条都显示",
                "像微信那样每条消息都带头像",
                "像微信那样每条消息都带头像；关着时，连着的几条只在最后一条旁边放一个。",
                settings.avatarEachMessage,
            ) { vm.setAvatarEachMessage(it) }
        }
    }
}
""",
        """    ListCard("头像") {
        ExplainedSwitch("聊天里显示头像", "消息旁边放上各自的头像", null, settings.chatAvatars) { vm.setChatAvatars(it) }
        if (settings.chatAvatars) {
            RowDivider(inset = 0.dp)
            ExplainedSwitch(
                "每条都显示",
                "像微信那样每条消息都带头像",
                "像微信那样每条消息都带头像；关着时，连着的几条只在最后一条旁边放一个。",
                settings.avatarEachMessage,
            ) { vm.setAvatarEachMessage(it) }
        }
    }

    ListCard("拍一拍") {
        var editing by remember { mutableStateOf(false) }
        val said = Pats.line(PatRecord(Pats.AI, 1, settings.patVerb, settings.patSuffix), vm.aiName)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("双击头像拍一下", color = palette.content, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "现在拍出来是「$said」。TA 不会为它单独回话，下次你说话时才知道。",
                    color = palette.contentSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Chip("改一改", selected = false) { editing = true }
        }
        RowDivider(inset = 0.dp)
        ExplainedSwitch("拍的时候震一下", "拍一下时手机跟着轻轻震一下", null, settings.patBuzz) { vm.setPatBuzz(it) }
        if (editing) {
            PatDialog(
                aiName = vm.aiName,
                verb = settings.patVerb,
                suffix = settings.patSuffix,
                buzz = settings.patBuzz,
                onSave = { verb, suffix, buzz ->
                    vm.setPat(verb, suffix, buzz)
                    editing = false
                },
                onDismiss = { editing = false },
            )
        }
    }
}
""",
    )
