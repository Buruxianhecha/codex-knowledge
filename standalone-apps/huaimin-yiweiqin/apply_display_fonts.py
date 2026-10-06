"""Add private TTF/OTF imports, global text rendering, explicit save and full font backup."""
from pathlib import Path
import shutil
import sys

ROOT = Path(sys.argv[1]).resolve()
HERE = Path(__file__).resolve().parent

def replace(path, old, new):
    file = ROOT / path
    text = file.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"Expected exactly one font patch target in {path}: {old[:80]!r}")
    file.write_text(text.replace(old, new, 1))

def imports(path, names):
    file = ROOT / path
    text = file.read_text()
    package, rest = text.split('\n', 1)
    additions = ''.join(f'\nimport {name}' for name in names if f'import {name}\n' not in text)
    file.write_text(package + additions + '\n' + rest)

repo = 'app/src/main/java/com/cleo/cleos/data/SettingsRepository.kt'
replace(repo, '    val chatTextSize: Int = 15,\n', '''    val chatTextSize: Int = 15,
    /** Imported TTF/OTF files inside the private store; null uses the system default. */
    val displayFonts: List<DisplayFont> = emptyList(),
    val displayFont: String? = null,
''')
replace(repo, '        val chatTextSize = intPreferencesKey("chat_text_size")\n', '''        val chatTextSize = intPreferencesKey("chat_text_size")
        val displayFonts = stringPreferencesKey("display_fonts")
        val displayFont = stringPreferencesKey("display_font")
''')
replace(repo, '            chatTextSize = this[Keys.chatTextSize] ?: d.chatTextSize,\n', '''            chatTextSize = this[Keys.chatTextSize] ?: d.chatTextSize,
            displayFonts = runCatching { DisplayFonts.decode(this[Keys.displayFonts]) }.getOrDefault(emptyList()),
            displayFont = this[Keys.displayFont]?.takeIf(DisplayFonts::isFontName),
''')
replace(repo, '            prefs[Keys.chatTextSize] = next.chatTextSize\n', '''            prefs[Keys.chatTextSize] = next.chatTextSize
            prefs[Keys.displayFonts] = DisplayFonts.encode(next.displayFonts)
            val selectedFont = next.displayFont?.takeIf { name -> next.displayFonts.any { it.file == name } }
            if (selectedFont != null) prefs[Keys.displayFont] = selectedFont else prefs.remove(Keys.displayFont)
''')
# This patch runs after the full-backup declarations were collected; keep these new keys typed too.
replace(repo, '    internal val backupPreferenceTypes: Map<String, String> = mapOf(\n', '''    internal val backupPreferenceTypes: Map<String, String> = mapOf(
        "display_fonts" to "string",
        "display_font" to "string",
''')

vm = 'app/src/main/java/com/cleo/cleos/ui/settings/SettingsViewModel.kt'
imports(vm, [
    'android.content.ContentResolver',
    'android.provider.OpenableColumns',
    'com.cleo.cleos.data.DisplayFont',
    'com.cleo.cleos.data.DisplayFonts',
    'com.cleo.cleos.data.DisplayFontException',
    'com.cleo.cleos.data.displayTypeface',
])
replace(vm, '    fun setChatTextSize(size: Int) {\n', '''    var fontBusy by mutableStateOf(false)
        private set
    var fontResult by mutableStateOf<String?>(null)
        private set
    var fontFailed by mutableStateOf(false)
        private set

    fun displayFontFile(name: String): File? = name.takeIf(DisplayFonts::isFontName)?.let(c.images::file)

    /** Register an immutable copy, but only preview it until the person explicitly saves. */
    fun importDisplayFont(uri: Uri, resolver: ContentResolver, onImported: (DisplayFont) -> Unit) {
        if (fontBusy || backupRestoring) return
        fontBusy = true
        fontFailed = false
        fontResult = null
        c.appScope.launch(Dispatchers.Main) {
            var copied: DisplayFont? = null
            try {
                if (c.settings.current().displayFonts.size >= DisplayFonts.MAX_FONTS) {
                    throw DisplayFontException("最多可导入 ${DisplayFonts.MAX_FONTS} 个字体")
                }
                val font = withContext(Dispatchers.IO) {
                    val name = runCatching {
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    }.getOrNull()
                    DisplayFonts.importFont(c.images.dir, name,
                        open = { resolver.openInputStream(uri) ?: throw DisplayFontException("读不到这个字体文件") },
                        validateNative = { displayTypeface(it) },
                    )
                }
                copied = font
                modelWriter.serially {
                    c.settings.update {
                        if (it.displayFonts.size >= DisplayFonts.MAX_FONTS) throw DisplayFontException("字体数量已达上限")
                        it.copy(displayFonts = it.displayFonts + font)
                    }
                }
                copied = null
                onImported(font)
                fontResult = "已导入「${font.name}」，预览后点保存即可使用。"
            } catch (e: CancellationException) {
                copied?.let { c.images.file(it.file).delete() }
                throw e
            } catch (e: Exception) {
                copied?.let { c.images.file(it.file).delete() }
                fontFailed = true
                fontResult = (e as? DisplayFontException)?.message ?: "字体导入失败，请选择有效的 TTF 或 OTF 文件。"
            } finally {
                fontBusy = false
            }
        }
    }

    fun saveDisplayFont(file: String?) {
        if (fontBusy || backupRestoring) return
        fontBusy = true
        fontFailed = false
        fontResult = null
        c.appScope.launch(Dispatchers.Main) {
            try {
                if (file != null) withContext(Dispatchers.IO) {
                    displayTypeface(displayFontFile(file) ?: throw DisplayFontException("这个字体无效，请重新导入"))
                }
                modelWriter.serially {
                    c.settings.update {
                        if (file != null && it.displayFonts.none { font -> font.file == file }) {
                            throw DisplayFontException("这个字体已不在列表中，请重新导入")
                        }
                        it.copy(displayFont = file)
                    }
                    check(c.settings.current().displayFont == file)
                }
                fontResult = if (file == null) "已恢复默认字体。" else "字体已保存，重新打开软件也会保留。"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fontFailed = true
                fontResult = (e as? DisplayFontException)?.message ?: "字体没有保存成功，请再试一次。"
            } finally {
                fontBusy = false
            }
        }
    }

    fun setChatTextSize(size: Int) {
''')

pages = 'app/src/main/java/com/cleo/cleos/ui/settings/AppPages.kt'
replace(pages, '    Section("字号") {\n', '    DisplayFontSettings(vm)\n\n    Section("字号") {\n')
replace(pages, '表情包、图片、全部软件配置、API Key 和 MCP 连接一起备份', '表情包、图片、自定义字体、全部软件配置、API Key 和 MCP 连接一起备份')

theme = 'app/src/main/java/com/cleo/cleos/ui/theme/CleosTheme.kt'
replace(theme, '    MaterialTheme(colorScheme = colorSchemeFor(palette)) {\n', '''    val selectedFont = settings.displayFonts.firstOrNull { it.file == settings.displayFont }
    ProvideDisplayFont(selectedFont?.let { images.file(it.file) }) {
    MaterialTheme(colorScheme = colorSchemeFor(palette)) {
''')
replace(theme, '    }\n}\n\n@Composable\nprivate fun Wallpaper', '    }\n    }\n}\n\n@Composable\nprivate fun Wallpaper')

backup = 'app/src/main/java/com/cleo/cleos/data/BackupService.kt'
replace(backup, '            listOfNotNull(s.wallpaper, s.userAvatar)).toSet()\n', '''            listOfNotNull(s.wallpaper, s.userAvatar) +
            s.displayFonts.map { it.file }).toSet()
''')
replace(backup, '                    name.startsWith(VOICE_PREFIX) -> voices++\n', '''                    DisplayFonts.isFontName(name) -> Unit // Font files are not counted as pictures.
                    name.startsWith(VOICE_PREFIX) -> voices++
''')
replace(backup, '                { picture(it) != null },\n', '''                { name ->
                    if (DisplayFonts.isFontName(name)) runCatching { displayTypeface(images.file(name)) }.isSuccess
                    else picture(name) != null
                },
''')
portable = 'app/src/main/java/com/cleo/cleos/data/PortableConfiguration.kt'
replace(portable, '        return restored\n', '        return DisplayFonts.restoredPreferences(restored, pictureExists)\n')

for name, package in [
    ('DisplayFonts.kt', 'data'), ('DisplayFontAndroid.kt', 'data'),
    ('DisplayFontTheme.kt', 'ui/theme'), ('DisplayFontSettings.kt', 'ui/settings'),
]:
    destination = ROOT / f'app/src/main/java/com/cleo/cleos/{package}/{name}'
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / 'src' / name, destination)
test = ROOT / 'app/src/test/java/com/cleo/cleos/data/DisplayFontsTest.kt'
test.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(HERE / 'tests/DisplayFontsTest.kt', test)
print('Display fonts applied: TTF/OTF imports, preview, explicit save, global text resolver, default reset and portable files.')
