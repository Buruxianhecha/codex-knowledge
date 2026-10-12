package com.cleo.cleos.ai

/**
 * Gallery/photo/avatar tools have their own large local tool-call allowance.
 * Ordinary tools keep their tighter limit. Identical repeated gallery requests
 * still stop early to avoid an infinite paid model-call loop.
 */
internal class GalleryToolBudget(
    private val ordinaryRoundLimit: Int = 5,
    private val galleryCallLimit: Int = 2048,
    private val duplicateRoundLimit: Int = 4,
) {
    var ordinaryRounds: Int = 0
        private set
    var galleryCalls: Int = 0
        private set
    var lastError: String = ""
        private set

    private var repeated: Int = 0
    private var previousGallerySignature: String? = null

    fun acceptCalls(calls: List<Pair<String, String>>): Boolean {
        if (calls.isEmpty()) return true
        val gallery = calls.filter { isGalleryOperation(it.first) }
        val ordinary = calls.size - gallery.size
        if (ordinary > 0 && ordinaryRounds >= ordinaryRoundLimit) {
            lastError = "普通工具调用已达到安全上限；相册工具有独立额度。"
            return false
        }
        if (galleryCalls.toLong() + gallery.size > galleryCallLimit) {
            lastError = "本轮相册工具已达到较高的${galleryCallLimit}次额度，请分下一条消息继续。"
            return false
        }
        // Four identical rounds indicate no progress, not a broad photo search.
        // Different photo IDs, search terms and offsets are all allowed.
        if (gallery.isNotEmpty() && ordinary == 0) {
            val signature = gallery.joinToString("\u001e") { (name, args) -> "$name\u001f$args" }
            repeated = if (signature == previousGallerySignature) repeated + 1 else 1
            previousGallerySignature = signature
            if (repeated >= duplicateRoundLimit) {
                lastError = "检测到连续重复相同的相册操作，已暂停无进展的重试；可换照片编号或继续聊。"
                return false
            }
        } else {
            previousGallerySignature = null
            repeated = 0
        }

        galleryCalls += gallery.size
        if (ordinary > 0) ordinaryRounds++
        lastError = ""
        return true
    }

    fun accept(calls: List<ToolCall>): Boolean =
        acceptCalls(calls.map { it.name to it.arguments })

    companion object {
        val galleryToolNames = setOf(
            "count_gallery_photos",
            "list_gallery_photos",
            "inspect_gallery_photos",
            "send_gallery_photos",
            "prepare_gallery_cleanup",
            "set_my_avatar",
            "search_web_avatar_images",
        )
        fun isGalleryOperation(name: String): Boolean = name in galleryToolNames
    }
}
