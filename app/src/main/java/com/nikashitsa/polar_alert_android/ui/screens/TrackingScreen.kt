package com.nikashitsa.polar_alert_android.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nikashitsa.polar_alert_android.lib.RoomState
import com.nikashitsa.polar_alert_android.lib.RoomViewModel
import com.nikashitsa.polar_alert_android.R
import com.nikashitsa.polar_alert_android.TrackingService
import com.nikashitsa.polar_alert_android.lib.AlertSettings
import com.nikashitsa.polar_alert_android.lib.AlertState
import com.nikashitsa.polar_alert_android.lib.AlertsViewModel
import com.nikashitsa.polar_alert_android.lib.BluetoothViewModel
import com.nikashitsa.polar_alert_android.lib.DeviceConnectionState
import com.nikashitsa.polar_alert_android.lib.HrFeature
import com.nikashitsa.polar_alert_android.lib.SettingsDefaults
import com.nikashitsa.polar_alert_android.lib.SettingsViewModel
import com.nikashitsa.polar_alert_android.lib.SoundType
import com.nikashitsa.polar_alert_android.lib.SoundViewModel
import com.nikashitsa.polar_alert_android.lib.TrackingState
import com.nikashitsa.polar_alert_android.ui.components.AppButton
import com.nikashitsa.polar_alert_android.ui.theme.Colors
import com.nikashitsa.polar_alert_android.ui.theme.Fonts
import com.nikashitsa.polar_alert_android.ui.theme.HeartAlertTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    bluetooth: BluetoothViewModel = hiltViewModel(),
    settings: SettingsViewModel = hiltViewModel(),
    sound: SoundViewModel = hiltViewModel(),
    alertsViewModel: AlertsViewModel = hiltViewModel(),
    roomViewModel: RoomViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val deviceConnectionState = bluetooth.deviceConnectionState.collectAsState()
    val hrFeature = bluetooth.hrFeature.collectAsState()
    val hrMin by settings.hrMin.collectAsState()
    val hrMax by settings.hrMax.collectAsState()
    val alertInterval by settings.alertInterval.collectAsState()
    val outOfRangeFor by settings.outOfRangeFor.collectAsState()
    val initialDelay by settings.initialDelay.collectAsState()
    val alerts = alertsViewModel.alerts
    val alertState by alerts.state.collectAsState()
    val roomState by roomViewModel.room.state.collectAsState()

    // A connectedDevice service needs BLUETOOTH_CONNECT. The demo runs without it, and without the service.
    val context = LocalContext.current
    val alertSettings = AlertSettings(hrMin, hrMax, alertInterval, outOfRangeFor, initialDelay)
    LaunchedEffect(alertSettings) { alerts.start(alertSettings) }
    DisposableEffect(Unit) {
        val service = Intent(context, TrackingService::class.java)
        val allowed = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (allowed) context.startForegroundService(service)
        // A rotation recreates the screen. The session and the service stay up, and the next start only updates them.
        onDispose {
            if ((context as? Activity)?.isChangingConfigurations != true) {
                alerts.stop()
                context.stopService(service)
            }
        }
    }

    BackHandler {
        bluetooth.hrStreamStop()
        onBack()
    }

    TrackingScreenContent(
        deviceConnectionState = deviceConnectionState.value,
        hrFeature = hrFeature.value,
        alertState = alertState,
        hrStreamStart = { address -> bluetooth.hrStreamStart(address) { alerts.onHeartRate(it) } },
        hrStreamStop = bluetooth::hrStreamStop,
        playSound = sound::play,
        hrMin = hrMin,
        hrMax = hrMax,
        outOfRangeFor = outOfRangeFor,
        initialDelay = initialDelay,
        partner = roomState as? RoomState.InRoom,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreenContent(
    deviceConnectionState: DeviceConnectionState,
    hrFeature: HrFeature = HrFeature(),
    alertState: AlertState = AlertState(),
    hrStreamStart: (String) -> Unit = {},
    hrStreamStop: () -> Unit = {},
    playSound: (SoundType) -> Unit = {},
    hrMin: Int = SettingsDefaults.HR_MIN,
    hrMax: Int = SettingsDefaults.HR_MAX,
    outOfRangeFor: Int = SettingsDefaults.OUT_OF_RANGE_FOR,
    initialDelay: Int = SettingsDefaults.INITIAL_DELAY,
    partner: RoomState.InRoom? = null,
    onBack: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.range, hrMin, hrMax))
        }
        Spacer(modifier = Modifier.weight(1f))

        BpmView(
            deviceConnectionState = deviceConnectionState,
            hrFeature = hrFeature,
            alertState = alertState,
            hrStreamStart = hrStreamStart,
            playSound = playSound,
            outOfRangeFor = outOfRangeFor,
            initialDelay = initialDelay,
        )

        partner?.let {
            Spacer(modifier = Modifier.height(32.dp))
            PartnerRow(it)
        }

        Spacer(modifier = Modifier.weight(1f))

        AppButton(stringResource(R.string.stop)) {
            hrStreamStop()
            onBack()
        }
    }
}

@Composable
fun BpmView(
    deviceConnectionState: DeviceConnectionState,
    hrFeature: HrFeature,
    alertState: AlertState,
    hrStreamStart: (String) -> Unit = {},
    playSound: (SoundType) -> Unit = {},
    outOfRangeFor: Int = SettingsDefaults.OUT_OF_RANGE_FOR,
    initialDelay: Int = SettingsDefaults.INITIAL_DELAY,
) {
    var prevConnectionState by rememberSaveable(
        stateSaver = DeviceConnectionState.Saver
    ) { mutableStateOf(DeviceConnectionState.Connected()) }

    when (val connectionState = deviceConnectionState) {
        is DeviceConnectionState.Disconnected -> {
            Text(stringResource(R.string.disconnected), style = Fonts.textLg)
            PlaySoundRepeatedly(playSound, SoundType.DISCONNECTED) {
                prevConnectionState = DeviceConnectionState.Disconnected()
            }
        }
        is DeviceConnectionState.Connecting -> {
            Text(stringResource(R.string.reconnecting), style = Fonts.textLg)
            PlaySoundRepeatedly(playSound, SoundType.RECONNECTING) {
                prevConnectionState = DeviceConnectionState.Disconnected()
            }
        }
        is DeviceConnectionState.Connected -> {
            if (hrFeature.isSupported) {
                LaunchedEffect(Unit) {
                    if (prevConnectionState is DeviceConnectionState.Disconnected) {
                        playSound(SoundType.CONNECTED)
                    }
                    hrStreamStart(connectionState.address)
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BpmReadout(
                        bpm = alertState.bpm,
                        state = alertState.trackingState,
                        color = if (alertState.alerting) Colors.Red else Colors.White,
                    )
                    AlertStatus(
                        state = alertState.trackingState,
                        alerting = alertState.alerting,
                        hasReading = alertState.bpm > -1,
                        initialDelay = initialDelay,
                        initialDelayActive = alertState.initialDelayActive,
                        trackingStartedAt = alertState.startedAt,
                        outOfRangeSince = alertState.outOfRangeSince,
                        outOfRangeForInterval = outOfRangeFor * 1000,
                    )
                }
            } else {
                Text(stringResource(R.string.reconnecting), style = Fonts.textLg)
            }
        }
    }
}

/** The partner of the room, small under the pulse of my runner. */
@Composable
fun PartnerRow(room: RoomState.InRoom) {
    val partner = room.partner
    val value = when {
        !room.linked -> "--"
        !partner.strapConnected -> stringResource(R.string.disconnected)
        partner.bpm < 0 -> "--"
        else -> "${partner.bpm} ${stringResource(R.string.bpm)}"
    }
    val color = if (room.linked && partner.trackingState != TrackingState.GOOD) Colors.Red else Colors.White
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(partner.name, style = Fonts.textLg)
        Text(value, style = Fonts.textLgBold, color = color)
    }
}

/** The big BPM number with the beating heart next to it. */
@Composable
fun BpmReadout(bpm: Int, state: TrackingState, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(80.dp),
    ) {
        Text(
            text = if (bpm > -1) "$bpm" else "--",
            style = Fonts.text2XlBold,
            overflow = TextOverflow.Visible,
            modifier = Modifier.offset(y = (-12).dp),
            color = color,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeartIcon(state)
            Text(text = stringResource(R.string.bpm), style = Fonts.textLg, color = color)
        }
    }
}

/**
 * The line under the BPM number. It shows whichever of these is happening: the
 * alert itself, the initial delay, the wait for HR to stay out of range, or else
 * the plain state of the last reading.
 * The fixed height keeps the screen from jumping as it switches between them.
 */
@Composable
fun AlertStatus(
    state: TrackingState,
    alerting: Boolean,
    hasReading: Boolean,
    initialDelay: Int,
    initialDelayActive: Boolean,
    trackingStartedAt: Long,
    outOfRangeSince: Long?,
    outOfRangeForInterval: Int,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(30.dp),
    ) {
        when {
            alerting -> Text(
                text = stringResource(state.heartBeatDescription),
                style = Fonts.textLg,
            )
            initialDelayActive -> if (initialDelay > 0) {
                InitialDelayCountdown(
                    since = trackingStartedAt,
                    duration = initialDelay * 1000,
                )
            } else {
                Text(
                    text = stringResource(R.string.initial_delay_until_in_range_status),
                    style = Fonts.textLg,
                )
            }
            outOfRangeSince != null && outOfRangeForInterval > 0 -> AlertCountdown(
                since = outOfRangeSince,
                duration = outOfRangeForInterval,
            )
            hasReading -> Text(
                text = stringResource(state.heartBeatDescription),
                style = Fonts.textLg,
            )
        }
    }
}

@Composable
fun PlaySoundRepeatedly(playSound: (SoundType) -> Unit = {}, soundType: SoundType, onStart: () -> Unit = {}) {
    LaunchedEffect(Unit) {
        onStart()
        while (true) {
            playSound(soundType)
            delay(5000.milliseconds)
        }
    }
}

/**
 * Counts down the time left of the initial delay, during which alerts are held back.
 * [since] is when tracking started, in epoch ms, [duration] the delay in ms.
 */
@Composable
fun InitialDelayCountdown(since: Long, duration: Int) {
    var remaining by remember(since, duration) { mutableIntStateOf(duration) }

    LaunchedEffect(since, duration) {
        while (remaining > 0) {
            remaining = (duration - (System.currentTimeMillis() - since)).coerceAtLeast(0L).toInt()
            // faster than once a second, so the displayed second never lags behind
            delay(200.milliseconds)
        }
    }

    Text(
        text = stringResource(R.string.initial_delay_countdown, remaining / 60_000, remaining / 1000 % 60),
        style = Fonts.textLg,
    )
}

/**
 * Fills up as the current out-of-range stretch approaches [duration] ms, at which
 * point alerts start. [since] is the start of the stretch, in epoch ms.
 */
@Composable
fun AlertCountdown(since: Long, duration: Int) {
    var progress by remember(since, duration) { mutableFloatStateOf(0f) }

    LaunchedEffect(since, duration) {
        // one step per ~1% of the wait, so a 10 min countdown isn't redrawn 20x a second
        val step = (duration / 100).coerceIn(50, 1000).toLong()
        while (progress < 1f) {
            progress = ((System.currentTimeMillis() - since).toFloat() / duration).coerceIn(0f, 1f)
            delay(step.milliseconds)
        }
    }

    CircularProgressIndicator(
        progress = { progress },
        modifier = Modifier.size(20.dp),
        color = Colors.Red,
        trackColor = Colors.White,
        strokeWidth = 2.dp,
        gapSize = 0.dp,
    )
}

@Composable
fun HeartIcon(state: TrackingState) {
    key(state.heartBeatDuration) {
        val infiniteTransition = rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween((state.heartBeatDuration * 1000).toInt(), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
        Image(
            painter = painterResource(id = R.drawable.heart),
            contentDescription = stringResource(R.string.heart),
            modifier = Modifier
                .height(32.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
    }
}

@Preview
@Composable
fun TrackingScreenConnectedPreview() {
    HeartAlertTheme {
        TrackingScreenContent(
            deviceConnectionState = DeviceConnectionState.Connected(),
            hrFeature = HrFeature(true),
            alertState = AlertState(bpm = 117),
        )
    }
}
@Preview
@Composable
fun TrackingScreenDisconnectedPreview() {
    HeartAlertTheme {
        TrackingScreenContent(
            deviceConnectionState = DeviceConnectionState.Disconnected()
        )
    }
}
@Preview
@Composable
fun TrackingScreenConnectingPreview() {
    HeartAlertTheme {
        TrackingScreenContent(
            deviceConnectionState = DeviceConnectionState.Connecting()
        )
    }
}
