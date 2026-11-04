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
import com.vse.launcher.util.IconRenderer
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
    var weatherError by mutableStateOf<String?>(null)
        private set
    var secretPin by mutableStateOf(storage.getString("secretPin")?.takeIf { it.isNotEmpty() })
        private set
    var torchOn by mutableStateOf(false)
        private set

    // ---------- Рабочий стол (экраны, док, папки) ----------
    var pages by mutableStateOf<List<List<String>>>(storage.loadPages() ?: emptyList())
        private set
    var dock by mutableStateOf(storage.loadStringList("dock"))
        private set
    var folders by mutableStateOf(storage.loadFolders())
        private set
    var editMode by mutableStateOf(false)
    var homeSignal by mutableStateOf(0)
    private var homeInitialized = storage.loadPages() != null
    private var known = storage.loadStringList("known").toSet()
    private var capFirst = 0
    private var capOther = 0

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
                        val shape = settings.iconShape
                        val icon = iconCache.getOrPut("$key@$shape") {
                            runCatching {
                                var bmp = IconRenderer.render(info.getIcon(0), sizePx, shape)
                                if (user != Process.myUserHandle()) {
                                    bmp = pm.getUserBadgedIcon(android.graphics.drawable.BitmapDrawable(ctx.resources, bmp), user)
                                        .toBitmap(sizePx, sizePx)
                                }
                                bmp.asImageBitmap()
                            }.getOrNull()
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
                            category = info.applicationInfo.category,
                        )
                    }
                }
            }
            allApps = list
            loading = false
            syncHomeWithApps()
        }
    }

    /** Только для тестов: подставить список приложений. */
    internal fun setAppsForTest(list: List<AppInfo>) {
        allApps = list
        loading = false
        syncHomeWithApps()
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
        if (hide) removeFromHome(app.key) else addToHome(app.key)
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

    // ---------- Рабочий стол ----------

    fun appMap(): Map<String, AppInfo> = withNames(allApps).associateBy { it.key }

    private fun savePages() {
        storage.savePages(pages)
        storage.saveStringList("dock", dock)
        storage.saveFolders(folders)
    }

    /** Первый запуск: раскладываем приложения как на iPhone. Потом — добавляем новые и убираем удалённые. */
    private fun syncHomeWithApps() {
        if (allApps.isEmpty()) return
        val keys = allApps.map { it.key }.toSet()
        if (!homeInitialized) {
            val pm = ctx.packageManager
            fun pkgFor(i: android.content.Intent): String? = runCatching {
                pm.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull()?.takeIf { it != "android" }
            val dockPkgs = listOfNotNull(
                pkgFor(android.content.Intent(android.content.Intent.ACTION_DIAL)),
                pkgFor(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:"))),
                pkgFor(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))),
                pkgFor(android.content.Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            ).distinct()
            val main = allApps.filter { it.user == Process.myUserHandle() }
            dock = dockPkgs.mapNotNull { p -> main.firstOrNull { it.packageName == p }?.key }.take(DOCK_MAX)
            val rest = visibleApps().map { it.key }.filter { it !in dock }
            val favFirst = favorites.filter { it in rest }
            pages = listOf(favFirst + rest.filter { it !in favFirst })
            homeInitialized = true
            applyAutoFolders()
            storage.putString("autoFolders1", "1")
        } else {
            // Новые приложения — в конец последнего экрана, как на iPhone
            val newOnes = allApps.map { it.key }.filter { it !in known && it !in hidden && !isOnHome(it) }
            if (newOnes.isNotEmpty() && known.isNotEmpty()) {
                pages = if (pages.isEmpty()) listOf(newOnes) else pages.dropLast(1) + listOf(pages.last() + newOnes)
            }
            // Удалённые приложения убираем отовсюду
            pages = pages.map { p -> p.filter { it.startsWith(FOLDER_PREFIX) || it in keys } }
            dock = dock.filter { it.startsWith(FOLDER_PREFIX) || it in keys }
            folders = folders.mapValues { (_, f) -> f.copy(apps = f.apps.filter { it in keys }) }
            dissolveSmallFolders()
        }
        if (pages.isEmpty()) pages = listOf(emptyList())
        // Обновление с первой версии: один раз раскладываем приложения по папкам
        if (storage.getString("autoFolders1") == null) {
            applyAutoFolders()
            storage.putString("autoFolders1", "1")
        }
        known = keys
        storage.saveStringList("known", known)
        normalize()
        savePages()
    }

    fun isOnHome(key: String) = pages.any { key in it } || key in dock || folders.values.any { key in it.apps }

    fun setCapacity(first: Int, other: Int) {
        if (first == capFirst && other == capOther) return
        capFirst = first
        capOther = other
        normalize()
        savePages()
    }

    /** Если экран переполнен — лишние иконки переезжают на следующий. */
    private fun normalize() {
        if (capFirst <= 0 || capOther <= 0) return
        val res = mutableListOf<List<String>>()
        var carry = emptyList<String>()
        pages.forEachIndexed { i, p ->
            val cap = if (i == 0) capFirst else capOther
            val all = carry + p
            res += all.take(cap)
            carry = all.drop(cap)
        }
        while (carry.isNotEmpty()) {
            res += carry.take(capOther)
            carry = carry.drop(capOther)
        }
        if (res.isEmpty()) res += emptyList<String>()
        if (res != pages) pages = res
    }

    private fun removeEverywhere(id: String) {
        pages = pages.map { p -> p.filter { it != id } }
        dock = dock.filter { it != id }
    }

    sealed interface Target {
        data class Page(val page: Int, val index: Int) : Target
        data class Dock(val index: Int) : Target
    }

    fun locate(id: String): Target? {
        dock.indexOf(id).takeIf { it >= 0 }?.let { return Target.Dock(it) }
        pages.forEachIndexed { pi, p -> p.indexOf(id).takeIf { it >= 0 }?.let { return Target.Page(pi, it) } }
        return null
    }

    /** Переместить иконку (живое перемещение при перетаскивании). */
    fun moveItem(id: String, target: Target): Boolean {
        val from = locate(id)
        if (from == target) return true
        if (target is Target.Dock && from !is Target.Dock && dock.size >= DOCK_MAX) return false
        removeEverywhere(id)
        when (target) {
            is Target.Dock -> dock = dock.toMutableList().apply { add(target.index.coerceIn(0, size), id) }
            is Target.Page -> {
                val list = pages.toMutableList()
                while (list.size <= target.page) list += emptyList<String>()
                list[target.page] = list[target.page].toMutableList().apply { add(target.index.coerceIn(0, size), id) }
                pages = list
            }
        }
        normalize()
        savePages()
        return true
    }

    /** Бросили иконку на другую — создаём папку или кладём в существующую. */
    fun mergeInto(dragId: String, targetId: String) {
        if (dragId == targetId || dragId.startsWith(FOLDER_PREFIX)) return
        if (targetId.startsWith(FOLDER_PREFIX)) {
            val f = folders[targetId] ?: return
            removeEverywhere(dragId)
            folders = folders + (targetId to f.copy(apps = f.apps + dragId))
        } else {
            val pos = locate(targetId) ?: return
            removeEverywhere(dragId)
            val id = FOLDER_PREFIX + System.currentTimeMillis()
            val map = appMap()
            val name = folderName(listOfNotNull(map[targetId], map[dragId]))
            folders = folders + (id to Folder(id, name, listOf(targetId, dragId)))
            when (pos) {
                is Target.Dock -> dock = dock.map { if (it == targetId) id else it }
                is Target.Page -> pages = pages.map { p -> p.map { if (it == targetId) id else it } }
            }
        }
        normalize()
        savePages()
    }

    private fun folderName(apps: List<AppInfo>): String {
        val cat = apps.map { it.category }.distinct().singleOrNull()
        return when (cat) {
            ApplicationInfo.CATEGORY_GAME -> "Игры"
            ApplicationInfo.CATEGORY_SOCIAL -> "Общение"
            ApplicationInfo.CATEGORY_AUDIO -> "Музыка"
            ApplicationInfo.CATEGORY_VIDEO -> "Видео"
            ApplicationInfo.CATEGORY_IMAGE -> "Фото"
            ApplicationInfo.CATEGORY_NEWS -> "Новости"
            ApplicationInfo.CATEGORY_MAPS -> "Навигация"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Работа"
            else -> if (apps.all { it.isSystem }) "Утилиты" else "Папка"
        }
    }

    /** Категория приложения для автоматических папок. */
    private fun bucket(a: AppInfo): String? {
        val p = a.packageName.lowercase()
        fun has(vararg k: String) = k.any { p.contains(it) }
        return when {
            has("telegram", "whatsapp", "viber", "vkontakte", "com.vk.", "instagram", "facebook", "messenger", "discord",
                "snapchat", "tiktok", "musically", "twitter", "threads", "skype", "zoom", "signal", "wechat", "odnoklassniki", "ok.android") -> "Общение"
            has("kaspi", "halyk", "bank", "sber", "tinkoff", "jusan", "freedom", "forte", "bcc", "homecredit", "paypal", "wallet") -> "Финансы"
            has("taxi", "uber", "indriver", "indrive", "yandex.go", "bolt", "2gis", "dublgis", "navigator", "maps", "waze", "glovo", "wolt", "choco") -> "Поездки и еда"
            has("wildberries", "ozon", "aliexpress", "alibaba", "olx", "krisha", "kolesa", "lamoda", "temu", "shein", "avito") -> "Покупки"
            has("music", "spotify", "soundcloud", "deezer", "shazam", "podcast") -> "Музыка"
            has("youtube", "netflix", "kinopoisk", "okko", "twitch", "video") -> "Видео"
            else -> when (a.category) {
                ApplicationInfo.CATEGORY_GAME -> "Игры"
                ApplicationInfo.CATEGORY_SOCIAL -> "Общение"
                ApplicationInfo.CATEGORY_AUDIO -> "Музыка"
                ApplicationInfo.CATEGORY_VIDEO -> "Видео"
                ApplicationInfo.CATEGORY_IMAGE -> "Фото"
                ApplicationInfo.CATEGORY_NEWS -> "Новости"
                ApplicationInfo.CATEGORY_MAPS -> "Поездки и еда"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Работа"
                else -> when {
                    p.startsWith("com.google") -> "Google"
                    a.isSystem -> "Утилиты"
                    else -> null
                }
            }
        }
    }

    /** Разложить приложения с экранов по папкам (как на iPhone). */
    fun autoFolders() {
        applyAutoFolders()
        normalize()
        savePages()
    }

    private fun applyAutoFolders() {
        val map = appMap()
        val items = pages.flatten()
        val loose = items.filter { !it.startsWith(FOLDER_PREFIX) }
        val groups = loose.mapNotNull { k -> map[k]?.let { a -> bucket(a)?.let { it to k } } }
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size >= 2 }
        if (groups.isEmpty()) return
        val newFolders = folders.toMutableMap()
        val created = mutableListOf<String>()
        groups.toSortedMap().forEach { (name, keys) ->
            val existing = newFolders.values.firstOrNull { it.name == name }
            if (existing != null) {
                newFolders[existing.id] = existing.copy(apps = existing.apps + keys)
            } else {
                val id = FOLDER_PREFIX + System.nanoTime()
                newFolders[id] = Folder(id, name, keys)
                created += id
            }
        }
        val grouped = groups.values.flatten().toSet()
        folders = newFolders
        val folderItems = items.filter { it.startsWith(FOLDER_PREFIX) }
        pages = listOf(folderItems + created + loose.filter { it !in grouped })
    }

    fun renameFolder(id: String, name: String) {
        val f = folders[id] ?: return
        folders = folders + (id to f.copy(name = name))
        savePages()
    }

    fun removeFromFolder(folderId: String, key: String) {
        val f = folders[folderId] ?: return
        folders = folders + (folderId to f.copy(apps = f.apps - key))
        // Кладём приложение сразу после папки
        when (val pos = locate(folderId)) {
            is Target.Page -> moveItem(key, Target.Page(pos.page, pos.index + 1))
            else -> addToHome(key)
        }
        dissolveSmallFolders()
        normalize()
        savePages()
    }

    private fun dissolveSmallFolders() {
        val small = folders.values.filter { it.apps.size <= 1 }
        if (small.isEmpty()) return
        small.forEach { f ->
            val repl = f.apps.firstOrNull()
            pages = pages.map { p -> p.mapNotNull { if (it == f.id) repl else it } }
            dock = dock.mapNotNull { if (it == f.id) repl else it }
        }
        folders = folders - small.map { it.id }.toSet()
    }

    /** Убрать с экрана «Домой» (приложение останется в библиотеке). */
    fun removeFromHome(id: String) {
        if (id.startsWith(FOLDER_PREFIX)) folders = folders - id
        removeEverywhere(id)
        folders = folders.mapValues { (_, f) -> f.copy(apps = f.apps - id) }
        dissolveSmallFolders()
        savePages()
    }

    fun addToHome(key: String) {
        if (isOnHome(key)) return
        val idx = pages.indices.firstOrNull { i -> pages[i].size < (if (i == 0) capFirst else capOther).coerceAtLeast(1) }
        pages = if (idx != null) pages.mapIndexed { i, p -> if (i == idx) p + key else p } else pages + listOf(listOf(key))
        normalize()
        savePages()
    }

    fun addPage() {
        pages = pages + listOf(emptyList())
        savePages()
    }

    fun removePage(i: Int) {
        if (pages.size <= 1 || pages.getOrNull(i)?.isNotEmpty() == true) return
        pages = pages.filterIndexed { idx, _ -> idx != i }
        savePages()
    }

    fun exitEditMode() {
        editMode = false
        // Пустые экраны исчезают, как на iPhone
        val cleaned = pages.filter { it.isNotEmpty() }.ifEmpty { listOf(emptyList()) }
        if (cleaned != pages) { pages = cleaned; savePages() }
    }

    // ---------- Секретная папка ----------

    fun saveSecretPin(pin: String?) {
        secretPin = pin
        if (pin == null) storage.putString("secretPin", "") else storage.putString("secretPin", pin)
    }

    // ---------- Местоположение для погоды ----------

    @android.annotation.SuppressLint("MissingPermission")
    suspend fun detectCity(): com.vse.launcher.util.City? = withContext(Dispatchers.IO) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val providers = listOf(
            android.location.LocationManager.NETWORK_PROVIDER,
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.PASSIVE_PROVIDER,
        )
        var loc = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (loc == null) {
            val provider = providers.firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            if (provider != null) {
                loc = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            lm.getCurrentLocation(provider, null, ctx.mainExecutor) { l -> if (cont.isActive) cont.resumeWith(Result.success(l)) }
                        } else {
                            @Suppress("DEPRECATION")
                            lm.requestSingleUpdate(provider, { l -> if (cont.isActive) cont.resumeWith(Result.success(l)) }, android.os.Looper.getMainLooper())
                        }
                    }
                }
            }
        }
        val l = loc ?: return@withContext null
        val name = runCatching {
            @Suppress("DEPRECATION")
            android.location.Geocoder(ctx, java.util.Locale("ru")).getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()?.locality
        }.getOrNull() ?: Weather.KZ_CITIES.minByOrNull {
            val r = FloatArray(1)
            android.location.Location.distanceBetween(l.latitude, l.longitude, it.lat, it.lon, r)
            r[0]
        }?.takeIf {
            val r = FloatArray(1)
            android.location.Location.distanceBetween(l.latitude, l.longitude, it.lat, it.lon, r)
            r[0] < 60_000
        }?.name ?: "Моё местоположение"
        com.vse.launcher.util.City(name, l.latitude, l.longitude, "")
    }

    // ---------- Настройки ----------

    fun updateSettings(transform: (Settings) -> Settings) {
        val old = settings
        settings = transform(settings)
        storage.saveSettings(settings)
        if (old.weatherLat != settings.weatherLat || old.weatherLon != settings.weatherLon) refreshWeather()
        if (old.iconShape != settings.iconShape) reloadApps()
    }

    // ---------- Погода ----------

    fun refreshWeather() {
        if (!settings.showWeather || weatherLoading) return
        weatherLoading = true
        viewModelScope.launch {
            val w = Weather.fetch(settings.weatherLat, settings.weatherLon)
            if (w != null) { weather = w; weatherError = null }
            else weatherError = "Не удалось загрузить погоду. Проверьте интернет и нажмите «Обновить»."
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
