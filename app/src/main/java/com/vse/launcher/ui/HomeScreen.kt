package com.vse.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vse.launcher.data.LauncherViewModel
import com.vse.launcher.util.SystemActions
import com.vse.launcher.util.Weather
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BatteryState(val level: Int, val charging: Boolean)

@Composable
fun rememberBattery(): BatteryState {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(BatteryState(-1, false)) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                state = BatteryState(
                    if (level >= 0) level * 100 / scale else -1,
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                )
            }
        }
        ctx.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { receiver.onReceive(ctx, it) }
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    return state
}

@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    return now
}

fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Доброе утро"
    in 12..16 -> "Добрый день"
    in 17..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: LauncherViewModel, open: (Screen) -> Unit) {
    val s = vm.settings
    val ctx = LocalContext.current
    var homeMenu by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = s.wallpaperDim))
            .pointerInput(s.swipeDownNotifications) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -100) open(Screen.Drawer())
                        else if (total > 100 && s.swipeDownNotifications) SystemActions.expandNotifications(ctx)
                    },
                    onVerticalDrag = { change, d -> total += d; change.consume() },
                )
            }
            .pointerInput(s.doubleTapLock) {
                detectTapGestures(
                    onDoubleTap = { if (s.doubleTapLock) SystemActions.lockScreen(ctx) },
                    onLongPress = { homeMenu = true },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            ClockBlock(vm)
            if (s.showWeather) {
                Spacer(Modifier.height(12.dp))
                WeatherChip(vm) { open(Screen.Tool(ToolId.WEATHER)) }
            }
            Spacer(Modifier.height(18.dp))
            SearchPill { open(Screen.Drawer(search = true)) }
            if (s.showTools) {
                Spacer(Modifier.height(14.dp))
                QuickTools(vm, open)
            }
            TodoWidget(vm) { open(Screen.Tool(ToolId.TODO)) }
            Spacer(Modifier.weight(1f))
            FavoritesGrid(vm)
            if (s.showDock) {
                Spacer(Modifier.height(10.dp))
                Dock(vm, open)
            }
            Spacer(Modifier.height(4.dp))
            Icon(
                Icons.Default.KeyboardArrowUp, "Свайп вверх — все приложения",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally).size(20.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    if (homeMenu) {
        ModalBottomSheet(onDismissRequest = { homeMenu = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Рабочий стол", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
                SheetItem(Icons.Default.Wallpaper, "Сменить обои") { homeMenu = false; SystemActions.pickWallpaper(ctx) }
                SheetItem(Icons.Default.Settings, "Настройки лаунчера") { homeMenu = false; open(Screen.Settings) }
                SheetItem(Icons.Default.Widgets, "Инструменты") { homeMenu = false; open(Screen.Tools) }
                SheetItem(Icons.Default.Apps, "Все приложения") { homeMenu = false; open(Screen.Drawer()) }
                SheetItem(Icons.Default.Tune, "Быстрые настройки телефона") { homeMenu = false; SystemActions.expandQuickSettings(ctx) }
                SheetItem(Icons.Default.Home, "Лаунчер по умолчанию") { homeMenu = false; SystemActions.setDefaultLauncher(ctx) }
            }
        }
    }
}

@Composable
private fun SheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ClockBlock(vm: LauncherViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current
    val now = rememberNow()
    val battery = rememberBattery()
    val ru = Locale("ru")
    val pattern = buildString {
        append(if (s.use24h) "HH:mm" else "h:mm")
        if (s.showSeconds) append(":ss")
    }
    val time = SimpleDateFormat(pattern, ru).format(Date(now))
    val date = SimpleDateFormat("EEEE, d MMMM", ru).format(Date(now)).replaceFirstChar { it.uppercase() }
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                time,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, fontWeight = FontWeight.Light).onWallpaper(),
                modifier = Modifier.clickable { SystemActions.alarms(ctx) },
            )
            if (!s.use24h) {
                Text(
                    if (hour < 12) " AM" else " PM",
                    style = MaterialTheme.typography.titleMedium.onWallpaper(),
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                date,
                style = MaterialTheme.typography.titleMedium.onWallpaper(),
                modifier = Modifier.clickable { SystemActions.calendar(ctx) },
            )
            if (s.showBattery && battery.level >= 0) {
                Text("  •  ", style = MaterialTheme.typography.titleMedium.onWallpaper())
                Icon(
                    if (battery.charging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                    null, tint = Color.White, modifier = Modifier.size(18.dp),
                )
                Text("${battery.level}%", style = MaterialTheme.typography.titleMedium.onWallpaper())
            }
        }
        if (s.showGreeting) {
            val name = s.userName.trim()
            Text(
                greeting(hour) + if (name.isNotEmpty()) ", $name" else "",
                style = MaterialTheme.typography.bodyLarge.onWallpaper().copy(color = Color.White.copy(alpha = 0.85f)),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun WeatherChip(vm: LauncherViewModel, onClick: () -> Unit) {
    val w = vm.weather
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = when {
            w != null -> "${Weather.emoji(w.code)}  ${w.temp.toInt()}°  ${Weather.describe(w.code)} · ${vm.settings.weatherCity}"
            vm.weatherLoading -> "Загружаю погоду…"
            else -> "🌡️  Погода недоступна · нажмите"
        }
        Text(text, style = MaterialTheme.typography.bodyMedium.onWallpaper(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = Color(0xFF444450))
        Spacer(Modifier.width(12.dp))
        Text("Приложения, контакты, интернет, 2+2…", color = Color(0xFF666670), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickTools(vm: LauncherViewModel, open: (Screen) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            RoundTile(
                if (vm.torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                "Фонарик", active = vm.torchOn, onWallpaper = true,
            ) { vm.toggleTorch() }
        }
        items(listOf(ToolId.CALCULATOR, ToolId.NOTES, ToolId.TODO, ToolId.TIMER, ToolId.STOPWATCH, ToolId.COMPASS, ToolId.CONVERTER)) { t ->
            RoundTile(t.icon, t.title, onWallpaper = true) { open(Screen.Tool(t)) }
        }
        item { RoundTile(Icons.Default.Widgets, "Все", onWallpaper = true) { open(Screen.Tools) } }
        item { RoundTile(Icons.Default.Settings, "Лаунчер", onWallpaper = true) { open(Screen.Settings) } }
    }
}

@Composable
private fun TodoWidget(vm: LauncherViewModel, onClick: () -> Unit) {
    val open = vm.todos.filter { !it.done }
    if (open.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("Задачи · ${open.size}", style = MaterialTheme.typography.labelLarge.onWallpaper())
        open.take(3).forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).clickable { vm.toggleTodo(t.id) }
                        .background(Color.White.copy(alpha = 0.25f)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    t.text,
                    style = MaterialTheme.typography.bodyMedium.onWallpaper().copy(textDecoration = if (t.done) TextDecoration.LineThrough else null),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FavoritesGrid(vm: LauncherViewModel) {
    val s = vm.settings
    val favs = vm.favoriteApps()
    if (favs.isEmpty()) {
        if (!vm.loading) {
            Text(
                "Здесь будут ваши любимые приложения.\nСвайп вверх → удерживайте иконку → «На главный экран».",
                style = MaterialTheme.typography.bodyMedium.onWallpaper().copy(color = Color.White.copy(alpha = 0.85f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
        return
    }
    Column {
        favs.chunked(s.columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    AppIcon(app, vm, s.iconSizeDp.dp, s.showLabels, onWallpaper = true, modifier = Modifier.weight(1f))
                }
                repeat(s.columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Dock(vm: LauncherViewModel, open: (Screen) -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        DockButton(Icons.Default.Phone, "Телефон") { SystemActions.dial(ctx) }
        DockButton(Icons.AutoMirrored.Filled.Message, "Сообщения") { SystemActions.messages(ctx) }
        DockButton(Icons.Default.Apps, "Приложения", highlight = true) { open(Screen.Drawer()) }
        DockButton(Icons.Default.Public, "Браузер") { SystemActions.browser(ctx) }
        DockButton(Icons.Default.PhotoCamera, "Камера") { SystemActions.camera(ctx) }
    }
}

@Composable
private fun DockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (highlight) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (highlight) MaterialTheme.colorScheme.onPrimary else Color.White)
    }
}
