package com.cleo.cleos

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.Path
import android.accessibilityservice.GestureDescription
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.cleo.cleos.ai.AppNames
import com.cleo.cleos.ai.QqSearchPlan
import com.cleo.cleos.ai.QqFinalPlayRules
import com.cleo.cleos.ai.QqUiAction
import com.cleo.cleos.ai.QqUiNode
import com.cleo.cleos.ai.QqWindowRules
import com.cleo.cleos.ai.SongRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** An explicit point-song task only. No tasks are created by accessibility events. */
class QqMusicAccessibilityService : AccessibilityService() {
    private var interruption = 0
    override fun onServiceConnected() {
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

    private suspend fun selectSong(player: String, song: SongRequest, isPlaying: (() -> Boolean)?, resumePaused: (() -> Boolean)?): String? = withContext(Dispatchers.Main.immediate) {
        val plan = QqSearchPlan(song, player)
        val started = interruption
        var finalDetail = "QQ 音乐已打开目标歌曲页面，但未确认开始播放。"
        val result = withTimeoutOrNull(38_000) {
            var failedActions = 0
            var seenQQ = false
            var initialLooks = 0
            var mediaAttempted = false
            var mediaGrace = 0
            var finalPlayAttempts = 0
            var finalWait = 0
            var playTriggered = false
            while (true) {
                if (isPlaying?.invoke() == true) return@withTimeoutOrNull "selected"
                if (instance !== this@QqMusicAccessibilityService) return@withTimeoutOrNull "QQ 音乐点歌权限已关闭，任务停止。"
                if (interruption != started) return@withTimeoutOrNull "点歌任务被系统中断，请重新发送点歌指令。"
                if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return@withTimeoutOrNull "手机已锁屏，点歌停止；请解锁后重试。"
                // Android IME can own rootInActiveWindow even while QQ Music
                // remains visible. Inspect only application windows and choose
                // QQ if it is genuinely the frontmost app underneath the IME.
                val openWindows = windows
                val activeRoot = rootInActiveWindow
                val appRoots = openWindows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    .associate { it.id to it.root }
                val snapshots = openWindows.map { w ->
                    QqWindowRules.Window(
                        w.id, appRoots[w.id]?.packageName?.toString().orEmpty(),
                        w.type == AccessibilityWindowInfo.TYPE_APPLICATION,
                        w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                        w.isActive, w.isFocused, w.layer,
                    )
                }
                val activeWindowId = activeRoot?.windowId
                    ?: openWindows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.isActive }?.id
                val chosenId = QqWindowRules.choose(
                    activeWindowId, activeRoot?.packageName?.toString(), snapshots, player)
                val root = if (chosenId == activeRoot?.windowId) activeRoot else appRoots[chosenId]
                val pkg = root?.packageName?.toString().orEmpty()
                if (root == null || pkg != player) {
                    // Never manipulate a different app. Give QQ a moment to
                    // launch or for the keyboard's window hierarchy to refresh.
                    if (!seenQQ && initialLooks++ < 12) { delay(300); continue }
                    return@withTimeoutOrNull "QQ 音乐界面不在前台，自动选歌已停止；这不表示点歌权限未开启。"
                }
                seenQQ = true
                val actual = ArrayList<AccessibilityNodeInfo>()
                val nodes = ArrayList<QqUiNode>()
                fun visit(node: AccessibilityNodeInfo, parent: Int?, depth: Int) {
                    if (depth > 26 || actual.size >= 1200) return
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
                    for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, visibleParent, depth + 1) }
                }
                visit(root, null, 0)
                if (plan.picked) {
                    // Result-row activation only opens the player. Do not end
                    // the task until playback actually starts or fails safely.
                    if (!mediaAttempted) {
                        mediaAttempted = true
                        if (resumePaused?.invoke() == true) {
                            mediaGrace = 5 // allow the QQ media state to update
                        }
                    }
                    if (mediaGrace > 0) { mediaGrace--; delay(350); continue }
                    if (playTriggered) {
                        if (++finalWait >= 12) return@withTimeoutOrNull finalDetail
                        delay(350)
                        continue
                    }
                    val region = Rect().also(root::getBoundsInScreen)
                    val finalAction = QqFinalPlayRules.choose(
                        song, nodes, region.left, region.top, region.width(), region.height())
                    if (finalAction != null && finalPlayAttempts++ < 2) {
                        fun gesture(x: Int, y: Int): Boolean {
                            val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
                            val stroke = GestureDescription.StrokeDescription(p, 0L, 90L)
                            return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
                        }
                        val clicked = when (finalAction) {
                            is QqFinalPlayRules.Action.Click -> {
                                val node = actual.getOrNull(finalAction.nodeId)
                                node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true ||
                                    (node?.let {
                                        val r = Rect().also(it::getBoundsInScreen)
                                        gesture(r.centerX(), r.centerY())
                                    } == true)
                            }
                            is QqFinalPlayRules.Action.Gesture ->
                                gesture(finalAction.x, finalAction.y)
                        }
                        if (clicked) {
                            playTriggered = true
                            finalDetail = "已尝试点击 QQ 音乐《${song.title}》播放键，但尚未从媒体会话确认开始播放。"
                        } else {
                            finalDetail = "已打开《${song.title}》播放器，但播放按钮没有响应。"
                        }
                    }
                    if (finalPlayAttempts >= 2 && !playTriggered) return@withTimeoutOrNull finalDetail
                    delay(450)
                    continue
                }
                val action = plan.next(pkg, nodes)
                if (action is QqUiAction.Stop) return@withTimeoutOrNull action.reason
                val success = when (action) {
                    is QqUiAction.Click -> actual.getOrNull(action.id)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    is QqUiAction.Fill -> actual.getOrNull(action.id)?.let { node ->
                        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.query)
                        })
                    } == true
                    is QqUiAction.Submit -> Build.VERSION.SDK_INT >= 30 && actual.getOrNull(action.id)?.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id,
                    ) == true
                    else -> false
                }
                if (action != QqUiAction.Wait) {
                    plan.applied(action, success)
                    if (!success && ++failedActions >= 3) return@withTimeoutOrNull "QQ 音乐的搜索控件没有响应，暂时没法自动点这首歌。请手动打开一次搜索页后重试。"
                }
                if (Build.VERSION.SDK_INT < 33) {
                    @Suppress("DEPRECATION")
                    actual.forEach { it.recycle() }
                }
                delay(450)
            }
        }
        when (result) {
            "selected" -> null
            null -> if (plan.picked) finalDetail else "QQ 音乐点歌超时：没有找到可点击的匹配歌曲，可能是界面变化、歌曲未找到或搜索未完成。"
            else -> result
        }
    }

    companion object {
        @Volatile private var instance: QqMusicAccessibilityService? = null
        fun connected(): Boolean = instance != null
        fun enabled(context: Context): Boolean = context.getSystemService(AccessibilityManager::class.java)
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { it.resolveInfo.serviceInfo.let { info -> info.packageName == context.packageName && info.name == QqMusicAccessibilityService::class.java.name } } == true
        fun accessIntent(context: Context) = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, QqMusicAccessibilityService::class.java).flattenToString())
        }
        suspend fun select(
            player: String, song: SongRequest,
            isPlaying: (() -> Boolean)? = null,
            resumePaused: (() -> Boolean)? = null,
        ): String? {
            if (player !in AppNames.qqMusic) return "自动点歌只支持 QQ 音乐。"
            val service = instance ?: return "QQ 音乐无障碍服务当前未连接，请检查系统设置中的服务运行状态。"
            return service.selectSong(player, song, isPlaying, resumePaused)
        }
    }
}
