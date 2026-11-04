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
            Settings(
                theme = runCatching { ThemeMode.valueOf(o.optString("theme")) }.getOrDefault(d.theme),
                dynamicColor = o.optBoolean("dynamicColor", d.dynamicColor),
                accent = o.optLong("accent", d.accent),
                columns = o.optInt("columns", d.columns),
                iconSizeDp = o.optInt("iconSizeDp", d.iconSizeDp),
                showLabels = o.optBoolean("showLabels", d.showLabels),
                wallpaperDim = o.optDouble("wallpaperDim", d.wallpaperDim.toDouble()).toFloat(),
                use24h = o.optBoolean("use24h", d.use24h),
                showSeconds = o.optBoolean("showSeconds", d.showSeconds),
                showGreeting = o.optBoolean("showGreeting", d.showGreeting),
                showWeather = o.optBoolean("showWeather", d.showWeather),
                showTools = o.optBoolean("showTools", d.showTools),
                showDock = o.optBoolean("showDock", d.showDock),
                showBattery = o.optBoolean("showBattery", d.showBattery),
                weatherCity = o.optString("weatherCity", d.weatherCity),
                weatherLat = o.optDouble("weatherLat", d.weatherLat),
                weatherLon = o.optDouble("weatherLon", d.weatherLon),
                swipeDownMode = o.optInt("swipeDownMode", d.swipeDownMode),
                showClock = o.optBoolean("showClock", d.showClock),
                clockSize = o.optDouble("clockSize", d.clockSize.toDouble()).toFloat(),
                clockWeight = o.optInt("clockWeight", d.clockWeight),
                clockFont = o.optInt("clockFont", d.clockFont),
                clockAlign = o.optInt("clockAlign", d.clockAlign),
                clockColor = o.optLong("clockColor", d.clockColor),
                showDate = o.optBoolean("showDate", d.showDate),
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

    fun loadPages(): List<List<String>>? {
        val s = prefs.getString("pages", null) ?: return null
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) { i -> val p = a.getJSONArray(i); List(p.length()) { p.getString(it) } }
        }.getOrNull()
    }

    fun savePages(pages: List<List<String>>) {
        val a = JSONArray()
        pages.forEach { a.put(JSONArray(it)) }
        prefs.edit().putString("pages", a.toString()).apply()
    }

    fun loadFolders(): Map<String, Folder> {
        val s = prefs.getString("folders", null) ?: return emptyMap()
        return runCatching {
            val a = JSONArray(s)
            List(a.length()) {
                val o = a.getJSONObject(it)
                val apps = o.getJSONArray("apps")
                Folder(o.getString("id"), o.optString("name"), List(apps.length()) { i -> apps.getString(i) })
            }.associateBy { it.id }
        }.getOrDefault(emptyMap())
    }

    fun saveFolders(folders: Map<String, Folder>) {
        val a = JSONArray()
        folders.values.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("apps", JSONArray(it.apps))) }
        prefs.edit().putString("folders", a.toString()).apply()
    }

    fun getString(key: String): String? = prefs.getString(key, null)
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}
