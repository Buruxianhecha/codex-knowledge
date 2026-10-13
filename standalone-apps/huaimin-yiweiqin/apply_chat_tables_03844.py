#!/usr/bin/env python3
"""0.38.44: structured table tool AND legacy Markdown table rendering, no migration."""
from pathlib import Path
from shutil import copyfile
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def one(path,old,new):
    text=path.read_text(encoding="utf-8")
    count=text.count(old)
    if count!=1:
        raise RuntimeError(f"0.38.44 patch prerequisite mismatch {path.name}: {old[:115]!r} (found {count})")
    path.write_text(text.replace(old,new,1),encoding="utf-8")

data=root/"app/src/main/java/com/cleo/cleos/data"
ai=root/"app/src/main/java/com/cleo/cleos/ai"
ui=root/"app/src/main/java/com/cleo/cleos/ui/chat"
copyfile(here/"ChatTables_03844.kt",data/"ChatTables.kt")
copyfile(here/"ChatTableCard_03844.kt",ui/"ChatTableCard.kt")

tools=ai/"Tools.kt"
one(tools,'import com.cleo.cleos.data.Companions\n',
    'import com.cleo.cleos.data.Companions\nimport com.cleo.cleos.data.ChatTables\n')
one(tools,
    '    val webSendUrls: List<String> = emptyList(),\n',
    '''    val webSendUrls: List<String> = emptyList(),
    /** Versioned table message content; repository persists it as a normal assistant row. */
    val tablePayload: String? = null,
''')
one(tools,
    '    val sendWebImage = ToolSpec(\n',
    '''    val sendTable = ToolSpec(
        name = "send_table",
        groups = setOf(ToolGroup.Messages),
        action = "制作表格",
        description = "将内容真正发送为聊天内整齐的结构化表格卡片，不要输出用竖线拼起来的Markdown字符串。用户说‘做成表格’‘整理图片里的信息成表格’‘分类比较’‘列成表格’时使用。columns 是列名字符串数组；rows 是二维单元格数组，每行列数必须匹配。尽可能完整保留原资料并准确区分事实与未经核实的标记，原图标注不要伪称已核实。可连续调用多次产生多张表，按主题分类，用户无需复制代码。最多6列、120行、每个单元格600字；列多时会横向滚动、表可展开、复制。已用工具发出的表格不要再用普通回复复述。",
        parameters = schema(
            required = listOf("columns", "rows"),
            "title" to prop("string", "可选表格标题，如 社交平台、搜索与研究、系统工具"),
            "columns" to buildJsonObject {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
                put("description", "2到6个列名，如 [应用, 官方 / 第三方, 核心能力]")
            },
            "rows" to buildJsonObject {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                put("description", "二维数据，每个内部数组都是一行，单元格按columns的顺序排列")
            },
        ),
    )

    val sendWebImage = ToolSpec(
''')
one(tools,'        sendWebImage,\n','        sendWebImage,\n        sendTable,\n')
one(tools,
    '                ToolSpecs.sendWebImage.name -> sendWebImage(args)\n',
    '                ToolSpecs.sendWebImage.name -> sendWebImage(args)\n                ToolSpecs.sendTable.name -> sendTable(args)\n')
one(tools,
    '    private fun sendWebImage(a: JsonObject): ToolOutcome {',
    '''    private fun sendTable(a: JsonObject): ToolOutcome {
        val table = ChatTables.fromArgs(a)
            ?: throw ToolFailure("表格数据不合法：至少2列、各行列数一致、最多6列120行，列名不能为空。请按真实资料修正后重试。", "行列不匹配")
        return ToolOutcome(
            result = "表格已通过结构校验，等待应用真正保存为表格卡片。不要重复发送Markdown文字表格。",
            note = "正在制作表格",
            tablePayload = ChatTables.encode(table),
        )
    }

    private fun sendWebImage(a: JsonObject): ToolOutcome {''')

prompt=ai/"Prompt.kt"
one(prompt,
    '    private const val FORMAT_RULE = "这是手机上的聊天。像平常发消息那样回复，不用 Markdown 标题、列表和加粗。"',
    '    private const val FORMAT_RULE = "这是手机上的聊天。平常像人一样自然回复，不用复杂Markdown排版；但用户要求表格时必须优先输出真正的表格卡片，而不是竖线拼凑的文字。"')
one(prompt,
    '''        if (ToolGroup.Messages in tools) add("你有能力真正为自己改名''',
    '''        if (ToolGroup.Messages in tools) add("用户要求‘做成表格’‘对比表’‘将图片整理成表格’时，使用 send_table 传真实列名数组和二维行数据，而不是把 | 表格字符直接当消息发出。识别图片时先辨认列行，按需要分成多张有标题的表，忠实转录，不编造图里没出现的来源或官方认证。每个表格可以在手机聊天内真正显示、滑动、展开、复制；工具成功后不必再次发相同Markdown。")
        if (ToolGroup.Messages in tools) add("你有能力真正为自己改名''')

chat=ai/"ChatRepository.kt"
one(chat,
    'import com.cleo.cleos.data.MessageImages\n',
    'import com.cleo.cleos.data.MessageImages\nimport com.cleo.cleos.data.ChatTables\n')
one(chat,
    '''                        if (outcome.webSendUrls.isNotEmpty()) {
                            outcome = persistWebImages(conversationId, ta.id, outcome.webSendUrls,
                                background, null)
                        }''',
    '''                        if (outcome.webSendUrls.isNotEmpty()) {
                            outcome = persistWebImages(conversationId, ta.id, outcome.webSendUrls,
                                background, null)
                        }
                        if (outcome.tablePayload != null) {
                            outcome = persistTable(conversationId, ta.id, outcome.tablePayload, background, null)
                        }''')
one(chat,
    '''            if (outcome.webSendUrls.isNotEmpty()) {
                outcome = persistWebImages(conversationId, companionId, outcome.webSendUrls, wake, inCall)
            }''',
    '''            if (outcome.webSendUrls.isNotEmpty()) {
                outcome = persistWebImages(conversationId, companionId, outcome.webSendUrls, wake, inCall)
            }
            if (outcome.tablePayload != null) {
                outcome = persistTable(conversationId, companionId, outcome.tablePayload, wake, inCall)
            }''')
one(chat,
    '    private suspend fun persistWebImages(\n',
    '''    private suspend fun persistTable(
        conversationId: Long, companionId: Long, payload: String,
        wake: Boolean, inCall: Long?,
    ): ToolOutcome {
        if (ChatTables.decode(payload) == null)
            return ToolOutcome("表格结构未通过验证，未发出，必须修正行列数。", "发送表格失败")
        return try {
            withContext(NonCancellable) {
                val now = System.currentTimeMillis()
                db.messages().insert(MessageEntity(
                    conversationId = conversationId,
                    role = "assistant",
                    content = payload,
                    createdAt = now,
                    proactive = wake,
                    call = inCall,
                    senderCompanionId = companionId,
                ))
                db.conversations().touch(conversationId, now)
            }
            ToolOutcome("已经真正发送了可展开、横向滚动和复制的表格卡片。不要再重复Markdown版本。", "已发送表格")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolOutcome("表格没有保存成功：" + (e.message ?: "数据库写入失败") +
                "。不要声称已发出去。", "发送表格失败")
        }
    }

    private suspend fun persistWebImages(
''')

screen=ui/"ChatScreen.kt"
one(screen,
    'import com.cleo.cleos.data.MessageImages\n',
    'import com.cleo.cleos.data.MessageImages\nimport com.cleo.cleos.data.ChatTables\nimport com.cleo.cleos.data.ChatTablePart\n')
one(screen,
    '    val locationPoint = remember(message.content) { ChatLocationPayload.decode(message.content) }\n',
    '''    val locationPoint = remember(message.content) { ChatLocationPayload.decode(message.content) }
    // Old Markdown table messages immediately become proper grids after upgrading.
    val tableParts = remember(message.content) { ChatTables.parts(message.content) }
    val hasTables = tableParts.any { it is ChatTablePart.Grid }
''')
one(screen,
    '''                    } else if (BatchMessageActions.isMerged(message.content)) {''',
    '''                    } else if (hasTables) {
                        tableParts.forEach { part ->
                            when (part) {
                                is ChatTablePart.Grid -> ChatTableCard(part.table, onLongClick = { menu = true })
                                is ChatTablePart.Words -> if (part.text.isNotBlank()) {
                                    BubbleSurface(
                                        mine = mine, companionId = aiLabelId ?: LocalBubbleCompanion.current,
                                        modifier = Modifier.widthIn(max = bubbleMaxWidth())
                                            .combinedClickable(onClick = {}, onLongClick = { menu = true }),
                                    ) {
                                        Text(part.text,
                                            color = bubbleTextColor(mine, aiLabelId ?: LocalBubbleCompanion.current),
                                            style = LocalChatType.current.body)
                                    }
                                }
                            }
                        }
                    } else if (BatchMessageActions.isMerged(message.content)) {''')

test=root/"app/src/test/java/com/cleo/cleos/data/ChatTablesTest.kt"
test.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"ChatTablesTest_03844.kt",test)
gradle=root/"app/build.gradle.kts"
one(gradle,'versionName = "0.38.43"','versionName = "0.38.44"')
one(gradle,"versionCode = 62109","versionCode = 62110")
print("v0.38.44: actual send_table messages + legacy Markdown parsed into scrollable/copyable cards")
