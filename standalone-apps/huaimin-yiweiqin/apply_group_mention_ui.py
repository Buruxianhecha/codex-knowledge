#!/usr/bin/env python3
"""Precise cursor-aware @ selection tied to companion IDs in the group composer."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
p=root/"app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
s=p.read_text(encoding="utf-8")
def rep(a,b,expected=1):
    global s
    n=s.count(a)
    if n!=expected: raise SystemExit(f"ChatScreen: expected {expected} found {n}: {a[:140]!r}")
    s=s.replace(a,b)

rep(
'''import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf''',
'''import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf''')
rep(
'''import androidx.compose.ui.text.font.FontFamily''',
'''import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily''')
rep(
'''    var input by rememberSaveable { mutableStateOf("") }
    var inputHeight by remember''',
'''    var input by rememberSaveable { mutableStateOf("") }
    // Each picker selection is tracked by immutable ID; deleting its token also deletes the binding.
    val selectedMentions = remember(state.conversationId) { mutableStateMapOf<Long, String>() }
    var inputHeight by remember''')
rep(
'''                onTextChange = { input = it },
                isGroup = state.isGroup,
                groupMembers = state.groupMembers,''',
'''                onTextChange = { changed ->
                    input = changed
                    selectedMentions.keys.toList().forEach { id ->
                        if (selectedMentions[id]?.let(changed::contains) != true) selectedMentions.remove(id)
                    }
                },
                isGroup = state.isGroup,
                groupMembers = state.groupMembers,
                onMentionSelected = { id, name -> selectedMentions[id] = "@$name " },''')
rep(
'''                    if (vm.send(input)) {
                        input = ""
                        sentCount++''',
'''                    if (vm.send(input, selectedMentions.keys.toSet())) {
                        input = ""
                        selectedMentions.clear()
                        sentCount++''')
rep(
'''                                            input += "$gap@$name "
                                            inputFocus.requestFocus()''',
'''                                            input += "$gap@$name "
                                            speaker?.id?.let { selectedMentions[it] = "@$name " }
                                            inputFocus.requestFocus()''')
rep(
'''    groupMembers: List<GroupMemberUi> = emptyList(),
    attachments: List<MessageImage>,''',
'''    groupMembers: List<GroupMemberUi> = emptyList(),
    onMentionSelected: (Long, String) -> Unit = { _, _ -> },
    attachments: List<MessageImage>,''')
rep(
'''    val canSend = text.isNotBlank() || attachments.isNotEmpty()

    val mentionStart = if (isGroup) maxOf(text.lastIndexOf('@'), text.lastIndexOf('＠')) else -1
    val mentionTail = if (mentionStart >= 0) text.substring(mentionStart + 1) else ""''',
'''    val canSend = text.isNotBlank() || attachments.isNotEmpty()
    var fieldValue by remember { mutableStateOf(TextFieldValue(text, selection = TextRange(text.length))) }
    LaunchedEffect(text) {
        if (fieldValue.text != text) {
            fieldValue = TextFieldValue(text, selection = TextRange(text.length))
        }
    }
    val cursor = fieldValue.selection.start.coerceIn(0, text.length)
    val beforeCursor = text.substring(0, cursor)
    val mentionStart = if (isGroup) maxOf(beforeCursor.lastIndexOf('@'), beforeCursor.lastIndexOf('＠')) else -1
    val mentionTail = if (mentionStart >= 0) beforeCursor.substring(mentionStart + 1) else ""''')
rep(
'''    fun insertMention(name: String) {
        if (mentionStart < 0) return
        onTextChange(text.substring(0, mentionStart) + "@$name ")
        focus.requestFocus()
    }''',
'''    fun insertMention(name: String, id: Long? = null) {
        if (mentionStart < 0) return
        val insert = "@$name "
        val updated = text.substring(0, mentionStart) + insert + text.substring(cursor)
        fieldValue = TextFieldValue(updated, selection = TextRange(mentionStart + insert.length))
        onTextChange(updated)
        id?.let { onMentionSelected(it, name) }
        focus.requestFocus()
    }''')
rep(
'''                            .clickable { insertMention(member.name.ifBlank { "TA" }) }''',
'''                            .clickable { insertMention(member.name.ifBlank { "TA" }, member.id) }''')
rep(
'''                    value = text,
                    onValueChange = onTextChange,
                    textStyle = type.body.copy(color = palette.content),''',
'''                    value = fieldValue,
                    onValueChange = { updated ->
                        fieldValue = updated
                        onTextChange(updated.text)
                    },
                    textStyle = type.body.copy(color = palette.content),''')
rep(
'''                                val gap = if (text.isNotEmpty() && !text.last().isWhitespace()) " " else ""
                                onTextChange(text + gap + "@")
                                focus.requestFocus()''',
'''                                val at = fieldValue.selection.start.coerceIn(0, text.length)
                                val gap = if (at > 0 && !text[at - 1].isWhitespace()) " " else ""
                                val addition = gap + "@"
                                val updated = text.substring(0, at) + addition + text.substring(at)
                                fieldValue = TextFieldValue(updated, selection = TextRange(at + addition.length))
                                onTextChange(updated)
                                focus.requestFocus()''')
p.write_text(s,encoding="utf-8")
print("Cursor-aware stable ID mention picker applied")
