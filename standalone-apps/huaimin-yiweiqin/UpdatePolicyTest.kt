package com.cleo.cleos.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun newerCodesOnly() {
        assertTrue(UpdatePolicy.isNewer(62055, 62056))
        assertFalse(UpdatePolicy.isNewer(62055, 62055))
        assertFalse(UpdatePolicy.isNewer(62055, 62054))
    }
    @Test fun secureHttpsRequired() {
        assertTrue(UpdatePolicy.secureHttps("https://huaimin-download.vercel.app/latest.json"))
        assertFalse(UpdatePolicy.secureHttps("http://huaimin-download.vercel.app/latest.json"))
    }
    @Test fun disallowFileAndJavascriptUrls() {
        assertFalse(UpdatePolicy.secureHttps("file:///sdcard/app.apk"))
        assertFalse(UpdatePolicy.secureHttps("javascript:alert(1)"))
    }
    @Test fun disallowCredentialsAndMissingHost() {
        assertFalse(UpdatePolicy.secureHttps("https://user:password@example.com/apk"))
        assertFalse(UpdatePolicy.secureHttps("https://"))
    }
    @Test fun customHttpsSourcesAllowed() {
        assertTrue(UpdatePolicy.secureHttps("https://example.pages.dev/latest.json"))
    }
    @Test fun apkSizeHardLimitIsPresent() {
        assertEquals(300L * 1024 * 1024, UpdatePolicy.MAX_APK_BYTES)
    }
    @Test fun feedAndSiteAreNotLocalhost() {
        assertTrue(UpdatePolicy.FEED_URL.startsWith("https://"))
        assertTrue(UpdatePolicy.SITE_URL.startsWith("https://"))
        assertFalse(UpdatePolicy.FEED_URL.contains("localhost"))
    }
    @Test fun updateIdentityUsesMonotonicVersionCodeNotVersionText() {
        assertTrue(UpdatePolicy.isNewer(62054, 62055))
        assertFalse(UpdatePolicy.isNewer(62055, 62054))
    }
}
