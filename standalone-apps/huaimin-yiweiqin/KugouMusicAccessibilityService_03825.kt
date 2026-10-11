package com.cleo.cleos

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.cleo.cleos.ai.AppNames
import com.cleo.cleos.ai.KugouGestureCompletion
import com.cleo.cleos.ai.KugouScreenRules
import com.cleo.cleos.ai.KugouSearchPlan
import com.cleo.cleos.ai.QqUiAction
import com.cleo.cleos.ai.QqUiNode
import com.cleo.cleos.ai.SongRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Only an explicit point-song task may operate the foreground Kugou window. */
class KugouMusicAccessibilityService : AccessibilityService() {
    private var interruption = 0
    override fun onServiceConnected() {
        // Keep the XML declaration and runtime capabilities consistent across upgrades.
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        instance = this
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { interruption++ }
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private suspend fun tap(target: AccessibilityNodeInfo, purpose: String, song: SongRequest, visible: Rect): Boolean {
        if (!target.refresh() || !target.isVisibleToUser || !target.isEnabled) return false
        val words = listOf(target.text?.toString(), target.contentDescription?.toString()).filterNotNull()
        val valid = when (purpose) {
            "songs" -> words.any { it.trim() == "单曲" }
            "song" -> words.any { AppNames.key(it.trim('《', '》')) == AppNames.key(song.title) }
            else -> false
        }
        if (!valid) return false
        val box = Rect().also(target::getBoundsInScreen)
        fun Rect.bounds() = KugouScreenRules.Bounds(left, top, right, bottom)
        if (!KugouScreenRules.safeTap(box.bounds(), visible.bounds(), resources.displayMetrics.density, purpose)) return false
        val path = Path().apply { moveTo(box.exactCenterX(), box.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 90L)).build()
        return KugouGestureCompletion.await { complete ->
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { complete(true) }
                override fun onCancelled(gestureDescription: GestureDescription?) { complete(false) }
            }, null)
        }
    }

    private suspend fun selectSong(player: String, song: SongRequest, isPlaying: (() -> Boolean)?): String? = withContext(Dispatchers.Main.immediate) {
        val plan = KugouSearchPlan(song, player)
        val started = interruption
        var detail = "酷狗音乐点歌超时：未找到可以确认的单曲结果。"
        val completed = withTimeoutOrNull(26_000L) {
            var failedActions = 0
            var seenPlayer = false
            var initialLooks = 0
            var missingRootLooks = 0
            var keyboardDismissed = false
            var keyboardWait = 0
            var playbackWait = 0
            while (true) {
                if (instance !== this@KugouMusicAccessibilityService) { detail = "酷狗音乐点歌权限已关闭，任务停止。"; return@withTimeoutOrNull false }
                if (interruption != started) { detail = "点歌任务被系统中断，请重新发送点歌指令。"; return@withTimeoutOrNull false }
                if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) { detail = "手机已锁屏，点歌停止；请解锁后重试。"; return@withTimeoutOrNull false }
                if (isPlaying?.invoke() == true || (plan.picked && isPlaying == null)) return@withTimeoutOrNull true

                val windowList = windows
                val active = rootInActiveWindow
                // An IME can clear focus flags on the still-visible player.
                // Keep all app roots; the authorization below picks the front app.
                val roots = windowList.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    .associate { it.id to it.root }
                val snapshots = windowList.map { window ->
                    KugouScreenRules.Window(window.id,
                        roots[window.id]?.packageName?.toString().orEmpty(),
                        window.type == AccessibilityWindowInfo.TYPE_APPLICATION,
                        window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                        window.isActive, window.isFocused, window.layer)
                }
                val chosenId = KugouScreenRules.applicationWindow(
                    active?.windowId, active?.packageName?.toString(), snapshots)
                val rootTemporarilyMissing = active == null ||
                    (snapshots.any { it.id == active.windowId && it.inputMethod } && chosenId == null)
                if (rootTemporarilyMissing) {
                    val otherForeground = snapshots.any { it.application && it.active && it.packageName.isNotEmpty() && it.packageName != player }
                    if (!otherForeground && missingRootLooks++ < 6) { delay(300); continue }
                } else missingRootLooks = 0
                val root = if (chosenId != null && chosenId == active?.windowId) active else roots[chosenId]
                if (root == null || root.packageName?.toString() != player) {
                    if (!seenPlayer && initialLooks++ < 12) { delay(300); continue }
                    detail = "酷狗音乐不在前台，自动点歌已停止。请保持酷狗音乐在前台再试。"
                    return@withTimeoutOrNull false
                }
                seenPlayer = true
                val visible = Rect().also(root::getBoundsInScreen)
                val ime = windowList.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                if (ime != null) {
                    val imeBounds = Rect().also(ime::getBoundsInScreen)
                    visible.bottom = minOf(visible.bottom, imeBounds.top)
                }
                val actual = ArrayList<AccessibilityNodeInfo>()
                val nodes = ArrayList<QqUiNode>()
                fun visit(node: AccessibilityNodeInfo, parent: Int?, depth: Int) {
                    if (depth > 28 || actual.size >= 1_200) return
                    var visibleParent = parent
                    if (node.isVisibleToUser) {
                        val id = actual.size
                        actual.add(node)
                        val bounds = Rect().also(node::getBoundsInScreen)
                        nodes.add(QqUiNode(id, parent, node.text?.toString().orEmpty(), node.contentDescription?.toString().orEmpty(),
                            node.viewIdResourceName.orEmpty(), node.isEditable, node.isClickable && node.isEnabled, node.isSelected,
                            (bounds.height() / resources.displayMetrics.density).toInt(), node.hintText?.toString().orEmpty(),
                            bounds.centerX(), bounds.centerY(), resources.displayMetrics.density))
                        visibleParent = id
                    }
                    // Invisible wrapper nodes must not hide visible tab/title descendants.
                    for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, visibleParent, depth + 1) }
                }
                visit(root, null, 0)
                try {
                    if (ime != null && plan.canDismissKeyboard(nodes)) {
                        if (!keyboardDismissed) {
                            // BACK is used only with an observed IME over our submitted
                            // results page. It is never used to guess at navigation.
                            if (!performGlobalAction(GLOBAL_ACTION_BACK)) { detail = "搜索结果已出现，但系统未能收起输入法，请收起键盘后重试。"; return@withTimeoutOrNull false }
                            keyboardDismissed = true
                        } else if (++keyboardWait >= 5) {
                            detail = "酷狗搜索结果已出现，但输入法仍遮挡页面，请收起键盘后重试。"
                            return@withTimeoutOrNull false
                        }
                        delay(300)
                        continue // Always take a new hierarchy after the keyboard disappears.
                    }
                    if (plan.picked && isPlaying != null) {
                        detail = "已点选《${song.title}》，但没有确认酷狗正在播放这首歌。"
                        if (++playbackWait < 5) { delay(450); continue }
                        // An accepted semantic click can be a no-op. Retry the same
                        // verified result label with a bounded gesture, never an artist link.
                        plan.retryUnconfirmedSong()
                        playbackWait = 0
                    }
                    val action = plan.next(player, nodes)
                    if (action is QqUiAction.Stop) { detail = action.reason; return@withTimeoutOrNull false }
                    val success = when (action) {
                        is QqUiAction.Click -> {
                            val target = actual.getOrNull(action.id)
                            val exact = actual.getOrNull(action.gestureId ?: action.id)
                            val native = !action.preferGesture && target?.refresh() == true && target.isVisibleToUser && target.isEnabled &&
                                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            native || (exact != null && tap(exact, action.purpose, song, visible))
                        }
                        is QqUiAction.Fill -> actual.getOrNull(action.id)?.let { node ->
                            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.query)
                            })
                        } == true
                        is QqUiAction.Submit -> Build.VERSION.SDK_INT >= 30 && actual.getOrNull(action.id)?.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id) == true
                        else -> false
                    }
                    if (action != QqUiAction.Wait) {
                        plan.applied(action, success)
                        if (success) failedActions = 0
                        else if (++failedActions >= 3) { detail = "酷狗音乐的目标控件连续三次没有响应，点歌已停止。"; return@withTimeoutOrNull false }
                    }
                } finally {
                    if (Build.VERSION.SDK_INT < 33) {
                        @Suppress("DEPRECATION")
                        actual.forEach { it.recycle() }
                    }
                }
                delay(450)
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        if (completed == true) null else detail
    }

    companion object {
        @Volatile private var instance: KugouMusicAccessibilityService? = null
        fun connected(): Boolean = instance != null
        fun enabled(context: Context): Boolean = context.getSystemService(AccessibilityManager::class.java)
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { it.resolveInfo.serviceInfo.let { info -> info.packageName == context.packageName && info.name == KugouMusicAccessibilityService::class.java.name } } == true
        fun accessIntent(context: Context) = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, KugouMusicAccessibilityService::class.java).flattenToString())
        }
        suspend fun select(player: String, song: SongRequest, isPlaying: (() -> Boolean)? = null): String? {
            if (player != "com.kugou.android") return "自动点歌只支持酷狗音乐。"
            return instance?.selectSong(player, song, isPlaying) ?: "还没开启「怀民亦未寝 · 酷狗音乐点歌」权限，请从「设置 → 能做的事 → 打开手机 App」开启后重试。"
        }
    }
}
