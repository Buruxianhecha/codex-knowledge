package com.cleo.cleos.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.cleo.cleos.data.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class OnlineAvatarHit(
    val title: String,
    val imageUrl: String,
    val previewUrl: String,
    val sourcePage: String,
    val license: String,
)
data class OnlineAvatarSearch(val results: List<OnlineAvatarHit>, val previews: List<Pair<Int, String>>)

/** An app-private image ready for the existing chat-image MessageImages codec. */
data class OnlineChatImage(val file: String, val width: Int, val height: Int)

/** These are public web images, NEVER MediaStore photos or chat messages. */
interface OnlineAvatarSource {
    suspend fun find(query: String): OnlineAvatarSearch
    suspend fun apply(companionId: Long, imageUrl: String): Boolean
    suspend fun importForChat(imageUrl: String): OnlineChatImage
}

internal object OnlineAvatarRules {
    private val formats = setOf("image/jpeg", "image/png", "image/webp")
    fun allowedMime(raw: String?): Boolean =
        raw?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) in formats

    /** Reject arbitrary protocols, userinfo, numeric addresses and internal hostnames. */
    fun safeHttpsUrl(raw: String): Boolean = try {
        if (raw.length > 2000) false else {
            val uri = URI(raw)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) &&
                host.length in 4..253 && host.contains('.') &&
                Regex("^[a-z0-9.-]+$").matches(host) &&
                host.any { it in 'a'..'z' } &&
                !host.endsWith(".local") && !host.endsWith(".internal") &&
                !host.endsWith(".localhost") && !host.endsWith(".test") &&
                !host.endsWith(".onion") && host != "localhost"
        }
    } catch (_: Exception) { false }

    fun publicIp(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress ||
            a.isSiteLocalAddress || a.isMulticastAddress) return false
        val b = a.address
        if (b.size == 4) {
            val x = b[0].toInt() and 255
            val y = b[1].toInt() and 255
            if (x == 0 || x >= 224 || (x == 100 && y in 64..127) ||
                (x == 192 && y == 0) || (x == 198 && y in 18..19)) return false
        } else if (b.size == 16) {
            if ((b[0].toInt() and 254) == 252) return false // IPv6 unique-local
        }
        return true
    }
}

/**
 * Keyless open-web discovery (Openverse / Wikimedia Commons) and HTTPS-only
 * private download. No photo is saved to the phone album or sent to chat.
 */
class OnlineAvatarService(
    http: OkHttpClient,
    private val images: ImageStore,
    private val avatar: SelfAvatar,
) : OnlineAvatarSource {
    private val client: OkHttpClient = http.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isEmpty() || addresses.any { !OnlineAvatarRules.publicIp(it) })
                    throw UnknownHostException("Restricted network address")
                return addresses
            }
        })
        .build()

    private fun JsonObject.str(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun requestText(url: String): String? {
        if (!OnlineAvatarRules.safeHttpsUrl(url)) return null
        val req = Request.Builder().url(url)
            .header("User-Agent", "HuaiminAndroid/1.0 (online-avatar-search)")
            .header("Accept", "application/json")
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else resp.body?.string()?.take(500_000)
            }
        }.getOrNull()
    }

    private fun openverse(query: String): List<OnlineAvatarHit> {
        val url = "https://api.openverse.org/v1/images/?q=" +
            URLEncoder.encode(query, "UTF-8") + "&page_size=12"
        val raw = requestText(url) ?: return emptyList()
        return runCatching {
            val results = Json.parseToJsonElement(raw).jsonObject["results"]?.jsonArray
                ?: return@runCatching emptyList()
            results.mapNotNull { element ->
                val obj = element.jsonObject
                val direct = obj.str("url")
                if (!OnlineAvatarRules.safeHttpsUrl(direct)) return@mapNotNull null
                OnlineAvatarHit(
                    title = obj.str("title").take(90).ifBlank { "网络图片" },
                    imageUrl = direct,
                    previewUrl = obj.str("thumbnail").takeIf(OnlineAvatarRules::safeHttpsUrl)
                        ?: direct,
                    sourcePage = obj.str("foreign_landing_url"),
                    license = (obj.str("license") + " " + obj.str("license_version")).trim(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun commons(query: String): List<OnlineAvatarHit> {
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
            "&gsrnamespace=6&gsrlimit=12&gsrsearch=" +
            URLEncoder.encode("filetype:bitmap " + query, "UTF-8") +
            "&prop=imageinfo&iiprop=url%7Cmime%7Csize&iiurlwidth=640&format=json"
        val raw = requestText(url) ?: return emptyList()
        return runCatching {
            val pages = Json.parseToJsonElement(raw).jsonObject["query"]
                ?.jsonObject?.get("pages")?.jsonObject?.values.orEmpty()
            pages.mapNotNull { element ->
                val o = element.jsonObject
                val info = o["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@mapNotNull null
                if (!OnlineAvatarRules.allowedMime(info.str("mime"))) return@mapNotNull null
                val direct = info.str("thumburl").ifBlank { info.str("url") }
                if (!OnlineAvatarRules.safeHttpsUrl(direct)) return@mapNotNull null
                OnlineAvatarHit(
                    title = o.str("title").removePrefix("File:").take(90),
                    imageUrl = direct,
                    previewUrl = direct,
                    sourcePage = info.str("descriptionurl"),
                    license = "请查看原图来源页的许可和署名要求",
                )
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun find(query: String): OnlineAvatarSearch = withContext(Dispatchers.IO) {
        val q = query.trim().take(90)
        if (q.length < 2) throw ToolFailure("请先想一个至少两个字的图片主题。", "缺少搜索关键词")
        val hits = openverse(q).ifEmpty { commons(q) }.distinctBy { it.imageUrl }.take(12)
        if (hits.isEmpty()) return@withContext OnlineAvatarSearch(emptyList(), emptyList())
        val previews = hits.take(3).mapIndexedNotNull { index, item ->
            val raw = readImageBytes(item.previewUrl, 2_000_000) ?: return@mapIndexedNotNull null
            val bitmap = decodeImage(raw, 360) ?: return@mapIndexedNotNull null
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 76, stream)
                index + 1 to ("data:image/jpeg;base64," +
                    Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP))
            } finally {
                bitmap.recycle()
            }
        }
        OnlineAvatarSearch(hits, previews)
    }

    private fun readImageBytes(url: String, maxBytes: Int): ByteArray? {
        if (!OnlineAvatarRules.safeHttpsUrl(url)) return null
        val req = Request.Builder().url(url)
            .header("User-Agent", "HuaiminAndroid/1.0")
            .header("Accept", "image/jpeg,image/png,image/webp")
            .build()
        return runCatching {
            client.newCall(req).execute().use response@ { resp ->
                val body = resp.body ?: return@response null
                if (!resp.isSuccessful ||
                    !OnlineAvatarRules.allowedMime(resp.header("Content-Type")) ||
                    body.contentLength() > maxBytes) return@response null
                val bytes = ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val chunk = ByteArray(8192)
                    var used = 0
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        used += n
                        if (used > maxBytes) return@response null
                        bytes.write(chunk, 0, n)
                    }
                }
                bytes.toByteArray()
            }
        }.getOrNull()
    }

    private fun decodeImage(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w !in 64..12000 || h !in 64..12000 ||
            w.toLong() * h > 80_000_000L) return null
        var sample = 1
        while (maxOf(w, h) / sample > maxEdge * 2) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    /** Securely downloads a real bitmap into private chat storage, not the system gallery.
     * The chat repository owns the returned file and must delete it on message-save failure.
     */
    override suspend fun importForChat(imageUrl: String): OnlineChatImage = withContext(Dispatchers.IO) {
        if (!OnlineAvatarRules.safeHttpsUrl(imageUrl))
            throw ToolFailure("只能发送公网 HTTPS 图片直链，不能发送网页、本地地址或内网链接。", "图片地址不安全")
        val bytes = readImageBytes(imageUrl, 8_000_000)
            ?: throw ToolFailure("网络图片无法直接获取：链接失效、网站禁止下载、文件不是普通图片或超过8MB。请更换一张图片直链。", "网络图片下载失败")
        val bitmap = decodeImage(bytes, 2048)
            ?: throw ToolFailure("下载结果不是可支持的 JPG/PNG/WebP 图片，或图片尺寸异常。", "图片格式不支持")
        try {
            OnlineChatImage(
                file = images.save(bitmap, prefix = "ai-web-chat-"),
                width = bitmap.width,
                height = bitmap.height,
            )
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun apply(companionId: Long, imageUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (!OnlineAvatarRules.safeHttpsUrl(imageUrl))
            throw ToolFailure("只允许有效的公网 HTTPS 图片链接，不能使用本地或内网地址。", "图片地址不安全")
        val bytes = readImageBytes(imageUrl, 8_000_000)
            ?: throw ToolFailure("图片站点不允许直接下载、链接失效或图片超过8MB，请换另一张。", "网络图片下载失败")
        val bitmap = decodeImage(bytes, 2048)
            ?: throw ToolFailure("图片格式或尺寸不支持，请选择普通 JPG、PNG 或 WebP 图片。", "无法读取网络图片")
        val temporary = try { images.save(bitmap, prefix = "online-avatar-temp-") }
        finally { bitmap.recycle() }
        try {
            avatar.usePicture(companionId, temporary)
        } finally {
            images.delete(listOf(temporary))
        }
    }
}
