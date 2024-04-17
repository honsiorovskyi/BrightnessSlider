package fr.netstat.brightnessslider

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        EventBus.getDefault().post(SettingsUpdatedEvent(R.id.settings))
        return super.onPreferenceTreeClick(preference)
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