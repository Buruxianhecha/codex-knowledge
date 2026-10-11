package com.cleo.cleos.ai

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.ceil

data class SongRequest(val conversationId: Long, val title: String, val artist: String)

/** AI tools never try to launch an Activity from a background worker.
 * The foreground chat handles all consent and external launches after the request is queued.
 */
object SongRequests {
    private val queue = MutableSharedFlow<SongRequest>(
        extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = queue.asSharedFlow()

    fun enqueue(conversationId: Long, title: String, artist: String): Boolean {
        val cleanTitle = title.trim().take(120)
        if (conversationId <= 0L || cleanTitle.isEmpty()) return false
        return queue.tryEmit(SongRequest(conversationId, cleanTitle, artist.trim().take(120)))
    }
}

/** Known player packages, enumerated explicitly for Android 11+ visibility (no QUERY_ALL_PACKAGES). */
data class SongPlayer(val name: String, val packageId: String)
enum class LaunchOutcome { PLAY_REQUESTED, SEARCH_OPENED, APP_OPENED, FAILED }

object MusicLaunch {
    val known = listOf(
        SongPlayer("QQ 音乐", "com.tencent.qqmusic"),
        SongPlayer("网易云音乐", "com.netease.cloudmusic"),
        SongPlayer("酷狗音乐", "com.kugou.android"),
        SongPlayer("酷我音乐", "cn.kuwo.player"),
        SongPlayer("咪咕音乐", "cmccwm.mobilemusic"),
    )

    fun installed(context: Context): List<SongPlayer> = known.filter {
        context.packageManager.getLaunchIntentForPackage(it.packageId) != null
    }

    fun open(context: Context, packageId: String): Boolean {
        if (packageId !in known.map { it.packageId } &&
            !MusicApps.counts(packageId)) return false
        return try {
            val launcher = context.packageManager.getLaunchIntentForPackage(packageId) ?: return false
            launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launcher)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Standard Android play-from-search; only the receiver decides whether to actually play.
     * Never claim a track is playing just because startActivity returned.
     */
    fun request(context: Context, player: SongPlayer, song: SongRequest): LaunchOutcome {
        if (player !in installed(context)) return LaunchOutcome.FAILED
        val query = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" ")
        val play = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(player.packageId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            .putExtra(MediaStore.EXTRA_MEDIA_TITLE, song.title)
            .putExtra(SearchManager.QUERY, query)
        if (song.artist.isNotBlank()) play.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, song.artist)
        if (play.resolveActivity(context.packageManager) != null &&
            runCatching { context.startActivity(play) }.isSuccess) return LaunchOutcome.PLAY_REQUESTED

        val search = Intent(Intent.ACTION_SEARCH)
            .setPackage(player.packageId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(SearchManager.QUERY, query)
        if (search.resolveActivity(context.packageManager) != null &&
            runCatching { context.startActivity(search) }.isSuccess) return LaunchOutcome.SEARCH_OPENED

        return if (open(context, player.packageId)) LaunchOutcome.APP_OPENED else LaunchOutcome.FAILED
    }

    /** Music stream only, not ringtone or alarms. Normal volume is never modified. */
    fun musicVolumePercent(context: Context): Int {
        val mgr = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val value = mgr.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (100.0 * value / max).toInt().coerceIn(0, 100)
    }

    /**
     * 0%~19% -> 20% (rounded up to closest hardware step). 20%~100% untouched.
     * Android can forbid changes while DND or volume-fixed mode is active; caller reports it.
     */
    fun normalizeLowMusicVolume(context: Context): Boolean {
        val mgr = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = mgr.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current.toDouble() / max >= 0.20) return true
        if (mgr.isVolumeFixed) return false
        val step = ceil(max * 0.20).toInt().coerceAtLeast(1)
        return runCatching { mgr.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0) }.isSuccess
    }

    fun requiresLoudnessConfirmation(volume: Int): Boolean = volume >= 80
}
