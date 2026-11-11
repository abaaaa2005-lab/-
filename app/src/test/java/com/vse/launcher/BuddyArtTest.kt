package com.vse.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vse.launcher.buddy.BuddyCharacter
import com.vse.launcher.buddy.BuddyLook
import com.vse.launcher.buddy.Emotion
import com.vse.launcher.buddy.Wardrobe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Рисует помощника во всех эмоциях и в разных нарядах в build/screenshots — проверка рисовки без телефона. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1400dp-mdpi")
class BuddyArtTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun save(name: String) {
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun emotionsAndOutfits() {
        val base = BuddyLook()
        val outfits = listOf(
            base.copy(hat = 3, hatColor = 0xFFFF5E5E, headphones = 1),
            base.copy(hat = 5, glasses = 3, accessory = 6),
            base.copy(hat = 6, hatColor = 0xFF7B5CE0, headphones = 4, phonesColor = 0xFF34C759),
            base.copy(hat = 17, hatColor = 0xFFFFFFFF, headphones = 3, phonesColor = 0xFFFF6FB5),
            base.copy(hat = 12, accessory = 11, accColor = 0xFFFF5E5E),
            base.copy(hat = 13, glasses = 9, accessory = 1),
            base.copy(bodyColor = 0xFF6C8CFF, eyes = 1, belly = true, blush = true, hat = 11, headphones = 2),
            base.copy(bodyColor = 0xFFFFD93D, eyes = 2, shape = 4, hat = 0, headphones = 8, aura = 4),
            base.copy(bodyColor = 0xFF34C759, shape = 5, eyes = 6, hat = 21, glasses = 1, face = 1),
            base.copy(bodyColor = 0xFFFF6FB5, shape = 3, hat = 14, aura = 2),
            base.copy(bodyColor = 0xFF2EC4B6, shape = 6, hat = 29, face = 6),
            base.copy(bodyColor = Wardrobe.RAINBOW, shape = 7, hat = 27, glasses = 6),
        )
        rule.setContent {
            Column(Modifier.testTag("sheet").background(Color.Black).padding(8.dp)) {
                Emotion.entries.chunked(7).forEach { row ->
                    Row {
                        row.forEach { e ->
                            Column(Modifier.width(110.dp)) {
                                BuddyCharacter(base, e, talking = false, modifier = Modifier.size(110.dp), animate = false)
                                Text(e.title, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
                outfits.chunked(6).forEach { row ->
                    Row { row.forEach { BuddyCharacter(it, Emotion.HAPPY, false, Modifier.size(128.dp), animate = false) } }
                }
                Row { BuddyCharacter(base, Emotion.NEUTRAL, true, Modifier.size(300.dp), animate = false) }
            }
        }
        rule.waitForIdle()
        save("buddy_sheet")
    }
}
