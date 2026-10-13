#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])
src=root/"app/src/main/java/com/cleo/cleos"
def edit(name,pairs):
    p=src/name
    s=p.read_text()
    for a,b in pairs:
        if s.count(a)!=1: raise RuntimeError(name+" anchor not unique: "+a[:90]+" "+str(s.count(a)))
        s=s.replace(a,b,1)
    p.write_text(s)
edit("data/LifeModels.kt",[
 ('LifeProfession("software", "软件工程师", 15000, "work_software", "软件工程实践")',
  'LifeProfession("software", "软件工程师", 15000, "work_software", "软件工程实践"),\n        LifeProfession("custom", "自定义职业", 6500, "work_custom", "职业实践")'),
 ('    fun effectiveMonthlyRmb(profile: LifeProfile): Int = LifeRules.effectiveMonthlyRmb(profile)',
  '    fun title(profile: LifeProfile): String = profile.customProfessionTitle.takeIf { it.isNotBlank() } ?: get(profile.professionId).title\n    fun effectiveMonthlyRmb(profile: LifeProfile): Int = LifeRules.effectiveMonthlyRmb(profile)'),
 ('    val professionId: String = "engineer",\n    val customMonthlyRmb: Int? = null,',
  '    val professionId: String = "engineer",\n    val customProfessionTitle: String = "",\n    val customMonthlyRmb: Int? = null,')
])
edit("data/LifeStore.kt",[
 ('            LifeProfessions.get(p.professionId)\n            p.customMonthlyRmb?.let',
  '            LifeProfessions.get(p.professionId)\n            require(p.customProfessionTitle.length <= 60 && p.customProfessionTitle.none { it.code < 32 })\n            if (p.professionId=="custom") require(p.customProfessionTitle.isNotBlank() && p.customMonthlyRmb!=null)\n            p.customMonthlyRmb?.let'),
 ('                           customMonthlyRmb: Int? = null): Unit = mutex.withLock {',
  '                           customMonthlyRmb: Int? = null, customProfessionTitle: String = ""): Unit = mutex.withLock {'),
 ('        LifeProfessions.get(professionId)\n        LifeRules.validateSchedule(schedule)',
  '        LifeProfessions.get(professionId)\n        val enteredTitle = customProfessionTitle.trim().ifBlank { if(professionId=="custom") "" else LifeProfessions.get(professionId).title }\n        require(enteredTitle.length in 1..60 && enteredTitle.none { it.code < 32 }) { "职业名称需为1—60字" }\n        if(professionId=="custom") require(customMonthlyRmb!=null) { "自定义职业必须填写月薪" }\n        LifeRules.validateSchedule(schedule)'),
 ('            customMonthlyRmb = customMonthlyRmb, lastAccruedAt = max(now, it.lastAccruedAt))',
  '            customMonthlyRmb = customMonthlyRmb, customProfessionTitle=enteredTitle, lastAccruedAt = max(now, it.lastAccruedAt))'),
 ('LifeProfessions.get(p.professionId).title','LifeProfessions.title(p)'),
 ('                append("当前','                append("你当前的职业身份是用户设置的职位，不代表具备现实职业资格。")\n                append("当前')
])
edit("ui/LifeScreen.kt",[
 ('    onSave = { professionId, schedule, strict, custom -> perform {',
  '    onSave = { professionId, title, schedule, strict, custom -> perform {'),
 ('c.life.configure(id, professionId, schedule, strict, customMonthlyRmb = custom)',
  'c.life.configure(id, professionId, schedule, strict, customMonthlyRmb = custom, customProfessionTitle = title)'),
 ('    onSave: (String, LifeSchedule, Boolean, Int?) -> Unit)',
  '    onSave: (String, String, LifeSchedule, Boolean, Int?) -> Unit)'),
 ('    var profession by remember(profile.companionId) { mutableStateOf(profile.professionId) }',
  '    var profession by remember(profile.companionId) { mutableStateOf(profile.professionId) }\n    var jobTitle by remember(profile.companionId) { mutableStateOf(LifeProfessions.title(profile)) }'),
 ('onClick = { profession = job.id; professionMenu = false }',
  'onClick = { profession=job.id; jobTitle=if(job.id=="custom") "" else job.title; if(job.id=="custom")customSalary=""; professionMenu=false }'),
 ('                Text("上海 · 模拟月薪参考',
  '                OutlinedTextField(value=jobTitle,onValueChange={jobTitle=it.take(60)}, label={Text("职业名称（自由编辑）")},singleLine=true, enabled=enabled, modifier=Modifier.fillMaxWidth())\n                Text("可以输入任意职业名，预设只是经验类型分类。",color=p.contentSecondary,fontSize=12.sp)\n                Text("上海 · 模拟月薪参考'),
 ('                     require(days.isNotEmpty())',
  '                     require(jobTitle.trim().isNotBlank() && jobTitle.length<=60) { "请填写职业名称" }\n                     require(days.isNotEmpty())'),
 ('                     onSave(profession, schedule, strict, salary)',
  '                     if(profession=="custom")require(salary!=null) { "自定义职业请输入月薪" }\n                     onSave(profession, jobTitle.trim(), schedule, strict, salary)'),
 ('profession?.title ?: "职业"','LifeProfessions.title(profile)'),
])
print("career UI and identity patched")
