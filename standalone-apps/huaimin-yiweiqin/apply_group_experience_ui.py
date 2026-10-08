#!/usr/bin/env python3
"""Wire v0.37.23 group settings, safe operations and character-specific read aloud into Compose UI."""
from pathlib import Path
import shutil,sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
base="app/src/main/java/com/cleo/cleos/"
def replace(path,before,after,expected=1):
    p=root/path; s=p.read_text(encoding="utf-8"); n=s.count(before)
    if n!=expected: raise SystemExit(f"{path}: expected {expected}, got {n}: {before[:125]!r}")
    p.write_text(s.replace(before,after),encoding="utf-8")

shutil.copyfile(here/"GroupOptionsDialog.kt",root/base/"ui/chat/GroupOptionsDialog.kt")
vm=base+"ui/chat/ChatViewModel.kt"
replace(vm,
'''data class GroupMemberUi(val id: Long, val name: String, val avatar: String?, val avatarEmoji: String?)''',
'''data class GroupMemberUi(val id: Long, val name: String, val avatar: String?, val avatarEmoji: String?, val voiceOverride: String? = null)''')
replace(vm,
'''    val groupSpeakers: List<GroupMemberUi> = emptyList(),
    val aiName: String = "",''',
'''    val groupSpeakers: List<GroupMemberUi> = emptyList(),
    val groupMode: Int = 0,
    val groupMaxReplies: Int = 3,
    val groupDailyLimit: Int = 40,
    val groupCallsToday: Int = 0,
    val groupShareOutside: Boolean = true,
    val groupAnnouncement: String = "",
    val groupMutedIds: String = "",
    val aiName: String = "",''')
replace(vm,
'''                groupMembers = members.map { GroupMemberUi(it.id, it.name, it.avatar, it.avatarEmoji) },
                groupSpeakers = if (conversation?.isGroup == true) allCompanions.map { GroupMemberUi(it.id, it.name, it.avatar, it.avatarEmoji) } else emptyList(),
                aiName = if (conversation?.isGroup == true) conversation.title else ta.name,''',
'''                groupMembers = members.map { GroupMemberUi(it.id, it.name, it.avatar, it.avatarEmoji, it.speechVoiceOverride) },
                groupSpeakers = if (conversation?.isGroup == true) allCompanions.map { GroupMemberUi(it.id, it.name, it.avatar, it.avatarEmoji, it.speechVoiceOverride) } else emptyList(),
                groupMode = conversation?.groupMode ?: 0,
                groupMaxReplies = conversation?.groupMaxReplies ?: 3,
                groupDailyLimit = conversation?.groupDailyLimit ?: 40,
                groupCallsToday = if (conversation?.groupUsedDay == java.time.LocalDate.now().toEpochDay()) conversation.groupCallsToday else 0,
                groupShareOutside = conversation?.groupShareOutside ?: true,
                groupAnnouncement = conversation?.groupAnnouncement.orEmpty(),
                groupMutedIds = conversation?.groupMutedIds.orEmpty(),
                aiName = if (conversation?.isGroup == true) conversation.title else ta.name,''')
replace(vm,
'''    fun updateGroupMembers(memberIds: Set<Long>) {''',
'''    fun continueGroup() {
        val id = state.value.conversationId ?: return
        if (state.value.isGroup) c.chat.continueGroup(id)
    }

    fun updateGroupOptions(mode: Int, maxReplies: Int, dailyLimit: Int, shareOutside: Boolean, announcement: String, muted: Set<Long>, voices: Map<Long, String>) {
        val id = state.value.conversationId ?: return
        if (!state.value.isGroup) return
        viewModelScope.launch {
            c.db.conversations().updateGroupOptions(
                id, mode.coerceIn(0, 2), maxReplies.coerceIn(1, 6), dailyLimit.coerceIn(1, 120),
                shareOutside, announcement.take(300), muted.joinToString(","),
            )
            for ((roleId, raw) in voices) {
                val ta = c.db.companions().get(roleId) ?: continue
                val selected = raw.trim().ifEmpty { null }
                if (ta.speechVoiceOverride != selected) c.db.companions().update(ta.copy(speechVoiceOverride = selected))
            }
        }
    }

    fun updateGroupMembers(memberIds: Set<Long>) {''')

screen=base+"ui/chat/ChatScreen.kt"
replace(screen,
'''    var managingGroup by remember { mutableStateOf(false) }
    var readingRecap by remember { mutableStateOf(false) }''',
'''    var managingGroup by remember { mutableStateOf(false) }
    var groupOptionsOpen by remember { mutableStateOf(false) }
    var readingRecap by remember { mutableStateOf(false) }''')
replace(screen,
'''                        if (state.isGroup) {
                            DropdownMenuItem(
                                text = { Text("管理群成员") },''',
'''                        if (state.isGroup) {
                            DropdownMenuItem(
                                text = { Text("群聊设置与声音") },
                                onClick = { switching = false; groupOptionsOpen = true },
                            )
                            DropdownMenuItem(
                                text = { Text("让他们继续聊") },
                                onClick = { switching = false; vm.continueGroup() },
                            )
                            DropdownMenuItem(
                                text = { Text("管理群成员") },''')
replace(screen,
'''    if (managingGroup) {
        GroupMembersDialog(''',
'''    if (groupOptionsOpen && state.isGroup) {
        GroupOptionsDialog(
            state = state,
            onSave = { mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices ->
                vm.updateGroupOptions(mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices)
                groupOptionsOpen = false
            },
            onDismiss = { groupOptionsOpen = false },
        )
    }
    if (managingGroup) {
        GroupMembersDialog(''')
replace(screen,
'''                                        editEnabled = !state.isGroup,
                                        retryEnabled = !state.isGroup,
                                        readEnabled = !state.isGroup,
                                        reactEnabled = !state.isGroup,''',
'''                                        editEnabled = true,
                                        retryEnabled = true,
                                        readEnabled = true,
                                        reactEnabled = true,''')
# For each group speaker, route read-aloud to their own configured voice instead of the global voice.
replace(screen,
'''        val dir = File(context.cacheDir, "read")
        val settingsNow = appSettings
        playing = mark
        preparing[0] = playScope.launch {
            try {
                if (parts.cut)''',
'''        val dir = File(context.cacheDir, "read")
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
                if (parts.cut)''')
replace(screen,
'''c.speaker.reading(settingsNow, parts.pieces''',
'''c.speaker.reading(voiceSettings, parts.pieces''',
expected=2)

# Standalone conversations can be opted out from retrieval by unrelated roles.
conversationScreen=base+"ui/chat/ConversationsScreen.kt"
replace(conversationScreen,
'''                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { actions = null; confirm = conv },
                    ) { Text("删除", color = LocalGlassPalette.current.error) }''',
'''                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            actions = null
                            c.appScope.launch { c.db.conversations().setHistoryShare(conv.id, !conv.historyShareAllowed) }
                        },
                    ) { Text(if (conv.historyShareAllowed) "禁止其他角色读取本会话" else "允许其他角色读取本会话") }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { actions = null; confirm = conv },
                    ) { Text("删除", color = LocalGlassPalette.current.error) }''')
print("0.37.23 Compose controls, group message operations, role voice and per-chat privacy applied")
