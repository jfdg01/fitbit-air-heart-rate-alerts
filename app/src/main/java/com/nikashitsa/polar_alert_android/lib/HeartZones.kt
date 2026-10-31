package com.nikashitsa.polar_alert_android.lib

import kotlin.math.roundToInt

/** Five training zones from the Karvonen formula: resting + percent * (peak - resting). */
object HeartZones {
    val NUMBERS = 1..5

    /** Tanaka formula. The real peak of a person can differ by 10 BPM or more. */
    fun peakFromAge(age: Int): Int = (208 - 0.7 * age).roundToInt()

    /** Zone 1 is 50-60% of the reserve, up to zone 5 at 90-100%. The zones do not overlap. */
    fun range(zone: Int, resting: Int, peak: Int): IntRange {
        val at = { percent: Int -> resting + (peak - resting) * percent / 100 }
        val low = at(40 + zone * 10)
        return if (zone == NUMBERS.last) low..peak else low until at(50 + zone * 10)
    }

    fun median(values: List<Int>): Int? = values.sorted().let { it.getOrNull(it.size / 2) }
}
