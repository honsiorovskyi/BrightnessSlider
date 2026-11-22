package fr.netstat.brightnessslider.preferences

import android.content.Context
import androidx.preference.PreferenceManager
import fr.netstat.brightnessslider.BarAction
import fr.netstat.brightnessslider.Edge
import fr.netstat.brightnessslider.GestureSlot
import fr.netstat.brightnessslider.R
import fr.netstat.brightnessslider.SettingsUpdatedEvent
import org.greenrobot.eventbus.EventBus

class Preferences(private val context: Context) {
    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val sharedPrefsEditor = sharedPrefs.edit()

    var isGloballyEnabled
        get() = run {
            sharedPrefs.getBoolean(context.getString(R.string.shared_pref_is_globally_enabled), false)
        }
        set(value) = run {
            sharedPrefsEditor.putBoolean(context.getString(R.string.shared_pref_is_globally_enabled), value)
            sharedPrefsEditor.apply()
            EventBus.getDefault().post(SettingsUpdatedEvent(R.id.preferences))
        }

    var useLogarithmicBrightness
        get() = run {
            sharedPrefs.getBoolean(context.getString(R.string.sw_logarithmic_id), false)
        }
        set(value) = run {
            sharedPrefsEditor.putBoolean(context.getString(R.string.sw_logarithmic_id), value)
            sharedPrefsEditor.apply()
            EventBus.getDefault().post(SettingsUpdatedEvent(R.id.preferences))
        }

    // read-only: the value is written by the ListPreference as a string
    val flickSensitivityThreshold
        get() = run {
            val default = context.getString(R.string.lst_flick_sensitivity_default)
            sharedPrefs.getString(context.getString(R.string.lst_flick_sensitivity_id), default)
                ?.toFloat() ?: default.toFloat()
        }

    // read-only: the value is written by the ListPreference as a string
    val backGestureEdges: Set<Edge>
        get() = run {
            val default = context.getString(R.string.lst_back_gesture_edges_default)
            when (sharedPrefs.getString(context.getString(R.string.lst_back_gesture_edges_id), default)) {
                "left" -> setOf(Edge.LEFT)
                "right" -> setOf(Edge.RIGHT)
                "both" -> setOf(Edge.LEFT, Edge.RIGHT)
                else -> emptySet()
            }
        }

    // read-only: the value is written by the ListPreference as a string
    val backGestureWidth: Int
        get() = run {
            val default = context.getString(R.string.lst_back_gesture_width_default)
            sharedPrefs.getString(context.getString(R.string.lst_back_gesture_width_id), default)
                ?.toInt() ?: default.toInt()
        }

    // read-only: the value is written by the ListPreference as a string
    val recentsFraction: Float
        get() = run {
            val default = context.getString(R.string.lst_recents_threshold_default)
            sharedPrefs.getString(context.getString(R.string.lst_recents_threshold_id), default)
                ?.toFloat() ?: default.toFloat()
        }

    // read-only: the value is written by the ListPreference as a string
    fun action(slot: GestureSlot): BarAction =
        BarAction.parse(sharedPrefs.getString(context.getString(slot.keyRes), context.getString(slot.defaultRes)))

    // read-only: the value is written by the MultiSelectListPreference
    val lockscreenSlots: Set<GestureSlot>
        get() = run {
            val default = context.resources.getStringArray(R.array.arr_lockscreen_gesture_default).toSet()
            val ids = sharedPrefs.getStringSet(context.getString(R.string.mlst_lockscreen_gestures_id), default) ?: default
            GestureSlot.entries.filter { it.id in ids }.toSet()
        }
}
