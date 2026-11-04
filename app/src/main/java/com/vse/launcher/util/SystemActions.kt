package com.vse.launcher.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import com.vse.launcher.LockAccessibilityService

/** Системные ярлыки, которые можно найти через поиск. */
data class SystemShortcut(val title: String, val keywords: String, val action: String)

val SYSTEM_SHORTCUTS = listOf(
    SystemShortcut("Wi‑Fi", "wifi вайфай интернет сеть", Settings.ACTION_WIFI_SETTINGS),
    SystemShortcut("Bluetooth", "bluetooth блютуз", Settings.ACTION_BLUETOOTH_SETTINGS),
    SystemShortcut("Мобильная сеть", "мобильный интернет sim сим", Settings.ACTION_DATA_ROAMING_SETTINGS),
    SystemShortcut("Режим полёта", "самолет полет airplane", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    SystemShortcut("Экран", "экран яркость дисплей display", Settings.ACTION_DISPLAY_SETTINGS),
    SystemShortcut("Звук", "звук громкость рингтон sound", Settings.ACTION_SOUND_SETTINGS),
    SystemShortcut("Батарея", "батарея аккумулятор зарядка battery", Intent.ACTION_POWER_USAGE_SUMMARY),
    SystemShortcut("Память", "память хранилище storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SystemShortcut("Приложения", "приложения apps", Settings.ACTION_APPLICATION_SETTINGS),
    SystemShortcut("Местоположение", "gps геолокация местоположение", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SystemShortcut("Безопасность", "безопасность пароль security", Settings.ACTION_SECURITY_SETTINGS),
    SystemShortcut("Дата и время", "дата время date", Settings.ACTION_DATE_SETTINGS),
    SystemShortcut("Язык и ввод", "язык клавиатура language", Settings.ACTION_LOCALE_SETTINGS),
    SystemShortcut("Спец. возможности", "доступность accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SystemShortcut("NFC", "nfc оплата", Settings.ACTION_NFC_SETTINGS),
    SystemShortcut("Точка доступа", "hotspot точка модем", "android.settings.TETHER_SETTINGS"),
    SystemShortcut("Уведомления", "уведомления notifications", "android.settings.ALL_APPS_NOTIFICATION_SETTINGS"),
    SystemShortcut("Об устройстве", "о телефоне about версия", Settings.ACTION_DEVICE_INFO_SETTINGS),
    SystemShortcut("Настройки", "настройки settings", Settings.ACTION_SETTINGS),
)

object SystemActions {

    fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

    fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        toast(ctx, "Нет приложения для этого действия")
        false
    } catch (e: SecurityException) {
        toast(ctx, "Нет доступа")
        false
    }

    fun openSettings(ctx: Context, action: String) {
        if (!start(ctx, Intent(action))) start(ctx, Intent(Settings.ACTION_SETTINGS))
    }

    @SuppressLint("WrongConstant")
    fun expandNotifications(ctx: Context) {
        try {
            val sb = ctx.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Throwable) {
        }
    }

    @SuppressLint("WrongConstant")
    fun expandQuickSettings(ctx: Context) {
        try {
            val sb = ctx.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(sb)
        } catch (_: Throwable) {
        }
    }

    fun lockScreen(ctx: Context) {
        if (!LockAccessibilityService.lock()) {
            toast(ctx, "Включите службу «Всё Лаунчер: блокировка экрана»")
            start(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    fun webSearch(ctx: Context, baseUrl: String, query: String) =
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + Uri.encode(query))))

    fun openUrl(ctx: Context, url: String) = start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun maps(ctx: Context, query: String) =
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))

    fun playStore(ctx: Context, query: String) {
        if (!start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query)))))
            openUrl(ctx, "https://play.google.com/store/search?q=" + Uri.encode(query))
    }

    fun dial(ctx: Context, number: String = "") =
        start(ctx, Intent(Intent.ACTION_DIAL, if (number.isEmpty()) null else Uri.parse("tel:" + Uri.encode(number))))

    fun messages(ctx: Context) =
        start(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))

    fun camera(ctx: Context) = start(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    fun browser(ctx: Context) =
        start(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER))

    fun gallery(ctx: Context) =
        start(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))

    fun music(ctx: Context) =
        start(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))

    fun email(ctx: Context) =
        start(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL))

    fun contacts(ctx: Context) =
        start(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS))

    fun alarms(ctx: Context) = start(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun calendar(ctx: Context) =
        start(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))

    fun setDefaultLauncher(ctx: Context) {
        if (!start(ctx, Intent(Settings.ACTION_HOME_SETTINGS))) start(ctx, Intent(Settings.ACTION_SETTINGS))
    }

    fun pickWallpaper(ctx: Context) =
        start(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Выберите обои"))

    fun uninstall(ctx: Context, pkg: String) =
        start(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

    fun share(ctx: Context, text: String) =
        start(ctx, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))

    fun component(pkg: String, cls: String) = ComponentName(pkg, cls)

    /** Фонарик. Возвращает новое состояние или null, если вспышки нет. */
    fun setTorch(ctx: Context, on: Boolean): Boolean? {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return null
            cm.setTorchMode(id, on)
            on
        } catch (_: Throwable) {
            null
        }
    }
}
