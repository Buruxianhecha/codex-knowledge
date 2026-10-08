#!/usr/bin/env python3
"""v0.37.24 group avatar presets + transparent text statistics UI."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base="app/src/main/java/com/cleo/cleos/"
def rep(path,old,new,count=1):
    p=root/path;s=p.read_text(encoding="utf-8");n=s.count(old)
    if n!=count:raise SystemExit(f"{path}: wanted {count}, found {n}: {old[:100]!r}")
    p.write_text(s.replace(old,new),encoding="utf-8")
vm=base+"ui/chat/ChatViewModel.kt"
rep(vm,
'''    val groupMutedIds: String = "",
    val aiName: String = "",''',
'''    val groupMutedIds: String = "",
    val groupAvatarEmoji: String = "👥",
    val groupTotalCalls: Long = 0L,
    val groupTextInputChars: Long = 0L,
    val groupTextOutputChars: Long = 0L,
    val aiName: String = "",''')
rep(vm,
'''                groupMutedIds = conversation?.groupMutedIds.orEmpty(),
                aiName = if (conversation?.isGroup == true) conversation.title else ta.name,''',
'''                groupMutedIds = conversation?.groupMutedIds.orEmpty(),
                groupAvatarEmoji = conversation?.groupAvatarEmoji ?: "👥",
                groupTotalCalls = conversation?.groupTotalCalls ?: 0L,
                groupTextInputChars = conversation?.groupTextInputChars ?: 0L,
                groupTextOutputChars = conversation?.groupTextOutputChars ?: 0L,
                aiName = if (conversation?.isGroup == true) conversation.title else ta.name,''')
rep(vm,
'''fun updateGroupOptions(mode: Int, maxReplies: Int, dailyLimit: Int, shareOutside: Boolean, announcement: String, muted: Set<Long>, voices: Map<Long, String>)''',
'''fun updateGroupOptions(mode: Int, maxReplies: Int, dailyLimit: Int, shareOutside: Boolean, announcement: String, muted: Set<Long>, voices: Map<Long, String>, avatarEmoji: String)''')
rep(vm,
'''                shareOutside, announcement.take(300), muted.joinToString(","),
            )
            for ((roleId, raw) in voices)''',
'''                shareOutside, announcement.take(300), muted.joinToString(","),
            )
            c.db.conversations().setGroupAvatar(id, avatarEmoji.take(10).ifBlank { "👥" })
            for ((roleId, raw) in voices)''')
screen=base+"ui/chat/ChatScreen.kt"
rep(screen,
'''title = state.aiName.ifBlank { "聊天" },''',
'''title = if (state.isGroup) "${state.groupAvatarEmoji} ${state.aiName.ifBlank { "群聊" }}" else state.aiName.ifBlank { "聊天" },''')
rep(screen,
'''onSave = { mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices ->
                vm.updateGroupOptions(mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices)''',
'''onSave = { mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices, avatar ->
                vm.updateGroupOptions(mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices, avatar)''')
history=base+"ui/chat/ConversationsScreen.kt"
rep(history,
'''Text(conv.title, color = palette.content, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)''',
'''Text((if (conv.isGroup) "${conv.groupAvatarEmoji} " else "") + conv.title, color = palette.content, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)''')
print("v0.37.24 group avatar + stats UI integrated")
