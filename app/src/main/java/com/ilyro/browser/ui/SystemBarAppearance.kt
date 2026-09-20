package com.ilyro.browser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable
internal fun IlyroSystemBarAppearance(
    background: Color = MaterialTheme.colorScheme.background
) {
    val view = LocalView.current
    val useDarkIcons = background.luminance() >= 0.5f
    val argb = background.toArgb()

    SideEffect {
        val window = resolveIlyroWindow(view) ?: return@SideEffect

        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = argb
            window.navigationBarColor = argb
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = useDarkIcons
            isAppearanceLightNavigationBars = useDarkIcons
        }

        // Some Android/Samsung builds re-apply the platform dialog theme on the next frame.
        view.post {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = useDarkIcons
                isAppearanceLightNavigationBars = useDarkIcons
            }
        }
    }
}

private fun resolveIlyroWindow(view: View): Window? {
    if (view is DialogWindowProvider) return view.window

    var parent: ViewParent? = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }

    return view.context.findIlyroActivity()?.window
}

private tailrec fun Context.findIlyroActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findIlyroActivity()
    else -> null
}
