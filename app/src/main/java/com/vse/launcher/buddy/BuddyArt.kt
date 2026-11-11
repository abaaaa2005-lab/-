package com.vse.launcher.buddy

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Персонаж-помощник. Всё нарисовано кодом: тело, эмоции, моргание, речь и весь гардероб.
 * [talking] — открывает и закрывает рот, пока говорит голос.
 */
@Composable
fun BuddyCharacter(look: BuddyLook, emotion: Emotion, talking: Boolean, modifier: Modifier = Modifier, animate: Boolean = true) {
    var time by remember { mutableFloatStateOf(0.7f) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time = (it - start) / 1_000_000_000f + 0.7f }
        }
    }
    Canvas(modifier) { BuddyPainter(this, look, emotion, talking, time).draw() }
}

private val Ink = Color(0xFF23233A)
private val White = Color.White
private val Gold = Color(0xFFFFC83D)
private val Pink = Color(0xFFFF8FB1)

/** Где на теле лицо, уши и шея — зависит от формы. */
private data class Geo(val top: Float, val halfW: Float, val earY: Float, val eyeY: Float, val mouthY: Float, val neckY: Float, val bottom: Float)

private class BuddyPainter(
    val ds: DrawScope,
    val look: BuddyLook,
    val emo: Emotion,
    val talking: Boolean,
    val t: Float,
) {
    val u = ds.size.minDimension / 100f
    val cx = ds.size.width / 2f

    // Покачивание: у каждой эмоции свой «ритм»
    val bob = when (emo) {
        Emotion.EXCITED, Emotion.LAUGHING -> abs(sin(t * 7f)) * -3.5f
        Emotion.SLEEPY -> sin(t * 1.1f) * 1.6f
        Emotion.SAD -> sin(t * 1.4f) * 0.8f + 1.5f
        else -> sin(t * 2.2f) * 1.4f
    }
    val shake = when (emo) {
        Emotion.SCARED -> sin(t * 55f) * 0.7f
        Emotion.ANGRY -> sin(t * 40f) * 0.45f
        else -> 0f
    }
    val oy = bob * u
    val ox = shake * u

    val geo = when (look.shape) {
        1 -> Geo(30f, 27f, 61f, 59f, 71f, 83f, 88f)
        2 -> Geo(34f, 27f, 57f, 56f, 69f, 80f, 87f)
        3 -> Geo(33f, 26f, 56f, 55f, 67f, 79f, 89f)
        6 -> Geo(44f, 32f, 64f, 62f, 73f, 82f, 88f)
        7 -> Geo(33f, 24f, 60f, 58f, 69f, 79f, 86f)
        else -> Geo(33f, 27f, 58f, 56f, 68f, 80f, 87f)
    }

    fun p(x: Float, y: Float) = Offset(cx + x * u + ox, y * u + oy)
    fun s(w: Float, h: Float) = Size(w * u, h * u)
    fun r(x: Float, y: Float, w: Float, h: Float) = Rect(p(x, y), s(w, h))

    fun col(v: Long, offset: Float = 0f): Color =
        if (v == Wardrobe.RAINBOW) Color.hsv(((t * 50f + offset) % 360f + 360f) % 360f, 0.55f, 1f) else Color(v)

    val body = col(look.bodyColor)
    val bodyDark = lerp(body, Color.Black, 0.22f)
    val hat = col(look.hatColor, 120f)
    val hatDark = lerp(hat, Color.Black, 0.25f)
    val phones = col(look.phonesColor, 200f)
    val glassC = col(look.glassesColor, 60f)
    val acc = col(look.accColor, 240f)

    // Моргание раз в ~4 секунды (во сне глаза почти закрыты)
    val blink: Float = run {
        val ph = t % 4.3f
        if (ph < 0.14f) 1f - abs(ph - 0.07f) / 0.07f else 0f
    }
    val mouthOpen = if (talking) 0.3f + 0.7f * abs(sin(t * 13f)) * (0.6f + 0.4f * abs(sin(t * 3.1f))) else 0f

    fun draw() = with(ds) {
        drawAuraBack()
        if (look.accessory == 11) drawCape()
        // Тень под персонажем
        drawOval(Color.Black.copy(alpha = 0.18f), Offset(cx - 22 * u, 93 * u), s(44f, 5f))
        drawBody()
        if (look.shape == 4 && look.hat !in listOf(4, 11, 12, 21, 24, 29)) drawCatEars()
        drawBelly()
        drawCheeks()
        drawEyes()
        drawBrows()
        drawMouth()
        drawFaceExtra()
        drawGlasses()
        if (look.headphones > 0) drawPhonesBand()
        drawHat()
        if (look.headphones > 0) drawPhonesCups()
        if (look.accessory in 1..12 && look.accessory != 11) drawAccessory()
        drawEmotionMarks()
        drawAuraFront()
    }

    // ---------------- Тело ----------------

    fun DrawScope.drawBody() {
        val path = bodyPath()
        drawPath(path, Brush.verticalGradient(listOf(lerp(body, White, 0.18f), body, bodyDark), startY = geo.top * u + oy, endY = geo.bottom * u + oy))
        drawPath(path, Ink.copy(alpha = 0.18f), style = Stroke(1.2f * u))
        // Блик
        drawOval(White.copy(alpha = 0.35f), p(-17f, geo.top + 6f), s(10f, 6f))
        if (look.shape == 5) { // робот: антенна и болты
            drawLine(Ink, p(0f, geo.top), p(0f, geo.top - 8f), 1.5f * u)
            drawCircle(if (sin(t * 4) > 0) Color(0xFFFF5E5E) else Color(0xFFFFB3B3), 3f * u, p(0f, geo.top - 9f))
            drawCircle(Color.Gray, 2.4f * u, p(-27f, 60f)); drawCircle(Color.Gray, 2.4f * u, p(27f, 60f))
        }
        if (look.shape == 6) { // слизень: капельки
            drawOval(body.copy(alpha = 0.8f), p(-24f, 86f), s(5f, 7f))
            drawOval(body.copy(alpha = 0.7f), p(18f, 87f), s(4f, 5f))
        }
    }

    fun bodyPath(): Path = Path().apply {
        when (look.shape) {
            1 -> { // капелька
                moveTo(p(0f, 28f).x, p(0f, 28f).y)
                cubicTo(p(10f, 40f).x, p(10f, 40f).y, p(28f, 52f).x, p(28f, 52f).y, p(27f, 66f).x, p(27f, 66f).y)
                cubicTo(p(27f, 82f).x, p(27f, 82f).y, p(14f, 88f).x, p(14f, 88f).y, p(0f, 88f).x, p(0f, 88f).y)
                cubicTo(p(-14f, 88f).x, p(-14f, 88f).y, p(-27f, 82f).x, p(-27f, 82f).y, p(-27f, 66f).x, p(-27f, 66f).y)
                cubicTo(p(-28f, 52f).x, p(-28f, 52f).y, p(-10f, 40f).x, p(-10f, 40f).y, p(0f, 28f).x, p(0f, 28f).y)
                close()
            }
            2 -> addRoundRect(RoundRect(r(-27f, 34f, 54f, 53f), CornerRadius(13 * u)))
            3 -> { // призрак с волнистым низом
                val top = p(-26f, 33f)
                arcTo(Rect(top, s(52f, 52f)), 180f, 180f, false)
                lineTo(p(26f, 84f).x, p(26f, 84f).y)
                val n = 4
                for (i in 0 until n) {
                    val x1 = 26f - (i + 0.5f) * 52f / n
                    val x2 = 26f - (i + 1) * 52f / n
                    val wave = sin(t * 4f + i) * 1.5f
                    quadraticTo(p(x1, 92f + wave).x, p(x1, 92f + wave).y, p(x2, 84f).x, p(x2, 84f).y)
                }
                close()
            }
            5 -> addRoundRect(RoundRect(r(-27f, 34f, 54f, 52f), CornerRadius(7 * u)))
            6 -> addOval(r(-32f, 44f, 64f, 44f))
            7 -> { // звёздочка со скруглёнными лучами
                val c = p(0f, 62f)
                for (i in 0 until 10) {
                    val a = -PI / 2 + i * PI / 5
                    val rad = (if (i % 2 == 0) 31f else 19f) * u
                    val x = c.x + cos(a).toFloat() * rad
                    val y = c.y + sin(a).toFloat() * rad
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            else -> addOval(r(-27f, 33f, 54f, 54f))
        }
    }

    fun DrawScope.drawCatEars() {
        for (sx in listOf(-1f, 1f)) {
            val ear = Path().apply {
                moveTo(p(sx * 22f, 46f).x, p(sx * 22f, 46f).y)
                lineTo(p(sx * 21f, 26f).x, p(sx * 21f, 26f).y)
                lineTo(p(sx * 8f, 36f).x, p(sx * 8f, 36f).y); close()
            }
            drawPath(ear, body)
            val inner = Path().apply {
                moveTo(p(sx * 19f, 42f).x, p(sx * 19f, 42f).y)
                lineTo(p(sx * 19.5f, 30f).x, p(sx * 19.5f, 30f).y)
                lineTo(p(sx * 11f, 37f).x, p(sx * 11f, 37f).y); close()
            }
            drawPath(inner, Pink)
        }
    }

    fun DrawScope.drawBelly() {
        if (look.shape == 7) return
        val c = col(look.bellyColor, 180f)
        drawOval(c.copy(alpha = 0.85f), p(-14f, geo.neckY - 6f), s(28f, geo.bottom - geo.neckY + 3f))
    }

    fun DrawScope.drawCheeks() {
        if (!look.blush && emo != Emotion.LOVE) return
        val a = when (emo) { Emotion.LOVE -> 0.75f; Emotion.ANGRY -> 0.6f; Emotion.EXCITED, Emotion.LAUGHING -> 0.55f; else -> 0.38f }
        val c = if (emo == Emotion.ANGRY) Color(0xFFFF4040) else Pink
        drawOval(c.copy(alpha = a), p(-21f, geo.mouthY - 5f), s(8f, 4.5f))
        drawOval(c.copy(alpha = a), p(13f, geo.mouthY - 5f), s(8f, 4.5f))
    }

    // ---------------- Глаза ----------------

    fun DrawScope.drawEyes() {
        if (look.glasses == 8) return // VR-шлем закрывает глаза
        for (side in listOf(-1f, 1f)) drawEye(p(side * 10f, geo.eyeY), side)
    }

    fun DrawScope.drawEye(c: Offset, side: Float) {
        val eyeInk = col(look.eyeColor, 300f)
        when (emo) {
            Emotion.HAPPY -> { arcEye(c, up = true); return }
            Emotion.LAUGHING -> { chevron(c, side); return }
            Emotion.LOVE -> { drawPath(heart(c, 6.5f + sin(t * 8f) * 0.8f), Color(0xFFFF3B6B)); return }
            Emotion.EXCITED -> { drawPath(star(c, 7f + sin(t * 9f), 3f, t * 2f), Gold); drawPath(star(c, 7f + sin(t * 9f), 3f, t * 2f), Color(0xFFE09A00), style = Stroke(0.8f * u)); return }
            Emotion.SLEEPY -> { arcEye(c, up = false); return }
            else -> {}
        }
        // Обычный открытый глаз (с учётом стиля и моргания)
        var open = 1f - blink
        if (look.eyes == 4) open *= 0.45f
        if (open < 0.18f) { drawLine(eyeInk, c - Offset(4f * u, 0f), c + Offset(4f * u, 0f), 1.4f * u, StrokeCap.Round); return }
        val big = emo == Emotion.SURPRISED || emo == Emotion.SCARED
        val look2 = when (emo) {
            Emotion.THINKING -> Offset(1.8f, -2.2f)
            Emotion.SMUG -> Offset(2.2f, 0f)
            Emotion.SCARED -> Offset(sin(t * 6f) * 1.5f, 0f)
            else -> Offset(sin(t * 0.6f) * 1f, 0f)
        } * u
        withTransform({ scale(1f, open, c) }) {
            when (look.eyes) {
                1 -> { // аниме
                    drawOval(White, c - Offset(5f * u, 6.5f * u), s(10f, 13f))
                    drawOval(eyeInk, c - Offset(4f * u, 5.5f * u) + look2, s(8f, 11f))
                    drawCircle(Ink, (if (big) 1.5f else 2.6f) * u, c + look2)
                    drawCircle(White, 1.6f * u, c + look2 + Offset(-1.6f * u, -2.6f * u))
                    drawCircle(White, 0.8f * u, c + look2 + Offset(1.5f * u, 2.2f * u))
                }
                2 -> drawCircle(eyeInk, (if (big) 3.4f else 2.5f) * u, c + look2 * 0.3f)
                3 -> {
                    drawCircle(White, 8f * u, c); drawCircle(Ink.copy(alpha = 0.25f), 8f * u, c, style = Stroke(0.7f * u))
                    drawCircle(eyeInk, (if (big) 2.6f else 4.8f) * u, c + look2 * 1.4f)
                    drawCircle(White, 1.6f * u, c + look2 * 1.4f + Offset(-1.6f * u, -1.8f * u))
                }
                5 -> { // экран робота
                    val g = lerp(eyeInk, Color(0xFF40F0FF), 0.6f)
                    drawRoundRect(g.copy(alpha = 0.35f), c - Offset(6f * u, 4.5f * u), s(12f, 9f), CornerRadius(2 * u))
                    drawRoundRect(g, c - Offset(4f * u, (if (big) 3.5f else 2.5f) * u) + look2 * 0.5f, s(8f, if (big) 7f else 5f), CornerRadius(1.2f * u))
                }
                6 -> {
                    drawOval(White, c - Offset(4.5f * u, 7f * u), s(9f, 14f))
                    drawOval(Ink.copy(alpha = 0.3f), c - Offset(4.5f * u, 7f * u), s(9f, 14f), style = Stroke(0.6f * u))
                    drawOval(eyeInk, c - Offset(2.5f * u, 3.5f * u) + look2, s(5f, if (big) 5f else 7f))
                }
                7 -> { // пуговки
                    drawCircle(eyeInk, 5f * u, c); drawCircle(lerp(eyeInk, White, 0.25f), 4f * u, c, style = Stroke(0.6f * u))
                    for (dx in listOf(-1.4f, 1.4f)) for (dy in listOf(-1.4f, 1.4f)) drawCircle(body, 0.8f * u, c + Offset(dx * u, dy * u))
                }
                else -> {
                    val w = if (big) 11f else 9f
                    val h = if (big) 12f else 11f
                    if (big) drawOval(White, c - Offset(w / 2 * u, h / 2 * u), s(w, h))
                    if (big) drawCircle(eyeInk, 2.2f * u, c + look2)
                    else {
                        drawOval(eyeInk, c - Offset(4.5f * u, 5.5f * u) + look2 * 0.5f, s(9f, 11f))
                        drawCircle(White, 1.8f * u, c + look2 * 0.5f + Offset(-1.6f * u, -2.2f * u))
                        drawCircle(White, 0.8f * u, c + look2 * 0.5f + Offset(1.4f * u, 1.8f * u))
                    }
                }
            }
        }
        // Полуприкрытые веки: круто, ехидно, злость
        if (emo == Emotion.COOL || emo == Emotion.SMUG) {
            drawRect(body, c - Offset(9f * u, 9f * u), s(18f, 8.6f))
            drawLine(lerp(body, Ink, 0.5f), c - Offset(7f * u, 0.4f * u), c + Offset(7f * u, -0.4f * u), 1.3f * u, StrokeCap.Round)
        }
        if (emo == Emotion.ANGRY) {
            val lid = Path().apply {
                moveTo(c.x - 9f * u, c.y - 9f * u); lineTo(c.x + 9f * u, c.y - 9f * u)
                if (side < 0) { lineTo(c.x + 9f * u, c.y - 1f * u); lineTo(c.x - 9f * u, c.y - 6f * u) }
                else { lineTo(c.x + 9f * u, c.y - 6f * u); lineTo(c.x - 9f * u, c.y - 1f * u) }
                close()
            }
            drawPath(lid, body)
        }
        if (emo == Emotion.SAD && side > 0) {
            val k = (t * 0.6f) % 1f
            drawPath(drop(c + Offset(3f * u, (6f + k * 16f) * u), 2.2f), Color(0xFF6EC6FF).copy(alpha = 1f - k))
        }
    }

    fun DrawScope.arcEye(c: Offset, up: Boolean) {
        val path = Path().apply {
            moveTo(c.x - 5f * u, c.y + (if (up) 2f else -1f) * u)
            quadraticTo(c.x, c.y + (if (up) -5f else 3f) * u, c.x + 5f * u, c.y + (if (up) 2f else -1f) * u)
        }
        drawPath(path, col(look.eyeColor, 300f), style = Stroke(1.8f * u, cap = StrokeCap.Round))
    }

    fun DrawScope.chevron(c: Offset, side: Float) {
        val d = -side
        val path = Path().apply {
            moveTo(c.x - d * 4f * u, c.y - 4f * u); lineTo(c.x + d * 3f * u, c.y); lineTo(c.x - d * 4f * u, c.y + 4f * u)
        }
        drawPath(path, col(look.eyeColor, 300f), style = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun DrawScope.drawBrows() {
        if (look.glasses == 8 || look.glasses == 10) return
        val y = geo.eyeY - 9f
        val c = lerp(body, Ink, 0.75f)
        fun brow(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, p(x1, y1), p(x2, y2), 1.6f * u, StrokeCap.Round)
        when (emo) {
            Emotion.ANGRY -> { brow(-15f, y - 2f, -5f, y + 2f); brow(15f, y - 2f, 5f, y + 2f) }
            Emotion.SAD, Emotion.SCARED -> { brow(-15f, y + 1f, -6f, y - 2.5f); brow(15f, y + 1f, 6f, y - 2.5f) }
            Emotion.SURPRISED -> { brow(-14f, y - 3.5f, -6f, y - 4.5f); brow(14f, y - 3.5f, 6f, y - 4.5f) }
            Emotion.THINKING, Emotion.CONFUSED, Emotion.SMUG -> { brow(-14f, y, -6f, y); brow(14f, y - 3.5f, 6f, y - 2f) }
            else -> {}
        }
    }

    // ---------------- Рот ----------------

    fun DrawScope.drawMouth() {
        val m = p(0f, geo.mouthY)
        val ink = lerp(body, Ink, 0.85f)
        val stroke = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (talking) {
            val w = when (emo) { Emotion.HAPPY, Emotion.LAUGHING, Emotion.EXCITED -> 9f; Emotion.SAD -> 6f; else -> 7.5f }
            val h = 1.5f + 6f * mouthOpen
            drawOval(Color(0xFF7A1F2B), m - Offset(w / 2 * u, h / 2 * u), s(w, h))
            if (h > 4f) drawOval(Color(0xFFFF7A8A), m + Offset(-w / 4 * u, (h / 2 - 2.2f) * u), s(w / 2, 2f))
            return
        }
        when (emo) {
            Emotion.NEUTRAL -> drawPath(Path().apply { moveTo(m.x - 4 * u, m.y); quadraticTo(m.x, m.y + 3 * u, m.x + 4 * u, m.y) }, ink, style = stroke)
            Emotion.HAPPY, Emotion.LOVE -> drawPath(Path().apply { moveTo(m.x - 7 * u, m.y - 1 * u); quadraticTo(m.x, m.y + 7 * u, m.x + 7 * u, m.y - 1 * u) }, ink, style = stroke)
            Emotion.LAUGHING, Emotion.EXCITED -> {
                val d = Path().apply { moveTo(m.x - 8 * u, m.y - 2 * u); lineTo(m.x + 8 * u, m.y - 2 * u); quadraticTo(m.x, m.y + 11 * u, m.x - 8 * u, m.y - 2 * u); close() }
                drawPath(d, Color(0xFF7A1F2B))
                drawOval(Color(0xFFFF7A8A), m + Offset(-4 * u, 1.5f * u), s(8f, 3.5f))
                drawRect(White, m + Offset(-6 * u, -2 * u), s(12f, 1.8f))
            }
            Emotion.SURPRISED -> { drawOval(Color(0xFF7A1F2B), m - Offset(3.5f * u, 3 * u), s(7f, 8f)) }
            Emotion.SCARED, Emotion.CONFUSED -> {
                val amp = if (emo == Emotion.SCARED) 1.6f else 1.1f
                val path = Path().apply {
                    moveTo(m.x - 8 * u, m.y)
                    for (i in 1..6) lineTo(m.x + (-8 + i * 16f / 6) * u, m.y + (if (i % 2 == 1) -amp else amp) * u)
                }
                drawPath(path, ink, style = stroke)
            }
            Emotion.THINKING -> {
                drawLine(ink, m + Offset(-1 * u, 0f), m + Offset(5 * u, -1 * u), 1.6f * u, StrokeCap.Round)
            }
            Emotion.SAD -> drawPath(Path().apply { moveTo(m.x - 6 * u, m.y + 3 * u); quadraticTo(m.x, m.y - 3 * u, m.x + 6 * u, m.y + 3 * u) }, ink, style = stroke)
            Emotion.ANGRY -> {
                drawRoundRect(Color(0xFF7A1F2B), m - Offset(6 * u, 2.2f * u), s(12f, 4.5f), CornerRadius(1.5f * u))
                drawRect(White, m - Offset(5 * u, 2.2f * u), s(10f, 1.6f))
            }
            Emotion.SLEEPY -> drawOval(Color(0xFF7A1F2B), m - Offset(2 * u, 1.5f * u + sin(t * 1.1f) * 0.5f * u), s(4f, 3f + sin(t * 1.1f)))
            Emotion.COOL, Emotion.SMUG -> drawPath(Path().apply { moveTo(m.x - 6 * u, m.y + 1 * u); quadraticTo(m.x + 1 * u, m.y + 3 * u, m.x + 7 * u, m.y - 2.5f * u) }, ink, style = stroke)
        }
    }

    fun DrawScope.drawFaceExtra() {
        val m = p(0f, geo.mouthY)
        when (look.face) {
            1 -> { // усы
                val c = lerp(body, Ink, 0.85f)
                for (sx in listOf(-1f, 1f)) {
                    val path = Path().apply {
                        moveTo(m.x, m.y - 3.5f * u)
                        cubicTo(m.x + sx * 4 * u, m.y - 6.5f * u, m.x + sx * 9 * u, m.y - 4.5f * u, m.x + sx * 10 * u, m.y - 6.5f * u)
                        cubicTo(m.x + sx * 10 * u, m.y - 1.5f * u, m.x + sx * 4 * u, m.y - 1.5f * u, m.x, m.y - 3f * u)
                        close()
                    }
                    drawPath(path, c)
                }
            }
            2 -> for ((dx, dy) in listOf(-19f to -3f, -16f to -1f, -20f to 0f, 16f to -3f, 19f to -1f, 15f to 0f))
                drawCircle(Color(0xFFB0703A).copy(alpha = 0.7f), 0.8f * u, p(dx, geo.mouthY + dy - 2f))
            3 -> drawCircle(Ink, 1f * u, p(8f, geo.mouthY + 3f))
            4 -> withTransform({ rotate(-25f, p(17f, geo.eyeY + 3f)) }) {
                drawRoundRect(Color(0xFFF2C9A0), p(13f, geo.eyeY + 1.5f), s(9f, 3.5f), CornerRadius(1.5f * u))
                drawRoundRect(Color(0xFFF2C9A0), p(15.8f, geo.eyeY - 1.2f), s(3.5f, 9f), CornerRadius(1.5f * u))
            }
            5 -> if (!talking) for (sx in listOf(-1f, 1f)) {
                val path = Path().apply { moveTo(m.x + sx * 3 * u, m.y + 1.5f * u); lineTo(m.x + sx * 5 * u, m.y + 1.5f * u); lineTo(m.x + sx * 4 * u, m.y + 4.5f * u); close() }
                drawPath(path, White)
            }
            6 -> if (!talking) {
                drawRoundRect(Color(0xFFFF6B81), m + Offset(-2.5f * u, 0.5f * u), s(5f, 5.5f + sin(t * 3f) * 0.6f), CornerRadius(2.5f * u))
                drawLine(Color(0xFFD94A60), m + Offset(0f, 1.5f * u), m + Offset(0f, 4.5f * u), 0.6f * u)
            }
            7 -> {
                val beard = Path().apply {
                    moveTo(p(-17f, geo.mouthY - 4f).x, p(-17f, geo.mouthY - 4f).y)
                    quadraticTo(p(-16f, geo.mouthY + 16f).x, p(-16f, geo.mouthY + 16f).y, p(0f, geo.mouthY + 18f).x, p(0f, geo.mouthY + 18f).y)
                    quadraticTo(p(16f, geo.mouthY + 16f).x, p(16f, geo.mouthY + 16f).y, p(17f, geo.mouthY - 4f).x, p(17f, geo.mouthY - 4f).y)
                    quadraticTo(p(0f, geo.mouthY + 2f).x, p(0f, geo.mouthY + 2f).y, p(-17f, geo.mouthY - 4f).x, p(-17f, geo.mouthY - 4f).y)
                    close()
                }
                drawPath(beard, lerp(body, Color(0xFF5B3A1E), 0.75f))
            }
        }
    }

    // ---------------- Очки ----------------

    fun DrawScope.drawGlasses() {
        val y = geo.eyeY
        val l = p(-10f, y); val rr = p(10f, y)
        val st = Stroke(1.3f * u)
        when (look.glasses) {
            1 -> { drawCircle(glassC, 7f * u, l, style = st); drawCircle(glassC, 7f * u, rr, style = st); bridge(y) }
            2 -> {
                for (c in listOf(l, rr)) drawRoundRect(glassC, c - Offset(7.5f * u, 5.5f * u), s(15f, 11f), CornerRadius(2f * u), style = st)
                bridge(y)
            }
            3 -> {
                for (c in listOf(l, rr)) {
                    drawRoundRect(Color(0xFF15151F), c - Offset(7.5f * u, 5f * u), s(15f, 10f), CornerRadius(3.5f * u))
                    drawLine(White.copy(alpha = 0.5f), c + Offset(-4f * u, -2.5f * u), c + Offset(-1f * u, -2.5f * u), 1f * u)
                }
                bridge(y, Color(0xFF15151F))
            }
            4 -> {
                for (c in listOf(l, rr)) {
                    val path = Path().apply {
                        moveTo(c.x - 7f * u, c.y - 4.5f * u); lineTo(c.x + 7f * u, c.y - 4.5f * u)
                        quadraticTo(c.x + 7.5f * u, c.y + 7f * u, c.x, c.y + 6f * u)
                        quadraticTo(c.x - 7.5f * u, c.y + 5f * u, c.x - 7f * u, c.y - 4.5f * u); close()
                    }
                    drawPath(path, Brush.verticalGradient(listOf(Color(0xCC3A2A10), Color(0x88FFB347)), c.y - 5 * u, c.y + 6 * u))
                    drawPath(path, Gold, style = Stroke(0.9f * u))
                }
                bridge(y, Gold)
            }
            5 -> { for (c in listOf(l, rr)) { drawPath(heart(c, 8f), Color(0xBBFF3B6B)); drawPath(heart(c, 8f), glassC, style = Stroke(1f * u)) }; bridge(y) }
            6 -> { for (c in listOf(l, rr)) { drawPath(star(c, 9f, 4.2f, 0f), Color(0xBBFFD93D)); drawPath(star(c, 9f, 4.2f, 0f), glassC, style = Stroke(1f * u)) }; bridge(y) }
            7 -> {
                drawCircle(Gold, 7f * u, rr, style = Stroke(1.2f * u))
                drawCircle(White.copy(alpha = 0.2f), 6.4f * u, rr)
                drawLine(Gold, rr + Offset(5f * u, 5f * u), p(18f, geo.neckY), 0.6f * u)
            }
            8 -> { // VR
                drawLine(Color(0xFF2B2B35), p(-27f, y), p(27f, y), 3f * u)
                drawRoundRect(Color(0xFF2B2B35), p(-19f, y - 7f), s(38f, 14f), CornerRadius(4f * u))
                drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF7B5CE0), Color(0xFF3BB2F6)), p(-17f, 0f).x, p(17f, 0f).x), p(-17f, y - 5f), s(34f, 10f), CornerRadius(3f * u))
                val k = (t * 0.8f) % 1f
                drawLine(White.copy(alpha = 0.5f), p(-17f + 34f * k, y - 5f), p(-17f + 34f * k - 4f, y + 5f), 1.2f * u)
            }
            9 -> { // пиксельные
                val px = 2.2f
                val rows = listOf("11111111111111111", "01111100011111000", "00110000001100000")
                rows.forEachIndexed { ry, row ->
                    row.forEachIndexed { rx, ch ->
                        if (ch == '1') drawRect(Color.Black, p(-18.7f + rx * px, y - 3f + ry * px), s(px, px))
                    }
                }
                drawRect(White, p(-14.3f, y - 0.8f), s(px, px)); drawRect(White, p(3.3f, y - 0.8f), s(px, px))
            }
            10 -> { // маска героя
                val mask = Path().apply {
                    fillType = PathFillType.EvenOdd
                    moveTo(p(-22f, y - 2f).x, p(-22f, y - 2f).y)
                    quadraticTo(p(0f, y - 10f).x, p(0f, y - 10f).y, p(22f, y - 2f).x, p(22f, y - 2f).y)
                    quadraticTo(p(18f, y + 8f).x, p(18f, y + 8f).y, p(0f, y + 3f).x, p(0f, y + 3f).y)
                    quadraticTo(p(-18f, y + 8f).x, p(-18f, y + 8f).y, p(-22f, y - 2f).x, p(-22f, y - 2f).y)
                    close()
                    addOval(Rect(l - Offset(5f * u, 3.5f * u), s(10f, 7f)))
                    addOval(Rect(rr - Offset(5f * u, 3.5f * u), s(10f, 7f)))
                }
                drawPath(mask, glassC)
            }
            11 -> { // лыжная маска
                drawLine(Color(0xFF2B2B35), p(-27f, y), p(27f, y), 2.5f * u)
                drawRoundRect(glassC, p(-21f, y - 7.5f), s(42f, 15f), CornerRadius(7f * u))
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFF8A5B), Color(0xFFFFD93D), Color(0xFF6C8CFF)), p(-19f, y - 6f), p(19f, y + 6f)), p(-19f, y - 6f), s(38f, 12f), CornerRadius(6f * u))
                drawLine(White.copy(alpha = 0.6f), p(-12f, y - 3.5f), p(-4f, y - 3.5f), 1f * u, StrokeCap.Round)
            }
            12 -> { // кошачий глаз
                for ((c, sx) in listOf(l to -1f, rr to 1f)) {
                    val path = Path().apply {
                        moveTo(c.x - sx * 6f * u, c.y - 3f * u)
                        lineTo(c.x + sx * 9f * u, c.y - 7f * u)
                        quadraticTo(c.x + sx * 7f * u, c.y + 6f * u, c.x, c.y + 5f * u)
                        quadraticTo(c.x - sx * 7f * u, c.y + 4f * u, c.x - sx * 6f * u, c.y - 3f * u); close()
                    }
                    drawPath(path, glassC, style = Stroke(1.5f * u, join = StrokeJoin.Round))
                }
                bridge(y)
            }
        }
    }

    fun DrawScope.bridge(y: Float, c: Color = glassC) = drawLine(c, p(-3f, y - 1f), p(3f, y - 1f), 1.2f * u)

    // ---------------- Наушники ----------------

    fun phonesTint(): Color = if (look.headphones == 8) Color.hsv((t * 90f) % 360f, 0.8f, 1f) else phones

    fun DrawScope.drawPhonesBand() {
        val c = phonesTint()
        val hw = geo.halfW + 2f
        val topY = geo.top - 4f
        when (look.headphones) {
            5 -> return // капельки без дужки
            7 -> { // диджейские: дужка съехала набок
                withTransform({ rotate(-18f, p(0f, geo.earY)) }) {
                    drawArc(c, 180f, 180f, false, p(-hw, topY), s(hw * 2, (geo.earY - topY) * 2), style = Stroke(3f * u, cap = StrokeCap.Round))
                }
                return
            }
            6, 9 -> drawArc(c, 180f, 180f, false, p(-hw, topY), s(hw * 2, (geo.earY - topY) * 2), style = Stroke(1.3f * u, cap = StrokeCap.Round))
            else -> {
                val w = if (look.headphones == 2) 4.5f else 3.2f
                drawArc(c, 180f, 180f, false, p(-hw, topY), s(hw * 2, (geo.earY - topY) * 2), style = Stroke(w * u, cap = StrokeCap.Round))
                if (look.headphones == 8) drawArc(c.copy(alpha = 0.3f), 180f, 180f, false, p(-hw, topY), s(hw * 2, (geo.earY - topY) * 2), style = Stroke(w * 2.4f * u))
            }
        }
        if (look.headphones == 3) { // кошачьи ушки на дужке
            for (sx in listOf(-1f, 1f)) {
                val path = Path().apply {
                    moveTo(p(sx * 6f, topY + 1.5f).x, p(sx * 6f, topY + 1.5f).y)
                    lineTo(p(sx * 15f, topY - 9f).x, p(sx * 15f, topY - 9f).y)
                    lineTo(p(sx * 19f, topY + 4f).x, p(sx * 19f, topY + 4f).y); close()
                }
                drawPath(path, c)
                drawPath(Path().apply {
                    moveTo(p(sx * 9f, topY + 2f).x, p(sx * 9f, topY + 2f).y)
                    lineTo(p(sx * 15f, topY - 5.5f).x, p(sx * 15f, topY - 5.5f).y)
                    lineTo(p(sx * 17f, topY + 3f).x, p(sx * 17f, topY + 3f).y); close()
                }, Color.hsv((t * 60f) % 360f, 0.5f, 1f))
            }
        }
    }

    fun DrawScope.drawPhonesCups() {
        val c = phonesTint()
        val hw = geo.halfW + 2f
        val y = geo.earY
        for (sx in listOf(-1f, 1f)) {
            val e = p(sx * hw, y)
            when (look.headphones) {
                2 -> {
                    drawRoundRect(c, e - Offset(5f * u, 9f * u), s(10f, 18f), CornerRadius(5f * u))
                    drawRoundRect(lerp(c, White, 0.3f), e - Offset((if (sx < 0) 1f else 4f) * u, 7f * u), s(5f, 14f), CornerRadius(2.5f * u))
                }
                5 -> {
                    drawCircle(White, 3.4f * u, e); drawCircle(Color(0xFFDDDDDD), 3.4f * u, e, style = Stroke(0.6f * u))
                    drawRoundRect(White, e + Offset(-1.1f * u, 1.5f * u), s(2.2f, 7f), CornerRadius(1.1f * u))
                }
                6 -> { drawCircle(Color(0xFFFF8A3D), 5.5f * u, e); drawCircle(Color(0xFFE06A20), 5.5f * u, e, style = Stroke(0.7f * u)) }
                7 -> if (sx > 0) drawRoundRect(c, e - Offset(4f * u, 7.5f * u), s(8f, 15f), CornerRadius(3.5f * u))
                    else drawRoundRect(c, p(-hw + 3f, y - 22f), s(8f, 13f), CornerRadius(3.5f * u))
                9 -> { // меховые
                    for (i in 0 until 7) {
                        val a = i * 2 * PI / 7 + t * 0.3
                        drawCircle(lerp(c, White, 0.25f), 2.6f * u, e + Offset(cos(a).toFloat() * 3.2f * u, sin(a).toFloat() * 3.2f * u))
                    }
                    drawCircle(lerp(c, White, 0.4f), 3.4f * u, e)
                }
                else -> {
                    drawRoundRect(c, e - Offset(4f * u, 7.5f * u), s(8f, 15f), CornerRadius(3.5f * u))
                    drawRoundRect(lerp(c, Color.Black, 0.25f), e - Offset((if (sx < 0) 0f else 2.5f) * u, 5.5f * u), s(1.5f, 11f), CornerRadius(1f * u))
                    if (look.headphones == 4 || look.headphones == 3) {
                        drawCircle(Color.hsv((t * 120f + sx * 90f + 360f) % 360f, 0.8f, 1f), 1.6f * u, e)
                    }
                }
            }
        }
        if (look.headphones == 4) { // микрофон
            val path = Path().apply {
                moveTo(p(-hw, y + 4f).x, p(-hw, y + 4f).y)
                quadraticTo(p(-hw + 2f, geo.mouthY + 4f).x, p(-hw + 2f, geo.mouthY + 4f).y, p(-9f, geo.mouthY + 1f).x, p(-9f, geo.mouthY + 1f).y)
            }
            drawPath(path, c, style = Stroke(1.4f * u, cap = StrokeCap.Round))
            drawCircle(lerp(c, Color.Black, 0.3f), 2f * u, p(-9f, geo.mouthY + 1f))
        }
    }

    // ---------------- Шапки ----------------

    fun DrawScope.drawHat() {
        val y0 = geo.top
        fun q(x: Float, y: Float) = p(x, y0 + y)
        fun qr(x: Float, y: Float, w: Float, h: Float) = Rect(q(x, y), s(w, h))
        when (look.hat) {
            1, 2 -> { // кепка
                drawArc(hat, 180f, 180f, true, q(-20f, -12f), s(40f, 26f))
                drawArc(hatDark, 180f, 180f, false, q(-20f, -12f), s(40f, 26f), style = Stroke(0.8f * u))
                drawCircle(hatDark, 1.8f * u, q(0f, -12f))
                if (look.hat == 1) drawOval(hatDark, q(2f, -1.5f), s(28f, 5f))
                else { drawOval(hatDark, q(-26f, -1.5f), s(18f, 4f)); drawRect(White.copy(alpha = 0.6f), q(4f, -6f), s(7f, 3f)) }
                drawRect(lerp(hat, White, 0.4f), q(-6f, -9f), s(12f, 4f))
            }
            3 -> { // шапка с помпоном
                drawRoundRect(hat, q(-21f, -16f), s(42f, 24f), CornerRadius(16f * u, 16f * u))
                for (i in -2..2) drawLine(hatDark.copy(alpha = 0.5f), q(i * 7f, -12f), q(i * 7f, 2f), 0.9f * u)
                drawRoundRect(hatDark, q(-22f, 1f), s(44f, 8f), CornerRadius(3f * u))
                val pom = q(0f, -18f + sin(t * 3f) * 0.6f)
                drawCircle(White, 6.5f * u, pom); drawCircle(Color(0xFFE5E5E5), 6.5f * u, pom, style = Stroke(0.7f * u))
            }
            4 -> { // цилиндр
                drawRect(Color(0xFF1E1E26), q(-12f, -28f), s(24f, 30f))
                drawRect(hat, q(-12f, -8f), s(24f, 5f))
                drawRoundRect(Color(0xFF1E1E26), q(-21f, 0f), s(42f, 4.5f), CornerRadius(2f * u))
                drawLine(White.copy(alpha = 0.2f), q(-8f, -26f), q(-8f, -10f), 1.5f * u)
            }
            5 -> { // корона
                val path = Path().apply {
                    moveTo(q(-17f, 5f).x, q(-17f, 5f).y)
                    lineTo(q(-19f, -14f).x, q(-19f, -14f).y); lineTo(q(-9f, -4f).x, q(-9f, -4f).y)
                    lineTo(q(0f, -19f).x, q(0f, -19f).y); lineTo(q(9f, -4f).x, q(9f, -4f).y)
                    lineTo(q(19f, -14f).x, q(19f, -14f).y); lineTo(q(17f, 5f).x, q(17f, 5f).y); close()
                }
                drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFE680), Gold, Color(0xFFE0A000)), q(0f, -19f).y, q(0f, 5f).y))
                drawPath(path, Color(0xFFC08000), style = Stroke(0.8f * u, join = StrokeJoin.Round))
                drawCircle(hat, 2.4f * u, q(0f, -1f)); drawCircle(Color(0xFF34C759), 1.8f * u, q(-11f, 0f)); drawCircle(Color(0xFF4F7DFF), 1.8f * u, q(11f, 0f))
                for (x in listOf(-19f, 0f, 19f)) drawCircle(White, 1.5f * u, q(x, if (x == 0f) -19f else -14f))
            }
            6 -> { // колпак волшебника
                val path = Path().apply {
                    moveTo(q(-18f, 3f).x, q(-18f, 3f).y)
                    quadraticTo(q(-4f, -18f).x, q(-4f, -18f).y, q(10f + sin(t * 1.5f) * 1.5f, -36f).x, q(10f, -36f).y)
                    quadraticTo(q(6f, -14f).x, q(6f, -14f).y, q(18f, 3f).x, q(18f, 3f).y); close()
                }
                drawPath(path, hat)
                drawPath(star(q(-3f, -12f), 3.5f, 1.5f, 0f), Gold); drawPath(star(q(5f, -24f), 2.5f, 1f, 0f), Gold)
                drawCircle(Gold, 1.2f * u, q(-8f, -3f))
                drawOval(hatDark, q(-26f, -1f), s(52f, 7f))
            }
            7 -> { // ковбойская
                drawRoundRect(hat, q(-13f, -17f), s(26f, 19f), CornerRadius(6f * u, 6f * u))
                drawLine(hatDark, q(0f, -17f), q(0f, -11f), 1.2f * u)
                drawRect(hatDark, q(-13f, -5f), s(26f, 3.5f))
                val brim = Path().apply {
                    moveTo(q(-32f, -7f).x, q(-32f, -7f).y)
                    quadraticTo(q(-26f, 4f).x, q(-26f, 4f).y, q(0f, 4f).x, q(0f, 4f).y)
                    quadraticTo(q(26f, 4f).x, q(26f, 4f).y, q(32f, -7f).x, q(32f, -7f).y)
                    quadraticTo(q(24f, 0f).x, q(24f, 0f).y, q(0f, -1f).x, q(0f, -1f).y)
                    quadraticTo(q(-24f, 0f).x, q(-24f, 0f).y, q(-32f, -7f).x, q(-32f, -7f).y); close()
                }
                drawPath(brim, hat); drawPath(brim, hatDark, style = Stroke(0.7f * u))
            }
            8 -> { // праздничный колпак
                val path = Path().apply { moveTo(q(-13f, 4f).x, q(-13f, 4f).y); lineTo(q(2f, -30f).x, q(2f, -30f).y); lineTo(q(14f, 4f).x, q(14f, 4f).y); close() }
                drawPath(path, hat)
                clipStripes(path)
                drawCircle(Gold, 3.6f * u, q(2f, -31f))
                drawCircle(Color.hsv((t * 80f) % 360f, 0.6f, 1f), 1.2f * u, q(-5f, -8f))
            }
            9 -> { // поварской
                for (x in listOf(-10f, 0f, 10f)) drawCircle(White, 10f * u, q(x, -16f))
                drawCircle(White, 9f * u, q(0f, -22f))
                drawRect(White, q(-15f, -10f), s(30f, 14f))
                for (x in listOf(-8f, 0f, 8f)) drawLine(Color(0xFFE0E0E0), q(x, -8f), q(x, 3f), 0.8f * u)
                drawRect(Color(0xFFE6E6E6), q(-15f, 1f), s(30f, 3f))
            }
            10 -> { // берет
                withTransform({ rotate(-12f, q(0f, -2f)) }) {
                    drawOval(hat, q(-24f, -12f), s(46f, 15f))
                    drawOval(hatDark.copy(alpha = 0.5f), q(-24f, -12f), s(46f, 15f), style = Stroke(0.7f * u))
                    drawLine(hatDark, q(-1f, -12f), q(1f, -16f), 1.6f * u, StrokeCap.Round)
                }
            }
            11 -> { // шапка Санты
                val path = Path().apply {
                    moveTo(q(-21f, 4f).x, q(-21f, 4f).y)
                    quadraticTo(q(-10f, -24f).x, q(-10f, -24f).y, q(10f, -20f).x, q(10f, -20f).y)
                    quadraticTo(q(22f, -16f).x, q(22f, -16f).y, q(25f + sin(t * 2f), -4f).x, q(25f, -4f).y)
                    lineTo(q(21f, 4f).x, q(21f, 4f).y); close()
                }
                drawPath(path, Color(0xFFE53935))
                drawRoundRect(White, q(-23f, 0f), s(46f, 8f), CornerRadius(4f * u))
                drawCircle(White, 4.5f * u, q(25f + sin(t * 2f), -4f))
            }
            12 -> { // шлем викинга
                for (sx in listOf(-1f, 1f)) {
                    val horn = Path().apply {
                        moveTo(q(sx * 16f, -4f).x, q(sx * 16f, -4f).y)
                        quadraticTo(q(sx * 28f, -6f).x, q(sx * 28f, -6f).y, q(sx * 30f, -22f).x, q(sx * 30f, -22f).y)
                        quadraticTo(q(sx * 22f, -10f).x, q(sx * 22f, -10f).y, q(sx * 14f, -12f).x, q(sx * 14f, -12f).y); close()
                    }
                    drawPath(horn, Color(0xFFF5EBD7)); drawPath(horn, Color(0xFFC9B48A), style = Stroke(0.6f * u))
                }
                drawArc(Color(0xFF9EA7B3), 180f, 180f, true, q(-21f, -16f), s(42f, 34f))
                drawRect(hat, q(-21f, -2f), s(42f, 5f))
                for (x in listOf(-15f, -5f, 5f, 15f)) drawCircle(Color(0xFFDDE2E8), 1f * u, q(x, 0.5f))
                drawRect(Color(0xFF7D8590), q(-1.5f, -16f), s(3f, 16f))
            }
            13 -> { // пропеллер
                val colors = listOf(Color(0xFFFF5E5E), Color(0xFFFFD93D), Color(0xFF34C759), Color(0xFF4F7DFF))
                for (i in 0..3) drawArc(colors[i], 180f + i * 45f, 45f, true, q(-20f, -12f), s(40f, 26f))
                drawOval(hatDark, q(2f, -1.5f), s(24f, 4.5f))
                drawLine(Ink, q(0f, -12f), q(0f, -17f), 1.2f * u)
                val k = cos(t * 18f)
                drawOval(hat, q(-12f * abs(k), -19.5f), s(24f * abs(k) + 0.5f, 3f))
                drawCircle(Ink, 1.4f * u, q(0f, -18f))
            }
            14 -> { // нимб
                val hy = -10f + sin(t * 2f) * 1.5f
                drawOval(Gold.copy(alpha = 0.35f), q(-17f, hy - 4f), s(34f, 10f), style = Stroke(4f * u))
                drawOval(Gold, q(-15f, hy - 3f), s(30f, 8f), style = Stroke(2f * u))
            }
            15 -> for (sx in listOf(-1f, 1f)) { // рожки
                val path = Path().apply {
                    moveTo(q(sx * 8f, 4f).x, q(sx * 8f, 4f).y)
                    quadraticTo(q(sx * 12f, -6f).x, q(sx * 12f, -6f).y, q(sx * 18f, -11f).x, q(sx * 18f, -11f).y)
                    quadraticTo(q(sx * 17f, -2f).x, q(sx * 17f, -2f).y, q(sx * 16f, 5f).x, q(sx * 16f, 5f).y); close()
                }
                drawPath(path, Color(0xFFE53935))
            }
            16 -> { // бантик
                val c = q(13f, 2f)
                for (sx in listOf(-1f, 1f)) {
                    val wing = Path().apply { moveTo(c.x, c.y); lineTo(c.x + sx * 9f * u, c.y - 6f * u); lineTo(c.x + sx * 9f * u, c.y + 6f * u); close() }
                    drawPath(wing, hat, style = Fill)
                    drawPath(wing, hatDark, style = Stroke(0.8f * u, join = StrokeJoin.Round))
                }
                drawCircle(hatDark, 2.6f * u, c)
            }
            17 -> for (sx in listOf(-1f, 1f)) { // ушки зайки
                val ang = sx * (12f + sin(t * 2.5f + sx) * 4f)
                withTransform({ rotate(ang, q(sx * 9f, 4f)) }) {
                    drawOval(hat, q(sx * 9f - 5.5f, -30f), s(11f, 36f))
                    drawOval(Pink, q(sx * 9f - 3f, -25f), s(6f, 26f))
                }
            }
            18 -> { // сомбреро
                drawOval(hat, q(-38f, -6f), s(76f, 13f))
                drawOval(hatDark, q(-38f, -6f), s(76f, 13f), style = Stroke(0.8f * u))
                drawArc(hat, 180f, 180f, true, q(-12f, -24f), s(24f, 40f))
                drawRect(Color(0xFF34C759), q(-11.5f, -6f), s(23f, 3f))
                for (x in listOf(-30f, -18f, 18f, 30f)) drawCircle(Color(0xFFFF5E5E), 1.4f * u, q(x, 0.5f))
            }
            19 -> { // треуголка
                val path = Path().apply {
                    moveTo(q(-28f, -6f).x, q(-28f, -6f).y)
                    quadraticTo(q(-16f, -10f).x, q(-16f, -10f).y, q(0f, -18f).x, q(0f, -18f).y)
                    quadraticTo(q(16f, -10f).x, q(16f, -10f).y, q(28f, -6f).x, q(28f, -6f).y)
                    quadraticTo(q(14f, 6f).x, q(14f, 6f).y, q(0f, 3f).x, q(0f, 3f).y)
                    quadraticTo(q(-14f, 6f).x, q(-14f, 6f).y, q(-28f, -6f).x, q(-28f, -6f).y); close()
                }
                drawPath(path, Color(0xFF1E1E26)); drawPath(path, Gold, style = Stroke(1f * u))
                drawCircle(White, 3f * u, q(0f, -8f)); drawCircle(Color(0xFF1E1E26), 0.8f * u, q(-1f, -8.5f)); drawCircle(Color(0xFF1E1E26), 0.8f * u, q(1f, -8.5f))
                drawLine(White, q(-4f, -3.5f), q(4f, -1f), 1f * u); drawLine(White, q(4f, -3.5f), q(-4f, -1f), 1f * u)
            }
            20 -> { // панама
                val path = Path().apply {
                    moveTo(q(-15f, -14f).x, q(-15f, -14f).y); lineTo(q(15f, -14f).x, q(15f, -14f).y)
                    lineTo(q(19f, 0f).x, q(19f, 0f).y); lineTo(q(-19f, 0f).x, q(-19f, 0f).y); close()
                }
                drawPath(path, hat)
                val brim = Path().apply {
                    moveTo(q(-19f, -1f).x, q(-19f, -1f).y); lineTo(q(19f, -1f).x, q(19f, -1f).y)
                    lineTo(q(26f, 6f).x, q(26f, 6f).y); quadraticTo(q(0f, 9f).x, q(0f, 9f).y, q(-26f, 6f).x, q(-26f, 6f).y); close()
                }
                drawPath(brim, hatDark)
                for (i in 0..4) drawCircle(White.copy(alpha = 0.7f), 1f * u, q(-10f + i * 5f, -8f + (i % 2) * 3f))
            }
            21 -> { // каска
                drawArc(Color(0xFFFFC107), 180f, 180f, true, q(-21f, -18f), s(42f, 36f))
                drawRoundRect(Color(0xFFFFB300), q(-4f, -18f), s(8f, 18f), CornerRadius(3f * u))
                drawRoundRect(Color(0xFFE0A000), q(-25f, -1f), s(50f, 5f), CornerRadius(2.5f * u))
            }
            22 -> { // венок
                val flowers = listOf(Color(0xFFFF6FB5), Color(0xFFFFD93D), Color(0xFFFFFFFF), Color(0xFFA66CFF), Color(0xFFFF8A5B))
                for (i in 0..8) {
                    val a = PI + i * PI / 8
                    val c = q(cos(a).toFloat() * 22f, 6f + sin(a).toFloat() * 9f)
                    drawCircle(Color(0xFF34C759), 2.6f * u, c + Offset(1.6f * u, 1f * u))
                    for (k in 0..4) {
                        val b = k * 2 * PI / 5 + t * 0.5
                        drawCircle(flowers[i % flowers.size], 1.6f * u, c + Offset(cos(b).toFloat() * 1.8f * u, sin(b).toFloat() * 1.8f * u))
                    }
                    drawCircle(Gold, 1f * u, c)
                }
            }
            23 -> { // тюбетейка
                val path = Path().apply {
                    moveTo(q(-17f, 3f).x, q(-17f, 3f).y); lineTo(q(-14f, -10f).x, q(-14f, -10f).y)
                    quadraticTo(q(0f, -14f).x, q(0f, -14f).y, q(14f, -10f).x, q(14f, -10f).y); lineTo(q(17f, 3f).x, q(17f, 3f).y); close()
                }
                drawPath(path, hat)
                for (x in listOf(-9f, 0f, 9f)) {
                    drawPath(star(q(x, -4f), 3.2f, 1.5f, 0f), White)
                    drawCircle(Gold, 0.9f * u, q(x, -4f))
                }
                drawLine(Gold, q(-17f, 2f), q(17f, 2f), 1.3f * u)
            }
            24 -> { // ушанка
                for (sx in listOf(-1f, 1f)) drawRoundRect(lerp(hat, White, 0.15f), q(sx * 22f - 6f, -2f), s(12f, 22f), CornerRadius(6f * u))
                drawArc(hat, 180f, 180f, true, q(-22f, -16f), s(44f, 34f))
                drawRoundRect(lerp(hat, White, 0.45f), q(-23f, -4f), s(46f, 9f), CornerRadius(4f * u))
                drawPath(star(q(0f, -8f), 3f, 1.3f, 0f), Color(0xFFE53935))
            }
            25 -> { // котелок
                drawArc(Color(0xFF2B2420), 180f, 180f, true, q(-14f, -20f), s(28f, 40f))
                drawRect(hat, q(-14f, -4f), s(28f, 3f))
                drawPath(Path().apply {
                    moveTo(q(-21f, -2f).x, q(-21f, -2f).y); quadraticTo(q(0f, 6f).x, q(0f, 6f).y, q(21f, -2f).x, q(21f, -2f).y)
                    quadraticTo(q(0f, 2f).x, q(0f, 2f).y, q(-21f, -2f).x, q(-21f, -2f).y); close()
                }, Color(0xFF2B2420))
            }
            26 -> { // диадема
                drawArc(Color(0xFFDDE2E8), 200f, 140f, false, q(-18f, -2f), s(36f, 18f), style = Stroke(1.6f * u))
                drawPath(star(q(0f, -4f), 4f, 1.8f, 0f), hat)
                for (x in listOf(-7f, 7f)) drawCircle(Color(0xFFBFE9FF), 1.4f * u, q(x, -1f))
                val sp = (sin(t * 3f) + 1) / 2
                drawPath(star(q(3f, -8f), 2f * sp, 0.6f * sp, 0f), White)
            }
            27 -> { // ирокез
                for (i in -3..3) {
                    val c = Color.hsv(((i + 3) * 40f + t * 30f) % 360f, 0.7f, 1f).let { if (look.hatColor != Wardrobe.RAINBOW) lerp(hat, it, 0.25f) else it }
                    val path = Path().apply {
                        moveTo(q(i * 4f - 3f, 4f).x, q(i * 4f - 3f, 4f).y)
                        lineTo(q(i * 4.6f, -14f + abs(i) * 2.5f + sin(t * 3f + i) * 0.8f).x, q(i * 4.6f, -14f + abs(i) * 2.5f).y)
                        lineTo(q(i * 4f + 3f, 4f).x, q(i * 4f + 3f, 4f).y); close()
                    }
                    drawPath(path, c)
                }
            }
            28 -> for (sx in listOf(-1f, 1f)) { // антенны-сердечки
                val tip = q(sx * 11f + sin(t * 3f + sx) * 2f, -16f)
                drawPath(Path().apply { moveTo(q(sx * 6f, 3f).x, q(sx * 6f, 3f).y); quadraticTo(q(sx * 9f, -6f).x, q(sx * 9f, -6f).y, tip.x, tip.y) }, Ink, style = Stroke(1f * u))
                drawPath(heart(tip, 5f), hat)
            }
            29 -> { // капюшон-лягушка
                drawArc(Color(0xFF5CC35C), 180f, 180f, true, q(-25f, -10f), s(50f, 30f))
                for (sx in listOf(-1f, 1f)) {
                    drawCircle(Color(0xFF5CC35C), 6.5f * u, q(sx * 11f, -11f))
                    drawCircle(White, 4.5f * u, q(sx * 11f, -11f))
                    drawCircle(Ink, 2.2f * u, q(sx * 11f + 0.6f, -11f))
                }
                drawArc(Color(0xFF3E9E3E), 20f, 140f, false, q(-6f, -8f), s(12f, 5f), style = Stroke(0.9f * u))
            }
        }
    }

    fun DrawScope.clipStripes(path: Path) {
        val top = geo.top
        clipPath(path) { // наклонные полоски
            for (i in -4..4) drawLine(White.copy(alpha = 0.55f), p(i * 7f - 12f, top + 6f), p(i * 7f + 12f, top - 32f), 2f * u)
        }
    }

    // ---------------- Аксессуары ----------------

    fun DrawScope.drawAccessory() {
        val n = p(0f, geo.neckY)
        when (look.accessory) {
            1 -> { // бабочка
                for (sx in listOf(-1f, 1f)) drawPath(Path().apply { moveTo(n.x, n.y); lineTo(n.x + sx * 8f * u, n.y - 4.5f * u); lineTo(n.x + sx * 8f * u, n.y + 4.5f * u); close() }, acc)
                drawCircle(lerp(acc, Color.Black, 0.25f), 2f * u, n)
            }
            2 -> { // галстук
                drawPath(Path().apply {
                    moveTo(n.x - 2.5f * u, n.y - 2f * u); lineTo(n.x + 2.5f * u, n.y - 2f * u); lineTo(n.x + 2f * u, n.y + 2f * u); lineTo(n.x - 2f * u, n.y + 2f * u); close()
                }, lerp(acc, Color.Black, 0.2f))
                drawPath(Path().apply {
                    moveTo(n.x - 2f * u, n.y + 2f * u); lineTo(n.x + 2f * u, n.y + 2f * u); lineTo(n.x + 4f * u, n.y + 12f * u)
                    lineTo(n.x, n.y + 15f * u); lineTo(n.x - 4f * u, n.y + 12f * u); close()
                }, acc)
                for (i in 0..2) drawLine(lerp(acc, Color.Black, 0.25f), n + Offset(-3f * u, (5f + i * 3f) * u), n + Offset(3f * u, (3f + i * 3f) * u), 0.8f * u)
            }
            3 -> { // шарф
                drawRoundRect(acc, Offset(n.x - 21f * u, n.y - 3.5f * u), s(42f, 7f), CornerRadius(3.5f * u))
                withTransform({ rotate(8f + sin(t * 2f) * 4f, n + Offset(10f * u, 0f)) }) {
                    drawRoundRect(acc, n + Offset(7f * u, 0f), s(7f, 16f), CornerRadius(2f * u))
                    for (i in 0..2) drawRect(White.copy(alpha = 0.6f), n + Offset(7f * u, (4f + i * 4f) * u), s(7f, 1.4f))
                }
                for (i in -3..3) drawRect(White.copy(alpha = 0.6f), Offset(n.x + i * 6f * u - 0.7f * u, n.y - 3.5f * u), s(1.4f, 7f))
            }
            4 -> for (i in 0..10) { // жемчуг
                val a = PI * 0.15 + i * PI * 0.7 / 10
                drawCircle(Color(0xFFFFFDF5), 1.7f * u, n + Offset(cos(a).toFloat() * 16f * u, (sin(a).toFloat() * 6f - 4f) * u))
                drawCircle(Color(0xFFD9D4C7), 1.7f * u, n + Offset(cos(a).toFloat() * 16f * u, (sin(a).toFloat() * 6f - 4f) * u), style = Stroke(0.4f * u))
            }
            5 -> { // медаль
                drawPath(Path().apply { moveTo(n.x - 6f * u, n.y - 3f * u); lineTo(n.x, n.y + 5f * u); lineTo(n.x + 6f * u, n.y - 3f * u); lineTo(n.x + 3f * u, n.y - 3f * u); lineTo(n.x, n.y + 1f * u); lineTo(n.x - 3f * u, n.y - 3f * u); close() }, acc)
                drawCircle(Gold, 4.5f * u, n + Offset(0f, 8f * u)); drawPath(star(n + Offset(0f, 8f * u), 2.8f, 1.2f, 0f), Color(0xFFE0A000))
            }
            6 -> { // цепь
                for (i in 0..12) {
                    val a = PI * 0.12 + i * PI * 0.76 / 12
                    drawOval(Gold, n + Offset(cos(a).toFloat() * 17f * u - 1.4f * u, (sin(a).toFloat() * 9f - 6f) * u - 1f * u), s(2.8f, 2f), style = Stroke(0.7f * u))
                }
                drawCircle(Gold, 3.6f * u, n + Offset(0f, 4.5f * u))
                drawPath(star(n + Offset(0f, 4.5f * u), 2.2f, 1f, t), White.copy(alpha = 0.8f))
            }
            7 -> { // бант
                for (sx in listOf(-1f, 1f)) drawOval(acc, n + Offset(if (sx < 0) -12f * u else 0f, -4f * u), s(12f, 8f))
                drawCircle(lerp(acc, Color.Black, 0.2f), 2.5f * u, n)
                for (sx in listOf(-1f, 1f)) drawLine(acc, n, n + Offset(sx * 4f * u, 8f * u), 2f * u, StrokeCap.Round)
            }
            8 -> drawPath(star(n + Offset(-10f * u, 2f * u), 4.5f, 2f, 0f), acc)
            9 -> { // бандана
                drawPath(Path().apply { moveTo(n.x - 17f * u, n.y - 3f * u); lineTo(n.x + 17f * u, n.y - 3f * u); lineTo(n.x, n.y + 10f * u); close() }, acc)
                for (i in 0..3) drawCircle(White.copy(alpha = 0.8f), 0.9f * u, n + Offset((-6f + i * 4f) * u, (i % 2) * 2f * u))
            }
            10 -> { // колокольчик
                drawRoundRect(acc, Offset(n.x - 17f * u, n.y - 2.5f * u), s(34f, 4f), CornerRadius(2f * u))
                withTransform({ rotate(sin(t * 5f) * 15f, n) }) {
                    drawCircle(Gold, 3.4f * u, n + Offset(0f, 3.5f * u))
                    drawLine(Color(0xFFC08000), n + Offset(-2f * u, 4f * u), n + Offset(2f * u, 4f * u), 0.7f * u)
                }
            }
            12 -> { // ошейник с шипами
                drawRoundRect(Color(0xFF2B2B35), Offset(n.x - 18f * u, n.y - 2.5f * u), s(36f, 5f), CornerRadius(2f * u))
                for (i in -3..3) drawPath(Path().apply {
                    moveTo(n.x + (i * 5f - 1.5f) * u, n.y + 2.5f * u); lineTo(n.x + i * 5f * u, n.y + 5.5f * u); lineTo(n.x + (i * 5f + 1.5f) * u, n.y + 2.5f * u); close()
                }, Color(0xFFDDE2E8))
            }
        }
    }

    fun DrawScope.drawCape() {
        val wave = sin(t * 2.5f) * 2f
        val path = Path().apply {
            moveTo(p(-18f, geo.neckY - 4f).x, p(-18f, geo.neckY - 4f).y)
            lineTo(p(18f, geo.neckY - 4f).x, p(18f, geo.neckY - 4f).y)
            quadraticTo(p(30f + wave, 80f).x, p(30f + wave, 80f).y, p(34f + wave, 96f).x, p(34f, 96f).y)
            quadraticTo(p(0f, 92f).x, p(0f, 92f).y, p(-34f - wave, 96f).x, p(-34f, 96f).y)
            quadraticTo(p(-30f - wave, 80f).x, p(-30f, 80f).y, p(-18f, geo.neckY - 4f).x, p(-18f, geo.neckY - 4f).y)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(acc, lerp(acc, Color.Black, 0.35f)), p(0f, geo.neckY).y, p(0f, 96f).y))
    }

    // ---------------- Эмоциональные значки и ауры ----------------

    fun DrawScope.drawEmotionMarks() {
        val side = p(24f, geo.top - 2f)
        when (emo) {
            Emotion.SLEEPY -> for (i in 0..2) {
                val k = (t * 0.4f + i / 3f) % 1f
                text("z", side + Offset((k * 8f) * u, (-k * 18f) * u), (5f + k * 6f) * u, Ink.copy(alpha = 1f - k))
            }
            Emotion.SURPRISED -> text("!", side + Offset(0f, -2f * u + sin(t * 10f) * u), 13f * u, Color(0xFFFFB300))
            Emotion.CONFUSED -> text("?", side + Offset(sin(t * 3f) * u, 0f), 13f * u, Color(0xFF7B5CE0))
            Emotion.THINKING -> for (i in 0..2) {
                val on = ((t * 2f).toInt() % 4) > i
                drawCircle(Ink.copy(alpha = if (on) 0.8f else 0.2f), (1.4f + i * 0.5f) * u, side + Offset(i * 4f * u, -i * 4f * u))
            }
            Emotion.ANGRY -> {
                val c = p(20f, geo.top + 2f)
                val k = 1f + sin(t * 12f) * 0.12f
                for (a in 0 until 4) {
                    rotate(a * 90f + 45f, c) {
                        drawArc(Color(0xFFE53935), 200f, 140f, false, c + Offset(1.5f * u * k, -2f * u * k), s(4f * k, 4f * k), style = Stroke(1.2f * u))
                    }
                }
            }
            Emotion.SCARED -> {
                val k = (t * 0.7f) % 1f
                drawPath(drop(p(-22f, geo.top + 10f + k * 6f), 2.6f), Color(0xFF8ED8FF).copy(alpha = 1f - k * 0.6f))
            }
            Emotion.LOVE -> for (i in 0..2) {
                val k = (t * 0.5f + i / 3f) % 1f
                drawPath(heart(p(-20f + i * 20f + sin(t * 2f + i) * 3f, geo.top - k * 22f), 3f + k * 2f), Color(0xFFFF3B6B).copy(alpha = 1f - k))
            }
            Emotion.EXCITED -> for (i in 0..3) {
                val a = t * 1.5f + i * PI.toFloat() / 2
                drawPath(star(p(cos(a) * 34f, 58f + sin(a) * 30f), 3f, 1.2f, t * 3f), Gold)
            }
            Emotion.LAUGHING -> for (sx in listOf(-1f, 1f)) {
                val k = (t * 1.2f) % 1f
                drawPath(drop(p(sx * 17f, geo.eyeY + 1f + k * 4f), 1.8f), Color(0xFF8ED8FF).copy(alpha = 1f - k))
            }
            Emotion.COOL -> {
                val sp = (sin(t * 2.5f) + 1f) / 2f
                drawPath(star(p(17f, geo.eyeY - 6f), 3.2f * sp, 0.8f * sp, 0f), White)
            }
            else -> {}
        }
    }

    fun DrawScope.drawAuraBack() {
        when (look.aura) {
            1 -> {
                val k = 1f + sin(t * 2f) * 0.06f
                drawCircle(Brush.radialGradient(listOf(body.copy(alpha = 0.55f), Color.Transparent), p(0f, 60f), 46f * u * k), 46f * u * k, p(0f, 60f))
            }
        }
    }

    fun DrawScope.drawAuraFront() {
        when (look.aura) {
            2 -> for (i in 0..5) {
                val a = t * 0.8f + i * PI.toFloat() / 3
                val tw = (sin(t * 5f + i * 2f) + 1f) / 2f
                drawPath(star(p(cos(a) * 38f, 58f + sin(a) * 32f), 2.5f + tw * 1.5f, 0.8f, 0f), Gold.copy(alpha = 0.4f + tw * 0.6f))
            }
            3 -> particles(0.25f, rising = true) { c, k, _ -> drawPath(heart(c, 3.2f), Color(0xFFFF6FB5).copy(alpha = 1f - k)) }
            4 -> particles(0.18f, rising = false) { c, k, i ->
                for (a in 0 until 3) rotate(a * 60f + t * 40f + i * 20f, c) {
                    drawLine(White.copy(alpha = 1f - k * 0.5f), c - Offset(2.4f * u, 0f), c + Offset(2.4f * u, 0f), 0.6f * u)
                }
            }
            5 -> particles(0.22f, rising = true) { c, k, _ ->
                val col = Ink.copy(alpha = 1f - k)
                drawOval(col, c - Offset(1.6f * u, 1.1f * u), s(3.2f, 2.3f))
                drawLine(col, c + Offset(1.4f * u, 0f), c + Offset(1.4f * u, -6f * u), 0.6f * u)
                drawLine(col, c + Offset(1.4f * u, -6f * u), c + Offset(3.4f * u, -4.5f * u), 0.6f * u)
            }
            6 -> particles(0.2f, rising = true) { c, k, i ->
                val rad = (1.5f + (i % 3)) * u
                drawCircle(Color(0xFF8ED8FF).copy(alpha = 0.8f - k * 0.6f), rad, c, style = Stroke(0.5f * u))
                drawCircle(White.copy(alpha = 0.7f - k * 0.5f), rad * 0.3f, c + Offset(-rad * 0.4f, -rad * 0.4f))
            }
            7 -> for (i in 0..6) {
                val a = t * 0.3f + i * 0.9f
                val c = p(cos(a * 1.3f + i) * 38f, 55f + sin(a * 1.7f + i * 2f) * 32f)
                val tw = (sin(t * 4f + i * 1.3f) + 1f) / 2f
                drawCircle(Color(0xFFFFF59D).copy(alpha = tw * 0.4f), 3.5f * u, c)
                drawCircle(Color(0xFFFFEB3B).copy(alpha = tw), 1.2f * u, c)
            }
        }
    }

    inline fun particles(speed: Float, rising: Boolean, draw: (Offset, Float, Int) -> Unit) {
        for (i in 0..5) {
            val k = (t * speed + i / 6f) % 1f
            val x = sin(i * 1.9f + t * 0.8f) * 36f
            val y = if (rising) 92f - k * 90f else 2f + k * 90f
            draw(p(x, y), k, i)
        }
    }

    // ---------------- Геометрия ----------------

    fun heart(c: Offset, size: Float): Path {
        val w = size * u
        return Path().apply {
            moveTo(c.x, c.y + w * 0.75f)
            cubicTo(c.x - w * 1.1f, c.y, c.x - w * 0.6f, c.y - w * 0.9f, c.x, c.y - w * 0.35f)
            cubicTo(c.x + w * 0.6f, c.y - w * 0.9f, c.x + w * 1.1f, c.y, c.x, c.y + w * 0.75f)
            close()
        }
    }

    fun star(c: Offset, outer: Float, inner: Float, rot: Float): Path = Path().apply {
        for (i in 0 until 10) {
            val a = -PI / 2 + i * PI / 5 + rot
            val rr = (if (i % 2 == 0) outer else inner) * u
            val x = c.x + cos(a).toFloat() * rr
            val y = c.y + sin(a).toFloat() * rr
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    fun drop(c: Offset, size: Float): Path {
        val w = size * u
        return Path().apply {
            moveTo(c.x, c.y - w * 1.6f)
            quadraticTo(c.x + w, c.y, c.x, c.y + w)
            quadraticTo(c.x - w, c.y, c.x, c.y - w * 1.6f)
            close()
        }
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    fun DrawScope.text(s: String, at: Offset, sizePx: Float, c: Color) {
        textPaint.textSize = sizePx
        textPaint.color = c.toArgb()
        drawContext.canvas.nativeCanvas.drawText(s, at.x, at.y, textPaint)
    }
}
