package com.vse.launcher.buddy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

/**
 * Держит виртуальный дисплей, который зеркалит экран в уменьшенном виде,
 * и по запросу отдаёт последний кадр.
 */
class ScreenGrabber(ctx: Context, private val projection: MediaProjection, onStopped: () -> Unit) {
    private val width: Int
    private val height: Int
    private val reader: ImageReader
    private var display: VirtualDisplay? = null
    private var last: Bitmap? = null
    private var lastPrint: IntArray? = null

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            release()
            onStopped()
        }
    }

    init {
        val m = ctx.resources.displayMetrics
        // Для Claude хватает ~720 px по ширине: дёшево и разборчиво
        val scale = minOf(1f, 720f / minOf(m.widthPixels, m.heightPixels))
        width = (m.widthPixels * scale).toInt() / 2 * 2
        height = (m.heightPixels * scale).toInt() / 2 * 2
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        // На Android 14+ callback обязательно регистрировать до создания дисплея
        projection.registerCallback(callback, Handler(Looper.getMainLooper()))
        display = projection.createVirtualDisplay(
            "buddy-eyes", width, height, (m.densityDpi * scale).toInt(),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null,
        )
    }

    /** Последний кадр экрана или null, если кадров ещё не было. */
    fun grab(): Bitmap? {
        val img = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return last
        try {
            val plane = img.planes[0]
            val rowPad = plane.rowStride - plane.pixelStride * width
            val tmp = Bitmap.createBitmap(width + rowPad / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
            tmp.copyPixelsFromBuffer(plane.buffer)
            val bmp = if (rowPad == 0) tmp else Bitmap.createBitmap(tmp, 0, 0, width, height).also { tmp.recycle() }
            last?.recycle()
            last = bmp
        } finally {
            img.close()
        }
        return last
    }

    /**
     * Сильно ли поменялся экран с прошлой проверки.
     * Сравниваем крошечный отпечаток 12×20 в оттенках серого — анимация самого помощника почти не влияет.
     */
    fun changed(b: Bitmap): Boolean {
        val small = Bitmap.createScaledBitmap(b, 12, 20, true)
        val print = IntArray(240) { i ->
            val c = small.getPixel(i % 12, i / 12)
            ((c shr 16 and 255) + (c shr 8 and 255) + (c and 255)) / 3
        }
        small.recycle()
        val prev = lastPrint
        lastPrint = print
        if (prev == null) return true
        val diff = print.indices.count { abs(print[it] - prev[it]) > 18 }
        return diff > 14
    }

    fun release() {
        runCatching { display?.release() }
        display = null
        runCatching { reader.close() }
        runCatching { projection.unregisterCallback(callback) }
    }

    fun stop() {
        release()
        runCatching { projection.stop() }
    }
}
