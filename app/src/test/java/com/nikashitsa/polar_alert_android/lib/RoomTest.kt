package com.nikashitsa.polar_alert_android.lib

import com.nikashitsa.polar_alert_android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomTest {
    @Test
    fun theRunnerStateCrossesTheLinkWhole() {
        val ana = RunnerState("Ana", bpm = 152, trackingState = TrackingState.HIGH, alerting = true, strapConnected = true, leaving = false)
        assertEquals(ana, RunnerState.decode(ana.encode()))
        val left = RunnerState("Javi", bpm = -1, trackingState = TrackingState.GOOD, alerting = false, strapConnected = false, leaving = true)
        assertEquals(left, RunnerState.decode(left.encode()))
    }

    @Test
    fun theStateFitsInOnePacketWithoutAnotherMtu() {
        // 10 characters of 4 bytes each are 40 bytes of UTF-8.
        val state = RunnerState("😀".repeat(10), 180, TrackingState.HIGH, true, true, false)
        val packet = state.encode()
        assertTrue(packet.size <= 20)
        assertEquals("😀".repeat(4), RunnerState.decode(packet)?.name)
    }

    @Test
    fun aPacketThatIsNotAStateIsNoState() {
        assertNull(RunnerState.decode(byteArrayOf()))
        assertNull(RunnerState.decode(byteArrayOf(3, 120)))
    }

    private fun ana(state: TrackingState, alerting: Boolean) = RunnerState("Ana", 150, state, alerting, true, false)

    @Test
    fun thePartnerVoiceSpeaksWhenTheAlertStarts() {
        assertEquals(R.string.voice_too_high, partnerLine(ana(TrackingState.HIGH, false), ana(TrackingState.HIGH, true)))
        assertEquals(R.string.voice_too_low, partnerLine(null, ana(TrackingState.LOW, true)))
    }

    @Test
    fun thePartnerVoiceKeepsQuietWhileTheAlertGoesOn() {
        assertNull(partnerLine(ana(TrackingState.HIGH, true), ana(TrackingState.HIGH, true)))
        // Out of range without an alert: the settings of the partner say it is not time yet.
        assertNull(partnerLine(ana(TrackingState.GOOD, false), ana(TrackingState.HIGH, false)))
    }

    @Test
    fun thePartnerVoiceSaysTheFlipFromLowToHigh() {
        assertEquals(R.string.voice_too_high, partnerLine(ana(TrackingState.LOW, true), ana(TrackingState.HIGH, true)))
    }

    @Test
    fun thePartnerVoiceSaysGoodOnlyAfterAnAlert() {
        assertEquals(R.string.voice_good, partnerLine(ana(TrackingState.HIGH, true), ana(TrackingState.GOOD, false)))
        assertNull(partnerLine(ana(TrackingState.HIGH, false), ana(TrackingState.GOOD, false)))
        // The session of the partner stops: the alert ends without a return to the zone.
        assertNull(partnerLine(ana(TrackingState.HIGH, true), ana(TrackingState.HIGH, false)))
        // Outside the session, the partner sends GOOD without a pulse.
        val stopped = RunnerState("Ana", -1, TrackingState.GOOD, false, true, false)
        assertNull(partnerLine(ana(TrackingState.HIGH, true), stopped))
    }
}
