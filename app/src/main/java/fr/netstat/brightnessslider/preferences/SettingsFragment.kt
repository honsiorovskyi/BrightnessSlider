package fr.netstat.brightnessslider.preferences

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import fr.netstat.brightnessslider.R
import fr.netstat.brightnessslider.SettingsUpdatedEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class SettingsFragment : PreferenceFragmentCompat() {
    // posted after the value is actually saved (a ListPreference saves after its dialog closes)
    private val sharedPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        EventBus.getDefault().post(SettingsUpdatedEvent(R.id.settings))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSettingsUpdated(event: SettingsUpdatedEvent) {
        // if settings were updated by us, skip the refresh
        if (event.sender == R.id.settings) {
            return
        }

        // otherwise, refresh the view
        preferenceScreen = null
        addPreferencesFromResource(R.xml.root_preferences);
    }
}