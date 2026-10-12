#!/usr/bin/env python3
"""0.38.42: per-TA editable AI growth thresholds and a real manual refresh button."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

def one(path, old, new):
    s=path.read_text(encoding="utf-8")
    n=s.count(old)
    if n != 1:
        raise RuntimeError(f"03842 unexpected baseline {path.name}: {old[:80]!r}, got {n}")
    path.write_text(s.replace(old,new,1), encoding="utf-8")

ai = root/"app/src/main/java/com/cleo/cleos/ai"
copyfile(here/"PersonalityGrowth_03842.kt", ai/"PersonalityGrowth.kt")
copyfile(here/"PersonalityGrowthConfig_03842.kt", ai/"PersonalityGrowthConfig.kt")

prompt=ai/"Prompt.kt"
one(prompt,
    "memories.filterNot { it.name == PersonalityGrowthRules.NAME }",
    "memories.filterNot { it.name == PersonalityGrowthRules.NAME || PersonalityGrowthConfigStore.isConfig(it) }")

app=root/"app/src/main/java/com/cleo/cleos/CleosApp.kt"
one(app, "import com.cleo.cleos.ai.PersonalityGrowth\n",
    "import com.cleo.cleos.ai.PersonalityGrowth\nimport com.cleo.cleos.ai.PersonalityGrowthConfigStore\n")
one(app, "    val recaps = Recaps(db, settings, secrets, chatClient, appScope)\n",
    """    val personalityGrowthConfig = PersonalityGrowthConfigStore(db)
    val personalityGrowth = PersonalityGrowth(db, companions, secrets, chatClient, personalityGrowthConfig)
    val recaps = Recaps(db, settings, secrets, chatClient, appScope)
""")
one(app, "personalityGrowth = PersonalityGrowth(db, companions, secrets, chatClient),",
    "personalityGrowth = personalityGrowth,")

vm=root/"app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt"
one(vm, "import com.cleo.cleos.ai.PersonalityGrowthRules\n",
    "import com.cleo.cleos.ai.PersonalityGrowthRules\nimport com.cleo.cleos.ai.PersonalityGrowthConfig\n")
one(vm, """    var persona by mutableStateOf("")
        private set""",
    """    fun growthOptions(id: Long): Flow<PersonalityGrowthConfig> =
        c.personalityGrowthConfig.observe(id)

    var growthBusy by mutableStateOf(false)
        private set
    var growthFeedback by mutableStateOf<String?>(null)
        private set

    fun changeGrowth(id: Long, change: (PersonalityGrowthConfig) -> PersonalityGrowthConfig) {
        if (id <= 0L) return
        viewModelScope.launch {
            try {
                c.personalityGrowthConfig.update(id, change)
                growthFeedback = null
            } catch (_: Exception) {
                growthFeedback = "设置保存失败，请重试。"
            }
        }
    }

    fun growNow(id: Long) {
        if (growthBusy || id <= 0L) return
        growthBusy = true
        growthFeedback = null
        viewModelScope.launch {
            try {
                growthFeedback = c.personalityGrowth.growNow(id)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                growthFeedback = "这次总结失败，请检查模型、网络或聊天记录后重试。"
            } finally {
                growthBusy = false
            }
        }
    }

    var persona by mutableStateOf("")
        private set""")

page=root/"app/src/main/java/com/cleo/cleos/ui/settings/TaPages.kt"
one(page, "import androidx.compose.material3.ButtonDefaults\n",
    "import androidx.compose.material3.ButtonDefaults\nimport androidx.compose.material3.Slider\nimport androidx.compose.material3.Switch\n")
one(page, "import androidx.compose.runtime.mutableStateOf\n",
    "import androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.mutableFloatStateOf\n")
one(page, "import androidx.compose.ui.unit.sp\n",
    "import androidx.compose.ui.unit.sp\nimport kotlin.math.roundToInt\n")
one(page,
    """    val growing by remember(vm.companionId) { vm.growingPersona(vm.companionId) }
        .collectAsStateWithLifecycle(initialValue = "")""",
    """    val growing by remember(vm.companionId) { vm.growingPersona(vm.companionId) }
        .collectAsStateWithLifecycle(initialValue = "")
    val growthOptions by remember(vm.companionId) { vm.growthOptions(vm.companionId) }
        .collectAsStateWithLifecycle(initialValue = com.cleo.cleos.ai.PersonalityGrowthConfig())
    var messageTarget by remember(vm.companionId, growthOptions.minMessages) {
        mutableFloatStateOf(growthOptions.minMessages.toFloat())
    }
    var intervalTarget by remember(vm.companionId, growthOptions.minHours) {
        mutableFloatStateOf(growthOptions.minHours.toFloat())
    }""")
one(page,
    '''            fontSize = 13.sp, lineHeight = 20.sp)
    }

    if (vm.companionCount > 1) {''',
    '''            fontSize = 13.sp, lineHeight = 20.sp)

        Spacer(Modifier.width(1.dp))
        Text("AI 自主性格成长设置", color = palette.content,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("自动形成和更新性格", color = palette.content, modifier = Modifier.weight(1f), fontSize = 14.sp)
            Switch(checked = growthOptions.enabled,
                onCheckedChange = { enabled ->
                    vm.changeGrowth(vm.companionId) { it.copy(enabled = enabled) }
                })
        }

        Text("新消息门槛：${messageTarget.roundToInt()} 条",
            color = palette.content, fontSize = 13.sp)
        Slider(value = messageTarget, onValueChange = { messageTarget = it },
            valueRange = 1f..100f, steps = 98,
            enabled = growthOptions.enabled,
            onValueChangeFinished = {
                val selected = messageTarget.roundToInt()
                vm.changeGrowth(vm.companionId) { it.copy(minMessages = selected) }
            })
        Text("达到门槛后才考虑下一次自动总结。", color = palette.contentSecondary, fontSize = 12.sp)

        Text("更新间隔：${if (intervalTarget.roundToInt() == 0) "不限制" else "${intervalTarget.roundToInt()} 小时"}",
            color = palette.content, fontSize = 13.sp)
        Slider(value = intervalTarget, onValueChange = { intervalTarget = it },
            valueRange = 0f..48f, steps = 47,
            enabled = growthOptions.enabled,
            onValueChangeFinished = {
                val selected = intervalTarget.roundToInt()
                vm.changeGrowth(vm.companionId) { it.copy(minHours = selected) }
            })
        Text("设为 0 小时仍须满足消息门槛，避免无意义的重复总结。",
            color = palette.contentSecondary, fontSize = 12.sp)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("允许手动立即总结", color = palette.content,
                modifier = Modifier.weight(1f), fontSize = 14.sp)
            Switch(checked = growthOptions.manualEnabled,
                onCheckedChange = { enabled ->
                    vm.changeGrowth(vm.companionId) { it.copy(manualEnabled = enabled) }
                })
        }
        Button(
            enabled = growthOptions.manualEnabled && !vm.growthBusy && vm.companionId > 0L,
            onClick = { vm.growNow(vm.companionId) },
        ) { Text(if (vm.growthBusy) "正在整理性格…" else "立即总结一次") }
        vm.growthFeedback?.let { Text(it, color = palette.contentSecondary, fontSize = 12.sp) }
        Text("手动总结不受消息数量和时间间隔限制；每次成功总结都可能产生模型调用费用。上述选项按 AI 角色分别保存。",
            color = palette.contentSecondary, fontSize = 12.sp)
    }

    if (vm.companionCount > 1) {''')

unit=root/"app/src/test/java/com/cleo/cleos/ai/PersonalityGrowthConfigTest.kt"
unit.parent.mkdir(parents=True, exist_ok=True)
copyfile(here/"PersonalityGrowthConfigTest_03842.kt", unit)
gradle=root/"app/build.gradle.kts"
one(gradle, 'versionName = "0.38.41"', 'versionName = "0.38.42"')
one(gradle, "versionCode = 62107", "versionCode = 62108")
print("PASS 0.38.42/62108: character-specific auto growth controls, threshold sliders and manual refresh button")
