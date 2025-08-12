package net.syuzen.wallpapermanager.utils

import android.content.Context
import android.graphics.Insets
import android.util.Size
import android.view.WindowInsets
import android.view.WindowManager
import java.lang.Float.max
import java.lang.Float.min
import android.graphics.*
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap

fun getScreenSizePx(context: Context, excludeSystemBars: Boolean = false): Size {
    val wm = context.getSystemService(WindowManager::class.java)
    val metrics = wm.currentWindowMetrics
    var width = metrics.bounds.width()
    var height = metrics.bounds.height()
    if (excludeSystemBars) {
        val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        val insets: Insets = metrics.windowInsets.getInsetsIgnoringVisibility(types)
        width -= (insets.left + insets.right)
        height -= (insets.top + insets.bottom)
    }
    return Size(width, height)
}

fun getAspectForUCrop(context: Context, portraitFixed: Boolean = false): Pair<Float, Float> {
    val s = getScreenSizePx(context, excludeSystemBars = false)
    val w = s.width
    val h = s.height
    return if (portraitFixed) {
        val short = min(w.toFloat(), h.toFloat())
        val long = max(w.toFloat(), h.toFloat())
        short to long
    } else {
        w.toFloat() to h.toFloat()
    }
}

suspend fun makeLetterboxedImage(
    context: Context,
    srcUri: Uri,
    aspectW: Int,        // 例: 9
    aspectH: Int,        // 例: 16
    minWidth: Int,       // 例: 端末の幅 or 2160 など
    minHeight: Int,      // 例: 端末の高 or 3840 など
    bgColor: Int = Color.BLACK
): Uri {
    // 元画像を読み込み
    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val src = context.contentResolver.openInputStream(srcUri).use { ins ->
        BitmapFactory.decodeStream(ins, null, opts)
    } ?: error("decode failed")

    val needW = maxOf(src.width, minWidth)
    val needH = maxOf(src.height, minHeight)

    // 指定アスペクトでキャンバスの幅高を決める
    var canvasW = needW
    var canvasH = (canvasW.toLong() * aspectH / aspectW).toInt()
    if (canvasH < needH) {
        canvasH = needH
        canvasW = (canvasH.toLong() * aspectW / aspectH).toInt()
    }

    // 黒背景キャンバスに中央貼り付け
    val outBmp = createBitmap(canvasW, canvasH)
    val c = Canvas(outBmp)
    c.drawColor(bgColor)

    val left = ((canvasW - src.width) / 2f).roundToInt()
    val top  = ((canvasH - src.height) / 2f).roundToInt()
    c.drawBitmap(src, left.toFloat(), top.toFloat(), null)

    // 一時ファイルへ保存
    val outFile = File.createTempFile("letterbox_", ".jpg", context.cacheDir)
    FileOutputStream(outFile).use { fos ->
        outBmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
    }

    // メモリ解放
    src.recycle()
    outBmp.recycle()

    return outFile.toUri()
}

fun saveKey(context: Context, sharedPrefsName: String, key: String, value: Boolean) {
    context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)
        .edit {
            putBoolean(key, value)
        }
}

fun saveKey(context: Context, sharedPrefsName: String, key: String, value: String) {
    context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)
        .edit {
            putString(key, value)
        }
}

inline fun <reified T> saveValue(
    context: Context,
    prefsName: String,
    key: String,
    value: T
) {
    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    prefs.edit {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Float -> putFloat(key, value)
            is Long -> putLong(key, value)
            else -> throw IllegalArgumentException("This type cannot be saved into SharedPreferences")
        }
    }
}

inline fun <reified T> loadValue(
    context: Context,
    prefsName: String,
    key: String,
    defaultValue: T? = null
): T? {
    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    return when (T::class) {
        String::class -> prefs.getString(key, defaultValue as? String) as? T
        Boolean::class -> prefs.getBoolean(key, defaultValue as? Boolean ?: false) as? T // BooleanのdefaultValueは非nullが必要
        Int::class -> prefs.getInt(key, defaultValue as? Int ?: 0) as? T
        Float::class -> prefs.getFloat(key, defaultValue as? Float ?: 0f) as? T
        Long::class -> prefs.getLong(key, defaultValue as? Long ?: 0L) as? T
        else -> throw IllegalArgumentException("This type cannot be loaded from SharedPreferences")
    }
}