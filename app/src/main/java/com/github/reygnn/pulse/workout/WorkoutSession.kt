package com.github.reygnn.pulse.workout

/**
 * Heart rate zones based on percentage of max HR.
 * Default max HR = 220 - age (can be customized).
 */
enum class HrZone(val label: String, val minPct: Float, val maxPct: Float, val colorHex: Long) {
    REST("Ruhe", 0f, 0.50f, 0xFF9E9E9E),
    WARMUP("Aufwärmen", 0.50f, 0.60f, 0xFF42A5F5),
    FAT_BURN("Fettverbrennung", 0.60f, 0.70f, 0xFF66BB6A),
    CARDIO("Cardio", 0.70f, 0.80f, 0xFFFFA726),
    PEAK("Peak", 0.80f, 0.90f, 0xFFEF5350),
    MAX("Maximum", 0.90f, 1.0f, 0xFFD50000);

    companion object {
        fun fromHeartRate(hr: Int, maxHr: Int): HrZone {
            if (maxHr <= 0 || hr <= 0) return REST
            val pct = hr.toFloat() / maxHr
            return entries.lastOrNull { pct >= it.minPct } ?: REST
        }
    }
}

/**
 * A single HR sample with timestamp relative to workout start.
 */
data class HrSample(
    val timestampMs: Long,  // ms since workout start
    val heartRate: Int
)

/**
 * Holds all data for a workout session.
 */
data class WorkoutSession(
    val startTimeMs: Long = System.currentTimeMillis(),
    val samples: List<HrSample> = emptyList(),
    val maxHr: Int = 190,
    val userProfile: UserProfile = UserProfile()
) {
    val durationMs: Long
        get() = if (samples.isEmpty()) 0L else samples.last().timestampMs

    val avgHr: Int
        get() = if (samples.isEmpty()) 0 else samples.map { it.heartRate }.average().toInt()

    val minHr: Int
        get() = samples.minOfOrNull { it.heartRate } ?: 0

    val peakHr: Int
        get() = samples.maxOfOrNull { it.heartRate } ?: 0

    val caloriesEstimate: Int
        get() = userProfile.calculateCalories(samples)

    /**
     * Time spent in each zone as percentage of total duration.
     */
    fun zoneDistribution(): Map<HrZone, Float> {
        if (samples.size < 2) return emptyMap()

        val zoneDurations = mutableMapOf<HrZone, Long>()
        for (i in 1 until samples.size) {
            val zone = HrZone.fromHeartRate(samples[i].heartRate, maxHr)
            val dt = samples[i].timestampMs - samples[i - 1].timestampMs
            zoneDurations[zone] = (zoneDurations[zone] ?: 0L) + dt
        }

        val total = zoneDurations.values.sum().toFloat().coerceAtLeast(1f)
        return zoneDurations.mapValues { it.value / total }
    }
}