package com.vse.launcher.ui.tools

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vse.launcher.ui.ScreenScaffold
import kotlinx.coroutines.delay

private fun formatMs(ms: Long, centis: Boolean = true): String {
    val h = ms / 3_600_000
    val m = ms / 60_000 % 60
    val s = ms / 1000 % 60
    val c = ms / 10 % 100
    val base = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return if (centis) "$base.%02d".format(c) else base
}

// ---------------- Секундомер ----------------

/** Состояние секундомера живёт в объекте, чтобы не сбрасываться при выходе с экрана. */
private object StopwatchState {
    var running by mutableStateOf(false)
    var startedAt = 0L
    var accumulated = 0L
    val laps = mutableStateListOf<Long>()
    fun elapsed(now: Long) = accumulated + if (running) now - startedAt else 0L
}

@Composable
fun StopwatchTool(onBack: () -> Unit) {
    val st = StopwatchState
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st.running) {
        while (st.running) {
            now = System.currentTimeMillis()
            delay(16)
        }
        now = System.currentTimeMillis()
    }
    val elapsed = st.elapsed(now)

    ScreenScaffold("Секундомер", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            Text(formatMs(elapsed), fontSize = 64.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = {
                        if (st.running) st.laps.add(0, elapsed)
                        else { st.accumulated = 0; st.laps.clear(); now = System.currentTimeMillis() }
                    },
                    modifier = Modifier.size(width = 130.dp, height = 56.dp),
                ) { Text(if (st.running) "Круг" else "Сброс") }
                Button(
                    onClick = {
                        val t = System.currentTimeMillis()
                        if (st.running) { st.accumulated += t - st.startedAt; st.running = false }
                        else { st.startedAt = t; st.running = true }
                    },
                    modifier = Modifier.size(width = 130.dp, height = 56.dp),
                ) {
                    Icon(if (st.running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (st.running) "Пауза" else "Старт")
                }
            }
            Spacer(Modifier.height(24.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(st.laps) { i, lap ->
                    val prev = st.laps.getOrNull(i + 1) ?: 0L
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp)) {
                        Text("Круг ${st.laps.size - i}", modifier = Modifier.weight(1f))
                        Text("+" + formatMs(lap - prev), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(16.dp))
                        Text(formatMs(lap), fontFamily = FontFamily.Monospace)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// ---------------- Таймер ----------------

private object TimerState {
    var endAt by mutableLongStateOf(0L)       // момент окончания, если идёт
    var pausedLeft by mutableLongStateOf(0L)  // остаток на паузе
    var total by mutableLongStateOf(0L)
    var ringing by mutableStateOf(false)
    var ringtone: Ringtone? = null
}

private fun vibrator(ctx: Context): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)

private fun startRinging(ctx: Context) {
    TimerState.ringing = true
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        TimerState.ringtone = RingtoneManager.getRingtone(ctx, uri)?.also { it.play() }
    }
    runCatching { vibrator(ctx).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500, 300, 500), -1)) }
}

private fun stopRinging(ctx: Context) {
    TimerState.ringing = false
    runCatching { TimerState.ringtone?.stop() }
    TimerState.ringtone = null
    runCatching { vibrator(ctx).cancel() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerTool(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val st = TimerState
    var minutes by remember { mutableIntStateOf(5) }
    var seconds by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = st.endAt > 0

    LaunchedEffect(running) {
        while (st.endAt > 0) {
            now = System.currentTimeMillis()
            if (now >= st.endAt) {
                st.endAt = 0
                st.pausedLeft = 0
                startRinging(ctx)
                break
            }
            delay(100)
        }
    }
    val left = when {
        running -> (st.endAt - now).coerceAtLeast(0)
        st.pausedLeft > 0 -> st.pausedLeft
        else -> (minutes * 60L + seconds) * 1000
    }

    ScreenScaffold("Таймер", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (st.total > 0 && (running || st.pausedLeft > 0)) left.toFloat() / st.total else 1f },
                    modifier = Modifier.size(240.dp),
                    strokeWidth = 10.dp,
                )
                Text(formatMs(left + 999, centis = false), fontSize = 52.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(24.dp))
            if (st.ringing) {
                Text("⏰ Время вышло!", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Button({ stopRinging(ctx) }, Modifier.height(56.dp)) { Text("Остановить сигнал") }
            } else if (!running && st.pausedLeft == 0L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stepper("мин", minutes, 0..180) { minutes = it }
                    Spacer(Modifier.width(24.dp))
                    Stepper("сек", seconds, 0..59, step = 5) { seconds = it }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 10, 15, 30, 60).forEach { m ->
                        AssistChip(onClick = { minutes = m; seconds = 0 }, label = { Text("$m мин") })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val ms = (minutes * 60L + seconds) * 1000
                        if (ms > 0) { st.total = ms; st.endAt = System.currentTimeMillis() + ms }
                    },
                    modifier = Modifier.size(width = 180.dp, height = 56.dp),
                ) { Icon(Icons.Default.PlayArrow, null); Text("Старт") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton({ st.endAt = 0; st.pausedLeft = 0 }, Modifier.height(56.dp)) { Text("Сброс") }
                    Button(
                        onClick = {
                            if (running) { st.pausedLeft = st.endAt - System.currentTimeMillis(); st.endAt = 0 }
                            else { st.endAt = System.currentTimeMillis() + st.pausedLeft; st.pausedLeft = 0 }
                        },
                        modifier = Modifier.height(56.dp),
                    ) { Text(if (running) "Пауза" else "Продолжить") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Таймер работает, пока открыт лаунчер. Для долгих таймеров используйте системные часы.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton({ onChange((value + step).coerceIn(range)) }) { Icon(Icons.Default.KeyboardArrowUp, "Больше") }
        Text("%02d".format(value), fontSize = 40.sp, fontFamily = FontFamily.Monospace)
        Text(label, style = MaterialTheme.typography.labelMedium)
        IconButton({ onChange((value - step).coerceIn(range)) }) { Icon(Icons.Default.KeyboardArrowDown, "Меньше") }
    }
}
