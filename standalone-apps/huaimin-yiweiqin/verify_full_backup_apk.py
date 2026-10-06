"""Check full-backup wiring in the compiled APK, not just patched Kotlin sources."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import re
from PIL import Image
from loguru import logger
from androguard.core.apk import APK
from androguard.core.dex import DEX

logger.remove()
parser = argparse.ArgumentParser()
parser.add_argument("--apk", required=True)
args = parser.parse_args()
path = Path(args.apk)
apk = APK(str(path))
assert apk.get_package() == "com.lin.huaimin"
assert apk.get_androidversion_name() == "0.37.14"
assert apk.get_androidversion_code() == "62031"

methods = []
for dex_bytes in apk.get_all_dex():
    for cls in DEX(dex_bytes).get_classes():
        methods.extend(cls.get_methods())
definitions = {f"{m.get_class_name()}->{m.get_name()}" for m in methods}
calls = set()
for method in methods:
    if method.get_code() is None:
        continue
    for ins in method.get_instructions():
        if ins.get_name().startswith("invoke"):
            calls.add(ins.get_output())

required = {
    "Lcom/cleo/cleos/data/SecretStore;": [
        "backupSecrets", "prepareBackupSecrets", "encryptedBackupState", "replaceEncryptedBackupState",
    ],
    "Lcom/cleo/cleos/data/SettingsRepository;": ["backupPreferences", "replaceBackupPreferences"],
    "Lcom/cleo/cleos/data/ConfigurationCodec;": ["encode", "decode"],
    "Lcom/cleo/cleos/data/PreferenceBackup;": ["capture", "replace", "validate"],
    "Lcom/cleo/cleos/data/SecretBackup;": ["decryptAll", "encryptAll", "prepareRestore"],
    "Lcom/cleo/cleos/data/PortableConfigurationKt;": ["withConfigurationRollback"],
    "Lcom/cleo/cleos/ui/settings/SettingsViewModel;": ["reloadBackupFields", "loadGlobalFields"],
}
for cls, names in required.items():
    for name in names:
        # Internal Kotlin APIs can have a module-name suffix.
        assert any(m.startswith(f"{cls}->{name}") for m in definitions), (cls, name, "missing compiled method")
        assert any(f"{cls}->{name}" in call for call in calls), (cls, name, "not wired into compiled app")
for cls in ("Lcom/cleo/cleos/data/ConfigurationBackup;", "Lcom/cleo/cleos/data/BackupPreference;"):
    assert any(m.startswith(cls.replace(";", "$Companion;") + "->serializer") for m in definitions), (cls, "serializer missing")

assert any("Lcom/cleo/cleos/data/BackupService;->restoreFrom" in call for call in calls)
assert any("Lcom/cleo/cleos/data/BackupService;->write" in call for call in calls)
assert any("Lcom/cleo/cleos/ai/PhoneAppActions;->" in call for call in calls), "phone-App tools disappeared"
assert any("Lcom/cleo/cleos/data/Recalls;->" in call for call in calls), "recall disappeared"
assert any("Lcom/cleo/cleos/data/ReactionEvents;->" in call for call in calls), "reaction awareness disappeared"
speech_init = next(
    m for m in methods
    if m.get_class_name() == "Lcom/cleo/cleos/ai/Speech;" and m.get_name() == "<clinit>"
)
speech_constants = "\n".join(
    ins.get_output() for ins in speech_init.get_instructions()
    if ins.get_name() in ("const-string", "const-string/jumbo")
)
voice_ids = set(re.findall(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", speech_constants))
assert voice_ids == {"5ee59da9-cb84-437a-8909-8ec1cfceb425", "19411508-8731-4b68-901d-7e4b8a98e23f"}, ("unexpected compiled Mossland presets", voice_ids)
assert "青年音" in speech_constants and "少女音" in speech_constants, "voice preset labels missing"
for name in ("pickVoice", "voiceOf"):
    assert any(f"Lcom/cleo/cleos/ui/settings/SettingsViewModel;->{name}" in call for call in calls), (name, "voice button/field wiring missing")
assert any("Lcom/cleo/cleos/ai/Speech;->builtIn" in call for call in calls), "voice presets not consumed by UI"
print("Compiled Mossland presets verified: 青年音 -> 5ee59da9-cb84-437a-8909-8ec1cfceb425; 少女音 -> 19411508-8731-4b68-901d-7e4b8a98e23f; existing selection and ID-field wiring preserved.")
avatar_manifest = json.loads((Path(__file__).resolve().parent / "assets/avatar_presets/manifest.json").read_text())
assert len(avatar_manifest) == 14
expected_assets = {"assets/avatar_presets/" + item["file_name"] for item in avatar_manifest}
actual_assets = {name for name in apk.get_files() if name.startswith("assets/avatar_presets/")}
assert actual_assets == expected_assets, ("unexpected packaged avatar assets", actual_assets)
for item in avatar_manifest:
    raw = apk.get_file("assets/avatar_presets/" + item["file_name"])
    assert hashlib.sha256(raw).hexdigest() == item["sha256"], (item["file_name"], "original image changed")
    with Image.open(io.BytesIO(raw)) as picture:
        picture.load()
        assert picture.size == (item["width"], item["height"]), item["file_name"]
avatar_init = next(m for m in methods if m.get_class_name() == "Lcom/cleo/cleos/data/AvatarPresets;" and m.get_name() == "<clinit>")
avatar_constants = "\n".join(ins.get_output() for ins in avatar_init.get_instructions() if ins.get_name() in ("const-string", "const-string/jumbo"))
assert set(re.findall(r"avatar_\d{2}\.png", avatar_constants)) == {item["file_name"] for item in avatar_manifest}
for cls, name in (
    ("Lcom/cleo/cleos/data/AvatarPresets;", "getAll"),
    ("Lcom/cleo/cleos/data/AvatarPresets;", "copyToStore"),
    ("Lcom/cleo/cleos/data/AvatarPreset;", "isSelected"),
    ("Lcom/cleo/cleos/data/AvatarSaveQueue;", "submit"),
    ("Lcom/cleo/cleos/ui/settings/SettingsViewModel;", "setAvatarPreset"),
    ("Lcom/cleo/cleos/ui/settings/AvatarPresetPickerKt;", "AvatarPresetPicker"),
):
    assert any(f"{cls}->{name}" in call for call in calls), (name, "compiled avatar selection wiring missing")
print("Compiled avatar presets verified: all 14 original images, picker, per-TA image storage, selection state and ordered saving.")
for cls, name in (
    ("Lcom/cleo/cleos/data/DisplayFonts;", "importFont"),
    ("Lcom/cleo/cleos/data/DisplayFonts;", "encode"),
    ("Lcom/cleo/cleos/data/DisplayFonts;", "decode"),
    ("Lcom/cleo/cleos/data/DisplayFonts;", "restoredPreferences"),
    ("Lcom/cleo/cleos/data/DisplayFontAndroidKt;", "displayTypeface"),
    ("Lcom/cleo/cleos/ui/theme/DisplayFontThemeKt;", "ProvideDisplayFont"),
    ("Lcom/cleo/cleos/ui/settings/DisplayFontSettingsKt;", "DisplayFontSettings"),
    ("Lcom/cleo/cleos/ui/settings/SettingsViewModel;", "importDisplayFont"),
    ("Lcom/cleo/cleos/ui/settings/SettingsViewModel;", "saveDisplayFont"),
):
    assert any(f"{cls}->{name}" in call for call in calls), (name, "compiled font selection/storage/backup wiring missing")
assert any("Landroidx/compose/ui/platform/CompositionLocalsKt;->getLocalFontFamilyResolver" in call for call in calls), "global font resolver not provided"
assert "Lcom/cleo/cleos/ui/theme/DisplayFontResolver;->resolve" in definitions
assert any("Lcom/cleo/cleos/data/AppSettings;->getDisplayFonts" in call for call in calls)
assert any("Lcom/cleo/cleos/data/AppSettings;->getDisplayFont" in call for call in calls)
backup_write = next(m for m in methods if m.get_class_name() == "Lcom/cleo/cleos/data/BackupService;" and m.get_name() == "write")
backup_font_calls = "\n".join(ins.get_output() for ins in backup_write.get_instructions() if ins.get_name().startswith("invoke"))
assert "Lcom/cleo/cleos/data/AppSettings;->getDisplayFonts" in backup_font_calls, "actual font files not added to backup archive"
print("Compiled display fonts verified: imports, native validation, preview/save/default, global text resolution and actual font-file backup.")
print(f"Full backup DEX wiring verified: {sum(map(len, required.values()))} methods, serializers and prior features.")
print(f"Verified APK: version=0.37.14 code=62031 bytes={path.stat().st_size} sha256={hashlib.sha256(path.read_bytes()).hexdigest()}")

