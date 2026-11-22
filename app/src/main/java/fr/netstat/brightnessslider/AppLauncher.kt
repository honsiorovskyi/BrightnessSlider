package fr.netstat.brightnessslider

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.util.Log

enum class Ratio(val xw: Int, val xh: Int) {
    R9X16(9, 16),
    R10X16(10, 16),
    R9X20(9, 20),
    R3X4(3, 4),
}

enum class WindowMode(private val rw: Int, private val rh: Int, private val scale: Float) {
    Fullscreen(0, 0, 0f),
    Large3x4(3, 4, 0.88f),
    Large9x16(9, 16, 0.88f),
    Large10x16(10, 16, 0.88f),
    Medium4x5(4, 5, 0.82f),
    Medium3x4(3, 4, 0.82f),
    Medium9x16(9, 16, 0.82f),
    Medium10x16(10, 16, 0.82f);

    fun bounds(context: Context): Rect {
        if (this == Fullscreen) {
            throw RuntimeException("calling bounds() for fullscreen apps does not make sense")
        }

        val displayMetrics = getDisplayMetrics(context)
        val deviceHeight = displayMetrics.heightPixels + getStatusBarHeight(context)
        val deviceWidth = displayMetrics.widthPixels

        val margin = (deviceWidth * (1.0f - this.scale)).toInt()
        val width = deviceWidth - 2 * margin
        val height = width * rh / rw

        val right = deviceWidth - margin
        val left = right - width

        val bottom = deviceHeight - margin
        val top = bottom - height

        return Rect(left, top, right, bottom)
    }
}

class AppLauncher(private val ctx: Context) {
    fun launch(
        packageId: String,
        windowMode: WindowMode = WindowMode.Fullscreen,
        includeLockscreen: Boolean = false,
    ) {
        val isLocked = isDeviceLocked(ctx)

        if (!includeLockscreen && isLocked) {
            return
        }

        val intent = ctx.packageManager.getLaunchIntentForPackage(packageId) ?: run {
            Log.w("WARN", "no launch intent for $packageId, is it installed?")
            return
        }
        val options = ActivityOptions.makeBasic()
        val bundle = when {
            // fullscreen if fullscreen requested and also always on lock screen
            windowMode == WindowMode.Fullscreen || isLocked -> options.toBundle()

            // otherwise try free-form window
            else ->  {
                // window size
                options.setLaunchBounds(windowMode.bounds(ctx))

                // set window mode
                // Build.VERSION.SDK_INT >= 28
                options.toBundle().also {
                    it.putInt("android.activity.windowingMode", 5)
                }
            }
        }

        ctx.startActivity(intent, bundle)
    }
}