package com.cleo.cleos.data

/** Deleting a TA must never silently leave a historical group with fewer than two active TAs. */
internal object GroupDeletionPolicy {
    fun canDelete(memberCountsOfJoinedGroups: List<Int>): Boolean =
        memberCountsOfJoinedGroups.all { it > 2 }
}
