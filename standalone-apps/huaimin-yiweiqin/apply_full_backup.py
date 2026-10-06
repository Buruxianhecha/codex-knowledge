"""Add full configuration and portable credential backup to the pinned app."""
from pathlib import Path
import re
import shutil
import sys


def apply_full_backup(root: Path):
    here = Path(__file__).resolve().parent
    pending = {}

    def replace(rel: str, old: str, new: str):
        text = pending.get(rel, (root / rel).read_text(encoding="utf-8"))
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{rel}: expected one match, got {count}: {old[:90]!r}")
        pending[rel] = text.replace(old, new, 1)

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""import kotlinx.coroutines.withContext

""".removesuffix("\n"), r"""import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""    val stickers: Int = 0,
) {
""".removesuffix("\n"), r"""    val stickers: Int = 0,
    val credentials: Int? = null,
) {
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""            if (stickers > 0) "、$stickers 个表情包" else ""
""".removesuffix("\n"), r"""            (if (stickers > 0) "、$stickers 个表情包" else "") +
            (credentials?.let { "、全部配置（$it 项已保存凭据）" } ?: "")
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""/**
 * Export and restore. The API key is never written out: it only exists encrypted with a
 * key that cannot leave this phone, and a backup file travels (chat apps, cloud drives).
 *
 * Restore replaces everything, so it is careful about order:
 *  1. read and check the whole file before touching anything;
 *  2. snapshot what is there now (`before-restore.zip`), so a wrong file can be undone;
 *  3. copy pictures in, then swap the database contents in one transaction. If any row
 *     is bad the transaction rolls back and the old data is still there.
 */

""".removesuffix("\n"), r"""/**
 * Full user-requested backup: credentials are decrypted only for the portable archive,
 * and encrypted using the receiving installation's Keystore on restore.
 *
 * Validate before mutation, keep a full undo snapshot, and roll back both DataStores
 * when the Room transaction or either configuration write fails.
 */

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""    private val images: ImageStore,
) {
""".removesuffix("\n"), r"""    private val images: ImageStore,
    private val secrets: SecretStore,
) {
    private val lock = Mutex()
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""    suspend fun export(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val out = resolver.openOutputStream(uri) ?: throw BackupException("打不开要保存的位置")
        out.use { write(it) }
    }

    suspend fun restore(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: throw BackupException("打不开这个文件")
        input.use { restoreFrom(it, takeSnapshot = true) }
    }

    /** Puts back what was there before the last restore. */
    suspend fun undoRestore(): BackupSummary = withContext(Dispatchers.IO) {
        if (!snapshot.exists()) throw BackupException("没有可以撤销的恢复")
        val summary = snapshot.inputStream().use { restoreFrom(it, takeSnapshot = false) }
        snapshot.delete()
        summary
    }


""".removesuffix("\n"), r"""    suspend fun export(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
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


""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""        val s = settings.current()

""".removesuffix("\n"), r"""        val configuration = ConfigurationBackup(
            ConfigurationBackup.FORMAT, ConfigurationBackup.VERSION,
            settings.backupPreferences(), secrets.backupSecrets(),
        )
        val configurationBytes = ConfigurationCodec.encode(configuration)
        val s = settings.current()

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""            zip.closeEntry()
            for (name in pictures) {
""".removesuffix("\n"), r"""            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ConfigurationBackup.ENTRY))
            zip.write(configurationBytes)
            zip.closeEntry()
            for (name in pictures) {
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""            written, voices, stickers,

""".removesuffix("\n"), r"""            written, voices, stickers, credentials = configuration.secrets.size,

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""            var data: BackupFile? = null

""".removesuffix("\n"), r"""            var data: BackupFile? = null
            var configuration: ConfigurationBackup? = null

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""                        entry.name == JSON_NAME -> {

""".removesuffix("\n"), r"""                        entry.name == JSON_NAME -> {
                            if (data != null) throw BackupException("备份包含重复的数据，没有恢复")

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""                        entry.name.startsWith("images/") && !entry.isDirectory -> {
""".removesuffix("\n"), r"""                        entry.name == ConfigurationBackup.ENTRY -> {
                            if (configuration != null) throw BackupException("备份包含重复的配置，没有恢复")
                            configuration = ConfigurationCodec.decode(zip)
                        }
                        entry.name.startsWith("images/") && !entry.isDirectory -> {
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""            if (takeSnapshot) {

""".removesuffix("\n"), r"""            val fullConfiguration = configuration?.also {
                it.validate(settings.backupPreferenceTypes)
            }
            // A missing entry is an old backup: keep the current credentials unchanged.
            val encryptedSecrets = secrets.prepareBackupSecrets(fullConfiguration?.secrets)

            if (takeSnapshot) {

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""                tmp.outputStream().use { write(it) }
                tmp.renameTo(snapshot)

""".removesuffix("\n"), r"""                try {
                    tmp.outputStream().use { write(it) }
                    if (!tmp.renameTo(snapshot)) throw BackupException("无法保存恢复前的备份，没有恢复")
                } finally {
                    tmp.delete()
                }

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""            fun picture(name: String?) = name?.takeIf { images.file(it).exists() }
""".removesuffix("\n"), r"""            fun picture(name: String?) = name?.takeIf {
                it.isNotBlank() && '/' !in it && '\\' !in it && !it.startsWith(".") && images.file(it).exists()
            }
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""            db.withTransaction {
                // What TAs noted to come back to, and what came of it: not in backups, and about
                // conversations that are about to go.
                db.later().clear()
                db.wakes().clear()
                db.messages().clear()
                db.conversations().clear()
                db.diary().clear()
                db.todos().clear()
                db.letters().clear()
                db.memories().clear()
                db.stickers().clear()
                db.companions().clear()
                db.companions().insertAll(companions)
                db.conversations().insertAll(d.conversations)
                db.messages().insertAll(d.messages)
                db.diary().insertAll(diary)
                db.todos().insertAll(d.todos)
                db.letters().insertAll(d.letters)
                db.memories().insertAll(d.memories)
                db.stickers().insertAll(stickers)
            }
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

""".removesuffix("\n"), r"""            val restoredPreferences = fullConfiguration?.restoredPreferences(
                companions.map { it.id },
                d.conversations.associate { it.id to it.companionId },
                { picture(it) != null },
                portableRestore = takeSnapshot,
            )
            withConfigurationRollback(
                settings::backupPreferences, secrets::encryptedBackupState,
                settings::replaceBackupPreferences, secrets::replaceEncryptedBackupState,
            ) {
                db.withTransaction {
                    // What TAs noted to come back to, and what came of it: not in backups, and about
                    // conversations that are about to go.
                    db.later().clear()
                    db.wakes().clear()
                    db.messages().clear()
                    db.conversations().clear()
                    db.diary().clear()
                    db.todos().clear()
                    db.letters().clear()
                    db.memories().clear()
                    db.stickers().clear()
                    db.companions().clear()
                    db.companions().insertAll(companions)
                    db.conversations().insertAll(d.conversations)
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
                }
            }

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/BackupService.kt", r"""                pictures.size - stickers.size, stickers = stickers.size,

""".removesuffix("\n"), r"""                pictures.size - stickers.size, stickers = stickers.size, credentials = fullConfiguration?.secrets?.size,

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/SecretStore.kt", r"""    private fun key(): SecretKey {

""".removesuffix("\n"), r"""    /** Portable values: copying Keystore ciphertext would lose every Key on reinstall. */
    internal suspend fun backupSecrets(): Map<String, String> =
        SecretBackup.decryptAll(encryptedBackupState(), ::decrypt)

    internal fun prepareBackupSecrets(values: Map<String, String>?): Map<String, String>? =
        SecretBackup.prepareRestore(values, ::encrypt)

    /** Used only inside this install for rollback; not written to the portable archive. */
    internal suspend fun encryptedBackupState(): Map<String, String> =
        context.secretsStore.data.first().asMap().entries
            .filter { SecretBackup.recognized(it.key.name) }
            .associate { (key, value) ->
                key.name to (value as? String ?: throw BackupException("已保存的凭据格式无效"))
            }

    internal suspend fun replaceEncryptedBackupState(values: Map<String, String>) {
        context.secretsStore.edit { prefs ->
            prefs.asMap().keys.filter { SecretBackup.recognized(it.name) }
                .forEach { prefs.remove(stringPreferencesKey(it.name)) }
            values.forEach { (name, value) -> prefs[stringPreferencesKey(name)] = value }
        }
    }

    private fun key(): SecretKey {

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/SecretStore.kt", r""" * this install on this phone. That is also why the file is excluded from backups (see
 * data_extraction_rules.xml): a restored copy could only ever fail to decrypt.
""".removesuffix("\n"), r""" * this install on this phone. Android automatic backups exclude this ciphertext.
 * Explicit full export instead decrypts it into a portable file, and import encrypts
 * it again using the receiving installation's key.
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/CleosApp.kt", r"""    val backup = BackupService(context, db, settings, images)
""".removesuffix("\n"), r"""    val backup = BackupService(context, db, settings, images, secrets)
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt", r"""            userName = s.userName
            historySize = s.historySize
            weatherCity = s.weatherCity
            voiceBaseUrl = s.voiceBaseUrl
            voiceModel = s.voiceModel
            voiceService = VoiceService.of(s.speechEngine)
            speechVoices.putAll(s.speechVoices)
            minimaxGlobal = s.minimaxGlobal
            speechBaseUrl = s.speechBaseUrl
            speechModel = s.speechModel
            speechVoice = s.speechVoice
            elevenVoice = s.elevenVoice
            elevenModel = s.elevenModel

""".removesuffix("\n"), r"""            loadGlobalFields(s)

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt", r"""    @OptIn(FlowPreview::class)
    private suspend fun watch() {
""".removesuffix("\n"), r"""    private fun loadGlobalFields(s: AppSettings) {
        userName = s.userName
        historySize = s.historySize
        weatherCity = s.weatherCity
        voiceBaseUrl = s.voiceBaseUrl
        voiceModel = s.voiceModel
        voiceService = VoiceService.of(s.speechEngine)
        speechVoices.clear()
        speechVoices.putAll(s.speechVoices)
        minimaxGlobal = s.minimaxGlobal
        speechBaseUrl = s.speechBaseUrl
        speechModel = s.speechModel
        speechVoice = s.speechVoice
        elevenVoice = s.elevenVoice
        elevenModel = s.elevenModel
    }

    private suspend fun reloadBackupFields() {
        val s = c.settings.current()
        val ta = c.companions.current()
        withContext(Dispatchers.Main) {
            chat.keyInput = ""
            spoken.keyInput = ""
            voiceKeyInput = ""
            speechKeyInput = ""
            load(ta)
            loadGlobalFields(s)
            listedVoices = null
            listProblem = null
            voiceResult = null
            speechResult = null
            deleted = false
            loaded = true
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun watch() {
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt", r"""        if (!loaded || deleted) return@serially

""".removesuffix("\n"), r"""        if (!loaded || deleted || backupRestoring) return@serially

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt", r"""        if (!loaded || deleted || modelSaving) return

""".removesuffix("\n"), r"""        if (!loaded || deleted || modelSaving || backupBusy) return

""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt", r"""    fun exportBackup(uri: Uri) = runBackup("导出了") { c.backup.export(uri) }
""".removesuffix("\n"), r"""    private var backupRestoring = false

    fun exportBackup(uri: Uri) = runBackup("导出了") {
        persist()
        modelWriter.serially {
            // Match the existing save-on-leave behavior for the two model Key fields.
            val pending = listOfNotNull(chat.pendingKey(), spoken.pendingKey())
            for ((address, key) in pending) c.secrets.setKey(address, key)
            c.backup.export(uri)
        }
    }
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt", r"""    fun restoreBackup(uri: Uri) = runBackup("恢复了") {
""".removesuffix("\n"), r"""    fun restoreBackup(uri: Uri) = runBackup("恢复了", restoresContent = true) {
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt", r"""    fun undoRestore() = runBackup("撤销了，回到恢复前：") {
""".removesuffix("\n"), r"""    fun undoRestore() = runBackup("撤销了，回到恢复前：", restoresContent = true) {
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt", r"""    private fun runBackup(done: String, action: suspend () -> Any) {
        if (backupBusy) return
        backupBusy = true
        backupMessage = null
        c.appScope.launch {
            val message = try {
                "$done ${action()}"
            } catch (e: Exception) {
                (e as? com.cleo.cleos.data.BackupException)?.message ?: "出错了：${e.message ?: e.javaClass.simpleName}"
            }
            withContext(Dispatchers.Main) {
                backupMessage = message
                backupBusy = false
                canUndoRestore = c.backup.hasSnapshot
            }
        }
    }


""".removesuffix("\n"), r"""    private fun runBackup(done: String, restoresContent: Boolean = false, action: suspend () -> Any) {
        if (backupBusy || modelSaving) return
        backupBusy = true
        backupRestoring = restoresContent
        backupMessage = null
        c.appScope.launch {
            try {
                val result = if (restoresContent) {
                    modelWriter.serially {
                        withContext(Dispatchers.Main) { loaded = false }
                        try { action() } finally { reloadBackupFields() }
                    }
                } else action()
                withContext(Dispatchers.Main) { backupMessage = "$done $result" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    backupMessage = (e as? com.cleo.cleos.data.BackupException)?.message ?: "处理失败，请重试"
                }
            } finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    backupRestoring = false
                    backupBusy = false
                    canUndoRestore = c.backup.hasSnapshot
                }
            }
        }
    }


""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt", r"""        speechPlayer = null
        val pending = listOfNotNull(chat.pendingKey(), spoken.pendingKey())
""".removesuffix("\n"), r"""        speechPlayer = null
        if (backupRestoring || !loaded) return
        val pending = listOfNotNull(chat.pendingKey(), spoken.pendingKey())
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt", r"""把每个 TA、聊天、日记、信、记忆、待办、表情包和图片打包成一个文件。换手机、重装之前先导出一份。API Key 不会导出。
""".removesuffix("\n"), r"""把每个 TA、聊天、日记、信、记忆、待办、表情包、图片、全部软件配置、API Key 和 MCP 连接一起备份。更新、重装或换手机后，一次导入即可恢复。备份含 Key 和连接凭据，请只保存在自己可信的位置。旧备份不含 Key，导入时会保留当前已存 Key。系统权限需要在手机上重新授权。
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt", r"""现在的 TA、聊天、日记和待办会被备份里的全部替换掉。恢复之前会自动把现在的留一份，恢复完可以撤销。
""".removesuffix("\n"), r"""现在的内容和配置会被备份替换；新版备份也会替换全部已存 Key 和 MCP 连接，旧备份会保留当前 Key。恢复前自动保存完整备份，恢复后可以撤销。
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt", r"""回到恢复之前的 TA、聊天、日记和待办。恢复之后新写的会没有。
""".removesuffix("\n"), r"""回到恢复前的内容、全部配置、Key 和 MCP 连接。恢复后新写的内容与配置会被替换。
""".removesuffix("\n"))

    replace("app/src/main/java/com/cleo/cleos/data/McpServers.kt", r""" * keys, never in the database or a backup: the address can carry a key (`?key=`), and the
 * token is one.
""".removesuffix("\n"), r""" * keys rather than database rows. The user's explicit full backup includes the
 * connection and credentials; restore encrypts them with the receiving install's key.
""".removesuffix("\n"))

    # Derive the receiving app's known types from its declarations, so new settings
    # automatically enter both the snapshot and strict type validation.
    rel = "app/src/main/java/com/cleo/cleos/data/SettingsRepository.kt"
    repository = pending.get(rel, (root / rel).read_text(encoding="utf-8"))
    kinds = {"string": "string", "boolean": "boolean", "int": "int", "long": "long",
             "float": "float", "double": "double", "stringSet": "string-set"}
    declared = re.findall(r'(stringSet|string|boolean|int|long|float|double)PreferencesKey\("([^"]+)"\)', repository)
    if len(declared) < 40:
        raise SystemExit("SettingsRepository: too few declarations to validate complete settings")
    entries = "\n".join(f'        "{name}" to "{kinds[kind]}",' for kind, name in declared)
    methods = '''    internal val backupPreferenceTypes: Map<String, String> = mapOf(
''' + entries + '''
    )

    /** All raw preferences travel, including future configuration keys and selection. */
    internal suspend fun backupPreferences(): Map<String, BackupPreference> =
        PreferenceBackup.capture(context.settingsStore.data.first())

    internal suspend fun replaceBackupPreferences(values: Map<String, BackupPreference>) {
        PreferenceBackup.validate(values, backupPreferenceTypes)
        context.settingsStore.edit { PreferenceBackup.replace(values, it) }
    }

'''
    replace(rel, "    private object Keys {\n", methods + "    private object Keys {\n")

    for rel, text in pending.items():
        (root / rel).write_text(text, encoding="utf-8")
    for filename, folder in (
        ("PortableConfiguration.kt", "main/java/com/cleo/cleos/data"),
        ("PortableConfigurationTest.kt", "test/java/com/cleo/cleos/data"),
    ):
        destination = root / "app/src" / folder / filename
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(here / ("src" if folder.startswith("main/") else "tests") / filename, destination)
    print(f"完整配置与凭据备份已应用；{len(declared)} 项已知设置类型已校验。")


if __name__ == "__main__":
    apply_full_backup(Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd())
