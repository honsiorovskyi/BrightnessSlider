package fr.netstat.brightnessslider

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import fr.netstat.brightnessslider.preferences.Preferences
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

    private val gestureListener: GestureListener = this
    private val gestureDetector = GestureDetector(gestureListener)

    private val appLauncher = AppLauncher(this)
    private lateinit var displayMetrics: DisplayMetrics

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

        displayMetrics = getDisplayMetrics(this)

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
        windowManager().addView(statusBarView, params)
    }

    private fun windowManager(): WindowManager {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        return windowManager
    }

    @SuppressLint("InlinedApi")
    override fun onDoubleTapConfirmed(event: MotionEvent) {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    @SuppressLint("InlinedApi")
    override fun onSingleLongTap(event: MotionEvent) {
        when {
            event.x < displayMetrics.widthPixels / 3f -> toggleFlashLight()
            event.x < displayMetrics.widthPixels * 2f / 3f -> appLauncher.launch(App.OPEN_CAMERA)
            else -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
        Log.v("INFO", "long tap")
    }

    override fun onDoubleLongTap(event: MotionEvent) {
        when {
            event.x < displayMetrics.widthPixels / 3f -> appLauncher.launch(App.SIMPLY_TRANSLATE)
            event.x < displayMetrics.widthPixels * 2f / 3f -> appLauncher.launch(App.GRAPHENEOS_CAMERA, true)
            else -> appLauncher.launch(App.CALCULATOR)
        }
        Log.v("INFO", "long tap")
    }

    private fun toggleFlashLight() {
        torch.torch(40)
    }

    override fun onSingleHorizontalSlide(event: MotionEvent) {
        val totalWidth = displayMetrics.widthPixels
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
        appLauncher.launch(App.OPEN_CAMERA, true)
        Log.v("INFO", "Flick!")
    }

    override fun onDoubleHorizontalFlick(event: MotionEvent, velocity: Float) {
        Log.v("INFO", "Double flick!")
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId != 0) resources.getDimensionPixelSize(resourceId) else 120
    }

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



    private fun updateViewVisibility() {
        statusBarView.visibility = when {
            !preferences.isGloballyEnabled || isLandscape(this) /*|| isDeviceLocked()*/ -> View.INVISIBLE
            else -> View.VISIBLE
        }
    }

    private fun updateSettings() {
        // change brightness scale
        useLogarithmicBrightness = preferences.useLogarithmicBrightness

        // gesture detector settings
        gestureDetector.flickVelocityThreshold = preferences.flickSensitivityThreshold
        Toast.makeText(this, "%f".format(gestureDetector.flickVelocityThreshold), Toast.LENGTH_SHORT).show()

        // update status bar visibility based on the global setting and device state
        updateViewVisibility()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSettingsUpdated(event: SettingsUpdatedEvent) {
        updateSettings()
    }
}

