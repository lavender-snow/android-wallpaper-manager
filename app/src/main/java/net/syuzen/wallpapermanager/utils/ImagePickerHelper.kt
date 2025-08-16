package net.syuzen.wallpapermanager.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File

class ImagePickerHelper(
    private val activity: AppCompatActivity,
    private val onCropped: (Uri) -> Unit,
    private val isPortrait: Boolean
) {
    private val pickImageLauncher: ActivityResultLauncher<Array<String>>

    init {
        pickImageLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri ?: return@registerForActivityResult
            activity.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            activity.lifecycleScope.launch {
                startCrop(uri)
            }
        }
    }

    fun pickImage() {
        pickImageLauncher.launch(arrayOf("image/jpeg", "image/png", "image/jpg", "image/webp"))
    }

    private suspend fun startCrop(input: Uri) {
        val outFile = File.createTempFile("cropped_", ".jpg", activity.cacheDir)
        val outUri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", outFile
        )

        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(false)
            setHideBottomControls(false)
            setCompressionQuality(95)
        }

        val size = getScreenSizePx(activity)
        val (ratioX, ratioY) = getAspectForUCrop(activity, true)

        if (isPortrait) {
            val paddedUri = makeLetterboxedImage(activity, input, ratioX.toInt(), ratioY.toInt(), size.width, size.height)
            UCrop.of(paddedUri, outUri)
                .withAspectRatio(ratioX, ratioY)
                .withOptions(options)
                .start(activity)
        } else {
            val paddedUri = makeLetterboxedImage(activity, input, ratioY.toInt(), ratioX.toInt(), size.width, size.height)
            UCrop.of(paddedUri, outUri)
                .withAspectRatio(ratioY, ratioX)
                .withOptions(options)
                .start(activity)
        }
    }

    @Suppress("unused")
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data!!) ?: return
            onCropped(resultUri)
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val err = UCrop.getError(data!!)
            Toast.makeText(activity, "画像の切り抜きに失敗しました: ${err?.message}", Toast.LENGTH_LONG).show()
        }
    }
}

