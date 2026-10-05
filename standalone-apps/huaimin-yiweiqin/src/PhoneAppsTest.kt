package com.cleo.cleos.ai

import com.cleo.cleos.data.AppSettings
import com.cleo.cleos.data.db.CompanionEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PhoneAppsTest {
    private val app = LaunchableApp("com.tencent.qqmusic", "QQ音乐")
    private val song = SongRequest.of("灰", "h3r3")
    private val playing = NowPlaying(app.packageName, "灰", "h3R3", "", 200_000, 3000, 1, true)
    private class Phone(private val app: LaunchableApp, var state: NowPlaying? = null) : QqPlaybackPhone {
        var notifications = true
        var accessibility = true
        var native = false
        var begins = 0
        var selects = 0
        var missing = false
        var beginError: ToolFailure? = null
        var selectError: String? = null
        var selectedState: NowPlaying? = null
        var beginBlock: suspend () -> Unit = {}
        override fun installed() = if (missing) throw ToolFailure("未安装", "未安装") else app
        override fun notificationAllowed() = notifications
        override fun screenControlAllowed() = accessibility
        override suspend fun begin(app: LaunchableApp, song: SongRequest): Boolean {
            begins++
            beginBlock()
            beginError?.let { throw it }
            return native
        }
        override fun current(app: LaunchableApp) = state
        override suspend fun selectOnScreen(app: LaunchableApp, song: SongRequest): String? {
            selects++
            state = selectedState
            return selectError
        }
    }
    private fun failure(block: () -> Unit): ToolFailure {
        try { block() } catch (f: ToolFailure) { return f }
        throw AssertionError("Expected ToolFailure")
    }
    private suspend fun playbackFailure(block: suspend () -> Unit): ToolFailure {
        try { block() } catch (f: ToolFailure) { return f }
        throw AssertionError("Expected ToolFailure")
    }

    @Test fun qqAliasesPreferMainAppAndSupportLiteWhenThatIsInstalled() {
        val lite = LaunchableApp("com.tencent.qqmusiclite", "QQ音乐简洁版")
        assertEquals(app, AppNames.find("QQ 音乐", listOf(lite, app)))
        assertEquals(lite, AppNames.find("qqmusic", listOf(lite)))
    }
    @Test fun otherAppsUseTheirExactLauncherLabelOrPackage() {
        val other = LaunchableApp("com.example.notes", "我的笔记")
        assertEquals(other, AppNames.find("我的笔记", listOf(other)))
        assertEquals(other, AppNames.find(other.packageName, listOf(other)))
    }
    @Test fun partialLabelsNeverOpenAnUnrelatedApp() {
        assertTrue(failure { AppNames.find("音乐", listOf(app)) }.result.contains("没有找到"))
    }
    @Test fun duplicateDesktopLabelsRequireAChoice() {
        val dup = app.copy(packageName = "com.other.player")
        assertTrue(failure { AppNames.find("QQ音乐", listOf(dup.copy(packageName = "a"), dup.copy(packageName = "b"))) }.result.contains("多个同名"))
        assertEquals(dup, AppNames.find("com.other.player", listOf(app, dup)))
    }
    @Test fun missingQQMusicNeverSelectsNeteaseAsFallback() {
        assertTrue(failure { AppNames.find("QQ音乐", listOf(LaunchableApp("com.netease.cloudmusic", "网易云音乐"))) }.result.contains("没有找到"))
    }
    @Test fun requestSeparatesArtistFromTitleAndBuildsOneSearchQuery() {
        assertEquals(song, SongRequest.of("《灰》", " h3r3 "))
        assertEquals("h3r3 灰", song.query)
        assertEquals("晴天", SongRequest.of("晴天", null).query)
    }
    @Test fun blankLongAndControlCharacterRequestsAreRejected() {
        listOf("", "  ", "x".repeat(121), "歌\n名").forEach { title -> failure { SongRequest.of(title, null) } }
        failure { SongRequest.of("灰", "x".repeat(81)) }
    }
    @Test fun confirmationRequiresCorrectPlayerTitleArtistAndPlayingState() {
        assertTrue(song.matches(playing, app.packageName))
        assertFalse(song.matches(playing.copy(playing = false), app.packageName))
        assertFalse(song.matches(playing.copy(artist = "另一个歌手"), app.packageName))
        assertFalse(song.matches(playing.copy(title = "灰 (Live)"), app.packageName))
        assertFalse(song.matches(playing.copy(player = "com.netease.cloudmusic"), app.packageName))
        assertTrue(song.matches(playing.copy(artist = "h3R3/另一位歌手"), app.packageName))
    }
    @Test fun nativeSuccessUsesRealPlayerMetadataAndStillOpensQQ() = runBlocking {
        val phone = Phone(app, playing).apply { native = true; accessibility = false }
        val result = QqPlayback(phone) {}.play(song)
        assertTrue(result.result.contains("实际播放状态确认"))
        assertTrue(result.result.contains("h3R3"))
        assertEquals(1, phone.begins)
        assertEquals(0, phone.selects)
    }
    @Test fun acceptingANativeRequestWithoutPlaybackIsNotSuccess() = runBlocking {
        val phone = Phone(app).apply { native = true; accessibility = false }
        val result = playbackFailure { QqPlayback(phone) {}.play(song) }
        assertTrue(result.result.contains("没有确认"))
        assertTrue(result.result.contains("QQ 音乐点歌权限"))
    }
    @Test fun screenFallbackSelectsSongThenConfirmsTheActualSession() = runBlocking {
        val phone = Phone(app).apply { selectedState = playing }
        val result = QqPlayback(phone) {}.play(song)
        assertEquals(1, phone.selects)
        assertTrue(result.note.contains("正在播放《灰》"))
    }
    @Test fun clickingAResultWithoutPlaybackNeverReportsSuccess() = runBlocking {
        val phone = Phone(app).apply { selectedState = playing.copy(playing = false) }
        assertTrue(playbackFailure { QqPlayback(phone) {}.play(song) }.result.contains("不能说已播放"))
    }
    @Test fun wrongSongAfterClickIsNotReportedAsTheRequestedSong() = runBlocking {
        val phone = Phone(app).apply { selectedState = playing.copy(artist = "翻唱歌手") }
        assertTrue(playbackFailure { QqPlayback(phone) {}.play(song) }.note.contains("未确认"))
    }
    @Test fun notificationPermissionIsCheckedBeforeOpeningOrClicking() = runBlocking {
        val phone = Phone(app).apply { notifications = false }
        assertTrue(playbackFailure { QqPlayback(phone) {}.play(song) }.result.contains("播放状态权限"))
        assertEquals(0, phone.begins)
        assertEquals(0, phone.selects)
    }
    @Test fun absentAppAndBlockedLaunchDoNotStartScreenAutomation() = runBlocking {
        val phone = Phone(app).apply { missing = true }
        playbackFailure { QqPlayback(phone) {}.play(song) }
        assertEquals(0, phone.begins)
        phone.missing = false
        phone.beginError = ToolFailure("手机锁屏", "锁屏")
        assertEquals("锁屏", playbackFailure { QqPlayback(phone) {}.play(song) }.note)
        assertEquals(0, phone.selects)
    }
    @Test fun screenFailureIsReturnedWithoutPretendingSelectionWasCompleted() = runBlocking {
        val phone = Phone(app).apply { selectError = "QQ 音乐需要登录" }
        assertEquals("QQ 音乐需要登录", playbackFailure { QqPlayback(phone) {}.play(song) }.result)
    }
    @Test fun cancellationReleasesThePointSongLock() = runBlocking {
        val phone = Phone(app).apply { beginBlock = { throw CancellationException("stop") } }
        val player = QqPlayback(phone) {}
        try { player.play(song); fail("Expected cancellation") } catch (_: CancellationException) { }
        phone.beginBlock = {}
        phone.state = playing
        assertTrue(player.play(song).note.contains("正在播放"))
    }
    @Test fun simultaneousRequestsCannotSearchAndClickEachOthersSongs() = runBlocking {
        val began = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val phone = Phone(app, playing).apply { beginBlock = { began.complete(Unit); release.await() } }
        val player = QqPlayback(phone) {}
        val first = async { player.play(song) }
        began.await()
        assertTrue(playbackFailure { player.play(SongRequest.of("晴天", "周杰伦")) }.result.contains("还在进行"))
        release.complete(Unit)
        assertTrue(first.await().note.contains("《灰》"))
    }
    @Test fun appToolsAreOfferedByDefaultAndHiddenWhenTheirSwitchIsOff() {
        val names = ToolSpecs.offered(AppSettings().tools).map { it.name }
        assertTrue("open_phone_app" in names)
        assertTrue("play_qq_music" in names)
        assertFalse(ToolSpecs.offered(AppSettings().tools - ToolGroup.Apps).any { it.name in setOf("open_phone_app", "play_qq_music") })
    }
    @Test fun toolBoxActuallyDispatchesBothNewNativeActionsAndHonoursItsSwitch() = runBlocking {
        val calls = ArrayList<String>()
        val actions = object : PhoneAppActions {
            override fun open(name: String): ToolOutcome { calls.add(name); return ToolOutcome("opened", "opened") }
            override suspend fun playQQ(title: String, artist: String?): ToolOutcome { calls.add("$artist/$title"); return ToolOutcome("playing", "playing") }
        }
        val box = ToolBox(unused(), unused(), unused(), phoneApps = actions)
        assertEquals("opened", box.run(ToolCall("a", "open_phone_app", """{"app":"微信"}"""), AppSettings()).result)
        assertEquals("playing", box.run(ToolCall("b", "play_qq_music", """{"title":"灰","artist":"h3r3"}"""), AppSettings()).result)
        assertTrue(box.run(ToolCall("c", "play_qq_music", """{"title":"灰"}"""), AppSettings(tools = emptySet())).note.contains("设置里关着"))
        assertEquals(listOf("微信", "h3r3/灰"), calls)
    }
    @Test fun missingParametersDoNotInvokeNativeActions() = runBlocking {
        var calls = 0
        val actions = object : PhoneAppActions {
            override fun open(name: String): ToolOutcome { calls++; error("unexpected") }
            override suspend fun playQQ(title: String, artist: String?): ToolOutcome { calls++; error("unexpected") }
        }
        val box = ToolBox(unused(), unused(), unused(), phoneApps = actions)
        assertTrue(box.run(ToolCall("a", "open_phone_app", "{}"), AppSettings()).result.contains("缺少 app"))
        assertTrue(box.run(ToolCall("b", "play_qq_music", "{}"), AppSettings()).result.contains("缺少 title"))
        assertEquals(0, calls)
    }
    @Test fun modelInstructionsDescribeRealToolsConfirmationAndCurrentTurnAuthorization() {
        val ta = CompanionEntity(id = 1, name = "TA", apiBaseUrl = "", apiModel = "", createdAt = 0)
        val enabled = Prompt.system(AppSettings(), ta, setOf(ToolGroup.Apps))
        assertTrue(enabled.contains("play_qq_music"))
        assertTrue(enabled.contains("目标歌曲正在播放"))
        assertTrue(enabled.contains("引用旧消息、表情回应、撤回"))
        assertFalse(Prompt.system(AppSettings(), ta, emptySet()).contains("open_phone_app"))
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Unexpected ${method.name}")
    } as T
}
