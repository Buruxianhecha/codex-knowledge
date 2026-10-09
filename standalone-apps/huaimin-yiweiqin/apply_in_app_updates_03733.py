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

# Show the first-party Huaimin updater instead of the inherited Cleos/蓝奏云 link.
pages = src/"ui/settings/AppPages.kt"
once(pages,'''import com.cleo.cleos.glass.GlassShape''',
'''import com.cleo.cleos.glass.GlassShape
import com.cleo.cleos.update.UpdateSettingsSection''')
text = pages.read_text(encoding="utf-8")
about = text.find('internal fun AboutPage()')
start = text.find('    Section(', about)
end = text.find('    // For whoever wants to give something back.', start)
if about < 0 or start < 0 or end < 0 or end <= start:
    raise SystemExit("app-update patch: inherited About section not found")
text = text[:start] + '    UpdateSettingsSection()\n\n' + text[end:]
pages.write_text(text,encoding="utf-8")

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
