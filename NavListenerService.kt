package com.nelson.navled

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NavListenerService : NotificationListenerService() {
    private val navApps = setOf("com.google.android.apps.maps", "com.waze")

    // Speed estimate from how fast the distance to the next turn shrinks.
    private var estKmh = 0.0
    private var prevDist = -1
    private var prevMode = -1
    private var prevT = 0L

    override fun onListenerConnected() {
        BleBridge.init(this)
        BleBridge.connect()
    }

    private fun updateSpeed(mode: Int, dist: Int) {
        if (mode !in 1..7) return
        val now = System.currentTimeMillis()
        if (prevDist < 0 || mode != prevMode) {
            prevDist = dist; prevMode = mode; prevT = now
            return
        }
        if (dist == prevDist) return
        if (dist < prevDist) {
            val dt = (now - prevT) / 1000.0
            if (dt in 0.5..30.0) {
                val v = (prevDist - dist) / dt * 3.6
                if (v in 5.0..160.0) estKmh = if (estKmh == 0.0) v else 0.6 * estKmh + 0.4 * v
            }
        }
        prevDist = dist
        prevT = now
    }

    private fun resetSpeed() {
        estKmh = 0.0; prevDist = -1; prevMode = -1
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in navApps) return
        BleBridge.init(this)
        BleBridge.connect()
        val e = sbn.notification.extras
        val keys = listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT
        )
        val text = keys.joinToString(" | ") { e.getCharSequence(it)?.toString() ?: "" }
        BleBridge.lastRaw = "[${sbn.packageName.substringAfterLast('.')}] $text"
        val p = NavParser.parse(text) ?: return
        updateSpeed(p.first, p.second)
        BleBridge.send(p.first, p.second, false, estKmh.toInt())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in navApps) {
            resetSpeed()
            BleBridge.send(0, 0, true)
        }
    }
}

