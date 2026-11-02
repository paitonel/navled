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
    private var started = false
    private var misses = 0

    @Volatile var status = "Not connected"
    @Volatile var lastRaw = "(no navigation notification seen yet)"
    @Volatile var gpsKmh = 0
    @Volatile var gpsTime = 0L
    @Volatile var gpsStatus = "off"
    private var lastDir = -1
    private var lastDist = -1
    private var lastSend = 0L
    private var lastWarn = 0
    private val logs = ArrayDeque<String>()

    // The unit clears itself after 15 s without data, so while a turn is active
    // the last packet is resent every 5 s (for at most 2 minutes without a new notification).
    private var lastPacket: ByteArray? = null
    private var lastPacketTime = 0L
    private val keepAlive = object : Runnable {
        override fun run() {
            val p = lastPacket
            if (p != null) {
                if (System.currentTimeMillis() - lastPacketTime > 120000) lastPacket = null
                else write(p)
            }
            main.postDelayed(this, 5000)
        }
    }

    private val retryConnect = Runnable { connect() }

    // Keeps trying: every 3 s at first, every 15 s after several misses
    // (for example while the unit is asleep). Wake the unit and it reconnects by itself.
    private fun scheduleRetry() {
        main.removeCallbacks(retryConnect)
        main.postDelayed(retryConnect, if (misses < 5) 3000L else 15000L)
    }

    fun init(c: Context) {
        ctx = c.applicationContext
        if (!started) { started = true; main.postDelayed(keepAlive, 5000) }
    }

    fun isConnected(): Boolean = gatt != null && ch != null

    fun connect() {
        val c = ctx ?: return
        if (gatt != null || scanning) return
        try {
            val adapter = (c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if (adapter == null || !adapter.isEnabled) {
                status = "Bluetooth is off"
                scheduleRetry()
                return
            }
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) { scheduleRetry(); return }
            scanning = true
            status = "Scanning ..."
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
            val cb = object : ScanCallback() {
                override fun onScanResult(type: Int, r: ScanResult) {
                    if (!scanning) return
                    scanning = false
                    scanner.stopScan(this)
                    status = "Connecting ..."
                    gatt = r.device.connectGatt(c, false, gattCb, BluetoothDevice.TRANSPORT_LE)
                }
                override fun onScanFailed(code: Int) {
                    scanning = false
                    status = "Scan failed ($code), retrying"
                    scheduleRetry()
                }
            }
            scanner.startScan(listOf(filter), settings, cb)
            main.postDelayed({
                if (scanning) {
                    scanning = false
                    misses++
                    try { scanner.stopScan(cb) } catch (e: Exception) {}
                    status = "NavTurnLED not found (powered or asleep?), still looking"
                    scheduleRetry()
                }
            }, 10000)
        } catch (e: SecurityException) {
            scanning = false
            status = "Bluetooth permission missing: tap 1. Allow Bluetooth"
            scheduleRetry()
        }
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, s: Int, n: Int) {
            if (n == BluetoothProfile.STATE_CONNECTED) {
                status = "Discovering ..."
                g.discoverServices()
            } else {
                ch = null
                g.close()
                gatt = null
                status = "Disconnected, reconnecting ..."
                scheduleRetry()
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, s: Int) {
            ch = g.getService(SERVICE)?.getCharacteristic(CHAR)
            if (ch != null) { misses = 0; status = "Connected" } else status = "Characteristic not found"
        }
    }

    // true = the packet was handed to Bluetooth, false = not connected or Bluetooth refused it
    @Synchronized
    private fun write(data: ByteArray): Boolean {
        val g = gatt ?: return false
        val c = ch ?: return false
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        c.value = data
        return g.writeCharacteristic(c)
    }

    // dir: 0 none, 1 left, 2 right, 3 straight, 4 u-turn, 5-7 roundabout, 8 arrived,
    // 9 faster route, 10 hazard, 13/14 keep left/right, 15/16 merge left/right.
    // dist in metres, speed in km/h (0 = unknown), exit = roundabout exit number,
    // warn = 1 police, 2 camera, 3 pothole, 4 other, 5 accident, 6 roadworks, 7 traffic,
    // variant = 0 normal, 1 slight, 2 sharp.
    fun send(dir: Int, dist: Int, force: Boolean = false, speed: Int = 0, exit: Int = 0, warn: Int = 0, variant: Int = 0): Boolean {
        if (gatt == null || ch == null) return false
        val d = dist.coerceIn(0, 65535)
        val sp = speed.coerceIn(0, 255)
        val now = System.currentTimeMillis()
        val oneShot = dir in 8..10
        if (!force && oneShot && dir == lastDir && warn == lastWarn && now - lastSend < 30000) return true
        if (!force && !oneShot && dir == lastDir && Math.abs(d - lastDist) < 10 && now - lastSend < 2000) return true
        lastDir = dir; lastDist = d; lastSend = now; lastWarn = warn
        val pkt = byteArrayOf(
            dir.toByte(), (d shr 8).toByte(), (d and 0xFF).toByte(), sp.toByte(),
            exit.coerceIn(0, 9).toByte(), warn.coerceIn(0, 7).toByte(), variant.coerceIn(0, 2).toByte()
        )
        lastPacket = if (!force && (dir in 1..7 || dir in 13..16)) pkt else null
        lastPacketTime = now
        return write(pkt)
    }

    // 20 unit ON, 21 unit OFF, 22 buzzer ON, 23 buzzer OFF, 24 speed limit (value km/h, 0 = off)
    fun sendCommand(code: Int, value: Int = 0): Boolean =
        write(byteArrayOf(code.toByte(), 0, 0, value.coerceIn(0, 255).toByte()))

    // 12 = speed-only packet. bearing in degrees (0-359) or -1 when unknown (sent as bearing / 2).
    fun sendSpeed(kmh: Int, bearing: Int = -1): Boolean {
        val h = if (bearing in 0..359) bearing / 2 else 255
        return write(byteArrayOf(12, 0, 0, kmh.coerceIn(0, 255).toByte(), h.toByte(), 0))
    }

    @Synchronized
    fun addLog(line: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        logs.addFirst("$t  $line")
        while (logs.size > 20) logs.removeLast()
    }

    @Synchronized
    fun logText(): String = logs.joinToString("\n")
}

