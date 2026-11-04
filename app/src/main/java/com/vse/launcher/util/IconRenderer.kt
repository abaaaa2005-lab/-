package com.vse.launcher.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap

/** Рисует иконку приложения в выбранной форме: «как на iPhone» (скруглённый квадрат), круг или системная. */
object IconRenderer {

    fun render(src: Drawable, size: Int, shape: Int): Bitmap {
        if (shape == 2) return src.toBitmap(size, size)
        val d = src.mutate()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val path = shapePath(size.toFloat(), shape)
        c.clipPath(path)
        if (d is AdaptiveIconDrawable) {
            // Слои адаптивной иконки — 108dp, видимая часть — 72dp (2/3), поэтому выходим за края на 1/4
            val inset = size / 4
            d.background?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
            d.foreground?.let { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
        } else {
            // Старая иконка — кладём на белую подложку
            c.drawColor(Color.WHITE)
            val pad = size / 7
            val old = d.bounds
            d.setBounds(pad, pad, size - pad, size - pad)
            d.draw(c)
            d.bounds = old
        }
        // Тонкая обводка, как у iOS
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size / 64f
            color = Color.argb(28, 0, 0, 0)
        }
        c.drawPath(path, stroke)
        return bmp
    }

    /** Суперэллипс (squircle), близкий к форме иконок iOS. */
    fun shapePath(size: Float, shape: Int): Path {
        val p = Path()
        if (shape == 1) {
            p.addCircle(size / 2, size / 2, size / 2, Path.Direction.CW)
            return p
        }
        val n = 5.0
        val r = size / 2.0
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val cos = Math.cos(t)
            val sin = Math.sin(t)
            val x = r + r * Math.signum(cos) * Math.pow(Math.abs(cos), 2 / n)
            val y = r + r * Math.signum(sin) * Math.pow(Math.abs(sin), 2 / n)
            if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
        }
        p.close()
        return p
    }

    fun roundRect(size: Float) = RectF(0f, 0f, size, size)
}
