#!/usr/bin/env python3
"""Incremental v0.37.23 group experience data upgrade (from generated v0.37.22)."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
def replace(path, before, after, expected=1):
    p=root/path; body=p.read_text(encoding="utf-8")
    count=body.count(before)
    if count!=expected: raise SystemExit(f"{path}: expected {expected}, saw {count}, for {before[:120]!r}")
    p.write_text(body.replace(before, after),encoding="utf-8")

base="app/src/main/java/com/cleo/cleos/"
replace("app/build.gradle.kts", 'versionName = "0.37.22"', 'versionName = "0.37.23"')
replace("app/build.gradle.kts", 'versionCode = 62042', 'versionCode = 62043')

replace(base+"data/db/Entities.kt",
'''    val manualRank: Long? = null,
    /** What the TA keeps of the messages no longer sent verbatim:''',
'''    val manualRank: Long? = null,
    /** Natural 0, every active member 1, only explicitly @-mentioned members 2. */
    @ColumnInfo(defaultValue = "0") val groupMode: Int = 0,
    @ColumnInfo(defaultValue = "3") val groupMaxReplies: Int = 3,
    @ColumnInfo(defaultValue = "40") val groupDailyLimit: Int = 40,
    @ColumnInfo(defaultValue = "0") val groupUsedDay: Long = 0,
    @ColumnInfo(defaultValue = "0") val groupCallsToday: Int = 0,
    /** Only the current group may read cross-conversation records when enabled. */
    @ColumnInfo(defaultValue = "1") val groupShareOutside: Boolean = true,
    /** Records from this conversation can be read by other characters when enabled. */
    @ColumnInfo(defaultValue = "1") val historyShareAllowed: Boolean = true,
    @ColumnInfo(defaultValue = "''") val groupAnnouncement: String = "",
    /** Comma-separated member IDs; applies only while they are members. */
    @ColumnInfo(defaultValue = "''") val groupMutedIds: String = "",
    /** What the TA keeps of the messages no longer sent verbatim:''')
replace(base+"data/db/Entities.kt",
'''    val spokenApiModel: String = "",
) {''',
'''    val spokenApiModel: String = "",
    /** Optional provider voice ID for reading this character's own group messages. */
    val speechVoiceOverride: String? = null,
) {''')
replace(base+"data/db/AppDatabase.kt",
'''    version = 19,''','''    version = 20,''')
replace(base+"data/db/AppDatabase.kt",
'''        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN manualRank INTEGER")
            }
        }
    }''',
'''        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN manualRank INTEGER")
            }
        }

        /** Preserve every existing chat while adding multi-character controls. */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupMaxReplies INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupDailyLimit INTEGER NOT NULL DEFAULT 40")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupUsedDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupCallsToday INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupShareOutside INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE conversations ADD COLUMN historyShareAllowed INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupAnnouncement TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupMutedIds TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE companions ADD COLUMN speechVoiceOverride TEXT")
            }
        }
    }''')
replace(base+"CleosApp.kt",
'''AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19)''',
'''AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20)''')
dao=base+"data/db/Daos.kt"
replace(dao,
'''    suspend fun setManualRank(id: Long, rank: Long)
''',
'''    suspend fun setManualRank(id: Long, rank: Long)

    @Query("UPDATE conversations SET groupMode=:mode, groupMaxReplies=:maxReplies, groupDailyLimit=:dailyLimit, groupShareOutside=:shareOutside, groupAnnouncement=:announcement, groupMutedIds=:muted WHERE id=:id AND isGroup=1")
    suspend fun updateGroupOptions(id: Long, mode: Int, maxReplies: Int, dailyLimit: Int, shareOutside: Boolean, announcement: String, muted: String)

    @Query("UPDATE conversations SET historyShareAllowed=:allowed WHERE id=:id")
    suspend fun setHistoryShare(id: Long, allowed: Boolean)

    /** Atomic budget claim; a failed or SKIP model request still counts toward the cap. */
    @Query("UPDATE conversations SET groupUsedDay=:day, groupCallsToday=CASE WHEN groupUsedDay=:day THEN groupCallsToday + 1 ELSE 1 END WHERE id=:id AND isGroup=1 AND (groupUsedDay != :day OR groupCallsToday < groupDailyLimit)")
    suspend fun claimGroupCall(id: Long, day: Long): Int
''')
replace(dao,
'''m.conversationId != :excludeConversationId AND m.role IN ('user','assistant')''',
'''m.conversationId != :excludeConversationId AND c.historyShareAllowed = 1 AND m.role IN ('user','assistant')''',
expected=2)
replace(dao,
'''    suspend fun sharedForCompanion(excludeConversationId: Long, companionId: Long, limit: Int): List<SharedMessageRow>
''',
'''    suspend fun sharedForCompanion(excludeConversationId: Long, companionId: Long, limit: Int): List<SharedMessageRow>

    /** Bounded keyword retrieval across authorized conversations. No full-history prompt dump. */
    @Query("SELECT m.id AS id, m.conversationId AS conversationId, c.title AS conversationTitle, c.companionId AS ownerCompanionId, m.senderCompanionId AS senderCompanionId, m.role AS role, m.content AS content, m.createdAt AS createdAt FROM messages m JOIN conversations c ON c.id=m.conversationId WHERE m.conversationId != :excludeConversationId AND c.historyShareAllowed=1 AND m.role IN ('user','assistant') AND m.note IS NULL AND m.error IS NULL AND m.content LIKE :pattern ORDER BY m.createdAt DESC, m.id DESC LIMIT :limit")
    suspend fun sharedMatches(excludeConversationId: Long, pattern: String, limit: Int): List<SharedMessageRow>
''')
print("0.37.23 data model + explicit 19->20 migration + safe shared search applied")
