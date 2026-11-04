package com.nikashitsa.polar_alert_android.lib

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.StringRes
import com.nikashitsa.polar_alert_android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The link between the two phones of a room, with a GATT service of the app. The host advertises
 * the code and is the GATT server. The phone that joins scans for the code and is the GATT client.
 * After a loss of the link, the host advertises again and the other phone scans again.
 * Every callback comes on the main thread. The callers check the Bluetooth permissions.
 */
@SuppressLint("MissingPermission")
@Singleton
class RoomBle @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val tag = "RoomBle"
    private val main = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter = manager.adapter

    var onState: (ByteArray) -> Unit = {}
    var onLost: () -> Unit = {}
    /** A failure of BLE, with the text of the room section and the code of Android. */
    var onError: (Int, Int) -> Unit = { _, _ -> }

    // Each host() and join() starts a session. A late callback of an old session does nothing.
    private var session = 0
    private var code = ""
    private var hosting = false
    private var server: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null
    private var partner: BluetoothDevice? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var clientCharacteristic: BluetoothGattCharacteristic? = null

    fun canHost(): Boolean = adapter.isMultipleAdvertisementSupported && adapter.bluetoothLeAdvertiser != null

    fun host(code: String) {
        stop()
        this.code = code
        hosting = true
        val mySession = session
        val callback = object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService) = onMain(mySession) {
                if (status == BluetoothGatt.GATT_SUCCESS) advertise() else fail(R.string.room_error_server, status)
            }

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) = onMain(mySession) {
                Log.d(tag, "server: ${device.address} state $newState, status $status")
                if (newState != BluetoothProfile.STATE_CONNECTED && device == partner) {
                    partner = null
                    onLost()
                    advertise()
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
            ) {
                if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                onMain(mySession) { claim(device) }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
            ) {
                if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                onMain(mySession) { if (claim(device)) onState(value) }
            }
        }
        val s = manager.openGattServer(context, callback) ?: return failOrWait(R.string.room_error_server)
        server = s
        val characteristic = BluetoothGattCharacteristic(
            STATE,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(
            BluetoothGattDescriptor(CLIENT_CONFIG, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        )
        serverCharacteristic = characteristic
        s.addService(BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply { addCharacteristic(characteristic) })
    }

    /**
     * The GATT server hears the connection of each device of the phone, the strap too. So the
     * partner is the phone that subscribes or writes, and not the first device that connects.
     * A third phone gets no room.
     */
    private fun claim(device: BluetoothDevice): Boolean {
        if (partner == null) {
            partner = device
            stopAdvertising()
        }
        if (device != partner) server?.cancelConnection(device)
        return device == partner
    }

    private fun advertise() {
        val advertiser = adapter.bluetoothLeAdvertiser ?: return failOrWait(R.string.room_error_advertise)
        stopAdvertising()
        val mySession = session
        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) = onMain(mySession) {
                if (advertiseCallback !== this) return@onMain
                advertiseCallback = null
                fail(R.string.room_error_advertise, errorCode)
            }
        }
        advertiser.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .build(),
            AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(roomUuid(code)))
                .build(),
            callback,
        )
        advertiseCallback = callback
    }

    private fun stopAdvertising() {
        advertiseCallback?.let { adapter.bluetoothLeAdvertiser?.stopAdvertising(it) }
        advertiseCallback = null
    }

    fun join(code: String) {
        stop()
        this.code = code
        hosting = false
        scan()
    }

    private fun scan() {
        val scanner = adapter.bluetoothLeScanner ?: return failOrWait(R.string.room_error_scan)
        val mySession = session
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onMain(mySession) {
                if (scanCallback !== this) return@onMain
                stopScan()
                Log.d(tag, "client: found the room in ${result.device.address}")
                gatt = result.device.connectGatt(context, false, gattCallback(mySession), BluetoothDevice.TRANSPORT_LE)
            }

            override fun onScanFailed(errorCode: Int) = onMain(mySession) { fail(R.string.room_error_scan, errorCode) }
        }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(roomUuid(code))).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        scanCallback = callback
    }

    private fun stopScan() {
        scanCallback?.let { adapter.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
    }

    private fun gattCallback(mySession: Int) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = onMain(mySession) {
            Log.d(tag, "client: state $newState, status $status")
            if (g !== gatt) return@onMain g.close()
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
                return@onMain
            }
            g.close()
            gatt = null
            clientCharacteristic = null
            onLost()
            scan()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = onMain(mySession) {
            val characteristic = g.getService(SERVICE)?.getCharacteristic(STATE)
            val cccd = characteristic?.getDescriptor(CLIENT_CONFIG) ?: return@onMain g.disconnect()
            g.setCharacteristicNotification(characteristic, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        // The writes of the state start after the subscription, so two operations never overlap.
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = onMain(mySession) {
            clientCharacteristic = g.getService(SERVICE)?.getCharacteristic(STATE)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) =
            onMain(mySession) { onState(value) }

        // Android 12 calls this version only. Android 13 and later call the version with the value.
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            onCharacteristicChanged(g, characteristic, value.copyOf())
        }
    }

    /**
     * Sends the state of my runner. A write or a notification that finds the link busy is lost:
     * the next second sends a newer state.
     */
    fun send(value: ByteArray) {
        // A Bluetooth that turns off kills the link without a disconnection.
        val live = server != null || gatt != null || scanCallback != null
        val failure = runCatching { write(value) }.exceptionOrNull()
        if (live && (failure != null || !adapter.isEnabled)) {
            Log.w(tag, "send: Bluetooth off, $failure")
            onLost()
            waitForBluetooth()
        }
    }

    private fun write(value: ByteArray) {
        val device = partner
        val s = server
        val sc = serverCharacteristic
        if (device != null && s != null && sc != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                s.notifyCharacteristicChanged(device, sc, false, value)
            } else {
                @Suppress("DEPRECATION")
                sc.value = value
                @Suppress("DEPRECATION")
                s.notifyCharacteristicChanged(device, sc, false)
            }
        }
        val g = gatt
        val cc = clientCharacteristic
        if (g != null && cc != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(cc, value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                cc.value = value
                cc.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                g.writeCharacteristic(cc)
            }
        }
    }

    /** Ends the session. A [last] state goes first, and the link closes a moment later. */
    fun stop(last: ByteArray? = null) {
        last?.let { runCatching { write(it) } }
        session++
        stopScan()
        stopAdvertising()
        val g = gatt
        val s = server
        gatt = null
        clientCharacteristic = null
        server = null
        serverCharacteristic = null
        partner = null
        main.postDelayed({
            g?.disconnect()
            g?.close()
            s?.close()
        }, if (last != null) 500L else 0L)
    }

    private fun failOrWait(@StringRes text: Int) = if (adapter.isEnabled) fail(text, -1) else waitForBluetooth()

    /** The room keeps its code and its role, and starts again when Bluetooth is on. */
    private fun waitForBluetooth() {
        stop()
        val mySession = session
        main.postDelayed({
            if (mySession != session) return@postDelayed
            if (!adapter.isEnabled) waitForBluetooth() else if (hosting) host(code) else join(code)
        }, 1000L)
    }

    private fun fail(@StringRes text: Int, errorCode: Int) {
        Log.e(tag, "${context.getString(text, errorCode)}")
        stop()
        onError(text, errorCode)
    }

    private fun onMain(mySession: Int, block: () -> Unit) {
        main.post { if (mySession == session) block() }
    }

    private companion object {
        val SERVICE: UUID = UUID.fromString("5f1a0000-6b4e-4c1d-9a39-2f6c8d1e7a00")
        val STATE: UUID = UUID.fromString("5f1a0002-6b4e-4c1d-9a39-2f6c8d1e7a00")
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** The code of the room goes in the advertised UUID, so a scan filter finds it. */
        fun roomUuid(code: String): UUID = UUID.fromString("5f1a0001-6b4e-4c1d-9a39-00000000$code")
    }
}
