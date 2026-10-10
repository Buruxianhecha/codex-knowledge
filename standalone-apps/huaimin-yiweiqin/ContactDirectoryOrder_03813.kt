package com.cleo.cleos.ui

/** Stable user-defined AI contact order.
 * Stored separately from CompanionEntity so older Room databases upgrade unchanged.
 * IDs not in a saved list (newly created contacts) are appended.
 */
internal object ContactDirectoryOrder {
    fun decode(raw: String?): List<Long> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull()?.takeIf { n -> n > 0L } }.distinct()

    fun encode(ids: List<Long>): String =
        ids.filter { it > 0L }.distinct().joinToString(",")

    fun apply(ids: List<Long>, desired: List<Long>): List<Long> {
        val valid = ids.toSet()
        val kept = desired.distinct().filter { it in valid }
        return kept + ids.filterNot { it in kept }
    }

    /** Steps are relative to the full directory, even when the list is filtered. */
    fun move(ids: List<Long>, who: Long, offset: Int): List<Long> {
        val from = ids.indexOf(who)
        if (from < 0) return ids
        val to = (from + offset).coerceIn(0, ids.lastIndex)
        if (to == from) return ids
        return ids.toMutableList().also {
            val value = it.removeAt(from)
            it.add(to, value)
        }
    }

    fun top(ids: List<Long>, who: Long): List<Long> =
        if (who !in ids) ids else listOf(who) + ids.filterNot { it == who }

    fun afterDelete(ids: List<Long>, who: Long): List<Long> = ids.filterNot { it == who }
}
