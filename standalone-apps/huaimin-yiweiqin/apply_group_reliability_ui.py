#!/usr/bin/env python3
"""Compose group @ picker: attach immutable IDs without showing IDs inside chat text."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base="app/src/main/java/com/cleo/cleos/"
def rep(path,before,after,count=1):
    p=root/path; s=p.read_text(encoding="utf-8"); n=s.count(before)
    if n!=count: raise SystemExit(f"{path}: expected {count}, got {n} for {before[:100]!r}")
    p.write_text(s.replace(before,after),encoding="utf-8")

screen=base+"ui/chat/ChatScreen.kt"
vm=base+"ui/chat/ChatViewModel.kt"
rep(vm,
'''    fun send(text: String): Boolean {
        val id = conversationId.value ?: return false
        if (!c.chat.send(id, text, attachments.toList(), quoting)) return false''',
'''    fun send(text: String, mentions: Map<Long, String> = emptyMap()): Boolean {
        val id = conversationId.value ?: return false
        if (!c.chat.send(id, text, attachments.toList(), quoting, mentions)) return false''')
rep(screen,
'''    var input by rememberSaveable { mutableStateOf("") }
    var inputHeight by remember { mutableIntStateOf(0) }''',
'''    var input by rememberSaveable { mutableStateOf("") }
    // Picker selections are semantic IDs, separate from editable human-readable @ text.
    var chosenMentionIds by remember(state.conversationId) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var inputHeight by remember { mutableIntStateOf(0) }''')
rep(screen,
'''                isGroup = state.isGroup,
                groupMembers = state.groupMembers,
                attachments = vm.attachments,''',
'''                isGroup = state.isGroup,
                groupMembers = state.groupMembers,
                onMentionChosen = { memberId, shownName ->
                    chosenMentionIds = chosenMentionIds + (memberId to shownName)
                },
                attachments = vm.attachments,''')
rep(screen,
'''                    if (vm.send(input)) {
                        input = ""
                        sentCount++
                    }''',
'''                    if (vm.send(input, chosenMentionIds)) {
                        input = ""
                        chosenMentionIds = emptyMap()
                        sentCount++
                    }''')
rep(screen,
'''                                        onMention = { name ->
                                            val gap = if (input.isNotEmpty() && !input.last().isWhitespace()) " " else ""
                                            input += "$gap@$name "
                                            inputFocus.requestFocus()
                                            keyboard?.show()
                                        },''',
'''                                        onMention = { memberId, name ->
                                            val gap = if (input.isNotEmpty() && !input.last().isWhitespace()) " " else ""
                                            input += "$gap@$name "
                                            if (memberId != null) chosenMentionIds = chosenMentionIds + (memberId to name)
                                            inputFocus.requestFocus()
                                            keyboard?.show()
                                        },''')
rep(screen,
'''                                        aiLabel = speaker?.name?.trim()?.ifEmpty { "TA" },
                                        patEnabled = true,''',
'''                                        aiLabel = speaker?.name?.trim()?.ifEmpty { "TA" },
                                        aiLabelId = speaker?.id,
                                        patEnabled = true,''')
rep(screen,
'''    aiLabel: String? = null,
    onMention: (String) -> Unit = {},
    patEnabled: Boolean = true,''',
'''    aiLabel: String? = null,
    aiLabelId: Long? = null,
    onMention: (Long?, String) -> Unit = { _, _ -> },
    patEnabled: Boolean = true,''')
rep(screen,
'''                                .clickable { onMention(aiLabel) },''',
'''                                .clickable { onMention(aiLabelId, aiLabel) },''')
rep(screen,
'''    isGroup: Boolean = false,
    groupMembers: List<GroupMemberUi> = emptyList(),
    attachments: List<MessageImage>,''',
'''    isGroup: Boolean = false,
    groupMembers: List<GroupMemberUi> = emptyList(),
    onMentionChosen: (Long, String) -> Unit = { _, _ -> },
    attachments: List<MessageImage>,''')
rep(screen,
'''    fun insertMention(name: String) {
        if (mentionStart < 0) return
        onTextChange(text.substring(0, mentionStart) + "@$name ")
        focus.requestFocus()
    }''',
'''    fun insertMention(name: String, memberId: Long? = null) {
        if (mentionStart < 0) return
        onTextChange(text.substring(0, mentionStart) + "@$name ")
        if (memberId != null) onMentionChosen(memberId, name)
        focus.requestFocus()
    }''')
rep(screen,
'''                            .clickable { insertMention(member.name.ifBlank { "TA" }) }''',
'''                            .clickable { insertMention(member.name.ifBlank { "TA" }, member.id) }''')
print("stable-ID group picker, bubble quick mention and send wiring patched")
