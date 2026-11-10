package com.vse.launcher

import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.vse.launcher.data.AppInfo
import com.vse.launcher.data.FOLDER_PREFIX
import com.vse.launcher.data.LauncherViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Проверка переноса сразу нескольких иконок. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupMoveTest {
    private lateinit var vm: LauncherViewModel

    private fun app(k: String) = AppInfo("$k/c", k, k, "user.$k", "c", Process.myUserHandle(), null, 0, false)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        ctx.getSharedPreferences("vse_launcher", 0).edit().clear().commit()
        ctx.getSharedPreferences("vse_launcher", 0).edit().putString("autoFolders1", "1").commit()
        vm = LauncherViewModel(ctx)
        vm.setAppsForTest(listOf("a", "b", "c", "d", "e", "f").map { app(it) })
        vm.setGrid(4, 4, 4)
    }

    private fun page0() = vm.pages[0].map { it.substringBefore("/") }

    @Test fun groupLandsRightAfterPrimary() {
        // Тащим «e» вместе с «a» и «c» и ставим «e» на место «b»
        vm.moveItem("e/c", LauncherViewModel.Target.Page(0, 1))
        vm.moveGroupAfter("e/c", listOf("a/c", "c/c"))
        assertEquals(listOf("b", "e", "a", "c", "d", "f").let { listOf("e", "a", "c") }, page0().filter { it in setOf("e", "a", "c") })
        val p = page0()
        assertEquals(p.indexOf("e") + 1, p.indexOf("a"))
        assertEquals(p.indexOf("e") + 2, p.indexOf("c"))
    }

    @Test fun groupDroppedOnIconMakesOneFolder() {
        vm.mergeGroup(listOf("a/c", "b/c", "c/c"), "d/c")
        val folderId = vm.pages[0].single { it.startsWith(FOLDER_PREFIX) }
        assertEquals(setOf("a/c", "b/c", "c/c", "d/c"), vm.folders[folderId]!!.apps.toSet())
        assertTrue(vm.pages[0].none { it in setOf("a/c", "b/c", "c/c", "d/c") })
    }
}
