package net.syuzen.wallpapermanager

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import net.syuzen.wallpapermanager.service.OrientationService
import net.syuzen.wallpapermanager.utils.getAspectForUCrop
import net.syuzen.wallpapermanager.utils.getScreenSizePx
import net.syuzen.wallpapermanager.utils.loadValue
import net.syuzen.wallpapermanager.utils.makeLetterboxedImage
import net.syuzen.wallpapermanager.utils.saveValue
import net.syuzen.wallpapermanager.utils.Const.SHARED_PREFS_NAME
import net.syuzen.wallpapermanager.utils.Const.PORTRAIT_URI_KEY
import net.syuzen.wallpapermanager.utils.Const.LANDSCAPE_URI_KEY
import java.io.File
import java.io.IOException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.text.compareTo


private const val REQUEST_CROP_PORTRAIT = UCrop.REQUEST_CROP + 1
private const val REQUEST_CROP_LANDSCAPE = UCrop.REQUEST_CROP + 2

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // レイアウトファイルを指定

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

        setPortraitWallpaperButton.setOnClickListener {
            pickPortraitImage.launch(arrayOf("image/jpeg", "image/png", "image/jpg", "image/webp"))
        }

        setLandscapeWallpaperButton.setOnClickListener {
            pickLandscapeImage.launch(arrayOf("image/jpeg", "image/png", "image/jpg", "image/webp"))
        }

        wallpaperManagerEnabled = loadValue(this, SHARED_PREFS_NAME, "wallpaper_manager_enabled", false) ?: false
        val wallpaperManagerEnabledSwitch = findViewById<SwitchMaterial>(R.id.switch1)

        wallpaperManagerEnabledSwitch.isChecked = wallpaperManagerEnabled

        val intent = Intent(this, OrientationService::class.java)

        wallpaperManagerEnabledSwitch?.setOnCheckedChangeListener { _, isChecked ->
            saveValue(this, SHARED_PREFS_NAME, "wallpaper_manager_enabled", isChecked)
            if (isChecked) {
                startForegroundService(intent)
                setCustomWallpaper(
                    if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        WallpaperType.PORTRAIT
                    } else {
                        WallpaperType.LANDSCAPE
                    }
                )
            } else {
                stopService(intent)
            }
        }

        if (wallpaperManagerEnabled) {
            startForegroundService(intent)
        }
    }

    private val pickPortraitImage = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        lifecycleScope.launch {
            startCrop(uri, REQUEST_CROP_PORTRAIT)
        }
    }

    private val pickLandscapeImage = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        lifecycleScope.launch {
            startCrop(uri, REQUEST_CROP_LANDSCAPE)
        }
    }

    private suspend fun startCrop(input: Uri, requestCode: Int) {

        val outFile = File.createTempFile("cropped_", ".jpg", cacheDir)
        val outUri = FileProvider.getUriForFile(
            this, "${packageName}.fileprovider", outFile
        )

        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(false)      // 自由トリミングON
            setHideBottomControls(false)       // 回転等のUIを表示
            setCompressionQuality(95)
        }

        val size = getScreenSizePx(this)
        val (ratioX, ratioY) = getAspectForUCrop(this, true)

        if (requestCode == REQUEST_CROP_PORTRAIT) {
            val paddedUri = makeLetterboxedImage(this, input, ratioX.toInt(), ratioY.toInt(), size.width, size.height)
            UCrop.of(paddedUri, outUri)
                .withAspectRatio(ratioX, ratioY)
                .withOptions(options)
                .start(this, requestCode)
        } else {
            val paddedUri = makeLetterboxedImage(this, input, ratioY.toInt(), ratioX.toInt(), size.width, size.height)
            UCrop.of(paddedUri, outUri)
                .withAspectRatio(ratioY, ratioX)
                .withOptions(options)
                .start(this, requestCode)
        }


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CROP_PORTRAIT) {
            val resultUri = UCrop.getOutput(data!!) ?: return
            MainActivity.portraitUri = resultUri
            saveValue(this, SHARED_PREFS_NAME, PORTRAIT_URI_KEY, resultUri.toString())
            val portraitImage: ImageView = findViewById(R.id.portraitImageView)
            portraitImage.setImageURI(resultUri) // 画像ビューも更新

            // スイッチがONで、かつ現在の向きがポートレートなら壁紙を更新
            if (wallpaperManagerEnabled && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                setCustomWallpaper(WallpaperType.PORTRAIT)
            }
            Toast.makeText(this, "縦向きの壁紙の設定が完了しました", Toast.LENGTH_SHORT).show()
        } else if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CROP_LANDSCAPE) {
            val resultUri = UCrop.getOutput(data!!) ?: return
            MainActivity.landscapeUri = resultUri
            saveValue(this, SHARED_PREFS_NAME, LANDSCAPE_URI_KEY, resultUri.toString())
            val landscapeImage: ImageView = findViewById(R.id.landscapeImageView)
            landscapeImage.setImageURI(resultUri) // 画像ビューも更新

            // スイッチがONで、かつ現在の向きがランドスケープなら壁紙を更新 (isCurrentOrientationPortrait() の否定で判定)
            if (wallpaperManagerEnabled && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setCustomWallpaper(WallpaperType.LANDSCAPE)
            }
            Toast.makeText(this, "横向きの壁紙の設定が完了しました", Toast.LENGTH_SHORT).show()
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val err = UCrop.getError(data!!)
            Log.e("onActivityResultError", err.toString())
            Toast.makeText(this, "画像の切り抜きに失敗しました: ${err?.message}", Toast.LENGTH_LONG).show() // エラー内容をToastで表示
        }
    }

    private fun setCustomWallpaper(type: WallpaperType) {
        val wallpaperManager = WallpaperManager.getInstance(applicationContext)
        try {
            if (type == WallpaperType.PORTRAIT) {
                val currentPortraitUri = portraitUri
                if (currentPortraitUri != null) {
                    contentResolver.openInputStream(currentPortraitUri)?.use { input ->
                        wallpaperManager.setStream(
                            input,
                            null,
                            false
                        )
                    }
                }
            } else if (type == WallpaperType.LANDSCAPE) {
                val currentLandscapeUri = landscapeUri
                if (currentLandscapeUri != null) {
                    contentResolver.openInputStream(currentLandscapeUri)?.use { input ->
                        wallpaperManager.setStream(
                            input,
                            null,
                            false
                        )
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to set wallpaper.", Toast.LENGTH_SHORT).show()
        }
    }
}