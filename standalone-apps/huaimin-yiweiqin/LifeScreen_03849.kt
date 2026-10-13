package com.cleo.cleos.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleo.cleos.data.ChinaWorkCalendar
import com.cleo.cleos.data.LifeOvertime
import com.cleo.cleos.data.LifeBook
import com.cleo.cleos.data.LifeProfile
import com.cleo.cleos.data.LifeProfessions
import com.cleo.cleos.data.LifeRules
import com.cleo.cleos.data.LifeSchedule
import com.cleo.cleos.glass.*
import com.cleo.cleos.ui.common.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A real, per-companion schedule and learning surface; all changes use LifeStore. */
@Composable
fun LifeScreen(onBack: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val palette = LocalGlassPalette.current
    val snapshot by c.life.state.collectAsStateWithLifecycle()
    val people by remember { c.companions.all }.collectAsStateWithLifecycle(emptyList())
    val current by remember { c.companions.current }.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val conversations by remember(selectedId) { c.db.conversations().observeFor(selectedId ?: 0L) }
        .collectAsStateWithLifecycle(emptyList())
    var choosingPerson by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf(false) }
    var addingOvertime by remember { mutableStateOf(false) }
    var studyBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lifeUnavailable by remember { mutableStateOf(false) }
    var studyingBookId by remember { mutableStateOf<String?>(null) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(people, current?.id) {
        if (people.none { it.id == selectedId }) selectedId = current?.id
            ?.takeIf { id -> people.any { it.id == id } } ?: people.firstOrNull()?.id
    }
    LaunchedEffect(c.life) {
        while (true) {
            try { c.life.tick() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                lifeUnavailable = true
                error = failure.message ?: "生活记录暂时无法读取，已暂停本页操作"
                break
            }
            clock = System.currentTimeMillis()
            delay(30_000)
        }
    }
    fun perform(action: suspend () -> Unit) {
        if (busy || lifeUnavailable) return
        busy = true
        scope.launch {
            try {
                action()
                clock = System.currentTimeMillis()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "操作未完成，请重试"
            } finally {
                busy = false
            }
        }
    }
    val bookImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform {
            val imported = withContext(Dispatchers.IO) { readLifeBook(context, uri) }
            studyBookId = c.life.importBook(imported.first, imported.second, "用户导入 · ${imported.first}")
            notice = "原文已保存。点击「让 TA 学习并测验」，由角色自己阅读与作答。"
        }
    }
    val person = people.firstOrNull { it.id == selectedId }
    val id = person?.id
    val profile = id?.let { snapshot.profiles[it] ?: c.life.profile(it) }
    val availability = id?.let { c.life.availability(it, clock) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    GlassPage(overlay = { page ->
        GlassTopBar(title = "生活与成长", subtitle = "工作 · 休息 · 阅读 · 能力", backdrop = page,
            leading = { GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回发现", onBack, page) })
    }) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().fadeUnderTopBar(top + TopBarHeight),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + TopBarHeight + 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LifeCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(person?.name?.ifBlank { "TA" } ?: "选择 AI 角色", color = palette.content,
                                fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text("每个角色有自己的作息、工资和知识", color = palette.contentSecondary, fontSize = 12.sp)
                        }
                        Box {
                            TextButton(onClick = { choosingPerson = true }, enabled = people.isNotEmpty()) { Text("切换角色") }
                            DropdownMenu(expanded = choosingPerson, onDismissRequest = { choosingPerson = false }) {
                                people.forEach { ta ->
                                    DropdownMenuItem(text = { Text(ta.name.ifBlank { "TA ${ta.id}" }) }, onClick = {
                                        selectedId = ta.id
                                        choosingPerson = false
                                        studyBookId = null
                                    })
                                }
                            }
                        }
                    }
                }
            }
            if (lifeUnavailable) item {
                LifeCard { Text("生活记录暂时不可用，已暂停操作以保留现有数据。请退出此页后重试。", color = palette.error, fontSize = 13.sp) }
            }
            if (id == null || profile == null || availability == null) {
                item { LifeCard { Text("请先在通讯录创建 AI 角色，再为 TA 安排工作与阅读。", color = palette.contentSecondary) } }
            } else {
                item {
                    LifeCard {
                        Text(if (profile.enabled) availability.label else "尚未开启生活作息", color = palette.content,
                            fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        val next = availability.busyUntil
                        Text(when {
                            !profile.enabled -> "开启后，工作和休息会影响 TA 回复消息的时间。"
                            availability.isBusy && profile.strict -> "消息会保留，${next?.let { lifeTime(it, profile.schedule.zoneId) } ?: "忙碌结束"}后继续回复。"
                            availability.isBusy -> "当前允许即时回复；可在作息设置中开启「忙碌时延后回复」。"
                            profile.wakeUntil > clock -> "本次唤醒至 ${lifeTime(profile.wakeUntil, profile.schedule.zoneId)}，之后恢复原作息。"
                            else -> "现在可以正常回复消息。"
                        }, color = palette.contentSecondary, fontSize = 13.sp)
                        Text("待回复 ${profile.pending.size} 项", color = palette.contentSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(enabled = !busy && !lifeUnavailable, onClick = { editingSchedule = true }) { Text(if (profile.enabled) "职业与作息" else "开始职业") }
                            if (profile.enabled) TextButton(enabled = !busy && !lifeUnavailable, onClick = { perform { c.life.toggle(id, false) } }) { Text("暂停生活作息") }
                        }
                        if (profile.enabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(enabled = !busy && !lifeUnavailable, onClick = { perform { c.life.rest(id, 30) } }) { Text("休息30分钟") }
                                TextButton(enabled = !busy && !lifeUnavailable, onClick = { perform { c.life.wake(id, 15) } }) { Text("唤醒15分钟") }
                                if (profile.restUntil > clock) TextButton(enabled = !busy && !lifeUnavailable, onClick = { perform { c.life.rest(id, 0) } }) { Text("结束休息") }
                            }
                            Text("休息和睡眠期间也会延后回复。临时唤醒只开放这段时间的聊天。", color = palette.contentSecondary, fontSize = 12.sp)
                        }
                    }
                }
                item {
                    LifeCard {
                        val profession = LifeProfessions.presets.firstOrNull { it.id == profile.professionId }
                        Text("${profession?.title ?: "职业"} · 薪资与经验", color = palette.content, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("上海 · 模拟月薪参考 ￥${LifeRules.effectiveMonthlyRmb(profile)} / 月", color = palette.content)
                        Text("人民币 ￥1 = 1 星币；仅在软件内使用，不支持真实兑换。", color = palette.contentSecondary, fontSize = 12.sp)
                        HorizontalDivider(color = palette.contentSecondary.copy(alpha = 0.15f))
                        LifeMetric("累计已赚", "${lifeCoins(profile.salaryAccruedCents)} 星币")
                        LifeMetric("已入钱包", "${lifeCoins(profile.salaryCents)} 星币")
                        LifeMetric("待结算", "${lifeCoins((profile.salaryAccruedCents - profile.salaryCents).coerceAtLeast(0L))} 星币")
                        LifeMetric("累计有效工作", lifeDuration(profile.workedMillis))
                        LifeMetric("累计加班时长", lifeDuration(profile.overtimeWorkedMillis))
                        LifeMetric("累计加班工资", lifeCoins(profile.overtimeSalaryCents) + " 星币")
                        LifeMetric("工作经验", "${profile.experiencePoints} 点")
                        Text("工资按有效工时累计，按小时入账；下班、改变作息或暂停时结算。休息与睡眠不计入工时。", color = palette.contentSecondary, fontSize = 12.sp)
                    }
                }
                item {
                    LifeCard {
                        val date = Instant.ofEpochMilli(clock).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
                        Text("真实日历 · 加班工资",color=palette.content,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        Text("今天 " + date + " · " + ChinaWorkCalendar.label(date),color=palette.content)
                        Text("工作日150% · 休息日200%（无补休）· 法定节假日300%",
                            fontSize=12.sp,color=palette.contentSecondary)
                        Text("2026年国务院官方调休安排；未发布年份不推测假期",
                            fontSize=11.sp,color=palette.contentSecondary)
                        if(profile.enabled) OutlinedButton(enabled=!busy&&!lifeUnavailable,
                            onClick={addingOvertime=true}) { Text("预约加班") }
                        if(profile.overtime.isEmpty()) Text("暂无加班预约",color=palette.contentSecondary,fontSize=12.sp)
                        profile.overtime.sortedByDescending { it.startAt }.take(10).forEach { shift ->
                            val day=Instant.ofEpochMilli(shift.startAt).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(day.toString()+" · "+ChinaWorkCalendar.label(day)+" · "+shift.ratePercent+"%",fontSize=13.sp,color=palette.content)
                                    Text(lifeTime(shift.startAt,profile.schedule.zoneId)+"—"+lifeTime(shift.endAt,profile.schedule.zoneId),
                                        fontSize=11.sp,color=palette.contentSecondary)
                                }
                                if(shift.startAt>clock) TextButton(enabled=!busy&&!lifeUnavailable,
                                    onClick={perform { c.life.cancelOvertime(id,shift.id) }}) { Text("取消") }
                            }
                        }
                    }
                }
                if (profile.pending.isNotEmpty()) item {
                    LifeCard {
                        Text("等待 TA 回复", color = palette.content, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        profile.pending.sortedBy { it.createdAt }.take(8).forEach { pending ->
                            val conversation = conversations.firstOrNull { it.id == pending.conversationId }
                            Text(conversation?.let { entry -> entry.title.ifBlank { if (entry.isGroup) "群聊" else "与 TA 的对话" } }
                                ?: "待回复对话", color = palette.content, fontSize = 13.sp)
                            Text("${if (conversation?.isGroup == true) "群聊" else "单聊"} · ${lifeTime(pending.createdAt, profile.schedule.zoneId)}起等待", color = palette.contentSecondary, fontSize = 11.sp)
                        }
                        if (profile.pending.size > 8) Text("共 ${profile.pending.size} 项，显示最早8项。", color = palette.contentSecondary, fontSize = 11.sp)
                    }
                }
                item {
                    LifeCard {
                        Text("技能与已验证知识", color = palette.content, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (profile.skills.isEmpty()) Text("通过工作积累经验，通过阅读和核对积累具体知识。", color = palette.contentSecondary, fontSize = 13.sp)
                        profile.skills.forEach { skill ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(skill.title, modifier = Modifier.weight(1f), color = palette.content)
                                Text("经验 ${skill.experiencePoints} · 知识 ${skill.verifiedKnowledgeCount}", color = palette.contentSecondary, fontSize = 12.sp)
                            }
                        }
                        Text("TA 已验证 ${profile.knowledge.size} 个知识片段。TA 自己通过测验的片段会用于后续聊天，不等同于精通整本书。", color = palette.contentSecondary, fontSize = 12.sp)
                        profile.knowledge.takeLast(3).reversed().forEach { knowledge ->
                            HorizontalDivider(color = palette.contentSecondary.copy(alpha = 0.12f))
                            Text(knowledge.title, color = palette.content, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(knowledge.excerpt, color = palette.contentSecondary, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Text("来源：${knowledge.source}", color = palette.contentSecondary, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    LifeCard {
                        Text("阅读与学习", color = palette.content, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("每次让 TA 学习一个片段：由 TA 的模型阅读真实正文、写学习笔记，再接受内容与应用测验。通过后才增加相应知识。", color = palette.contentSecondary, fontSize = 13.sp)
                        Text("学习使用该角色已配置的模型与 API。", color = palette.contentSecondary, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !busy && !lifeUnavailable, onClick = { bookImporter.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown", "application/octet-stream")) }) { Text("导入 TXT / Markdown") }
                            TextButton(enabled = !busy && !lifeUnavailable, onClick = { perform {
                                val bookId = c.life.addBuiltInBook()
                                studyBookId = bookId
                            } }) { Text("实用短课") }
                        }
                        Text("支持 UTF-8 文件，单本最多30万字。大型书籍最多选取80个片段供核对，保留全文。查看原文或人工答题不会替 TA 增加能力。", color = palette.contentSecondary, fontSize = 11.sp)
                        if (availability.isBusy) Text("TA 目前${availability.label}，空闲或临时唤醒后可以学习。", color = palette.accentContent, fontSize = 12.sp)
                        notice?.let { Text(it, color = palette.accentContent, fontSize = 12.sp) }
                    }
                }
                items(snapshot.books, key = { "book:${it.id}" }) { book ->
                    val reading = profile.readings.firstOrNull { it.bookId == book.id }
                    LifeCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(book.title, color = palette.content, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text("${book.source} · ${book.content.length} 字 · ${book.lessons.size} 个核对片段", color = palette.contentSecondary, fontSize = 11.sp)
                                Text("TA 已读 ${reading?.readLessonIds?.size ?: 0} 个片段 · 已通过 ${reading?.passedLessonIds?.size ?: 0}", color = palette.contentSecondary, fontSize = 12.sp)
                                reading?.let { Text("最近学习：${lifeTime(it.lastReadAt, profile.schedule.zoneId)}", color = palette.contentSecondary, fontSize = 11.sp) }
                            }
                            TextButton(onClick = { studyBookId = book.id }) {
                                Text("查看")
                                Icon(Icons.Rounded.ChevronRight, null)
                            }
                        }
                        OutlinedButton(enabled = !busy && !lifeUnavailable && !availability.isBusy, onClick = { perform {
                            studyingBookId = book.id
                            notice = "TA 正在阅读与测验，完成后会显示真实学习笔记。"
                            try { notice = c.lifeStudy.study(id, book.id) }
                            finally { studyingBookId = null }
                        } }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (studyingBookId == book.id) "正在阅读与测验…" else "让 TA 学习并测验")
                        }
                    }
                }
                item {
                    LifeCard {
                        Text("TA 的学习笔记与测验证据", color = palette.content, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        val evidence = profile.studyEvidence.filter { it.actor == "model" }.takeLast(4).reversed()
                        if (evidence.isEmpty()) Text("尚无角色学习记录。先选一本书，让 TA 阅读并接受测验。", color = palette.contentSecondary, fontSize = 13.sp)
                        evidence.forEach { note ->
                            val book = snapshot.books.firstOrNull { it.id == note.bookId }
                            val lesson = book?.lessons?.firstOrNull { it.id == note.lessonId }
                            HorizontalDivider(color = palette.contentSecondary.copy(alpha = 0.12f))
                            Text(lesson?.title ?: "学习片段", color = palette.content, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(if (note.passed) if (lesson?.transferChoices?.isNotEmpty() == true) "通过内容与应用测验" else "通过原文核对" else "测验未通过 · 尚未增加能力",
                                color = if (note.passed) palette.accentContent else palette.error, fontSize = 12.sp)
                            SelectionContainer { Text(note.summary, color = palette.content, fontSize = 13.sp) }
                            Text("内容题作答：${lesson?.choices?.getOrNull(note.answerIndex) ?: "无有效答案"}", color = palette.contentSecondary, fontSize = 12.sp)
                            if (lesson?.transferChoices?.isNotEmpty() == true) Text("应用题作答：${note.applicationAnswerIndex?.let { lesson.transferChoices.getOrNull(it) } ?: "尚未作答"}", color = palette.contentSecondary, fontSize = 12.sp)
                            Text("${book?.source ?: "原书来源"} · ${note.model} · ${lifeTime(note.at, profile.schedule.zoneId)}", color = palette.contentSecondary, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    LifeCard {
                        Text("工资流水 · 最近12笔", color = palette.content, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        val ledger = snapshot.settlements.filter { it.companionId == id }.takeLast(12).reversed()
                        if (ledger.isEmpty()) Text("开启职业并完成有效工作后，工资会进入角色钱包。", color = palette.contentSecondary, fontSize = 13.sp)
                        ledger.forEach { entry ->
                            LifeMetric("${lifeTime(entry.at, profile.schedule.zoneId)} · ${lifeDuration(entry.workedMillis)}",
                                "+${lifeCoins(entry.cents)} 星币${if (entry.paid) "" else " · 待入账"}")
                        }
                        Text("手机后台限制可能延后处理待回复消息；再次打开应用时会检查作息与待回复记录。", color = palette.contentSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    if (addingOvertime && id != null && profile != null) LifeOvertimeDialog(
        profile=profile, enabled=!busy&&!lifeUnavailable, now=clock,
        onDismiss={if(!busy) addingOvertime=false},
        onSave={from,to-> perform {
            c.life.scheduleOvertime(id,from,to)
            addingOvertime=false
        }}
    )
    if (editingSchedule && id != null && profile != null) LifeScheduleDialog(
        profile = profile, enabled = !busy && !lifeUnavailable,
        onDismiss = { if (!busy) editingSchedule = false },
        onSave = { professionId, schedule, strict, custom -> perform {
            c.life.configure(id, professionId, schedule, strict, customMonthlyRmb = custom)
            if (!profile.enabled) c.life.toggle(id, true)
            editingSchedule = false
        } },
    )
    snapshot.books.firstOrNull { it.id == studyBookId }?.let { book ->
        if (id != null && profile != null) LifeReadingDialog(
            book = book, profile = profile,
            onDismiss = { studyBookId = null },
        )
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("操作未完成") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } })
    }
}

@Composable
private fun LifeCard(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = GlassShape.Rounded(20.dp),
        contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun LifeMetric(label: String, value: String) {
    val p = LocalGlassPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = p.contentSecondary, fontSize = 12.sp)
        Text(value, color = p.content, fontSize = 13.sp)
    }
}

/** Shared banner in real single/group chat, with an explicit user override. */
@Composable
fun LifeChatBanner(people: List<Pair<Long, String>>, conversationId: Long? = null, modifier: Modifier = Modifier) {
    val c = appContainer()
    val p = LocalGlassPalette.current
    val snapshot by c.life.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var waking by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(people) {
        while (true) {
            clock = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val waiting = people.distinctBy { it.first }.mapNotNull { (id, name) ->
        val profile = snapshot.profiles[id] ?: return@mapNotNull null
        val availability = c.life.availability(id, clock)
        if (profile.enabled && profile.strict && availability.isBusy) Triple(name.ifBlank { "TA" }, profile, availability) else null
    }
    if (waiting.isEmpty()) return
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = GlassShape.Rounded(16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            waiting.take(3).forEach { (name, profile, availability) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$name · ${availability.label}", color = p.content, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        val pendingCount = profile.pending.count { conversationId == null || it.conversationId == conversationId }
                        Text("待回复 $pendingCount 项${availability.busyUntil?.let { " · ${lifeTime(it, profile.schedule.zoneId)}后恢复" } ?: ""}",
                            color = p.contentSecondary, fontSize = 11.sp)
                    }
                    TextButton(enabled = waking == null, onClick = {
                        waking = profile.companionId
                        scope.launch {
                            try { c.life.wake(profile.companionId, 15) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message ?: "唤醒未完成" }
                            finally { waking = null; clock = System.currentTimeMillis() }
                        }
                    }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("唤醒15分钟", fontSize = 11.sp) }
                }
            }
            if (waiting.size > 3) Text("另有 ${waiting.size - 3} 位角色忙碌，可在发现 → 生活与成长查看。", color = p.contentSecondary, fontSize = 11.sp)
        }
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("唤醒未完成") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } })
    }
}

@Composable
private fun LifeOvertimeDialog(profile: LifeProfile, enabled: Boolean, now: Long,
    onDismiss: () -> Unit, onSave: (Long,Long) -> Unit) {
    val zone=ZoneId.of(profile.schedule.zoneId)
    val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    var day by remember(profile.companionId) { mutableStateOf(today.toString()) }
    var start by remember(profile.companionId) { mutableStateOf("18:00") }
    var end by remember(profile.companionId) { mutableStateOf("21:00") }
    var error by remember { mutableStateOf<String?>(null) }
    val parsed=runCatching { LocalDate.parse(day) }.getOrNull()
    val rate=parsed?.let { runCatching { ChinaWorkCalendar.overtimeRate(it,profile.schedule) }.getOrNull() }
    AlertDialog(onDismissRequest=onDismiss,title={Text("预约加班")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("真实日期和节假日自动判定倍率；人民币1元=1星币",fontSize=12.sp)
            OutlinedTextField(value=day,onValueChange={day=it.take(10)},label={Text("日期 YYYY-MM-DD")},
                singleLine=true,enabled=enabled)
            Text((parsed?.let { ChinaWorkCalendar.label(it) } ?: "日期无效") +
                " · " + (rate?.let { it.toString()+"%" } ?: "尚未内置官方日历"))
            LifeTimeFields("开始",start,{start=it},"结束",end,{end=it},enabled)
            Text("只预约未来加班；工作日不超过3小时，单月不超过36小时。无法补休的休息日按照200%模拟结算。",
                fontSize=11.sp)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp) }
        }
    },confirmButton={TextButton(enabled=enabled && rate!=null,onClick={
        try {
            val date=requireNotNull(parsed) { "日期错误" }
            val from=date.atStartOfDay().plusMinutes(parseLifeMinute(start).toLong())
                .atZone(zone).toInstant().toEpochMilli()
            val to=date.atStartOfDay().plusMinutes(parseLifeMinute(end).toLong())
                .atZone(zone).toInstant().toEpochMilli()
            LifeRules.validateOvertime(profile,LifeOvertime(startAt=from,endAt=to,ratePercent=rate!!),
                System.currentTimeMillis())
            onSave(from,to)
        } catch(e:Exception) { error=e.message ?: "预约失败" }
    }) { Text("确认") }},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable
private fun LifeScheduleDialog(profile: LifeProfile, enabled: Boolean, onDismiss: () -> Unit,
    onSave: (String, LifeSchedule, Boolean, Int?) -> Unit) {
    val p = LocalGlassPalette.current
    var profession by remember(profile.companionId) { mutableStateOf(profile.professionId) }
    var professionMenu by remember { mutableStateOf(false) }
    var strict by remember(profile.companionId) { mutableStateOf(profile.strict) }
    var days by remember(profile.companionId) { mutableStateOf(profile.schedule.workDays.toSet()) }
    var start by remember { mutableStateOf(lifeMinute(profile.schedule.workStartMinute)) }
    var end by remember { mutableStateOf(lifeMinute(profile.schedule.workEndMinute)) }
    var lunchStart by remember { mutableStateOf(lifeMinute(profile.schedule.lunchStartMinute)) }
    var lunchEnd by remember { mutableStateOf(lifeMinute(profile.schedule.lunchEndMinute)) }
    var restStart by remember { mutableStateOf(lifeMinute(profile.schedule.restStartMinute)) }
    var restEnd by remember { mutableStateOf(lifeMinute(profile.schedule.restEndMinute)) }
    var sleepStart by remember { mutableStateOf(lifeMinute(profile.schedule.sleepStartMinute)) }
    var sleepEnd by remember { mutableStateOf(lifeMinute(profile.schedule.sleepEndMinute)) }
    var customSalary by remember { mutableStateOf(profile.customMonthlyRmb?.toString().orEmpty()) }
    var validation by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (profile.enabled) "职业与作息" else "开始职业") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { professionMenu = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(LifeProfessions.presets.firstOrNull { it.id == profession }?.let { "${it.title} · ￥${it.monthlyRmb}/月" } ?: "选择职业")
                    }
                    DropdownMenu(expanded = professionMenu, onDismissRequest = { professionMenu = false }) {
                        LifeProfessions.presets.forEach { job ->
                            DropdownMenuItem(text = { Text("${job.title} · ￥${job.monthlyRmb}/月") }, onClick = { profession = job.id; professionMenu = false })
                        }
                    }
                }
                Text("上海 · 模拟月薪参考，按有效工时折算星币；仅作为角色生活规则。", color = p.contentSecondary, fontSize = 12.sp)
                OutlinedTextField(value = customSalary, onValueChange = { customSalary = it.filter(Char::isDigit).take(5) },
                    label = { Text("自定义参考月薪（元，可留空）") }, singleLine = true, enabled = enabled,
                    supportingText = { Text("2740—60000 元；留空使用该职业参考值") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("忙碌时延后回复")
                        Text("工作、休息、睡眠期间保留消息，结束后继续回复。", fontSize = 11.sp, color = p.contentSecondary)
                    }
                    Switch(checked = strict, onCheckedChange = { strict = it }, enabled = enabled)
                }
                Text("工作日", fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..7).forEach { day ->
                        TextButton(onClick = { days = if (day in days) days - day else days + day },
                            enabled = enabled, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(32.dp)) {
                            Text(listOf("一", "二", "三", "四", "五", "六", "日")[day - 1],
                                fontWeight = if (day in days) FontWeight.Bold else FontWeight.Normal,
                                color = if (day in days) p.accentContent else p.contentSecondary)
                        }
                    }
                }
                LifeTimeFields("上班", start, { start = it }, "下班", end, { end = it }, enabled)
                LifeTimeFields("午休开始", lunchStart, { lunchStart = it }, "午休结束", lunchEnd, { lunchEnd = it }, enabled)
                LifeTimeFields("休息开始", restStart, { restStart = it }, "休息结束", restEnd, { restEnd = it }, enabled)
                LifeTimeFields("入睡", sleepStart, { sleepStart = it }, "醒来", sleepEnd, { sleepEnd = it }, enabled)
                Text("时间格式 09:00；采用上海时间。休息起止相同表示关闭该段休息。睡眠可跨午夜。", color = p.contentSecondary, fontSize = 11.sp)
                validation?.let { Text(it, color = p.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = enabled, onClick = {
                try {
                    require(LifeProfessions.presets.any { it.id == profession }) { "请选择职业" }
                    require(days.isNotEmpty()) { "至少选择一个工作日" }
                    val salary = customSalary.takeIf { it.isNotBlank() }?.toIntOrNull()
                    require(customSalary.isBlank() || salary != null && salary in LifeRules.MIN_MONTHLY_RMB..LifeRules.MAX_MONTHLY_RMB) { "参考月薪应为2740—60000元的整数" }
                    val schedule = profile.schedule.copy(zoneId = "Asia/Shanghai", workDays = days.sorted(),
                        workStartMinute = parseLifeMinute(start), workEndMinute = parseLifeMinute(end),
                        lunchStartMinute = parseLifeMinute(lunchStart), lunchEndMinute = parseLifeMinute(lunchEnd),
                        restStartMinute = parseLifeMinute(restStart), restEndMinute = parseLifeMinute(restEnd),
                        sleepStartMinute = parseLifeMinute(sleepStart), sleepEndMinute = parseLifeMinute(sleepEnd))
                    require(schedule.workStartMinute != schedule.workEndMinute) { "上班和下班时间不能相同" }
                    onSave(profession, schedule, strict, salary)
                } catch (failure: IllegalArgumentException) { validation = failure.message }
            }) { Text(if (profile.enabled) "保存作息" else "保存并开始") }
        },
        dismissButton = { TextButton(enabled = enabled, onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun LifeTimeFields(labelA: String, valueA: String, changeA: (String) -> Unit,
    labelB: String, valueB: String, changeB: (String) -> Unit, enabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(valueA, { changeA(it.take(5)) }, label = { Text(labelA) },
            singleLine = true, enabled = enabled, modifier = Modifier.weight(1f))
        OutlinedTextField(valueB, { changeB(it.take(5)) }, label = { Text(labelB) },
            singleLine = true, enabled = enabled, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LifeReadingDialog(book: LifeBook, profile: LifeProfile, onDismiss: () -> Unit) {
    val p = LocalGlassPalette.current
    var lessonIndex by remember(book.id) { mutableIntStateOf(0) }
    var answer by remember(book.id, lessonIndex) { mutableStateOf<Int?>(null) }
    var result by remember(book.id, lessonIndex) { mutableStateOf<Boolean?>(null) }
    var choosingLesson by remember { mutableStateOf(false) }
    var selfTest by remember { mutableStateOf(false) }
    var fullText by remember { mutableStateOf(false) }
    var fullTextPage by remember(book.id) { mutableIntStateOf(0) }
    val lesson = book.lessons.getOrNull(lessonIndex)
    val reading = profile.readings.firstOrNull { it.bookId == book.id }
    val pageCount = ((book.content.length + 5999) / 6000).coerceAtLeast(1)
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(book.source, color = p.contentSecondary, fontSize = 11.sp)
                Text("查看原文，不会替 TA 标记已读或增加能力。", color = p.contentSecondary, fontSize = 12.sp)
                Box {
                    TextButton(onClick = { choosingLesson = true }) { Text("片段 ${lessonIndex + 1} / ${book.lessons.size} · 选择片段") }
                    DropdownMenu(expanded = choosingLesson, onDismissRequest = { choosingLesson = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                        book.lessons.forEachIndexed { index, piece ->
                            DropdownMenuItem(text = { Text("${index + 1}. ${piece.title}${if (piece.id in reading?.passedLessonIds.orEmpty()) " · TA已通过" else ""}") },
                                onClick = { lessonIndex = index; fullText = false; choosingLesson = false })
                        }
                    }
                }
                if (lesson != null && !fullText) {
                    Text(lesson.title, fontWeight = FontWeight.SemiBold)
                    Text("原文第 ${lesson.startOffset + 1}—${lesson.endOffset} 字 · ${lesson.skillTitle}", color = p.contentSecondary, fontSize = 11.sp)
                    SelectionContainer { Text(lesson.excerpt, fontSize = 14.sp) }
                    if (lesson.id in reading?.passedLessonIds.orEmpty()) Text("TA 已通过此片段的测验", color = p.accentContent, fontSize = 12.sp)
                    TextButton(onClick = { selfTest = !selfTest }) { Text(if (selfTest) "收起人工自测" else "人工自测（仅自己练习）") }
                    if (selfTest) {
                        HorizontalDivider()
                        Text("人工自测 · 不计入 TA 的能力", color = p.accentContent, fontWeight = FontWeight.Medium)
                        Text(lesson.question, fontSize = 13.sp)
                        lesson.choices.forEachIndexed { index, choice ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = answer == index, onClick = { answer = index; result = null })
                                Text(choice, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            }
                        }
                        TextButton(enabled = answer != null, onClick = { result = answer == lesson.correctIndex }) { Text("检查我的答案") }
                        result?.let { passed ->
                            Text(if (passed) "你答对了。${lesson.explanation}" else "请对照原文重新核对。", color = if (passed) p.accentContent else p.error, fontSize = 12.sp)
                        }
                    }
                }
                TextButton(onClick = { fullText = !fullText }) { Text(if (fullText) "回到当前片段" else "查看保存的全文") }
                if (fullText) {
                    Text("全文第 ${fullTextPage + 1} / $pageCount 页", color = p.contentSecondary, fontSize = 12.sp)
                    val pageStart = (fullTextPage * 6000).coerceAtMost(book.content.length)
                    val pageEnd = (pageStart + 6000).coerceAtMost(book.content.length)
                    SelectionContainer { Text(book.content.substring(pageStart, pageEnd), fontSize = 14.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(enabled = fullTextPage > 0, onClick = { fullTextPage-- }) { Text("上一页") }
                        TextButton(enabled = fullTextPage < pageCount - 1, onClick = { fullTextPage++ }) { Text("下一页") }
                    }
                }
                if (lesson == null && !fullText) Text("此文档没有可核对的正文片段，可以查看保存的原文。", color = p.contentSecondary)
            }
        },
        confirmButton = { TextButton(onClick = {
            if (lessonIndex < book.lessons.lastIndex) { lessonIndex++; fullText = false } else onDismiss()
        }) { Text(if (lessonIndex < book.lessons.lastIndex) "下一片段" else "结束查看") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun parseLifeMinute(value: String): Int {
    require(Regex("\\d{1,2}:\\d{2}").matches(value)) { "请按09:00格式填写时间" }
    val parts = value.split(':').map { it.toInt() }
    require(parts[0] in 0..23 && parts[1] in 0..59) { "时间应在00:00—23:59之间" }
    return parts[0] * 60 + parts[1]
}

private fun lifeMinute(value: Int): String = "%02d:%02d".format(Locale.ROOT, value / 60, value % 60)
private fun lifeCoins(cents: Long): String = "%.2f".format(Locale.ROOT, cents / 100.0)
private fun lifeDuration(millis: Long): String {
    val minutes = millis.coerceAtLeast(0L) / 60_000L
    return if (minutes >= 60L) "${minutes / 60L}小时${minutes % 60L}分" else "${minutes}分钟"
}
private fun lifeTime(at: Long, zoneId: String): String = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    .withZone(runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.of("Asia/Shanghai"))).format(Instant.ofEpochMilli(at))

private fun readLifeBook(context: Context, uri: Uri): Pair<String, String> {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "导入书籍.txt"
    val lower = name.lowercase(Locale.ROOT)
    require(lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) { "请选择TXT或Markdown文件" }
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val block = ByteArray(8192)
        while (output.size() <= 1_200_000) {
            val read = input.read(block, 0, minOf(block.size, 1_200_001 - output.size()))
            if (read < 0) break
            if (read == 0) continue
            output.write(block, 0, read)
        }
        output.toByteArray()
    } ?: error("无法打开所选文件")
    require(bytes.size <= 1_200_000) { "文件超过1.2MB，请先分成较小章节后导入" }
    val text = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    } catch (_: Exception) { error("文件不是有效UTF-8文本，请转为UTF-8后再导入") }
    require(text.isNotBlank()) { "文件没有正文" }
    require(text.length <= LifeRules.MAX_BOOK_CHARS) { "单本最多30万字，请按章节分开导入" }
    return name.substringBeforeLast('.').take(100).ifBlank { "导入书籍" } to text
}
