package com.nelson.navled

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

@SuppressLint("MissingPermission")
object BleBridge {
    private val SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val CHAR: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var gatt: BluetoothGatt? = null
    private var ch: BluetoothGattCharacteristic? = null
    private var scanning = false

    @Volatile var status = "Not connected"
    @Volatile var lastRaw = "(no Google Maps notification seen yet)"
    private var lastDir = -1
    private var lastDist = -1
    private var lastSend = 0L

    fun init(c: Context) { ctx = c.applicationContext }

    fun connect() {
        val c = ctx ?: return
        if (gatt != null || scanning) return
        try {
            val adapter = (c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if (adapter == null || !adapter.isEnabled) { status = "Bluetooth is off"; return }
            val scanner = adapter.bluetoothLeScanner ?: return
            scanning = true
            status = "Scanning..."
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            val cb = object : ScanCallback() {
                override fun onScanResult(type: Int, r: ScanResult) {
                    if (!scanning) return
                    scanning = false
                    scanner.stopScan(this)
                    status = "Connecting..."
                    gatt = r.device.connectGatt(c, false, gattCb, BluetoothDevice.TRANSPORT_LE)
                }
                override fun onScanFailed(code: Int) { scanning = false; status = "Scan failed ($code)" }
            }
            scanner.startScan(listOf(filter), settings, cb)
            main.postDelayed({
                if (scanning) {
                    scanning = false
                    try { scanner.stopScan(cb) } catch (e: Exception) {}
                    status = "NavTurnLED not found (is it powered?)"
                }
            }, 10000)
        } catch (e: SecurityException) {
            scanning = false
            status = "Bluetooth permission missing"
        }
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, s: Int, n: Int) {
            if (n == BluetoothProfile.STATE_CONNECTED) {
                status = "Discovering..."
                g.discoverServices()
            } else {
                ch = null
                g.close()
                gatt = null
                status = "Disconnected, retrying..."
                main.postDelayed({ connect() }, 3000)
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, s: Int) {
            ch = g.getService(SERVICE)?.getCharacteristic(CHAR)
            status = if (ch != null) "Connected" else "Characteristic not found"
        }
    }

    // dir: 0 none, 1 left, 2 right, 3 straight, 4 u-turn. dist in metres.
    fun send(dir: Int, dist: Int, force: Boolean = false) {
        val g = gatt ?: return
        val c = ch ?: return
        val d = dist.coerceIn(0, 65535)
        val now = System.currentTimeMillis()
        if (!force && dir == lastDir && Math.abs(d - lastDist) < 10 && now - lastSend < 2000) return
        lastDir = dir; lastDist = d; lastSend = now
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        c.value = byteArrayOf(dir.toByte(), (d shr 8).toByte(), (d and 0xFF).toByte())
        g.writeCharacteristic(c)
    }
}
