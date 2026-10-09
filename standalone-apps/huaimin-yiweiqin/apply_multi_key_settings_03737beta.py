#!/usr/bin/env python3
"""Add encrypted local backup-key management to existing role model editor."""
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
def change(path,before,after,why):
    p=root/path
    text=p.read_text(encoding="utf-8")
    hits=text.count(before)
    if hits!=1: raise RuntimeError(f"{why}: expected 1 anchor got {hits}")
    p.write_text(text.replace(before,after,1),encoding="utf-8")

fields="app/src/main/java/com/cleo/cleos/ui/settings/EndpointFields.kt"
ui="app/src/main/java/com/cleo/cleos/ui/settings/TaPages.kt"

change(fields,
'''    var checkResult by mutableStateOf<String?>(null)
        private set
''',
'''    var checkResult by mutableStateOf<String?>(null)
        private set
    var backupAlias by mutableStateOf("")
    var backupInput by mutableStateOf("")
    var backupModelScope by mutableStateOf("")
    var backupRows by mutableStateOf<List<com.cleo.cleos.ai.KeyDisplay>>(emptyList())
    var autoKeySwitch by mutableStateOf(true)
    var poolError by mutableStateOf<String?>(null)

    fun refreshPool() {
        val url=baseUrl
        scope.launch {
            backupRows=c.apiKeyPool.display(url)
            autoKeySwitch=c.apiKeyPool.enabled()
        }
    }
    fun setAutoSwitch(on:Boolean) {
        scope.launch {
            c.apiKeyPool.setEnabled(on)
            autoKeySwitch=on
        }
    }
    fun addBackup() {
        val url=baseUrl
        val input=backupInput.trim()
        if(input.isEmpty()) {poolError="请先填写备用密钥";return}
        scope.launch {
            poolError=runCatching {
                c.apiKeyPool.addBackup(url,backupAlias,input,backupModelScope)
                backupInput=""
                backupAlias=""
                backupModelScope=""
                null
            }.getOrElse { it.message ?: "保存失败" }
            refreshPool()
        }
    }
    fun setBackupEnabled(id:String,on:Boolean) {
        val url=baseUrl
        scope.launch { c.apiKeyPool.enable(url,id,on);refreshPool() }
    }
    fun restoreBackup(id:String) {
        val url=baseUrl
        scope.launch { c.apiKeyPool.reset(url,id);refreshPool() }
    }
    fun removeBackup(id:String) {
        val url=baseUrl
        scope.launch { c.apiKeyPool.remove(url,id);refreshPool() }
    }
''',"settings key pool view-model")

change(ui,
'''    var pickingModel by remember { mutableStateOf(false) }

    Field("接口地址"''',
'''    var pickingModel by remember { mutableStateOf(false) }
    var editingKeys by remember { mutableStateOf(false) }

    Field("接口地址"''',"pool editor dialog state")

change(ui,
'''    Field("模型", fields.model, { fields.selectModel(it) }, enabled = !saving)
''',
'''    Chip("备用密钥与自动切换", selected = false, enabled = !saving) {
        fields.refreshPool()
        editingKeys = true
    }
    Field("模型", fields.model, { fields.selectModel(it) }, enabled = !saving)
    if (editingKeys) {
        AlertDialog(
            onDismissRequest = { editingKeys = false },
            title = { Text("API 密钥自动切换") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("所有 Key 只加密保存在本机。同接口内轮换，不自动换模型或服务商。", fontSize=12.sp)
                    Chip(if (fields.autoKeySwitch) "自动切换：已开启" else "自动切换：已关闭",
                        selected=fields.autoKeySwitch) {
                        fields.setAutoSwitch(!fields.autoKeySwitch)
                    }
                    fields.backupRows.forEach { key ->
                        Text("${key.alias} · ${key.health} · ${if (key.enabled) "启用" else "停用"} · 优先级${key.priority}",fontSize=12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(if(key.enabled) "停用" else "启用",selected=false) {
                                fields.setBackupEnabled(key.id,!key.enabled)
                            }
                            Chip("恢复/重新检测",selected=false) { fields.restoreBackup(key.id) }
                            if(key.id!="primary") Chip("删除",selected=false) { fields.removeBackup(key.id) }
                        }
                    }
                    OutlinedTextField(value=fields.backupAlias,
                        onValueChange={fields.backupAlias=it},label={Text("备用密钥别名")})
                    OutlinedTextField(value=fields.backupInput,
                        onValueChange={fields.backupInput=it},label={Text("备用 API Key")},
                        visualTransformation=PasswordVisualTransformation())
                    OutlinedTextField(value=fields.backupModelScope,
                        onValueChange={fields.backupModelScope=it},
                        label={Text("专属模型 ID（留空为此接口所有模型）")})
                    fields.poolError?.let { Text(it,color=palette.error) }
                    Chip("保存备用密钥",selected=true) { fields.addBackup() }
                }
            },
            confirmButton = { TextButton(onClick={ editingKeys=false }) { Text("完成") } },
        )
    }
''',"real settings controls with masked keys")
print("0.37.37beta key editor wired to existing role model page")
