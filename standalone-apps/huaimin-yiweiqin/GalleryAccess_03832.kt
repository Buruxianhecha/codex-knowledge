package com.cleo.cleos.ai

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId

/** The owner must grant Android access and separately enable the AI tool. */
interface GallerySource {
    fun access(): GalleryAccess
    suspend fun listPhotos(query: String, album: String, offset: Int, limit: Int): List<GalleryPhoto>
    suspend fun inspectPhotos(ids: List<Long>): List<GalleryPicture>
    suspend fun proposeTrash(ids: List<Long>): Int
}
enum class GalleryAccess { None, Selected, All }
data class GalleryPhoto(
    val id: Long, val title: String, val album: String,
    val dateMillis: Long, val sizeBytes: Long,
)
data class GalleryPicture(val photo: GalleryPhoto, val dataUrl: String)

/** In-memory only; never authorizes deletion itself. */
object GalleryReviewQueue {
    private val pending = MutableStateFlow<List<Long>>(emptyList())
    val ids = pending.asStateFlow()
    fun propose(ids: List<Long>) { pending.value = ids.distinct().take(50) }
    fun clear() { pending.value = emptyList() }
}
object GalleryPermissions {
    val requests: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun access(context: Context): GalleryAccess {
        fun granted(name: String) = ContextCompat.checkSelfPermission(context, name) ==
            PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> GalleryAccess.All
            Build.VERSION.SDK_INT >= 34 &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> GalleryAccess.Selected
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> GalleryAccess.All
            else -> GalleryAccess.None
        }
    }
}
class DeviceGallery(private val context: Context) : GallerySource {
    override fun access() = GalleryPermissions.access(context)
    private val resolver get() = context.contentResolver
    private val uri get() = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private fun rows(
        selection: String? = null, args: Array<String>? = null,
        limit: Int = 50, offset: Int = 0,
    ): List<GalleryPhoto> {
        if (access() == GalleryAccess.None) return emptyList()
        val cols = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.SIZE,
        )
        val order = MediaStore.Images.Media.DATE_ADDED + " DESC, " +
            MediaStore.Images.Media._ID + " DESC"
        val c = if (Build.VERSION.SDK_INT >= 26) {
            val bundle = android.os.Bundle().apply {
                selection?.let { putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, it) }
                args?.let { putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it) }
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order)
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit.coerceIn(1, 60))
                putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset.coerceIn(0, 5000))
            }
            resolver.query(uri, cols, bundle, null)
        } else {
            resolver.query(uri, cols, selection, args, order + " LIMIT " +
                limit.coerceIn(1,60) + " OFFSET " + offset.coerceIn(0,5000))
        }
        val photos = ArrayList<GalleryPhoto>()
        c?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val date = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            while (cursor.moveToNext() && photos.size < limit) photos += GalleryPhoto(
                cursor.getLong(id),
                cursor.getString(name).orEmpty().take(140),
                cursor.getString(album).orEmpty().take(100),
                cursor.getLong(date) * 1000,
                cursor.getLong(size).coerceAtLeast(0),
            )
        }
        return photos
    }

    override suspend fun listPhotos(query: String, album: String, offset: Int, limit: Int) =
        withContext(Dispatchers.IO) {
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
            rows(filters.joinToString(" AND ").ifBlank { null },
                args.takeIf { it.isNotEmpty() }?.toTypedArray(),
                limit.coerceIn(1,30), offset.coerceIn(0,5000))
        }

    private fun photo(id: Long) = rows(
        MediaStore.Images.Media._ID + "=?", arrayOf(id.toString()), 1
    ).firstOrNull()

    override suspend fun inspectPhotos(ids: List<Long>): List<GalleryPicture> =
        withContext(Dispatchers.IO) {
            if (access() == GalleryAccess.None) return@withContext emptyList()
            ids.distinct().take(3).mapNotNull { id ->
                if (id <= 0) return@mapNotNull null
                val meta = runCatching { photo(id) }.getOrNull() ?: return@mapNotNull null
                val bmp = runCatching {
                    resolver.loadThumbnail(ContentUris.withAppendedId(uri, id),
                        Size(960, 960), null)
                }.getOrNull() ?: return@mapNotNull null
                val bytes = ByteArrayOutputStream()
                try {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 72, bytes)
                    GalleryPicture(meta, "data:image/jpeg;base64," +
                        Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
                } finally {
                    bmp.recycle()
                }
            }
        }

    override suspend fun proposeTrash(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        if (access() == GalleryAccess.None) return@withContext 0
        val existing = ids.distinct().take(50).filter { it > 0L }
            .filter { runCatching { photo(it) != null }.getOrDefault(false) }
        GalleryReviewQueue.propose(existing)
        existing.size
    }

    fun uriFor(id: Long) = ContentUris.withAppendedId(uri, id)
}
object GalleryResultFormatter {
    fun display(items: List<GalleryPhoto>, access: GalleryAccess): String =
        if (items.isEmpty()) "未检索到可访问照片：可能无匹配项或系统仅允许访问选中照片。"
        else buildString {
            append("相册授权：")
            append(if (access == GalleryAccess.All) "全部照片" else "部分已选照片")
            append("。以下为元数据，不代表已经看过照片内容：\n")
            for (p in items) {
                append("ID=" + p.id + "；文件名=" + p.title + "；相册=" + p.album +
                    "；日期=" + Instant.ofEpochMilli(p.dateMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDate() +
                    "；大小=" + p.sizeBytes + " 字节\n")
            }
            append("要看照片内容必须调用 inspect_gallery_photos，不能根据文件名臆测。")
        }
}
