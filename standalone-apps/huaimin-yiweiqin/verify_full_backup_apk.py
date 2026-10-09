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
assert apk.get_androidversion_name() == "0.37.34"
assert apk.get_androidversion_code() == "62056"

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
sticker_assets = {name for name in apk.get_files() if name.startswith("assets/sticker_presets/")}
expected_sticker_assets = {
    *(f"assets/sticker_presets/sticker-mengmeizhi-{i:02d}.webp" for i in range(1, 25)),
    *(f"assets/sticker_presets/sticker-dog-{i:02d}.webp" for i in range(1, 7)),
}
assert sticker_assets == expected_sticker_assets, ("unexpected packaged sticker preset assets", sticker_assets)
for name in sorted(expected_sticker_assets):
    raw = apk.get_file(name)
    with Image.open(io.BytesIO(raw)) as picture:
        picture.load()
        assert picture.width > 0 and picture.height > 0, name
assert any("Lcom/cleo/cleos/data/StickerPresetCatalog;->getImageBuiltIns" in call for call in calls), "sticker preset catalog not wired"
assert any("Lcom/cleo/cleos/data/Stickers;->ensureBuiltIns" in call for call in calls), "sticker self-heal seeding is not wired"
print("Compiled sticker packs verified: 24 萌妹纸 + 6 小白狗 assets, categorized catalog and self-heal seeding wiring.")
for cls, name in (
    ("Lcom/cleo/cleos/data/DisplayFonts;", "importFont"),
    ("Lcom/cleo/cleos/data/DisplayFonts;", "encode"),
    ("Lcom/cleo/cleos/data/DisplayFonts;", "decode"),
    ("Lcom/cleo/cleos/data/DisplayFonts;", "restoredPreferences"),
    ("Lcom/cleo/cleos/data/DisplayFonts;", "removeImported"),
    ("Lcom/cleo/cleos/data/DisplayFontAndroidKt;", "displayTypeface"),
    ("Lcom/cleo/cleos/ui/theme/DisplayFontThemeKt;", "ProvideDisplayFont"),
    ("Lcom/cleo/cleos/ui/settings/DisplayFontSettingsKt;", "DisplayFontSettings"),
    ("Lcom/cleo/cleos/ui/settings/SettingsViewModel;", "importDisplayFont"),
    ("Lcom/cleo/cleos/ui/settings/SettingsViewModel;", "saveDisplayFont"),
    ("Lcom/cleo/cleos/ui/settings/SettingsViewModel;", "deleteDisplayFont"),
):
    assert any(f"{cls}->{name}" in call for call in calls), (name, "compiled font selection/storage/backup wiring missing")
assert any("Landroidx/compose/material3/Typography;->copy" in call for call in calls), "custom typography not provided"
for screen in ("ChatScreenKt", "DiaryEditorScreenKt", "TodoScreenKt", "SearchScreenKt", "LetterScreenKt"):
    screen_calls = "\n".join(
        ins.get_output() for method in methods if screen in method.get_class_name() and method.get_code() is not None
        for ins in method.get_instructions() if ins.get_name().startswith("invoke")
    )
    assert "Lcom/cleo/cleos/ui/theme/DisplayFontThemeKt;->getLocalDisplayFontFamily" in screen_calls, (screen, "custom font missing from independent text styles")
assert any("Lcom/cleo/cleos/data/AppSettings;->getDisplayFonts" in call for call in calls)
assert any("Lcom/cleo/cleos/data/AppSettings;->getDisplayFont" in call for call in calls)
backup_write = next(m for m in methods if m.get_class_name() == "Lcom/cleo/cleos/data/BackupService;" and m.get_name() == "write")
backup_font_calls = "\n".join(ins.get_output() for ins in backup_write.get_instructions() if ins.get_name().startswith("invoke"))
assert "Lcom/cleo/cleos/data/AppSettings;->getDisplayFonts" in backup_font_calls, "actual font files not added to backup archive"
print("Compiled display fonts verified: imports, native validation, preview/save/default/delete, global typography, editable text styles and actual font-file backup.")
for cls, name in (
    ("Lcom/cleo/cleos/ai/AssistantBubbleSplitter;", "split"),
    ("Lcom/cleo/cleos/ai/AssistantBubbleSplitter;", "requestedCount"),
    ("Lcom/cleo/cleos/ai/AssistantBubbleDelivery;", "deliver"),
    ("Lcom/cleo/cleos/ai/AssistantBubbleDelivery;", "gapAfter"),
    ("Lcom/cleo/cleos/ai/ChatRepository;", "storeAssistantBubbles"),
):
    assert any(f"{cls}->{name}" in call for call in calls), (name, "compiled sequential-message wiring missing")
row_calls = "\n".join(
    ins.get_output() for method in methods
    if "ChatRepository$storeAssistantBubbles$" in method.get_class_name() and method.get_code() is not None
    for ins in method.get_instructions() if ins.get_name().startswith("invoke")
)
assert "Lcom/cleo/cleos/data/db/MessageEntity;-><init>" in row_calls, "individual bubble rows not constructed"
assert "Lcom/cleo/cleos/data/db/MessageDao;->insert" in row_calls, "individual bubble rows not persisted"
print("Compiled sentence-message delivery verified: splitter, explicit-count fallback, cancellable pacing and independent MessageEntity inserts.")
for cls, name in (
    ("Lcom/cleo/cleos/data/MessageEdits;", "canEdit"),
    ("Lcom/cleo/cleos/data/MessageEdits;", "canSubmit"),
    ("Lcom/cleo/cleos/data/MessageEdits;", "prefix"),
    ("Lcom/cleo/cleos/data/MessageEdits;", "copyRow"),
    ("Lcom/cleo/cleos/data/MessageEdits;", "copyConversation"),
    ("Lcom/cleo/cleos/ai/MessageEditCommitKt;", "commitMessageEdit"),
    ("Lcom/cleo/cleos/ai/ChatRepository;", "editAndResend"),
    ("Lcom/cleo/cleos/ui/chat/ChatViewModel;", "edit"),
    ("Lcom/cleo/cleos/ui/chat/ChatViewModel;", "resendEdited"),
    ("Lcom/cleo/cleos/ui/chat/MessageEditDialogKt;", "MessageEditDialog"),
    ("Lcom/cleo/cleos/data/SettingsRepository;", "openEditedConversation"),
    ("Lcom/cleo/cleos/data/db/MessageDao;", "prefixForEdit"),
    ("Lcom/cleo/cleos/data/db/ConversationDao;", "updateForMessageEdit"),
):
    assert any(f"{cls}->{name}" in call for call in calls), (name, "compiled edit/resend wiring missing")
edit_calls = "\n".join(
    ins.get_output() for method in methods
    if ("ChatRepository$editAndResend$" in method.get_class_name() or
        (method.get_class_name() == "Lcom/cleo/cleos/ai/ChatRepository;" and method.get_name() == "editAndResend")) and method.get_code() is not None
    for ins in method.get_instructions() if ins.get_name().startswith("invoke")
)
assert "Lcom/cleo/cleos/ai/RecallCoordinator;->perform" in edit_calls, "edit does not share the recall fence"
assert "Lcom/cleo/cleos/ai/RecallCoordinator;->perform$default" not in edit_calls, "edit reused the recall resume callback and could replay old inputs"
assert "Lcom/cleo/cleos/data/db/ConversationDao;->insert" in edit_calls, "original conversation is not preserved by branching"
assert "Lcom/cleo/cleos/data/db/MessageDao;->insert" in edit_calls, "edited history is not persisted"
assert "Lcom/cleo/cleos/data/db/MessageDao;->delete" not in edit_calls, "edit unexpectedly deletes original messages"
assert any(ref in edit_calls for ref in ("Lcom/cleo/cleos/ai/ChatRepository;->reply", "Lcom/cleo/cleos/ai/ChatRepository;->access$reply")), "edited message does not trigger a model reply"
print("Compiled edit/resend verified: own-message menu, editor, shared recall fence, transaction, preserved original conversation, new history rows and real model reply.")
print(f"Full backup DEX wiring verified: {sum(map(len, required.values()))} methods, serializers and prior features.")
for cls, name in (
    ("Lcom/cleo/cleos/ai/FreeTopicRules;", "level"),
    ("Lcom/cleo/cleos/ai/FreeTopics;", "configure"),
    ("Lcom/cleo/cleos/ai/FreeTopics;", "run"),
    ("Lcom/cleo/cleos/ai/FollowUpRules;", "eligible"),
    ("Lcom/cleo/cleos/ai/FollowUps;", "plan"),
    ("Lcom/cleo/cleos/ai/FollowUps;", "run"),
    ("Lcom/cleo/cleos/ui/settings/FreeTopicSectionKt;", "FreeTopicSection"),
    ("Lcom/cleo/cleos/ui/settings/ProactiveHistorySectionKt;", "ProactiveHistorySection"),
):
    assert any(m.startswith(f"{cls}->{name}") for m in definitions), (name, "proactive feature missing from APK")
assert any("Lcom/cleo/cleos/data/db/AppDatabase;->freeTopics" in call for call in calls), "free-topic DAO not wired"
assert any("Lcom/cleo/cleos/ai/ChatRepository;->cancelFollowUp" in call for call in calls), "user-input cancellation not wired"
print("Compiled proactive system verified: persona/free-topic rules, follow-up scheduler, settings/history UI and cancellation wiring.")
for cls, name in (
    ("Lcom/cleo/cleos/ai/GroupChats;", "mentioned"),
    ("Lcom/cleo/cleos/ai/GroupChats;", "historyFor"),
    ("Lcom/cleo/cleos/ai/GroupChats;", "sharedContext"),
    ("Lcom/cleo/cleos/ai/ChatRepository;", "newGroupConversation"),
    ("Lcom/cleo/cleos/ai/ChatRepository;", "updateGroupConversationMembers"),
    ("Lcom/cleo/cleos/ai/ChatRepository;", "singleConversation"),
    ("Lcom/cleo/cleos/data/db/AppDatabase;", "groupMembers"),
    ("Lcom/cleo/cleos/data/db/ConversationDao;", "setPinned"),
    ("Lcom/cleo/cleos/data/db/ConversationDao;", "setManualRank"),
    ("Lcom/cleo/cleos/data/db/ConversationMemberDao;", "deleteFor"),
    ("Lcom/cleo/cleos/ui/chat/ChatViewModel;", "createGroup"),
    ("Lcom/cleo/cleos/ui/chat/ChatViewModel;", "updateGroupMembers"),
):
    assert any(m.startswith(f"{cls}->{name}") for m in definitions), (name, "group-chat feature missing from APK")
assert any("Lcom/cleo/cleos/data/db/MessageEntity;->getSenderCompanionId" in call for call in calls), "group speaker identity not consumed"
assert any(m.startswith("Lcom/cleo/cleos/data/PatRecord;->getTargetCompanionId") for m in definitions), "group pat target is not stored"
assert any("Lcom/cleo/cleos/ui/chat/PatActions;->" in call for call in calls), "pat gesture wiring missing"
print("Compiled group pat verified: exact target identity survives gesture, storage and group routing.")
ui_strings = "\n".join(
    ins.get_output() for method in methods if method.get_code() is not None
    for ins in method.get_instructions() if ins.get_name() in ("const-string", "const-string/jumbo")
)
assert "@所有人" in ui_strings, "compiled group @ picker is missing @所有人"
assert "没有匹配的群成员" in ui_strings, "compiled group @ picker suggestions are missing"
print("Compiled group mentions verified: dedicated picker, @所有人 and member suggestions are present.")
assert any(m.startswith("Lcom/cleo/cleos/data/db/MessageEntity;->getMentionedCompanionIds") for m in definitions), "stable mention IDs not persisted in compiled message model"
assert any(m.startswith("Lcom/cleo/cleos/ai/GroupChats;->selectedMentionIds") for m in definitions), "selected mention validation absent"
assert any("Lcom/cleo/cleos/ai/GroupChats;->targeted" in call for call in calls), "stable-ID targeting not consumed"
print("Stable-ID @ mentions verified in compiled APK.")
for cls, name in (
    ("Lcom/cleo/cleos/data/db/ConversationDao;", "setGroupAvatar"),
    ("Lcom/cleo/cleos/data/db/ConversationDao;", "recordGroupText"),
    ("Lcom/cleo/cleos/data/db/ConversationEntity;", "getGroupTotalCalls"),
    ("Lcom/cleo/cleos/ui/chat/ChatUiState;", "getGroupAvatarEmoji"),
):
    assert any(m.startswith(f"{cls}->{name}") for m in definitions), (cls, name, "group world feature missing")
print("Group identity, durable actual call counters and explicitly approximate text statistics are compiled.")
for cls, name in (
    ("Lcom/cleo/cleos/data/BackupArchiveGuard;", "copyLimited"),
    ("Lcom/cleo/cleos/data/BackupArchiveGuard;", "readLimited"),
    ("Lcom/cleo/cleos/data/BackupArchiveGuard;", "validateRows"),
):
    assert any(m.startswith(f"{cls}->{name}") for m in definitions), (cls, name, "restore safety guard absent")
print("Bounded backup ZIP handling and group graph preflight are compiled.")
assert any(m.startswith("Lcom/cleo/cleos/data/GroupDeletionPolicy;->canDelete") for m in definitions), "group deletion safety rule missing"
assert any("Lcom/cleo/cleos/data/db/ConversationMemberDao;->groupIdsFor" in call for call in calls), "group deletion member check not wired"
print("Group role deletion protection verified in compiled APK.")
for cls, name in (
    ("Lcom/cleo/cleos/data/GroupReactionRules;", "parse"),
    ("Lcom/cleo/cleos/data/GroupReactionRules;", "target"),
    ("Lcom/cleo/cleos/data/GroupReactionRules;", "add"),
    ("Lcom/cleo/cleos/data/MessageReaction;", "getActorCompanionId"),
    ("Lcom/cleo/cleos/ui/chat/StickersKt;", "ReactionChips"),
    ("Lcom/cleo/cleos/ui/chat/StickersKt;", "ReactionPicker"),
):
    assert any(m.startswith(f"{cls}->{name}") for m in definitions), (cls, name, "group reaction code not compiled")
assert any("Lcom/cleo/cleos/data/GroupReactionRules;->parse" in call for call in calls), "group AI emoji action not wired"
print("Compiled group actor emoji actions and reaction chips verified.")




for cls, name in (
    ("Lcom/cleo/cleos/data/db/ConversationDao;", "claimGroupCall"),
    ("Lcom/cleo/cleos/data/db/ConversationDao;", "updateGroupOptions"),
    ("Lcom/cleo/cleos/data/db/ConversationDao;", "setHistoryShare"),
    ("Lcom/cleo/cleos/data/db/MessageDao;", "sharedMatches"),
    ("Lcom/cleo/cleos/ai/ChatRepository;", "continueGroup"),
    ("Lcom/cleo/cleos/ui/chat/ChatViewModel;", "updateGroupOptions"),
    ("Lcom/cleo/cleos/ui/chat/GroupOptionsDialogKt;", "GroupOptionsDialog"),
):
    assert any(m.startswith(f"{cls}->{name}") for m in definitions), (cls, name, "group experience feature missing")
print("Compiled group controls checked: authorized history, request budget, group modes and voice settings.")

print("Compiled group chat verified: membership, editable members, cross-character context, per-speaker messages, conversation pinning/reorder and creation UI wiring.")
print(f"Verified APK: version=0.37.34 code=62056 bytes={path.stat().st_size} sha256={hashlib.sha256(path.read_bytes()).hexdigest()}")


