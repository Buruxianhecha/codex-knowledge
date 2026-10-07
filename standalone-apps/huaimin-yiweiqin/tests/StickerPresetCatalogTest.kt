package com.cleo.cleos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerPresetCatalogTest {
    @Test fun hasExpectedPackSizes() {
        assertEquals(30, StickerPresetCatalog.imageBuiltIns.size)
        assertEquals(24, StickerPresetCatalog.imageBuiltIns.count { it.shelf == StickerPresetShelf.MengMeiZhi })
        assertEquals(6, StickerPresetCatalog.imageBuiltIns.count { it.shelf == StickerPresetShelf.Dog })
    }

    @Test fun namesAndFilesAreUnique() {
        val all = StickerPresetCatalog.imageBuiltIns
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertEquals(all.size, all.map { it.assetFile }.toSet().size)
    }

    @Test fun shelfRoutingUsesStablePrefixes() {
        assertEquals(StickerPresetShelf.MengMeiZhi, StickerPresetCatalog.shelfOf("sticker-mengmeizhi-12.webp"))
        assertEquals(StickerPresetShelf.Dog, StickerPresetCatalog.shelfOf("sticker-dog-05.webp"))
        assertEquals(StickerPresetShelf.Default, StickerPresetCatalog.shelfOf("sticker-preset-1.png"))
    }

    @Test fun allBundledImagesUseSafeWebpNames() {
        assertTrue(StickerPresetCatalog.imageBuiltIns.all {
            it.assetFile.matches(Regex("""sticker-(mengmeizhi|dog)-\d{2}\.webp"""))
        })
    }
}
