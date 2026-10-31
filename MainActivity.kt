package com.nelson.navled

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
                        result.success(null)
                    }
                    "notifSettings" -> {
                        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        result.success(null)
                    }
                    "connect" -> { BleBridge.connect(); result.success(null) }
                    "gpsStart" -> {
                        val i = Intent(this, GpsSpeedService::class.java)
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                        result.success(null)
                    }
                    "gpsStop" -> {
                        stopService(Intent(this, GpsSpeedService::class.java))
                        result.success(null)
                    }
                    "send" -> {
                        BleBridge.send(
                            call.argument<Int>("dir") ?: 0,
                            call.argument<Int>("dist") ?: 0,
                            true,
                            call.argument<Int>("speed") ?: 0
                        )
                        result.success(null)
                    }
                    "cmd" -> {
                        BleBridge.sendCommand(call.argument<Int>("code") ?: 0)
                        result.success(null)
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
                        val gps = if (gpsFresh) "${BleBridge.gpsKmh} km/h" else "off / no fix yet"
                        result.success(mapOf(
                            "ble" to BleBridge.status,
                            "raw" to BleBridge.lastRaw,
                            "notif" to enabled,
                            "gps" to gps
                        ))
                    }
                    else -> result.notImplemented()
                }
            }
    }
}

