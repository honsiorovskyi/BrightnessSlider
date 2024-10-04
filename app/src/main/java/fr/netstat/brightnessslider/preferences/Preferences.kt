package fr.netstat.brightnessslider.preferences

import android.content.Context
import androidx.preference.PreferenceManager
import fr.netstat.brightnessslider.R
import fr.netstat.brightnessslider.SettingsUpdatedEvent
import org.greenrobot.eventbus.EventBus

class Preferences(private val context: Context) {
    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val sharedPrefsEditor = sharedPrefs.edit()

    var isGloballyEnabled
        get() = run {
            sharedPrefs.getBoolean(context.getString(R.string.shared_pref_is_globally_disabled), false)
        }
        set(value) = run {
            sharedPrefsEditor.putBoolean(context.getString(R.string.shared_pref_is_globally_disabled), value)
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

    var flickSensitivityThreshold
        get() = run {
            val default = context.getString(R.string.lst_flick_sensitivity_default)
            sharedPrefs.getString(context.getString(R.string.lst_flick_sensitivity_id), default)
                ?.toFloat() ?: default.toFloat()
        }
        set(value) = run {
//            sharedPrefsEditor.putFloat(context.getString(R.string.lst_flick_sensitivity_id), value)
//            sharedPrefsEditor.apply()
//            EventBus.getDefault().post(SettingsUpdatedEvent(R.id.preferences))
        }
}
