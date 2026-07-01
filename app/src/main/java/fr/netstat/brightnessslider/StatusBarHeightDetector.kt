package fr.netstat.brightnessslider

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM
import kotlin.math.max
import kotlin.math.min

class StatusBarHeightDetector(private val accessibilityService: AccessibilityService) {
    private var portraitHeight: Int = -1
    private var landscapeHeight: Int = -1

    val height: Int
        get() {
            // disable for not as it's buggy as hell; falling back to the fallback height for now
            reevaluateHeights()

            return if (isLandscape(accessibilityService)) {
                if (landscapeHeight != -1) {
                    Log.v("StatusBarHeightDetector", "Using landscape status bar height: $landscapeHeight")
                    landscapeHeight
                } else {
                    val fallback = fallbackHeight()
                    Log.v("StatusBarHeightDetector", "Landscape status bar height not found, using fallback: $fallback")
                    fallback
                }
            } else {
                if (portraitHeight != -1) {
                    Log.v("StatusBarHeightDetector", "Using portrait status bar height: $portraitHeight")
                    portraitHeight
                } else {
                    val fallback = fallbackHeight()
                    Log.v("StatusBarHeightDetector", "Portrait status bar height not found, using fallback: $fallback")
                    fallback
                }
            }
        }

    private fun reevaluateHeights() {
        if (portraitHeight != -1 && landscapeHeight != -1) {
            // Heights already evaluated, no need to re-evaluate
            return
        }

        Log.v("INFO", "Reevaluating status bar heights...")
//        accessibilityService.windows.forEach {
//            Log.v("INFO", "Window: $it")
//        }

        val maxWindowMetrics = getWindowManager(accessibilityService).maximumWindowMetrics;
        val displayMetrics = getDisplayMetrics(accessibilityService)

        accessibilityService.windows.find {
            it.type == TYPE_SYSTEM && it.layer == 1 && !it.isFocused && !it.isActive && it.childCount == 0
        }?.let {
            val rect = Rect()
            it.getBoundsInScreen(rect)

            Log.v("INFO", "m1: ${maxWindowMetrics.bounds}, m2: ${displayMetrics} window: $it")

            if (rect.left != 0 || rect.top != 0) {
                return@let
            }

            // portrait status bar
            if (
                rect.width() == min(maxWindowMetrics.bounds.height(), maxWindowMetrics.bounds.width()) &&
                rect.height() < max(maxWindowMetrics.bounds.height(), maxWindowMetrics.bounds.width()) / 5
            ) {
                portraitHeight = rect.height()

                Log.v("StatusBarHeightDetector", "Detected portrait status bar height: $portraitHeight")
            }

            // landscape status bar
            if (
                rect.width() == max(maxWindowMetrics.bounds.height(), maxWindowMetrics.bounds.width()) &&
                rect.height() < min(maxWindowMetrics.bounds.height(), maxWindowMetrics.bounds.width()) / 5
            ) {
                landscapeHeight = rect.height()

                Log.v("StatusBarHeightDetector", "Detected landscape status bar height: $landscapeHeight")
            }
        }
    }

    private fun fallbackHeight(): Int {
        val resourceId = accessibilityService.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId != 0) accessibilityService.resources.getDimensionPixelSize(resourceId) else 63
    }
}