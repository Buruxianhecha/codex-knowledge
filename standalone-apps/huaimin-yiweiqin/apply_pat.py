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
    shutil.copy2(source / "tests/PatToolTest.kt", tests_ai / "PatToolTest.kt")
    shutil.copy2(source / "tests/ToolSettingsTest.kt", tests_data / "ToolSettingsTest.kt")

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
    fun pat(conversationId: Long, who: String, verb: String, suffix: String, targetCompanionId: Long? = null) {
        scope.launch {
            patting.withLock {
                val last = db.messages().newest(conversationId, 1).firstOrNull()?.takeIf { it.role == "pat" }
                val now = stamp()
                val record = Pats.again(
                    Pats.decode(last?.content),
                    last?.createdAt ?: 0L,
                    who,
                    verb,
                    suffix,
                    now,
                    targetCompanionId = targetCompanionId,
                )
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
    fun pat(ai: Boolean, targetCompanionId: Long? = null) {
        val s = state.value
        val id = s.conversationId ?: return
        c.chat.pat(id, if (ai) Pats.AI else Pats.ME, s.patVerb, s.patSuffix, targetCompanionId)
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
            pat = { ai, targetCompanionId ->
                if (buzz) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.pat(ai, targetCompanionId)
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
        """        if (faces != null && !mine) {
            if (showFace) {
                Avatar(faces.ai.file, faces.ai.letter, AvatarSize)
                Spacer(Modifier.width(AvatarGap))
""",
        """        if (faces != null && !mine) {
            if (showFace) {
                Avatar(faces.ai.file, faces.ai.letter, AvatarSize, Modifier.pattable(ai = true))
                Spacer(Modifier.width(AvatarGap))
""",
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


    # Cleos 0.36.2 additions: TA can pat back, and a very long run gets a short answer.
    replace(
        settings,
        """        ToolGroup.Alarm,
        ToolGroup.Stickers,
    ),
""",
        """        ToolGroup.Alarm,
        ToolGroup.Stickers,
        ToolGroup.Pat,
    ),
""",
    )

    tools = "app/src/main/java/com/cleo/cleos/ai/Tools.kt"
    replace(
        tools,
        "import com.cleo.cleos.data.DiaryBlocks\n",
        "import com.cleo.cleos.data.DiaryBlocks\nimport com.cleo.cleos.data.Pats\n",
    )
    replace(
        tools,
        "enum class ToolGroup { Todos, Diary, AiDiary, Secrets, Avatar, Weather, Messages, Letters, Memory, Location, Speak, Later, Alarm, Calendar, Music, Stickers }",
        "enum class ToolGroup { Todos, Diary, AiDiary, Secrets, Avatar, Weather, Messages, Letters, Memory, Location, Speak, Later, Alarm, Calendar, Music, Stickers, Pat }",
    )
    replace(
        tools,
        """    /** Tools that leave no trace in the chat, neither a line nor "在…" while they run. */
    val quiet = setOf(noteForLater.name)
""",
        """    val patUser = ToolSpec(
        name = "pat_user",
        groups = setOf(ToolGroup.Pat),
        action = "拍一拍",
        description = "拍一拍对方，像聊天软件里双击头像：对方的手机会轻轻震一下，聊天里多一行“你拍了拍我”。" +
            "对方拍了你、想撒娇、打招呼、逗一逗的时候用，用来代替一句话也行。要用就在回复之间用，一次回复最多一下，别回回都拍。",
        parameters = schema(
            "suffix" to prop("string", "拍在哪儿，接在“我”后面，比如 的头、的脸蛋，最多 12 个字；不填就是直接拍了拍我"),
        ),
    )

    /** Tools that leave no trace in the chat, neither a line nor "在…" while they run. */
    val quiet = setOf(noteForLater.name, patUser.name)
""",
    )
    replace(
        tools,
        """        deleteEvent,
        musicControl,
    )
""",
        """        deleteEvent,
        musicControl,
        patUser,
    )
""",
    )
    replace(
        tools,
        """    /** Whatever the phone is playing, for music_control. */
    private val music: MusicSource? = null,
    private val clock: () -> Long = System::currentTimeMillis,
""",
        """    /** Whatever the phone is playing, for music_control. */
    private val music: MusicSource? = null,
    /** Leaves a pat from the TA in a conversation (pat_user); the chat shows it, so the tool has no line of its own. */
    private val patBack: suspend (conversationId: Long, suffix: String) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
""",
    )
    replace(
        tools,
        """                ToolSpecs.deleteEvent.name -> deleteEvent(args, today)
                ToolSpecs.musicControl.name -> musicControl(args)
                else -> getWeather(args, settings)
""",
        """                ToolSpecs.deleteEvent.name -> deleteEvent(args, today)
                ToolSpecs.musicControl.name -> musicControl(args)
                ToolSpecs.patUser.name -> patUser(args, conversationId)
                else -> getWeather(args, settings)
""",
    )
    replace(
        tools,
        """    private suspend fun noteForLater(a: JsonObject, conversationId: Long, companionId: Long): ToolOutcome {
""",
        """    private suspend fun patUser(a: JsonObject, conversationId: Long): ToolOutcome {
        patBack(conversationId, Pats.cleanSuffix(ToolArgs.text(a, "suffix").orEmpty()))
        return ToolOutcome("拍了拍对方，对方的手机会震一下，聊天里已经有这一行了，不用再说明。", "")
    }

    private suspend fun noteForLater(a: JsonObject, conversationId: Long, companionId: Long): ToolOutcome {
""",
    )

    replace(
        "app/src/main/java/com/cleo/cleos/CleosApp.kt",
        """        calendar = calendar,
        music = music,
    )
""",
        """        calendar = calendar,
        music = music,
        patBack = { id, suffix -> chat.patBack(id, suffix) },
    )
""",
    )

    replace(
        repository,
        "import com.cleo.cleos.data.Pats\n",
        "import com.cleo.cleos.data.PatRecord\nimport com.cleo.cleos.data.Pats\n",
    )
    replace(
        repository,
        """                if (last != null && record.count > 1) {
                    db.messages().setPat(last.id, Pats.encode(record), now)
                } else {
""",
        """                if (last != null && record.count > 1) {
                    db.messages().setPat(last.id, Pats.encode(record), now)
                    if (record.count == Pats.HEAVY_AT && Pats.heavy(record)) answerHeavyPats(conversationId, last.id)
                } else {
""",
    )
    replace(
        repository,
        """    /** Throw away [assistantMessageId] (a failed or unwanted reply) and ask again. */
    fun retry(conversationId: Long, assistantMessageId: Long) {
""",
        """    /** The TA patting the person (pat_user): a line in the chat, after what it has said so far. */
    suspend fun patBack(conversationId: Long, suffix: String) {
        val verb = settings.current().patVerb
        withContext(NonCancellable) {
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "pat",
                    content = Pats.encode(PatRecord(Pats.FROM_AI, 1, verb, Pats.cleanSuffix(suffix))),
                    createdAt = stamp(),
                ),
            )
        }
    }

    /** Patted so many times in a row: once the person stops, let the TA answer with a word or two. */
    private fun answerHeavyPats(conversationId: Long, patId: Long) {
        scope.launch {
            if (ToolGroup.Pat !in settings.current().tools) return@launch
            while (true) {
                val row = db.messages().newest(conversationId, 1).firstOrNull()
                    ?.takeIf { it.id == patId && it.role == "pat" } ?: return@launch
                val quietFor = System.currentTimeMillis() - row.createdAt
                if (quietFor >= Pats.STREAK_MS + 500) break
                delay(Pats.STREAK_MS + 500 - quietFor)
            }
            val ta = taOf(conversationId)
            if (secrets.key(ta.modelFor(heard = false).baseUrl).isNullOrBlank()) return@launch
            // The switch may have been turned off while waiting for the person to stop patting.
            if (ToolGroup.Pat !in settings.current().tools) return@launch
            start(conversationId) { reply(conversationId) }
        }
    }

    /** Throw away [assistantMessageId] (a failed or unwanted reply) and ask again. */
    fun retry(conversationId: Long, assistantMessageId: Long) {
""",
    )

    replace(
        prompt,
        """            val record = Pats.decode(m.content) ?: continue
            val next = theirs.firstOrNull { it.createdAt > m.createdAt }?.id ?: continue
""",
        """            val record = Pats.decode(m.content) ?: continue
            // The TA's own pats are in its own calls; a heavy run is a turn of its own.
            if (record.who == Pats.FROM_AI || Pats.heavy(record)) continue
            val next = theirs.firstOrNull { it.createdAt > m.createdAt }?.id ?: continue
""",
    )
    replace(
        prompt,
        """        // "note" lines, "request" cards and raw "pat" rows are for the person; pats are attached to the next user message above.
        else -> null
""",
        """        // "note" lines and "request" cards are for the person reading the chat, not for the model.
        // A normal pat is told with the person's next message; a heavy run is a turn of its own.
        "pat" -> Pats.decode(content)?.takeIf(Pats::heavy)?.let { ApiMessage("user", Pats.forModel(it)) }
        else -> null
""",
    )

    replace(
        screen,
        """    val buzz = state.patBuzz
    val patActions = remember(buzz) {
""",
        """    val buzz = state.patBuzz
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
""",
    )

    replace(
        "app/src/main/java/com/cleo/cleos/ui/settings/SettingsPages.kt",
        "ToolGroup.Messages, ToolGroup.Speak, ToolGroup.Stickers,",
        "ToolGroup.Messages, ToolGroup.Speak, ToolGroup.Stickers, ToolGroup.Pat,",
    )
    replace(
        "app/src/main/java/com/cleo/cleos/ui/settings/SharedPages.kt",
        """        ExplainedSwitch(
            "发表情包",
            "偶尔从你的表情包里挑一张发",
            "TA 会从你的表情包里挑着发，偶尔一张（表情包在聊天输入框的笑脸里加）。TA 看不到图，是按名字和说明认的，" +
                "所以名字起得像在说那张图最好。这个不需要模型支持工具。",
            on(ToolGroup.Stickers),
        ) { vm.setTool(ToolGroup.Stickers, it) }
    }
""",
        """        ExplainedSwitch(
            "发表情包",
            "偶尔从你的表情包里挑一张发",
            "TA 会从你的表情包里挑着发，偶尔一张（表情包在聊天输入框的笑脸里加）。TA 看不到图，是按名字和说明认的，" +
                "所以名字起得像在说那张图最好。这个不需要模型支持工具。",
            on(ToolGroup.Stickers),
        ) { vm.setTool(ToolGroup.Stickers, it) }
        RowDivider(inset = 0.dp)
        ExplainedSwitch(
            "拍回来",
            "你拍 TA 之后，TA 有时会拍回来",
            "TA 回你话的时候，偶尔会拍你一下，聊天里多一行“TA 拍了拍我”，手机震一下。你连着拍了很多下，TA 也会回一两句。" +
                "拍回来要模型支持工具，连拍之后的那一句不用；关了这个开关，两样都停。",
            on(ToolGroup.Pat),
        ) { vm.setTool(ToolGroup.Pat, it) }
    }
""",
    )


    # 怀民亦未寝 0.36.9: a pat on the TA is now a real input turn and gets a reply after the
    # normal short "wait until the person is quiet" debounce. Repeated pats in that window merge
    # into one counted line and therefore produce one reply, not one reply per tap.
    replace(
        repository,
        """    fun pat(conversationId: Long, who: String, verb: String, suffix: String, targetCompanionId: Long? = null) {
        scope.launch {
            patting.withLock {
                val last = db.messages().newest(conversationId, 1).firstOrNull()?.takeIf { it.role == "pat" }
                val now = stamp()
                val record = Pats.again(
                    Pats.decode(last?.content),
                    last?.createdAt ?: 0L,
                    who,
                    verb,
                    suffix,
                    now,
                    targetCompanionId = targetCompanionId,
                )
                if (last != null && record.count > 1) {
                    db.messages().setPat(last.id, Pats.encode(record), now)
                    if (record.count == Pats.HEAVY_AT && Pats.heavy(record)) answerHeavyPats(conversationId, last.id)
                } else {
                    db.messages().insert(
                        MessageEntity(conversationId = conversationId, role = "pat", content = Pats.encode(record), createdAt = now),
                    )
                }
            }
        }
    }
""",
        """    fun pat(conversationId: Long, who: String, verb: String, suffix: String, targetCompanionId: Long? = null) {
        scope.launch {
            patting.withLock {
                val last = db.messages().newest(conversationId, 1).firstOrNull()?.takeIf { it.role == "pat" }
                val now = stamp()
                val record = Pats.again(
                    Pats.decode(last?.content),
                    last?.createdAt ?: 0L,
                    who,
                    verb,
                    suffix,
                    now,
                    targetCompanionId = targetCompanionId,
                )
                if (last != null && record.count > 1) {
                    db.messages().setPat(last.id, Pats.encode(record), now)
                } else {
                    db.messages().insert(
                        MessageEntity(conversationId = conversationId, role = "pat", content = Pats.encode(record), createdAt = now),
                    )
                }
            }
            if (who == Pats.AI) answerSoon(conversationId)
        }
    }
""",
    )

    replace(
        repository,
        """    /** Whether the person has said something since what the last reply took in. */
    private suspend fun unanswered(conversationId: Long): Boolean {
        val upTo = answeredUpTo[conversationId] ?: Long.MIN_VALUE
        return db.messages().newest(conversationId, UNANSWERED_LOOKBACK).any { m ->
            m.role == "user" && m.note == null && m.error == null && m.createdAt > upTo &&
                (m.content.isNotBlank() || m.images != null)
        }
    }
""",
        """    /** Whether the person has said or done something since what the last reply took in. */
    private suspend fun unanswered(conversationId: Long): Boolean {
        val upTo = answeredUpTo[conversationId] ?: Long.MIN_VALUE
        return db.messages().newest(conversationId, UNANSWERED_LOOKBACK).any { m ->
            val userMessage = m.role == "user" && m.note == null && m.error == null &&
                (m.content.isNotBlank() || m.images != null)
            val patOnTa = m.role == "pat" && Pats.decode(m.content)?.who == Pats.AI
            m.createdAt > upTo && (userMessage || patOnTa)
        }
    }
""",
    )

    replace(
        repository,
        """        // Something said aloud is answered by the model the TA has for words heard, when it has one.
        val use = ta.modelFor(heard = history.lastOrNull { it.role == "user" }?.audio != null)
""",
        """        // A pat is a text-like interaction; only an actual latest voice message uses the heard model.
        val lastInput = history.lastOrNull { m ->
            m.role == "user" || (m.role == "pat" && Pats.decode(m.content)?.who == Pats.AI)
        }
        val use = ta.modelFor(heard = lastInput?.role == "user" && lastInput.audio != null)
""",
    )
    replace(
        repository,
        """            history.lastOrNull { it.role == "user" }?.let { m -> answeredUpTo.merge(conversationId, m.createdAt) { a, b -> maxOf(a, b) } }
""",
        """            lastInput?.let { m -> answeredUpTo.merge(conversationId, m.createdAt) { a, b -> maxOf(a, b) } }
""",
    )

    replace(
        prompt,
        """        for (m in history) {
            if (m.role != "pat") continue
            val record = Pats.decode(m.content) ?: continue
            // The TA's own pats are in its own calls; a heavy run is a turn of its own.
            if (record.who == Pats.FROM_AI || Pats.heavy(record)) continue
            val next = theirs.firstOrNull { it.createdAt > m.createdAt }?.id ?: continue
            out.getOrPut(next) { mutableListOf() } += Pats.forModel(record)
        }
""",
        """        for (m in history) {
            if (m.role != "pat") continue
            val record = Pats.decode(m.content) ?: continue
            // Patting the TA is now its own turn; only patting oneself stays quiet until the next message.
            if (record.who != Pats.ME) continue
            val next = theirs.firstOrNull { it.createdAt > m.createdAt }?.id ?: continue
            out.getOrPut(next) { mutableListOf() } += Pats.forModel(record)
        }
""",
    )
    replace(
        prompt,
        """"pat" -> Pats.decode(content)?.takeIf(Pats::heavy)?.let { ApiMessage("user", Pats.forModel(it)) }
""",
        """"pat" -> Pats.decode(content)?.takeIf { it.who == Pats.AI }?.let { ApiMessage("user", Pats.forModel(it)) }
""",
    )

    replace(
        page,
        """"现在拍出来是「$said」。TA 不会为它单独回话，下次你说话时才知道。",""",
        """"现在拍出来是「$said」。拍 TA 后停一下，TA 会直接回应；连续拍会合并成一轮回复。",""",
    )

    replace(
        "app/src/main/java/com/cleo/cleos/ui/settings/SharedPages.kt",
        """"TA 回你话的时候，偶尔会拍你一下，聊天里多一行“TA 拍了拍我”，手机震一下。你连着拍了很多下，TA 也会回一两句。" +
                "拍回来要模型支持工具，连拍之后的那一句不用；关了这个开关，两样都停。",""",
        """"TA 回你话的时候，偶尔会拍你一下，聊天里多一行“TA 拍了拍我”，手机震一下。" +
                "你拍 TA 时的直接回复不依赖这个开关；这里控制的是 TA 能不能主动拍回来。",""",
    )
