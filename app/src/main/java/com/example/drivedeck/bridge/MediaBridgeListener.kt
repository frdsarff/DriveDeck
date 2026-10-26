package com.example.drivedeck.bridge

import android.service.notification.NotificationListenerService

/**
 * Empty on purpose. Android only lets an app read other apps' media sessions
 * if it owns an enabled NotificationListenerService.
 * When the user grants access, onListenerConnected() kicks the bridge on.
 */
class MediaBridgeListener : NotificationListenerService() {
    override fun onListenerConnected() {
        MediaBridge.start(applicationContext)
    }
}
