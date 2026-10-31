package com.nikashitsa.polar_alert_android.lib

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartZonesTest {
    @Test
    fun zonesFollowKarvonenWithoutGaps() {
        // Reserve of 120 BPM: 60% is 72 and 70% is 84.
        assertEquals(132..143, HeartZones.range(2, 60, 180))
        assertEquals(144..155, HeartZones.range(3, 60, 180))
        assertEquals(168..180, HeartZones.range(5, 60, 180))
    }

    @Test
    fun peakComesFromAge() {
        assertEquals(180, HeartZones.peakFromAge(40))
        assertEquals(152, HeartZones.peakFromAge(80))
    }
}
