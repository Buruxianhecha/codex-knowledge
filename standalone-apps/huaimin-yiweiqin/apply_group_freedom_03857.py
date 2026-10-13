#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])
src=root/"app/src/main/java/com/cleo/cleos"
def edit(name,pairs):
    p=src/name
    s=p.read_text(encoding="utf-8")
    for a,b in pairs:
        n=s.count(a)
        if n!=1:raise RuntimeError(name+" anchor count "+str(n)+" "+a[:120])
        s=s.replace(a,b,1)
    p.write_text(s,encoding="utf-8")
edit("ai/GroupChats.kt",[
 ('    const val MAX_MEMBERS = 6','    const val MAX_MEMBERS = Int.MAX_VALUE\n    const val MAX_ROUND_RESPONSES = 24')
])
edit("ai/ChatRepository.kt",[
 ('fresh.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)',
  'fresh.groupMaxReplies.coerceIn(1, GroupChats.MAX_ROUND_RESPONSES)'),
 ('mentions.size.coerceIn(1, GroupChats.MAX_MEMBERS)',
  'mentions.size.coerceIn(1, GroupChats.MAX_ROUND_RESPONSES)'),
 ('conversation.groupMaxReplies.coerceIn(1, GroupChats.MAX_MEMBERS)',
  'conversation.groupMaxReplies.coerceIn(1, GroupChats.MAX_ROUND_RESPONSES)')
])
edit("ui/chat/ChatViewModel.kt",[
 ('maxReplies.coerceIn(1, 6)','maxReplies.coerceIn(1, 24)')
])
edit("ui/chat/GroupOptionsDialog.kt",[
 ('每轮最多 AI 回复人数（1–6）','每轮最多 AI 回复人数（1–24）'),
 ('(maxReplies.toIntOrNull() ?: 3).coerceIn(1, 6)',
  '(maxReplies.toIntOrNull() ?: 3).coerceIn(1, 24)'),
 ('默认关闭。每群每天最多考虑邀请一次；被邀请角色可以拒绝。同意后自动入群并能查看群历史。最多6名AI，邀请决定会消耗相应模型额度。',
  '默认关闭。没有6人限制或每天一次的限制；邀请判断有20分钟冷却以保护模型额度。被邀请者可拒绝，接受后可查看群历史。')
])
edit("ui/chat/ChatScreen.kt",[
 ('if (selected.size >= GroupChats.MAX_MEMBERS || companions.size > GroupChats.MAX_MEMBERS) Text("一次群聊最多 ',
  'if (companions.isEmpty()) Text("一次群聊最多 '),
 ('if (selected.size >= GroupChats.MAX_MEMBERS || companions.size > GroupChats.MAX_MEMBERS) {\n                    Text("一次群聊最多 ',
  'if (companions.isEmpty()) {\n                    Text("一次群聊最多 '),
])
# Replace the now-unreachable legacy limit sentences with a meaningful empty-contact hint.
chat=src/"ui/chat/ChatScreen.kt"
v=chat.read_text(encoding="utf-8")
v=v.replace('Text("一次群聊最多 '+chr(36)+'{GroupChats.MAX_MEMBERS} 个角色。", fontSize = 12.sp)',
            'Text("请先创建AI角色。", fontSize = 12.sp)')
chat.write_text(v,encoding="utf-8")
invite=Path(__file__).resolve().parent/"GroupAiInvitations_03857.kt"
assert invite.exists()
(src/"ai/GroupAiInvitations.kt").write_bytes(invite.read_bytes())
test=Path(__file__).resolve().parent/"GroupInviteRulesTest_03857.kt"
assert test.exists()
(root/"app/src/test/java/com/cleo/cleos/ai/GroupInviteRulesTest.kt").write_bytes(test.read_bytes())
print("No 6-AI cap; 24 max replies per turn; 20-minute autonomous invitation cooldown")
