package net.syuzen.wallpapermanager

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.switchmaterial.SwitchMaterial
import net.syuzen.wallpapermanager.service.OrientationService
import net.syuzen.wallpapermanager.utils.Const.LANDSCAPE_URI_KEY
import net.syuzen.wallpapermanager.utils.Const.PORTRAIT_URI_KEY
import net.syuzen.wallpapermanager.utils.Const.SHARED_PREFS_NAME
import net.syuzen.wallpapermanager.utils.ImagePickerHelper
import net.syuzen.wallpapermanager.utils.WallpaperUtil.setCustomWallpaper
import net.syuzen.wallpapermanager.utils.loadValue
import net.syuzen.wallpapermanager.utils.saveValue

enum class WallpaperType {
    PORTRAIT,
    LANDSCAPE,
    PORTRAIT_LOCK,
    LANDSCAPE_LOCK,
}

class MainActivity : AppCompatActivity() {
    companion object {
        var portraitUri : Uri? = null
        var landscapeUri : Uri? = null
        var wallpaperManagerEnabled = false
    }

    private fun getCurrentWallpaperType(): WallpaperType {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            WallpaperType.PORTRAIT
        } else {
            WallpaperType.LANDSCAPE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // レイアウトファイルを指定

        // 通知権限の確認とリクエスト
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (this.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                this.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        portraitUri = loadValue(this, SHARED_PREFS_NAME, PORTRAIT_URI_KEY, "string")?.toUri()
        landscapeUri = loadValue(this, SHARED_PREFS_NAME, LANDSCAPE_URI_KEY, "string")?.toUri()

        val portraitImage: ImageView = findViewById(R.id.portraitImageView)
        val landscapeImage: ImageView = findViewById(R.id.landscapeImageView)

        if (portraitUri != null) {
            portraitImage.setImageURI(portraitUri)
        }

        if (landscapeUri != null) {
            landscapeImage.setImageURI(landscapeUri)
        }

        val setPortraitWallpaperButton: Button = findViewById(R.id.setPortraitWallpaperButton)
        val setLandscapeWallpaperButton: Button = findViewById(R.id.setLandscapeWallpaperButton)

        val portraitImagePickerHelper = ImagePickerHelper(
            activity = this,
            onCropped = { uri ->
                portraitUri = uri
                saveValue(this, SHARED_PREFS_NAME, PORTRAIT_URI_KEY, uri.toString())
                portraitImage.setImageURI(uri)
                updateWallpaper()
                Toast.makeText(this, "縦向きの壁紙の設定が完了しました", Toast.LENGTH_SHORT).show()
            },
            isPortrait = true
        )

        val landscapeImagePickerHelper = ImagePickerHelper(
            activity = this,
            onCropped = { uri ->
                landscapeUri = uri
                saveValue(this, SHARED_PREFS_NAME, LANDSCAPE_URI_KEY, uri.toString())
                landscapeImage.setImageURI(uri)
                updateWallpaper()
                Toast.makeText(this, "横向きの壁紙の設定が完了しました", Toast.LENGTH_SHORT).show()
            },
            isPortrait = false
        )

        setPortraitWallpaperButton.setOnClickListener {
            portraitImagePickerHelper.pickImage()
        }

        setLandscapeWallpaperButton.setOnClickListener {
            landscapeImagePickerHelper.pickImage()
        }

        wallpaperManagerEnabled = loadValue(this, SHARED_PREFS_NAME, "wallpaper_manager_enabled", false) ?: false
        val wallpaperManagerEnabledSwitch = findViewById<SwitchMaterial>(R.id.switch1)

        wallpaperManagerEnabledSwitch.isChecked = wallpaperManagerEnabled

        val intent = Intent(this, OrientationService::class.java)

        if (wallpaperManagerEnabled) {
            startForegroundService(intent)
            Log.v("MainActivity", "WallpaperManager is enabled, starting service and foreground service")

            updateWallpaper()
        }

        wallpaperManagerEnabledSwitch?.setOnCheckedChangeListener { _, isChecked ->
            saveValue(this, SHARED_PREFS_NAME, "wallpaper_manager_enabled", isChecked)
            if (isChecked) {
                startForegroundService(intent)
                updateWallpaper()
            } else {
                stopService(intent)
            }
        }
    }

    private fun updateWallpaper() {
        if (getCurrentWallpaperType() == WallpaperType.PORTRAIT) {
            val currentPortraitUri = portraitUri

            if (currentPortraitUri != null) {
                setCustomWallpaper(this, currentPortraitUri)
            }
        } else {
            val currentLandscapeUri = landscapeUri

            if (currentLandscapeUri != null) {
                setCustomWallpaper(this, currentLandscapeUri)
            }
        }
    }
}