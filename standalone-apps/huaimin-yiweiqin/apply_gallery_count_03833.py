#!/usr/bin/env python3
"""0.38.33: fix 'how many photos?' using a real one-shot MediaStore count."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
java=root/"app/src/main/java/com/cleo/cleos"
def once(path,old,new,reason):
    txt=path.read_text(encoding="utf-8")
    n=txt.count(old)
    if n!=1: raise RuntimeError(f"{reason}: 0.38.32 anchor mismatch, {n} matches")
    path.write_text(txt.replace(old,new,1),encoding="utf-8")
gallery=java/"ai/GalleryAccess.kt"
once(gallery,
    "    suspend fun listPhotos(query: String, album: String, offset: Int, limit: Int): List<GalleryPhoto>",
    """    /** Number of visible images in the Android MediaStore permission scope, no paging. */
    suspend fun countPhotos(query: String, album: String): Int
    suspend fun listPhotos(query: String, album: String, offset: Int, limit: Int): List<GalleryPhoto>""",
    "source interface")
method='''    /** Count IDs only: no pagination, thumbnail decoding, or uploading photo names.
     * Android enforces full-vs-selected photo access inside the query.
     */
    override suspend fun countPhotos(query: String, album: String): Int =
        withContext(Dispatchers.IO) {
            if (access() == GalleryAccess.None)
                throw SecurityException("相册权限已关闭")
            val filters = ArrayList<String>()
            val args = ArrayList<String>()
            if (query.isNotBlank()) {
                filters += MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?"
                args += "%" + query.trim().take(80) + "%"
            }
            if (album.isNotBlank()) {
                filters += MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " = ?"
                args += album.trim().take(80)
            }
            // Cursor.getCount() reports the provider's entire visible result
            // set. Crucially: no QUERY_ARG_LIMIT / QUERY_ARG_OFFSET / LIMIT.
            val selection = filters.joinToString(" AND ").ifBlank { null }
            val selectionArgs = args.takeIf { it.isNotEmpty() }?.toTypedArray()
            val cursor = resolver.query(
                uri, arrayOf(MediaStore.Images.Media._ID),
                selection, selectionArgs, null,
            ) ?: throw IllegalStateException("系统媒体库未返回查询结果")
            cursor.use { it.count }
        }

'''
once(gallery,"    private fun rows(",method+"    private fun rows(","MediaStore unpaginated count")
formatter='''/** Counts are only for the system-visible permission scope, not cloud-only photos. */
object GalleryCountText {
    fun render(
        count: Int,
        access: GalleryAccess,
        query: String = "",
        album: String = "",
    ): String {
        if (access == GalleryAccess.None) return "尚未授权相册，无法统计照片数量。"
        if (count < 0) return "统计失败，请稍后重试。"
        val filter = buildList {
            if (album.isNotBlank()) add("相册：" + album.take(80))
            if (query.isNotBlank()) add("文件名包含：" + query.take(80))
        }.joinToString("，")
        val scope = if (access == GalleryAccess.All) "全部照片授权范围" else "仅部分照片授权范围"
        return "Android 媒体库" + (if (filter.isEmpty()) "" else "（" + filter + "）") +
            "在当前" + scope + "内，共有 " + count + " 张图片。" +
            "不包括视频、仅云端且未同步到媒体库的照片。" +
            if (access == GalleryAccess.Selected)
                " 注意：系统目前只允许访问仅部分照片，" +
                    count + " 张不是整个相册的总数；要统计全部请在系统设置中授权全部照片。"
            else " 这是本机媒体库当前可访问的图片数量。"
    }
}

'''
once(gallery,"object GalleryResultFormatter {",formatter+"object GalleryResultFormatter {","count formatter")

tools=java/"ai/Tools.kt"
countSpec='''    val galleryCount = ToolSpec(
        name = "count_gallery_photos",
        groups = setOf(ToolGroup.Gallery),
        action = "统计相册照片",
        description = "对方问相册一共有多少张照片、图片数量、按相册/文件名统计时，必须优先且仅调用这个工具一次！直接返回Android媒体库授权范围内的精确图片总数，不要用 list_gallery_photos 连续翻页凑数；照片列表30张只是每页数量。仅统计图片不包括视频，部分照片授权不代表全相册。",
        parameters = schema(
            "query" to prop("string","可选：统计文件名包含该词的图片"),
            "album" to prop("string","可选：统计这个系统相册里的图片；不填即全部授权图片"),
        ),
    )
'''
once(tools,'    val galleryList = ToolSpec(',countSpec+'    val galleryList = ToolSpec(',"register count spec")
once(tools,
     'description = "按文件名关键词、相册名称列出对方已授权手机相册照片的编号和元数据。无法按人物/内容猜照片；要看画面请随后调用 inspect_gallery_photos。结果分页，不能一次遍历上传相册。",',
     'description = "仅在要查看照片列表或查找照片编号时调用，返回最多30张一页。只问有多少照片时不得使用本工具，必须调用 count_gallery_photos 一次。无法按人物/内容猜照片；要看画面请随后调用 inspect_gallery_photos。",',
     "do not list-page when counting")
once(tools,'        galleryList,\n','        galleryCount,\n        galleryList,\n',"offer dedicated count tool")
once(tools,
     '                ToolSpecs.galleryList.name -> galleryList(args)',
     '                ToolSpecs.galleryCount.name -> galleryCount(args)\n                ToolSpecs.galleryList.name -> galleryList(args)',
     "execute count")
countFn='''    private suspend fun galleryCount(a: JsonObject): ToolOutcome {
        val source = userGallery() // Re-checks system access on every call.
        val query = ToolArgs.text(a, "query").orEmpty()
        val album = ToolArgs.text(a, "album").orEmpty()
        val count = try {
            source.countPhotos(query, album)
        } catch (e: Exception) {
            throw ToolFailure(
                "无法查询系统媒体库照片数量，请检查相册授权或稍后重试。",
                "相册数量统计失败",
            )
        }
        return ToolOutcome(
            GalleryCountText.render(count, source.access(), query, album),
            "已统计授权范围内的 " + count + " 张图片",
        )
    }

'''
once(tools,'    private suspend fun galleryList(a: JsonObject): ToolOutcome {',
     countFn+'    private suspend fun galleryList(a: JsonObject): ToolOutcome {',
     "count tool logic")
gradle=root/"app/build.gradle.kts"
once(gradle,'versionName = "0.38.32"','versionName = "0.38.33"',"version")
once(gradle,'versionCode = 62098','versionCode = 62099',"build number")
print("0.38.33/62099: one-shot system MediaStore photo count, permission-aware, no pagination or image upload")
