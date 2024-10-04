package fr.netstat.brightnessslider

import android.accessibilityservice.AccessibilityService.KEYGUARD_SERVICE
import android.accessibilityservice.AccessibilityService.WINDOW_SERVICE
import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.WindowManager


fun getStatusBarHeight(ctx: Context): Int {
    val resourceId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resourceId != 0) ctx.resources.getDimensionPixelSize(resourceId) else 120
}

fun getWindowManager(ctx: Context): WindowManager {
    return ctx.getSystemService(WINDOW_SERVICE) as WindowManager
}

fun getDisplayMetrics(ctx: Context): DisplayMetrics {
    val displayMetrics = DisplayMetrics()

    getWindowManager(ctx).defaultDisplay.getMetrics(displayMetrics)

    return displayMetrics
}

fun isDeviceLocked(ctx: Context): Boolean {
    val keyguardManager = ctx.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    return keyguardManager.isDeviceLocked
}

fun getScreenOrientation(ctx: Context): Int {
    val display = getWindowManager(ctx).defaultDisplay

    return if (display.width == display.height) {
        Configuration.ORIENTATION_SQUARE
    } else {
        if (display.width < display.height) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }
    }
}

fun isLandscape(ctx: Context): Boolean {
    return getScreenOrientation(ctx) == Configuration.ORIENTATION_LANDSCAPE
}