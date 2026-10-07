package com.cleo.cleos.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

data class AvatarPreset(val number: Int, val fileName: String) {
    val assetPath: String get() = "avatar_presets/$fileName"
    val assetUri: String get() = "file:///android_asset/$assetPath"
    private val storedPrefix: String get() = "avatar-preset-${fileName.removeSuffix(".png")}-"
    fun isSelected(storedFile: String?): Boolean = storedFile?.startsWith(storedPrefix) == true
    internal fun newStoredName(): String = "$storedPrefix${UUID.randomUUID()}.png"
}

/** Original pictures bundled with the app; each choice owns its own backed-up image file. */
object AvatarPresets {
    val all = listOf(
        AvatarPreset(1, "avatar_01.png"),
        AvatarPreset(2, "avatar_02.png"),
        AvatarPreset(3, "avatar_03.png"),
        AvatarPreset(4, "avatar_04.png"),
        AvatarPreset(5, "avatar_05.png"),
        AvatarPreset(6, "avatar_06.png"),
        AvatarPreset(7, "avatar_07.png"),
        AvatarPreset(8, "avatar_08.png"),
        AvatarPreset(9, "avatar_09.png"),
        AvatarPreset(10, "avatar_10.png"),
        AvatarPreset(11, "avatar_11.png"),
        AvatarPreset(12, "avatar_12.png"),
        AvatarPreset(13, "avatar_13.png"),
        AvatarPreset(14, "avatar_14.png"),
    )

    /** Called on the IO dispatcher. No TA shares a file that replacing another avatar deletes. */
    fun copyToStore(preset: AvatarPreset, directory: File, open: () -> InputStream): String {
        require(preset in all)
        val name = preset.newStoredName()
        val temporary = File(directory, "$name.tmp")
        val destination = File(directory, name)
        try {
            open().use { source -> temporary.outputStream().use { source.copyTo(it) } }
            if (!temporary.renameTo(destination)) throw IOException("Could not save avatar")
            return name
        } finally {
            temporary.delete()
        }
    }
}
