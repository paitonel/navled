package com.nelson.navled

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NavListenerService : NotificationListenerService() {
    private val MAPS = "com.google.android.apps.maps"

    override fun onListenerConnected() {
        BleBridge.init(this)
        BleBridge.connect()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != MAPS) return
        BleBridge.init(this)
        BleBridge.connect()
        val e = sbn.notification.extras
        val keys = listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT
        )
        val text = keys.joinToString(" | ") { e.getCharSequence(it)?.toString() ?: "" }
        BleBridge.lastRaw = text
        val p = NavParser.parse(text) ?: return
        BleBridge.send(p.first, p.second)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS) BleBridge.send(0, 0, true)
    }
}
