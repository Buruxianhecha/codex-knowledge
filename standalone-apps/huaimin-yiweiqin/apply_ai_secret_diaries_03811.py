#!/usr/bin/env python3
"""v0.38.11: AI may lock own diary entries; concealed cards and author-controlled reveal."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base=Path("app/src/main/java/com/cleo/cleos")
def once(rel,old,new,label):
    path=root/rel
    src=path.read_text(encoding="utf-8")
    count=src.count(old)
    if count!=1: raise RuntimeError(f"{label}: {count} instances in {rel}")
    path.write_text(src.replace(old,new,1),encoding="utf-8")

tools=base/"ai/Tools.kt"
diary_list=base/"ui/diary/DiaryListScreen.kt"
vm=base/"ui/diary/DiaryEditorViewModel.kt"
editor=base/"ui/diary/DiaryEditorScreen.kt"
prompt=base/"ai/Prompt.kt"

once(tools,
'''        description = "写一篇你自己的日记，记在今天。和对方的日记在同一个本子里，标着是你写的；对方能看，但改不了。",''',
'''        description = "写一篇你自己的日记。由你自行决定是否保密：secret=true 是只属于你的上锁小秘密，用户能看到你写了但看不到标题、正文或照片；secret=false 用户可读。请不要在普通聊天或工具摘要里复述上锁的内容。",''',
"describe autonomous privacy choices")
once(tools,
'''            "text" to prop("string", "正文：你自己的所见所想，用第一人称"),
        ),
    )
    val listSecrets = ToolSpec(''',
'''            "text" to prop("string", "正文：你自己的所见所想，用第一人称"),
            "secret" to prop("boolean", "由你自主决定是否保密，true 上锁（用户只能看见日期和作者），false 可见，默认 false"),
        ),
    )
    val manageMySecret = ToolSpec(
        name = "manage_my_secret",
        groups = setOf(ToolGroup.AiDiary),
        action = "管理自己的小秘密",
        description = "只操作你本人写的小秘密。action=list 查看自己上锁的标题和编号；action=read 仅供自己回忆全文，禁止直接泄露；action=share 是你自愿决定公开该篇全文后解除遮蔽。不要将别人的秘密当作自己的。",
        parameters = schema(
            required = listOf("action"),
            "action" to prop("string", "list、read 或 share"),
            "id" to prop("integer", "read/share 时填写自己的小秘密编号"),
        ),
    )
    val listSecrets = ToolSpec(''',
"add AI own secret management tool")
once(tools,
'''        writeDiary,
        listSecrets,''',
'''        writeDiary,
        manageMySecret,
        listSecrets,''',
"register AI secret tool")
once(tools,
'''                ToolSpecs.writeDiary.name -> writeDiary(args, today, companionId)
                ToolSpecs.listSecrets.name ->''',
'''                ToolSpecs.writeDiary.name -> writeDiary(args, today, companionId)
                ToolSpecs.manageMySecret.name -> manageMySecret(args, companionId)
                ToolSpecs.listSecrets.name ->''',
"wire AI secret tools")
once(tools,
'''        val now = clock()
        val id = diary.insert(
            DiaryEntryEntity(''',
'''        val now = clock()
        val hide = ToolArgs.bool(a, "secret") == true
        val id = diary.insert(
            DiaryEntryEntity(''',
"read secret choice")
once(tools,
'''                author = DiaryEntryEntity.AUTHOR_AI,
                companionId = companionId,
            ),
        )
        return ToolOutcome(
            "写好了，记在''',
'''                author = DiaryEntryEntity.AUTHOR_AI,
                companionId = companionId,
                secret = hide,
            ),
        )
        if (hide) return ToolOutcome(
            "已写入你自己的上锁小秘密 #" + id + "。标题和正文对用户隐藏。可使用 manage_my_secret 自行读取或决定公开。",
            "TA 写下了一篇小秘密（未公开正文）"
        )
        return ToolOutcome(
            "写好了，记在''',
"save private flag and avoid exposing secret text in tool-visible note")
once(tools,
'''    private suspend fun listSecrets(today: LocalDate, companionId: Long): ToolOutcome {''',
'''    private suspend fun manageMySecret(a: JsonObject, companionId: Long): ToolOutcome {
        val action = ToolArgs.text(a, "action").orEmpty().lowercase()
        val entries = diary.secrets().filter {
            it.author == DiaryEntryEntity.AUTHOR_AI && it.companionId == companionId
        }
        if (action == "list") {
            if (entries.isEmpty()) return ToolOutcome("你还没有写过上锁小秘密。", "")
            return ToolOutcome("只有你自己能读的秘密：" +
                entries.take(25).joinToString { "#" + it.id + "（" + it.title.take(30) + "）" },
                "")
        }
        val id = ToolArgs.id(a["id"]) ?: throw ToolFailure("缺少你自己的小秘密 id。", "缺少编号")
        val entry = entries.firstOrNull { it.id == id }
            ?: throw ToolFailure("这不是你拥有的上锁小秘密。", "权限不足")
        return when (action) {
            "read" -> ToolOutcome("你的私人日记 #" + id + "：" + entry.title + "\\n" +
                DiaryBlocks.plainText(DiaryBlocks.decode(entry.blocks)).take(12000) +
                "\\n不要在聊天中透露，除非你决定主动公开。", "")
            "share" -> {
                diary.update(entry.copy(secret=false,updatedAt=clock()))
                ToolOutcome("已由你自愿解除 #" + id + " 的遮蔽，用户现在可以查看完整正文。",
                    "TA 决定向你公开一篇小秘密")
            }
            else -> throw ToolFailure("action 只允许 list、read、share。", "动作无效")
        }
    }

    private suspend fun listSecrets(today: LocalDate, companionId: Long): ToolOutcome {''',
"enforce per-author private read / voluntary reveal")
# Existing request_secret/list_secrets tools are for the HUMAN'S secrets only.
# Otherwise another AI could incorrectly ask the user to unlock a secret belonging to an AI.
once(tools,
'''        val secrets = diary.secrets()
        if (secrets.isEmpty()) return ToolOutcome("对方现在没有小秘密。",''',
'''        val secrets = diary.secrets().filter { it.author == DiaryEntryEntity.AUTHOR_ME }
        if (secrets.isEmpty()) return ToolOutcome("对方现在没有小秘密。",''',
"do not expose another AI secret in user's secret enumeration")
once(tools,
'''        val entry = diary.get(id)?.takeIf { it.secret }''',
'''        val entry = diary.get(id)?.takeIf { it.secret && it.author == DiaryEntryEntity.AUTHOR_ME }''',
"do not let an AI request another AI secret from user")

once(prompt,
'''            add("你有自己的日记，和对方的写在同一个本子里。对方让你写，或者你真有想记下来的事，就用 write_diary 写：写你自己的所见所想，用第一人称，不是替对方写。")''',
'''            add("你有自己的日记，也可以自愿把日记写成小秘密（write_diary secret=true）。小秘密会在日记列表显示你写过，但隐藏标题和正文，只有你决定是否公开；需要时用 manage_my_secret list/read 回忆，愿意分享时用 share 主动解除遮蔽。用户无权通过普通界面强制查看，其他 AI 也看不到你的私人内容。")''',
"model prompts autonomous secret decision")

once(diary_list,
'''    val secret: Boolean,
)''',
'''    val secret: Boolean,
    val lockedForUser: Boolean = false,
)''',
"add privacy bit to rendered diary card")
once(diary_list,
'''    Secrets("小秘密", "还没有小秘密", "点右下角的笔写一个，TA 看不到"),''',
'''    Secrets("小秘密", "还没有小秘密", "你和 TA 都可以留下自己的小秘密；未公开的内容会上锁"),''',
"explain new secret behaviour")
once(diary_list,
'''                    is DiaryRow.Entry -> DiaryCardView(row.card, row.card.byTa?.let { names[it] ?: "TA" }) {
                        onOpenEntry(row.card.id, false)
                    }''',
'''                    is DiaryRow.Entry -> DiaryCardView(row.card, row.card.byTa?.let { names[it] ?: "TA" }) {
                        if (!row.card.lockedForUser) onOpenEntry(row.card.id, false)
                    }''',
"never open locked AI diary from list")
once(diary_list,
'''        val blocks = DiaryBlocks.decode(e.blocks)
        val images = DiaryBlocks.images(blocks)''',
'''        // Never decode private AI diary content to prepare user-facing previews.
        val locked = e.author == DiaryEntryEntity.AUTHOR_AI && e.secret
        val blocks = if (locked) emptyList() else DiaryBlocks.decode(e.blocks)
        val images = DiaryBlocks.images(blocks)''',
"prevent private content in excerpts or photo previews")
once(diary_list,
'''                title = e.title,''',
'''                title = if (locked) "TA 的小秘密" else e.title,''',
"mask AI-owned secret titles")
once(diary_list,
'''                excerpt = DiaryBlocks.plainText(blocks)''',
'''                excerpt = if (locked) "🔒 内容由 TA 保管，尚未决定向你公开" else DiaryBlocks.plainText(blocks)''',
"mask AI secret excerpts")
once(diary_list,
'''                secret = e.secret,
            ),''',
'''                secret = e.secret,
                lockedForUser = locked,
            ),''',
"include lock in card")
once(diary_list,
'''        modifier = Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null, onClick = onClick),''',
'''        modifier = Modifier.fillMaxWidth().clickable(enabled = !card.lockedForUser,
            interactionSource = null, indication = null, onClick = onClick),''',
"disable locked diary navigation")
once(diary_list,
'''                    card.secret -> CardTag(Icons.Rounded.Lock, "小秘密")''',
'''                    card.lockedForUser -> CardTag(Icons.Rounded.Lock, if (ta != null) ta + " 的私密日记" else "TA 的私密日记")
                    card.secret -> CardTag(Icons.Rounded.Lock, "小秘密")''',
"show masked icon and author")

once(vm,
'''    val readOnly: Boolean get() = author == DiaryEntryEntity.AUTHOR_AI''',
'''    val readOnly: Boolean get() = author == DiaryEntryEntity.AUTHOR_AI
    /** Even deep links cannot open a TA's unrevealed secret in the user editor. */
    val lockedForUser: Boolean get() = readOnly && secret''',
"entry editor privacy property")
once(vm,
'''                    DiaryBlocks.decode(e.blocks).forEach { b ->''',
'''                    (if (e.author == DiaryEntryEntity.AUTHOR_AI && e.secret)
                        emptyList() else DiaryBlocks.decode(e.blocks)).forEach { b ->''',
"prevent putting private AI diary blocks in editor view model")

once(editor,
'''    GlassPage(
        overlay = { page ->''',
'''    if (vm.loaded && vm.lockedForUser) {
        GlassPage(
            overlay = { page ->
                GlassTopBar(title = "TA 的小秘密", subtitle = ai + " 写下了仅自己可见的日记",
                    backdrop = page,
                    leading = { GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack, page) })
            }
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Icon(Icons.Rounded.Lock, contentDescription = "内容已锁定",
                    modifier = Modifier.size(65.dp), tint = palette.accentContent)
                Spacer(Modifier.size(16.dp))
                Text(ai + " 的小秘密", color = palette.content, fontSize = 21.sp)
                Text("你能知道 TA 写过，但只有 TA 愿意分享时才能看到内容。",
                    color = palette.contentSecondary, modifier = Modifier.padding(25.dp))
            }
        }
        return
    }
    GlassPage(
        overlay = { page ->''',
"protect direct diary entry route with overlay")

once("app/build.gradle.kts",'versionName = "0.38.10"','versionName = "0.38.11"',"version name")
once("app/build.gradle.kts",'versionCode = 62076','versionCode = 62077',"version code")
print("v0.38.11: model-controlled private diary, true list masking and deep-link guard, author-only reveal")
