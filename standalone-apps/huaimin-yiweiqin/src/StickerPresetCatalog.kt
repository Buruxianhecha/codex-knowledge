package com.cleo.cleos.data

enum class StickerPresetShelf { Default, MengMeiZhi, Dog }

data class BundledStickerPreset(
    val assetFile: String,
    val name: String,
    val description: String,
    val shelf: StickerPresetShelf,
)

object StickerPresetCatalog {
    const val ASSET_DIR = "sticker_presets"
    const val MENG_PREFIX = "sticker-mengmeizhi-"
    const val DOG_PREFIX = "sticker-dog-"

    val imageBuiltIns = listOf(
        BundledStickerPreset("${MENG_PREFIX}01.webp", "萌妹纸·乖巧站好", "萌妹纸乖巧站好，安静可爱", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}02.webp", "萌妹纸·抱爱心", "抱着爱心表达喜欢和心动", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}03.webp", "萌妹纸·委屈巴巴", "眼泪汪汪，委屈可怜", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}04.webp", "萌妹纸·开心张手", "开心张开双手欢迎", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}05.webp", "萌妹纸·偷偷探头", "躲在旁边偷偷探头看", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}06.webp", "萌妹纸·睡觉觉", "趴着睡觉，困困的", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}07.webp", "萌妹纸·点赞", "竖起大拇指表示好和赞", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}08.webp", "萌妹纸·不可以", "双手交叉拒绝，不可以", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}09.webp", "萌妹纸·满头问号", "疑惑不解，满头问号", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}10.webp", "萌妹纸·大哭", "眼泪成河，伤心大哭", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}11.webp", "萌妹纸·生气", "脸红生气，有点恼火", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}12.webp", "萌妹纸·举牌牌", "抱着牌子可爱提示", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}13.webp", "萌妹纸·在吗", "挥手问在吗，打招呼", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}14.webp", "萌妹纸·吃面面", "开心吃面，吃饭中", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}15.webp", "萌妹纸·好累", "趴下休息，累趴了", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}16.webp", "萌妹纸·加油呀", "拿着啦啦球加油鼓励", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}17.webp", "萌妹纸·送你爱心", "送出大爱心表达喜欢", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}18.webp", "萌妹纸·拜拜", "转身挥手拜拜再见", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}19.webp", "萌妹纸·震惊", "瞪大眼睛非常震惊", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}20.webp", "萌妹纸·忙碌中", "电脑和书堆前忙碌工作", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}21.webp", "萌妹纸·抱抱", "抱着软枕想要抱抱", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}22.webp", "萌妹纸·求求你", "闪亮大眼睛拜托求求你", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}23.webp", "萌妹纸·哇哦", "兴奋惊呼哇哦", StickerPresetShelf.MengMeiZhi),
        BundledStickerPreset("${MENG_PREFIX}24.webp", "萌妹纸·OK", "比出OK手势表示没问题", StickerPresetShelf.MengMeiZhi),

        BundledStickerPreset("${DOG_PREFIX}01.webp", "小白狗·宝宝", "爱心围绕，撒娇叫宝宝", StickerPresetShelf.Dog),
        BundledStickerPreset("${DOG_PREFIX}02.webp", "小白狗·脸好烫", "害羞脸红，脸好烫", StickerPresetShelf.Dog),
        BundledStickerPreset("${DOG_PREFIX}03.webp", "小白狗·心动抱抱", "抱着软枕，满眼爱心想抱抱", StickerPresetShelf.Dog),
        BundledStickerPreset("${DOG_PREFIX}04.webp", "小白狗·萌萌看你", "睁大眼睛可爱地看着你", StickerPresetShelf.Dog),
        BundledStickerPreset("${DOG_PREFIX}05.webp", "小白狗·纯挑衅", "坏坏歪嘴，纯挑衅的表情", StickerPresetShelf.Dog),
        BundledStickerPreset("${DOG_PREFIX}06.webp", "小白狗·狗怒", "气鼓鼓炸毛生气", StickerPresetShelf.Dog),
    )

    fun shelfOf(file: String): StickerPresetShelf = when {
        file.startsWith(MENG_PREFIX) -> StickerPresetShelf.MengMeiZhi
        file.startsWith(DOG_PREFIX) -> StickerPresetShelf.Dog
        else -> StickerPresetShelf.Default
    }
}
