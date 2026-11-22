package fr.netstat.brightnessslider.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import fr.netstat.brightnessslider.BarAction
import fr.netstat.brightnessslider.GestureSlot
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
        addAppEntries()
    }

    // launchable apps as (label, package ID), sorted by label
    private val launchableApps by lazy {
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }

    // the XML only lists the fixed actions, append one "launch" entry per installed app
    private fun addAppEntries() {
        val appEntries = launchableApps.map { (label, packageId) ->
            getString(R.string.action_launch_app, label) to BarAction.APP_PREFIX + packageId
        }

        for (slot in GestureSlot.entries) {
            val pref = findPreference<ListPreference>(getString(slot.keyRes)) ?: continue
            val entries = pref.entries.zip(pref.entryValues).map { it.first.toString() to it.second.toString() } +
                appEntries

            // keep a configured app that isn't installed (anymore) selectable and visible
            val missing = pref.value
                ?.takeIf { value -> value.startsWith(BarAction.APP_PREFIX) && entries.none { it.second == value } }
                ?.let { getString(R.string.action_app_not_installed, it.removePrefix(BarAction.APP_PREFIX)) to it }

            val all = entries + listOfNotNull(missing)
            pref.entries = all.map { it.first }.toTypedArray()
            pref.entryValues = all.map { it.second }.toTypedArray()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSettingsUpdated(event: SettingsUpdatedEvent) {
        // if settings were updated by us, skip the refresh
        if (event.sender == R.id.settings) {
            return
        }

        // otherwise, refresh the view
        preferenceScreen = null
        addPreferencesFromResource(R.xml.root_preferences)
        addAppEntries()
    }
}