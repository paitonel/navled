package com.nelson.navled

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder

// Foreground service: reads phone GPS speed once a second and sends it to the LED unit.
class GpsSpeedService : Service() {
    private var lm: LocationManager? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!location.hasSpeed()) return
            var kmh = Math.round(location.speed * 3.6f)
            if (kmh < 2) kmh = 0                      // GPS noise while standing still
            BleBridge.gpsKmh = kmh
            BleBridge.gpsTime = System.currentTimeMillis()
            BleBridge.sendSpeed(kmh)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BleBridge.init(this)
        BleBridge.connect()

        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("gps", "GPS speed", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "gps")
                      else Notification.Builder(this)
        val n = builder
            .setContentTitle("NavTurnLED")
            .setContentText("Sending GPS speed to the LED unit")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, n)

        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm = manager
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        } catch (e: SecurityException) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { lm?.removeUpdates(listener) } catch (e: Exception) {}
        BleBridge.gpsTime = 0
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
