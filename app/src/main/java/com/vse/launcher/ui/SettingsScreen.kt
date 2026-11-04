package com.vse.launcher.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vse.launcher.data.*
import com.vse.launcher.util.City
import com.vse.launcher.util.SystemActions
import com.vse.launcher.util.Weather
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: LauncherViewModel, onBack: () -> Unit, open: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val s = vm.settings
    var cityDialog by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var engineDialog by remember { mutableStateOf(false) }

    ScreenScaffold("Настройки лаунчера", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionTitle("Основное")
            ClickRow(Icons.Default.Home, "Сделать лаунчером по умолчанию", "Выберите «Всё Лаунчер» в списке") { SystemActions.setDefaultLauncher(ctx) }
            ClickRow(Icons.Default.Wallpaper, "Обои", "Выбрать картинку рабочего стола") { SystemActions.pickWallpaper(ctx) }
            ClickRow(Icons.Default.Person, "Ваше имя", s.userName.ifBlank { "Для приветствия" }) { nameDialog = true }

            SectionTitle("Внешний вид")
            ClickRow(Icons.Default.DarkMode, "Тема", s.theme.title) { themeDialog = true }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(Icons.Default.AutoAwesome, "Цвета Material You", "Брать цвета из обоев", s.dynamicColor) { v -> vm.updateSettings { it.copy(dynamicColor = v) } }
            }
            if (!s.dynamicColor) {
                Text("Акцентный цвет", modifier = Modifier.padding(start = 20.dp, top = 8.dp), style = MaterialTheme.typography.bodyLarge)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ACCENTS.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(if (c == s.accent) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                                .clickable { vm.updateSettings { it.copy(accent = c) } },
                        )
                    }
                }
            }
            SliderRow("Колонок в сетке: ${s.columns}", s.columns.toFloat(), 3f..7f, 3) { v -> vm.updateSettings { it.copy(columns = v.toInt()) } }
            SliderRow("Размер иконок: ${s.iconSizeDp} dp", s.iconSizeDp.toFloat(), 36f..72f, 8) { v -> vm.updateSettings { it.copy(iconSizeDp = v.toInt()) } }
            SliderRow("Затемнение обоев: ${(s.wallpaperDim * 100).toInt()}%", s.wallpaperDim, 0f..0.8f, 0) { v -> vm.updateSettings { it.copy(wallpaperDim = v) } }
            SwitchRow(Icons.Default.Title, "Подписи под иконками", null, s.showLabels) { v -> vm.updateSettings { it.copy(showLabels = v) } }

            SectionTitle("Рабочий стол")
            SwitchRow(Icons.Default.Schedule, "24-часовой формат", null, s.use24h) { v -> vm.updateSettings { it.copy(use24h = v) } }
            SwitchRow(Icons.Default.Timer, "Показывать секунды", null, s.showSeconds) { v -> vm.updateSettings { it.copy(showSeconds = v) } }
            SwitchRow(Icons.Default.WavingHand, "Приветствие", null, s.showGreeting) { v -> vm.updateSettings { it.copy(showGreeting = v) } }
            SwitchRow(Icons.Default.BatteryStd, "Заряд батареи", null, s.showBattery) { v -> vm.updateSettings { it.copy(showBattery = v) } }
            SwitchRow(Icons.Default.WbSunny, "Погода", null, s.showWeather) { v -> vm.updateSettings { it.copy(showWeather = v) }; vm.refreshWeather() }
            ClickRow(Icons.Default.LocationCity, "Город для погоды", s.weatherCity) { cityDialog = true }
            SwitchRow(Icons.Default.Widgets, "Панель инструментов", "Фонарик, калькулятор, заметки…", s.showTools) { v -> vm.updateSettings { it.copy(showTools = v) } }
            SwitchRow(Icons.Default.Dock, "Док внизу", "Телефон, сообщения, браузер, камера", s.showDock) { v -> vm.updateSettings { it.copy(showDock = v) } }

            SectionTitle("Жесты")
            SwitchRow(Icons.Default.SwipeDown, "Свайп вниз — уведомления", null, s.swipeDownNotifications) { v -> vm.updateSettings { it.copy(swipeDownNotifications = v) } }
            SwitchRow(Icons.Default.Lock, "Двойной тап — заблокировать", "Нужна служба спец. возможностей", s.doubleTapLock) { v -> vm.updateSettings { it.copy(doubleTapLock = v) } }
            InfoRow("Свайп вверх — все приложения. Долгое нажатие на пустом месте — меню рабочего стола.")

            SectionTitle("Поиск")
            ClickRow(Icons.Default.TravelExplore, "Поисковик", SEARCH_ENGINES.getOrElse(s.searchEngine) { SEARCH_ENGINES[0] }.first) { engineDialog = true }

            SectionTitle("Приложения")
            ClickRow(Icons.Default.VisibilityOff, "Скрытые приложения", "${vm.hidden.size} скрыто") { open(Screen.HiddenApps) }
            ClickRow(Icons.Default.RestartAlt, "Сбросить статистику запусков", null) {
                vm.resetUsage(); SystemActions.toast(ctx, "Статистика сброшена")
            }
            ClickRow(Icons.Default.Refresh, "Обновить список приложений", "${vm.allApps.size} приложений") { vm.reloadApps() }

            SectionTitle("О лаунчере")
            InfoRow("Всё Лаунчер 1.0 — рабочий стол, поиск, погода, заметки, задачи, калькулятор, фонарик, таймеры, компас, уровень, конвертер и многое другое. Без рекламы и сбора данных.")
        }
    }

    if (themeDialog) {
        ChoiceDialog("Тема", ThemeMode.entries.map { it.title }, s.theme.ordinal, { themeDialog = false }) { i ->
            vm.updateSettings { it.copy(theme = ThemeMode.entries[i]) }
        }
    }
    if (engineDialog) {
        ChoiceDialog("Поисковик", SEARCH_ENGINES.map { it.first }, s.searchEngine, { engineDialog = false }) { i ->
            vm.updateSettings { it.copy(searchEngine = i) }
        }
    }
    if (nameDialog) {
        var text by remember { mutableStateOf(s.userName) }
        AlertDialog(
            onDismissRequest = { nameDialog = false },
            title = { Text("Как вас зовут?") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.updateSettings { it.copy(userName = text.trim()) }; nameDialog = false }) { Text("Готово") } },
        )
    }
    if (cityDialog) CityDialog(vm) { cityDialog = false }
}

@Composable
private fun CityDialog(vm: LauncherViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Город") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text("Например, Казань") })
                    IconButton({
                        scope.launch {
                            searching = true
                            results = Weather.findCity(text)
                            searching = false
                            searched = true
                        }
                    }) { Icon(Icons.Default.Search, "Найти") }
                }
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                if (searched && results.isEmpty() && !searching) Text("Ничего не найдено (нужен интернет)", modifier = Modifier.padding(top = 8.dp))
                results.forEach { c ->
                    Text(
                        "${c.name}, ${c.country}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                vm.updateSettings { it.copy(weatherCity = c.name, weatherLat = c.lat, weatherLon = c.lon) }
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Закрыть") } },
    )
}

@Composable
fun ChoiceDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSelect(i); onDismiss() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = { onSelect(i); onDismiss() })
                        Text(o)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ClickRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked, onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun InfoRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
fun HiddenAppsScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val list = vm.hiddenApps()
    ScreenScaffold("Скрытые приложения", onBack) {
        if (list.isEmpty()) {
            Text(
                "Нет скрытых приложений. Скрыть приложение: долгое нажатие на иконку → «Скрыть».",
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.verticalScroll(rememberScrollState())) { list.forEach { app ->
            ListItem(
                headlineContent = { Text(app.label) },
                supportingContent = { Text(app.packageName) },
                leadingContent = {
                    app.icon?.let { androidx.compose.foundation.Image(it, null, Modifier.size(40.dp)) }
                },
                trailingContent = {
                    Row {
                        IconButton({ vm.launch(app) }) { Icon(Icons.Default.PlayArrow, "Открыть") }
                        IconButton({ vm.setHidden(app, false) }) { Icon(Icons.Default.Visibility, "Показать") }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        } }
    }
}

@Composable
fun ToolsHub(vm: LauncherViewModel, onBack: () -> Unit, open: (Screen) -> Unit) {
    ScreenScaffold("Инструменты", onBack) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ToolId.entries) { t ->
                val active = t == ToolId.FLASHLIGHT && vm.torchOn
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { open(Screen.Tool(t)) }
                        .padding(vertical = 18.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(t.icon, null, tint = fg, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(t.title, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}
