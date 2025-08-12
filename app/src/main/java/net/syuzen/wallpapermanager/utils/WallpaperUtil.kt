package net.syuzen.wallpapermanager.utils

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.io.IOException

object WallpaperUtil {
    private val wallpaperCache = HashMap<Uri, ByteArray>()

    fun setCustomWallpaper(
        context: Context,
        uri: Uri
    ) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        try {
            val data = wallpaperCache[uri] ?: run {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    WallpaperUtil.wallpaperCache[uri] = bytes
                    bytes
                } ?: throw IOException("Failed to open input stream")
            }
            data.inputStream().use { input ->
                wallpaperManager.setStream(input, null, false)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to set wallpaper.", Toast.LENGTH_SHORT).show()
        }
    }
}