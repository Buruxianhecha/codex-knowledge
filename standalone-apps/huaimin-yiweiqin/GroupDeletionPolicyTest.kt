package com.cleo.cleos.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupDeletionPolicyTest {
    @Test fun aTaOutsideAllGroupsCanBeDeleted() {
        assertTrue(GroupDeletionPolicy.canDelete(emptyList()))
    }

    @Test fun cannotBreakTwoMemberOrLegacyOneMemberGroup() {
        assertFalse(GroupDeletionPolicy.canDelete(listOf(2)))
        assertFalse(GroupDeletionPolicy.canDelete(listOf(1)))
        assertFalse(GroupDeletionPolicy.canDelete(listOf(4, 2)))
    }

    @Test fun deletionAllowedWhenEveryGroupRetainsAtLeastTwoMembers() {
        assertTrue(GroupDeletionPolicy.canDelete(listOf(3)))
        assertTrue(GroupDeletionPolicy.canDelete(listOf(3, 5)))
    }
}
