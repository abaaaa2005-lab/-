package com.vse.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vse.launcher.data.*
import com.vse.launcher.util.SystemActions
import com.vse.launcher.util.Weather
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

// ======================================================================
//  Вспомогательное
// ======================================================================

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

/** «Стекло» для виджетов и дока. */
private val Glass = Color.White.copy(alpha = 0.20f)
private val GlassDark = Color.Black.copy(alpha = 0.22f)

val CLOCK_WEIGHTS = listOf(FontWeight.Thin, FontWeight.Light, FontWeight.Normal, FontWeight.Bold, FontWeight.Black)
val CLOCK_WEIGHT_NAMES = listOf("Тонкий", "Лёгкий", "Обычный", "Жирный", "Очень жирный")
val CLOCK_FONTS = listOf(FontFamily.Default, FontFamily.Serif, FontFamily.Monospace, FontFamily.Cursive)
val CLOCK_FONT_NAMES = listOf("Обычный", "С засечками", "Моноширинный", "Рукописный")
val CLOCK_COLORS = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFD166, 0xFFFF7675, 0xFF74B9FF, 0xFF55EFC4, 0xFFA29BFE, 0xFFFD79A8)

/** Номер «ячейки» для поиска касаний. page = -1 — док. */
private data class Slot(val page: Int, val index: Int)

/** Координаты элементов рабочего стола — нужны, чтобы понимать, куда нажали и куда тащат. */
private class HitMap {
    val cells = HashMap<Slot, Rect>()
    /** Какой элемент (индекс в списке экрана) занимает ячейку. */
    val owner = HashMap<Slot, Int>()
    /** Где на экране иконка каждого элемента. */
    val iconById = HashMap<String, Rect>()
    /** Приложения внутри больших папок: id папки → (приложение, его иконка). */
    val inner = HashMap<String, List<Pair<String, Rect>>>()
    var dock = Rect.Zero
    var clock = Rect.Zero
    var dateLine = Rect.Zero
    var origin = Offset.Zero

    fun local(r: Rect) = r.translate(-origin)
}

private class HomeActions(
    val tap: (Offset) -> Unit,
    val longPress: (Offset) -> Unit,
    val longPressMove: (Offset) -> Unit,
    val longPressEnd: () -> Unit,
    val verticalSwipe: (Float) -> Unit,
)

// ======================================================================
//  Рабочий стол
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: LauncherViewModel, open: (Screen) -> Unit) {
    val s = vm.settings
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val pager = rememberPagerState(initialPage = 1) { 1 + vm.pages.size }
    val hits = remember { HitMap() }

    // Перетаскивание
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var mergeTarget by remember { mutableStateOf<String?>(null) }
    var pressedId by remember { mutableStateOf<String?>(null) }
    var pressStart by remember { mutableStateOf(Offset.Zero) }
    var gestureLock by remember { mutableStateOf(false) }
    var edge by remember { mutableIntStateOf(0) }

    // Окна поверх рабочего стола
    var openFolder by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Pair<String, Rect>?>(null) }
    var renaming by remember { mutableStateOf<AppInfo?>(null) }
    var clockSheet by remember { mutableStateOf(false) }
    var confirmFolderDelete by remember { mutableStateOf<String?>(null) }
    var lastEmptyTap by remember { mutableLongStateOf(0L) }

    val apps = remember(vm.allApps, vm.renamed) { vm.appMap() }
    val iconSize = s.iconSizeDp.dp
    val cellH = iconSize + (if (s.showLabels) 18.dp else 0.dp) + 22.dp
    var clockHeightPx by remember { mutableIntStateOf(0) }

    // Пока приложения грузятся, экранов ещё нет — как только появятся, встаём на первый
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(pager.pageCount) {
        if (!positioned && pager.pageCount > 1) {
            pager.scrollToPage(1)
            positioned = true
        }
    }

    // Кнопка «Домой» — на первый экран, выйти из редактирования
    LaunchedEffect(vm.homeSignal) {
        if (vm.homeSignal > 0) {
            openFolder = null
            menuFor = null
            if (pager.currentPage != 1) pager.animateScrollToPage(1)
        }
    }
    BackHandler(enabled = vm.editMode || openFolder != null) {
        if (openFolder != null) openFolder = null else vm.exitEditMode()
    }

    fun currentAppPage() = pager.currentPage - 1

    fun slotItem(slot: Slot): String? =
        if (slot.page == -1) vm.dock.getOrNull(slot.index)
        else hits.owner[slot]?.let { vm.pages.getOrNull(slot.page)?.getOrNull(it) }

    fun iconRect(id: String): Rect? = hits.iconById[id]?.let { hits.local(it) }

    fun hitSlot(pos: Offset): Slot? {
        if (hits.local(hits.dock).contains(pos)) {
            return hits.cells.entries.firstOrNull { it.key.page == -1 && hits.local(it.value).contains(pos) }?.key
                ?: Slot(-1, vm.dock.size)
        }
        val p = currentAppPage()
        if (p < 0) return null
        return hits.cells.entries.firstOrNull { it.key.page == p && hits.local(it.value).contains(pos) }?.key
    }

    fun updateHover(pos: Offset) {
        val id = dragId ?: return
        val w = with(density) { 28.dp.toPx() }
        val width = with(density) { (ctx.resources.displayMetrics.widthPixels).toFloat() }
        edge = when {
            pos.x < w -> -1
            pos.x > width - w -> 1
            else -> 0
        }
        val slot = hitSlot(pos) ?: return
        val target = slotItem(slot)
        val iconRect = target?.let { iconRect(it) }
        if (target != null && target != id && iconRect != null && !id.startsWith(FOLDER_PREFIX)) {
            val inner = Rect(
                iconRect.left + iconRect.width * 0.22f, iconRect.top + iconRect.height * 0.22f,
                iconRect.right - iconRect.width * 0.22f, iconRect.bottom - iconRect.height * 0.22f,
            )
            if (inner.contains(pos)) {
                if (mergeTarget != target) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                mergeTarget = target
                return
            }
        }
        mergeTarget = null
        if (slot.page == -1) {
            val inDock = id in vm.dock
            vm.moveItem(id, LauncherViewModel.Target.Dock(slot.index.coerceAtMost(if (inDock) vm.dock.size - 1 else vm.dock.size)))
        } else {
            val list = vm.pages.getOrNull(slot.page) ?: emptyList()
            val same = id in list
            val index = hits.owner[slot] ?: list.size
            vm.moveItem(id, LauncherViewModel.Target.Page(slot.page, index.coerceAtMost(if (same) list.size - 1 else list.size)))
        }
    }

    // Автоперелистывание, когда иконку подносят к краю экрана
    LaunchedEffect(edge, dragId) {
        while (dragId != null && edge != 0) {
            delay(650)
            if (dragId == null || edge == 0) break
            val next = pager.currentPage + edge
            if (next < 1) break
            if (next >= pager.pageCount) vm.addPage()
            pager.animateScrollToPage(next)
            updateHover(dragPos)
        }
    }

    val actions = rememberUpdatedState(
        HomeActions(
            tap = { pos ->
                menuFor = null
                val slot = hitSlot(pos)
                val id = slot?.let { slotItem(it) }
                when {
                    id == null && hits.local(hits.dateLine).contains(pos) && pager.currentPage == 1 && !vm.editMode ->
                        open(Screen.Tool(ToolId.WEATHER))
                    id == null && hits.local(hits.clock).contains(pos) && pager.currentPage == 1 && !vm.editMode ->
                        SystemActions.alarms(ctx)
                    id == null -> {
                        if (vm.editMode) vm.exitEditMode()
                        else if (s.doubleTapLock && pager.currentPage >= 1) {
                            val t = System.currentTimeMillis()
                            if (t - lastEmptyTap < 350) SystemActions.lockScreen(ctx)
                            lastEmptyTap = t
                        }
                    }
                    vm.editMode -> {
                        val icon = iconRect(id)
                        val badge = with(density) { 26.dp.toPx() }
                        val onBadge = icon != null && pos.x < icon.left + badge && pos.y < icon.top + badge
                        if (onBadge) {
                            if (id.startsWith(FOLDER_PREFIX)) confirmFolderDelete = id else vm.removeFromHome(id)
                        } else if (id.startsWith(FOLDER_PREFIX)) openFolder = id
                    }
                    id.startsWith(FOLDER_PREFIX) -> {
                        // В большой папке приложения запускаются прямо с рабочего стола
                        val hit = hits.inner[id]?.firstOrNull { hits.local(it.second).contains(pos) }?.first
                        val app = hit?.let { apps[it] }
                        if (app != null) vm.launch(app) else openFolder = id
                    }
                    else -> apps[id]?.let { vm.launch(it) }
                }
            },
            longPress = { pos ->
                val slot = hitSlot(pos)
                val id = slot?.let { slotItem(it) }
                gestureLock = true
                pressStart = pos
                dragPos = pos
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                when {
                    id != null -> pressedId = id
                    pager.currentPage == 1 && hits.local(hits.clock).contains(pos) && !vm.editMode -> {
                        clockSheet = true; gestureLock = false
                    }
                    pager.currentPage >= 1 -> { vm.editMode = true; gestureLock = false }
                    else -> gestureLock = false
                }
            },
            longPressMove = { pos ->
                dragPos = pos
                val pid = pressedId
                if (dragId == null && pid != null && (pos - pressStart).getDistance() > with(density) { 12.dp.toPx() }) {
                    menuFor = null
                    vm.editMode = true
                    dragId = pid
                }
                if (dragId != null) updateHover(pos)
            },
            longPressEnd = {
                val id = dragId
                if (id != null) {
                    mergeTarget?.let { vm.mergeInto(id, it) }
                } else {
                    val pid = pressedId
                    if (pid != null) {
                        val rect = iconRect(pid)
                        if (rect != null) menuFor = pid to rect
                    }
                }
                dragId = null
                pressedId = null
                mergeTarget = null
                gestureLock = false
                edge = 0
            },
            verticalSwipe = { dy ->
                if (vm.editMode || pager.currentPage == 0) return@HomeActions
                if (dy < 0) open(Screen.Drawer())
                else when (s.swipeDownMode) {
                    0 -> open(Screen.Drawer(search = true))
                    1 -> SystemActions.expandNotifications(ctx)
                }
            },
        )
    )

    val desktop = vm.currentDesktop()
    var taskView by remember { mutableStateOf(false) }
    // Плавное появление при переключении рабочего стола
    val appear = remember { Animatable(if (vm.desktopSwitched > 0) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(320)) }
    var showDesktopName by remember { mutableStateOf(vm.desktopSwitched > 0 && System.currentTimeMillis() - vm.desktopSwitched < 2000) }
    LaunchedEffect(showDesktopName) { if (showDesktopName) { delay(1400); showDesktopName = false } }

    DesktopWallpaper(desktop.wallpaper, Modifier.fillMaxSize())
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = appear.value
                val sc = 0.94f + 0.06f * appear.value
                scaleX = sc; scaleY = sc
            }
            .background(Color.Black.copy(alpha = s.wallpaperDim))
            .onGloballyPositioned { hits.origin = it.boundsInRoot().topLeft }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var consumed = false
                    val r = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var res = 0
                        while (res == 0) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            if (c == null) { res = 3; break }
                            if (c.isConsumed) consumed = true
                            if (c.changedToUp() || !c.pressed) res = if (c.isConsumed) 3 else 1
                            else if ((c.position - start).getDistance() > viewConfiguration.touchSlop) res = 2
                        }
                        res
                    }
                    when (r) {
                        null -> {
                            actions.value.longPress(start)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (c.changedToUp() || !c.pressed) break
                                actions.value.longPressMove(c.position)
                                c.consume()
                            }
                            actions.value.longPressEnd()
                        }
                        1 -> if (!consumed) actions.value.tap(start)
                        2 -> {
                            var last = start
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (c.isConsumed) consumed = true
                                last = c.position
                                if (c.changedToUp() || !c.pressed) break
                            }
                            val d = last - start
                            if (!consumed && abs(d.y) > abs(d.x) * 1.3f && abs(d.y) > 70.dp.toPx()) {
                                actions.value.verticalSwipe(d.y)
                            }
                        }
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            if (vm.editMode) {
                EditBar(
                    onAddPage = {
                        vm.addPage()
                        scope.launch { pager.animateScrollToPage(vm.pages.size) }
                    },
                    onAutoFolders = { vm.autoFolders() },
                    onDesktops = { taskView = true },
                    onWallpaper = { SystemActions.pickWallpaper(ctx) },
                    onSettings = { open(Screen.Settings) },
                    onDone = { vm.exitEditMode() },
                )
            } else if (vm.desktops.size > 1) {
                DesktopPill(desktop.name) { taskView = true }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val gridH = maxHeight - 8.dp
                val clockH = with(density) { clockHeightPx.toDp() }
                val rowsOther = floor(gridH / cellH).toInt().coerceAtLeast(1)
                val rowsFirst = (if (s.showClock) floor((gridH - clockH) / cellH).toInt() else rowsOther).coerceAtLeast(1)
                LaunchedEffect(rowsFirst, rowsOther, s.columns) {
                    vm.setGrid(rowsFirst, rowsOther, s.columns)
                }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !gestureLock && dragId == null,
                    key = { it },
                ) { pi ->
                    if (pi == 0) {
                        TodayPage(vm, open)
                    } else {
                        val page = pi - 1
                        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                            if (page == 0 && s.showClock) {
                                ClockWidget(
                                    s, vm,
                                    modifier = Modifier
                                        .onSizeChanged { clockHeightPx = it.height }
                                        .onGloballyPositioned { hits.clock = it.boundsInRoot() },
                                    onDateLine = { hits.dateLine = it },
                                    jiggle = vm.editMode,
                                )
                            }
                            AppGrid(
                                vm = vm,
                                page = page,
                                items = vm.pages.getOrNull(page) ?: emptyList(),
                                rows = if (page == 0) rowsFirst else rowsOther,
                                columns = s.columns,
                                apps = apps,
                                hits = hits,
                                dragId = dragId,
                                mergeTarget = mergeTarget,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                // Пустой рабочий стол — подсказка
                val cur = pager.currentPage - 1
                if (!vm.editMode && cur == 0 && vm.pages.all { it.isEmpty() } && !vm.loading) {
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("«${desktop.name}» пока пуст", style = MaterialTheme.typography.titleMedium.onWallpaper())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Свайп вверх → удерживайте приложение → «Добавить на экран «Домой»»",
                            style = MaterialTheme.typography.bodyMedium.onWallpaper(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // Пустой экран в режиме редактирования — можно удалить
                if (vm.editMode && cur >= 0 && vm.pages.getOrNull(cur)?.isEmpty() == true && vm.pages.size > 1 && dragId == null) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Пустой экран", style = MaterialTheme.typography.titleMedium.onWallpaper())
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton({ vm.removePage(cur) }) { Text("Удалить экран") }
                    }
                }
            }

            PageDots(pager.pageCount, pager.currentPage)
            if (s.showDock && (vm.dock.isNotEmpty() || vm.editMode)) {
                Dock(vm, apps, hits, dragId, mergeTarget)
            } else {
                hits.dock = Rect.Zero
            }
            Spacer(Modifier.height(8.dp))
        }

        // Иконка, которую тащат
        val dragging = dragId
        if (dragging != null) {
            val half = with(density) { (iconSize / 2).toPx() }
            Box(Modifier.offset { IntOffset((dragPos.x - half).roundToInt(), (dragPos.y - half).roundToInt()) }.scale(1.12f)) {
                HomeIcon(dragging, apps, vm.folders, iconSize, showLabel = false, forceSmall = true)
            }
        }

        // Контекстное меню по долгому нажатию
        menuFor?.let { (id, rect) ->
            Box(
                Modifier
                    .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                    .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
            ) {
                if (id.startsWith(FOLDER_PREFIX)) {
                    DropdownMenu(expanded = true, onDismissRequest = { menuFor = null }, shape = RoundedCornerShape(16.dp)) {
                        DropdownMenuItem(text = { Text("Открыть папку") }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, onClick = { menuFor = null; openFolder = id })
                        val big = vm.folders[id]?.big == true
                        DropdownMenuItem(
                            text = { Text(if (big) "Сделать маленькой" else "Сделать большой (2×2)") },
                            leadingIcon = { Icon(if (big) Icons.Default.ZoomInMap else Icons.Default.ZoomOutMap, null) },
                            onClick = { menuFor = null; vm.toggleFolderBig(id) },
                        )
                        DropdownMenuItem(text = { Text("Изменить экран «Домой»") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuFor = null; vm.editMode = true })
                        DropdownMenuItem(text = { Text("Удалить папку") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuFor = null; confirmFolderDelete = id })
                    }
                } else apps[id]?.let { app ->
                    AppMenu(
                        app, vm, expanded = true,
                        onDismiss = { menuFor = null },
                        onRename = { renaming = app },
                        onEdit = { vm.editMode = true },
                    )
                }
            }
        }

        // Открытая папка — «окно» поверх рабочего стола
        AnimatedVisibility(
            visible = openFolder != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
        ) {
            var lastFolder by remember { mutableStateOf<String?>(null) }
            if (openFolder != null) lastFolder = openFolder
            lastFolder?.let { fid -> FolderWindow(vm, fid, onClose = { openFolder = null }) }
        }

        // Название стола после переключения
        AnimatedVisibility(
            visible = showDesktopName,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                desktop.name,
                style = MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }

        // «Представление задач» — все рабочие столы, как в Windows
        AnimatedVisibility(visible = taskView, enter = fadeIn() + scaleIn(initialScale = 1.06f), exit = fadeOut() + scaleOut(targetScale = 1.06f)) {
            TaskView(vm, apps, onClose = { taskView = false })
        }
    }
    BackHandler(enabled = taskView) { taskView = false }

    renaming?.let { app -> RenameDialog(app, vm) { renaming = null } }

    confirmFolderDelete?.let { fid ->
        AlertDialog(
            onDismissRequest = { confirmFolderDelete = null },
            title = { Text("Удалить папку «${vm.folders[fid]?.name ?: ""}»?") },
            text = { Text("Приложения не удалятся — они останутся в меню всех приложений.") },
            confirmButton = { TextButton({ vm.removeFromHome(fid); confirmFolderDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton({ confirmFolderDelete = null }) { Text("Отмена") } },
        )
    }

    if (clockSheet) {
        ModalBottomSheet(onDismissRequest = { clockSheet = false }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Text("Часы", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
                ClockControls(vm)
            }
        }
    }
}

// ======================================================================
//  Сетка иконок одного экрана
// ======================================================================

@Composable
private fun AppGrid(
    vm: LauncherViewModel,
    page: Int,
    items: List<String>,
    rows: Int,
    columns: Int,
    apps: Map<String, AppInfo>,
    hits: HitMap,
    dragId: String?,
    mergeTarget: String?,
    modifier: Modifier,
) {
    val s = vm.settings
    val placed = remember(items, rows, columns, vm.folders) {
        com.vse.launcher.util.GridPlacer.place(items, rows, columns) { vm.isBigFolder(it) }.first
    }
    // Запоминаем, какой элемент в какой ячейке — для перетаскивания и нажатий
    SideEffect {
        hits.owner.keys.removeAll { it.page == page }
        placed.forEach { p ->
            for (dr in 0 until p.span) for (dc in 0 until p.span) {
                hits.owner[Slot(page, (p.row + dr) * columns + p.col + dc)] = p.itemIndex
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cw = maxWidth / columns
        val rh = maxHeight / rows
        // Ячейки (в том числе пустые) — чтобы знать, куда бросают иконку
        for (r in 0 until rows) for (c in 0 until columns) {
            val slot = Slot(page, r * columns + c)
            Box(
                Modifier.offset(cw * c, rh * r).size(cw, rh)
                    .onGloballyPositioned { hits.cells[slot] = it.boundsInRoot() },
            )
        }
        placed.forEach { p ->
            val id = items[p.itemIndex]
            Box(
                Modifier.offset(cw * p.col, rh * p.row).size(cw * p.span, rh * p.span),
                contentAlignment = Alignment.Center,
            ) {
                HomeIcon(
                    id, apps, vm.folders, s.iconSizeDp.dp, s.showLabels,
                    jiggle = vm.editMode, jigglePhase = p.itemIndex,
                    hidden = id == dragId,
                    highlighted = id == mergeTarget,
                    onIconPositioned = { hits.iconById[id] = it },
                    onInner = { hits.inner[id] = it },
                    bigArea = if (p.span == 2) Pair(cw * 2, rh * 2) else null,
                )
            }
        }
    }
}

/** Иконка приложения или папки на рабочем столе (без собственных жестов — их обрабатывает рабочий стол). */
@Composable
private fun HomeIcon(
    id: String,
    apps: Map<String, AppInfo>,
    folders: Map<String, Folder>,
    iconSize: androidx.compose.ui.unit.Dp,
    showLabel: Boolean,
    jiggle: Boolean = false,
    jigglePhase: Int = 0,
    hidden: Boolean = false,
    highlighted: Boolean = false,
    onIconPositioned: (Rect) -> Unit = {},
    onInner: (List<Pair<String, Rect>>) -> Unit = {},
    bigArea: Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>? = null,
    forceSmall: Boolean = false,
) {
    val folder = if (id.startsWith(FOLDER_PREFIX)) folders[id] else null
    val app = if (folder == null) apps[id] else null
    if (folder == null && app == null) return
    val label = folder?.name ?: app!!.label
    val big = folder != null && folder.big && bigArea != null && !forceSmall

    val rotation = if (jiggle) {
        val t = rememberInfiniteTransition(label = "jiggle")
        val a by t.animateFloat(
            initialValue = if (big) -0.8f else -1.6f, targetValue = if (big) 0.8f else 1.6f,
            animationSpec = infiniteRepeatable(tween(130 + (jigglePhase % 3) * 15, easing = LinearEasing), RepeatMode.Reverse),
            label = "rot",
        )
        if (jigglePhase % 2 == 0) a else -a
    } else 0f
    val scale by animateFloatAsState(if (highlighted) (if (big) 1.06f else 1.18f) else 1f, label = "hl")

    // Размер плитки: обычная иконка или большая папка 2×2
    val tile = if (big) {
        val (w, h) = bigArea!!
        minOf(w - 20.dp, h - (if (showLabel) 24.dp else 6.dp) - 12.dp)
    } else iconSize

    Column(
        Modifier.alpha(if (hidden) 0f else 1f).rotate(rotation),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(tile)
                .scale(scale)
                .onGloballyPositioned { onIconPositioned(it.boundsInRoot()) },
        ) {
            when {
                big -> BigFolderTile(folder!!, apps, tile, onInner)
                folder != null -> FolderIcon(folder, apps, tile)
                app!!.icon != null -> Image(app.icon!!, app.label, Modifier.fillMaxSize())
                else -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22)).background(Glass), contentAlignment = Alignment.Center) {
                    Text(app.label.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            if (jiggle) {
                Box(
                    Modifier
                        .offset((-6).dp, (-6).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xE6D0D0D6)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Remove, "Убрать", tint = Color.Black, modifier = Modifier.size(16.dp)) }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp).onWallpaper(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = tile + 22.dp),
            )
        }
    }
}

@Composable
private fun FolderIcon(folder: Folder, apps: Map<String, AppInfo>, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(24)).background(Glass).padding(size / 8),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(size / 20)) {
            folder.apps.take(9).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(size / 20)) {
                    row.forEach { k ->
                        val a = apps[k]
                        if (a?.icon != null) Image(a.icon, null, Modifier.size(size / 4.6f))
                        else Spacer(Modifier.size(size / 4.6f))
                    }
                }
            }
        }
    }
}

/** Большая папка 2×2: первые три приложения крупно (открываются сразу), в четвёртом углу — остальные. */
@Composable
private fun BigFolderTile(
    folder: Folder,
    apps: Map<String, AppInfo>,
    size: androidx.compose.ui.unit.Dp,
    onInner: (List<Pair<String, Rect>>) -> Unit,
) {
    val pad = size * 0.09f
    val gap = size * 0.07f
    val cell = (size - pad * 2 - gap) / 2
    val many = folder.apps.size > 4
    val shown = if (many) folder.apps.take(3) else folder.apps.take(4)
    val rects = remember(folder.apps) { HashMap<String, Rect>() }
    Box(Modifier.size(size).clip(RoundedCornerShape(16)).background(Glass).padding(pad)) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (r in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until 2) {
                        val i = r * 2 + c
                        val k = shown.getOrNull(i)
                        when {
                            k != null -> {
                                val a = apps[k]
                                Box(
                                    Modifier.size(cell).onGloballyPositioned { coords ->
                                        rects[k] = coords.boundsInRoot()
                                        onInner(shown.mapNotNull { key -> rects[key]?.let { key to it } })
                                    },
                                ) {
                                    if (a?.icon != null) Image(a.icon, a.label, Modifier.fillMaxSize())
                                }
                            }
                            many && i == 3 -> {
                                // Остальные приложения мелко — нажатие открывает папку
                                Box(Modifier.size(cell).padding(cell * 0.06f)) {
                                    val mini = (cell * 0.88f - cell * 0.08f) / 2
                                    Column(verticalArrangement = Arrangement.spacedBy(cell * 0.08f)) {
                                        folder.apps.drop(3).take(4).chunked(2).forEach { row ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(cell * 0.08f)) {
                                                row.forEach { kk ->
                                                    val a = apps[kk]
                                                    if (a?.icon != null) Image(a.icon, null, Modifier.size(mini))
                                                    else Spacer(Modifier.size(mini))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> Spacer(Modifier.size(cell))
                        }
                    }
                }
            }
        }
    }
}

// ======================================================================
//  Часы (виджет на первом экране)
// ======================================================================

@Composable
fun ClockWidget(
    s: Settings,
    vm: LauncherViewModel,
    modifier: Modifier = Modifier,
    onDateLine: (Rect) -> Unit = {},
    jiggle: Boolean = false,
) {
    val now = rememberNow()
    val ru = Locale("ru")
    val pattern = (if (s.use24h) "HH:mm" else "h:mm") + if (s.showSeconds) ":ss" else ""
    val time = SimpleDateFormat(pattern, ru).format(Date(now))
    val date = SimpleDateFormat("EEEE, d MMMM", ru).format(Date(now)).replaceFirstChar { it.uppercase() }
    val color = Color(s.clockColor)
    val align = when (s.clockAlign) { 0 -> Alignment.Start; 2 -> Alignment.End; else -> Alignment.CenterHorizontally }
    val textAlign = when (s.clockAlign) { 0 -> TextAlign.Start; 2 -> TextAlign.End; else -> TextAlign.Center }
    val shadow = if (s.clockColor == 0xFF000000) null else WallpaperShadow
    val w = vm.weather

    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).alpha(if (jiggle) 0.75f else 1f),
        horizontalAlignment = align,
    ) {
        if (s.showDate || (s.showWeather && w != null)) {
            val line = buildString {
                if (s.showDate) append(date)
                if (s.showWeather && w != null) {
                    if (isNotEmpty()) append("   ")
                    append("${Weather.emoji(w.code)} ${w.temp.roundToInt()}°")
                }
            }
            Text(
                line,
                style = TextStyle(color = color, fontSize = (s.clockSize * 0.2f).coerceIn(14f, 24f).sp, fontWeight = FontWeight.SemiBold, shadow = shadow),
                textAlign = textAlign,
                modifier = Modifier.onGloballyPositioned { onDateLine(it.boundsInRoot()) },
            )
        }
        Text(
            time,
            style = TextStyle(
                color = color,
                fontSize = s.clockSize.sp,
                lineHeight = (s.clockSize * 1.05f).sp,
                fontWeight = CLOCK_WEIGHTS.getOrElse(s.clockWeight) { FontWeight.Light },
                fontFamily = CLOCK_FONTS.getOrElse(s.clockFont) { FontFamily.Default },
                shadow = shadow,
            ),
            textAlign = textAlign,
            maxLines = 1,
        )
    }
}

/** Настройки часов: размер, толщина, шрифт, цвет, выравнивание. Используется в шторке и в настройках. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClockControls(vm: LauncherViewModel) {
    val s = vm.settings
    Column(Modifier.padding(horizontal = 20.dp)) {
        // Предпросмотр
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2D3436)).padding(vertical = 8.dp),
        ) { ClockWidget(s, vm) }
        Spacer(Modifier.height(12.dp))
        Text("Размер: ${s.clockSize.roundToInt()}", style = MaterialTheme.typography.bodyLarge)
        Slider(s.clockSize, { v -> vm.updateSettings { it.copy(clockSize = v) } }, valueRange = 36f..180f)
        Text("Толщина", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CLOCK_WEIGHT_NAMES.forEachIndexed { i, n ->
                FilterChip(s.clockWeight == i, { vm.updateSettings { it.copy(clockWeight = i) } }, label = { Text(n) })
            }
        }
        Text("Шрифт", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CLOCK_FONT_NAMES.forEachIndexed { i, n ->
                FilterChip(s.clockFont == i, { vm.updateSettings { it.copy(clockFont = i) } }, label = { Text(n, fontFamily = CLOCK_FONTS[i]) })
            }
        }
        Text("Положение", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Слева", "По центру", "Справа").forEachIndexed { i, n ->
                FilterChip(s.clockAlign == i, { vm.updateSettings { it.copy(clockAlign = i) } }, label = { Text(n) })
            }
        }
        Text("Цвет", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            CLOCK_COLORS.forEach { c ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .then(
                            if (c == s.clockColor) Modifier.background(Color.Transparent).padding(2.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(3.dp)
                                .clip(CircleShape).background(Color(c))
                            else Modifier
                        )
                        .clickable { vm.updateSettings { it.copy(clockColor = c) } },
                )
            }
        }
        SwitchLine("Показывать часы", s.showClock) { v -> vm.updateSettings { it.copy(showClock = v) } }
        SwitchLine("Дата над часами", s.showDate) { v -> vm.updateSettings { it.copy(showDate = v) } }
        SwitchLine("Погода рядом с датой", s.showWeather) { v -> vm.updateSettings { it.copy(showWeather = v) }; vm.refreshWeather() }
        SwitchLine("24-часовой формат", s.use24h) { v -> vm.updateSettings { it.copy(use24h = v) } }
        SwitchLine("Секунды", s.showSeconds) { v -> vm.updateSettings { it.copy(showSeconds = v) } }
    }
}

@Composable
private fun SwitchLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

// ======================================================================
//  Док, точки страниц, панель редактирования
// ======================================================================

@Composable
private fun Dock(vm: LauncherViewModel, apps: Map<String, AppInfo>, hits: HitMap, dragId: String?, mergeTarget: String?) {
    val s = vm.settings
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Glass)
            .onGloballyPositioned { hits.dock = it.boundsInRoot() }
            .padding(vertical = 12.dp, horizontal = 8.dp)
            .height(s.iconSizeDp.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.dock.isEmpty()) {
            hits.cells.keys.removeAll { it.page == -1 }
            Text(
                if (vm.editMode) "Перетащите сюда до $DOCK_MAX приложений" else "",
                style = MaterialTheme.typography.bodySmall.onWallpaper(),
            )
        }
        hits.cells.keys.removeAll { it.page == -1 && it.index >= vm.dock.size }
        vm.dock.forEachIndexed { i, id ->
            val slot = Slot(-1, i)
            Box(
                Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { hits.cells[slot] = it.boundsInRoot() },
                contentAlignment = Alignment.Center,
            ) {
                HomeIcon(
                    id, apps, vm.folders, s.iconSizeDp.dp, showLabel = false,
                    jiggle = vm.editMode, jigglePhase = i + 1,
                    hidden = id == dragId, highlighted = id == mergeTarget,
                    onIconPositioned = { hits.iconById[id] = it },
                )
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(GlassDark).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { i ->
                val active = i == current
                if (i == 0) {
                    Icon(
                        Icons.Default.Widgets, "Сегодня",
                        tint = Color.White.copy(alpha = if (active) 1f else 0.5f),
                        modifier = Modifier.size(10.dp),
                    )
                } else {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = if (active) 1f else 0.45f)))
                }
            }
        }
    }
}

@Composable
private fun EditBar(onAddPage: () -> Unit, onAutoFolders: () -> Unit, onDesktops: () -> Unit, onWallpaper: () -> Unit, onSettings: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassButton(Icons.Default.Add, null, onAddPage)
        GlassButton(Icons.Default.CreateNewFolder, "Папки", onAutoFolders)
        GlassButton(Icons.Default.DesktopWindows, "Столы", onDesktops)
        GlassButton(Icons.Default.Wallpaper, null, onWallpaper)
        GlassButton(Icons.Default.Settings, null, onSettings)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onDone)
                .padding(horizontal = 18.dp, vertical = 9.dp),
        ) { Text("Готово", color = Color.Black, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun GlassButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String?, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Glass).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, text, tint = Color.White, modifier = Modifier.size(18.dp))
        if (text != null) {
            Spacer(Modifier.width(4.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ======================================================================
//  Папка — окно поверх рабочего стола
// ======================================================================

@Composable
private fun FolderWindow(vm: LauncherViewModel, folderId: String, onClose: () -> Unit) {
    val folder = vm.folders[folderId]
    if (folder == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val apps = remember(vm.allApps, vm.renamed) { vm.appMap() }
    var name by remember(folderId) { mutableStateOf(folder.name) }
    val s = vm.settings

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                vm.renameFolder(folderId, name.ifBlank { "Папка" }); onClose()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(14.dp)) {
            BasicTextField(
                name, { name = it; vm.renameFolder(folderId, it) },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                folder.apps.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { k ->
                            val app = apps[k]
                            if (app != null) {
                                AppIcon(
                                    app, vm, s.iconSizeDp.dp + 10.dp, true, onWallpaper = true,
                                    modifier = Modifier.weight(1f),
                                    folderId = folderId,
                                    onLaunched = onClose,
                                )
                            } else Spacer(Modifier.weight(1f))
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f))
                    .clickable { vm.toggleFolderBig(folderId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (folder.big) Icons.Default.ZoomInMap else Icons.Default.ZoomOutMap, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (folder.big) "Сделать маленькой" else "Большая папка на рабочем столе (2×2)", color = Color.White)
            }
            Text(
                "Удерживайте иконку, чтобы вынуть её из папки",
                style = MaterialTheme.typography.bodySmall.onWallpaper(),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

// ======================================================================
//  Экран «Сегодня» (виджеты) — слева от первого экрана, как на iPhone
// ======================================================================

@Composable
private fun TodayPage(vm: LauncherViewModel, open: (Screen) -> Unit) {
    val s = vm.settings
    val ctx = LocalContext.current
    val now = rememberNow()
    val battery = rememberBattery()
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val date = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date(now)).replaceFirstChar { it.uppercase() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.padding(top = 4.dp)) {
            Text(date, style = MaterialTheme.typography.titleMedium.onWallpaper())
            val name = s.userName.trim()
            if (s.showGreeting) {
                Text(
                    greeting(hour) + if (name.isNotEmpty()) ", $name" else "",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold).onWallpaper(),
                )
            }
        }

        // Поиск
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Glass)
                .clickable { open(Screen.Drawer(search = true)) }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.width(10.dp))
            Text("Поиск", color = Color.White.copy(alpha = 0.8f))
        }

        // Погода
        if (s.showWeather) {
            val w = vm.weather
            Widget(onClick = { open(Screen.Tool(ToolId.WEATHER)) }) {
                Text(s.weatherCity, style = MaterialTheme.typography.titleSmall.onWallpaper())
                if (w == null) {
                    Text(
                        if (vm.weatherLoading) "Загрузка…" else (vm.weatherError ?: "Нет данных") + "\nНажмите, чтобы открыть",
                        style = MaterialTheme.typography.bodyMedium.onWallpaper(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${w.temp.roundToInt()}°", style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Light).onWallpaper())
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Weather.emoji(w.code), fontSize = 28.sp)
                            Text(Weather.describe(w.code), style = MaterialTheme.typography.bodyMedium.onWallpaper())
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        w.days.take(5).forEachIndexed { i, d ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (i == 0) "Сег." else dayShort(d.date), style = MaterialTheme.typography.labelSmall.onWallpaper())
                                Text(Weather.emoji(d.code), fontSize = 16.sp)
                                Text("${d.max.roundToInt()}°", style = MaterialTheme.typography.labelMedium.onWallpaper())
                            }
                        }
                    }
                }
            }
        }

        // Батарея + фонарик (два маленьких виджета)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Widget(Modifier.weight(1f)) {
                Text("Батарея", style = MaterialTheme.typography.titleSmall.onWallpaper())
                Spacer(Modifier.height(6.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    CircularProgressIndicator(
                        progress = { (battery.level.coerceAtLeast(0)) / 100f },
                        modifier = Modifier.size(64.dp), strokeWidth = 6.dp,
                        color = if (battery.level in 0..20) Color(0xFFFF7675) else Color(0xFF55EFC4),
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                    Text(
                        if (battery.level >= 0) "${battery.level}%" else "—",
                        style = MaterialTheme.typography.titleMedium.onWallpaper(),
                    )
                }
                if (battery.charging) Text("⚡ Заряжается", style = MaterialTheme.typography.labelSmall.onWallpaper(), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Widget(Modifier.weight(1f), onClick = { vm.toggleTorch() }, active = vm.torchOn) {
                Text("Фонарик", style = MaterialTheme.typography.titleSmall.onWallpaper())
                Spacer(Modifier.height(6.dp))
                Icon(
                    if (vm.torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, null,
                    tint = Color.White, modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
                )
                Text(if (vm.torchOn) "Включён" else "Выключен", style = MaterialTheme.typography.labelSmall.onWallpaper(), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }

        // Инструменты
        if (s.showTools) {
            Widget {
                Text("Инструменты", style = MaterialTheme.typography.titleSmall.onWallpaper())
                Spacer(Modifier.height(8.dp))
                val tools = ToolId.entries.filter { it != ToolId.WEATHER && it != ToolId.FLASHLIGHT }
                tools.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { t ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                RoundTile(t.icon, t.title, onWallpaper = true) { open(Screen.Tool(t)) }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // Задачи
        Widget(onClick = { open(Screen.Tool(ToolId.TODO)) }) {
            val openTodos = vm.todos.filter { !it.done }
            Text("Задачи · ${openTodos.size}", style = MaterialTheme.typography.titleSmall.onWallpaper())
            if (openTodos.isEmpty()) Text("Всё сделано 🎉", style = MaterialTheme.typography.bodyMedium.onWallpaper())
            openTodos.take(4).forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))
                            .clickable { vm.toggleTodo(t.id) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(t.text, style = MaterialTheme.typography.bodyMedium.onWallpaper(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Заметки
        Widget(onClick = { open(Screen.Tool(ToolId.NOTES)) }) {
            Text("Заметки · ${vm.notes.size}", style = MaterialTheme.typography.titleSmall.onWallpaper())
            if (vm.notes.isEmpty()) Text("Нажмите, чтобы создать заметку", style = MaterialTheme.typography.bodyMedium.onWallpaper())
            vm.notes.take(2).forEach { n ->
                Text(
                    n.title.ifBlank { n.text }, style = MaterialTheme.typography.bodyMedium.onWallpaper(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Часто используемые
        val top = remember(vm.launchCounts, vm.allApps) { vm.topApps(4) }
        if (top.isNotEmpty()) {
            Widget {
                Text("Предложения", style = MaterialTheme.typography.titleSmall.onWallpaper())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    top.forEach { a -> AppIcon(a, vm, 48.dp, true, onWallpaper = true, modifier = Modifier.weight(1f)) }
                    repeat(4 - top.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        TextButton({ open(Screen.Settings) }, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Настройки лаунчера", color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
    }
}

private fun dayShort(iso: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!
    SimpleDateFormat("EE", Locale("ru")).format(d).replaceFirstChar { it.uppercase() }
}.getOrDefault("")

@Composable
private fun Widget(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    active: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else Glass)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}
