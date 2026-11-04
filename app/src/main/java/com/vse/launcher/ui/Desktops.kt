package com.vse.launcher.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vse.launcher.data.*
import com.vse.launcher.util.SystemActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun presetBrush(index: Int): Brush {
    val colors = WALLPAPER_PRESETS.getOrElse(index) { WALLPAPER_PRESETS[0] }.second.map { Color(it) }
    return Brush.linearGradient(colors)
}

/** Обои рабочего стола: системные (ничего не рисуем), градиент или картинка из галереи. */
@Composable
fun DesktopWallpaper(wallpaper: String, modifier: Modifier = Modifier, sample: Int = 1) {
    when {
        wallpaper.startsWith("grad:") -> Box(modifier.background(presetBrush(wallpaper.removePrefix("grad:").toIntOrNull() ?: 0)))
        wallpaper.startsWith("file:") -> {
            val bmp by produceState<ImageBitmap?>(null, wallpaper, sample) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        BitmapFactory.decodeFile(wallpaper.removePrefix("file:"), BitmapFactory.Options().apply { inSampleSize = sample })
                            ?.asImageBitmap()
                    }.getOrNull()
                }
            }
            bmp?.let { Image(it, null, modifier, contentScale = ContentScale.Crop) }
                ?: Box(modifier.background(Color(0xFF1E1E24)))
        }
        else -> Unit
    }
}

/** Плашка с названием стола сверху — нажатие открывает список столов. */
@Composable
fun DesktopPill(name: String, secret: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.25f)).clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (secret) Icons.Default.Lock else Icons.Default.DesktopWindows, null, tint = if (secret) Color(0xFFFFD166) else Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(name, color = Color.White, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Default.ArrowDropDown, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** «Представление задач» как в Windows: все рабочие столы, переключение, создание, обои. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskView(vm: LauncherViewModel, apps: Map<String, AppInfo>, onClose: () -> Unit) {
    var renameFor by remember { mutableStateOf<Desktop?>(null) }
    var wallpaperFor by remember { mutableStateOf<Desktop?>(null) }
    var deleteFor by remember { mutableStateOf<Desktop?>(null) }
    var pinGate by remember { mutableStateOf(false) }
    val visible = vm.desktops.filter { !it.secret || vm.secretUnlocked }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0101018))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Рабочие столы", style = MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.weight(1f))
                if (vm.secretUnlocked) {
                    TextButton({ vm.lockSecret() }) {
                        Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Скрыть секретные", color = Color.White)
                    }
                }
                IconButton(onClose) { Icon(Icons.Default.Close, "Закрыть", tint = Color.White) }
            }
            Text(
                "У каждого стола свои экраны и обои. Док внизу общий — как панель задач.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(visible, key = { it.id }) { d ->
                    DesktopCard(
                        vm, d, apps,
                        active = d.id == vm.activeDesktop,
                        onOpen = { onClose(); vm.switchDesktop(d.id) },
                        onRename = { renameFor = d },
                        onWallpaper = { wallpaperFor = d },
                        onDelete = { deleteFor = d },
                    )
                }
                item { AddCard(Icons.Default.Add, "Новый рабочий стол") { onClose(); vm.addDesktop() } }
                if (vm.secretUnlocked) {
                    item { AddCard(Icons.Default.Lock, "Новый секретный стол") { onClose(); vm.addDesktop(secret = true) } }
                } else {
                    item {
                        AddCard(Icons.Default.Lock, "Секретные столы", "Откроются по PIN-коду") { pinGate = true }
                    }
                }
            }
        }
    }

    if (pinGate) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { pinGate = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            PinScreen(
                vm, create = vm.secretPin == null,
                onSuccess = { vm.unlockSecret(); pinGate = false },
                onBack = { pinGate = false },
                header = "Секретные столы",
            )
        }
    }

    renameFor?.let { d ->
        var text by remember(d.id) { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("Название стола") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.renameDesktop(d.id, text); renameFor = null }) { Text("Готово") } },
            dismissButton = { TextButton({ renameFor = null }) { Text("Отмена") } },
        )
    }
    deleteFor?.let { d ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Удалить «${d.name}»?") },
            text = { Text("Иконки и папки этого стола исчезнут. Сами приложения останутся на телефоне.") },
            confirmButton = { TextButton({ vm.deleteDesktop(d.id); deleteFor = null }) { Text("Удалить") } },
            dismissButton = { TextButton({ deleteFor = null }) { Text("Отмена") } },
        )
    }
    wallpaperFor?.let { d -> WallpaperSheet(vm, d) { wallpaperFor = null } }
}

@Composable
private fun DesktopCard(
    vm: LauncherViewModel,
    d: Desktop,
    apps: Map<String, AppInfo>,
    active: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onWallpaper: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val items = remember(d.id, vm.pages, vm.desktops) { vm.desktopPages(d.id).firstOrNull() ?: emptyList() }
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.56f)
                .clip(RoundedCornerShape(18.dp))
                .then(if (active) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier)
                .background(Color(0xFF2A2A33))
                .clickable(onClick = onOpen),
        ) {
            DesktopWallpaper(d.wallpaper, Modifier.fillMaxSize(), sample = 4)
            if (d.wallpaper == "system") {
                Text("Системные обои", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
            }
            // Мини-копия первого экрана
            Column(Modifier.padding(10.dp).padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.take(16).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { id ->
                            val a = apps[id]
                            if (a?.icon != null) Image(a.icon, null, Modifier.size(22.dp))
                            else Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.3f)))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (d.secret) Icon(Icons.Default.Lock, "Секретный", tint = Color(0xFFFFD166), modifier = Modifier.size(16.dp).padding(end = 2.dp))
            Text(
                d.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f).clickable(onClick = onOpen),
            )
            Box {
                IconButton({ menu = true }, Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "Ещё", tint = Color.White) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Открыть") }, { menu = false; onOpen() }, leadingIcon = { Icon(Icons.Default.OpenInNew, null) })
                    DropdownMenuItem({ Text("Обои") }, { menu = false; onWallpaper() }, leadingIcon = { Icon(Icons.Default.Wallpaper, null) })
                    DropdownMenuItem({ Text("Переименовать") }, { menu = false; onRename() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        if (d.secret) {
                        DropdownMenuItem({ Text("Сделать обычным") }, { menu = false; vm.setDesktopSecret(d.id, false) }, leadingIcon = { Icon(Icons.Default.LockOpen, null) })
                    } else if (vm.desktops.count { !it.secret } > 1) {
                        DropdownMenuItem({ Text("Сделать секретным") }, { menu = false; vm.setDesktopSecret(d.id, true) }, leadingIcon = { Icon(Icons.Default.Lock, null) })
                    }
                    if (vm.desktops.size > 1 && (d.secret || vm.desktops.count { !it.secret } > 1)) {
                        DropdownMenuItem({ Text("Удалить") }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.56f)
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
            Text(title, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Выбор обоев для конкретного стола. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WallpaperSheet(vm: LauncherViewModel, d: Desktop, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = vm.importWallpaper(d.id, uri)
            if (!ok) SystemActions.toast(ctx, "Не удалось загрузить картинку")
            onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Обои: ${d.name}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Button(
                { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Выбрать из галереи") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ vm.setDesktopWallpaper(d.id, "system"); onDismiss() }, Modifier.fillMaxWidth()) {
                Text("Системные обои телефона")
            }
            Spacer(Modifier.height(16.dp))
            Text("Готовые", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WALLPAPER_PRESETS.forEachIndexed { i, (name, _) ->
                    val selected = d.wallpaper == "grad:$i"
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(width = 56.dp, height = 96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(presetBrush(i))
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable { vm.setDesktopWallpaper(d.id, "grad:$i"); onDismiss() },
                        )
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun dot(color: Color) = Box(Modifier.size(8.dp).clip(CircleShape).background(color))
