package com.vse.launcher.ui.tools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vse.launcher.data.LauncherViewModel
import com.vse.launcher.ui.ScreenScaffold
import com.vse.launcher.util.SystemActions
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ---------------- Фонарик ----------------

@Composable
fun FlashlightTool(vm: LauncherViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var sos by remember { mutableStateOf(false) }
    LaunchedEffect(sos) {
        if (!sos) return@LaunchedEffect
        // ... --- ...
        val pattern = listOf(200, 200, 200, 600, 600, 600, 200, 200, 200)
        while (sos) {
            for (d in pattern) {
                if (!sos) break
                SystemActions.setTorch(ctx, true); delay(d.toLong())
                SystemActions.setTorch(ctx, false); delay(200)
            }
            delay(1000)
        }
    }
    DisposableEffect(Unit) { onDispose { if (sos) SystemActions.setTorch(ctx, false) } }

    ScreenScaffold("Фонарик", onBack) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(if (vm.torchOn) Color(0xFFFFD166) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { sos = false; vm.toggleTorch() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (vm.torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, "Фонарик",
                    modifier = Modifier.size(96.dp),
                    tint = if (vm.torchOn) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(if (vm.torchOn) "Включён" else "Выключен", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            OutlinedButton({ if (vm.torchOn) vm.toggleTorch(); sos = !sos }) { Text(if (sos) "Остановить SOS" else "Сигнал SOS") }
        }
    }
}

// ---------------- Сенсоры ----------------

@Composable
private fun rememberSensorValues(type: Int): FloatArray? {
    val ctx = LocalContext.current
    var values by remember { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(type) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(type)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { values = e.values.clone() }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return values
}

private fun direction(deg: Int) = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")[((deg + 22) % 360) / 45]

@Composable
fun CompassTool(onBack: () -> Unit) {
    val rv = rememberSensorValues(Sensor.TYPE_ROTATION_VECTOR)
    var smoothed by remember { mutableFloatStateOf(0f) }
    val azimuth = rv?.let {
        val m = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(m, it)
        val o = FloatArray(3)
        SensorManager.getOrientation(m, o)
        ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
    }
    if (azimuth != null) {
        var diff = azimuth - smoothed
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        smoothed = (smoothed + diff * 0.2f + 360) % 360
    }
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    ScreenScaffold("Компас", onBack) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (azimuth == null) {
                Text("Датчик ориентации не найден или ещё не готов", modifier = Modifier.padding(24.dp))
            }
            val deg = smoothed.roundToInt() % 360
            Text("$deg° ${direction(deg)}", fontSize = 44.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(24.dp))
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(300.dp).rotate(-smoothed)) {
                    val r = size.minDimension / 2
                    drawCircle(onSurface.copy(alpha = 0.15f), r, style = Stroke(4f))
                    for (i in 0 until 360 step 15) {
                        val a = Math.toRadians(i.toDouble() - 90)
                        val len = if (i % 90 == 0) 30f else if (i % 45 == 0) 20f else 10f
                        val start = Offset(center.x + (r - len) * kotlin.math.cos(a).toFloat(), center.y + (r - len) * kotlin.math.sin(a).toFloat())
                        val end = Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat())
                        drawLine(onSurface.copy(alpha = 0.6f), start, end, strokeWidth = if (i % 90 == 0) 6f else 3f)
                    }
                    // Стрелка на север
                    val north = Path().apply {
                        moveTo(center.x, center.y - r + 40); lineTo(center.x - 22, center.y); lineTo(center.x + 22, center.y); close()
                    }
                    val south = Path().apply {
                        moveTo(center.x, center.y + r - 40); lineTo(center.x - 22, center.y); lineTo(center.x + 22, center.y); close()
                    }
                    drawPath(north, Color(0xFFE53935))
                    drawPath(south, onSurface.copy(alpha = 0.4f))
                    drawCircle(primary, 10f)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Держите телефон горизонтально", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LevelTool(onBack: () -> Unit) {
    val grav = rememberSensorValues(Sensor.TYPE_GRAVITY)
    val accel = rememberSensorValues(Sensor.TYPE_ACCELEROMETER)
    val g = grav ?: accel
    val x = g?.getOrNull(0) ?: 0f
    val y = g?.getOrNull(1) ?: 0f
    val z = g?.getOrNull(2) ?: 9.81f
    val pitch = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat()
    val roll = Math.toDegrees(atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
    val level = abs(pitch) < 1f && abs(roll) < 1f
    val bubbleColor = if (level) Color(0xFF00B894) else MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.onSurface

    ScreenScaffold("Уровень", onBack) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(300.dp)) {
                val r = size.minDimension / 2
                drawCircle(ring.copy(alpha = 0.15f), r, style = Stroke(4f))
                drawCircle(ring.copy(alpha = 0.3f), r / 4, style = Stroke(3f))
                drawLine(ring.copy(alpha = 0.2f), Offset(center.x, 0f), Offset(center.x, size.height), 2f)
                drawLine(ring.copy(alpha = 0.2f), Offset(0f, center.y), Offset(size.width, center.y), 2f)
                val maxDeg = 30f
                val bx = center.x + (roll.coerceIn(-maxDeg, maxDeg) / maxDeg) * (r - 40)
                val by = center.y - (pitch.coerceIn(-maxDeg, maxDeg) / maxDeg) * (r - 40)
                drawCircle(bubbleColor, 36f, Offset(bx, by))
            }
            Spacer(Modifier.height(24.dp))
            Text("X: ${"%.1f".format(roll)}°   Y: ${"%.1f".format(pitch)}°", fontSize = 24.sp)
            if (level) Text("Ровно ✓", color = Color(0xFF00B894), fontSize = 20.sp, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.height(8.dp))
            Text("Положите телефон на поверхность", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------- Об устройстве ----------------

private fun human(bytes: Long): String {
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gb >= 1) "%.1f ГБ".format(gb) else "%.0f МБ".format(bytes / 1024.0 / 1024.0)
}

@Composable
fun DeviceInfoTool(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }

    val rows = remember(tick) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(Environment.getDataDirectory().path)
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100) }
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f)
        val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val health = when (battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Хорошее"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Износ"
            BatteryManager.BATTERY_HEALTH_COLD -> "Холод"
            else -> "Неизвестно"
        }
        val up = SystemClock.elapsedRealtime() / 1000
        val dm = ctx.resources.displayMetrics
        listOf(
            "Устройство" to listOf(
                "Модель" to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "Процессор" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "—") + " · ${Runtime.getRuntime().availableProcessors()} ядер",
                "Экран" to "${dm.widthPixels}×${dm.heightPixels}, ${dm.densityDpi} dpi",
                "Работает без перезагрузки" to "%d ч %02d мин".format(up / 3600, up / 60 % 60),
            ),
            "Память" to listOf(
                "ОЗУ занято" to "${human(mem.totalMem - mem.availMem)} из ${human(mem.totalMem)}",
                "Хранилище занято" to "${human(stat.totalBytes - stat.availableBytes)} из ${human(stat.totalBytes)}",
                "Свободно" to human(stat.availableBytes),
            ),
            "Батарея" to listOf(
                "Заряд" to "${level ?: "—"}%",
                "Температура" to "${temp ?: "—"} °C",
                "Напряжение" to "${voltage ?: "—"} мВ",
                "Состояние" to health,
            ),
        ) to listOf(
            (mem.totalMem - mem.availMem).toFloat() / mem.totalMem,
            (stat.totalBytes - stat.availableBytes).toFloat() / stat.totalBytes,
        )
    }

    ScreenScaffold("Об устройстве", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Gauge("ОЗУ", rows.second[0], Modifier.weight(1f))
                Gauge("Хранилище", rows.second[1], Modifier.weight(1f))
            }
            rows.first.forEach { (section, items) ->
                Text(section, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
                items.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(k, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Gauge(label: String, fraction: Float, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(96.dp), strokeWidth = 8.dp)
                Text("${(fraction * 100).roundToInt()}%", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(label)
        }
    }
}
