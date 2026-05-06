package com.github.reygnn.pulse.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileTest {

    // --- Tanaka max-HR formula: HRmax = 208 - 0.7 * age ---

    @Test
    fun `estimatedMaxHr matches Tanaka formula at age 30`() {
        // 208 - 0.7 * 30 = 187.0 → toInt() truncates to 187
        assertEquals(187, UserProfile(age = 30).estimatedMaxHr)
    }

    @Test
    fun `estimatedMaxHr at age 20 and 60`() {
        assertEquals(194, UserProfile(age = 20).estimatedMaxHr) // 208 - 14 = 194
        assertEquals(166, UserProfile(age = 60).estimatedMaxHr) // 208 - 42 = 166
    }

    // --- Keytel calorie estimation ---

    @Test
    fun `calories return zero for empty samples`() {
        assertEquals(0, UserProfile().calculateCalories(emptyList()))
    }

    @Test
    fun `calories return zero for a single sample - need at least two for delta-time`() {
        assertEquals(0, UserProfile().calculateCalories(listOf(HrSample(0L, 120))))
    }

    @Test
    fun `calories with hr at or below zero are skipped, not negative`() {
        // The Keytel formula can go negative for very low HR. The implementation
        // coerces per-segment kcal to >= 0, so a stretch of zero-HR readings
        // does not cancel out real workout calories.
        val samples = listOf(
            HrSample(timestampMs = 0L, heartRate = 0),
            HrSample(timestampMs = 60_000L, heartRate = 0),
        )
        assertEquals(0, UserProfile().calculateCalories(samples))
    }

    @Test
    fun `male calories for one minute at 150bpm match formula`() {
        // For age 30, weight 75 kg, HR 150 bpm:
        //   kcal/min = (-55.0969 + 0.6309*150 + 0.1988*75 + 0.2017*30) / 4.184
        //            = (-55.0969 + 94.635 + 14.91 + 6.051) / 4.184
        //            = 60.4991 / 4.184
        //            ≈ 14.46 kcal/min
        // .toInt() truncates: 14
        val profile = UserProfile(age = 30, weightKg = 75.0, isMale = true)
        val samples = listOf(
            HrSample(timestampMs = 0L, heartRate = 150),
            HrSample(timestampMs = 60_000L, heartRate = 150),
        )
        assertEquals(14, profile.calculateCalories(samples))
    }

    @Test
    fun `female calories for one minute at 150bpm match formula`() {
        // For age 30, weight 65 kg, HR 150 bpm:
        //   kcal/min = (-20.4022 + 0.4472*150 + 0.1263*65 + 0.074*30) / 4.184
        //            = (-20.4022 + 67.08 + 8.2095 + 2.22) / 4.184
        //            = 57.1073 / 4.184
        //            ≈ 13.65 kcal/min
        // .toInt() truncates: 13
        val profile = UserProfile(age = 30, weightKg = 65.0, isMale = false)
        val samples = listOf(
            HrSample(timestampMs = 0L, heartRate = 150),
            HrSample(timestampMs = 60_000L, heartRate = 150),
        )
        assertEquals(13, profile.calculateCalories(samples))
    }

    @Test
    fun `male formula outpaces female at high heart rates`() {
        // The two Keytel formulas cross over: at moderate HR (~140 bpm) they
        // are close enough that the result depends on weight and age, but at
        // high HR (~170+) the male formula's larger HR coefficient (0.6309
        // vs 0.4472) dominates. Pin the high-HR side because that is the
        // direction the original "men burn more" intuition holds.
        val male = UserProfile(age = 30, weightKg = 70.0, isMale = true)
        val female = male.copy(isMale = false)
        val samples = listOf(
            HrSample(0L, 175),
            HrSample(600_000L, 175), // 10 minutes
        )
        assertTrue(male.calculateCalories(samples) > female.calculateCalories(samples))
    }

    @Test
    fun `calories scale with workout duration`() {
        val profile = UserProfile(age = 30, weightKg = 75.0, isMale = true)
        val tenMin = listOf(HrSample(0L, 140), HrSample(600_000L, 140))
        val twentyMin = listOf(HrSample(0L, 140), HrSample(1_200_000L, 140))
        // Same HR, double the time → roughly double the kcal (within rounding).
        assertTrue(profile.calculateCalories(twentyMin) >= 2 * profile.calculateCalories(tenMin) - 1)
    }

    @Test
    fun `non-monotonic timestamps do not produce negative calorie segments`() {
        // dtMinutes <= 0 is filtered in the implementation. A clock glitch
        // that emits an out-of-order sample must not dent the running total.
        val profile = UserProfile(age = 30, weightKg = 75.0, isMale = true)
        val realFirstMinute = listOf(
            HrSample(0L, 150),
            HrSample(60_000L, 150),
        )
        val withGlitch = listOf(
            HrSample(0L, 150),
            HrSample(60_000L, 150),
            HrSample(30_000L, 150), // earlier than the previous timestamp
        )
        assertEquals(
            profile.calculateCalories(realFirstMinute),
            profile.calculateCalories(withGlitch),
        )
    }
}
