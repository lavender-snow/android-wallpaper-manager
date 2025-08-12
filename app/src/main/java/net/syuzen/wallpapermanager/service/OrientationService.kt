package net.syuzen.wallpapermanager.service

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.Surface
import net.syuzen.wallpapermanager.R

class OrientationService : Service() {
    private lateinit var displayManager: DisplayManager
    private var lastIsPortrait: Boolean? = null

    public fun isPortrait(): Boolean {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return true
        val rotation = display.rotation

        return rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
    }

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return

            val currentOrientationIsPortrait = isPortrait()
            if (lastIsPortrait == null || lastIsPortrait != currentOrientationIsPortrait) {
                Log.v("OrientationService", "Orientation changed: isPortrait=$currentOrientationIsPortrait")
                lastIsPortrait = currentOrientationIsPortrait
                notifyOrientationChanged(currentOrientationIsPortrait)
            }
        }
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(listener, null)
        Log.v("OrientationService", "OrientationService started")
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "orientation_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Orientation Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Wallpaper Manager")
            .setContentText("画面の向きを監視中")
            .setSmallIcon(R.drawable.ic_kyomu_penguin_silhouette)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        displayManager.unregisterDisplayListener(listener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notifyOrientationChanged(isPortrait: Boolean) {
        val intent = Intent(this, net.syuzen.wallpapermanager.receiver.OrientationReceiver::class.java)
        intent.putExtra("isPortrait", isPortrait)
        sendBroadcast(intent)
    }
}