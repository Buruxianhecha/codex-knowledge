#!/usr/bin/env python3
"""0.38.46: repair false Goal Mode file-format blockers and add explicit fallback controls."""
from pathlib import Path
from shutil import copyfile
import sys
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def one(p, old, new):
    s=p.read_text(encoding="utf-8")
    found=s.count(old)
    if found!=1:
        raise RuntimeError(f"goal delivery prerequisite mismatch: {p.name}, {found} copies of {old[:95]!r}")
    p.write_text(s.replace(old,new,1),encoding="utf-8")

ai=root/"app/src/main/java/com/cleo/cleos/ai"
ui=root/"app/src/main/java/com/cleo/cleos/ui/chat"

copyfile(here/"GoalDelivery_03846.kt", ai/"GoalDelivery.kt")
copyfile(here/"GoalModeUi_03846.kt", ui/"GoalModeUi.kt")

chat=ai/"ChatRepository.kt"
one(chat,
    '''                "reconcile" -> store.reconcile(g.id)
                "budget" -> store.adjustBudget(g.id,budget)''',
    '''                "reconcile" -> store.reconcile(g.id)
                "delivery_chat","delivery_markdown" -> {
                    // Explicit user action only. Original file obligations are replaced with
                    // new acceptance, the version increases, and stale callbacks are rejected.
                    val selected=if(action=="delivery_chat") "CHAT_TEXT" else "MARKDOWN"
                    val changed=store.chooseDelivery(g.id,selected)
                    if(changed!=null && changed.version!=g.version) {
                        jobs[conversationId]?.cancel()
                        if(store.resume(g.id)?.status=="RUNNING") scheduleGoal(conversationId)
                    }
                }
                "budget" -> store.adjustBudget(g.id,budget)''')

test=root/"app/src/test/java/com/cleo/cleos/ai/GoalDeliveryRulesTest.kt"
test.parent.mkdir(parents=True,exist_ok=True)
copyfile(here/"GoalDeliveryRulesTest_03846.kt",test)

gradle=root/"app/build.gradle.kts"
one(gradle,'versionName = "0.38.45"','versionName = "0.38.46"')
one(gradle,'versionCode = 62111','versionCode = 62112')
print("0.38.46: per-goal delivery routing + explicit fallback UI, preserves mandatory acceptance by default")
