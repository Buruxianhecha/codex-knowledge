#!/usr/bin/env python3
"""v0.38.54: additive opt-in AI-to-AI group invitations, preserving all existing features."""
from pathlib import Path
import sys

def main(root: Path):
    here=Path(__file__).resolve().parent
    src=root/"app/src/main/java/com/cleo/cleos"
    tests=root/"app/src/test/java/com/cleo/cleos"
    def edit(filename,changes):
        f=src/filename
        old=f.read_text(encoding="utf-8")
        new=old
        for a,b in changes:
            count=new.count(a)
            if count!=1: raise RuntimeError(f"{filename}: expected one anchor, got {count}: {a[:95]}")
            new=new.replace(a,b,1)
        f.write_text(new,encoding="utf-8")

    gradle=root/"app/build.gradle.kts"
    ver=gradle.read_text(encoding="utf-8")
    assert ver.count('versionName = "0.38.53"') == 1
    assert ver.count('versionCode = 62119') == 1

    edit("data/db/Entities.kt",[
        ('''    @ColumnInfo(defaultValue = "1") val groupShareOutside: Boolean = true,''',
         '''    @ColumnInfo(defaultValue = "1") val groupShareOutside: Boolean = true,
    /** Opt-in for a current participant to invite a different AI, who may decline. */
    @ColumnInfo(defaultValue = "0") val groupAutoInviteEnabled: Boolean = false,
    /** At most one model invitation consideration per group/local calendar day. */
    @ColumnInfo(defaultValue = "-1") val groupInviteDay: Long = -1L,'''),
    ])
    edit("data/db/AppDatabase.kt",[
        ("    version = 22,", "    version = 23,"),
        ('''        }
    }

    abstract fun groupMembers(): ConversationMemberDao''',
         '''        }

        /** Upgrade settings only; never delete or reassign group members. */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupAutoInviteEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupInviteDay INTEGER NOT NULL DEFAULT -1")
            }
        }
    }

    abstract fun groupMembers(): ConversationMemberDao''')
    ])
    edit("CleosApp.kt",[
        ("AppDatabase.MIGRATION_21_22)","AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23)")
    ])
    edit("data/db/Daos.kt",[
        ('''    suspend fun setGroupAvatar(id: Long, emoji: String)''',
         '''    suspend fun setGroupAvatar(id: Long, emoji: String)

    @Query("UPDATE conversations SET groupAutoInviteEnabled=:allow WHERE id=:id AND isGroup=1")
    suspend fun setGroupAiInvites(id: Long, allow: Boolean)

    /** Persistent compare-and-swap: no duplicate invites after app restart or a second worker. */
    @Query("UPDATE conversations SET groupInviteDay=:day WHERE id=:id AND isGroup=1 AND groupAutoInviteEnabled=1 AND groupInviteDay != :day")
    suspend fun claimGroupInviteDay(id: Long, day: Long): Int''')
    ])

    edit("ui/chat/ChatViewModel.kt",[
        ('''    val groupShareOutside: Boolean = true,''',
         '''    val groupShareOutside: Boolean = true,
    val groupAutoInviteEnabled: Boolean = false,'''),
        ('''                groupShareOutside = conversation?.groupShareOutside ?: true,''',
         '''                groupShareOutside = conversation?.groupShareOutside ?: true,
                groupAutoInviteEnabled = conversation?.groupAutoInviteEnabled ?: false,'''),
        ('''fun updateGroupOptions(mode: Int, maxReplies: Int, dailyLimit: Int, shareOutside: Boolean, announcement: String, muted: Set<Long>, voices: Map<Long, String>, avatarEmoji: String)''',
         '''fun updateGroupOptions(mode: Int, maxReplies: Int, dailyLimit: Int, shareOutside: Boolean, announcement: String, muted: Set<Long>, voices: Map<Long, String>, avatarEmoji: String, allowAiInvites: Boolean)'''),
        ('''            c.db.conversations().setGroupAvatar(id, avatarEmoji.take(10).ifBlank { "👥" })''',
         '''            c.db.conversations().setGroupAvatar(id, avatarEmoji.take(10).ifBlank { "👥" })
            c.db.conversations().setGroupAiInvites(id, allowAiInvites)''')
    ])
    edit("ui/chat/GroupOptionsDialog.kt",[
        ('''onSave: (mode: Int, maxReplies: Int, dailyLimit: Int, shareOutside: Boolean, announcement: String, muted: Set<Long>, voices: Map<Long, String>, avatar: String) -> Unit,''',
         '''onSave: (mode: Int, maxReplies: Int, dailyLimit: Int, shareOutside: Boolean, announcement: String, muted: Set<Long>, voices: Map<Long, String>, avatar: String, allowAiInvites: Boolean) -> Unit,'''),
        ('''    var share by remember(state.conversationId) { mutableStateOf(state.groupShareOutside) }''',
         '''    var share by remember(state.conversationId) { mutableStateOf(state.groupShareOutside) }
    var aiInvites by remember(state.conversationId) { mutableStateOf(state.groupAutoInviteEnabled) }'''),
        ('''                Text("群成员发言控制", fontSize = 15.sp)''',
         '''                Row(modifier = Modifier.fillMaxWidth().clickable { aiInvites = !aiInvites }) {
                    Checkbox(checked = aiInvites, onCheckedChange = { aiInvites = it })
                    Text("允许群内 AI 自主邀请其他 AI 加入", modifier = Modifier.padding(top = 4.dp), fontSize = 13.sp)
                }
                Text("默认关闭。每群每天最多考虑邀请一次；被邀请角色可以拒绝。同意后自动入群并能查看群历史。最多6名AI，邀请决定会消耗相应模型额度。",
                    fontSize = 11.sp)
                Text("群成员发言控制", fontSize = 15.sp)'''),
        ('''                        groupAvatar,
                    )''',
         '''                        groupAvatar,
                        aiInvites,
                    )''')
    ])
    edit("ui/chat/ChatScreen.kt",[
        ('''onSave = { mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices, avatar ->
                vm.updateGroupOptions(mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices, avatar)''',
         '''onSave = { mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices, avatar, aiInvites ->
                vm.updateGroupOptions(mode, maxReplies, dailyLimit, shareOutside, announcement, muted, voices, avatar, aiInvites)''')
    ])
    edit("ai/ChatRepository.kt",[
        ('''    /** The replies being written, by conversation. */''',
         '''    private val groupAiInvitations by lazy { GroupAiInvitations(db,companions,secrets,client,life) }

    /** The replies being written, by conversation. */'''),
        ('''        if (said > 0) members.forEach { member ->
            scope.launch { runCatching { personalityGrowth?.maybeGrow(member.id, conversationId) } }
        }''',
         '''        if (said > 0) members.forEach { member ->
            scope.launch { runCatching { personalityGrowth?.maybeGrow(member.id, conversationId) } }
        }
        // Autonomous social action is explicitly opt-in and rate-limited separately.
        // Runs after actual dialogue, not from text commands or a fabricated system message.
        if (said > 0 && db.conversations().get(conversationId)?.groupAutoInviteEnabled == true) {
            scope.launch { groupAiInvitations.consider(conversationId) }
        }''')
    ])

    service=here/"GroupAiInvitations_03854.kt"
    test=here/"GroupInviteRulesTest_03854.kt"
    assert service.is_file() and test.is_file()
    (src/"ai/GroupAiInvitations.kt").write_bytes(service.read_bytes())
    (tests/"ai/GroupInviteRulesTest.kt").write_bytes(test.read_bytes())

    checks={
        "data/db/Entities.kt":["groupAutoInviteEnabled","groupInviteDay"],
        "data/db/AppDatabase.kt":["MIGRATION_22_23","version = 23"],
        "data/db/Daos.kt":["claimGroupInviteDay","setGroupAiInvites"],
        "ai/ChatRepository.kt":["groupAiInvitations.consider(conversationId)"],
        "ui/chat/GroupOptionsDialog.kt":["aiInvites","允许群内 AI 自主邀请"],
        "ui/chat/ChatScreen.kt":["avatar, aiInvites"],
    }
    for filename,terms in checks.items():
        body=(src/filename).read_text(encoding="utf-8")
        for term in terms: assert term in body,(filename,term)
    ver=ver.replace('versionName = "0.38.53"','versionName = "0.38.54"',1)
    ver=ver.replace('versionCode = 62119','versionCode = 62120',1)
    gradle.write_text(ver,encoding="utf-8")
    print("v0.38.54 Build 62120: double-consent AI group invitations, opt-in and daily CAS; old data safe")

if __name__=="__main__":
    if len(sys.argv)!=2: raise SystemExit("usage: apply_group_ai_invitations_03854.py PROJECT_ROOT")
    main(Path(sys.argv[1]).resolve())
