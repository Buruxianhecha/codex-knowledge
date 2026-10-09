#!/usr/bin/env python3
"""v0.37.38: safely add two offline OFL-licensed presets to the existing font system.

Runs only AFTER the v0.37.37beta patches. No database migration.
The CI download step supplies actual, inspected TTF files in assets.
"""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

def change(rel, before, after, description):
    p = root / rel
    original = p.read_text(encoding="utf-8")
    count = original.count(before)
    if count != 1:
        raise RuntimeError(f"{description}: expected one anchor, found {count}")
    p.write_text(original.replace(before, after, 1), encoding="utf-8")

app = "app/src/main/java/com/cleo/cleos/CleosApp.kt"
change(app,
    "import com.cleo.cleos.data.ensureBundledDisplayFont\n",
    "import com.cleo.cleos.data.ensureBundledDisplayFont\n"
    "import com.cleo.cleos.data.ensureExtraBundledDisplayFonts\n",
    "font registration import")
change(app,
    '''            companions.ensure()
            stickers.ensureBuiltIns()
''',
    '''            // Two real offline OFL fonts are provisioned separately. A malformed
            // legacy preset cannot hide these; existing selection and imported files stay.
            val extraFonts = runCatching {
                ensureExtraBundledDisplayFonts(context.assets, images.dir)
            }.getOrElse { emptyList() }
            if (extraFonts.isNotEmpty()) {
                settings.update { previous ->
                    val unique = (extraFonts + previous.displayFonts)
                        .distinctBy { it.file }
                    previous.copy(displayFonts = unique)
                }
            }
            companions.ensure()
            stickers.ensureBuiltIns()
''',
    "register new real font presets without losing old catalog")
settings = "app/src/main/java/com/cleo/cleos/ui/settings/DisplayFontSettings.kt"
# In-app credits are visible and also delivered as complete OFL copies in assets.
# Do not change the existing preview, save, system default or custom import logic.
for filename, dest in [
    ("DisplayFonts.kt", "app/src/main/java/com/cleo/cleos/data/DisplayFonts.kt"),
    ("DisplayFontAndroid.kt", "app/src/main/java/com/cleo/cleos/data/DisplayFontAndroid.kt"),
    ("DisplayFontsTest.kt", "app/src/test/java/com/cleo/cleos/data/DisplayFontsTest.kt"),
]:
    target = root / dest
    if not target.exists():
        raise RuntimeError(f"Missing existing font system: {target}")
    copyfile(here / ("tests" if filename.endswith("Test.kt") else "src") / filename, target)

build = root / "app/build.gradle.kts"
txt = build.read_text(encoding="utf-8")
for before, after in [
    ('versionName = "0.37.37beta"', 'versionName = "0.37.38"'),
    ('versionCode = 62060', 'versionCode = 62061'),
]:
    if txt.count(before) != 1:
        raise RuntimeError(f"Version anchor missing or ambiguous: {before}")
    txt = txt.replace(before, after, 1)
build.write_text(txt, encoding="utf-8")
print("v0.37.38 62061: two stable offline OFL font presets installed into existing font system")
