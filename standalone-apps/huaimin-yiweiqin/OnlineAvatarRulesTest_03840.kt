package com.cleo.cleos.ai

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class OnlineAvatarRulesTest {
    @Test fun acceptsRealImageLink() {
        assertTrue(OnlineAvatarRules.safeHttpsUrl("https://upload.wikimedia.org/wikipedia/commons/test.jpg"))
    }
    @Test fun refusesHttpAndFile() {
        assertFalse(OnlineAvatarRules.safeHttpsUrl("http://example.com/a.jpg"))
        assertFalse(OnlineAvatarRules.safeHttpsUrl("file:///data/local.jpg"))
    }
    @Test fun refusesLoopbackAndPrivateHostnames() {
        assertFalse(OnlineAvatarRules.safeHttpsUrl("https://localhost/pic.jpg"))
        assertFalse(OnlineAvatarRules.safeHttpsUrl("https://internal.local/photo.png"))
    }
    @Test fun refusesIpLiterals() {
        assertFalse(OnlineAvatarRules.safeHttpsUrl("https://192.168.1.1/test.jpg"))
        assertFalse(OnlineAvatarRules.safeHttpsUrl("https://127.0.0.1/test.jpg"))
    }
    @Test fun refusesEmbeddedCredentials() {
        assertFalse(OnlineAvatarRules.safeHttpsUrl("https://user:password@example.com/avatar.png"))
    }
    @Test fun refusesArbitraryPorts() {
        assertFalse(OnlineAvatarRules.safeHttpsUrl("https://example.com:8080/a.png"))
    }
    @Test fun blocksLocalIpV4() {
        assertFalse(OnlineAvatarRules.publicIp(InetAddress.getByName("10.0.0.1")))
        assertFalse(OnlineAvatarRules.publicIp(InetAddress.getByName("192.168.0.10")))
    }
    @Test fun blocksSharedAndBenchmarkIpv4Addresses() {
        assertFalse(OnlineAvatarRules.publicIp(InetAddress.getByName("100.100.100.100")))
        assertFalse(OnlineAvatarRules.publicIp(InetAddress.getByName("198.18.0.1")))
    }
    @Test fun blocksIpv6UniqueLocal() {
        assertFalse(OnlineAvatarRules.publicIp(InetAddress.getByName("fd00::1")))
    }
    @Test fun permitsPublicIp() {
        assertTrue(OnlineAvatarRules.publicIp(InetAddress.getByName("8.8.8.8")))
    }
    @Test fun onlyAllowsBitmapMimeTypes() {
        assertTrue(OnlineAvatarRules.allowedMime("image/jpeg"))
        assertTrue(OnlineAvatarRules.allowedMime("image/png; charset=binary"))
        assertTrue(OnlineAvatarRules.allowedMime("image/webp"))
        assertFalse(OnlineAvatarRules.allowedMime("image/svg+xml"))
        assertFalse(OnlineAvatarRules.allowedMime("text/html"))
    }
}
