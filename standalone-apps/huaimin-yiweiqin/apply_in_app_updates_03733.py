#!/usr/bin/env python3
"""0.37.33: independent update feed, optional on-launch notice, verified APK installer."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
src = root/"app/src/main/java/com/cleo/cleos"

def once(path, before, after):
    text = path.read_text(encoding="utf-8")
    count = text.count(before)
    if count != 1:
        raise SystemExit("app-update patch: %s anchor count %s: %r" % (path, count, before[:160]))
    path.write_text(text.replace(before, after, 1), encoding="utf-8")

# One application-wide controller: settings and startup dialog never race each other.
cleos = src/"CleosApp.kt"
once(cleos,'''    val settings = SettingsRepository(context)
    val secrets = SecretStore(context)''',
'''    val settings = SettingsRepository(context)
    val updates = com.cleo.cleos.update.UpdateController(context.applicationContext)
    val secrets = SecretStore(context)''')

activity = src/"MainActivity.kt"
once(activity,
'''import com.cleo.cleos.ui.theme.CleosTheme''',
'''import com.cleo.cleos.ui.theme.CleosTheme
import com.cleo.cleos.update.UpdateNoticeHost''')
# Preserve the existing themed container: earlier patches also change this call site.
once(activity, 'CleosNavHost()', 'CleosNavHost(); UpdateNoticeHost()')

# A separate first-party settings page, rather than altering inherited Cleos About.
pages = src/"ui/settings/SettingsPages.kt"
once(pages,
'''    Data("数据与备份"),
    About("关于"),''',
'''    Data("数据与备份"),
    Updates("软件更新"),
    About("关于"),''')
once(pages,
'''        Entry(Icons.Rounded.Inventory2, "数据与备份", "导出、恢复、从别的 App 搬过来") { onOpen(SettingsPage.Data) }
        RowDivider()
        if (crashed) {''',
'''        Entry(Icons.Rounded.Inventory2, "数据与备份", "导出、恢复、从别的 App 搬过来") { onOpen(SettingsPage.Data) }
        RowDivider()
        Entry(Icons.Rounded.Info, "软件更新", "启动时检查更新 · 下载安装包 · 更新日志") {
            onOpen(SettingsPage.Updates)
        }
        RowDivider()
        if (crashed) {''')
screen = src/"ui/settings/SettingsScreen.kt"
once(screen,
'''import com.cleo.cleos.ui.common.GlassPage''',
'''import com.cleo.cleos.ui.common.GlassPage
import com.cleo.cleos.update.UpdateSettingsSection''')
once(screen, 'SettingsPage.Data -> DataPage(vm)',
    'SettingsPage.Data -> DataPage(vm)\n                    SettingsPage.Updates -> UpdateSettingsSection()')

# Installer goes through FileProvider and Android's explicit user confirmation.
manifest = root/"app/src/main/AndroidManifest.xml"
once(manifest,'''    <uses-permission android:name="android.permission.INTERNET" />''',
'''    <uses-permission android:name="android.permission.INTERNET" />
    <!-- Only for the user-initiated APK update flow; installation is never silent. -->
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />''')
once(manifest,'''        <service
            android:name=".ReplyKeeper"''',
'''        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="¤{applicationId}.updates"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/huaimin_update_paths" />
        </provider>

        <service
            android:name=".ReplyKeeper"'''.replace('¤','$'))
resource = root/"app/src/main/res/xml/huaimin_update_paths.xml"
resource.parent.mkdir(parents=True, exist_ok=True)
resource.write_text('''<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-files-path name="updates" path="Download/" />
</paths>
''', encoding="utf-8")

for file, destination in (
    ("UpdatePolicy.kt", src/"update/UpdatePolicy.kt"),
    ("UpdateController.kt", src/"update/UpdateController.kt"),
    ("UpdateUi.kt", src/"update/UpdateUi.kt"),
    ("UpdatePolicyTest.kt",root/"app/src/test/java/com/cleo/cleos/update/UpdatePolicyTest.kt"),
):
    destination.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(here/file,destination)

gradle = root/"app/build.gradle.kts"
once(gradle,'versionName = "0.37.32"','versionName = "0.37.33"')
once(gradle,'versionCode = 62054','versionCode = 62055')
print("Huaimin 0.37.33 / 62055: in-app updates, prompt, signed APK verification & Android installer. No database migration.")
