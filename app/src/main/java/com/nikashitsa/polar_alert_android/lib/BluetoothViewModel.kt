package com.nikashitsa.polar_alert_android.lib

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val ble: HeartRateBle
): ViewModel() {

    private val tag = "BluetoothViewModel"
    val isBluetoothOn = ble.isOn

    private val _foundDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val foundDevices = _foundDevices.asStateFlow()

    private val _deviceConnectionState = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Disconnected())
    val deviceConnectionState = _deviceConnectionState.asStateFlow()

    private val _deviceName = MutableStateFlow("")
    val deviceName = _deviceName.asStateFlow()

    private val _batteryStatusFeature = MutableStateFlow(BatteryStatusFeature())
    val batteryStatusFeature = _batteryStatusFeature.asStateFlow()

    private val _hrFeature = MutableStateFlow(HrFeature())
    val hrFeature = _hrFeature.asStateFlow()

    private var demoHrJob: Job? = null

    /** Unlocked from the Connect screen; adds the simulated strap to the device list. */
    private val _demoEnabled = MutableStateFlow(false)
    val demoEnabled = _demoEnabled.asStateFlow()

    /** A simulated strap is connected, so the app can be used without real hardware. */
    val isDemo: Boolean
        get() = (_deviceConnectionState.value as? DeviceConnectionState.Connected)?.address == DEMO_ADDRESS

    init {
        ble.onConnected = { device ->
            Log.d(tag, "CONNECTED: ${device.address}")
            _deviceName.value = device.name
            _hrFeature.value = HrFeature(true)
            _deviceConnectionState.value = DeviceConnectionState.Connected(device.address)
        }
        ble.onReconnecting = {
            Log.d(tag, "RECONNECTING")
            val address = _deviceConnectionState.value.address
            _hrFeature.value = HrFeature()
            _deviceConnectionState.value = DeviceConnectionState.Connecting(address)
        }
        ble.onNoHeartRateService = {
            Log.d(tag, "NO HEART RATE SERVICE")
            val address = _deviceConnectionState.value.address
            _hrFeature.value = HrFeature()
            _batteryStatusFeature.value = BatteryStatusFeature()
            _deviceConnectionState.value = DeviceConnectionState.Disconnected(address)
        }
        ble.onBattery = { level ->
            Log.d(tag, "BATTERY LEVEL: $level")
            _batteryStatusFeature.value = BatteryStatusFeature(true, level)
        }
    }

    fun searchForDevice() {
        Log.d(tag, "searchForDevice")
        // Demo mode offers the demo strap alone, so there is nothing to scan for.
        if (_demoEnabled.value) {
            _foundDevices.value = listOf(DEMO_DEVICE)
            return
        }
        _foundDevices.value = ble.bondedBands()
        ble.scan { device ->
            Log.d(tag, "found ${device.name} ${device.address}")
            val currentList = _foundDevices.value
            if (currentList.none { it.address == device.address }) {
                _foundDevices.value = currentList + device
            }
        }
    }

    fun stopDevicesSearch() {
        Log.d(tag, "stopDevicesSearch")
        ble.stopScan()
        _foundDevices.value = emptyList()
    }

    fun connectToDevice(device: BleDevice, onComplete: () -> Unit, onFailed: () -> Unit) {
        viewModelScope.launch {
            Log.d(tag, "connectToDevice")
            val state = _deviceConnectionState.value
            if (state is DeviceConnectionState.Connected && state.address == device.address) {
                onComplete()
                return@launch
            }

            if (device.address == DEMO_ADDRESS) {
                startDemo()
                onComplete()
                return@launch
            }

            ble.stopScan()
            _deviceConnectionState.value = DeviceConnectionState.Connecting(device.address)
            ble.connect(device.address)
            val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                _deviceConnectionState.first { it !is DeviceConnectionState.Connecting }
            }
            if (result is DeviceConnectionState.Connected) {
                onComplete()
            } else {
                ble.disconnect()
                _deviceConnectionState.value = DeviceConnectionState.Disconnected(device.address)
                onFailed()
            }
        }
    }

    fun hrStreamStart(address: String, onNext: (Int) -> Unit) {
        if (address == DEMO_ADDRESS) {
            demoHrStreamStart(onNext)
            return
        }
        ble.startHeartRate(onNext)
    }

    fun hrStreamStop() {
        ble.stopHeartRate()
        demoHrJob?.cancel()
    }

    /** Lasts until the process ends; from then on the demo strap is the only device offered. */
    fun enableDemo() {
        Log.d(tag, "enableDemo")
        _demoEnabled.value = true
        ble.stopScan()
        if (_foundDevices.value.isNotEmpty()) _foundDevices.value = listOf(DEMO_DEVICE)
    }

    /** Pretends a strap with HR and battery support has connected. */
    private fun startDemo() {
        Log.d(tag, "startDemo")
        _deviceName.value = DEMO_DEVICE_NAME
        _hrFeature.value = HrFeature(true)
        _batteryStatusFeature.value = BatteryStatusFeature(true, DEMO_BATTERY_LEVEL)
        _deviceConnectionState.value = DeviceConnectionState.Connected(DEMO_ADDRESS)
    }

    /**
     * One sample a second, like a real strap: a slow wave through 90..160 BPM with a little
     * jitter. With the default range a session starts good, then goes too high and too low.
     */
    private fun demoHrStreamStart(onNext: (Int) -> Unit) {
        if (demoHrJob?.isActive == true) return
        demoHrJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (true) {
                val t = (System.currentTimeMillis() - startedAt) / DEMO_HR_PERIOD_MS.toDouble()
                val hr = 125 + 35 * sin(2 * PI * t) + Random.nextInt(-2, 3)
                onNext(demoBpm ?: hr.roundToInt())
                delay(1000)
            }
        }
    }

    companion object {
        /** Replaces the wave of the demo while it is not null. The debug build sets it from adb. */
        @Volatile
        var demoBpm: Int? = null
        const val DEMO_ADDRESS = "demo"
        const val DEMO_DEVICE_NAME = "Heart Alert Demo"
        private const val CONNECT_TIMEOUT_MS = 20_000L
        private const val DEMO_BATTERY_LEVEL = 80
        private val DEMO_DEVICE = BleDevice(DEMO_ADDRESS, DEMO_DEVICE_NAME)
        private const val DEMO_HR_PERIOD_MS = 120_000L
    }
}