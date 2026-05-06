package com.github.reygnn.pulse.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionTest {

    private val maxHr = 200

    // --- Aggregate stats with empty / single / many samples ---

    @Test
    fun `empty session reports zero stats and zero duration`() {
        val s = WorkoutSession(maxHr = maxHr)
        assertEquals(0L, s.durationMs)
        assertEquals(0, s.avgHr)
        assertEquals(0, s.minHr)
        assertEquals(0, s.peakHr)
        assertEquals(0, s.caloriesEstimate)
        assertTrue(s.zoneDistribution().isEmpty())
    }

    @Test
    fun `single sample has min and peak equal but avg of single value`() {
        val s = WorkoutSession(
            samples = listOf(HrSample(0L, 130)),
            maxHr = maxHr,
        )
        assertEquals(130, s.minHr)
        assertEquals(130, s.peakHr)
        assertEquals(130, s.avgHr)
        // duration is .last().timestampMs
        assertEquals(0L, s.durationMs)
        // zoneDistribution requires >= 2 samples for a delta-time calc
        assertTrue(s.zoneDistribution().isEmpty())
    }

    @Test
    fun `min, peak, avg over many samples`() {
        val s = WorkoutSession(
            samples = listOf(
                HrSample(0L, 100),
                HrSample(10_000L, 200),
                HrSample(20_000L, 150),
            ),
            maxHr = maxHr,
        )
        assertEquals(100, s.minHr)
        assertEquals(200, s.peakHr)
        assertEquals(150, s.avgHr) // average of 100, 200, 150 → 150.0
        assertEquals(20_000L, s.durationMs)
    }

    // --- Zone distribution ---

    @Test
    fun `zone distribution sums to one within rounding`() {
        val s = WorkoutSession(
            samples = listOf(
                HrSample(0L, 100),     // WARMUP
                HrSample(10_000L, 130),// FAT_BURN
                HrSample(20_000L, 150),// CARDIO
                HrSample(30_000L, 170),// PEAK
                HrSample(40_000L, 195),// MAX
            ),
            maxHr = maxHr,
        )
        val total = s.zoneDistribution().values.sum()
        assertEquals(1.0f, total, 0.0001f)
    }

    @Test
    fun `zone distribution attributes time to the zone of the segment endpoint`() {
        // Implementation uses samples[i].heartRate to classify segment i
        // (the segment i-1..i), so 60 s entirely in CARDIO should report 100 % CARDIO.
        val s = WorkoutSession(
            samples = listOf(
                HrSample(0L, 150),
                HrSample(60_000L, 150),
            ),
            maxHr = maxHr,
        )
        val dist = s.zoneDistribution()
        assertEquals(1.0f, dist[HrZone.CARDIO] ?: 0f, 0.0001f)
    }

    @Test
    fun `zone distribution splits two equally long segments fifty fifty`() {
        // First segment 60 s in CARDIO (150 bpm), second 60 s in PEAK (170 bpm).
        val s = WorkoutSession(
            samples = listOf(
                HrSample(0L, 150),
                HrSample(60_000L, 150),  // segment 1 ends in CARDIO
                HrSample(120_000L, 170), // segment 2 ends in PEAK
            ),
            maxHr = maxHr,
        )
        val dist = s.zoneDistribution()
        assertEquals(0.5f, dist[HrZone.CARDIO] ?: 0f, 0.0001f)
        assertEquals(0.5f, dist[HrZone.PEAK] ?: 0f, 0.0001f)
    }

    // --- Calorie integration via the user profile ---

    @Test
    fun `calorie estimate delegates to userProfile - male sanity check`() {
        val profile = UserProfile(age = 30, weightKg = 75.0, isMale = true)
        val s = WorkoutSession(
            samples = listOf(
                HrSample(0L, 150),
                HrSample(60_000L, 150),
            ),
            maxHr = maxHr,
            userProfile = profile,
        )
        // See UserProfileTest for the exact derivation; this test pins the
        // delegation pathway, not the formula itself.
        assertEquals(profile.calculateCalories(s.samples), s.caloriesEstimate)
    }
}
