package fr.netstat.brightnessslider

import android.accessibilityservice.AccessibilityService
import androidx.annotation.StringRes

enum class GestureKind {
    // a slide can drive a continuous action (brightness) or fire a one-shot action
    // once it has covered half the bar
    SLIDE,

    // a tap can only fire one-shot actions
    TAP,
}

// The status bar gestures that can be bound to an action. Each gesture holds a single action,
// so actions recognized from the same gesture (e.g. brightness vs. lock on a single slide)
// can't conflict. `id` is the value stored by the lock screen MultiSelectListPreference.
enum class GestureSlot(
    val id: String,
    val kind: GestureKind,
    @StringRes val keyRes: Int,
    @StringRes val defaultRes: Int,
) {
    SINGLE_SLIDE("single_slide", GestureKind.SLIDE, R.string.lst_action_single_slide_id, R.string.lst_action_single_slide_default),
    LONG_TAP_SLIDE("long_tap_slide", GestureKind.SLIDE, R.string.lst_action_long_tap_slide_id, R.string.lst_action_long_tap_slide_default),
    DOUBLE_TAP_SLIDE("double_tap_slide", GestureKind.SLIDE, R.string.lst_action_double_tap_slide_id, R.string.lst_action_double_tap_slide_default),
    DOUBLE_TAP_LEFT("double_tap_left", GestureKind.TAP, R.string.lst_action_double_tap_left_id, R.string.lst_action_double_tap_left_default),
    DOUBLE_TAP_MIDDLE("double_tap_middle", GestureKind.TAP, R.string.lst_action_double_tap_middle_id, R.string.lst_action_double_tap_middle_default),
    DOUBLE_TAP_RIGHT("double_tap_right", GestureKind.TAP, R.string.lst_action_double_tap_right_id, R.string.lst_action_double_tap_right_default),
    DOUBLE_LONG_TAP_LEFT("double_long_tap_left", GestureKind.TAP, R.string.lst_action_double_long_tap_left_id, R.string.lst_action_double_long_tap_left_default),
    DOUBLE_LONG_TAP_RIGHT("double_long_tap_right", GestureKind.TAP, R.string.lst_action_double_long_tap_right_id, R.string.lst_action_double_long_tap_right_default),
}

// Parsed from the stored ListPreference value: one of the arr_*_action_values, or
// APP_PREFIX followed by a package ID.
sealed interface BarAction {
    object None : BarAction
    object Brightness : BarAction
    object Torch : BarAction
    data class Global(val action: Int) : BarAction
    data class LaunchApp(val packageId: String) : BarAction

    companion object {
        const val APP_PREFIX = "app:"

        fun parse(value: String?): BarAction = when {
            value == null -> None
            value.startsWith(APP_PREFIX) -> LaunchApp(value.removePrefix(APP_PREFIX))
            else -> when (value) {
                "brightness" -> Brightness
                "torch" -> Torch
                "lock_screen" -> Global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                "screenshot" -> Global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                "notifications" -> Global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                "quick_settings" -> Global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                "recents" -> Global(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "home" -> Global(AccessibilityService.GLOBAL_ACTION_HOME)
                "back" -> Global(AccessibilityService.GLOBAL_ACTION_BACK)
                "power_dialog" -> Global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                else -> None
            }
        }
    }
}
