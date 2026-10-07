#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()

def rep(rel: str, old: str, new: str):
    p = ROOT / rel
    s = p.read_text(encoding="utf-8")
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{rel}: expected one match, got {n}: {old[:120]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")

screen = "app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"

rep(
    screen,
    '''                text = input,
                onTextChange = { input = it },
                attachments = vm.attachments,''',
    '''                text = input,
                onTextChange = { input = it },
                isGroup = state.isGroup,
                groupMembers = state.groupMembers,
                attachments = vm.attachments,''',
)

rep(
    screen,
    '''                                        onRead = { readAloud(m.id, it) },
                                    )''',
    '''                                        onRead = { readAloud(m.id, it) },
                                        onMention = { name ->
                                            val gap = if (input.isNotEmpty() && !input.last().isWhitespace()) " " else ""
                                            input += "$gap@$name "
                                            inputFocus.requestFocus()
                                            keyboard?.show()
                                        },
                                    )''',
)

rep(
    screen,
    '''    aiLabel: String? = null,
    patEnabled: Boolean = true,''',
    '''    aiLabel: String? = null,
    onMention: (String) -> Unit = {},
    patEnabled: Boolean = true,''',
)

rep(
    screen,
    '''                    if (!mine && !aiLabel.isNullOrBlank()) Text(aiLabel, color = palette.contentSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))''',
    '''                    if (!mine && !aiLabel.isNullOrBlank()) {
                        Text(
                            aiLabel,
                            color = palette.contentSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clickable { onMention(aiLabel) },
                        )
                    }''',
)

rep(
    screen,
    '''    text: String,
    onTextChange: (String) -> Unit,
    attachments: List<MessageImage>,''',
    '''    text: String,
    onTextChange: (String) -> Unit,
    isGroup: Boolean = false,
    groupMembers: List<GroupMemberUi> = emptyList(),
    attachments: List<MessageImage>,''',
)

rep(
    screen,
    '''    val c = appContainer()
    val canSend = text.isNotBlank() || attachments.isNotEmpty()
    Column(''',
    '''    val c = appContainer()
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

    fun insertMention(name: String) {
        if (mentionStart < 0) return
        onTextChange(text.substring(0, mentionStart) + "@$name ")
        focus.requestFocus()
    }

    Column(''',
)

rep(
    screen,
    '''        if (attachments.isNotEmpty() || attaching) {
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
        // Each button sits in the middle of a square as tall as the bar:''',
    '''        if (attachments.isNotEmpty() || attaching) {
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
                            .clickable { insertMention(member.name.ifBlank { "TA" }) }
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
        // Each button sits in the middle of a square as tall as the bar:''',
)

rep(
    screen,
    '''            // Narrower than the squares either side: the text keeps as much of the bar as it can.
            Box(Modifier.size(width = 40.dp, height = BarHeight), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(36.dp)''',
    '''            if (isGroup) {
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
                        .size(36.dp)''',
)

print("apply_group_mentions.py applied")
