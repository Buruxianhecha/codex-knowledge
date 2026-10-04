package com.cleo.cleos.ui.settings

import com.cleo.cleos.data.db.CompanionEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ModelSettingsWriterTest {
    private fun companion(id: Long = 1L) = CompanionEntity(
        id = id, apiBaseUrl = "https://old.example/v1", apiModel = "old", createdAt = 1L,
    )

    private fun selection(id: Long = 1L) = ModelSelection(
        id, "https://www.sui-xiang.net/v1", "gpt-6-sol", true,
        "https://voice.example/v1", "voice-model",
    )

    @Test
    fun aPendingAutomaticWriteCannotOverwriteTheExplicitSelection() = runBlocking {
        val old = companion().copy(name = "TA", persona = "keep this persona")
        var stored = old
        val writer = ModelSettingsWriter({ _, _ -> }, { _, change -> stored = change(stored) }, { stored })
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val automatic = launch(start = CoroutineStart.UNDISPATCHED) {
            writer.serially { entered.complete(Unit); release.await(); stored = old }
        }
        entered.await()
        val save = launch(start = CoroutineStart.UNDISPATCHED) { writer.commit(selection(), emptyList()) }
        assertFalse(save.isCompleted)
        release.complete(Unit)
        automatic.join()
        save.join()
        assertTrue(selection().matches(stored))
        assertEquals("keep this persona", stored.persona)
        assertEquals("TA", stored.name)
    }

    @Test
    fun navigationWaitsForKeyAndDatabaseWrites() = runBlocking {
        var stored = companion()
        var leftPage = false
        val keyDone = CompletableDeferred<Unit>()
        val databaseDone = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val writer = ModelSettingsWriter(
            { address, key ->
                assertEquals("https://www.sui-xiang.net/v1", address)
                assertEquals("test-key", key)
                keyDone.await(); events.add("key")
            },
            { _, change -> databaseDone.await(); stored = change(stored); events.add("database") },
            { events.add("readback"); stored },
        )
        val save = launch(start = CoroutineStart.UNDISPATCHED) {
            writer.commit(selection(), listOf(selection().baseUrl to "test-key"))
            leftPage = true
        }
        assertFalse(leftPage)
        assertTrue(events.isEmpty())
        keyDone.complete(Unit)
        kotlinx.coroutines.yield()
        assertFalse(leftPage)
        databaseDone.complete(Unit)
        save.join()
        assertTrue(leftPage)
        assertEquals(listOf("key", "database", "readback"), events)
    }

    @Test
    fun switchingTheEditableTaCannotMoveAnAlreadyCapturedSelection() = runBlocking {
        val records = mutableMapOf(1L to companion(1), 2L to companion(2))
        val release = CompletableDeferred<Unit>()
        val writer = ModelSettingsWriter(
            { _, _ -> release.await() },
            { id, change -> records[id] = change(records.getValue(id)) },
            { records[it] },
        )
        var editedId = 1L
        val captured = selection(editedId)
        val save = launch(start = CoroutineStart.UNDISPATCHED) {
            writer.commit(captured, listOf(captured.baseUrl to "test-key"))
        }
        editedId = 2L
        release.complete(Unit)
        save.join()
        assertTrue(captured.matches(records.getValue(1L)))
        assertNotEquals("gpt-6-sol", records.getValue(editedId).apiModel)
    }

    @Test
    fun aKeyFailureDoesNotReportSuccessOrChangeTheModel() = runBlocking {
        var wroteModel = false
        var leftPage = false
        val writer = ModelSettingsWriter(
            { _, _ -> error("key write failed") },
            { _, _ -> wroteModel = true },
            { companion() },
        )
        try {
            writer.commit(selection(), listOf(selection().baseUrl to "test-key"))
            leftPage = true
            fail("The key failure must reach the save caller")
        } catch (expected: IllegalStateException) {
            assertEquals("key write failed", expected.message)
        }
        assertFalse(wroteModel)
        assertFalse(leftPage)
    }

    @Test
    fun emptyModelAndMissingRecordCannotBeReportedAsSaved() = runBlocking {
        var wroteModel = false
        val writer = ModelSettingsWriter({ _, _ -> }, { _, _ -> wroteModel = true }, { null })
        try {
            writer.commit(selection().copy(model = ""), emptyList())
            fail("An empty model must stay on the settings page")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("选择模型"))
        }
        assertFalse(wroteModel)
        try {
            writer.commit(selection(), emptyList())
            fail("A deleted TA must not be reported as saved")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("不存在"))
        }
    }

    @Test
    fun staleReadbackCannotCloseThePage() = runBlocking {
        var leftPage = false
        val writer = ModelSettingsWriter({ _, _ -> }, { _, _ -> }, { companion() })
        try {
            writer.commit(selection(), emptyList())
            leftPage = true
            fail("Readback must confirm the selected model")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("重试"))
        }
        assertFalse(leftPage)
    }
}
