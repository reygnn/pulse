package com.github.reygnn.pulse.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class HrZoneTest {

    private val maxHr = 200

    @Test
    fun `hr of zero returns REST regardless of maxHr`() {
        assertEquals(HrZone.REST, HrZone.fromHeartRate(hr = 0, maxHr = 200))
        assertEquals(HrZone.REST, HrZone.fromHeartRate(hr = 0, maxHr = 0))
    }

    @Test
    fun `maxHr of zero returns REST as a safe fallback`() {
        // estimatedMaxHr is computed from age — a 0-or-negative max would
        // otherwise cause a divide-by-zero in fromHeartRate's percentage calc.
        assertEquals(HrZone.REST, HrZone.fromHeartRate(hr = 120, maxHr = 0))
        assertEquals(HrZone.REST, HrZone.fromHeartRate(hr = 120, maxHr = -10))
    }

    @Test
    fun `hr below 50 percent of max is REST`() {
        // 99 / 200 = 0.495
        assertEquals(HrZone.REST, HrZone.fromHeartRate(hr = 99, maxHr = maxHr))
    }

    @Test
    fun `boundary at 50 percent is WARMUP not REST`() {
        // 100 / 200 = 0.50 — entries.lastOrNull { pct >= it.minPct } picks WARMUP
        assertEquals(HrZone.WARMUP, HrZone.fromHeartRate(hr = 100, maxHr = maxHr))
    }

    @Test
    fun `boundary at 60 percent is FAT_BURN`() {
        assertEquals(HrZone.FAT_BURN, HrZone.fromHeartRate(hr = 120, maxHr = maxHr))
    }

    @Test
    fun `boundary at 70 percent is CARDIO`() {
        assertEquals(HrZone.CARDIO, HrZone.fromHeartRate(hr = 140, maxHr = maxHr))
    }

    @Test
    fun `boundary at 80 percent is PEAK`() {
        assertEquals(HrZone.PEAK, HrZone.fromHeartRate(hr = 160, maxHr = maxHr))
    }

    @Test
    fun `boundary at 90 percent is MAX`() {
        assertEquals(HrZone.MAX, HrZone.fromHeartRate(hr = 180, maxHr = maxHr))
    }

    @Test
    fun `hr above maxHr stays in MAX zone`() {
        // The zone classification clamps semantically: anything >= 0.9 is MAX,
        // including freak readings above the configured maximum.
        assertEquals(HrZone.MAX, HrZone.fromHeartRate(hr = 220, maxHr = maxHr))
    }

    @Test
    fun `mid-zone values map correctly across all six zones`() {
        // Sanity sweep: pick a heart rate mid-way through each zone window
        // and check it lands where the lookup table says it should.
        assertEquals(HrZone.REST,     HrZone.fromHeartRate(hr =  50, maxHr = maxHr)) // 25 %
        assertEquals(HrZone.WARMUP,   HrZone.fromHeartRate(hr = 110, maxHr = maxHr)) // 55 %
        assertEquals(HrZone.FAT_BURN, HrZone.fromHeartRate(hr = 130, maxHr = maxHr)) // 65 %
        assertEquals(HrZone.CARDIO,   HrZone.fromHeartRate(hr = 150, maxHr = maxHr)) // 75 %
        assertEquals(HrZone.PEAK,     HrZone.fromHeartRate(hr = 170, maxHr = maxHr)) // 85 %
        assertEquals(HrZone.MAX,      HrZone.fromHeartRate(hr = 195, maxHr = maxHr)) // 97.5 %
    }
}
