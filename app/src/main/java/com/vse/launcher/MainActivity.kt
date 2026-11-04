package com.vse.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.vse.launcher.data.LauncherViewModel
import com.vse.launcher.ui.*
import com.vse.launcher.ui.tools.ToolScreen

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private val stack = mutableStateOf<List<Screen>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val top = stack.value.lastOrNull()
            LauncherTheme(vm.settings) {
                val dark = isDark(vm.settings)
                // На рабочем столе — светлые значки статус-бара поверх обоев
                val lightBars = top != null && !dark
                LaunchedEffect(lightBars, dark) {
                    val style = if (lightBars) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    else SystemBarStyle.dark(Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                LauncherRoot(vm, top, push = { stack.value = stack.value + it }, pop = { pop() }, home = { stack.value = emptyList() })
            }
        }
    }

    private fun pop() {
        stack.value = stack.value.dropLast(1)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Нажатие «Домой» — возвращаемся на главный экран
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            stack.value = emptyList()
            if (vm.editMode) vm.exitEditMode()
            vm.homeSignal++
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshWeatherIfStale()
    }

    override fun onStop() {
        super.onStop()
        // Вернулись из приложения — ящик приложений закрыт
        if (stack.value.lastOrNull() is Screen.Drawer) stack.value = emptyList()
        if (vm.editMode) vm.exitEditMode()
    }
}

@Composable
fun LauncherRoot(
    vm: LauncherViewModel,
    top: Screen?,
    push: (Screen) -> Unit,
    pop: () -> Unit,
    home: () -> Unit,
) {
    BackHandler(enabled = top != null) { pop() }
    Box(Modifier.fillMaxSize()) {
        // Свой рабочий стол — свой экран (переключение как в Windows)
        key(vm.activeDesktop) { HomeScreen(vm, open = push) }
        AnimatedContent(
            targetState = top,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 10 }) togetherWith (fadeOut() + slideOutVertically { it / 10 })
            },
            contentKey = { it?.let { s -> s::class.simpleName + (s as? Screen.Tool)?.id } ?: "home" },
            label = "screens",
        ) { screen ->
            when (screen) {
                null -> Box(Modifier)
                is Screen.Drawer -> AppDrawer(vm, startSearch = screen.search, onClose = pop, open = push)
                Screen.Tools -> ToolsHub(vm, onBack = pop, open = push)
                is Screen.Tool -> ToolScreen(screen.id, vm, onBack = pop)
                Screen.Settings -> SettingsScreen(vm, onBack = pop, open = push)
                Screen.HiddenApps -> SecretScreen(vm, onBack = pop)
            }
        }
    }
}
