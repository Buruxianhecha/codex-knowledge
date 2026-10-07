package com.cleo.cleos

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.cleo.cleos.ai.AppNames
import com.cleo.cleos.ai.QqSearchPlan
import com.cleo.cleos.ai.QqUiAction
import com.cleo.cleos.ai.QqUiNode
import com.cleo.cleos.ai.SongRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** An explicit point-song task only. No tasks are created by accessibility events. */
class QqMusicAccessibilityService : AccessibilityService() {
    private var interruption = 0
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { interruption++ }
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private suspend fun selectSong(player: String, song: SongRequest): String? = withContext(Dispatchers.Main.immediate) {
        val plan = QqSearchPlan(song, player)
        val started = interruption
        withTimeoutOrNull(22_000) {
            var failedActions = 0
            var seenQQ = false
            var initialLooks = 0
            while (!plan.picked) {
                if (instance !== this@QqMusicAccessibilityService) return@withTimeoutOrNull "QQ 音乐点歌权限已关闭，任务停止。"
                if (interruption != started) return@withTimeoutOrNull "点歌任务被系统中断，请重新发送点歌指令。"
                if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return@withTimeoutOrNull "手机已锁屏，点歌停止；请解锁后重试。"
                val root = rootInActiveWindow
                if (root == null) { delay(300); continue }
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg != player) {
                    // Allow the launcher transition at the beginning, not another app after QQ was seen.
                    if (!seenQQ && initialLooks++ < 8) { delay(300); continue }
                    return@withTimeoutOrNull "QQ 音乐不在前台，自动点歌已停止。请保持 QQ 音乐在前台再试。"
                }
                seenQQ = true
                val actual = ArrayList<AccessibilityNodeInfo>()
                val nodes = ArrayList<QqUiNode>()
                fun visit(node: AccessibilityNodeInfo, parent: Int?, depth: Int) {
                    if (depth > 16 || actual.size >= 500 || !node.isVisibleToUser) return
                    val id = actual.size
                    actual.add(node)
                    val bounds = Rect().also(node::getBoundsInScreen)
                    nodes.add(QqUiNode(id, parent, node.text?.toString().orEmpty(), node.contentDescription?.toString().orEmpty(),
                        node.viewIdResourceName.orEmpty(), node.isEditable, node.isClickable && node.isEnabled, node.isSelected,
                        (bounds.height() / resources.displayMetrics.density).toInt(), node.hintText?.toString().orEmpty()))
                    for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, id, depth + 1) }
                }
                visit(root, null, 0)
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
            "selected"
        }?.takeUnless { it == "selected" } ?: if (plan.picked) null else "QQ 音乐点歌超时：没有找到可点击的匹配歌曲，可能是界面变化、歌曲未找到或搜索未完成。"
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
        suspend fun select(player: String, song: SongRequest): String? {
            if (player !in AppNames.qqMusic) return "自动点歌只支持 QQ 音乐。"
            return instance?.selectSong(player, song) ?: "还没开启「怀民亦未寝 · QQ 音乐点歌」权限，请从「设置 → 能做的事 → 打开手机 App」开启后重试。"
        }
    }
}
