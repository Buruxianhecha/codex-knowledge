package com.cleo.cleos.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.cleo.cleos.data.db.AppDatabase
import com.cleo.cleos.data.db.CompanionEntity
import com.cleo.cleos.data.db.ConversationEntity
import com.cleo.cleos.data.db.ConversationMemberEntity
import com.cleo.cleos.data.db.DiaryEntryEntity
import com.cleo.cleos.ai.ReplyWhen
import com.cleo.cleos.ai.Speech
import com.cleo.cleos.data.db.LetterEntity
import com.cleo.cleos.data.db.MemoryEntity
import com.cleo.cleos.data.db.MessageEntity
import com.cleo.cleos.data.db.StickerEntity
import com.cleo.cleos.data.db.TodoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The settings in a backup. apiBaseUrl through knownSince described the one TA of before
 * there could be several; a newer backup fills them from the first TA and restores all of
 * them from [BackupFile.companions].
 */
@Serializable
data class BackupSettings(
    val apiBaseUrl: String,
    val apiModel: String,
    val aiName: String,
    val userName: String,
    val persona: String,
    val historySize: Int,
    val wallpaper: String?,
    val glassMode: String,
    val wallpaperDark: Boolean?,
    val wallpaperHue: Float?,
    val wallpaperChroma: Float?,
    val wallpaperTrough: Float? = null,
    val wallpaperPeak: Float? = null,
    /** JSON as SettingsRepository stores it; absent in backups from before the glass lab could apply. */
    val glassTuning: String? = null,
    /** Tool groups by name, comma-separated; absent in backups from before tools. */
    val tools: String? = null,
    val weatherCity: String = "",
    /** File names under images/; absent in backups from before the home page. */
    val userAvatar: String? = null,
    val aiAvatar: String? = null,
    val aiAvatarEmoji: String? = null,
    val chatAvatars: Boolean = true,
    val avatarEachMessage: Boolean = false,
    val myBubble: Int? = null,
    val knownSince: Long? = null,
    /** Absent from backups made before these could be set: the defaults then. */
    val letterReply: String? = null,
    val letterEveryDays: Int = 5,
    val voiceBaseUrl: String = "",
    val voiceModel: String = "",
    /** "api" for any OpenAI-shaped service in backups from before each had its own place (Speech.migrate). */
    val speechEngine: String = "",
    val speechVoices: Map<String, String> = emptyMap(),
    val minimaxGlobal: Boolean = false,
    val speechBaseUrl: String = "",
    val speechModel: String = "",
    val speechVoice: String = "",
    val elevenVoice: String = "",
    val elevenModel: String = "",
    val earVoice: Boolean = true,
)

/** The backup format: one zip, `backup.json` plus the pictures under `images/`. */
@Serializable
data class BackupFile(
    // No defaults on decode: a file without these is not one of ours.
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val settings: BackupSettings,
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val diary: List<DiaryEntryEntity>,
    val todos: List<TodoEntity>,
    /** Absent in backups from before there could be several TAs. */
    val companions: List<CompanionEntity> = emptyList(),
    /** Absent in backups from before there were letters. */
    val letters: List<LetterEntity> = emptyList(),
    /** Absent in backups from before TAs kept memories. */
    val memories: List<MemoryEntity> = emptyList(),
    /** Absent in backups from before there were stickers; their pictures are under `images/` with the rest. */
    val stickers: List<StickerEntity> = emptyList(),
    /** Absent before 0.37.22; old backups simply contain no group chats. */
    val groupMembers: List<ConversationMemberEntity> = emptyList(),
    /** Absent before Moments existed. Posts and comments are private local data. */
    val moments: MomentsSnapshot? = null,
    /** A coordinated pair; absent in old backups preserves current wallet/life data. */
    val life: LifeSnapshot? = null,
    val wallet: WalletBook? = null,
) {
    companion object {
        const val FORMAT = "cleos-backup"
        const val VERSION = 1
    }
}

data class BackupSummary(
    val tas: Int,
    val conversations: Int,
    val messages: Int,
    val diary: Int,
    val letters: Int,
    val todos: Int,
    val images: Int,
    val voices: Int = 0,
    val stickers: Int = 0,
    val credentials: Int? = null,
    val skippedEmptyConversations: Int = 0,
) {
    override fun toString() =
        "$tas 个 TA、$conversations 段对话（$messages 条消息）、$diary 篇日记、$letters 封信、$todos 条待办、$images 张图" +
            (if (voices > 0) "、$voices 段语音" else "") +
            (if (stickers > 0) "、$stickers 个表情包" else "") +
            (credentials?.let { "、全部配置（$it 项已保存凭据）" } ?: "") +
            (if (skippedEmptyConversations > 0) "；安全跳过 $skippedEmptyConversations 段角色已删除且无消息的空会话" else "")
}


/** Media belonging to actual Moments posts must travel together with the backup. */
private fun MomentsSnapshot?.orEmptyPhotos(): List<String> =
    (this?.posts?.flatMap { it.photos } ?: emptyList()) +
        listOfNotNull(this?.profile?.cover,this?.profile?.avatar) +
        (this?.savedMessages?.mapNotNull { it.audioFile } ?: emptyList())

/** Recordings are named voice_…, among the pictures (VoiceRecorder). */
private const val VOICE_PREFIX = "voice_"

class BackupException(message: String) : Exception(message)

/**
 * Full user-requested backup: credentials are decrypted only for the portable archive,
 * and encrypted using the receiving installation's Keystore on restore.
 *
 * Validate before mutation, keep a full undo snapshot, and roll back both DataStores
 * when the Room transaction or either configuration write fails.
 */
class BackupService(
    context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val images: ImageStore,
    private val secrets: SecretStore,
    private val life: LifeStore? = null,
    private val wallet: VirtualWalletStore? = null,
) {
    private val lock = Mutex()
    private val resolver = context.contentResolver
    private val cacheDir = context.cacheDir
    private val snapshot = File(context.filesDir, "backups/before-restore.zip")
    private val momentsFile = File(context.filesDir, "moments-v1.json")
    // encodeDefaults: format and version have default values, and without this they are
    // silently left out of the file, which makes the "is this our backup, which version"
    // check on restore pass for anything.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private suspend fun restoreLifePair(data: BackupFile, block: suspend () -> Unit) {
        val store = life
        if (data.life == null && data.wallet == null) {
            if (store != null && wallet != null) store.restoreBackup(null, null, block = block)
                else block()
            return
        }
        if (data.life == null || data.wallet == null || store == null || wallet == null)
            throw BackupException("生活与钱包备份必须成对恢复，请使用完整备份和当前版本应用")
        store.restoreBackup(data.life, data.wallet, block = block)
    }

    val hasSnapshot: Boolean get() = snapshot.exists()

    suspend fun export(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        lock.withLock {
            val out = resolver.openOutputStream(uri) ?: throw BackupException("打不开要保存的位置")
            out.use { write(it) }
        }
    }

    suspend fun restore(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        lock.withLock {
            val input = resolver.openInputStream(uri) ?: throw BackupException("打不开这个文件")
            input.use { restoreFrom(it, takeSnapshot = true) }
        }
    }

    /** Undo restores settings and credentials as well as conversations. */
    suspend fun undoRestore(): BackupSummary = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!snapshot.exists()) throw BackupException("没有可以撤销的恢复")
            val summary = snapshot.inputStream().use { restoreFrom(it, takeSnapshot = false) }
            snapshot.delete()
            summary
        }
    }

    private suspend fun write(raw: OutputStream): BackupSummary {
        val configuration = ConfigurationBackup(
            ConfigurationBackup.FORMAT, ConfigurationBackup.VERSION,
            settings.backupPreferences(), secrets.backupSecrets(),
        )
        val configurationBytes = ConfigurationCodec.encode(configuration)
        val s = settings.current()
        val companions = db.companions().all()
        val exportMessages = db.messages().all()
        val exportMembers = db.groupMembers().all()
        val originalConversations = db.conversations().all()
        val exportConversations = BackupArchiveGuard.cleanEmptyOrphanConversations(
            companions.map { it.id }.ifEmpty { listOf(Companions.FIRST) },
            originalConversations, exportMembers, exportMessages,
        )
        BackupArchiveGuard.validateRows(
            companions.map { it.id }.ifEmpty { listOf(Companions.FIRST) },
            exportConversations, exportMembers, exportMessages,
        )
        val skippedOnExport = originalConversations.size - exportConversations.size
        val lead = companions.firstOrNull()
        val pair = life?.backupSnapshots()
        val data = BackupFile(
            format = BackupFile.FORMAT,
            version = BackupFile.VERSION,
            exportedAt = System.currentTimeMillis(),
            settings = BackupSettings(
                apiBaseUrl = lead?.apiBaseUrl ?: ApiPresets.DeepSeek.baseUrl,
                apiModel = lead?.apiModel ?: ApiPresets.DeepSeek.defaultModel,
                aiName = lead?.name.orEmpty(),
                userName = s.userName,
                persona = lead?.persona.orEmpty(),
                historySize = s.historySize,
                wallpaper = s.wallpaper,
                glassMode = s.glassMode.name,
                wallpaperDark = s.wallpaperDark,
                wallpaperHue = s.wallpaperHue,
                wallpaperChroma = s.wallpaperChroma,
                wallpaperTrough = s.wallpaperTrough,
                wallpaperPeak = s.wallpaperPeak,
                glassTuning = s.glassTuning.takeIf { it.isNotEmpty() }?.let { encodeTuning(it) },
                tools = encodeTools(s.tools),
                weatherCity = s.weatherCity,
                userAvatar = s.userAvatar,
                aiAvatar = lead?.avatar,
                aiAvatarEmoji = lead?.avatarEmoji,
                chatAvatars = s.chatAvatars,
                avatarEachMessage = s.avatarEachMessage,
                myBubble = s.myBubble,
                letterReply = s.letterReply.key,
                letterEveryDays = s.letterEveryDays,
                voiceBaseUrl = s.voiceBaseUrl,
                voiceModel = s.voiceModel,
                speechEngine = s.speechEngine,
                speechVoices = s.speechVoices,
                minimaxGlobal = s.minimaxGlobal,
                speechBaseUrl = s.speechBaseUrl,
                speechModel = s.speechModel,
                speechVoice = s.speechVoice,
                elevenVoice = s.elevenVoice,
                elevenModel = s.elevenModel,
                earVoice = s.earVoice,
                knownSince = lead?.knownSince,
            ),
            conversations = exportConversations,
            messages = exportMessages,
            diary = db.diary().all(),
            todos = db.todos().all(),
            companions = companions,
            letters = db.letters().all(),
            memories = db.memories().all(),
            stickers = db.stickers().all(),
            groupMembers = exportMembers,
            moments = if (momentsFile.exists()) runCatching {
                json.decodeFromString<MomentsSnapshot>(momentsFile.readText())
            }.getOrElse { throw BackupException("朋友圈本地数据损坏，无法生成安全备份") }
            else MomentsSnapshot(),
            life = pair?.first,
            wallet = pair?.second,
        )
        val stickerFiles = data.stickers.map { it.file }.toSet()
        val pictures = (data.diary.flatMap { e -> DiaryBlocks.images(DiaryBlocks.decode(e.blocks)).map { it.file } } +
            data.messages.flatMap { m -> MessageImages.decode(m.images).map { it.file } } +
            // Recordings live with the pictures and travel the same way.
            data.messages.mapNotNull { m -> MessageAudios.decode(m.audio)?.file } +
            companions.mapNotNull { it.avatar } +
            stickerFiles +
            listOfNotNull(s.wallpaper, s.userAvatar) +
            s.displayFonts.map { it.file } +
            data.moments.orEmptyPhotos()).toSet()

        var written = 0
        var voices = 0
        var stickers = 0
        ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_NAME))
            zip.write(json.encodeToString(data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ConfigurationBackup.ENTRY))
            zip.write(configurationBytes)
            zip.closeEntry()
            for (name in pictures) {
                val f = images.file(name)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry("images/$name"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                when {
                    DisplayFonts.isFontName(name) -> Unit // Font files are not counted as pictures.
                    name.startsWith(VOICE_PREFIX) -> voices++
                    name in stickerFiles -> stickers++
                    else -> written++
                }
            }
        }
        return BackupSummary(
            companions.size, data.conversations.size, data.messages.size, data.diary.size, data.letters.size, data.todos.size,
            written, voices, stickers, credentials = configuration.secrets.size,
            skippedEmptyConversations = skippedOnExport,
        )
    }

    private suspend fun restoreFrom(input: InputStream, takeSnapshot: Boolean): BackupSummary {
        val staging = File(cacheDir, "restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            var data: BackupFile? = null
            var configuration: ConfigurationBackup? = null
            val mediaNames = HashSet<String>()
            var entries = 0
            var uncompressed = 0L
            fun countUncompressed(bytes: Int) {
                uncompressed += bytes.toLong()
                if (uncompressed > BackupArchiveGuard.MAX_TOTAL_BYTES)
                    throw BackupException("备份超过可恢复的总大小限制")
            }
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > BackupArchiveGuard.MAX_ENTRIES) throw BackupException("备份包含太多文件，没有恢复")
                    when {
                        entry.name == JSON_NAME -> {
                            if (data != null) throw BackupException("备份包含重复的数据，没有恢复")
                            data = runCatching { json.decodeFromString<BackupFile>(
                                    BackupArchiveGuard.readLimited(zip, BackupArchiveGuard.MAX_JSON_BYTES, ::countUncompressed).decodeToString()
                                ) }
                                .getOrElse { throw BackupException("备份文件坏了，读不出来") }
                        }
                        entry.name == ConfigurationBackup.ENTRY -> {
                            if (configuration != null) throw BackupException("备份包含重复的配置，没有恢复")
                            configuration = ConfigurationCodec.decode(
                                ByteArrayInputStream(BackupArchiveGuard.readLimited(
                                    zip, BackupArchiveGuard.MAX_CONFIG_BYTES, ::countUncompressed,
                                )),
                            )
                        }
                        entry.name.startsWith("images/") && !entry.isDirectory -> {
                            // Only the bare file name is used: a crafted archive with
                            // "images/../../somewhere" must not write outside the staging dir.
                            val name = BackupArchiveGuard.imageName(entry.name)
                            if (!mediaNames.add(name)) throw BackupException("备份包含重复图片或音频，没有恢复")
                            File(staging, name).outputStream().use { output ->
                                BackupArchiveGuard.copyLimited(zip, output, BackupArchiveGuard.MAX_MEDIA_BYTES, ::countUncompressed)
                            }
                        }
                        entry.isDirectory -> Unit
                        else -> throw BackupException("备份包含无法识别的文件，没有恢复")
                    }
                }
            }
            val original = data ?: throw BackupException("这不是怀民亦未寝的备份文件")
            if (original.format != BackupFile.FORMAT) throw BackupException("这不是怀民亦未寝的备份文件")
            if (original.version > BackupFile.VERSION) throw BackupException("这份备份来自更新版本的怀民亦未寝，先更新 App 再恢复")
            // Only discard obsolete single-chat shells with no messages,
            // members or pin. Any real user content still blocks restoration.
            val roleIds = original.companions.map { it.id }.ifEmpty { listOf(Companions.FIRST) }
            val conversations = BackupArchiveGuard.cleanEmptyOrphanConversations(
                roleIds, original.conversations, original.groupMembers, original.messages,
            )
            val skippedOnRestore = original.conversations.size - conversations.size
            val d = original.copy(conversations = conversations)
            BackupArchiveGuard.validateRows(roleIds, d.conversations, d.groupMembers, d.messages)
            if ((d.life == null) != (d.wallet == null))
                throw BackupException("生活与钱包备份数据不完整，没有恢复")
            if (d.life != null && d.wallet != null) {
                if (life == null || wallet == null)
                    throw BackupException("当前版本未初始化生活与钱包，无法恢复这份备份")
                try {
                    life.validateSnapshot(d.life)
                    wallet.validateSnapshot(d.wallet)
                    require(d.life.profiles.values.filter { it.enabled }.all { it.companionId in roleIds })
                    val rooms = d.conversations.associateBy { it.id }
                    val groupMembers = d.groupMembers.groupBy { it.conversationId }
                    for (profile in d.life.profiles.values) {
                        for (pending in profile.pending) {
                            val room = rooms[pending.conversationId] ?: error("待处理会话缺失")
                            require(if (room.isGroup) groupMembers[room.id].orEmpty()
                                .any { it.companionId == profile.companionId }
                                else room.companionId == profile.companionId)
                            if (pending.purpose == "reply" && pending.messageId > 0L)
                                require(d.messages.any { it.id == pending.messageId && it.conversationId == room.id })
                        }
                    }
                } catch (error: Exception) {
                    throw BackupException("生活、工资或待回复消息备份校验失败，没有恢复")
                }
            }
            val incomingMoments = d.moments ?: MomentsSnapshot()
            if (incomingMoments.version != 1 || incomingMoments.posts.size > 50_000 ||
                incomingMoments.posts.map { it.id }.distinct().size != incomingMoments.posts.size)
                throw BackupException("朋友圈备份数据格式无效")
            incomingMoments.posts.forEach { post ->
                try {
                    MomentsRules.validatePost(post.text, post.photos.size)
                    require(post.photos.all(MomentsRules::validImageName))
                    require(post.comments.size <= 10000)
                    post.comments.forEach { MomentsRules.validateReply(it.text) }
                } catch (e: Exception) {
                    throw BackupException("朋友圈动态或评论数据不合法，已停止恢复")
                }
            }

            val fullConfiguration = configuration?.also {
                it.validate(settings.backupPreferenceTypes)
            }
            // A missing entry is an old backup: keep the current credentials unchanged.
            val encryptedSecrets = secrets.prepareBackupSecrets(fullConfiguration?.secrets)

            if (takeSnapshot) {
                snapshot.parentFile?.mkdirs()
                val tmp = File(snapshot.path + ".tmp")
                try {
                    tmp.outputStream().use { write(it) }
                    if (!tmp.renameTo(snapshot)) throw BackupException("无法保存恢复前的备份，没有恢复")
                } finally {
                    tmp.delete()
                }
            }

            val pictures = staging.listFiles().orEmpty()
            // A same-named file must not silently resolve to unrelated media on this device.
            for (f in pictures) {
                val dest = images.file(f.name)
                if (dest.exists() && !BackupArchiveGuard.sameFileContent(f, dest))
                    throw BackupException("备份媒体文件与本机文件同名但内容不同，已停止恢复")
            }
            for (f in pictures) {
                val dest = images.file(f.name)
                if (!dest.exists()) f.copyTo(dest)
            }
            if(incomingMoments.profile.name.length > 32 || incomingMoments.profile.bio.length > 200)
                throw BackupException("朋友圈主页资料过长")
            if(incomingMoments.savedMessages.size > 20000 ||
                incomingMoments.ai.size > 200)
                throw BackupException("朋友圈收藏或 AI 配置数量异常")
            incomingMoments.savedMessages.forEach { item ->
                if(item.text.length > 50000 || item.author.length > 100 ||
                    (item.audioFile != null && !MomentsRules.validMediaName(item.audioFile)))
                    throw BackupException("收藏语音或文字格式不安全")
            }
            incomingMoments.orEmptyPhotos().forEach { name ->
                if (!images.file(name).exists()) throw BackupException("朋友圈照片缺失，已停止恢复")
            }
            val bs = d.settings
            fun picture(name: String?) = name?.takeIf {
                it.isNotBlank() && '/' !in it && '\\' !in it && !it.startsWith(".") && images.file(it).exists()
            }
            // A backup from before there could be several TAs has one, described in its settings.
            val companions = d.companions.ifEmpty {
                listOf(
                    CompanionEntity(
                        id = Companions.FIRST,
                        name = bs.aiName,
                        persona = bs.persona,
                        apiBaseUrl = bs.apiBaseUrl,
                        apiModel = bs.apiModel,
                        avatar = bs.aiAvatar,
                        avatarEmoji = bs.aiAvatarEmoji,
                        knownSince = bs.knownSince,
                        createdAt = d.exportedAt,
                    ),
                )
            }.map { it.copy(avatar = picture(it.avatar)) }
            // TA entries in such a backup carry no owner: they were all TA 1's.
            val diary = d.diary.map {
                if (it.author == DiaryEntryEntity.AUTHOR_AI && it.companionId == null) it.copy(companionId = Companions.FIRST) else it
            }
            // A sticker is its picture: one whose file didn't come along would be a name drawn as nothing.
            val stickers = d.stickers.filter { images.file(it.file).exists() }
            val restoredPreferences = fullConfiguration?.restoredPreferences(
                companions.map { it.id },
                d.conversations.associate { it.id to it.companionId },
                { name ->
                    if (DisplayFonts.isFontName(name)) runCatching { displayTypeface(images.file(name)) }.isSuccess
                    else picture(name) != null
                },
                portableRestore = takeSnapshot,
            )
            val previousMoments = momentsFile.takeIf { it.exists() }?.readBytes()
            var momentsChanged = false
            try {
            withConfigurationRollback(
                settings::backupPreferences, secrets::encryptedBackupState,
                settings::replaceBackupPreferences, secrets::replaceEncryptedBackupState,
            ) {
                db.withTransaction {
                    restoreLifePair(d) {
                    // What TAs noted to come back to, and what came of it: not in backups, and about
                    // conversations that are about to go.
                    db.later().clear()
                    db.wakes().clear()
                    db.messages().clear()
                    db.groupMembers().clear()
                    db.conversations().clear()
                    db.diary().clear()
                    db.todos().clear()
                    db.letters().clear()
                    db.memories().clear()
                    db.stickers().clear()
                    db.companions().clear()
                    db.companions().insertAll(companions)
                    db.conversations().insertAll(d.conversations)
                    db.groupMembers().insertAll(d.groupMembers)
                    db.messages().insertAll(d.messages)
                    db.diary().insertAll(diary)
                    db.todos().insertAll(d.todos)
                    db.letters().insertAll(d.letters)
                    db.memories().insertAll(d.memories)
                    db.stickers().insertAll(stickers)

                    if (restoredPreferences != null) {
                        settings.replaceBackupPreferences(restoredPreferences)
                        secrets.replaceEncryptedBackupState(checkNotNull(encryptedSecrets))
                    } else {
                        // A backup from before each voice service had its own place says "api" for all of them.
                        val (speechEngine, speechVoices) = Speech.migrate(bs.speechEngine, bs.speechBaseUrl, bs.speechVoice, bs.speechVoices)
                        settings.update {
                            it.copy(
                                userName = bs.userName,
                                historySize = bs.historySize,
                                wallpaper = bs.wallpaper?.takeIf { name -> images.file(name).exists() },
                                glassMode = runCatching { GlassMode.valueOf(bs.glassMode) }.getOrDefault(GlassMode.Auto),
                                wallpaperDark = bs.wallpaperDark,
                                wallpaperHue = bs.wallpaperHue,
                                wallpaperChroma = bs.wallpaperChroma,
                                wallpaperTrough = bs.wallpaperTrough,
                                wallpaperPeak = bs.wallpaperPeak,
                                glassTuning = decodeTuning(bs.glassTuning),
                                tools = bs.tools?.let(::decodeTools) ?: AppSettings().tools,
                                weatherCity = bs.weatherCity,
                                userAvatar = picture(bs.userAvatar),
                                chatAvatars = bs.chatAvatars,
                                avatarEachMessage = bs.avatarEachMessage,
                                myBubble = bs.myBubble,
                                letterReply = ReplyWhen.of(bs.letterReply),
                                letterEveryDays = bs.letterEveryDays,
                                voiceBaseUrl = bs.voiceBaseUrl,
                                voiceModel = bs.voiceModel,
                                speechEngine = speechEngine,
                                speechVoices = speechVoices,
                                minimaxGlobal = bs.minimaxGlobal,
                                speechBaseUrl = bs.speechBaseUrl,
                                speechModel = bs.speechModel,
                                speechVoice = bs.speechVoice,
                                elevenVoice = bs.elevenVoice,
                                elevenModel = bs.elevenModel,
                                earVoice = bs.earVoice,
                            )
                    }
                    settings.setCurrentCompanion(companions.first().id)
                    settings.setCurrentConversation(null)
                    }
                    val momentsTemporary = File(momentsFile.path + ".restore")
                    try {
                        momentsTemporary.writeText(json.encodeToString(incomingMoments))
                        if (!momentsTemporary.renameTo(momentsFile))
                            throw BackupException("无法保存恢复后的朋友圈，仍保留了恢复前的撤销备份")
                    } finally { momentsTemporary.delete() }
                    momentsChanged = true
                    }
                }
            }
            } catch (error: Throwable) {
                if (momentsChanged) {
                    try {
                        if (previousMoments == null) momentsFile.delete()
                        else {
                            val rollback = File(momentsFile.path + ".rollback")
                            try {
                                rollback.writeBytes(previousMoments)
                                if (!rollback.renameTo(momentsFile)) throw BackupException("无法撤销朋友圈恢复")
                            } finally { rollback.delete() }
                        }
                    } catch (rollbackError: Throwable) { error.addSuppressed(rollbackError) }
                }
                throw error
            }
            return BackupSummary(
                companions.size, d.conversations.size, d.messages.size, d.diary.size, d.letters.size, d.todos.size,
                pictures.size - stickers.size, stickers = stickers.size, credentials = fullConfiguration?.secrets?.size,
                skippedEmptyConversations = skippedOnRestore,
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    private companion object {
        const val JSON_NAME = "backup.json"
    }
}
