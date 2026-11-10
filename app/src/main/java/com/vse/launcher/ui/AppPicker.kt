package com.vse.launcher.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vse.launcher.data.FOLDER_PREFIX
import com.vse.launcher.data.LauncherViewModel

/**
 * Выбор приложений галочками — чтобы добавлять их без перетаскивания.
 * Показывается на весь экран, как в iOS.
 */
@Composable
fun AppPickerDialog(
    vm: LauncherViewModel,
    title: String,
    confirmText: String,
    initial: Set<String> = emptySet(),
    markOnHome: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Порядок выбора сохраняем — в таком порядке иконки и встанут
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    val all = remember(vm.allApps, vm.renamed, vm.hidden) { vm.visibleApps().sortedBy { it.label.lowercase() } }
    val list = if (query.isBlank()) all else all.filter { it.label.contains(query.trim(), true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onDismiss) { Text("Отмена") }
                    Text(
                        title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    TextButton({ onConfirm(selected.toList()); onDismiss() }) {
                        Text(if (selected.isEmpty()) confirmText else "$confirmText (${selected.size})", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedTextField(
                    query, { query = it },
                    placeholder = { Text("Поиск приложений") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                if (markOnHome) {
                    Text(
                        "Отмечены галочкой — будут добавлены. Полупрозрачные уже есть на этом рабочем столе.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                LazyVerticalGrid(GridCells.Fixed(4), contentPadding = PaddingValues(8.dp), modifier = Modifier.weight(1f)) {
                    items(list, key = { it.key }) { app ->
                        val checked = app.key in selected
                        val onHome = markOnHome && vm.isOnHome(app.key) && app.key !in initial
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { if (checked) selected.remove(app.key) else selected.add(app.key) }
                                .padding(vertical = 8.dp)
                                .alpha(if (onHome && !checked) 0.45f else 1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                if (app.icon != null) Image(app.icon, null, Modifier.size(52.dp))
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(6.dp, (-6).dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                        .border(1.5.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (checked) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(app.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** «Переместить…» — куда отправить иконку без перетаскивания. */
@Composable
fun MoveDialog(vm: LauncherViewModel, id: String, currentPage: Int, onDismiss: () -> Unit) =
    MoveDialog(vm, listOf(id), currentPage, onDismiss)

/** «Переместить…» для нескольких иконок сразу. */
@Composable
fun MoveDialog(vm: LauncherViewModel, ids: List<String>, currentPage: Int, onDismiss: () -> Unit) {
    val id = ids.first()
        val anyApp = ids.any { !it.startsWith(FOLDER_PREFIX) }
    val name = if (ids.size > 1) null else if (id.startsWith(FOLDER_PREFIX)) vm.folders[id]?.name else vm.appMap()[id]?.label
    val foldersHere = vm.pages.flatten().filter { it.startsWith(FOLDER_PREFIX) && it !in ids }.mapNotNull { vm.folders[it] }
    fun each(f: (String) -> Unit) { ids.forEach(f); onDismiss() }
    val otherDesktops = vm.desktops.filter { it.id != vm.activeDesktop && (!it.secret || vm.secretUnlocked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (name != null) "Переместить «$name»" else "Переместить ${ids.size} шт.") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("Экран", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                vm.pages.indices.forEach { i ->
                    MoveRow(Icons.Default.Smartphone, "Экран ${i + 1}" + if (i == currentPage) " (этот)" else "") {
                        each { vm.moveToPage(it, i) }
                    }
                }
                MoveRow(Icons.Default.AddToPhotos, "Новый экран") { val p = vm.pages.size; each { vm.moveToPage(it, p) } }
                if (anyApp && foldersHere.isNotEmpty()) {
                    Text("В папку", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                    foldersHere.forEach { f ->
                        MoveRow(Icons.Default.Folder, f.name) { each { vm.moveToFolder(it, f.id) } }
                    }
                }
                if (otherDesktops.isNotEmpty()) {
                    Text("На другой рабочий стол", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                    otherDesktops.forEach { d ->
                        MoveRow(if (d.secret) Icons.Default.Lock else Icons.Default.DesktopWindows, d.name) {
                            each { vm.moveToDesktop(it, d.id) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun MoveRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

@Composable
fun unusedColor() = Color.Unspecified
