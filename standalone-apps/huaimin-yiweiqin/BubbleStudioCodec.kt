package com.cleo.cleos.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * First-party Huaimin bubble styles. Keep the user's existing glass bubbles untouched
 * until they explicitly apply a style in the lab.
 *
 * Stored as a single JSON DataStore preference; per-character overrides use stable TA ids,
 * not display names, so renaming someone never loses their theme.
 */
@Serializable
data class BubbleSkin(
    val startArgb: Int = 0xFF12769B.toInt(),
    val endArgb: Int = 0xFF22536A.toInt(),
    val gradient: Boolean = false,
    val opacity: Float = 0.84f,
    val blur: Float = 22f,
    val radius: Float = 22f,
    val paddingHorizontal: Float = 13f,
    val paddingVertical: Float = 9f,
    val rim: Float = 1.2f,
    val shadow: Float = 0.12f,
) {
    fun safe() = copy(
        opacity = opacity.safe(0.65f, 1f, 0.84f),
        blur = blur.safe(0f, 36f, 22f),
        radius = radius.safe(6f, 36f, 22f),
        paddingHorizontal = paddingHorizontal.safe(6f, 28f, 13f),
        paddingVertical = paddingVertical.safe(4f, 20f, 9f),
        rim = rim.safe(0f, 3f, 1.2f),
        shadow = shadow.safe(0f, 0.36f, 0.12f),
    )
}

private fun Float.safe(min: Float, max: Float, fallback: Float) =
    if (isNaN() || isInfinite()) fallback else coerceIn(min, max)

@Serializable
data class BubbleStudioConfig(
    val enabled: Boolean = false,
    val mine: BubbleSkin = BubblePresets.nightMine,
    val defaultTa: BubbleSkin = BubblePresets.nightTa,
    val perCompanion: Map<String, BubbleSkin> = emptyMap(),
    val saved: Map<String, BubbleSkin> = emptyMap(),
) {
    fun skinFor(mine: Boolean, companionId: Long?): BubbleSkin =
        (if (mine) this.mine else companionId?.let { perCompanion[it.toString()] } ?: defaultTa).safe()

    fun withSkin(roleKey: String, style: BubbleSkin): BubbleStudioConfig {
        val clean = style.safe()
        return when (roleKey) {
            "me" -> copy(enabled = true, mine = clean)
            "ta" -> copy(enabled = true, defaultTa = clean)
            else -> {
                val id = roleKey.removePrefix("ta:").toLongOrNull()
                if (id == null || id <= 0) this
                else copy(enabled = true, perCompanion = perCompanion + (id.toString() to clean))
            }
        }
    }

    fun savePreset(name: String, style: BubbleSkin): BubbleStudioConfig {
        val clean = name.trim().take(24)
        if (clean.isEmpty()) return this
        val next = saved.filterKeys { it != clean }.toMutableMap()
        if (next.size >= 24) next.remove(next.keys.first())
        next[clean] = style.safe()
        return copy(saved = next)
    }
}

object BubblePresets {
    val nightMine = BubbleSkin(startArgb = 0xFF126D94.toInt(), endArgb = 0xFF1E9ABD.toInt(), gradient = true, opacity = 0.88f)
    val nightTa = BubbleSkin(startArgb = 0xFF253F52.toInt(), endArgb = 0xFF334C5A.toInt(), gradient = true, opacity = 0.90f)
    val milk = BubbleSkin(startArgb = 0xFFF9F3E9.toInt(), endArgb = 0xFFE7DFD2.toInt(), gradient = true, opacity = 0.94f)
    val green = BubbleSkin(startArgb = 0xFF2A5545.toInt(), endArgb = 0xFF4C7761.toInt(), gradient = true, opacity = 0.89f)
    val mist = BubbleSkin(startArgb = 0xFFDCE4EB.toInt(), endArgb = 0xFFBFD3DF.toInt(), gradient = true, opacity = 0.91f)
    val peach = BubbleSkin(startArgb = 0xFFFFD8D2.toInt(), endArgb = 0xFFEAB7BF.toInt(), gradient = true, opacity = 0.93f)
    val paper = BubbleSkin(startArgb = 0xFFF6F4EF.toInt(), endArgb = 0xFFF6F4EF.toInt(), opacity = 0.96f, blur = 4f, radius = 16f, rim = 0.6f, shadow = 0.04f)
    val clear = BubbleSkin(startArgb = 0xFFA9CCD1.toInt(), endArgb = 0xFFA9CCD1.toInt(), opacity = 0.72f, blur = 32f, radius = 26f)
    val all: Map<String, BubbleSkin> = linkedMapOf(
        "夜海蓝光" to nightMine, "深海夜话" to nightTa, "暖雾奶白" to milk,
        "墨绿安静" to green, "雾蓝玻璃" to mist, "奶桃微光" to peach,
        "纸页留白" to paper, "清透玻璃" to clear,
    )
}

object BubbleStudioCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(raw: String?): BubbleStudioConfig = runCatching {
        if (raw.isNullOrBlank()) BubbleStudioConfig()
        else json.decodeFromString<BubbleStudioConfig>(raw)
    }.getOrDefault(BubbleStudioConfig()).let { c ->
        c.copy(
            mine = c.mine.safe(), defaultTa = c.defaultTa.safe(),
            perCompanion = c.perCompanion.filterKeys { it.toLongOrNull()?.let { n -> n > 0 } == true }
                .entries.take(80).associate { it.key to it.value.safe() },
            saved = c.saved.entries.take(24).associate { it.key.take(24) to it.value.safe() },
        )
    }

    fun encode(value: BubbleStudioConfig): String =
        json.encodeToString(decode(json.encodeToString(value)))
}
