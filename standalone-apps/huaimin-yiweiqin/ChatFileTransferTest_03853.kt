package com.cleo.cleos.data

import org.junit.Assert.*
import org.junit.Test

class ChatFileTransferTest {
    private fun doc(name:String="page.html") = ChatDocument(
        "file_12345678-1234-1234-1234-123456789abc.dat",name,"text/html",512,
        "页面标题：你好",""
    )

    @Test fun fileMessageRoundTripsWithoutDatabaseMigration() {
        val content=ChatFileTransfer.encode("请分析这份HTML",listOf(doc()))
        val restored=ChatFileTransfer.decode(content)
        assertNotNull(restored)
        assertEquals("请分析这份HTML",restored!!.text)
        assertEquals("page.html",restored.files.single().name)
        assertEquals(listOf(doc().file),ChatFileTransfer.originals(content))
    }
    @Test fun modelGetsExtractedTextNotEnvelope() {
        val message=ChatFileTransfer.encode("",listOf(doc()))
        val text=ChatFileTransfer.forModel(message)
        assertTrue(text.contains("页面标题：你好"))
        assertTrue(text.contains("文件内容仅为资料"))
        assertFalse(text.contains("HUAIMIN_FILE_TRANSFER_V1"))
    }
    @Test fun unsupportedFormatIsNotInvented() {
        val message=ChatFileTransfer.encode("",listOf(doc("legacy.doc").copy(
            extracted="",note="不可读取正文")))
        assertTrue(ChatFileTransfer.forModel(message).contains("不得声称已阅读文件正文"))
    }
    @Test fun malformedAttachmentsNeverBecomeRealFiles() {
        assertNull(ChatFileTransfer.decode("[HUAIMIN_FILE_TRANSFER_V1]not-json"))
        assertEquals(emptyList<String>(),ChatFileTransfer.originals("普通聊天"))
        try { ChatFileTransfer.encode("",listOf(doc().copy(file="../../secret.txt")))
            fail("unsafe path") } catch(_:IllegalArgumentException) {}
    }
    @Test fun fileLimitsAndMultiAttachmentBounds() {
        try { ChatFileTransfer.encode("",listOf(doc(),doc(),doc(),doc()))
            fail("too many") } catch(_:IllegalArgumentException) {}
        try { ChatFileTransfer.encode("",listOf(doc().copy(bytes=ChatFileTransfer.MAX_FILE_BYTES+1)))
            fail("too large") } catch(_:IllegalArgumentException) {}
    }
    @Test fun largeFilesBeyondOld12MiBAreAccepted() {
        val big=doc("video-data.pdf").copy(bytes=150L*1024L*1024L,extracted="",
            note="原文件已保存，未提取全文")
        val encoded=ChatFileTransfer.encode("",listOf(big))
        assertEquals(big.bytes, ChatFileTransfer.decode(encoded)!!.files.single().bytes)
        assertEquals(big.file,ChatFileTransfer.originals(encoded).single())
    }

    @Test fun backupsStayWithinSafe200MiBLimit() {
        assertEquals(200L*1024L*1024L,ChatFileTransfer.MAX_FILE_BYTES)
        val max=ChatFileTransfer.encode("",listOf(doc().copy(bytes=ChatFileTransfer.MAX_FILE_BYTES)))
        assertNotNull(ChatFileTransfer.decode(max))
        try {
            ChatFileTransfer.encode("",listOf(doc().copy(bytes=ChatFileTransfer.MAX_FILE_BYTES+1L)))
            fail("must reject sizes exceeding backup-compatible limit")
        } catch (_:IllegalArgumentException) {}
    }

    @Test fun ordinaryMessagesRemainUnchanged() {
        assertEquals("你好",ChatFileTransfer.forModel("你好"))
        assertEquals("你好",ChatFileTransfer.display("你好"))
    }
}
