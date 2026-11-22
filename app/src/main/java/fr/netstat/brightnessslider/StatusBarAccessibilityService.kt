package fr.netstat.brightnessslider

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import fr.netstat.brightnessslider.preferences.Preferences
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class StatusBarAccessibilityService : AccessibilityService(), GestureListener {
    companion object {
        // Not holding an indefinite static reference, so this should be ok.
        // However, singleton is unsatisfying here, but is there another solution?
        @SuppressLint("StaticFieldLeak")
        var instance: StatusBarAccessibilityService? = null
            private set
    }

    private val gestureListener: GestureListener = this
    private val gestureDetector = GestureDetector(gestureListener)

    private val statusBarHeightDetector = StatusBarHeightDetector(this)

    private val appLauncher = AppLauncher(this)

    private lateinit var statusBarView: View
    private lateinit var preferences: Preferences
    private lateinit var torch: Torch

    private var edgeBackGesture: EdgeBackGesture? = null

    private var useLogarithmicBrightness = false

    private var actions: Map<GestureSlot, BarAction> = emptyMap()
    private var lockscreenSlots: Set<GestureSlot> = emptySet()

    private val padding = 100 // Left and right "deadzone"

    override fun onCreate() {
        super.onCreate()

        instance = this
        statusBarView = View(this)
        preferences = Preferences(this)
        torch = Torch(this)

        updateSettings()

        EventBus.getDefault().register(this)
        EventBus.getDefault().post(AccessibilityStatusChangedEvent(AccessibilityStatusType.BOUND))
    }

    override fun onDestroy() {
        super.onDestroy()

        instance = null
        edgeBackGesture?.destroy()
        edgeBackGesture = null
        torch.close()
        EventBus.getDefault().unregister(this)
        EventBus.getDefault().post(AccessibilityStatusChangedEvent(AccessibilityStatusType.UNBOUND))
    }

    override fun onServiceConnected() {
        statusBarView.setOnTouchListener(gestureDetector)

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            windowWidth,
            statusBarHeightDetector.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR // Deprecated
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
//            or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE  // <= SDK30 ??
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP
        }
        windowManager().addView(statusBarView, params)

        edgeBackGesture = EdgeBackGesture(this, statusBarHeightDetector)
        updateSettings()
    }

    private fun windowManager(): WindowManager {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        return windowManager
    }

    override fun onDoubleTapConfirmed(event: MotionEvent) {
        perform(
            when {
                event.x < windowWidth / 3f -> GestureSlot.DOUBLE_TAP_LEFT
                event.x < windowWidth * 2f / 3f -> GestureSlot.DOUBLE_TAP_MIDDLE
                else -> GestureSlot.DOUBLE_TAP_RIGHT
            }
        )
    }

    override fun onSingleLongTap(event: MotionEvent) {
        Log.v("INFO", "single long tap at ${event.x}, ${event.y}")

        // tells the user they can start sliding
        if (actions[GestureSlot.LONG_TAP_SLIDE] != BarAction.None) {
            vibrate(this, 5)
        }
    }

    override fun onDoubleLongTap(event: MotionEvent) {
        val slot = when {
            event.x < windowWidth * 0.5f -> GestureSlot.DOUBLE_LONG_TAP_LEFT
            else -> GestureSlot.DOUBLE_LONG_TAP_RIGHT
        }

        if (actions[slot] != BarAction.None) {
            vibrate(this, 5)
        }
        perform(slot)
    }

    override fun onSingleHorizontalSlide(event: MotionEvent, downEvent: MotionEvent): Boolean =
        slide(GestureSlot.SINGLE_SLIDE, event, downEvent)

    override fun onSingleLongTapHorizontalSlide(event: MotionEvent, downEvent: MotionEvent): Boolean =
        slide(GestureSlot.LONG_TAP_SLIDE, event, downEvent)

    override fun onDoubleHorizontalSlide(event: MotionEvent, downEvent: MotionEvent): Boolean =
        slide(GestureSlot.DOUBLE_TAP_SLIDE, event, downEvent)

    // returns false to stop following the slide
    private fun slide(slot: GestureSlot, event: MotionEvent, downEvent: MotionEvent): Boolean {
        Log.v("INFO", "$slot down: ${downEvent.x}, current: ${event.x}")

        return when (actions[slot] ?: BarAction.None) {
            BarAction.None -> false

            BarAction.Brightness -> {
                setBrightness(event.x)
                true
            }

            // one-shot actions fire once the finger has slid across half the bar
            else -> if (abs(event.x - downEvent.x) > windowWidth * 0.5f) {
                perform(slot)
                false
            } else {
                true
            }
        }
    }

    private fun perform(slot: GestureSlot) {
        when (val action = actions[slot] ?: BarAction.None) {
            BarAction.None -> {}
            // only meaningful while sliding, see slide()
            BarAction.Brightness -> {}
            BarAction.Torch -> toggleFlashLight()
            is BarAction.Global -> performGlobalAction(action.action)
            is BarAction.LaunchApp -> appLauncher.launch(
                action.packageId,
                includeLockscreen = slot in lockscreenSlots,
            )
        }
    }

    private fun toggleFlashLight() {
        torch.torch(40)
    }

    private fun setBrightness(x: Float) {
        val pos = ((x - padding) / (windowWidth - 2 * padding)).coerceIn(0f, 1f)


        val brightnessValue = when (useLogarithmicBrightness) {
            true -> {
                255.0.pow(pos.toDouble()) - 1
            }

            false -> {
                255 * pos
            }
        }


        Settings.System.putInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightnessValue.toInt().coerceIn(0, 255),
        )
    }


//    override fun onSingleHorizontalFlick(event: MotionEvent, velocity: Float) {
//        appLauncher.launch(App.OPEN_CAMERA, true)
//        Log.v("INFO", "Flick!")
//    }
//
//    override fun onDoubleHorizontalFlick(event: MotionEvent, velocity: Float) {
//        toggleFlashLight()
//        Log.v("INFO", "Double flick!")
//    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            // We need to disable the view when screen is locked because the brightness
            // control is all buggy and laggy on the lockscreen, and it prevents opening
            // the status bar menu. The proper event type for this should be
            // "TYPE_ANNOUNCEMENT" and not "TYPE_WINDOW_STATE_CHANGED" because an event of
            // "TYPE_ANNOUNCEMENT" is supposed to be emitted when locking and unlocking the
            // screen. However, for some reason, the announcement is randomly not
            // triggered, typically when locking the screen and instantly powering the
            // screen back on. The window state changed is triggered more frequently, but
            // at least consistently.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> updateViewVisibility()
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> edgeBackGesture?.onWindowsChanged()
            else -> {}
        }
    }

    override fun onInterrupt() {}


    private fun updateViewVisibility() {
        statusBarView.visibility = when {
            !preferences.isGloballyEnabled /*|| isLandscape(this)*/ /*|| isDeviceLocked()*/ -> View.INVISIBLE
            else -> View.VISIBLE
        }
    }

    private val windowWidth: Int
        get() = if (isLandscape(this)) {
            max(displayMetrics.widthPixels, displayMetrics.heightPixels) * 80 / 100
        } else {
            min(displayMetrics.widthPixels, displayMetrics.heightPixels) * 80 / 100
        }

    private val displayMetrics: DisplayMetrics
        get() = getDisplayMetrics(this)

    private fun updateSettings() {
        // change brightness scale
        useLogarithmicBrightness = preferences.useLogarithmicBrightness

        // top bar gesture actions
        actions = GestureSlot.entries.associateWith { preferences.action(it) }
        lockscreenSlots = preferences.lockscreenSlots

        // gesture detector settings
        gestureDetector.flickVelocityThreshold = preferences.flickSensitivityThreshold

        // update status bar visibility based on the global setting and device state
        updateViewVisibility()

        // edge back gesture settings
        edgeBackGesture?.let {
            it.setEdges(preferences.backGestureEdges)
            it.setEdgeWidth(preferences.backGestureWidth)
            it.setRecentsFraction(preferences.recentsFraction)
            it.setEnabled(preferences.isGloballyEnabled)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.v("INFO", "Configuration changed: ${newConfig.orientation}")

        edgeBackGesture?.updateLayout()

        // Update the status bar height when the configuration changes
        val params = statusBarView.layoutParams as? WindowManager.LayoutParams? ?: return

        params.width = windowWidth
        params.height = statusBarHeightDetector.height
        windowManager().updateViewLayout(statusBarView, params)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSettingsUpdated(event: SettingsUpdatedEvent) {
        updateSettings()
    }
}
