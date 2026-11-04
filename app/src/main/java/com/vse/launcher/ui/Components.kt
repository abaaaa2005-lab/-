package com.vse.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vse.launcher.data.AppInfo
import com.vse.launcher.data.LauncherViewModel

val WallpaperShadow = Shadow(color = Color.Black.copy(alpha = 0.55f), blurRadius = 8f)

fun TextStyle.onWallpaper() = copy(color = Color.White, shadow = WallpaperShadow)

/** Иконка приложения с подписью и меню по долгому нажатию. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppInfo,
    vm: LauncherViewModel,
    iconSize: Dp,
    showLabel: Boolean,
    onWallpaper: Boolean,
    modifier: Modifier = Modifier,
    folderId: String? = null,
    onLaunched: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { vm.launch(app); onLaunched() },
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                )
                .padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (app.icon != null) {
                Image(app.icon, contentDescription = app.label, modifier = Modifier.size(iconSize))
            } else {
                Box(
                    Modifier.size(iconSize).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text(app.label.take(1).uppercase(), fontWeight = FontWeight.Bold) }
            }
            if (showLabel) {
                Spacer(Modifier.height(4.dp))
                val style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)
                Text(
                    app.label,
                    style = if (onWallpaper) style.onWallpaper() else style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        AppMenu(app, vm, expanded = menu, onDismiss = { menu = false }, onRename = { renaming = true }, folderId = folderId)
    }
    if (renaming) RenameDialog(app, vm) { renaming = false }
}

@Composable
fun AppMenu(
    app: AppInfo,
    vm: LauncherViewModel,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    folderId: String? = null,
    onEdit: (() -> Unit)? = null,
) {
    val onHome = vm.isOnHome(app.key)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(16.dp)) {
        Text(
            app.label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        if (folderId != null) {
            MenuItem("Вынуть из папки", Icons.Default.DriveFileMove) { vm.removeFromFolder(folderId, app.key); onDismiss() }
        } else if (onHome) {
            MenuItem("Убрать с экрана «Домой»", Icons.Default.RemoveCircleOutline) { vm.removeFromHome(app.key); onDismiss() }
        } else {
            MenuItem("Добавить на экран «Домой»", Icons.Default.AddToHomeScreen) { vm.addToHome(app.key); onDismiss() }
        }
        if (onEdit != null) MenuItem("Изменить экран «Домой»", Icons.Default.Edit) { onDismiss(); onEdit() }
        MenuItem("Переименовать", Icons.Default.DriveFileRenameOutline) { onDismiss(); onRename() }
        MenuItem("Скрыть", Icons.Default.VisibilityOff) { vm.setHidden(app, true); onDismiss() }
        MenuItem("О приложении", Icons.Default.Info) { vm.openAppInfo(app); onDismiss() }
        if (!app.isSystem) MenuItem("Удалить", Icons.Default.Delete) { vm.uninstall(app); onDismiss() }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
fun RenameDialog(app: AppInfo, vm: LauncherViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(app.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать") },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Название (${app.originalLabel})") })
        },
        confirmButton = { TextButton({ vm.rename(app, text); onDismiss() }) { Text("Сохранить") } },
        dismissButton = {
            TextButton({ vm.rename(app, ""); onDismiss() }) { Text("Сбросить") }
        },
    )
}

/** Общий каркас полноэкранных страниц (настройки, инструменты). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0),
            )
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun RoundTile(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onWallpaper: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        active -> MaterialTheme.colorScheme.primary
        onWallpaper -> Color.White.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        active -> MaterialTheme.colorScheme.onPrimary
        onWallpaper -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier.width(68.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = fg, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        val style = MaterialTheme.typography.labelSmall
        Text(label, style = if (onWallpaper) style.onWallpaper() else style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
