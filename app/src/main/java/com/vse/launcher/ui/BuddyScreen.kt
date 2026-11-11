package com.vse.launcher.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vse.launcher.buddy.*

private val TABS = listOf("Образ", "Шапки", "Наушники", "Очки", "Аксессуары", "Цвета", "Звук", "Поведение")

/** Настройка помощника: запуск, внешний вид, голос и «мозг». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuddyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { BuddyState.load(ctx) }
    val look = BuddyState.look
    fun set(f: (BuddyLook) -> BuddyLook) = BuddyState.update(ctx, f)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf(Emotion.HAPPY) }
    var talking by remember { mutableStateOf(false) }
    val voice = remember { BuddyVoice(ctx) { t -> talking = t } }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    // --- Запуск: оверлей → разрешения → запись экрана → сервис
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) BuddyService.start(ctx, r.resultCode, r.data)
        else BuddyService.start(ctx, null, null)
    }
    fun askScreen() {
        val mpm = ctx.getSystemService(MediaProjectionManager::class.java)
        projection.launch(mpm.createScreenCaptureIntent())
    }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { askScreen() }
    fun launchBuddy() {
        if (!Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            return
        }
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        perms.launch(list.toTypedArray())
    }

    ScreenScaffold(
        "Помощник ${look.name}", onBack,
        actions = {
            IconButton({ set { BuddyStore.random(it) } }) { Icon(Icons.Default.Casino, "Случайный образ") }
            IconButton({ set { BuddyLook(apiKey = it.apiKey, name = it.name) } }) { Icon(Icons.Default.RestartAlt, "Как на картинке") }
        },
    ) {
        // Превью на «звёздном» фоне, как на картинке
        Box(
            Modifier.fillMaxWidth().height(210.dp).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            BuddyCharacter(look, preview, talking, Modifier.size(180.dp))
        }
        Row(
            Modifier.fillMaxWidth().background(Color.Black).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Emotion.entries.forEach { e ->
                val sel = e == preview
                Text(
                    "${e.emoji} ${e.title}",
                    color = if (sel) Color.Black else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color(0xFF2B2B35))
                        .clickable { preview = e; if (look.chirps) voice.chirp(e) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            // Карточка запуска
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            !BuddyState.running -> "${look.name} спит"
                            BuddyState.seeing -> "${look.name} в углу экрана и смотрит 👀"
                            else -> "${look.name} в углу экрана, но экран не видит"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (look.apiKey.isBlank()) Text(
                        "Чтобы ${look.name} видел экран и отвечал, вставьте ключ Claude API во вкладке «Поведение».",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!BuddyState.running) Button(::launchBuddy) { Text("Запустить") }
                        else {
                            if (!BuddyState.seeing) Button(::askScreen) { Text("Дать зрение") }
                            OutlinedButton({ BuddyService.stop(ctx) }) { Text("Выключить") }
                        }
                    }
                    Text(
                        "Нажатие — комментарий сейчас · двойное — звук вкл/выкл · долгое — меню (вопрос голосом, пауза) · перетаскивание — в другой угол.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                TABS.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }

            when (tab) {
                0 -> {
                    OutlinedTextField(
                        look.name, { v -> set { it.copy(name = v.take(20)) } },
                        label = { Text("Имя") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    Gallery("Форма", Wardrobe.shapes, look.shape) { i -> look.copy(shape = i) }
                    Gallery("Глаза", Wardrobe.eyes, look.eyes) { i -> look.copy(eyes = i) }
                    Gallery("Лицо", Wardrobe.faces, look.face) { i -> look.copy(face = i) }
                    Gallery("Аура", Wardrobe.auras, look.aura) { i -> look.copy(aura = i) }
                    SwitchRow(Icons.Default.Favorite, "Румянец", null, look.blush) { v -> set { it.copy(blush = v) } }
                    SwitchRow(Icons.Default.Circle, "Животик", null, look.belly) { v -> set { it.copy(belly = v) } }
                }
                1 -> { Gallery("Шапки · ${Wardrobe.hats.size - 1} шт.", Wardrobe.hats, look.hat) { i -> look.copy(hat = i) }; Palette("Цвет шапки", look.hatColor) { c -> set { it.copy(hatColor = c) } } }
                2 -> { Gallery("Наушники", Wardrobe.headphones, look.headphones) { i -> look.copy(headphones = i) }; Palette("Цвет наушников", look.phonesColor) { c -> set { it.copy(phonesColor = c) } } }
                3 -> { Gallery("Очки и маски", Wardrobe.glasses, look.glasses) { i -> look.copy(glasses = i) }; Palette("Цвет оправы", look.glassesColor) { c -> set { it.copy(glassesColor = c) } } }
                4 -> { Gallery("Аксессуары", Wardrobe.accessories, look.accessory) { i -> look.copy(accessory = i) }; Palette("Цвет аксессуара", look.accColor) { c -> set { it.copy(accColor = c) } } }
                5 -> {
                    Palette("Тело", look.bodyColor) { c -> set { it.copy(bodyColor = c) } }
                    Palette("Глаза и рот (мультяшные глаза)", look.faceColor) { c -> set { it.copy(faceColor = c) } }
                    Palette("Зрачки (остальные глаза)", look.eyeColor) { c -> set { it.copy(eyeColor = c) } }
                    Palette("Животик", look.bellyColor) { c -> set { it.copy(bellyColor = c, belly = true) } }
                }
                6 -> {
                    SwitchRow(Icons.Default.RecordVoiceOver, "Говорить вслух", "Синтез речи Android", look.voice) { v -> set { it.copy(voice = v) } }
                    SwitchRow(Icons.Default.MusicNote, "Звуки эмоций", "Короткие мелодии на каждую эмоцию", look.chirps) { v -> set { it.copy(chirps = v) } }
                    Slider2("Высота голоса", look.voicePitch, 0.5f..2f) { v -> set { it.copy(voicePitch = v) } }
                    Slider2("Скорость речи", look.voiceRate, 0.6f..1.8f) { v -> set { it.copy(voiceRate = v) } }
                    Button(
                        { preview = Emotion.HAPPY; voice.chirp(Emotion.HAPPY); voice.say("Привет! Я ${look.name}. Так звучит мой голос.", look) },
                        Modifier.padding(horizontal = 16.dp),
                    ) { Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(8.dp)); Text("Послушать") }
                }
                7 -> {
                    SectionTitle("Характер")
                    Chips(Wardrobe.personalities.map { it.first }, look.personality) { i -> set { it.copy(personality = i) } }
                    SectionTitle("Сам комментирует экран")
                    Chips(Wardrobe.intervals.map { if (it == 0) "Только по нажатию" else if (it < 60) "раз в $it с" else "раз в ${it / 60} мин" }, Wardrobe.intervals.indexOf(look.interval).coerceAtLeast(0)) { i -> set { it.copy(interval = Wardrobe.intervals[i]) } }
                    Text(
                        "Если экран не изменился, помощник промолчит и не потратит запрос.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    SectionTitle("Где сидит")
                    Chips(Wardrobe.corners, look.corner) { i -> set { it.copy(corner = i) } }
                    Slider2("Размер: ${look.sizeDp} dp", look.sizeDp.toFloat(), 56f..140f) { v -> set { it.copy(sizeDp = v.toInt()) } }
                    Slider2("Прозрачность", look.opacity, 0.3f..1f) { v -> set { it.copy(opacity = v) } }
                    Slider2("Облачко висит: ${look.bubbleSeconds} с", look.bubbleSeconds.toFloat(), 3f..20f) { v -> set { it.copy(bubbleSeconds = v.toInt()) } }
                    SectionTitle("Мозг (Claude)")
                    Chips(Wardrobe.models.map { it.second }, look.model) { i -> set { it.copy(model = i) } }
                    var showKey by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        look.apiKey, { v -> set { it.copy(apiKey = v.trim()) } },
                        label = { Text("Ключ Claude API") }, placeholder = { Text("sk-ant-…") }, singleLine = true,
                        visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = { IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    Text(
                        "Ключ создаётся на console.anthropic.com и хранится только на этом телефоне. " +
                            "Когда помощник смотрит, уменьшенный снимок экрана отправляется в Claude API. " +
                            "Экраны с защитой (банки, пароли) Android сам закрывает чёрным. Поставьте паузу, если не хотите, чтобы он смотрел.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}

/** Плитки-варианты с живыми мини-превью персонажа. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gallery(title: String, names: List<String>, selected: Int, variant: (Int) -> BuddyLook) {
    val ctx = LocalContext.current
    SectionTitle(title)
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        names.forEachIndexed { i, name ->
            val sel = i == selected
            Column(
                Modifier.width(76.dp).clip(RoundedCornerShape(14.dp))
                    .clickable { val v = variant(i); BuddyState.update(ctx) { v } }
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF0E0E12))
                        .border(if (sel) 3.dp else 0.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    BuddyCharacter(variant(i), Emotion.HAPPY, false, Modifier.size(66.dp), animate = false)
                }
                Text(name, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, textAlign = TextAlign.Center, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Palette(title: String, value: Long, onPick: (Long) -> Unit) {
    SectionTitle(title)
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Wardrobe.palette.forEach { c ->
            val sel = c == value
            val m = Modifier.size(38.dp).clip(CircleShape)
                .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f), CircleShape)
                .clickable { onPick(c) }
            if (c == Wardrobe.RAINBOW) Canvas(m) {
                drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
            } else Box(m.background(Color(c)))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o -> FilterChip(i == selected, { onPick(i) }, label = { Text(o) }) }
    }
}

@Composable
private fun Slider2(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
