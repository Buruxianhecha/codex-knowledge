package com.cleo.cleos
import com.cleo.cleos.data.ensureBundledDisplayFont
import com.cleo.cleos.data.ensureExtraBundledDisplayFonts

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import androidx.room.Room
import com.cleo.cleos.ai.AiSelfAvatar
import com.cleo.cleos.ai.OnlineAvatarService
import com.cleo.cleos.ai.AiSelfName
import com.cleo.cleos.ai.PersonalityGrowth
import com.cleo.cleos.ai.PersonalityGrowthConfigStore
import com.cleo.cleos.ai.Calls
import com.cleo.cleos.ai.ChatClient
import com.cleo.cleos.ai.ChatRepository
import com.cleo.cleos.ai.GoalStore
import com.cleo.cleos.ai.EarVoice
import com.cleo.cleos.ai.Glance
import com.cleo.cleos.ai.Later
import com.cleo.cleos.ai.Letters
import com.cleo.cleos.ai.Listening
import com.cleo.cleos.ai.Lyrics
import com.cleo.cleos.ai.PersonaMemory
import com.cleo.cleos.ai.PhoneCalendar
import com.cleo.cleos.ai.PhoneClock
import com.cleo.cleos.ai.PhoneLocation
import com.cleo.cleos.ai.PhoneMusic
import com.cleo.cleos.ai.AndroidPhoneApps
import com.cleo.cleos.ai.McpClient
import com.cleo.cleos.ai.McpHub
import com.cleo.cleos.ai.Recaps
import com.cleo.cleos.ai.OpenMeteo
import com.cleo.cleos.ai.SecretRequests
import com.cleo.cleos.ai.Speaker
import com.cleo.cleos.ai.ToolBox
import com.cleo.cleos.ai.Transcriber
import com.cleo.cleos.data.BackupService
import com.cleo.cleos.data.Companions
import com.cleo.cleos.data.ForeignImport
import com.cleo.cleos.data.ImageStore
import com.cleo.cleos.data.McpServers
import com.cleo.cleos.data.SecretStore
import com.cleo.cleos.data.SettingsRepository
import com.cleo.cleos.data.Stickers
import com.cleo.cleos.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CleosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Before anything that could fail: a crash while the rest is set up is kept too.
        CrashLog.install(this)
        container = AppContainer(this)
        container.notifier.channels()
        // Only mode=3 groups can spend model calls; disabled groups are never queried.
        com.cleo.cleos.ai.GroupAutonomy.install(this)
    }
}

/** Where a tapped notification leads: MainActivity reads it from the intent, the screens follow. */
sealed interface Opening {
    data class Chat(val conversationId: Long) : Opening

    data class Letter(val id: Long) : Opening
}

/** Hand-made dependency wiring; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    /** Outlives any screen: saves and replies started here finish even if the user leaves. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "cleos.db")
        .addMigrations(AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22)
        .build()
    val settings = SettingsRepository(context)
    val updates = com.cleo.cleos.update.UpdateController(context.applicationContext)
    val secrets = SecretStore(context)
    val images = ImageStore(context)
    val wallet = com.cleo.cleos.data.VirtualWalletStore(context).also {
        com.cleo.cleos.ai.WalletChatBridge.store = it
        com.cleo.cleos.ai.WalletChatBridge.database = db
    }
    val life = com.cleo.cleos.data.LifeStore(context.applicationContext, wallet)
    val moments = com.cleo.cleos.data.MomentsStore(context, images).also {
        com.cleo.cleos.ai.MomentsChatBridge.store = it
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Reasoning models can think for a long time before the first visible token.
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    val companions = Companions(db, settings, secrets, images)
    val apiKeyPool = com.cleo.cleos.ai.ApiKeyPool(secrets)
    val providerFailoverPool = com.cleo.cleos.ai.ProviderFailoverPool(secrets)
    val chatClient = ChatClient(http, keyPool=apiKeyPool, providerPool=providerFailoverPool)
    val momentsAutonomy = com.cleo.cleos.ai.MomentsAutonomy(this)
    private val calendar = PhoneCalendar(context)

    /** What the phone is playing, and its words: the chat's 一起听 bar, a reply's line about it, music_control. */
    val music = PhoneMusic(context)
    val lyrics = Lyrics(http, "Cleos/$version (${Releases.url(Releases.HOSTS.first())})", appScope)
    // Typed: its note_for_later reaches [later], which is made further down from what uses this.
    val tools: ToolBox = ToolBox(
        db.todos(),
        db.diary(),
        OpenMeteo(http),
        requests = { id -> db.messages().requestsBy(id).mapNotNull { SecretRequests.decode(it.content) } },
        avatar = AiSelfAvatar(db, images, companions),
        galleryAvatar = { actor, photoId ->
            // Import a private image copy; never write to or delete the system photo.
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId
            )
            val stored = images.import(uri, maxEdge = 2048, prefix = "ai-gallery-avatar-")
            AiSelfAvatar(db, images, companions).usePicture(actor, stored.file)
        },
        selfName = AiSelfName(db, companions),
        onlineAvatar = OnlineAvatarService(http, images, AiSelfAvatar(db, images, companions)),
        letters = { id -> db.letters().allFor(id) },
        memories = db.memories(),
        location = PhoneLocation(context, http),
        later = { later },
        alarms = PhoneClock(context) { visible },
        calendar = calendar,
        music = music,
        gallery = com.cleo.cleos.ai.DeviceGallery(context),
        phoneApps = AndroidPhoneApps(context, music) { visible },
        patBackWithActor = { id, suffix, actor -> chat.patBack(id, suffix, actor) },
    )
    val personalityGrowthConfig = PersonalityGrowthConfigStore(db)
    val personalityGrowth = PersonalityGrowth(db, companions, secrets, chatClient, personalityGrowthConfig)
    val recaps = Recaps(db, settings, secrets, chatClient, appScope)
    val lifeStudy = com.cleo.cleos.ai.LifeStudyService(this)
    val mcp = McpHub(
        McpServers(secrets),
        McpClient(http, version),
    )
    val transcriber = Transcriber(http, secrets)
    val speaker = Speaker(images, http, secrets)

    /** The TA's voice messages beside the ear, on headphones. */
    val ear = EarVoice(context, images)
    val goalStore = GoalStore(context.applicationContext)
    val chat: ChatRepository = ChatRepository(
        db, settings, secrets, chatClient, tools, images, companions, recaps, mcp, transcriber, speaker, appScope,
        personalityGrowth = personalityGrowth,
        goalStore = goalStore,
        life = life,
        // A reply finished where the person isn't looking (they left, or went to another page): as a notification.
        replied = { ta, conversationId, said ->
            if (!(visible && chatOnScreen == conversationId)) notifier.messages(ta, conversationId, said)
            followUps.plan(ta, conversationId, said)
        },
        interrupted = { followUps.cancel(it); freeTopics.interrupt(it) },
        listening = { Listening(music, lyrics).line() },
    )
    val stickers = Stickers(context, db, images)
    val backup = BackupService(context, db, settings, images, secrets, life = life, wallet = wallet)
    val imports = ForeignImport(context, db, companions)
    val notifier = Notifier(context, images)

    /** Phone calls with a TA. */
    val calls = Calls(context, db, chat, companions, settings, transcriber, speaker, appScope)

    /** Whether the app is on screen (MainActivity, between onStart and onStop). */
    @Volatile
    var visible = false

    /**
     * The conversation whose chat is on screen right now (the chat tab showing, the app in front),
     * else null. Only what arrives there goes without a notification: the app being open is not
     * enough, since on another tab, or in settings, nothing new in the chat is seen.
     */
    @Volatile
    var chatOnScreen: Long? = null

    val later: Later = Later(
        context,
        db,
        chat,
        notifier,
        appScope,
        showing = { id -> visible && chatOnScreen == id },
        companions = companions,
        settings = settings,
        glance = Glance(db, calendar, settings) { PhoneCalendar.allowed(context) },
        inUse = {
            context.getSystemService(PowerManager::class.java)?.isInteractive == true &&
                context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true
        },
    )
    val followUps = com.cleo.cleos.ai.FollowUps(context, db, chat, notifier, appScope) { id -> visible && chatOnScreen == id }
    val freeTopics = com.cleo.cleos.ai.FreeTopics(
        context, db, chat, notifier, appScope,
        showing = { id -> visible && chatOnScreen == id },
        onEnabled = { later.wantsNotifications.value = true },
    )
    val letters = Letters(db, settings, secrets, chatClient, appScope, written = { later.letterWritten(it) })

    /** Where a tapped notification leads, until a screen has gone there. */
    val opening = MutableStateFlow<Opening?>(null)

    /** A notification about [conversationId] was tapped: its TA and the conversation become current, then the chat shows. */
    fun openConversation(conversationId: Long) {
        appScope.launch {
            val conversation = db.conversations().get(conversationId) ?: return@launch
            settings.setCurrentCompanion(conversation.companionId)
            settings.setCurrentConversation(conversationId)
            opening.value = Opening.Chat(conversationId)
        }
    }

    fun openLetter(id: Long) {
        opening.value = Opening.Letter(id)
    }

    init {
        com.cleo.cleos.ai.LifeWakeWorker.install(context.applicationContext)
        // Changes to the schedule or an explicit 15 minute wake immediately reconsider
        // pending inputs. A cursor commit never cancels a reply already in progress.
        appScope.launch {
            life.state.collect {
                com.cleo.cleos.ai.LifeWakeWorker.reconcile(context.applicationContext, life)
                appScope.launch { runCatching {
                    life.reconcileCompanions(db.companions().all().map { it.id }.toSet())
                    chat.resumeLifePending()
                } }
            }
        }
        appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                runCatching { life.reconcileCompanions(db.companions().all().map { it.id }.toSet()) }
                runCatching { life.tick() }
                runCatching { chat.resumeLifePending() }
            }
        }
        com.cleo.cleos.ai.MomentsAutonomy.schedule(context)
        // The first TA is made from the old settings before anything asks who is being talked to.
        appScope.launch {
            runCatching { ensureBundledDisplayFont(context.assets, images.dir) }.getOrNull()?.let { bundled ->
                settings.update { current ->
                    val others = current.displayFonts.filterNot { it.file == bundled.file }
                    current.copy(displayFonts = listOf(bundled) + others)
                }
            }
            // Two real offline OFL fonts are provisioned separately. A malformed
            // legacy preset cannot hide these; existing selection and imported files stay.
            val extraFonts = runCatching {
                ensureExtraBundledDisplayFonts(context.assets, images.dir)
            }.getOrElse { emptyList() }
            if (extraFonts.isNotEmpty()) {
                settings.update { previous ->
                    val unique = (extraFonts + previous.displayFonts)
                        .distinctBy { it.file }
                    previous.copy(displayFonts = unique)
                }
            }
            companions.ensure()
            life.reconcileCompanions(db.companions().all().map { it.id }.toSet())
            life.tick()
            chat.resumeLifePending()
            stickers.ensureBuiltIns()
            // What a TA brought from another app used to sit in their persona; it moves into their memory, once.
            PersonaMemory.migrate(db, System.currentTimeMillis())
            // A call the app was stopped in the middle of ended there.
            chat.closeOpenCalls()
        }
        // Restore all still-valid proactive background work.
        later.reconcile()
        followUps.restore()
        freeTopics.restore()
    }
}
