package com.cleo.cleos.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class UpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val offered: HuaiminRelease? = null,
    val readyFile: File? = null,
    val message: String? = null,
    val checked: Boolean = false,
)

/**
 * No account, login or notification permission needed. Never installs silently.
 * The Android install confirmation remains mandatory, and the downloaded APK
 * must be from the same package and signed by the existing certificate.
 */
class UpdateController(private val app: Context) {
    private val prefs = app.getSharedPreferences("huaimin_updates", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state = mutableState.asStateFlow()

    val automatic: Boolean get() = prefs.getBoolean("auto", true)
    val sourceUrl: String get() = prefs.getString("source", UpdatePolicy.FEED_URL)
        ?.takeIf { UpdatePolicy.secureHttps(it) } ?: UpdatePolicy.FEED_URL

    fun setAutomatic(enabled: Boolean) { prefs.edit().putBoolean("auto", enabled).apply() }
    fun setSource(url: String): Boolean {
        val value = url.trim()
        if (!UpdatePolicy.secureHttps(value)) return false
        prefs.edit().putString("source", value).putLong("checked_ms", 0).apply()
        mutableState.update { it.copy(message = null, checked = false, offered = null) }
        return true
    }

    private fun installedCode(): Long {
        @Suppress("DEPRECATION")
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        return info.longVersionCode
    }

    fun skipThisVersion() {
        val offered = state.value.offered ?: return
        prefs.edit().putLong("skip_code", offered.versionCode).apply()
        mutableState.update { it.copy(offered = null, message = "已跳过 v${offered.version}，后续版本仍会提醒。") }
    }

    fun dismiss() { mutableState.update { it.copy(offered = null, message = null) } }

    suspend fun check(manual: Boolean = false) {
        if (mutableState.value.checking || mutableState.value.downloading) return
        val now = System.currentTimeMillis()
        // On app startup, at most one remote request per 12 hours after success.
        if (!manual && (!automatic || now - prefs.getLong("checked_ms", 0) < 12 * 60 * 60 * 1000L)) return
        mutableState.update { it.copy(checking = true, message = null) }
        try {
            val feed = sourceUrl
            val release = withContext(Dispatchers.IO) {
                val request = URL(feed).openConnection() as HttpURLConnection
                try {
                    request.connectTimeout = 8_000
                    request.readTimeout = 10_000
                    request.setRequestProperty("Accept", "application/json")
                    request.setRequestProperty("Cache-Control", "no-cache")
                    request.setRequestProperty("User-Agent", "Huaimin-Android-Update/1")
                    if (request.responseCode != 200) throw IllegalStateException("HTTP ${request.responseCode}")
                    if (!UpdatePolicy.secureHttps(request.url.toString())) throw IllegalStateException("更新源跳转到不安全的地址")
                    val text = request.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(2048)
                        val result = StringBuilder()
                        while (true) {
                            val n = reader.read(buffer)
                            if (n < 0) break
                            result.append(buffer, 0, n)
                            if (result.length > 65536) throw IllegalStateException("版本信息过大")
                        }
                        result.toString()
                    }
                    UpdatePolicy.parseManifest(text, feed)
                } finally { request.disconnect() }
            }
            prefs.edit().putLong("checked_ms", now).apply()
            val newer = UpdatePolicy.isNewer(installedCode(), release.versionCode)
            val skipped = !manual && !release.mandatory &&
                prefs.getLong("skip_code", -1) == release.versionCode
            mutableState.update {
                it.copy(
                    checking = false, checked = true,
                    offered = release.takeIf { newer && !skipped },
                    readyFile = null,
                    message = if (newer && !skipped) null
                        else if (newer) "已跳过此版本。"
                        else "已经是最新版本。",
                )
            }
        } catch (e: CancellationException) {
            mutableState.update { it.copy(checking = false) }; throw e
        } catch (e: Exception) {
            mutableState.update {
                it.copy(checking = false, checked = true,
                    message = if (manual) "检查更新失败：${e.message ?: "网络暂不可用"}。可稍后重试，或从官网获取安装包。" else null)
            }
        }
    }

    suspend fun download(): Boolean {
        val release = mutableState.value.offered ?: return false
        if (mutableState.value.downloading) return false
        mutableState.update { it.copy(downloading = true, progress = 0, readyFile = null, message = null) }
        return try {
            val result = withContext(Dispatchers.IO) {
                val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IllegalStateException("手机存储暂不可用")
                if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建下载目录")
                val final = File(dir, "huaimin-${release.versionCode}.apk")
                val part = File(dir, "huaimin-${release.versionCode}.pending.apk")
                try {
                    if (final.exists()) {
                        if (verifyApk(final, release)) return@withContext final
                        final.delete()
                    }
                    val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 15_000
                        connection.readTimeout = 30_000
                        connection.instanceFollowRedirects = true
                        if (connection.responseCode != 200) throw IllegalStateException("下载服务返回 HTTP ${connection.responseCode}")
                        if (!UpdatePolicy.secureHttps(connection.url.toString())) throw IllegalStateException("下载地址不是 HTTPS")
                        val length = connection.contentLengthLong
                        if (length > UpdatePolicy.MAX_APK_BYTES) throw IllegalStateException("安装包异常大")
                        if (release.apkBytes != null && length > 0 && release.apkBytes != length)
                            throw IllegalStateException("安装包大小与发布信息不一致")
                        val digest = MessageDigest.getInstance("SHA-256")
                        var received = 0L
                        connection.inputStream.use { input ->
                            FileOutputStream(part).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    received += count
                                    if (received > UpdatePolicy.MAX_APK_BYTES) throw IllegalStateException("安装包超过限制")
                                    output.write(buffer, 0, count)
                                    digest.update(buffer, 0, count)
                                    if (received % (512 * 1024) < count) {
                                        val total = release.apkBytes ?: length.takeIf { it > 0 }
                                        if (total != null && total > 0)
                                            mutableState.update { it.copy(progress = ((received * 100 / total).toInt()).coerceIn(0, 99)) }
                                    }
                                }
                                output.fd.sync()
                            }
                        }
                        val actual = digest.digest().joinToString("") { "%02x".format(it) }
                        if (actual != release.sha256) throw IllegalStateException("SHA-256 校验失败：文件可能已损坏")
                        if (release.apkBytes != null && received != release.apkBytes)
                            throw IllegalStateException("文件大小与版本信息不一致")
                        if (!verifyPackageAndSignature(part, release.versionCode))
                            throw IllegalStateException("安装包的应用身份或签名与已安装版本不一致")
                        if (!part.renameTo(final)) throw IllegalStateException("保存安装包失败")
                        final
                    } finally { connection.disconnect() }
                } finally { part.delete() }
            }
            mutableState.update { it.copy(downloading = false, progress = 100, readyFile = result, message = "下载完成，等待 Android 确认安装。") }
            true
        } catch (e: CancellationException) {
            mutableState.update { it.copy(downloading = false) }; throw e
        } catch (e: Exception) {
            mutableState.update { it.copy(downloading = false, readyFile = null, message = "下载失败：${e.message ?: "请检查网络"}。可去官网下载。") }
            false
        }
    }

    private fun verifyApk(file: File, release: HuaiminRelease): Boolean {
        if (!file.exists() || file.length() < 1024) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == release.sha256 &&
            verifyPackageAndSignature(file, release.versionCode)
    }

    @Suppress("DEPRECATION")
    private fun verifyPackageAndSignature(file: File, expectedCode: Long): Boolean {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = app.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        val installed = app.packageManager.getPackageInfo(app.packageName, flags)
        if (archive.packageName != app.packageName || archive.longVersionCode != expectedCode) return false
        val existing = installed.signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet()
        val incoming = archive.signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet()
        return !existing.isNullOrEmpty() && existing == incoming
    }

    fun install(): Boolean {
        val apk = mutableState.value.readyFile ?: return false
        if (!apk.isFile) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
                val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(settings)
                mutableState.update { it.copy(message = "请在系统页面允许此应用安装更新，然后返回点「安装新版」。") }
                return false
            }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.startActivity(intent)
            true
        } catch (e: Exception) {
            mutableState.update { it.copy(message = "无法打开安装界面：${e.message ?: "请从官网下载"}") }
            false
        }
    }
}
