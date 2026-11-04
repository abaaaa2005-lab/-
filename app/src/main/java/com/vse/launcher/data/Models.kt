package com.vse.launcher.data

import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap

/** Приложение, найденное на устройстве. */
data class AppInfo(
    val key: String,            // package/class — уникальный ключ
    val label: String,          // имя (с учётом переименования)
    val originalLabel: String,
    val packageName: String,
    val className: String,
    val user: UserHandle,
    val icon: ImageBitmap?,
    val installTime: Long,
    val isSystem: Boolean,
    val category: Int = -1,
)

/** Папка на рабочем столе. */
data class Folder(val id: String, val name: String, val apps: List<String>, val big: Boolean = false)

/**
 * Рабочий стол (как виртуальные рабочие столы в Windows): свои экраны с иконками и свои обои.
 * wallpaper: "system" — системные обои, "grad:N" — градиент из набора, "file:/путь" — картинка из галереи.
 */
data class Desktop(val id: String, val name: String, val wallpaper: String = "system")

/** Готовые обои-градиенты. */
val WALLPAPER_PRESETS: List<Pair<String, List<Long>>> = listOf(
    "Синий" to listOf(0xFF001A4D, 0xFF0063B1, 0xFF4CC2FF),
    "Закат" to listOf(0xFF2B1055, 0xFFD53369, 0xFFDAAE51),
    "Аврора" to listOf(0xFF0F2027, 0xFF203A43, 0xFF2C9C7A),
    "Океан" to listOf(0xFF1A2980, 0xFF26D0CE),
    "Фиолет" to listOf(0xFF2F0743, 0xFF41295A, 0xFF8E54E9),
    "Персик" to listOf(0xFFFF6A88, 0xFFFF9A8B, 0xFFFFC3A0),
    "Лес" to listOf(0xFF134E5E, 0xFF71B280),
    "Ночь" to listOf(0xFF000000, 0xFF232526, 0xFF414345),
    "Степь" to listOf(0xFF00AFCA, 0xFFFEC50C),
    "Лаванда" to listOf(0xFF6A82FB, 0xFFFC5C7D),
)

const val FOLDER_PREFIX = "folder:"
const val DOCK_MAX = 4

data class Note(
    val id: Long,
    val title: String,
    val text: String,
    val updated: Long,
)

data class Todo(
    val id: Long,
    val text: String,
    val done: Boolean,
)

enum class ThemeMode(val title: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
    AMOLED("Чёрная (AMOLED)"),
}

enum class DrawerSort(val title: String) {
    ALPHA("По алфавиту"),
    USAGE("Часто используемые"),
    RECENT("Недавно установленные"),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val accent: Long = 0xFF6C5CE7,
    val columns: Int = 4,
    val iconSizeDp: Int = 48,
    val showLabels: Boolean = true,
    val wallpaperDim: Float = 0.15f,
    val use24h: Boolean = true,
    val showSeconds: Boolean = false,
    val showGreeting: Boolean = true,
    val showWeather: Boolean = true,
    val showTools: Boolean = true,
    val showDock: Boolean = true,
    val showBattery: Boolean = true,
    val weatherCity: String = "Алматы",
    val weatherLat: Double = 43.2389,
    val weatherLon: Double = 76.8897,
    val swipeDownMode: Int = 0,          // 0 — поиск, 1 — уведомления, 2 — ничего
    val doubleTapLock: Boolean = false,
    // Часы
    val showClock: Boolean = true,
    val clockSize: Float = 84f,
    val clockWeight: Int = 1,           // 0..4: тонкий..очень жирный
    val clockFont: Int = 0,             // 0 обычный, 1 с засечками, 2 моноширинный, 3 рукописный
    val clockAlign: Int = 1,            // 0 слева, 1 по центру, 2 справа
    val clockColor: Long = 0xFFFFFFFF,
    val showDate: Boolean = true,
    // Иконки
    val iconShape: Int = 0,             // 0 как на iPhone, 1 круг, 2 системная
    val drawerSort: DrawerSort = DrawerSort.ALPHA,
    val searchEngine: Int = 0,
    val userName: String = "",
)

val SEARCH_ENGINES = listOf(
    "Google" to "https://www.google.com/search?q=",
    "Яндекс" to "https://yandex.ru/search/?text=",
    "DuckDuckGo" to "https://duckduckgo.com/?q=",
    "Bing" to "https://www.bing.com/search?q=",
)

val ACCENTS = listOf(
    0xFF6C5CE7, 0xFF0984E3, 0xFF00B894, 0xFF00CEC9, 0xFFE17055,
    0xFFD63031, 0xFFE84393, 0xFFFDCB6E, 0xFF2D3436, 0xFF8D6E63,
)
