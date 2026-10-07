package com.cleo.cleos.data

import com.cleo.cleos.ai.ToolGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolSettingsTest {
    @Test
    fun choicesSurviveAndGroupsAddedLaterGetTheirDefault() {
        val chosen = setOf(ToolGroup.Todos, ToolGroup.Diary)
        assertEquals(chosen, decodeTools(encodeTools(chosen)))
        // Written before Secrets, Avatar and Messages existed: never chosen, so they take their default (on).
        assertEquals(
            chosen + ToolGroup.Secrets + ToolGroup.Avatar + ToolGroup.Messages + ToolGroup.Letters + ToolGroup.Memory + ToolGroup.Alarm + ToolGroup.Stickers + ToolGroup.Pat + ToolGroup.Apps,
            decodeTools("Todos:on,Diary:on,AiDiary:off,Weather:off"),
        )
    }

    @Test
    fun whatVersion030WroteStillReads() {
        // 0.3.0 listed only the groups that were on, and knew Todos, Diary and Weather.
        val newer = setOf(ToolGroup.AiDiary, ToolGroup.Secrets, ToolGroup.Avatar, ToolGroup.Messages, ToolGroup.Letters, ToolGroup.Memory, ToolGroup.Alarm, ToolGroup.Stickers, ToolGroup.Pat, ToolGroup.Apps)
        assertEquals(setOf(ToolGroup.Todos, ToolGroup.Weather) + newer, decodeTools("Todos,Weather"))
        // Everything switched off then stays off; the newer groups still start on.
        assertEquals(newer, decodeTools(""))
    }

    @Test
    fun unknownNamesAreSkipped() {
        assertEquals(AppSettings().tools - ToolGroup.Todos, decodeTools("Todos:off,Teleport:on"))
    }

    @Test
    fun newAppEntryDoesNotTurnBackOnMusicOrOtherDisabledGroups() {
        val old = "Music:off,Messages:off,Pat:off"
        val migrated = decodeTools(old)
        assertEquals(AppSettings().tools - setOf(ToolGroup.Music, ToolGroup.Messages, ToolGroup.Pat), migrated)
        assertEquals(migrated - ToolGroup.Apps, decodeTools(old + ",Apps:off"))
    }
}
