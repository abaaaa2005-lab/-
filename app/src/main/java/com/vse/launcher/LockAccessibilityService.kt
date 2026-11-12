package com.vse.launcher

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Служба спец. возможностей: блокировка экрана двойным тапом и, для помощника Дружка,
 * название открытого приложения. Содержимое экрана не читается.
 */
class LockAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        com.vse.launcher.buddy.BuddyState.watchingApps = true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        com.vse.launcher.buddy.BuddyState.watchingApps = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        // Клавиатуры, шторка и системные окна — не «приложения»
        if (pkg == "android" || pkg.contains("systemui") || pkg.contains("inputmethod") || pkg.contains("keyboard") || pkg.contains("honeyboard")) return
        // Свой пакет считаем, только когда открыт сам рабочий стол (а не окно Дружка)
        if (pkg == packageName && cls != com.vse.launcher.MainActivity::class.java.name) return
        val b = com.vse.launcher.buddy.BuddyState
        if (b.currentApp == pkg) return
        b.currentApp = pkg
        b.appSince = System.currentTimeMillis()
        b.currentLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull() ?: pkg.substringAfterLast('.')
    }
    override fun onInterrupt() {}

    companion object {
        @Volatile
        private var instance: LockAccessibilityService? = null

        fun lock(): Boolean {
            val s = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }
}
