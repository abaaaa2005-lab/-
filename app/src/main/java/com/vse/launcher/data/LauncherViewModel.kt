package com.vse.launcher.data

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Process
import android.os.UserHandle
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vse.launcher.util.SystemActions
import com.vse.launcher.util.Weather
import com.vse.launcher.util.WeatherData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactResult(val id: Long, val lookupKey: String, val name: String, val phone: String?)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val storage = Storage(app)
    private val launcherApps = app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    var settings by mutableStateOf(storage.loadSettings())
        private set
    var allApps by mutableStateOf<List<AppInfo>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var favorites by mutableStateOf(storage.loadStringList("favorites"))
        private set
    var hidden by mutableStateOf(storage.loadStringList("hidden").toSet())
        private set
    var renamed by mutableStateOf(storage.loadStringMap("renamed"))
        private set
    var launchCounts by mutableStateOf(storage.loadIntMap("launchCounts"))
        private set
    var notes by mutableStateOf(storage.loadNotes())
        private set
    var todos by mutableStateOf(storage.loadTodos())
        private set
    var weather by mutableStateOf<WeatherData?>(null)
        private set
    var weatherLoading by mutableStateOf(false)
        private set
    var torchOn by mutableStateOf(false)
        private set

    private val iconCache: MutableMap<String, androidx.compose.ui.graphics.ImageBitmap?> =
        java.util.Collections.synchronizedMap(HashMap())

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reloadApps()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reloadApps()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) {
            synchronized(iconCache) { iconCache.keys.removeAll { it.startsWith("$packageName/") } }
            reloadApps()
        }
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reloadApps()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reloadApps()
    }

    init {
        launcherApps.registerCallback(callback)
        reloadApps()
        refreshWeather()
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(callback)
        super.onCleared()
    }

    // ---------- Приложения ----------

    fun reloadApps() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                val sizePx = (64 * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(96)
                val users = runCatching { launcherApps.profiles }.getOrDefault(listOf(Process.myUserHandle()))
                users.flatMap { user ->
                    runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).mapNotNull { info ->
                        val cn = info.componentName
                        if (cn.packageName == ctx.packageName) return@mapNotNull null
                        val userSuffix = if (user == Process.myUserHandle()) "" else "#${user.hashCode()}"
                        val key = "${cn.packageName}/${cn.className}$userSuffix"
                        val icon = iconCache.getOrPut(key) {
                            runCatching { info.getBadgedIcon(0).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
                        }
                        val original = info.label?.toString() ?: cn.packageName
                        val installTime = runCatching { pm.getPackageInfo(cn.packageName, 0).firstInstallTime }.getOrDefault(0L)
                        AppInfo(
                            key = key,
                            label = original,
                            originalLabel = original,
                            packageName = cn.packageName,
                            className = cn.className,
                            user = user,
                            icon = icon,
                            installTime = installTime,
                            isSystem = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        )
                    }
                }
            }
            allApps = list
            loading = false
        }
    }

    /** Приложения с учётом переименований. */
    private fun withNames(list: List<AppInfo>) = list.map { a -> renamed[a.key]?.let { a.copy(label = it) } ?: a }

    fun visibleApps(): List<AppInfo> {
        val list = withNames(allApps).filter { it.key !in hidden }
        return when (settings.drawerSort) {
            DrawerSort.ALPHA -> list.sortedBy { it.label.lowercase() }
            DrawerSort.USAGE -> list.sortedWith(compareByDescending<AppInfo> { launchCounts[it.key] ?: 0 }.thenBy { it.label.lowercase() })
            DrawerSort.RECENT -> list.sortedByDescending { it.installTime }
        }
    }

    fun hiddenApps(): List<AppInfo> = withNames(allApps).filter { it.key in hidden }.sortedBy { it.label.lowercase() }

    fun favoriteApps(): List<AppInfo> {
        val map = withNames(allApps).associateBy { it.key }
        return favorites.mapNotNull { map[it] }
    }

    fun topApps(n: Int): List<AppInfo> = withNames(allApps)
        .filter { it.key !in hidden && (launchCounts[it.key] ?: 0) > 0 }
        .sortedByDescending { launchCounts[it.key] ?: 0 }
        .take(n)

    fun searchApps(q: String): List<AppInfo> {
        val query = q.trim().lowercase()
        if (query.isEmpty()) return emptyList()
        return withNames(allApps).filter { it.key !in hidden }.mapNotNull { a ->
            val l = a.label.lowercase()
            val score = when {
                l == query -> 0
                l.startsWith(query) -> 1
                l.split(' ', '-', '_').any { it.startsWith(query) } -> 2
                l.contains(query) -> 3
                a.packageName.lowercase().contains(query) -> 4
                a.originalLabel.lowercase().contains(query) -> 4
                fuzzy(l, query) -> 5
                else -> null
            }
            score?.let { it to a }
        }.sortedWith(compareBy({ it.first }, { -(launchCounts[it.second.key] ?: 0) })).map { it.second }
    }

    private fun fuzzy(text: String, q: String): Boolean {
        var i = 0
        for (c in text) if (i < q.length && c == q[i]) i++
        return q.length >= 2 && i == q.length
    }

    fun launch(app: AppInfo, bounds: Rect? = null) {
        try {
            launcherApps.startMainActivity(ComponentName(app.packageName, app.className), app.user, bounds, null)
            val m = launchCounts.toMutableMap()
            m[app.key] = (m[app.key] ?: 0) + 1
            launchCounts = m
            storage.saveIntMap("launchCounts", m)
        } catch (e: Exception) {
            SystemActions.toast(ctx, "Не удалось открыть ${app.label}")
            reloadApps()
        }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching {
            launcherApps.startAppDetailsActivity(ComponentName(app.packageName, app.className), app.user, null, null)
        }
    }

    fun uninstall(app: AppInfo) = SystemActions.uninstall(ctx, app.packageName)

    fun toggleFavorite(app: AppInfo) {
        favorites = if (app.key in favorites) favorites - app.key else favorites + app.key
        storage.saveStringList("favorites", favorites)
    }

    fun moveFavorite(app: AppInfo, delta: Int) {
        val list = favorites.toMutableList()
        val i = list.indexOf(app.key)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        favorites = list
        storage.saveStringList("favorites", list)
    }

    fun setHidden(app: AppInfo, hide: Boolean) {
        hidden = if (hide) hidden + app.key else hidden - app.key
        if (hide && app.key in favorites) {
            favorites = favorites - app.key
            storage.saveStringList("favorites", favorites)
        }
        storage.saveStringList("hidden", hidden)
    }

    fun rename(app: AppInfo, newName: String) {
        val m = renamed.toMutableMap()
        if (newName.isBlank() || newName == app.originalLabel) m.remove(app.key) else m[app.key] = newName.trim()
        renamed = m
        storage.saveStringMap("renamed", m)
    }

    fun resetUsage() {
        launchCounts = emptyMap()
        storage.saveIntMap("launchCounts", emptyMap())
    }

    // ---------- Настройки ----------

    fun updateSettings(transform: (Settings) -> Settings) {
        val old = settings
        settings = transform(settings)
        storage.saveSettings(settings)
        if (old.weatherLat != settings.weatherLat || old.weatherLon != settings.weatherLon) refreshWeather()
    }

    // ---------- Погода ----------

    fun refreshWeather() {
        if (!settings.showWeather || weatherLoading) return
        weatherLoading = true
        viewModelScope.launch {
            Weather.fetch(settings.weatherLat, settings.weatherLon)?.let { weather = it }
            weatherLoading = false
        }
    }

    fun refreshWeatherIfStale() {
        val w = weather
        if (w == null || System.currentTimeMillis() - w.fetchedAt > 30 * 60 * 1000) refreshWeather()
    }

    // ---------- Заметки ----------

    fun saveNote(note: Note) {
        val list = notes.filter { it.id != note.id }
        notes = (listOf(note.copy(updated = System.currentTimeMillis())) + list)
        storage.saveNotes(notes)
    }

    fun deleteNote(id: Long) {
        notes = notes.filter { it.id != id }
        storage.saveNotes(notes)
    }

    // ---------- Задачи ----------

    fun addTodo(text: String) {
        if (text.isBlank()) return
        todos = listOf(Todo(System.currentTimeMillis(), text.trim(), false)) + todos
        storage.saveTodos(todos)
    }

    fun toggleTodo(id: Long) {
        todos = todos.map { if (it.id == id) it.copy(done = !it.done) else it }
            .sortedBy { it.done }
        storage.saveTodos(todos)
    }

    fun deleteTodo(id: Long) {
        todos = todos.filter { it.id != id }
        storage.saveTodos(todos)
    }

    fun clearDoneTodos() {
        todos = todos.filter { !it.done }
        storage.saveTodos(todos)
    }

    // ---------- Фонарик ----------

    fun toggleTorch() {
        val r = SystemActions.setTorch(ctx, !torchOn)
        if (r == null) SystemActions.toast(ctx, "Фонарик недоступен") else torchOn = r
    }

    // ---------- Контакты ----------

    suspend fun searchContacts(q: String): List<ContactResult> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        runCatching {
            val res = mutableListOf<ContactResult>()
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                arrayOf("%$q%", "%$q%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { c ->
                val seen = HashSet<Long>()
                while (c.moveToNext() && res.size < 6) {
                    val id = c.getLong(0)
                    if (seen.add(id)) res += ContactResult(id, c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3))
                }
            }
            res
        }.getOrDefault(emptyList())
    }
}
