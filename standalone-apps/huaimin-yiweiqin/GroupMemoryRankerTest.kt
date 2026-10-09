package com.cleo.cleos.ai

import com.cleo.cleos.data.db.SharedMessageRow
import org.junit.Assert.*
import org.junit.Test

class GroupMemoryRankerTest {
    private fun row(id:Long, text:String)=SharedMessageRow(id,11,"旧群",1,1,"assistant",text,id)
    @Test fun chineseOverlappingTopicRetrievesCorrectActualMessage() {
        val rows=listOf(row(1,"猫咪昨天去医院看病"),row(2,"夏日暴雨造成小区停水"))
        assertEquals(1L,GroupMemoryRanker.rank("你记得昨天猫咪去医院看病吗",rows).first().id)
    }
    @Test fun deduplicatesAndRejectsUnrelatedRecords() {
        val x=row(4,"原来的那个电影")
        assertEquals(1,GroupMemoryRanker.rank("说说那个电影",listOf(x,x,row(8,"电梯维修"))).count { it.id==4L })
        assertTrue(GroupMemoryRanker.rank("量子力学",listOf(row(5,"今天很好"))).isEmpty())
    }
    @Test fun respectsMaxAndDoesNotFabricate() {
        val r=(1L..80L).map { row(it,"台风消息记录$it") }
        assertEquals(12,GroupMemoryRanker.rank("台风消息",r).size)
        assertTrue(GroupMemoryRanker.rank("...",r).isEmpty())
    }
}
