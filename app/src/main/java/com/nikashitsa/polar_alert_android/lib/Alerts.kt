package com.nikashitsa.polar_alert_android.lib

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** The settings of one session. Times are in seconds, as in the Settings screen. */
data class AlertSettings(
    val hrMin: Int = SettingsDefaults.HR_MIN,
    val hrMax: Int = SettingsDefaults.HR_MAX,
    val alertInterval: Int = SettingsDefaults.ALERT_INTERVAL,
    val outOfRangeFor: Int = SettingsDefaults.OUT_OF_RANGE_FOR,
    val initialDelay: Int = SettingsDefaults.INITIAL_DELAY,
)

/** What the tracking screen shows. Times are epoch ms. */
data class AlertState(
    /** The last sample, -1 before the first one. */
    val bpm: Int = -1,
    val trackingState: TrackingState = TrackingState.GOOD,
    /** The out-of-range stretch has lasted long enough, and the alert sounds. */
    val alerting: Boolean = false,
    /** The start of the current out-of-range stretch, null while in range. */
    val outOfRangeSince: Long? = null,
    /** Alerts are held back at the start of the session. */
    val initialDelayActive: Boolean = false,
    val startedAt: Long = 0,
)

/**
 * The alert state machine of one runner. The process has one, so a session outlives its
 * screen. Every call comes on the main thread: the strap posts its samples there.
 */
class Alerts(
    private val playSound: (SoundType) -> Unit,
    private val vibrate: (VibrationType) -> Unit,
) {
    private val _state = MutableStateFlow(AlertState())
    val state: StateFlow<AlertState> = _state.asStateFlow()

    private var settings = AlertSettings()
    private var running = false
    private var lastTriggerTime: Long? = null

    /** A second call before [stop] only changes the settings, so a rotation does not restart the session. */
    fun start(settings: AlertSettings, now: Long = System.currentTimeMillis()) {
        this.settings = settings
        if (running) return
        running = true
        lastTriggerTime = null
        _state.value = AlertState(initialDelayActive = settings.initialDelay != 0, startedAt = now)
    }

    fun stop() {
        running = false
    }

    /** Gets every sample the strap sends, about once a second. */
    fun onHeartRate(hr: Int, now: Long = System.currentTimeMillis()) {
        if (!running) return
        val prev = _state.value
        val trackingState = TrackingState.of(hr, settings.hrMin, settings.hrMax)
        var s = prev.copy(bpm = hr, trackingState = trackingState)

        // A fixed delay ends on time, in range or not. "Until in range" never times out.
        val delayMs = settings.initialDelay * 1000L
        if (s.initialDelayActive && delayMs > 0 && now - s.startedAt >= delayMs) {
            s = s.copy(initialDelayActive = false)
        }

        // Back in range. Announce the recovery only if we were really alerting.
        if (trackingState == TrackingState.GOOD) {
            lastTriggerTime = null
            _state.value = s.copy(
                alerting = false,
                outOfRangeSince = null,
                initialDelayActive = s.initialDelayActive && settings.initialDelay != SettingsOptions.UNTIL_IN_RANGE,
            )
            if (s.alerting) playSound(trackingState.soundState)
            return
        }

        // Out of range. Keep timing the stretch even while alerts are held back.
        val since = s.outOfRangeSince ?: now
        s = s.copy(outOfRangeSince = since)
        _state.value = s
        if (s.initialDelayActive) return

        // Stay quiet until HR has been out of range long enough.
        if (now - since < settings.outOfRangeFor * 1000L) return

        // Say what is wrong when alerts start, and again on a too low <-> too high flip.
        if (!s.alerting || trackingState != prev.trackingState) {
            _state.value = s.copy(alerting = true)
            playSound(trackingState.soundState)
        }

        // Repeat the beep and the vibration at the chosen interval.
        val throttleInterval = settings.alertInterval * 1000L - 310 // ms
        val lastTrigger = lastTriggerTime
        if (lastTrigger == null || now - lastTrigger > throttleInterval) {
            lastTriggerTime = now
            trackingState.sound?.let(playSound)
            trackingState.vibration?.let(vibrate)
        }
    }
}

/** Gives the tracking screen the one [Alerts] of the process. */
@HiltViewModel
class AlertsViewModel @Inject constructor(val alerts: Alerts) : ViewModel()
