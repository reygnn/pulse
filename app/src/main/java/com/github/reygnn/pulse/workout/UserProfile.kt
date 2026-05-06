package com.github.reygnn.pulse.workout

import android.content.Context
import android.content.SharedPreferences

/**
 * User profile for calorie calculation.
 */
data class UserProfile(
    val age: Int = 30,
    val weightKg: Double = 75.0,
    val isMale: Boolean = true
) {
    /**
     * Estimated max heart rate using Tanaka formula (more accurate than 220-age).
     * Tanaka et al. (2001): HRmax = 208 - 0.7 × age
     */
    val estimatedMaxHr: Int
        get() = (208 - 0.7 * age).toInt()

    /**
     * Calorie calculation based on Keytel et al. (2005).
     *
     * Male:   kcal/min = (-55.0969 + 0.6309 × HR + 0.1988 × weight + 0.2017 × age) / 4.184
     * Female: kcal/min = (-20.4022 + 0.4472 × HR + 0.1263 × weight + 0.074  × age) / 4.184
     *
     * @param samples list of HR samples with timestamps
     * @return estimated calories burned
     */
    fun calculateCalories(samples: List<HrSample>): Int {
        if (samples.size < 2) return 0

        var totalKcal = 0.0

        for (i in 1 until samples.size) {
            val hr = samples[i].heartRate.toDouble()
            val dtMinutes = (samples[i].timestampMs - samples[i - 1].timestampMs) / 60_000.0

            if (hr <= 0 || dtMinutes <= 0) continue

            val kcalPerMin = if (isMale) {
                (-55.0969 + 0.6309 * hr + 0.1988 * weightKg + 0.2017 * age) / 4.184
            } else {
                (-20.4022 + 0.4472 * hr + 0.1263 * weightKg + 0.074 * age) / 4.184
            }

            // Only count positive values (formula can go negative at very low HR)
            totalKcal += (kcalPerMin * dtMinutes).coerceAtLeast(0.0)
        }

        return totalKcal.toInt()
    }
}

/**
 * Persists UserProfile to SharedPreferences.
 */
class UserProfileStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "pulse_user_profile"
        private const val KEY_AGE = "age"
        private const val KEY_WEIGHT = "weight_kg"
        private const val KEY_IS_MALE = "is_male"
        private const val KEY_PROFILE_SET = "profile_set"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isProfileSet: Boolean
        get() = prefs.getBoolean(KEY_PROFILE_SET, false)

    fun load(): UserProfile {
        return UserProfile(
            age = prefs.getInt(KEY_AGE, 30),
            weightKg = prefs.getFloat(KEY_WEIGHT, 75f).toDouble(),
            isMale = prefs.getBoolean(KEY_IS_MALE, true)
        )
    }

    fun save(profile: UserProfile) {
        prefs.edit()
            .putInt(KEY_AGE, profile.age)
            .putFloat(KEY_WEIGHT, profile.weightKg.toFloat())
            .putBoolean(KEY_IS_MALE, profile.isMale)
            .putBoolean(KEY_PROFILE_SET, true)
            .apply()
    }
}