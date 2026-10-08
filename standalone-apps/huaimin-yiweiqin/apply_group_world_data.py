#!/usr/bin/env python3
"""v0.37.24: non-destructive Room 21->22; group identity and honest text-usage statistics."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base="app/src/main/java/com/cleo/cleos/"
def rep(path,old,new,count=1):
    p=root/path
    s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=count: raise SystemExit(f"{path}: wanted {count}, found {n}: {old[:110]!r}")
    p.write_text(s.replace(old,new),encoding="utf-8")
rep("app/build.gradle.kts",'versionName = "0.37.23"','versionName = "0.37.24"')
rep("app/build.gradle.kts",'versionCode = 62044','versionCode = 62046')
rep(base+"data/db/Entities.kt",
'''    @ColumnInfo(defaultValue = "''") val groupMutedIds: String = "",
    /** What the TA keeps of the messages no longer sent verbatim:''',
'''    @ColumnInfo(defaultValue = "''") val groupMutedIds: String = "",
    /** Custom group identity, one self-contained emoji; not a file path. */
    @ColumnInfo(defaultValue = "'👥'") val groupAvatarEmoji: String = "👥",
    /** Number of actual outgoing group model attempts, including SKIP and refusals. */
    @ColumnInfo(defaultValue = "0") val groupTotalCalls: Long = 0L,
    /** Character-count proxy only; NOT actual tokens or billing. */
    @ColumnInfo(defaultValue = "0") val groupTextInputChars: Long = 0L,
    @ColumnInfo(defaultValue = "0") val groupTextOutputChars: Long = 0L,
    /** What the TA keeps of the messages no longer sent verbatim:''')
rep(base+"data/db/AppDatabase.kt",'    version = 21,','    version = 22,')
rep(base+"data/db/AppDatabase.kt",
'''        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mentionedCompanionIds TEXT")
            }
        }
    }''',
'''        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mentionedCompanionIds TEXT")
            }
        }

        /** 21 -> 22 only adds metadata; no existing chat or group row is rewritten. */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupAvatarEmoji TEXT NOT NULL DEFAULT '👥'")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupTotalCalls INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupTextInputChars INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN groupTextOutputChars INTEGER NOT NULL DEFAULT 0")
            }
        }
    }''')
rep(base+"CleosApp.kt",
'''AppDatabase.MIGRATION_20_21)
        .build()''',
'''AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22)
        .build()''')
rep(base+"data/db/Daos.kt",
'''    suspend fun claimGroupCall(id: Long, day: Long): Int''',
'''    suspend fun claimGroupCall(id: Long, day: Long): Int

    @Query("UPDATE conversations SET groupAvatarEmoji=:emoji WHERE id=:id AND isGroup=1")
    suspend fun setGroupAvatar(id: Long, emoji: String)

    @Query("UPDATE conversations SET groupTextInputChars=groupTextInputChars+:input, groupTextOutputChars=groupTextOutputChars+:output WHERE id=:id AND isGroup=1")
    suspend fun recordGroupText(id: Long, input: Long, output: Long)''')
rep(base+"data/db/Daos.kt",
'''groupCallsToday=CASE WHEN groupUsedDay=:day THEN groupCallsToday + 1 ELSE 1 END WHERE id=:id AND isGroup=1 AND (groupUsedDay != :day OR groupCallsToday < groupDailyLimit)''',
'''groupCallsToday=CASE WHEN groupUsedDay=:day THEN groupCallsToday + 1 ELSE 1 END, groupTotalCalls=groupTotalCalls+1 WHERE id=:id AND isGroup=1 AND (groupUsedDay != :day OR groupCallsToday < groupDailyLimit)''')
print("v0.37.24 identity/telemetry migration applied")
