package com.cleo.cleos.data

import com.cleo.cleos.data.db.TodoEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TodoPhotoFieldTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test fun oldBackupWithoutPhotosStillDecodes() {
        val old = """{"id":3,"title":"整理房间","note":"今天晚上","done":false,"dueDay":null,"createdAt":111,"doneAt":null}"""
        val result = json.decodeFromString<TodoEntity>(old)
        assertNull(result.imageFile)
        assertEquals("整理房间", result.title)
        assertFalse(result.done)
    }

    @Test fun imageFilenameSurvivesBackupJsonRoundTrip() {
        val initial = TodoEntity(id=4,title="整理房间",
            imageFile="todo_12345678-1234-1234-1234-123456789abc.jpg",
            createdAt=112)
        val after = json.decodeFromString<TodoEntity>(json.encodeToString(initial))
        assertEquals(initial.imageFile, after.imageFile)
        assertEquals(initial.title, after.title)
    }

    @Test fun imageCanCoexistWithCompletedAndDatedTodo() {
        val item=TodoEntity(id=9,title="买牛奶",createdAt=120,imageFile="todo_12345678-1234-1234-1234-123456789abc.png",
            done=true,doneAt=130,dueDay=20000)
        val actual=json.decodeFromString<TodoEntity>(json.encodeToString(item))
        assertTrue(actual.done)
        assertEquals(130L, actual.doneAt)
        assertEquals(20000L, actual.dueDay)
        assertEquals(item.imageFile,actual.imageFile)
    }
}
