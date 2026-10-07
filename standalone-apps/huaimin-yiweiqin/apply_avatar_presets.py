"""Add the user's 14 avatar presets after the existing upstream backports."""
from pathlib import Path
import hashlib
import json
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve()
HERE = Path(__file__).resolve().parent

def replace(path, old, new):
    file = ROOT / path
    text = file.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"Expected exactly one avatar patch target in {path}")
    file.write_text(text.replace(old, new, 1))

def imports(path, names):
    file = ROOT / path
    text = file.read_text()
    package, rest = text.split('\n', 1)
    additions = ''.join(f'\nimport {name}' for name in names if f'import {name}\n' not in text)
    file.write_text(package + additions + '\n' + rest)

pages = 'app/src/main/java/com/cleo/cleos/ui/settings/TaPages.kt'
imports(pages, ['androidx.compose.ui.platform.LocalContext'])
replace(pages,
    '    var confirmDelete by remember { mutableStateOf(false) }\n',
    '    var confirmDelete by remember { mutableStateOf(false) }\n'
    '    var choosingAvatar by remember { mutableStateOf(false) }\n'
    '    val avatarAssets = LocalContext.current.applicationContext.assets\n')
replace(pages,
    '                val has = ta?.avatar != null || ta?.avatarEmoji != null\n',
    '                val has = ta?.avatar != null || ta?.avatarEmoji != null\n'
    '                Chip("头像预设", selected = false) { choosingAvatar = true }\n')
replace(pages,
    '        Field("名字", vm.aiName, { vm.aiName = it })\n',
    '        vm.avatarError?.let { Text(it, color = palette.error, fontSize = 12.sp) }\n'
    '        Field("名字", vm.aiName, { vm.aiName = it })\n')
replace(pages,
    '    cropping?.let { uri ->\n',
    '''    if (choosingAvatar) {
        AvatarPresetPicker(
            currentFile = ta?.avatar,
            onDismiss = { choosingAvatar = false },
            onPick = { preset ->
                choosingAvatar = false
                if (!preset.isSelected(ta?.avatar)) vm.setAvatarPreset(preset, avatarAssets)
            },
        )
    }

    cropping?.let { uri ->
''')

vm = 'app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt'
imports(vm, [
    'android.content.res.AssetManager',
    'com.cleo.cleos.data.AvatarPreset',
    'com.cleo.cleos.data.AvatarPresets',
    'com.cleo.cleos.data.AvatarSaveQueue',
])
old = '''    /** A picture cropped on the profile page; it replaces an emoji the TA picked for itself. */
    fun setAvatar(picture: Bitmap) {
        val id = companionId
        c.appScope.launch { c.companions.setAvatar(id, c.images.save(picture, prefix = "avatar-"), emoji = null) }
    }

    /** Back to the first letter of the name. */
    fun clearAvatar() {
        val id = companionId
        c.appScope.launch { c.companions.setAvatar(id, null, emoji = null) }
    }
'''
new = '''    private val avatarUpdates = AvatarSaveQueue(c.appScope)
    var avatarError by mutableStateOf<String?>(null)
        private set

    /** A picture cropped on the profile page; it replaces an emoji the TA picked for itself. */
    fun setAvatar(picture: Bitmap) = updateAvatar { c.images.save(picture, prefix = "avatar-") }

    /** Copies the original preset into the same private image store that backups already include. */
    fun setAvatarPreset(preset: AvatarPreset, assets: AssetManager) = updateAvatar {
        withContext(Dispatchers.IO) {
            AvatarPresets.copyToStore(preset, c.images.dir) { assets.open(preset.assetPath) }
        }
    }

    /** Back to the first letter of the name. */
    fun clearAvatar() = updateAvatar { null }

    /** Captures the TA at tap time, outlives this page, and serializes preset/photo/reset writes. */
    private fun updateAvatar(makeFile: suspend () -> String?) {
        val id = companionId
        if (id <= 0L) return
        avatarError = null
        avatarUpdates.submit {
            if (c.companions.get(id) == null) return@submit
            var file: String? = null
            try {
                file = makeFile()
                c.companions.setAvatar(id, file, emoji = null)
                withContext(Dispatchers.Main) { avatarError = null }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                file?.let { c.images.delete(listOf(it)) }
                withContext(Dispatchers.Main) { avatarError = "头像没有保存成功，请再试一次。" }
            }
        }
    }
'''
replace(vm, old, new)

for name, package in [('AvatarPresets.kt', 'data'), ('AvatarSaveQueue.kt', 'data'), ('AvatarPresetPicker.kt', 'ui/settings')]:
    destination = ROOT / f'app/src/main/java/com/cleo/cleos/{package}/{name}'
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / 'src' / name, destination)
test = ROOT / 'app/src/test/java/com/cleo/cleos/data/AvatarPresetsTest.kt'
test.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(HERE / 'tests/AvatarPresetsTest.kt', test)

manifest = json.loads((HERE / 'assets/avatar_presets/manifest.json').read_text())
assert len(manifest) == 14
assert [item['file_name'] for item in manifest] == [f'avatar_{number:02d}.png' for number in range(1, 15)]
destination = ROOT / 'app/src/main/assets/avatar_presets'
destination.mkdir(parents=True, exist_ok=True)
for item in manifest:
    source = HERE / 'assets/avatar_presets' / item['file_name']
    assert hashlib.sha256(source.read_bytes()).hexdigest() == item['sha256'], item['file_name']
    shutil.copyfile(source, destination / item['file_name'])
print('Avatar presets applied: 14 original images, profile picker, per-TA files, ordered saving and existing backup storage.')
