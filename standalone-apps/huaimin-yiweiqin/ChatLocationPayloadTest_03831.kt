
package com.cleo.cleos.ui.chat
import org.junit.Assert.*
import org.junit.Test

class ChatLocationPayloadTest {
    @Test fun roundTrip() {
        val p=ChatLocationPoint(31.23,121.53,"金葵新城","上海市浦东新区")
        assertEquals(p,ChatLocationPayload.decode(ChatLocationPayload.encode(p)))
    }
    @Test fun rejectsBadPosition() {
        try {
            ChatLocationPayload.encode(ChatLocationPoint(200.0,0.0,"x",""))
            fail("invalid coordinates were accepted")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun unrelatedMessagesStayUnchanged() {
        assertNull(ChatLocationPayload.decode("今天去哪里？"))
        assertNull(ChatLocationPayload.decode("HUAIMIN_LOCATION_V1:broken"))
        assertNull(ChatLocationPayload.decode("HUAIMIN_LOCATION_V1:{\"lat\":91,\"lon\":20}"))
    }
    @Test fun longNamesAreBounded() {
        val p=ChatLocationPoint(0.0,0.0,"地".repeat(500),"址".repeat(900))
        val decoded=ChatLocationPayload.decode(ChatLocationPayload.encode(p))!!
        assertEquals(120,decoded.name.length)
        assertEquals(320,decoded.address.length)
    }
    @Test fun jsonWithoutTagIsNotAccepted() {
        val p=ChatLocationPoint(45.0,15.0,"公园","")
        assertNull(ChatLocationPayload.decode(ChatLocationPayload.encode(p).removePrefix("HUAIMIN_LOCATION_V1:")))
    }
}
