package fr.netstat.brightnessslider

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.res.Resources
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.pow

class StatusBarAccessibilityService : AccessibilityService(), GestureListener {
    companion object {
        // Not holding an indefinite static reference, so this should be ok.
        // However, singleton is unsatisfying here, but is there another solution?
        @SuppressLint("StaticFieldLeak")
        var instance: StatusBarAccessibilityService? = null
            private set
    }


    private lateinit var statusBarView: View
    private lateinit var preferences: Preferences
    private lateinit var torch: Torch

    private var useLogarithmicBrightness = false

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
        EventBus.getDefault().unregister(this)
        EventBus.getDefault().post(AccessibilityStatusChangedEvent(AccessibilityStatusType.UNBOUND))
    }

    override fun onServiceConnected() {
        val gestureListener: GestureListener = this
        val gestureDetector = GestureDetector(gestureListener)
        statusBarView.setOnTouchListener(gestureDetector)

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            getStatusBarHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR // Deprecated
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
//            or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE  // <= SDK30 ??
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
        }
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(statusBarView, params)
    }

    override fun onDoubleTapConfirmed(event: MotionEvent) {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    override fun onSingleLongTap(event: MotionEvent) {
        if (event.x < getScreenWidth() / 3f) {
            toggleFlashLight()
        } else if (event.x > getScreenWidth() / 3f * 2f) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
        }
        Log.v("INFO", "long tap")
    }

    private fun toggleFlashLight() {
        torch.torch(40)
    }

    override fun onSingleHorizontalSlide(event: MotionEvent) {
        val totalWidth = getScreenWidth()
        val pos = ((event.x - padding) / (totalWidth - 2 * padding)).coerceIn(0f, 1f)

        val brightnessValue = when(useLogarithmicBrightness) {
            true ->  { 255.0.pow(pos.toDouble()) - 1 }
            false -> { 255*pos }
        }

        Settings.System.putInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightnessValue.toInt().coerceIn(0, 255),
        )
    }

    override fun onSingleHorizontalFlick(event: MotionEvent, velocity: Float) {
        Log.v("INFO", "Flick!")
    }

    override fun onDoubleHorizontalFlick(event: MotionEvent, velocity: Float) {
        Log.v("INFO", "Double flick!")
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId != 0) resources.getDimensionPixelSize(resourceId) else 120
    }

    private fun getScreenWidth() = Resources.getSystem().displayMetrics.widthPixels

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
            else -> {}
        }
    }

    override fun onInterrupt() {}

    private fun isDeviceLocked(): Boolean {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isDeviceLocked
    }

    private fun updateViewVisibility() {
        statusBarView.visibility = when {
            !preferences.isGloballyEnabled || isDeviceLocked() -> View.INVISIBLE
            else -> View.VISIBLE
        }
    }

    private fun updateSettings() {
        // change brightness scale
        useLogarithmicBrightness = preferences.useLogarithmicBrightness

        // update status bar visibility based on the global setting and device state
        updateViewVisibility()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSettingsUpdated(event: SettingsUpdatedEvent) {
        updateSettings()
    }
}
