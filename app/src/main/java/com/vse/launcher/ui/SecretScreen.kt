package com.vse.launcher.ui

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vse.launcher.data.LauncherViewModel
import kotlin.math.roundToInt

/** Можно ли разблокировать отпечатком/лицом (Android 11+). */
private fun biometricAvailable(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val bm = ctx.getSystemService(BiometricManager::class.java) ?: return false
    return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
}

private fun askBiometric(ctx: Context, onOk: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    runCatching {
        BiometricPrompt.Builder(ctx)
            .setTitle("Секретная папка")
            .setSubtitle("Подтвердите, что это вы")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButton("Ввести PIN", ctx.mainExecutor) { _, _ -> }
            .build()
            .authenticate(CancellationSignal(), ctx.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onOk()
            })
    }
}

@Composable
fun SecretScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }

    if (!unlocked || changingPin) {
        PinScreen(
            vm = vm,
            create = vm.secretPin == null || changingPin,
            onSuccess = { unlocked = true; changingPin = false },
            onBack = { if (changingPin) changingPin = false else onBack() },
        )
        LaunchedEffect(Unit) {
            if (!unlocked && vm.secretPin != null && biometricAvailable(ctx)) askBiometric(ctx) { unlocked = true }
        }
        return
    }

    val apps = vm.hiddenApps()
    val s = vm.settings
    ScreenScaffold("Секретная папка", onBack, actions = {
        IconButton({ changingPin = true }) { Icon(Icons.Default.Password, "Сменить PIN") }
    }) {
        if (apps.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Здесь пусто", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Чтобы спрятать приложение, удерживайте его иконку и выберите «В секретную папку». " +
                        "Оно пропадёт с рабочего стола, из меню и из поиска, а открыть его можно будет только здесь, по PIN-коду.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                "Удерживайте иконку, чтобы вернуть приложение из секретной папки.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            LazyVerticalGrid(GridCells.Fixed(s.columns), contentPadding = PaddingValues(12.dp)) {
                items(apps, key = { it.key }) { app ->
                    AppIcon(app, vm, s.iconSizeDp.dp, true, onWallpaper = false)
                }
            }
        }
    }
}

@Composable
private fun PinScreen(vm: LauncherViewModel, create: Boolean, onSuccess: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var entered by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }

    val title = when {
        !create -> "Введите PIN-код"
        first == null -> "Придумайте PIN-код"
        else -> "Повторите PIN-код"
    }

    LaunchedEffect(entered) {
        if (entered.length < 4) return@LaunchedEffect
        if (!create) {
            if (entered == vm.secretPin) onSuccess() else {
                error = "Неверный PIN-код"
                shake.animateTo(0f, androidx.compose.animation.core.keyframes {
                    durationMillis = 300
                    -24f at 50; 24f at 100; -16f at 150; 16f at 200; 0f at 300
                })
            }
            entered = ""
        } else if (first == null) {
            first = entered; entered = ""; error = null
        } else {
            if (entered == first) {
                vm.saveSecretPin(entered)
                onSuccess()
            } else {
                error = "PIN-коды не совпали, попробуйте ещё раз"
                first = null
            }
            entered = ""
        }
    }

    ScreenScaffold("Секретная папка", onBack) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Default.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(error ?: " ", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                repeat(4) { i ->
                    val filled = i < entered.length
                    Box(
                        Modifier.size(16.dp).clip(CircleShape)
                            .background(if (filled) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                            .border(1.5.dp, MaterialTheme.colorScheme.onBackground, CircleShape),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(if (k.length == 1) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .clickable(enabled = k != "bio" || (!create && biometricAvailable(ctx))) {
                                    when (k) {
                                        "del" -> entered = entered.dropLast(1)
                                        "bio" -> askBiometric(ctx, onSuccess)
                                        else -> if (entered.length < 4) entered += k
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Стереть")
                                "bio" -> if (!create && biometricAvailable(ctx)) Icon(Icons.Default.Fingerprint, "Отпечаток", Modifier.size(32.dp))
                                else -> Text(k, fontSize = 30.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
