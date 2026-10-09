package com.cleo.cleos.update

import org.json.JSONObject
import java.net.URI

/**
 * Manifest format hosted by the independent download site, not an App Store.
 * Only versionCode determines whether the installed APK is older.
 */
data class HuaiminRelease(
    val versionCode: Long,
    val version: String,
    val notes: List<String>,
    val apkUrl: String,
    val sha256: String,
    val apkBytes: Long?,
    val mandatory: Boolean,
)

object UpdatePolicy {
    const val FEED_URL = "https://huaimin-download.vercel.app/latest.json"
    const val SITE_URL = "https://huaimin-download.vercel.app/"
    const val MAX_APK_BYTES = 300L * 1024 * 1024

    fun secureHttps(url: String): Boolean = try {
        val uri = URI(url.trim())
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.port in -1..65535
    } catch (_: Exception) { false }

    fun parseManifest(raw: String, feedUrl: String = FEED_URL): HuaiminRelease {
        require(raw.length in 2..65536) { "版本信息文件大小不正确" }
        require(secureHttps(feedUrl)) { "更新地址必须使用 HTTPS" }
        val json = JSONObject(raw)
        val code = json.optLong("versionCode", -1)
        val version = json.optString("version", json.optString("versionName")).trim().take(35)
        val link = json.optString("apkUrl").trim()
        val resolved = URI(feedUrl).resolve(link).toString()
        val digest = json.optString("sha256").trim().lowercase()
        val size = json.optLong("bytes", -1L)
        val notesList = json.optJSONArray("releaseNotes") ?: json.optJSONArray("notes")
        val notes = mutableListOf<String>()
        if (notesList != null) for (i in 0 until minOf(notesList.length(), 12)) {
            val item = notesList.optString(i).trim().take(250)
            if (item.isNotEmpty()) notes.add(item)
        }
        require(code > 0 && version.isNotBlank()) { "版本号缺失" }
        require(secureHttps(resolved)) { "安装包地址必须使用 HTTPS" }
        require(digest.matches(Regex("[a-f0-9]{64}"))) { "缺少有效的安装包 SHA-256" }
        require(size == -1L || size in 1..MAX_APK_BYTES) { "安装包大小异常" }
        return HuaiminRelease(
            versionCode = code, version = version, notes = notes, apkUrl = resolved,
            sha256 = digest, apkBytes = size.takeIf { it > 0 },
            mandatory = json.optBoolean("mandatory", false),
        )
    }

    fun isNewer(localCode: Long, remoteCode: Long): Boolean = remoteCode > localCode
}
