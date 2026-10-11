package com.cleo.cleos.ai

/** Select the visible QQ Music application while an IME temporarily owns focus. */
object QqWindowRules {
    data class Window(
        val id: Int,
        val packageName: String,
        val application: Boolean = false,
        val inputMethod: Boolean = false,
        val active: Boolean = false,
        val focused: Boolean = false,
        val layer: Int = 0,
    )
    fun choose(activeId: Int?, activePackage: String?, windows: List<Window>, player: String): Int? {
        if (player !in AppNames.qqMusic) return null
        val app = windows.firstOrNull { it.application && it.active }
            ?: windows.firstOrNull { it.application && it.focused }
        if (activePackage == player) {
            if (app != null && app.packageName != player) return null
            return activeId
        }
        // Only an observed active input method may authorize the topmost
        // underlying application. A background music window is never used.
        val keyboard = windows.any { it.id == activeId && it.inputMethod && it.active }
        if (!keyboard) return null
        val top = app ?: windows.filter { it.application && it.packageName.isNotBlank() }
            .maxByOrNull { it.layer }
        return top?.takeIf { it.packageName == player }?.id
    }
}
