package com.vse.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Простое хранилище на SharedPreferences + JSON. */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("vse_launcher", Context.MODE_PRIVATE)

    fun loadSettings(): Settings {
        val s = prefs.getString("settings", null) ?: return Settings()
        return runCatching {
            val o = JSONObject(s)
            val d = Settings()
            val old = !o.has("v") // настройки первой версии — подправляем неудачные значения по умолчанию
            Settings(
                theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(d.theme),
                dynamicColor = o.optBoolean("dynamicColor", d.dynamicColor),
                accent = o.optLong("accent", d.accent),
                columns = o.optInt("columns", d.columns),
                iconSizeDp = o.optInt("iconSizeDp", d.iconSizeDp).let { if (old && it >= 52) d.iconSizeDp else it },
                showLabels = o.optBoolean("showLabels", d.showLabels),
                wallpaperDim = o.optDouble("wallpaperDim", d.wallpaperDim.toDouble()).toFloat(),
                use24h = o.optBoolean("use24h", d.use24h),
                showSeconds = o.optBoolean("showSeconds", d.showSeconds),
                showGreeting = o.optBoolean("showGreeting", d.showGreeting),
                showWeather = o.optBoolean("showWeather", d.showWeather),
                showTools = o.optBoolean("showTools", d.showTools),
                showDock = o.optBoolean("showDock", d.showDock),
                showBattery = o.optBoolean("showBattery", d.showBattery),
                weatherCity = o.optString("weatherCity", d.weatherCity).let { if (old && it == "Москва") d.weatherCity else it },
                weatherLat = if (old && o.optString("weatherCity") == "Москва") d.weatherLat else o.optDouble("weatherLat", d.weatherLat),
                weatherLon = if (old && o.optString("weatherCity") == "Москва") d.weatherLon else o.optDouble("weatherLon", d.weatherLon),
                swipeDownMode = o.optInt("swipeDownMode", d.swipeDownMode),
                showClock = o.optBoolean("showClock", d.showClock),
                clockSize = o.optDouble("clockSize", d.clockSize.toDouble()).toFloat(),
                clockWeight = o.optInt("clockWeight", d.clockWeight),
                clockFont = o.optInt("clockFont", d.clockFont),
                clockAlign = o.optInt("clockAlign", d.clockAlign),
                clockColor = o.optLong("clockColor", d.clockColor),
                showDate = o.optBoolean("showDate", d.showDate),
                clockScaleX = o.optDouble("clockScaleX", 1.0).toFloat(),
                clockScaleY = o.optDouble("clockScaleY", 1.0).toFloat(),
                clockAlpha = o.optDouble("clockAlpha", 1.0).toFloat(),
                clockStyle = o.optInt("clockStyle", 0),
                clockLines = o.optInt("clockLines", 0),
                iconShape = o.optInt("iconShape", d.iconShape),
                doubleTapLock = o.optBoolean("doubleTapLock", d.doubleTapLock),
                drawerSort = runCatching { DrawerSort.valueOf(o.optString("drawerSort")) }.getOrDefault(d.drawerSort),
                searchEngine = o.optInt("searchEngine", d.searchEngine),
                userName = o.optString("userName", d.userName),
            )
        }.getOrDefault(Settings())
    }

    fun saveSettings(s: Settings) {
        val o = JSONObject()
            .put("v", 2)
            .put("theme", s.theme.name)
            .put("dynamicColor", s.dynamicColor)
            .put("accent", s.accent)
            .put("columns", s.columns)
            .put("iconSizeDp", s.iconSizeDp)
            .put("showLabels", s.showLabels)
            .put("wallpaperDim", s.wallpaperDim.toDouble())
            .put("use24h", s.use24h)
            .put("showSeconds", s.showSeconds)
            .put("showGreeting", s.showGreeting)
            .put("showWeather", s.showWeather)
            .put("showTools", s.showTools)
            .put("showDock", s.showDock)
            .put("showBattery", s.showBattery)
            .put("weatherCity", s.weatherCity)
            .put("weatherLat", s.weatherLat)
            .put("weatherLon", s.weatherLon)
            .put("swipeDownMode", s.swipeDownMode)
            .put("showClock", s.showClock)
            .put("clockSize", s.clockSize.toDouble())
            .put("clockWeight", s.clockWeight)
            .put("clockFont", s.clockFont)
            .put("clockAlign", s.clockAlign)
            .put("clockColor", s.clockColor)
            .put("showDate", s.showDate)
            .put("clockScaleX", s.clockScaleX.toDouble())
            .put("clockScaleY", s.clockScaleY.toDouble())
            .put("clockAlpha", s.clockAlpha.toDouble())
            .put("clockStyle", s.clockStyle)
            .put("clockLines", s.clockLines)
            .put("iconShape", s.iconShape)
            .put("doubleTapLock", s.doubleTapLock)
            .put("drawerSort", s.drawerSort.name)
            .put("searchEngine", s.searchEngine)
            .put("userName", s.userName)
        prefs.edit().putString("settings", o.toString()).apply()
    }

    fun loadStringList(key: String): List<String> {
        val s = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) { a.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun saveStringList(key: String, list: Collection<String>) {
        prefs.edit().putString(key, JSONArray(list.toList()).toString()).apply()
    }

    fun loadStringMap(key: String): Map<String, String> {
        val s = prefs.getString(key, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun saveStringMap(key: String, map: Map<String, String>) {
        prefs.edit().putString(key, JSONObject(map as Map<*, *>).toString()).apply()
    }

    fun loadIntMap(key: String): Map<String, Int> {
        val s = prefs.getString(key, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
    }

    fun saveIntMap(key: String, map: Map<String, Int>) {
        prefs.edit().putString(key, JSONObject(map as Map<*, *>).toString()).apply()
    }

    fun loadNotes(): List<Note> {
        val s = prefs.getString("notes", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) {
                val o = a.getJSONObject(it)
                Note(o.getLong("id"), o.optString("title"), o.optString("text"), o.optLong("updated"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveNotes(notes: List<Note>) {
        val a = JSONArray()
        notes.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("text", it.text).put("updated", it.updated))
        }
        prefs.edit().putString("notes", a.toString()).apply()
    }

    fun loadTodos(): List<Todo> {
        val s = prefs.getString("todos", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) {
                val o = a.getJSONObject(it)
                Todo(o.getLong("id"), o.optString("text"), o.optBoolean("done"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveTodos(todos: List<Todo>) {
        val a = JSONArray()
        todos.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
        prefs.edit().putString("todos", a.toString()).apply()
    }

    fun loadDesktops(): List<Desktop> {
        val s = prefs.getString("desktops", null) ?: return listOf(Desktop("main", "Рабочий стол 1"))
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) {
                val o = a.getJSONObject(it)
                Desktop(o.getString("id"), o.optString("name"), o.optString("wallpaper", "system"), o.optBoolean("secret"))
            }
        }.getOrNull()?.ifEmpty { null } ?: listOf(Desktop("main", "Рабочий стол 1"))
    }

    fun saveDesktops(list: List<Desktop>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("wallpaper", it.wallpaper).put("secret", it.secret)) }
        prefs.edit().putString("desktops", a.toString()).apply()
    }

    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun loadPages(key: String = "pages"): List<List<String>>? {
        val s = prefs.getString(key, null) ?: return null
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) { i -> val p = a.getJSONArray(i); List(p.length()) { p.getString(it) } }
        }.getOrNull()
    }

    fun savePages(pages: List<List<String>>, key: String = "pages") {
        val a = JSONArray()
        pages.forEach { a.put(JSONArray(it)) }
        prefs.edit().putString(key, a.toString()).apply()
    }

    fun loadFolders(): Map<String, Folder> {
        val s = prefs.getString("folders", null) ?: return emptyMap()
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) {
                val o = a.getJSONObject(it)
                val apps = o.getJSONArray("apps")
                Folder(o.getString("id"), o.optString("name"), List(apps.length()) { i -> apps.getString(i) }, o.optBoolean("big"))
            }.associateBy { it.id }
        }.getOrDefault(emptyMap())
    }

    fun saveFolders(folders: Map<String, Folder>) {
        val a = JSONArray()
        folders.values.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("apps", JSONArray(it.apps)).put("big", it.big)) }
        prefs.edit().putString("folders", a.toString()).apply()
    }

    fun getString(key: String): String? = prefs.getString(key, null)
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}
