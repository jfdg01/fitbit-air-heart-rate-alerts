package com.nikashitsa.polar_alert_android.lib

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertsTest {
    private val heard = mutableListOf<Any>()
    private val alerts = Alerts(playSound = { heard += it }, vibrate = { heard += it })

    /** One sample a second, from second [from] to second [to], both included. */
    private fun samples(bpm: Int, from: Int, to: Int) {
        for (s in from..to) alerts.onHeartRate(bpm, s * 1000L)
    }

    @Test
    fun aShortStretchOutOfRangeStaysQuiet() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140, outOfRangeFor = 10), now = 0)
        samples(150, 0, 9)
        assertEquals(emptyList<Any>(), heard)
    }

    @Test
    fun aLongStretchSaysTheStateThenBeeps() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140, outOfRangeFor = 10), now = 0)
        samples(150, 0, 10)
        assertEquals(listOf(SoundType.TOO_HIGH, SoundType.HIGH_BEEP, VibrationType.HIGH), heard)
        assertEquals(true, alerts.state.value.alerting)
    }

    @Test
    fun theBeepRepeatsAtTheAlertInterval() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140, alertInterval = 3), now = 0)
        samples(110, 0, 6)
        assertEquals(
            listOf(
                SoundType.TOO_LOW, SoundType.LOW_BEEP, VibrationType.LOW, // second 0
                SoundType.LOW_BEEP, VibrationType.LOW, // second 3
                SoundType.LOW_BEEP, VibrationType.LOW, // second 6
            ),
            heard,
        )
    }

    @Test
    fun backInRangeSaysGoodOnlyAfterAnAlert() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140, outOfRangeFor = 5), now = 0)
        samples(150, 0, 2)
        samples(130, 3, 3)
        assertEquals(emptyList<Any>(), heard)

        samples(150, 4, 9)
        heard.clear()
        samples(130, 10, 10)
        assertEquals(listOf(SoundType.GOOD), heard)
        assertEquals(false, alerts.state.value.alerting)
    }

    @Test
    fun aFlipFromHighToLowSaysTheNewState() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140, alertInterval = 10), now = 0)
        samples(150, 0, 0)
        heard.clear()
        samples(110, 1, 1)
        assertEquals(listOf(SoundType.TOO_LOW), heard)
    }

    @Test
    fun aFixedInitialDelayHoldsAlertsBackThenEndsOnTime() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140, initialDelay = 60), now = 0)
        samples(150, 0, 59)
        assertEquals(emptyList<Any>(), heard)
        assertEquals(true, alerts.state.value.initialDelayActive)

        // In range at the end of the delay: the countdown ends, and nothing sounds.
        samples(130, 60, 60)
        assertEquals(false, alerts.state.value.initialDelayActive)
        assertEquals(emptyList<Any>(), heard)
    }

    @Test
    fun aFixedInitialDelayCountsTheStretchThatStartedInsideIt() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140, outOfRangeFor = 30, initialDelay = 60), now = 0)
        samples(150, 40, 60)
        assertEquals(emptyList<Any>(), heard)
        samples(150, 61, 70)
        assertEquals(listOf(SoundType.TOO_HIGH, SoundType.HIGH_BEEP, VibrationType.HIGH), heard)
    }

    @Test
    fun untilInRangeHoldsAlertsBackUntilTheFirstSampleInRange() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140, initialDelay = SettingsOptions.UNTIL_IN_RANGE), now = 0)
        samples(100, 0, 600)
        assertEquals(emptyList<Any>(), heard)

        samples(130, 601, 601)
        samples(100, 602, 602)
        assertEquals(listOf(SoundType.TOO_LOW, SoundType.LOW_BEEP, VibrationType.LOW), heard)
    }

    @Test
    fun aSecondStartKeepsTheSession() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140, initialDelay = 60), now = 0)
        samples(150, 0, 10)
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140, initialDelay = 60), now = 10_000)
        assertEquals(0L, alerts.state.value.startedAt)
        assertEquals(0L, alerts.state.value.outOfRangeSince)
    }

    @Test
    fun aSecondStartChangesTheRangeOfTheSession() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140), now = 0)
        samples(130, 0, 0)
        alerts.start(AlertSettings(hrMin = 140, hrMax = 160), now = 1000)
        samples(130, 1, 1)
        assertEquals(listOf(SoundType.TOO_LOW, SoundType.LOW_BEEP, VibrationType.LOW), heard)
        assertEquals(0L, alerts.state.value.startedAt)
    }

    @Test
    fun aStoppedSessionIgnoresLateSamples() {
        alerts.start(AlertSettings(hrMin = 120, hrMax = 140), now = 0)
        alerts.stop()
        samples(150, 0, 5)
        assertEquals(emptyList<Any>(), heard)
    }
}
