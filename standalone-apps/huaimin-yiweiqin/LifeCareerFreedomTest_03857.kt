package com.cleo.cleos.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LifeCareerFreedomTest {
    private val json=Json { ignoreUnknownKeys=true }
    @Test fun arbitraryProfessionNameAndSalarySurviveBackup() {
        val p=LifeProfile(companionId=42,professionId="custom",
            customProfessionTitle="宠物行为顾问",customMonthlyRmb=7200)
        val read=json.decodeFromString<LifeProfile>(json.encodeToString(p))
        assertEquals("宠物行为顾问", LifeProfessions.title(read))
        assertEquals(7200,LifeRules.effectiveMonthlyRmb(read))
    }
    @Test fun olderProfilesKeepPresetIdentity() {
        val p=LifeProfile(companionId=7,professionId="engineer")
        assertEquals("工程师",LifeProfessions.title(p))
        assertEquals(9000,LifeRules.effectiveMonthlyRmb(p))
    }
    @Test fun userCanRenamePresetWithoutBreakingExperienceBucket() {
        val p=LifeProfile(companionId=9,professionId="technician",customProfessionTitle="地铁检修员",
            customMonthlyRmb=8700)
        assertEquals("地铁检修员",LifeProfessions.title(p))
        assertEquals(8700,LifeRules.effectiveMonthlyRmb(p))
        assertEquals("technician",p.professionId)
    }
    @Test fun oldSerializedProfilesHaveNoNewField() {
        val p=LifeProfile(companionId=5)
        val legacy=json.encodeToString(p).replace(",\"customProfessionTitle\":\"\"","")
        assertEquals("工程师",LifeProfessions.title(json.decodeFromString(legacy)))
    }
}