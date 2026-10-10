#!/usr/bin/env python3
"""v0.38.10 remove app-only group daily model-call restriction, not actual API quotas."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
base=Path("app/src/main/java/com/cleo/cleos")
def once(rel,old,new,label):
    p=root/rel
    s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1: raise RuntimeError(f"{label}: {n} anchors in {rel}")
    p.write_text(s.replace(old,new,1),encoding="utf-8")

dao=base/"data/db/Daos.kt"
chat=base/"ai/ChatRepository.kt"
options=base/"ui/chat/GroupOptionsDialog.kt"

once(dao,
'''    /** Atomic budget claim; a failed or SKIP model request still counts toward the cap. */
    @Query("UPDATE conversations SET groupUsedDay=:day, groupCallsToday=CASE WHEN groupUsedDay=:day THEN groupCallsToday + 1 ELSE 1 END, groupTotalCalls=groupTotalCalls+1 WHERE id=:id AND isGroup=1 AND (groupUsedDay != :day OR groupCallsToday < groupDailyLimit)")
    suspend fun claimGroupCall(id: Long, day: Long): Int''',
'''    /** Atomic request telemetry, not a quota. Failed/SKIP calls still count. */
    @Query("UPDATE conversations SET groupUsedDay=:day, groupCallsToday=CASE WHEN groupUsedDay=:day THEN MIN(groupCallsToday + 1, 2147483647) ELSE 1 END, groupTotalCalls=CASE WHEN groupTotalCalls < 9223372036854775807 THEN groupTotalCalls + 1 ELSE groupTotalCalls END WHERE id=:id AND isGroup=1")
    suspend fun claimGroupCall(id: Long, day: Long): Int''',
"remove blocking database quota")

once(chat,
'''            if (latestConversation.groupUsedDay == java.time.LocalDate.now().toEpochDay() &&
                latestConversation.groupCallsToday >= latestConversation.groupDailyLimit) {
                note(conversationId, "群聊今天已达到模型调用上限（${latestConversation.groupDailyLimit} 次），可以在群设置调整限额或明天再试。")
                break
            }
''',
'',
"remove UI stop note")
once(chat,
'''        val remaining = if (room.groupUsedDay == LocalDate.now().toEpochDay())
            room.groupDailyLimit - room.groupCallsToday else room.groupDailyLimit''',
'''        val remaining = Int.MAX_VALUE // Unlimited per day; background activity remains bounded.''',
"decouple background reply from obsolete daily budget")
once(chat,
'''                val remaining = if (fresh.groupUsedDay == LocalDate.now().toEpochDay())
                    fresh.groupDailyLimit - fresh.groupCallsToday else fresh.groupDailyLimit''',
'''                val remaining = Int.MAX_VALUE // Bounded auto-follow-up rounds remain unchanged.''',
"decouple explicit continuation from daily budget")

once(options,
'''    var dailyLimit by remember(state.conversationId) { mutableStateOf(state.groupDailyLimit.toString()) }
''',
'',
"remove daily cap form state")
once(options,
'''                OutlinedTextField(
                    value = dailyLimit,
                    onValueChange = { dailyLimit = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    label = { Text("本群每天最多模型请求（1–120）") },
                )
                Text("今日实际请求尝试：${state.groupCallsToday} 次；累计尝试：${state.groupTotalCalls} 次（含 SKIP 和重试）。", fontSize = 12.sp)''',
'''                Text("群聊不再设置应用内每日模型调用上限；实际使用仍受 API 服务商余额与速率限制。", fontSize = 12.sp)
                Text("今日请求尝试：${state.groupCallsToday} 次；累计尝试：${state.groupTotalCalls} 次（含 SKIP 和重试）。", fontSize = 12.sp)''',
"replace daily cap edit with status")
once(options,
'''                        (dailyLimit.toIntOrNull() ?: 40).coerceIn(1, 120),''',
'''                        state.groupDailyLimit, // legacy storage field, no longer gates usage''',
"preserve old Room schema, don't re-enable quota")

once("app/build.gradle.kts",'versionName = "0.38.9"','versionName = "0.38.10"',"version")
once("app/build.gradle.kts",'versionCode = 62075','versionCode = 62076',"code")
print("v0.38.10/62076: no group daily cap; actual API errors and per-turn bounds remain")
