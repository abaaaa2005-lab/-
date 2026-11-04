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
)

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
    val iconSizeDp: Int = 52,
    val showLabels: Boolean = true,
    val wallpaperDim: Float = 0.25f,
    val use24h: Boolean = true,
    val showSeconds: Boolean = false,
    val showGreeting: Boolean = true,
    val showWeather: Boolean = true,
    val showTools: Boolean = true,
    val showDock: Boolean = true,
    val showBattery: Boolean = true,
    val weatherCity: String = "Москва",
    val weatherLat: Double = 55.7558,
    val weatherLon: Double = 37.6173,
    val swipeDownNotifications: Boolean = true,
    val doubleTapLock: Boolean = true,
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
