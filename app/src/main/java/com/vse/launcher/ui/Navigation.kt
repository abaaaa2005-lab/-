package com.vse.launcher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed interface Screen {
    data class Drawer(val search: Boolean = false) : Screen
    data object Tools : Screen
    data class Tool(val id: ToolId) : Screen
    data object Settings : Screen
    data object HiddenApps : Screen
}

enum class ToolId(val title: String, val icon: ImageVector, val keywords: String) {
    CALCULATOR("Калькулятор", Icons.Default.Calculate, "калькулятор посчитать calc"),
    NOTES("Заметки", Icons.Default.EditNote, "заметки блокнот notes"),
    TODO("Задачи", Icons.Default.TaskAlt, "задачи дела список todo"),
    WEATHER("Погода", Icons.Default.WbSunny, "погода прогноз weather"),
    FLASHLIGHT("Фонарик", Icons.Default.FlashlightOn, "фонарик свет вспышка flashlight"),
    STOPWATCH("Секундомер", Icons.Default.AvTimer, "секундомер stopwatch"),
    TIMER("Таймер", Icons.Default.Timer, "таймер обратный отсчёт timer"),
    COMPASS("Компас", Icons.Default.Explore, "компас север compass"),
    LEVEL("Уровень", Icons.Default.Straighten, "уровень ватерпас level"),
    CONVERTER("Конвертер", Icons.Default.SwapHoriz, "конвертер единицы перевести converter"),
    COUNTER("Счётчик", Icons.Default.AddCircleOutline, "счётчик подсчёт counter"),
    RANDOM("Случайность", Icons.Default.Casino, "кубик монетка случайное число random"),
    DEVICE("Об устройстве", Icons.Default.PhoneAndroid, "устройство память батарея device info"),
}
