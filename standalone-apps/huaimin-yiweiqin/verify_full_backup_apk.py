"""Check full-backup wiring in the compiled APK, not just patched Kotlin sources."""
from pathlib import Path
import argparse
import hashlib
import re
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
assert apk.get_androidversion_name() == "0.37.11"
assert apk.get_androidversion_code() == "62028"

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
assert voice_ids == {"5ee59da9-cb84-437a-8909-8ec1cfceb425"}, ("unexpected compiled Mossland presets", voice_ids)
assert "青年音" in speech_constants, "youth preset label missing"
for name in ("pickVoice", "voiceOf"):
    assert any(f"Lcom/cleo/cleos/ui/settings/SettingsViewModel;->{name}" in call for call in calls), (name, "voice button/field wiring missing")
assert any("Lcom/cleo/cleos/ai/Speech;->builtIn" in call for call in calls), "voice presets not consumed by UI"
print("Compiled Mossland preset verified: 青年音 -> 5ee59da9-cb84-437a-8909-8ec1cfceb425; existing selection and ID-field wiring preserved.")
print(f"Full backup DEX wiring verified: {sum(map(len, required.values()))} methods, serializers and prior features.")
print(f"Verified APK: version=0.37.11 code=62028 bytes={path.stat().st_size} sha256={hashlib.sha256(path.read_bytes()).hexdigest()}")
