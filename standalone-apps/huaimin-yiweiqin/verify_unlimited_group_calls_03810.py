#!/usr/bin/env python3
"""v0.38.10 integration checks: historical groups at 120 calls may keep sending."""
from pathlib import Path
import re
import sqlite3
import sys

root=Path(sys.argv[1]).resolve()
base=root/"app/src/main/java/com/cleo/cleos"
dao=(base/"data/db/Daos.kt").read_text(encoding="utf-8")
chat=(base/"ai/ChatRepository.kt").read_text(encoding="utf-8")
opts=(base/"ui/chat/GroupOptionsDialog.kt").read_text(encoding="utf-8")
group=(base/"ai/GroupAutonomy.kt").read_text(encoding="utf-8")
ver=(root/"app/build.gradle.kts").read_text(encoding="utf-8")

assert 'versionName = "0.38.11"' in ver
assert 'versionCode = 62077' in ver
assert "groupCallsToday < groupDailyLimit" not in dao
assert "groupCallsToday >= latestConversation.groupDailyLimit" not in chat
assert "群聊今天已达到模型调用上限" not in chat
assert "本群每天最多模型请求" not in opts
assert "dailyLimit.toIntOrNull()" not in opts
assert "state.groupDailyLimit" in opts
assert 'groupCallsToday=CASE WHEN groupUsedDay=:day THEN MIN(' in dao
assert chat.count("claimGroupCall(")>=2, "don't bypass atomic group usage logging"
assert 'val remaining = Int.MAX_VALUE' in chat
assert "UNANSWERED_MAX=2" in group, "bounded background activity must stay"
assert "GroupAutoMode.EXTRA_ROUNDS" in chat, "bounded autonomous follow-up must stay"

q=re.search(r'@Query\("(UPDATE conversations SET groupUsedDay=.*?)"\)\s*suspend fun claimGroupCall',
    dao,re.S)
assert q, "failed to find actual DAO query"
sql=q.group(1)
con=sqlite3.connect(":memory:")
con.execute("""CREATE TABLE conversations(
  id INTEGER PRIMARY KEY,
  isGroup INTEGER NOT NULL,
  groupUsedDay INTEGER NOT NULL,
  groupCallsToday INTEGER NOT NULL,
  groupTotalCalls INTEGER NOT NULL,
  groupDailyLimit INTEGER NOT NULL
)""")
today=30000
con.execute("INSERT INTO conversations VALUES(1,1,?,120,120,120)",(today,))
con.execute("INSERT INTO conversations VALUES(2,0,?,120,120,120)",(today,))
# 120/day group should allow the 121st, 122nd etc. and update statistics.
for n in range(121,251):
    result=con.execute(sql,{"id":1,"day":today})
    assert result.rowcount==1, f"blocked valid group call {n}"
daily,total=con.execute("SELECT groupCallsToday,groupTotalCalls FROM conversations WHERE id=1").fetchone()
assert (daily,total)==(250,250),(daily,total)
# Not a group: no use statistics, result 0; preserve the guard.
assert con.execute(sql,{"id":2,"day":today}).rowcount==0
# Midnight resets today counter, retains all-time counter.
assert con.execute(sql,{"id":1,"day":today+1}).rowcount==1
assert con.execute("SELECT groupCallsToday,groupTotalCalls FROM conversations WHERE id=1").fetchone()==(1,251)
# Saturating edge condition should never prevent service, nor overflow.
con.execute("UPDATE conversations SET groupUsedDay=?, groupCallsToday=2147483647 WHERE id=1",(today+1,))
assert con.execute(sql,{"id":1,"day":today+1}).rowcount==1
assert con.execute("SELECT groupCallsToday FROM conversations WHERE id=1").fetchone()[0]==2147483647
print("v0.38.10: old groups at 120/120 can call freely; statistics & day rollover correct; no quota warning or old form")
