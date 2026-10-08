#!/usr/bin/env python3
"""Persist immutable group mention IDs; retry group AI replies without losing the original."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
base = "app/src/main/java/com/cleo/cleos/"

def replace(path, before, after, expected=1):
    f = root / path
    s = f.read_text(encoding="utf-8")
    n = s.count(before)
    if n != expected:
        raise SystemExit(f"{path}: expected {expected} matches, got {n}: {before[:135]!r}")
    f.write_text(s.replace(before,after),encoding="utf-8")

replace("app/build.gradle.kts", "versionCode = 62043", "versionCode = 62044")
replace(base+"data/db/Entities.kt",
'''    val senderCompanionId: Long? = null,
)''',
'''    val senderCompanionId: Long? = null,
    /** Immutable selected @ targets. Old messages retain legacy text-only resolution. */
    @ColumnInfo(defaultValue = "''") val mentionIds: String = "",
)''')
replace(base+"data/db/AppDatabase.kt", "    version = 20,", "    version = 21,")
replace(base+"data/db/AppDatabase.kt",
'''                db.execSQL("ALTER TABLE companions ADD COLUMN speechVoiceOverride TEXT")
            }
        }
    }''',
'''                db.execSQL("ALTER TABLE companions ADD COLUMN speechVoiceOverride TEXT")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mentionIds TEXT NOT NULL DEFAULT ''")
            }
        }
    }''')
replace(base+"CleosApp.kt",
"AppDatabase.MIGRATION_19_20)\n        .build()",
"AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21)\n        .build()")
replace(base+"ai/ChatRepository.kt",
'''    fun send(conversationId: Long, text: String, pictures: List<MessageImage> = emptyList(), quote: MessageQuote? = null): Boolean {''',
'''    fun send(
        conversationId: Long,
        text: String,
        pictures: List<MessageImage> = emptyList(),
        quote: MessageQuote? = null,
        mentionIds: Set<Long> = emptySet(),
    ): Boolean {''')
replace(base+"ai/ChatRepository.kt",
'''                    images = MessageImages.encode(pictures),
                    quote = quote?.let(MessageQuotes::encode),
                ),''',
'''                    images = MessageImages.encode(pictures),
                    quote = quote?.let(MessageQuotes::encode),
                    mentionIds = mentionIds.filter { it > 0 }.distinct().take(6).joinToString(","),
                ),''')
replace(base+"ai/ChatRepository.kt",
'''        val mentions = GroupChats.mentioned(latestText, members)''',
'''        val mentions = GroupChats.targeted(latestText, trigger?.mentionIds, members)''')
replace(base+"ai/ChatRepository.kt",
'''            db.messages().delete(assistantMessageId)
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
'''            if (!convo.isGroup) {
                db.messages().delete(assistantMessageId)
                reply(conversationId)
            } else {
                val members = db.groupMembers().idsFor(conversationId).mapNotNull { companions.get(it) }
                val speaker = members.firstOrNull { it.id == row.senderCompanionId } ?: return@start
                // Preserve the original if the speaker has left, is muted, has no key or budget,
                // or the replacement is SKIP or an error.
                if (speaker.id in convo.groupMutedIds.split(",").mapNotNull { it.toLongOrNull() }) return@start
                if (secrets.key(speaker.apiBaseUrl).isNullOrBlank()) return@start
                if (db.conversations().claimGroupCall(conversationId, java.time.LocalDate.now().toEpochDay()) == 0) return@start
                val history = Recap.sent(recaps.live(convo), settings.current().historySize).filterNot { it.id == row.id }
                val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }
                val shaped = GroupChats.historyFor(history, speaker.id, names, convo.companionId)
                val query = history.lastOrNull { it.role == "user" }?.content.orEmpty()
                if (groupTurn(conversationId, speaker, members, shaped, worldContext(conversationId, query, settings.current()), targeted = true)) {
                    val last = db.messages().newest(conversationId, 1).firstOrNull()
                    if (last != null && last.id != assistantMessageId && last.role == "assistant" &&
                        last.error == null && last.senderCompanionId == speaker.id && last.content.isNotBlank()) {
                        db.messages().delete(assistantMessageId)
                    }
                }
            }''')
replace(base+"ui/chat/ChatViewModel.kt",
'''    fun send(text: String): Boolean {
        val id = conversationId.value ?: return false
        if (!c.chat.send(id, text, attachments.toList(), quoting)) return false''',
'''    fun send(text: String, mentionIds: Set<Long> = emptySet()): Boolean {
        val id = conversationId.value ?: return false
        if (!c.chat.send(id, text, attachments.toList(), quoting, mentionIds)) return false''')
print("Stable @ ID storage and lossless group retry applied.")
