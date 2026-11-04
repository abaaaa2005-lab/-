package com.vse.launcher.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vse.launcher.data.LauncherViewModel
import com.vse.launcher.data.Note
import com.vse.launcher.ui.ScreenScaffold
import com.vse.launcher.util.SystemActions
import com.vse.launcher.util.Weather
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- Заметки ----------------

@Composable
fun NotesTool(vm: LauncherViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Note?>(null) }
    var filter by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }

    editing?.let { note ->
        NoteEditor(note, vm, onClose = { editing = null })
        return
    }

    ScreenScaffold("Заметки", onBack) {
        Box(Modifier.fillMaxSize()) {
            Column {
                OutlinedTextField(
                    filter, { filter = it },
                    placeholder = { Text("Поиск в заметках") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                val list = vm.notes.filter {
                    filter.isBlank() || it.title.contains(filter, true) || it.text.contains(filter, true)
                }
                if (list.isEmpty()) {
                    Text(
                        "Пока нет заметок. Нажмите «+», чтобы создать.",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { n ->
                        Card(Modifier.fillMaxWidth().clickable { editing = n }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(n.title.ifBlank { "Без названия" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (n.text.isNotBlank()) {
                                    Text(n.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(fmt.format(Date(n.updated)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { editing = Note(System.currentTimeMillis(), "", "", System.currentTimeMillis()) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            ) { Icon(Icons.Default.Add, "Новая заметка") }
        }
    }
}

@Composable
private fun NoteEditor(note: Note, vm: LauncherViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(note.title) }
    var text by remember { mutableStateOf(note.text) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun saveAndClose() {
        if (title.isNotBlank() || text.isNotBlank()) {
            if (title != note.title || text != note.text) vm.saveNote(note.copy(title = title, text = text))
        }
        onClose()
    }
    BackHandler { saveAndClose() }

    ScreenScaffold(
        if (vm.notes.any { it.id == note.id }) "Заметка" else "Новая заметка",
        onBack = { saveAndClose() },
        actions = {
            IconButton({ SystemActions.share(ctx, listOf(title, text).filter { it.isNotBlank() }.joinToString("\n\n")) }) {
                Icon(Icons.Default.Share, "Поделиться")
            }
            IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить") }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            TextField(
                title, { title = it },
                placeholder = { Text("Заголовок") },
                textStyle = MaterialTheme.typography.titleLarge,
                singleLine = true,
                colors = TextFieldDefaults.colors(unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                text, { text = it },
                placeholder = { Text("Текст заметки…") },
                colors = TextFieldDefaults.colors(unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить заметку?") },
            confirmButton = { TextButton({ vm.deleteNote(note.id); confirmDelete = false; onClose() }) { Text("Удалить") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

// ---------------- Задачи ----------------

@Composable
fun TodoTool(vm: LauncherViewModel, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val done = vm.todos.count { it.done }
    ScreenScaffold("Задачи", onBack, actions = {
        if (done > 0) TextButton({ vm.clearDoneTodos() }) { Text("Убрать готовые") }
    }) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it },
                    placeholder = { Text("Новая задача") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.addTodo(text); text = "" }),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton({ vm.addTodo(text); text = "" }) { Icon(Icons.Default.Add, "Добавить") }
            }
            if (vm.todos.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { done.toFloat() / vm.todos.size },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                Text("Выполнено $done из ${vm.todos.size}", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
            } else {
                Text("Список пуст — добавьте первую задачу.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(vm.todos, key = { it.id }) { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.toggleTodo(t.id) }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(t.done, { vm.toggleTodo(t.id) })
                        Text(
                            t.text,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (t.done) TextDecoration.LineThrough else null,
                            color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton({ vm.deleteTodo(t.id) }) { Icon(Icons.Default.Close, "Удалить") }
                    }
                }
            }
        }
    }
}

// ---------------- Погода ----------------

@Composable
fun WeatherTool(vm: LauncherViewModel, onBack: () -> Unit) {
    val w = vm.weather
    var cityDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshWeatherIfStale() }
    ScreenScaffold("Погода · ${vm.settings.weatherCity}", onBack, actions = {
        IconButton({ cityDialog = true }) { Icon(Icons.Default.LocationCity, "Город") }
        IconButton({ vm.refreshWeather() }) { Icon(Icons.Default.Refresh, "Обновить") }
    }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (vm.weatherLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (w == null) {
                Text(
                    if (vm.weatherLoading) "Загружаю…" else (vm.weatherError ?: "Нет данных. Проверьте интернет."),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                Button({ cityDialog = true }) { Text("Выбрать город") }
                if (cityDialog) com.vse.launcher.ui.CityDialog(vm) { cityDialog = false }
                return@Column
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Weather.emoji(w.code), fontSize = 56.sp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("${w.temp.toInt()}°", fontSize = 56.sp, fontWeight = FontWeight.Light)
                            Text(Weather.describe(w.code), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Ощущается как ${w.feelsLike.toInt()}°")
                    Text("Ветер ${"%.1f".format(w.wind)} м/с · Влажность ${w.humidity}%")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Прогноз", style = MaterialTheme.typography.titleMedium)
            val inFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
            val outFmt = remember { SimpleDateFormat("EEEE, d MMM", Locale("ru")) }
            w.days.forEachIndexed { i, d ->
                val label = when (i) {
                    0 -> "Сегодня"
                    1 -> "Завтра"
                    else -> runCatching { outFmt.format(inFmt.parse(d.date)!!).replaceFirstChar { it.uppercase() } }.getOrDefault(d.date)
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, modifier = Modifier.weight(1f))
                    Text(Weather.emoji(d.code), fontSize = 22.sp)
                    Spacer(Modifier.width(16.dp))
                    Text("${d.min.toInt()}° / ${d.max.toInt()}°", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(90.dp))
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(12.dp))
            if (cityDialog) com.vse.launcher.ui.CityDialog(vm) { cityDialog = false }
            Text("Данные: Open-Meteo.com / wttr.in", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}
