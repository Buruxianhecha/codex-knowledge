#!/usr/bin/env python3
"""Stable companion-ID mentions and fail-safe group retry; apply after group-experience UI."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
def rep(path, before, after, count=1):
    p=root/path
    s=p.read_text(encoding="utf-8")
    n=s.count(before)
    if n!=count:
        raise SystemExit(f"{path}: expected {count}, got {n} for {before[:110]!r}")
    p.write_text(s.replace(before,after),encoding="utf-8")

base="app/src/main/java/com/cleo/cleos/"
ent=base+"data/db/Entities.kt"
db=base+"data/db/AppDatabase.kt"
app=base+"CleosApp.kt"
chat=base+"ai/ChatRepository.kt"

rep("app/build.gradle.kts", "versionCode = 62043", "versionCode = 62044")
rep(ent,
'''    /** Which TA actually said an assistant message. Null means the conversation's legacy owner. */
    val senderCompanionId: Long? = null,
)''',
'''    /** Which TA actually said an assistant message. Null means the conversation's legacy owner. */
    val senderCompanionId: Long? = null,
    /** Stable IDs selected via the group @ picker. Null means an old/free-typed message. */
    val mentionedCompanionIds: String? = null,
)''')
rep(db,"    version = 20,","    version = 21,")
rep(db,
'''        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {''',
'''        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {''')  # schema guard
rep(db,
'''                db.execSQL("ALTER TABLE companions ADD COLUMN speechVoiceOverride TEXT")
            }
        }
    }''',
'''                db.execSQL("ALTER TABLE companions ADD COLUMN speechVoiceOverride TEXT")
            }
        }

        /** Optional stable IDs for explicitly chosen @ members; old rows remain unchanged. */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mentionedCompanionIds TEXT")
            }
        }
    }''')
rep(app,
'''AppDatabase.MIGRATION_19_20)
        .build()''',
'''AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21)
        .build()''')

# Validate chosen entries against the actual text and then save only immutable member IDs.
rules=base+"ai/GroupChats.kt"
rep(rules,
'''    fun targeted(text: String, storedIds: String?, members: List<CompanionEntity>): List<CompanionEntity> {''',
'''    fun selectedMentionIds(text: String, chosen: Map<Long, String>): Set<Long> =
        chosen.filter { (_, visibleName) -> containsMention(text, visibleName) }.keys

    fun targeted(text: String, storedIds: String?, members: List<CompanionEntity>): List<CompanionEntity> {''')
rep(chat,
'''    fun send(conversationId: Long, text: String, pictures: List<MessageImage> = emptyList(), quote: MessageQuote? = null): Boolean {''',
'''    fun send(conversationId: Long, text: String, pictures: List<MessageImage> = emptyList(),
        quote: MessageQuote? = null, mentions: Map<Long, String> = emptyMap()): Boolean {''')
rep(chat,
'''        val at = stamp()
        scope.launch {
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "user",
                    content = content,
                    createdAt = at,
                    images = MessageImages.encode(pictures),
                    quote = quote?.let(MessageQuotes::encode),
                ),
            )''',
'''        val at = stamp()
        scope.launch {
            val convoForMentions = db.conversations().get(conversationId)
            val verified = if (convoForMentions?.isGroup == true) {
                val current = db.groupMembers().idsFor(conversationId).toSet()
                GroupChats.selectedMentionIds(content, mentions).filter { it in current }.sorted()
            } else emptyList()
            db.messages().insert(
                MessageEntity(
                    conversationId = conversationId,
                    role = "user",
                    content = content,
                    createdAt = at,
                    images = MessageImages.encode(pictures),
                    quote = quote?.let(MessageQuotes::encode),
                    mentionedCompanionIds = verified.takeIf { it.isNotEmpty() }?.joinToString(","),
                ),
            )''')
rep(chat,
'''        val mentions = GroupChats.mentioned(latestText, members)''',
'''        val mentions = GroupChats.targeted(latestText, trigger?.mentionedCompanionIds, members)''')

# Group retries preserve original until a valid replacement has been stored and checked.
rep(chat,
'''            val convo = db.conversations().get(conversationId) ?: return@start
            db.messages().delete(assistantMessageId)
            if (!convo.isGroup) {
                reply(conversationId)
            } else {
                val members = db.groupMembers().idsFor(conversationId).mapNotNull { companions.get(it) }
                val speaker = members.firstOrNull { it.id == row.senderCompanionId } ?: return@start
                val history = Recap.sent(recaps.live(convo), settings.current().historySize)
                val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }
                val shaped = GroupChats.historyFor(history, speaker.id, names, convo.companionId)
                val query = history.lastOrNull { it.role == "user" }?.content.orEmpty()
                if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) != 0) {
                    groupTurn(conversationId, speaker, members, shaped, worldContext(conversationId, query, settings.current()), targeted = true)
                }
            }''',
'''            val convo = db.conversations().get(conversationId) ?: return@start
            if (!convo.isGroup) {
                db.messages().delete(assistantMessageId)
                reply(conversationId)
            } else {
                // Keep the original until a complete replacement is actually on disk.
                val members = db.groupMembers().idsFor(conversationId).mapNotNull { companions.get(it) }
                val speaker = members.firstOrNull { it.id == row.senderCompanionId } ?: return@start
                if (secrets.key(speaker.apiBaseUrl).isNullOrBlank()) return@start
                if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) == 0) return@start
                val history = Recap.sent(recaps.live(convo), settings.current().historySize)
                    .filterNot { it.id == row.id }
                val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }
                val shaped = GroupChats.historyFor(history, speaker.id, names, convo.companionId)
                val query = history.lastOrNull { it.role == "user" }?.content.orEmpty()
                val priorIds = db.messages().newest(conversationId, 16).map { it.id }.toSet()
                val produced = groupTurn(conversationId, speaker, members, shaped,
                    worldContext(conversationId, query, settings.current()), targeted = true)
                if (produced) {
                    val replacement = db.messages().newest(conversationId, 16).any {
                        it.id !in priorIds && it.role == "assistant" &&
                        it.senderCompanionId == speaker.id && it.error == null &&
                        it.content.isNotBlank()
                    }
                    if (replacement) db.messages().delete(row.id)
                }
            }''')
print("stable @ IDs, room migration 20->21 and safe group retry patched")
