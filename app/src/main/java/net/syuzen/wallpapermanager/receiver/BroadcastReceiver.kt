package net.syuzen.wallpapermanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import net.syuzen.wallpapermanager.utils.loadValue
import net.syuzen.wallpapermanager.utils.Const.SHARED_PREFS_NAME
import net.syuzen.wallpapermanager.utils.Const.PORTRAIT_URI_KEY
import net.syuzen.wallpapermanager.utils.Const.LANDSCAPE_URI_KEY
import net.syuzen.wallpapermanager.utils.WallpaperUtil.setCustomWallpaper

class OrientationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val isPortrait = intent?.getBooleanExtra("isPortrait", true) ?: true
        val key = if (isPortrait) PORTRAIT_URI_KEY else LANDSCAPE_URI_KEY
        val uri = loadValue(context, SHARED_PREFS_NAME, key, "")?.toUri()

        if (uri != null) {
            setCustomWallpaper(context, uri)
        }
    }
}