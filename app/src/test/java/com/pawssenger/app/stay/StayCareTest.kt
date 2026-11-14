package com.pawssenger.app.stay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StayCareTest {
    @Test
    fun batteryStageBoundaries() {
        assertEquals(BatteryStage.NORMAL, StayCare.stageOf(null))
        assertEquals(BatteryStage.NORMAL, StayCare.stageOf(100))
        assertEquals(BatteryStage.NORMAL, StayCare.stageOf(21))
        assertEquals(BatteryStage.SAVE, StayCare.stageOf(20))
        assertEquals(BatteryStage.SAVE, StayCare.stageOf(11))
        assertEquals(BatteryStage.CRITICAL, StayCare.stageOf(10))
        assertEquals(BatteryStage.CRITICAL, StayCare.stageOf(0))
    }

    @Test
    fun contentIsReducedAsBatteryDrops() {
        assertEquals(StayContent.VIDEO, StayCare.contentFor(StayContent.VIDEO, BatteryStage.NORMAL))
        assertEquals(StayContent.SOUND, StayCare.contentFor(StayContent.VIDEO, BatteryStage.SAVE))
        assertEquals(StayContent.SOUND, StayCare.contentFor(StayContent.SOUND, BatteryStage.SAVE))
        assertEquals(StayContent.NONE, StayCare.contentFor(StayContent.NONE, BatteryStage.SAVE))
        StayContent.entries.forEach { assertEquals(StayContent.NONE, StayCare.contentFor(it, BatteryStage.CRITICAL)) }
    }

    @Test
    fun targetTempSnapsToHalfDegreesWithinRange() {
        assertEquals(22.5f, StayCare.clampTemp(22.4f))
        assertEquals(15f, StayCare.clampTemp(3f))
        assertEquals(30f, StayCare.clampTemp(31f))
    }

    @Test
    fun cannotStartWhenBatteryIsCritical() {
        assertTrue(StayUiState(batteryPct = 11).canStart)
        assertTrue(StayUiState(batteryPct = null).canStart)
        assertFalse(StayUiState(batteryPct = 10).canStart)
        assertEquals(StayContent.SOUND, StayUiState(batteryPct = 15, content = StayContent.VIDEO).playing)
    }
}
