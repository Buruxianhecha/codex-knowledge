package com.cleo.cleos.ai

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import com.cleo.cleos.QqMusicAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AndroidPhoneApps(
    private val context: Context,
    private val music: PhoneMusic,
    private val onScreen: () -> Boolean,
) : PhoneAppActions, QqPlaybackPhone {
    private val player = QqPlayback(this)

    @Suppress("DEPRECATION")
    private fun launcherApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        val results = if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            else pm.queryIntentActivities(intent, 0)
        return results.filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled }
            .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }.distinctBy { it.packageName }
    }

    private fun canLaunch() {
        if (context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) {
            throw ToolFailure("手机已锁屏，请解锁后再让我打开 App。", "手机锁屏了")
        }
        if (!onScreen() && !QqMusicAccessibilityService.connected()) {
            throw ToolFailure("Android 限制后台弹出 App。请保持「怀民亦未寝」在前台再说一次打开指令。", "App 在后台")
        }
    }

    private fun launch(app: LaunchableApp, intent: Intent? = null) {
        canLaunch()
        val target = intent ?: context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: throw ToolFailure("${app.label} 没有可打开的桌面入口。", "没有桌面入口")
        try {
            context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: SecurityException) {
            throw ToolFailure("系统阻止了打开 ${app.label}，请确认手机允许「怀民亦未寝」打开其他 App。", "系统阻止打开")
        } catch (_: android.content.ActivityNotFoundException) {
            throw ToolFailure("${app.label} 的入口已经失效，请确认 App 仍然安装着。", "App 入口失效")
        }
    }

    override fun open(name: String): ToolOutcome {
        val app = AppNames.find(name, launcherApps())
        launch(app)
        return ToolOutcome("已向 Android 发送打开 ${app.label} 的请求。这只表示打开请求已发出，不代表已经在这个 App 里搜索、播放或完成其他操作。", "已请求打开 ${app.label}")
    }

    override suspend fun playQQ(title: String, artist: String?) = player.play(SongRequest.of(title, artist))
    override fun installed() = AppNames.find("QQ音乐", launcherApps())
    override fun notificationAllowed() = music.allowed()
    override fun screenControlAllowed() = QqMusicAccessibilityService.connected()
    override fun current(app: LaunchableApp) = music.currentFor(app.packageName)

    @Suppress("DEPRECATION")
    override suspend fun begin(app: LaunchableApp, song: SongRequest): Boolean = withContext(Dispatchers.Main.immediate) {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(app.packageName)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            .putExtra(android.app.SearchManager.QUERY, song.query)
            .putExtra(MediaStore.EXTRA_MEDIA_TITLE, song.title)
            .putExtra(MediaStore.EXTRA_MEDIA_ARTIST, song.artist)
        val resolves = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.exported == true
        if (resolves) {
            launch(app, intent)
            true
        } else {
            launch(app)
            delay(600)
            music.search(app.packageName, song)
        }
    }

    override suspend fun selectOnScreen(app: LaunchableApp, song: SongRequest): String? =
        QqMusicAccessibilityService.select(app.packageName, song)
}
