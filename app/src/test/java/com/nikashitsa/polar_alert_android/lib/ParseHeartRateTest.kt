package com.nikashitsa.polar_alert_android.lib

import org.junit.Assert.assertEquals
import org.junit.Test

class ParseHeartRateTest {
    @Test
    fun readsBothValueSizes() {
        assertEquals(130, parseHeartRate(byteArrayOf(0x00, 130.toByte())))
        assertEquals(300, parseHeartRate(byteArrayOf(0x01, 0x2C, 0x01)))
    }

    @Test
    fun rejectsShortValues() {
        assertEquals(null, parseHeartRate(byteArrayOf()))
        assertEquals(null, parseHeartRate(byteArrayOf(0x01, 0x2C)))
    }
}
