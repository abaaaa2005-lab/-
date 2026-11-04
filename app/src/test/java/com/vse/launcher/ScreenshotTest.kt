package com.vse.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Process
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ActivityScenario
import com.vse.launcher.data.AppInfo
import com.vse.launcher.data.LauncherViewModel
import androidx.lifecycle.ViewModelProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

/** Рендерит экраны лаунчера в PNG (build/screenshots) — чтобы посмотреть на вёрстку без телефона. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    private fun fakeIcon(color: Int, letter: String): Bitmap {
        val b = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.drawPath(com.vse.launcher.util.IconRenderer.shapePath(160f, 0), p)
        p.color = android.graphics.Color.WHITE
        p.textSize = 80f
        p.textAlign = Paint.Align.CENTER
        c.drawText(letter, 80f, 108f, p)
        return b
    }

    private fun fakeApps(): List<AppInfo> {
        val list = listOf(
            "Телефон" to "com.android.dialer", "Сообщения" to "com.android.mms", "Chrome" to "com.android.chrome",
            "Камера" to "com.android.camera", "Фото" to "com.google.android.apps.photos", "Карты" to "com.google.android.apps.maps",
            "YouTube" to "com.google.android.youtube", "Telegram" to "org.telegram.messenger", "WhatsApp" to "com.whatsapp",
            "Почта" to "com.google.android.gm", "Календарь" to "com.android.calendar", "Часы" to "com.android.deskclock",
            "Kaspi.kz" to "kz.kaspi.mobile", "Halyk" to "kz.halykbank.homebank", "Настройки" to "com.android.settings",
            "inDrive" to "sinet.startup.inDriver", "2ГИС" to "ru.dublgis.dgismobile", "Spotify" to "com.spotify.music",
            "VK" to "com.vkontakte.android", "Instagram" to "com.instagram.android", "Wildberries" to "com.wildberries.ru",
            "Калькулятор" to "com.android.calculator2", "Диктофон" to "com.android.soundrecorder", "Krisha.kz" to "kz.krisha",
            "Kolesa.kz" to "kz.kolesa", "Заметки" to "com.notes.app", "Игра" to "com.game.one",
        )
        val colors = listOf(0xFF34C759, 0xFF30D158, 0xFF4285F4, 0xFF636366, 0xFFFF9500, 0xFF0A84FF, 0xFFFF0000, 0xFF2AABEE,
            0xFF25D366, 0xFF007AFF, 0xFFFF3B30, 0xFF1C1C1E, 0xFFF14635, 0xFF00805F, 0xFF8E8E93, 0xFFA4E000)
        return list.mapIndexed { i, (n, pkg) ->
            AppInfo(
                key = "$pkg/cls", label = n, originalLabel = n, packageName = pkg, className = "cls",
                user = Process.myUserHandle(),
                icon = fakeIcon(colors[i % colors.size].toInt(), n.take(1)).asImageBitmap(),
                installTime = i.toLong(), isSystem = pkg.startsWith("com.android") || pkg.startsWith("com.google"),
            )
        }
    }

    private fun shoot(name: String, setup: (MainActivity, LauncherViewModel) -> Unit = { _, _ -> }) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { act ->
            val vm = ViewModelProvider(act)[LauncherViewModel::class.java]
            vm.setAppsForTest(fakeApps())
            setup(act, vm)
        }
        repeat(20) { ShadowLooper.idleMainLooper(); Thread.sleep(20) }
        scenario.onActivity { act ->
            val root = act.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0xFF3B5B8C.toInt()) // «обои»
            root.draw(c)
            val dir = File("build/screenshots").apply { mkdirs() }
            FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        scenario.close()
    }

    @Test fun home() = shoot("home")
    @Test fun editMode() = shoot("edit") { _, vm -> vm.editMode = true }
}
