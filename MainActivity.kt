package com.nelson.navled

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        BleBridge.init(this)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "navled")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "permissions" -> {
                        val perms = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= 31) {
                            perms.add(Manifest.permission.BLUETOOTH_SCAN)
                            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        requestPermissions(perms.toTypedArray(), 1)
                        result.success(true)
                    }
                    "notifSettings" -> {
                        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        result.success(true)
                    }
                    "connect" -> { BleBridge.connect(); result.success(true) }
                    "gpsStart" -> {
                        val missing = Build.VERSION.SDK_INT >= 23 &&
                            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        if (missing) {
                            BleBridge.gpsStatus = "location permission needed: accept it, then tap Start GPS speed again"
                            requestPermissions(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2
                            )
                            result.success(false)
                        } else {
                            val i = Intent(this, GpsSpeedService::class.java)
                            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                            result.success(true)
                        }
                    }
                    "gpsStop" -> {
                        stopService(Intent(this, GpsSpeedService::class.java))
                        BleBridge.gpsStatus = "off"
                        result.success(true)
                    }
                    "send" -> {
                        result.success(
                            BleBridge.send(
                                call.argument<Int>("dir") ?: 0,
                                call.argument<Int>("dist") ?: 0,
                                true,
                                call.argument<Int>("speed") ?: 0,
                                call.argument<Int>("exit") ?: 0,
                                call.argument<Int>("warn") ?: 0,
                                call.argument<Int>("variant") ?: 0
                            )
                        )
                    }
                    "cmd" -> {
                        result.success(
                            BleBridge.sendCommand(call.argument<Int>("code") ?: 0, call.argument<Int>("value") ?: 0)
                        )
                    }
                    "speed" -> {
                        result.success(BleBridge.sendSpeed(call.argument<Int>("kmh") ?: 0))
                    }
                    "status" -> {
                        val enabled = if (Build.VERSION.SDK_INT >= 27) {
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.isNotificationListenerAccessGranted(ComponentName(this, NavListenerService::class.java))
                        } else {
                            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                                ?.contains(packageName) == true
                        }
                        val gpsFresh = System.currentTimeMillis() - BleBridge.gpsTime < 4000
                        val gps = if (gpsFresh) "${BleBridge.gpsKmh} km/h" else "no speed yet"
                        result.success(mapOf(
                            "ble" to BleBridge.status,
                            "connected" to BleBridge.isConnected(),
                            "raw" to BleBridge.lastRaw,
                            "notif" to enabled,
                            "gps" to gps,
                            "gpsStatus" to BleBridge.gpsStatus,
                            "log" to BleBridge.logText(),
                            "native" to "v7"
                        ))
                    }
                    else -> result.notImplemented()
                }
            }
    }
}

