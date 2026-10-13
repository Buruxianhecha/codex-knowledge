#!/usr/bin/env python3
"""0.38.44: verify persistent structured and old Markdown tables both render as cards."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
d=root/"app/src/main/java/com/cleo/cleos/data/ChatTables.kt"
u=root/"app/src/main/java/com/cleo/cleos/ui/chat/ChatTableCard.kt"
screen=root/"app/src/main/java/com/cleo/cleos/ui/chat/ChatScreen.kt"
tools=root/"app/src/main/java/com/cleo/cleos/ai/Tools.kt"
chat=root/"app/src/main/java/com/cleo/cleos/ai/ChatRepository.kt"
prompt=root/"app/src/main/java/com/cleo/cleos/ai/Prompt.kt"
test=root/"app/src/test/java/com/cleo/cleos/data/ChatTablesTest.kt"
assert d.read_bytes()==(here/"ChatTables_03844.kt").read_bytes()
assert u.read_bytes()==(here/"ChatTableCard_03844.kt").read_bytes()
assert test.read_bytes()==(here/"ChatTablesTest_03844.kt").read_bytes()
assert test.read_text().count("@Test fun ")==12
for fragment in ('ChatTable(', 'ChatTablePart', 'fun fromArgs(', 'fun encode(', 'fun decode(', 'fun parts(', 'fun tsv(', 'CLEOS_TABLE_V1:', 'inFence', 'separator('):
    assert fragment in d.read_text(), f"Table codec missing {fragment}"
for fragment in ('horizontalScroll(rememberScrollState())','AlertDialog(','"复制"','"展开"','"复制表格"','"左右滑动查看完整列"','TableGridRow(', 'ChatTables.tsv(table)'):
    assert fragment in u.read_text(), f"Table visual missing {fragment}"
for fragment in ('ChatTables.parts(message.content)','val hasTables =','ChatTableCard(part.table','is ChatTablePart.Words'):
    assert fragment in screen.read_text(), f"Existing message rendering missing {fragment}"
for fragment in ('name = "send_table"', 'val tablePayload: String? = null', 'ToolSpecs.sendTable.name -> sendTable(args)',
                 'ChatTables.fromArgs(a)', 'tablePayload = ChatTables.encode(table)'):
    assert fragment in tools.read_text(), f"Structured tool missing {fragment}"
for fragment in ('private suspend fun persistTable(', 'ChatTables.decode(payload)','content = payload',
                 'senderCompanionId = companionId', 'outcome = persistTable(conversationId, ta.id',
                 'outcome = persistTable(conversationId, companionId'):
    assert fragment in chat.read_text(), f"Table persistence/group/private missing {fragment}"
assert "使用 send_table" in prompt.read_text()
assert 'versionName = "0.38.44"' in (root/"app/build.gradle.kts").read_text()
assert "versionCode = 62110" in (root/"app/build.gradle.kts").read_text()
for fragment in ('set_my_name','send_web_image','search_web_avatar_images','play_qq_music','play_kugou_music','send_gallery_photos'):
    assert fragment in tools.read_text(), f"previous tool disappeared: {fragment}"
assert "PersonalityGrowthRules.forPrompt(" in chat.read_text(), "personality integration lost"
assert "ChatLocationCard(" in screen.read_text(), "location message lost"
print("PASS: real structured table messages and safe rendering of old Markdown, group/private and past features")
