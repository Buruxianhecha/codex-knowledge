#!/usr/bin/env python3
from pathlib import Path
import shutil, sys

ROOT = Path(sys.argv[1]).resolve()
HERE = Path(__file__).resolve().parent

def rep(rel, old, new):
    p = ROOT / rel
    s = p.read_text(encoding='utf-8')
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{rel}: expected one match, got {n}: {old[:120]!r}')
    p.write_text(s.replace(old, new, 1), encoding='utf-8')

def cp(src_name, rel):
    src = HERE / src_name
    dst = ROOT / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)


# Pure group-chat rules and regression tests.
cp('GroupChats.kt', 'app/src/main/java/com/cleo/cleos/ai/GroupChats.kt')
cp('GroupChatsTest.kt', 'app/src/test/java/com/cleo/cleos/ai/GroupChatsTest.kt')

# Version this feature as the next directly-upgradable build.
rep('app/build.gradle.kts', 'versionCode = 62038', 'versionCode = 62041')
rep('app/build.gradle.kts', 'versionName = "0.37.21"', 'versionName = "0.37.22"')

entities = 'app/src/main/java/com/cleo/cleos/data/db/Entities.kt'
rep(entities,
'''/** Scheduling state is ephemeral and intentionally not part of portable backups. */
@Entity(
    tableName = "free_topics",''',
'''/** Scheduling state is ephemeral and intentionally not part of portable backups. */
@Entity(
    tableName = "free_topics",''')
# Replace the old isolation comment: single-chat history can now be surfaced across characters.
rep(entities,
'''/**
 * One TA: who they are and which model speaks for them. Each has their own conversations
 * and their own diary entries; they don't see each other's.
 */''',
'''/**
 * One TA: who they are and which model speaks for them. Persona and private memories remain
 * theirs, while real chat history may be referenced across characters and inside group chats.
 */''')

rep(entities,
'''@Serializable
@Entity(tableName = "conversations", indices = [Index("companionId")])
data class ConversationEntity(''',
'''/** A TA participating in one group conversation. */
@Serializable
@Entity(
    tableName = "conversation_members",
    primaryKeys = ["conversationId", "companionId"],
    indices = [Index("companionId")],
    foreignKeys = [
        ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CompanionEntity::class, parentColumns = ["id"], childColumns = ["companionId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ConversationMemberEntity(
    val conversationId: Long,
    val companionId: Long,
    val position: Int = 0,
)

/** A compact projection used to let one TA read real excerpts from other chats. */
data class SharedMessageRow(
    val id: Long,
    val conversationId: Long,
    val conversationTitle: String,
    val ownerCompanionId: Long,
    val senderCompanionId: Long?,
    val role: String,
    val content: String,
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "conversations", indices = [Index("companionId"), Index("isGroup")])
data class ConversationEntity(''')
rep(entities,
'''    @ColumnInfo(defaultValue = "1")
    val companionId: Long = 1,
    /** What the TA keeps of the messages no longer sent verbatim: a running summary (ai/Recap.kt). */''',
'''    @ColumnInfo(defaultValue = "1")
    val companionId: Long = 1,
    /** A shared conversation whose speakers are in conversation_members. Old conversations remain single-TA. */
    @ColumnInfo(defaultValue = "0")
    val isGroup: Boolean = false,
    /** Pinned conversations stay above ordinary ones. */
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
    /** Stable order after the person manually moves a conversation. */
    val manualRank: Long? = null,
    /** What the TA keeps of the messages no longer sent verbatim: a running summary (ai/Recap.kt). */''')
rep(entities,
'''    val call: Long? = null,
)''',
'''    val call: Long? = null,
    /** Which TA actually said an assistant message. Null means the conversation's legacy owner. */
    val senderCompanionId: Long? = null,
)''')

appdb = 'app/src/main/java/com/cleo/cleos/data/db/AppDatabase.kt'
rep(appdb,
'''        FreeTopicStateEntity::class,
    ],
    version = 17,''',
'''        FreeTopicStateEntity::class,
        ConversationMemberEntity::class,
    ],
    version = 19,''')
rep(appdb,
'''        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
    ],''',
'''        AutoMigration(from = 15, to = 16),
    ],''')
rep(appdb,
'''import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase''',
'''import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase''')
rep(appdb,
'''abstract class AppDatabase : RoomDatabase() {
    abstract fun freeTopics(): FreeTopicDao''',
'''abstract class AppDatabase : RoomDatabase() {
    companion object {
        /** 16 -> 17 is the 0.37.21 proactive schema, explicit because schema 17 was not checked in. */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE companions ADD COLUMN followUpEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE companions ADD COLUMN followUpDelaySeconds INTEGER NOT NULL DEFAULT 60")
                db.execSQL("ALTER TABLE companions ADD COLUMN freeTopicEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE companions ADD COLUMN freeTopicLevel INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE companions ADD COLUMN freeTopicQuietOn INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE companions ADD COLUMN freeTopicQuietStart INTEGER NOT NULL DEFAULT 1380")
                db.execSQL("ALTER TABLE companions ADD COLUMN freeTopicQuietEnd INTEGER NOT NULL DEFAULT 480")
                db.execSQL("ALTER TABLE conversations ADD COLUMN followUpMessageId INTEGER")
                db.execSQL("ALTER TABLE conversations ADD COLUMN followUpAt INTEGER")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS free_topics (
                        companionId INTEGER NOT NULL,
                        nextAt INTEGER NOT NULL,
                        attemptDay INTEGER,
                        attempts INTEGER NOT NULL,
                        PRIMARY KEY(companionId),
                        FOREIGN KEY(companionId) REFERENCES companions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent(),
                )
            }
        }

        /** 17 -> 18 adds multi-character rooms and records the real speaker of AI messages. */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN isGroup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_isGroup ON conversations(isGroup)")
                db.execSQL("ALTER TABLE messages ADD COLUMN senderCompanionId INTEGER")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS conversation_members (
                        conversationId INTEGER NOT NULL,
                        companionId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(conversationId, companionId),
                        FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(companionId) REFERENCES companions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_members_companionId ON conversation_members(companionId)")
            }
        }

        /** 18 -> 19 adds persistent pinning and manual conversation ordering. */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN manualRank INTEGER")
            }
        }
    }

    abstract fun freeTopics(): FreeTopicDao''')
rep(appdb,
'''    abstract fun freeTopics(): FreeTopicDao''',
'''    abstract fun groupMembers(): ConversationMemberDao
    abstract fun freeTopics(): FreeTopicDao''')

cleos = 'app/src/main/java/com/cleo/cleos/CleosApp.kt'
rep(cleos,
'''    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "cleos.db").build()''',
'''    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "cleos.db")
        .addMigrations(AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19)
        .build()''')

daos = 'app/src/main/java/com/cleo/cleos/data/db/Daos.kt'
rep(daos,
'''@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE companionId = :companionId ORDER BY updatedAt DESC")
    fun observeFor(companionId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE companionId = :companionId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestFor(companionId: Long): ConversationEntity?''',
'''@Dao
interface ConversationMemberDao {
    @Query("SELECT * FROM conversation_members WHERE conversationId = :conversationId ORDER BY position, companionId")
    fun observeFor(conversationId: Long): Flow<List<ConversationMemberEntity>>

    @Query("SELECT * FROM conversation_members WHERE conversationId = :conversationId ORDER BY position, companionId")
    suspend fun forConversation(conversationId: Long): List<ConversationMemberEntity>

    @Query("SELECT companionId FROM conversation_members WHERE conversationId = :conversationId ORDER BY position, companionId")
    suspend fun idsFor(conversationId: Long): List<Long>

    @Insert
    suspend fun insertAll(items: List<ConversationMemberEntity>)

    @Query("DELETE FROM conversation_members WHERE conversationId = :conversationId")
    suspend fun deleteFor(conversationId: Long)

    @Query("SELECT * FROM conversation_members")
    suspend fun all(): List<ConversationMemberEntity>

    @Query("DELETE FROM conversation_members")
    suspend fun clear()
}

@Dao
interface ConversationDao {
    @Query("SELECT DISTINCT c.* FROM conversations c LEFT JOIN conversation_members gm ON gm.conversationId = c.id WHERE c.companionId = :companionId OR gm.companionId = :companionId ORDER BY c.pinned DESC, COALESCE(c.manualRank, c.updatedAt) DESC, c.id DESC")
    fun observeFor(companionId: Long): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE companionId = :companionId AND isGroup = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestFor(companionId: Long): ConversationEntity?''')
rep(daos,
'''    @Query("SELECT id FROM conversations WHERE companionId = :companionId")
    suspend fun idsFor(companionId: Long): List<Long>''',
'''    @Query("SELECT id FROM conversations WHERE companionId = :companionId AND isGroup = 0")
    suspend fun idsFor(companionId: Long): List<Long>

    @Query("SELECT * FROM conversations WHERE companionId = :companionId AND isGroup = 1")
    suspend fun groupsOwnedBy(companionId: Long): List<ConversationEntity>

    @Query("UPDATE conversations SET companionId = :companionId WHERE id = :id")
    suspend fun reassignCompanion(id: Long, companionId: Long)''')
rep(daos,
'''    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)
''',
'''    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE conversations SET pinned = :pinned, manualRank = :rank WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, rank: Long)

    @Query("UPDATE conversations SET manualRank = :rank WHERE id = :id")
    suspend fun setManualRank(id: Long, rank: Long)
''')

rep(daos,
'''interface MessageDao {
    @Query("SELECT MAX(m.createdAt) FROM messages m JOIN conversations c ON c.id = m.conversationId WHERE c.companionId = :companionId AND m.role = 'user' AND m.note IS NULL")''',
'''interface MessageDao {
    @Query("SELECT m.id AS id, m.conversationId AS conversationId, c.title AS conversationTitle, c.companionId AS ownerCompanionId, m.senderCompanionId AS senderCompanionId, m.role AS role, m.content AS content, m.createdAt AS createdAt FROM messages m JOIN conversations c ON c.id = m.conversationId WHERE m.conversationId != :excludeConversationId AND m.role IN ('user','assistant') AND m.note IS NULL AND m.error IS NULL AND m.content != '' ORDER BY m.createdAt DESC, m.id DESC LIMIT :limit")
    suspend fun sharedRecent(excludeConversationId: Long, limit: Int): List<SharedMessageRow>

    @Query("SELECT m.id AS id, m.conversationId AS conversationId, c.title AS conversationTitle, c.companionId AS ownerCompanionId, m.senderCompanionId AS senderCompanionId, m.role AS role, m.content AS content, m.createdAt AS createdAt FROM messages m JOIN conversations c ON c.id = m.conversationId WHERE m.conversationId != :excludeConversationId AND m.role IN ('user','assistant') AND m.note IS NULL AND m.error IS NULL AND m.content != '' AND (c.companionId = :companionId OR m.senderCompanionId = :companionId OR EXISTS (SELECT 1 FROM conversation_members gm WHERE gm.conversationId = c.id AND gm.companionId = :companionId)) ORDER BY m.createdAt DESC, m.id DESC LIMIT :limit")
    suspend fun sharedForCompanion(excludeConversationId: Long, companionId: Long, limit: Int): List<SharedMessageRow>

    @Query("SELECT MAX(m.createdAt) FROM messages m JOIN conversations c ON c.id = m.conversationId WHERE c.companionId = :companionId AND m.role = 'user' AND m.note IS NULL")''')

prompt = 'app/src/main/java/com/cleo/cleos/ai/Prompt.kt'
rep(prompt,
'''        outside: List<McpTool> = emptyList(),
        stickers: List<StickerEntity> = emptyList(),
    ): String = buildList {''',
'''        outside: List<McpTool> = emptyList(),
        stickers: List<StickerEntity> = emptyList(),
        extraContext: String? = null,
    ): String = buildList {''')
rep(prompt,
'''        if (ToolGroup.Memory in tools) MemoryDigest.forChat(memories, zone)?.let(::add)
        Recap.forChat(recap)?.let(::add)
    }.joinToString("\\n\\n")''',
'''        if (ToolGroup.Memory in tools) MemoryDigest.forChat(memories, zone)?.let(::add)
        Recap.forChat(recap)?.let(::add)
        extraContext?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
    }.joinToString("\\n\\n")''')
rep(prompt,
'''        call: Long? = null,
        calls: Map<Long, CallRecord> = emptyMap(),
    ): List<ApiMessage> {''',
'''        call: Long? = null,
        calls: Map<Long, CallRecord> = emptyMap(),
        extraContext: String? = null,
    ): List<ApiMessage> {''')
rep(prompt,
'''        return listOf(ApiMessage("system", system(settings, ta, tools, memories, now.zone, recap, outside, offered))) + merged''',
'''        return listOf(ApiMessage("system", system(settings, ta, tools, memories, now.zone, recap, outside, offered, extraContext))) + merged''')

print('apply_group_chats_core.py applied')
