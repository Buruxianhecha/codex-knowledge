package com.cleo.cleos.ui.chat

import org.junit.Assert.*
import org.junit.Test

class LocationQualityTest {
    @Test fun freshAccurateNetworkLocationCanBeShownImmediately() {
        assertTrue(LocationQuality.immediatelyUsable(50_000L, 120f))
    }
    @Test fun indoorGpsLocationMayUseRecentCache() {
        assertTrue(LocationQuality.usableCache(800_000L, 300f))
    }
    @Test fun oldCacheNeverMasqueradesAsCurrent() {
        assertFalse(LocationQuality.usableCache(1_800_001L, 100f))
    }
    @Test fun futureDatedCacheNeverMasqueradesAsCurrent() {
        assertFalse(LocationQuality.usableCache(-10L, 100f))
    }
    @Test fun veryInaccurateLocationRejected() {
        assertFalse(LocationQuality.usableCache(30_000L, 9500f))
    }
    @Test fun slightlyOlderFixIsNotInstantButMayBeFallback() {
        assertFalse(LocationQuality.immediatelyUsable(300_000L, 100f))
        assertTrue(LocationQuality.usableCache(300_000L, 100f))
    }
    @Test fun inaccurateFixDoesNotBypassLiveNetworkAndGpsRace() {
        assertFalse(LocationQuality.immediatelyUsable(5_000L, 1200f))
    }
    @Test fun invalidGpsCoordinatesCannotBeSent() {
        try {
            ChatLocationPayload.encode(ChatLocationPoint(100.0, 400.0, "bad", "bad"))
            fail("Invalid coordinates accepted")
        } catch (_: IllegalArgumentException) {}
    }
}
