package com.nikashitsa.polar_alert_android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nikashitsa.polar_alert_android.ui.components.AppButton
import com.nikashitsa.polar_alert_android.ui.components.AppTextButton
import com.nikashitsa.polar_alert_android.ui.theme.Colors
import com.nikashitsa.polar_alert_android.ui.theme.Fonts
import com.nikashitsa.polar_alert_android.ui.theme.HeartAlertTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.nikashitsa.polar_alert_android.R
import com.nikashitsa.polar_alert_android.lib.AppLanguage
import com.nikashitsa.polar_alert_android.lib.BatteryStatusFeature
import com.nikashitsa.polar_alert_android.lib.BluetoothViewModel
import com.nikashitsa.polar_alert_android.lib.SettingsDefaults
import com.nikashitsa.polar_alert_android.lib.SettingsOptions
import com.nikashitsa.polar_alert_android.lib.SettingsViewModel
import com.nikashitsa.polar_alert_android.lib.SoundType
import com.nikashitsa.polar_alert_android.lib.SoundViewModel
import com.nikashitsa.polar_alert_android.ui.components.Accordion
import com.nikashitsa.polar_alert_android.ui.components.AppSlider
import com.nikashitsa.polar_alert_android.ui.components.AppSwitch
import com.nikashitsa.polar_alert_android.ui.components.DevicePicker
import com.nikashitsa.polar_alert_android.ui.components.DropdownMenuSelector
import com.nikashitsa.polar_alert_android.ui.components.LanguageSelector
import com.nikashitsa.polar_alert_android.ui.components.NumberField

@Composable
fun SettingsScreen(
    bluetooth: BluetoothViewModel = hiltViewModel(),
    settings: SettingsViewModel = hiltViewModel(),
    sound: SoundViewModel = hiltViewModel(),
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val deviceName = bluetooth.deviceName.collectAsState()
    val batteryStatusFeature = bluetooth.batteryStatusFeature.collectAsState()
    val volume by settings.volume.collectAsState()
    val vibrate by settings.vibrate.collectAsState()
    val hrMin by settings.hrMin.collectAsState()
    val hrMax by settings.hrMax.collectAsState()
    val alertInterval by settings.alertInterval.collectAsState()
    val outOfRangeFor by settings.outOfRangeFor.collectAsState()
    val initialDelay by settings.initialDelay.collectAsState()
    val language by settings.language.collectAsState()

    BackHandler {
        onBack()
    }

    SettingsScreenContent(
        deviceName = deviceName.value,
        batteryStatusFeature = batteryStatusFeature.value,
        volume = volume,
        setVolume = settings::setVolume,
        vibrate = vibrate,
        setVibrate = settings::setVibrate,
        alertInterval = alertInterval,
        setAlertInterval = settings::setAlertInterval,
        outOfRangeFor = outOfRangeFor,
        setOutOfRangeFor = settings::setOutOfRangeFor,
        initialDelay = initialDelay,
        setInitialDelay = settings::setInitialDelay,
        hrMin = hrMin,
        setHrMin = settings::setHrMin,
        hrMax = hrMax,
        setHrMax = settings::setHrMax,
        playSound = sound::play,
        language = language,
        setLanguage = settings::setLanguage,
        onNext = onNext,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    deviceName: String = "HRM BELT",
    batteryStatusFeature: BatteryStatusFeature = BatteryStatusFeature(true),
    volume: Int = SettingsDefaults.VOLUME,
    setVolume: (Int) -> Unit = {},
    vibrate: Boolean = SettingsDefaults.VIBRATE,
    setVibrate: (Boolean) -> Unit = {},
    alertInterval: Int = SettingsDefaults.ALERT_INTERVAL,
    setAlertInterval: (Int) -> Unit = {},
    outOfRangeFor: Int = SettingsDefaults.OUT_OF_RANGE_FOR,
    setOutOfRangeFor: (Int) -> Unit = {},
    initialDelay: Int = SettingsDefaults.INITIAL_DELAY,
    setInitialDelay: (Int) -> Unit = {},
    hrMin: Int = SettingsDefaults.HR_MIN,
    setHrMin: (Int) -> Unit = {},
    hrMax: Int = SettingsDefaults.HR_MAX,
    setHrMax: (Int) -> Unit = {},
    playSound: (SoundType) -> Unit = {},
    language: AppLanguage = AppLanguage.DEFAULT,
    setLanguage: (AppLanguage) -> Unit = {},
    onNext: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.Black)
            .padding(16.dp, 40.dp, 16.dp, 16.dp)
            .verticalScroll(scrollState),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.settings), style = Fonts.textXlBold)
            Spacer(modifier = Modifier.weight(1f))
            LanguageSelector(value = language, setValue = setLanguage)
        }

        Spacer(modifier = Modifier.height(40.dp))

        SettingSection(title = stringResource(R.string.heart_rate)) {
            SettingRow(label = stringResource(R.string.min)) {
                NumberField(hrMin, SettingsOptions.HR_LOWEST..SettingsOptions.HR_HIGHEST, setHrMin)
                Text(" " + stringResource(R.string.bpm))
            }
            SettingRow(label = stringResource(R.string.max)) {
                NumberField(hrMax, SettingsOptions.HR_LOWEST..SettingsOptions.HR_HIGHEST, setHrMax)
                Text(" " + stringResource(R.string.bpm))
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        SettingSection(title = stringResource(R.string.alert)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeMute,
                    contentDescription = stringResource(R.string.volume_down),
                )
                AppSlider(
                    value = volume,
                    onValueChange = {
                        setVolume(it)
                        playSound(SoundType.LOW_BEEP)
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.volume_up),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Accordion(label = stringResource(R.string.advanced)) {
                SettingRow(label = stringResource(R.string.vibration)) {
                    AppSwitch(vibrate) {
                        setVibrate(it)
                    }
                }
                SettingRow(label = stringResource(R.string.interval)) {
                    DropdownMenuSelector(
                        alertInterval,
                        options = SettingsOptions.ALERT_INTERVAL,
                        label = { stringResource(R.string.seconds_value, it) },
                        setValue = { it -> setAlertInterval(it) }
                    )
                }
                SettingRow(label = stringResource(R.string.out_of_range_for)) {
                    DropdownMenuSelector(
                        outOfRangeFor,
                        options = SettingsOptions.OUT_OF_RANGE_FOR,
                        label = { formatDuration(it) },
                        itemLabel = { formatDuration(it) },
                        setValue = { it -> setOutOfRangeFor(it) }
                    )
                }
                SettingRow(label = stringResource(R.string.initial_delay)) {
                    DropdownMenuSelector(
                        initialDelay,
                        options = SettingsOptions.INITIAL_DELAY,
                        label = { formatInitialDelay(it) },
                        itemLabel = { formatInitialDelay(it) },
                        setValue = { it -> setInitialDelay(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        SettingSection(title = stringResource(R.string.connection)) {
            SettingRow(label = stringResource(R.string.device)) {
                AppTextButton(onClick = {
                    showPicker = true
                }) {
                    Text(text = deviceName)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.chevron))
                }
            }
            if (batteryStatusFeature.isSupported) {
                SettingRow(label = stringResource(R.string.battery)) {
                    Text(stringResource(R.string.battery_value, batteryStatusFeature.batteryLevel))
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        Spacer(modifier = Modifier.weight(1f))

        AppButton(stringResource(R.string.start), onClick = onNext)

        if (showPicker) {
            DevicePicker(sheetState) {
                showPicker = false
            }
        }
    }
}

@Composable
private fun formatDuration(seconds: Int): String =
    if (seconds < 60) {
        stringResource(R.string.seconds_value, seconds)
    } else {
        stringResource(R.string.minutes_value, seconds / 60)
    }

@Composable
private fun formatInitialDelay(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.initial_delay_off)
    SettingsOptions.UNTIL_IN_RANGE -> stringResource(R.string.initial_delay_until_in_range)
    else -> formatDuration(seconds)
}

@Composable
fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(text = title, style = Fonts.textLgBold)
        Column(content = content)
    }
}

@Composable
fun SettingRow(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(40.dp)
    ) {
        Text(label)
        Spacer(modifier = Modifier.weight(1f))
        content()
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    HeartAlertTheme {
        SettingsScreenContent()
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenFreeSessionsPreview() {
    HeartAlertTheme {
        SettingsScreenContent()
    }
}
