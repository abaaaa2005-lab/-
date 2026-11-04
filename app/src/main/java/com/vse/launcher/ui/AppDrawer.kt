package com.vse.launcher.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vse.launcher.data.ContactResult
import com.vse.launcher.data.DrawerSort
import com.vse.launcher.data.LauncherViewModel
import com.vse.launcher.data.SEARCH_ENGINES
import com.vse.launcher.util.Calc
import com.vse.launcher.util.SYSTEM_SHORTCUTS
import com.vse.launcher.util.SystemActions
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawer(vm: LauncherViewModel, startSearch: Boolean, onClose: () -> Unit, open: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val s = vm.settings
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(startSearch) {
        if (startSearch) {
            delay(150)
            runCatching { focus.requestFocus() }
        }
    }

    // Свайп вниз от верха списка закрывает меню
    val closeConnection = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    acc += available.y
                    if (acc > 220f) { acc = 0f; onClose() }
                } else if (consumed.y != 0f) acc = 0f
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                acc = 0f
                return Velocity.Zero
            }
        }
    }

    val q = query.trim()
    val apps = remember(q, vm.allApps, vm.hidden, vm.renamed, vm.launchCounts, s.drawerSort) {
        if (q.isEmpty()) vm.visibleApps() else vm.searchApps(q)
    }
    val top = remember(vm.allApps, vm.launchCounts, vm.hidden) { vm.topApps(s.columns) }

    var hasContacts by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasContacts = it }
    var contacts by remember { mutableStateOf<List<ContactResult>>(emptyList()) }
    LaunchedEffect(q, hasContacts) {
        contacts = if (hasContacts && q.length >= 2) { delay(200); vm.searchContacts(q) } else emptyList()
    }

    val engine = SEARCH_ENGINES.getOrElse(s.searchEngine) { SEARCH_ENGINES[0] }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            // Ручка и поиск
            Box(
                Modifier.fillMaxWidth().padding(top = 6.dp).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.padding(6.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).focusRequester(focus),
                placeholder = { Text("Поиск: приложения, контакты, настройки, 2+2…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Очистить") }
                    else IconButton({ open(Screen.Settings) }) { Icon(Icons.Default.Settings, "Настройки") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    val first = apps.firstOrNull()
                    if (first != null) { vm.launch(first) }
                    else if (q.isNotEmpty()) SystemActions.webSearch(ctx, engine.second, q)
                }),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(s.columns),
                modifier = Modifier.fillMaxSize().nestedScroll(closeConnection),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                if (q.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DrawerSort.entries.forEach { sort ->
                                FilterChip(
                                    selected = s.drawerSort == sort,
                                    onClick = { vm.updateSettings { it.copy(drawerSort = sort) } },
                                    label = { Text(sort.title, maxLines = 1) },
                                )
                            }
                        }
                    }
                    if (top.isNotEmpty() && s.drawerSort == DrawerSort.ALPHA) {
                        header("Часто используемые")
                        items(top, key = { "top_" + it.key }) { app ->
                            AppIcon(app, vm, s.iconSizeDp.dp, s.showLabels, onWallpaper = false)
                        }
                        header("Все приложения · ${apps.size}")
                    }
                } else {
                    // Калькулятор прямо в поиске
                    if (Calc.looksLikeMath(q)) {
                        Calc.eval(q)?.let { v ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                val res = Calc.format(v)
                                ResultRow(Icons.Default.Calculate, "= $res", "Нажмите, чтобы скопировать") {
                                    copy(ctx, res)
                                }
                            }
                        }
                    }
                    if (apps.isNotEmpty()) header("Приложения")
                }

                items(apps, key = { it.key }) { app ->
                    AppIcon(app, vm, s.iconSizeDp.dp, s.showLabels, onWallpaper = false)
                }

                if (q.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.padding(top = 12.dp)) {
                            ResultRow(Icons.Default.Lock, "Секретная папка", "Приложения под PIN-кодом · ${vm.hidden.size}") {
                                open(Screen.HiddenApps)
                            }
                        }
                    }
                }

                if (q.isNotEmpty()) {
                    val ql = q.lowercase()
                    val tools = ToolId.entries.filter { it.title.lowercase().contains(ql) || it.keywords.contains(ql) }
                    if (tools.isNotEmpty()) {
                        header("Инструменты")
                        tools.forEach { t ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ResultRow(t.icon, t.title, "Встроенный инструмент") {
                                    if (t == ToolId.FLASHLIGHT) vm.toggleTorch() else open(Screen.Tool(t))
                                }
                            }
                        }
                    }
                    val shortcuts = SYSTEM_SHORTCUTS.filter { it.title.lowercase().contains(ql) || it.keywords.contains(ql) }
                    if (shortcuts.isNotEmpty()) {
                        header("Настройки телефона")
                        shortcuts.forEach { sc ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ResultRow(Icons.Default.Settings, sc.title, "Системные настройки") {
                                    SystemActions.openSettings(ctx, sc.action)
                                }
                            }
                        }
                    }
                    header("Контакты")
                    if (!hasContacts) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ResultRow(Icons.Default.Contacts, "Искать в контактах", "Нужно разрешение на доступ к контактам") {
                                permLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        }
                    } else if (contacts.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Ничего не найдено", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    } else {
                        contacts.forEach { c ->
                            item(span = { GridItemSpan(maxLineSpan) }) { ContactRow(c) }
                        }
                    }
                    if (q.any { it.isDigit() } && q.all { it.isDigit() || it in "+-() " }) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ResultRow(Icons.Default.Call, "Позвонить: $q", "Открыть номеронабиратель") { SystemActions.dial(ctx, q) }
                        }
                    }
                    header("Интернет")
                    if (looksLikeUrl(q)) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ResultRow(Icons.Default.Link, "Открыть сайт", q) {
                                SystemActions.openUrl(ctx, if (q.startsWith("http")) q else "https://$q")
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ResultRow(Icons.Default.TravelExplore, "Искать «$q»", "в ${engine.first}") { SystemActions.webSearch(ctx, engine.second, q) }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ResultRow(Icons.Default.Map, "Найти на карте", q) { SystemActions.maps(ctx, q) }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ResultRow(Icons.Default.SmartDisplay, "Искать на YouTube", q) {
                            SystemActions.openUrl(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(q))
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ResultRow(Icons.Default.Shop, "Искать в магазине приложений", q) { SystemActions.playStore(ctx, q) }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ResultRow(Icons.Default.Translate, "Перевести", q) {
                            SystemActions.openUrl(ctx, "https://translate.google.com/?sl=auto&tl=ru&text=" + Uri.encode(q))
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ResultRow(Icons.Default.NoteAdd, "Сохранить как заметку", q) {
                            vm.saveNote(com.vse.launcher.data.Note(System.currentTimeMillis(), q.take(40), q, 0))
                            SystemActions.toast(ctx, "Заметка сохранена")
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ResultRow(Icons.Default.AddTask, "Добавить в задачи", q) {
                            vm.addTodo(q)
                            SystemActions.toast(ctx, "Задача добавлена")
                        }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.header(text: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
        )
    }
}

private fun looksLikeUrl(q: String) = !q.contains(' ') && Regex("^(https?://)?[\\w.-]+\\.[a-zа-я]{2,}(/.*)?$", RegexOption.IGNORE_CASE).matches(q)

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("result", text))
    SystemActions.toast(ctx, "Скопировано: $text")
}

@Composable
fun ResultRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ContactRow(c: ContactResult) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                val uri = ContactsContract.Contacts.getLookupUri(c.id, c.lookupKey)
                SystemActions.start(ctx, Intent(Intent.ACTION_VIEW, uri))
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(c.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            c.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        c.phone?.let { phone ->
            IconButton({ SystemActions.start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))) }) {
                Icon(Icons.Default.Call, "Позвонить")
            }
            IconButton({ SystemActions.start(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)))) }) {
                Icon(Icons.Default.Sms, "SMS")
            }
        }
    }
}
