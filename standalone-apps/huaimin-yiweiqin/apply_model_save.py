"""Apply the model selection commit and its settings-page controls to pinned Cleos."""
from pathlib import Path
import shutil


def apply_model_save(root: Path):
    source = Path(__file__).resolve().parent
    settings = "app/src/main/java/com/cleo/cleos/ui/settings/"

    def replace(rel: str, old: str, new: str):
        path = root / rel
        text = path.read_text(encoding="utf-8")
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{rel}: expected exactly one match, found {count}: {old[:80]!r}")
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    shutil.copy2(source / "src/ModelSettingsWriter.kt", root / settings / "ModelSettingsWriter.kt")
    tests = root / "app/src/test/java/com/cleo/cleos/ui/settings"
    tests.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source / "tests/ModelSettingsWriterTest.kt", tests / "ModelSettingsWriterTest.kt")

    vm = settings + "SettingsViewModel.kt"
    replace(vm, "    private var deleted = false\n", """    private var deleted = false
    private val modelWriter = ModelSettingsWriter(
        saveKey = { address, key -> c.secrets.setKey(address, key) },
        update = { id, change -> c.companions.update(id, change) },
        read = { id -> c.companions.get(id) },
    )
    var modelSaving by mutableStateOf(false)
        private set
    var modelSaveError by mutableStateOf<String?>(null)
        private set
""")
    replace(vm, """    private suspend fun persist() {
        if (!loaded || deleted) return
""", """    private suspend fun persist() = modelWriter.serially {
        if (!loaded || deleted) return@serially
""")
    replace(vm, """    /**
     * The settings of another TA, who also becomes the one being talked to (as picking them on the
""", """    /** Commit the exact model selection, then leave only after the stored record agrees. */
    fun saveModel(onSaved: () -> Unit) {
        if (!loaded || deleted || modelSaving) return
        modelSaveError = null
        val selection = ModelSelection(
            companionId, chat.baseUrl, chat.model,
            spokenOn, spoken.baseUrl, spoken.model,
        ).normalized()
        try {
            selection.validate()
        } catch (e: IllegalArgumentException) {
            modelSaveError = e.message
            return
        }
        val chatKey = chat.pendingKey()
        val spokenKey = spoken.pendingKey()
        val pending = listOfNotNull(chatKey, spokenKey)
        modelSaving = true
        viewModelScope.launch {
            try {
                modelWriter.commit(selection, pending)
                if (chat.pendingKey() == chatKey) chat.keyInput = ""
                if (spoken.pendingKey() == spokenKey) spoken.keyInput = ""
                modelSaving = false
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                modelSaveError = "保存失败：" + (e.message ?: "请重试")
            } finally {
                modelSaving = false
            }
        }
    }

    /**
     * The settings of another TA, who also becomes the one being talked to (as picking them on the
""")

    screen = settings + "SettingsScreen.kt"
    replace(screen, "    BackHandler(enabled = page != null) { page = null }",
            "    BackHandler(enabled = page != null) { if (!vm.modelSaving) page = null }")
    replace(screen, '{ if (page != null) page = null else onBack() }',
            '{ if (!vm.modelSaving) { if (page != null) page = null else onBack() } }')
    replace(screen, "SettingsPage.Model -> ModelPage(vm)",
            "SettingsPage.Model -> ModelPage(vm, onSaved = { page = null })")
    replace(screen, """    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
""", """    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
""")
    replace(screen, "internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {",
            "internal fun Chip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {")
    replace(screen, ".clickable(interactionSource = null, indication = null, onClick = onClick)",
            ".clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onClick)")

    pages = settings + "TaPages.kt"
    replace(pages, "import androidx.compose.foundation.layout.FlowRow\n", """import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
""")
    replace(pages, "import androidx.compose.material3.AlertDialog\n", """import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
""")
    replace(pages, "import androidx.compose.ui.Modifier\n", """import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
""")
    replace(pages, """internal fun ModelPage(vm: SettingsViewModel) {
    val palette = LocalGlassPalette.current

    Section("用谁家的") { ServiceChips(vm.chat) }
""", """internal fun ModelPage(vm: SettingsViewModel, onSaved: () -> Unit) {
    val palette = LocalGlassPalette.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val save = {
        focus.clearFocus()
        keyboard?.hide()
        vm.saveModel(onSaved)
    }

    Section("用谁家的") { ServiceChips(vm.chat, enabled = !vm.modelSaving) }
""")
    replace(pages, "        ConnectionFields(vm.chat)",
            "        ConnectionFields(vm.chat, vm.modelSaving, vm.modelSaveError, save)")
    replace(pages, ") { vm.spokenOn = it }", ") { if (!vm.modelSaving) vm.spokenOn = it }")
    replace(pages, "            ServiceChips(vm.spoken)",
            "            ServiceChips(vm.spoken, enabled = !vm.modelSaving)")
    replace(pages, "            ConnectionFields(vm.spoken)",
            "            ConnectionFields(vm.spoken, vm.modelSaving, vm.modelSaveError, save)")
    replace(pages, "private fun ServiceChips(fields: EndpointFields) {",
            "private fun ServiceChips(fields: EndpointFields, enabled: Boolean = true) {")
    replace(pages, "Chip(p.name, selected = fields.baseUrl.trimEnd('/') == p.baseUrl)",
            "Chip(p.name, selected = fields.baseUrl.trimEnd('/') == p.baseUrl, enabled = enabled)")
    replace(pages, "private fun ConnectionFields(fields: EndpointFields) {", """private fun ConnectionFields(
    fields: EndpointFields,
    saving: Boolean,
    saveError: String?,
    onSave: () -> Unit,
) {""")
    replace(pages, 'Field("接口地址", fields.baseUrl, { fields.baseUrl = it }, keyboardType = KeyboardType.Uri)',
            'Field("接口地址", fields.baseUrl, { fields.baseUrl = it }, keyboardType = KeyboardType.Uri, enabled = !saving)')
    replace(pages, "        onValueChange = { fields.keyInput = it },",
            "        onValueChange = { fields.keyInput = it },\n        enabled = !saving,")
    replace(pages, 'Chip("保存 Key", selected = true)', 'Chip("保存 Key", selected = true, enabled = !saving)')
    replace(pages, 'Chip("清除", selected = false)', 'Chip("清除", selected = false, enabled = !saving)')
    replace(pages, """    Field("模型", fields.model, { fields.model = it })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip(if (fields.checking) "正在连接…" else "测试并列出模型", selected = false) { if (!fields.checking) fields.check() }
        if (!fields.models.isNullOrEmpty()) Chip("从列表里选", selected = false) { pickingModel = true }
    }
""", """    Field("模型", fields.model, { fields.selectModel(it) }, enabled = !saving)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(if (fields.checking) "正在连接…" else "测试并列出模型", selected = false, enabled = !saving) {
            if (!fields.checking) fields.check()
        }
        if (!fields.models.isNullOrEmpty()) Chip("从列表里选", selected = false, enabled = !saving) { pickingModel = true }
        Button(
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier.widthIn(min = 72.dp).heightIn(min = 48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(if (saving) "保存中…" else "保存", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    saveError?.let { Text(it, color = palette.error, fontSize = 13.sp, lineHeight = 19.sp) }
""")
    replace(pages, """                                    fields.model = id
                                    pickingModel = false
""", """                                    fields.selectModel(id)
                                    pickingModel = false
""")
    endpoint = settings + "EndpointFields.kt"
    replace(endpoint, "    fun saveKey() {", """    fun selectModel(value: String) {
        model = value
        checkResult = null
    }

    fun saveKey() {""")
