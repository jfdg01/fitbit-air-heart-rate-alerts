package com.nikashitsa.polar_alert_android.lib

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BleDevice(val address: String, val name: String)

/** Android delivers the value of the standard Heart Rate Measurement characteristic (2A37) as bytes. */
fun parseHeartRate(value: ByteArray): Int? {
    val flags = value.getOrNull(0)?.toInt() ?: return null
    val low = value.getOrNull(1)?.toInt()?.and(0xFF) ?: return null
    if (flags and 1 == 0) return low
    val high = value.getOrNull(2)?.toInt()?.and(0xFF) ?: return null
    return low or (high shl 8)
}

/**
 * Talks to any band or strap that has the standard Heart Rate Service, with the Android API alone.
 * The callers check the Bluetooth permissions before they call scan or connect.
 */
@SuppressLint("MissingPermission")
@Singleton
class HeartRateBle @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val tag = "HeartRateBle"
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter =
        context.getSystemService(BluetoothManager::class.java).adapter

    private val _isOn = MutableStateFlow(adapter.isEnabled)
    val isOn = _isOn.asStateFlow()

    var onConnected: (BleDevice) -> Unit = {}
    var onReconnecting: () -> Unit = {}
    var onNoHeartRateService: () -> Unit = {}
    var onBattery: (Int) -> Unit = {}

    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var wanted: BluetoothDevice? = null
    private var onHeartRate: ((Int) -> Unit)? = null

    // Android runs one GATT operation at a time, so each operation waits for the end of the last.
    private val operations = ArrayDeque<(BluetoothGatt) -> Boolean>()
    private var operationRunning = false

    init {
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    _isOn.value = adapter.isEnabled
                    val device = wanted ?: return
                    // Android gives no connection callback when Bluetooth turns off.
                    gatt?.close()
                    gatt = null
                    resetOperations()
                    if (adapter.isEnabled) open(device, autoConnect = true) else onReconnecting()
                }
            },
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** A Fitbit band that shares its heart rate stops its advertisements, so a scan cannot find it. */
    fun bondedBands(): List<BleDevice> = adapter.bondedDevices
        .filter { it.name?.contains("Fitbit", ignoreCase = true) == true }
        .map { BleDevice(it.address, it.name) }

    fun scan(onFound: (BleDevice) -> Unit) {
        stopScan()
        val scanner = adapter.bluetoothLeScanner ?: return
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                onFound(BleDevice(result.device.address, name))
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(tag, "Scan failed. Code $errorCode")
            }
        }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        scanCallback = callback
    }

    fun stopScan() {
        scanCallback?.let { adapter.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
    }

    fun connect(address: String) {
        disconnect()
        val device = adapter.getRemoteDevice(address)
        wanted = device
        open(device, autoConnect = false)
    }

    fun disconnect() {
        wanted = null
        onHeartRate = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        resetOperations()
    }

    fun startHeartRate(onNext: (Int) -> Unit) {
        onHeartRate = onNext
        setHeartRateNotifications(true)
    }

    fun stopHeartRate() {
        onHeartRate = null
        setHeartRateNotifications(false)
    }

    private fun open(device: BluetoothDevice, autoConnect: Boolean) {
        gatt = device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun setHeartRateNotifications(enabled: Boolean) = enqueue { g ->
        val characteristic = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
        val cccd = characteristic?.getDescriptor(CLIENT_CONFIG)
        if (cccd == null) {
            false
        } else {
            g.setCharacteristicNotification(characteristic, enabled)
            val value = if (enabled) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            g.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
        }
    }

    @Synchronized
    private fun enqueue(operation: (BluetoothGatt) -> Boolean) {
        operations.addLast(operation)
        runNext()
    }

    @Synchronized
    private fun operationDone() {
        operationRunning = false
        runNext()
    }

    @Synchronized
    private fun runNext() {
        val g = gatt ?: return
        while (!operationRunning) {
            val next = operations.removeFirstOrNull() ?: return
            operationRunning = next(g)
        }
    }

    @Synchronized
    private fun resetOperations() {
        operations.clear()
        operationRunning = false
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(tag, "Connection state $newState, status $status")
            if (wanted == null) {
                g.close()
                return
            }
            // A band that already has a link to the phone can call back before connectGatt returns.
            gatt = g
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
                return
            }
            g.close()
            gatt = null
            resetOperations()
            val device = wanted ?: return
            onReconnecting()
            // Auto connect has no timeout, so the link returns when the band is back in range.
            open(device, autoConnect = true)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.d(tag, "Services ${g.services.map { it.uuid }}, status $status")
            if (g.getService(HR_SERVICE) == null) {
                wanted = null
                g.disconnect()
                onNoHeartRateService()
                return
            }
            onConnected(BleDevice(g.device.address, g.device.name ?: g.device.address))
            enqueue { current ->
                val battery = current.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)
                battery != null && current.readCharacteristic(battery)
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (characteristic.uuid == BATTERY_LEVEL && status == BluetoothGatt.GATT_SUCCESS) {
                value.firstOrNull()?.let { onBattery(it.toInt() and 0xFF) }
            }
            operationDone()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(tag, "Notification setting written, status $status")
            operationDone()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid != HR_MEASUREMENT) return
            // A band that is off the wrist can send 0. That is no reading, not a low heart rate.
            val bpm = parseHeartRate(value)?.takeIf { it > 0 } ?: return
            main.post { onHeartRate?.invoke(bpm) }
        }
    }

    private companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }
}
