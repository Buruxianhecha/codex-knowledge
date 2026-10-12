#!/usr/bin/env python3
"""0.38.37: add a real per-character AI self-rename tool without a DB migration."""
from pathlib import Path
from shutil import copyfile
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent

def replace_exact(path, old, new):
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise RuntimeError(f"Self-rename patch precondition failed at {path.name}: {old[:85]!r} (found {text.count(old)})")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")

ai = root / "app/src/main/java/com/cleo/cleos/ai"
tools = ai / "Tools.kt"
replace_exact(tools,
    "    val setMyAvatar = ToolSpec(",
    '''    val setMyName = ToolSpec(
        name = "set_my_name",
        groups = setOf(ToolGroup.Messages),
        action = "给自己改名",
        description = "只有对方在当前聊天明确让你自己选名字并换上时，先根据你自己的人格和喜好选一个你真正喜欢的昵称，再调用此工具直接永久修改你自己的角色资料。不能代其他角色改名，也不要只说已改而没有实际调用。对方仅询问能不能改名时先解释能力，不要擅自改名。最多14个字符。",
        parameters = schema(
            required = listOf("name"),
            "name" to prop("string", "你亲自决定的新名字；不能让对方代替你选择"),
        ),
    )

    val setMyAvatar = ToolSpec(''')
replace_exact(tools, "        setMyAvatar,\n", "        setMyAvatar,\n        setMyName,\n")
replace_exact(tools,
    "    private val avatar: SelfAvatar? = null,\n",
    "    private val avatar: SelfAvatar? = null,\n    private val selfName: SelfName? = null,\n")
replace_exact(tools,
    "                ToolSpecs.setMyAvatar.name -> setMyAvatar(args, conversationId, companionId)\n",
    "                ToolSpecs.setMyAvatar.name -> setMyAvatar(args, conversationId, companionId)\n                ToolSpecs.setMyName.name -> setMyName(args, conversationId, companionId)\n")
replace_exact(tools,
    "    private suspend fun setMyAvatar(a: JsonObject, conversationId: Long, companionId: Long): ToolOutcome {",
    '''    private suspend fun setMyName(a: JsonObject, conversationId: Long, companionId: Long): ToolOutcome {
        val changer = selfName ?: throw ToolFailure("这台设备未接入改名功能。", "改名功能不可用")
        val chosen = ToolArgs.text(a, "name")?.trim().orEmpty()
        if (chosen.isEmpty()) throw ToolFailure("请先自己选一个喜欢的名字再调用工具。", "没有提供新名字")
        val result = changer.rename(conversationId, companionId, chosen)
        return ToolOutcome(result, "AI自主改名成功：「$chosen」")
    }

    private suspend fun setMyAvatar(a: JsonObject, conversationId: Long, companionId: Long): ToolOutcome {''')

prompt = ai / "Prompt.kt"
replace_exact(prompt,
    "        if (ToolGroup.Todos in tools) {\n",
    '''        if (ToolGroup.Messages in tools) add("你可以真正修改自己的名字：如果对方明确说让你自己挑个喜欢的名字并改上，你应该根据自己的人格和喜好作出决定，然后立即调用 set_my_name 将新名字写入应用。成功后用自然的口吻告诉对方你选了什么名字、为什么喜欢。不要把决定推回给对方让他去设置，也不要只是口头答应。对方只是问能不能改名，或者只是讨论别人的名字时，不要调用；必须等明确指令。改名仅影响你自己，不能动其他角色。")
        if (ToolGroup.Todos in tools) {
''')
app = root / "app/src/main/java/com/cleo/cleos/CleosApp.kt"
replace_exact(app, "import com.cleo.cleos.ai.AiSelfAvatar\n",
    "import com.cleo.cleos.ai.AiSelfAvatar\nimport com.cleo.cleos.ai.AiSelfName\n")
replace_exact(app, "        avatar = AiSelfAvatar(db, images, companions),\n",
    "        avatar = AiSelfAvatar(db, images, companions),\n        selfName = AiSelfName(db, companions),\n")

copyfile(here / "AiSelfName_03837.kt", ai / "AiSelfName.kt")
dest = root / "app/src/test/java/com/cleo/cleos/ai/SelfNameRulesTest.kt"
dest.parent.mkdir(parents=True, exist_ok=True)
copyfile(here / "SelfNameRulesTest_03837.kt", dest)
gradle = root / "app/build.gradle.kts"
replace_exact(gradle, 'versionName = "0.38.36"', 'versionName = "0.38.37"')
replace_exact(gradle, "versionCode = 62102", "versionCode = 62103")
print("0.38.37/62103: self-selected name, verified current user request, persistent Room identity, in-chat feedback")
