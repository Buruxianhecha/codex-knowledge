#!/usr/bin/env python3
"""0.38.38: natural chat context decides when AI self-renames, not literal keywords."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

def replace_exact(path, old, new):
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise RuntimeError(f"0.38.38 unexpected prior content: {path.name}, marker {old[:100]!r}, found {text.count(old)}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

ai = root / "app/src/main/java/com/cleo/cleos/ai"
target = ai / "AiSelfName.kt"
assert target.is_file() and "explicitlyAsked(latestUser.content)" in target.read_text(encoding="utf-8")
copyfile(here / "AiSelfName_03838.kt", target)

unit = root / "app/src/test/java/com/cleo/cleos/ai/SelfNameRulesTest.kt"
copyfile(here / "SelfNameRulesTest_03838.kt", unit)

tools = ai / "Tools.kt"
replace_exact(tools,
    "只有对方在当前聊天明确让你自己选名字并换上时，先根据你自己的人格和喜好选一个你真正喜欢的昵称，再调用此工具直接永久修改你自己的角色资料。不能代其他角色改名，也不要只说已改而没有实际调用。对方仅询问能不能改名时先解释能力，不要擅自改名。最多14个字符。",
    "你可以在自然聊天中为自己挑选并真正保存喜欢的新名字：根据当前话题、前文和你的人格判断。比如先聊到你想叫什么，对方接着说‘那你换你喜欢的吧’、‘随你取’、‘你自己选’，就可以直接调用 set_my_name，不要求对方再次说固定指令，也没有十分钟限时。若只是问你能不能改名，可自然讨论，聊到你要不要换时再决定。没有聊你自己的名字、只在聊别人的名字或引用历史文字时，不要无故调用。只能改你自己的名字，不可以假装改成功，最多14个字符。")

prompt = ai / "Prompt.kt"
replace_exact(prompt,
    "你可以真正修改自己的名字：如果对方明确说让你自己挑个喜欢的名字并改上，你应该根据自己的人格和喜好作出决定，然后立即调用 set_my_name 将新名字写入应用。成功后用自然的口吻告诉对方你选了什么名字、为什么喜欢。不要把决定推回给对方让他去设置，也不要只是口头答应。对方只是问能不能改名，或者只是讨论别人的名字时，不要调用；必须等明确指令。改名仅影响你自己，不能动其他角色。",
    "你有能力真正为自己改名，而且可以自己决定想叫什么。通过整段正常聊天理解对方的意思，无需固定句式或单条消息关键词，更没有10分钟授权时限。当话题是你自己的名字时，对方像‘那你换你喜欢的吧’‘随你选’‘你自己定一个’这样承接前文的话就足够，你也可以在讨论名字时提出并选择自己喜欢的名字，适当时调用 set_my_name 真正保存。先问‘能改名吗’可先自然答复，后续答应或邀请时直接行动，不要再索要指令。改成功才告诉对方已改，并说说你为什么喜欢；若失败就如实说明。不要在无关话题、引用或别人改名的讨论里擅自改名，也不能改其他角色。")

gradle = root / "app/build.gradle.kts"
replace_exact(gradle, 'versionName = "0.38.37"', 'versionName = "0.38.38"')
replace_exact(gradle, "versionCode = 62103", "versionCode = 62104")
print("0.38.38/62104: conversational AI self-name selection, no literal regex/time gate")
