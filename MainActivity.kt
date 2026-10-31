package com.nelson.navled

import android.Manifest
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
                        val perms = if (Build.VERSION.SDK_INT >= 31)
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        requestPermissions(perms, 1)
                        result.success(null)
                    }
                    "notifSettings" -> {
                        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        result.success(null)
                    }
                    "connect" -> { BleBridge.connect(); result.success(null) }
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
                        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                            ?.contains(packageName) == true
                        result.success(mapOf("ble" to BleBridge.status, "raw" to BleBridge.lastRaw, "notif" to enabled))
                    }
                    else -> result.notImplemented()
                }
            }
    }
}

