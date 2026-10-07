"""Replace only the built-in Mossland voice buttons; retain selection and persistence."""
from pathlib import Path

def apply_voice_presets(root: Path) -> None:
    path = root / "app/src/main/java/com/cleo/cleos/ai/Speech.kt"
    source = path.read_text(encoding="utf-8")
    old = '''    /** Mossland's public voices (its docs, voices list); the library has more, by id. */
    val mosslandVoices = listOf(
        VoiceOption("806c9695-6160-404e-8722-4f788d935af3", "轻快灵动女声"),
        VoiceOption("fe85a513-9bf3-4ef7-aa0b-8b2d11e4db93", "少年感人声（男）"),
        VoiceOption("19411508-8731-4b68-901d-7e4b8a98e23f", "忧伤的秋"),
        VoiceOption("faf7f550-0627-4fc6-8db0-d3bfdad49358", "经验女教师"),
        VoiceOption("c6c0a40a-ea82-4468-9a21-333d3c4a76f6", "曼波有口音版"),
        VoiceOption("26838557-6890-4505-bc7c-e8198443a141", "东北虎哥"),
        VoiceOption("f80b6698-0066-430b-88a0-f0fb8796db34", "明太祖"),
        VoiceOption("7662a8a1-700c-466a-b66b-57ece9e2e231", "李白"),
        VoiceOption("0804710c-8e5e-4b67-acda-5785ef13c309", "历史解说男声"),
        VoiceOption("2fdf194e-c16e-4587-9027-0d3464e09b4e", "诗词朗读"),
        VoiceOption("133bd03b-d717-4a55-8974-7ffc9afc1b51", "故宫纪录片"),
        VoiceOption("944eb93b-3820-49f3-b2c0-4e37a31d1161", "三农农业旁白"),
        VoiceOption("9d1e88e9-3b9c-4992-a414-7a1cb3ff7ab5", "优雅英国女士"),
        VoiceOption("f9a1416b-d006-4b77-9581-8f0e8ec1e401", "旁白 Jake"),
        VoiceOption("ddc6e38b-6f55-4415-b21b-a88cad2cc1d9", "VOX AKUMA"),
    )'''
    new = '''    /** The custom Mossland preset selected by the user. */
    val mosslandVoices = listOf(
        VoiceOption("5ee59da9-cb84-437a-8909-8ec1cfceb425", "青年音"),
        VoiceOption("19411508-8731-4b68-901d-7e4b8a98e23f", "少女音"),
    )'''
    if source.count(old) != 1:
        raise SystemExit("Speech.kt: expected exactly one unchanged Mossland preset list")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")
