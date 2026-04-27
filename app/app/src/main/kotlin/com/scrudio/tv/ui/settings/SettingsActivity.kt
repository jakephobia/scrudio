package com.scrudio.tv.ui.settings

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.leanback.preference.LeanbackSettingsFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.scrudio.tv.R

/**
 * TV-styled settings screen using `leanback-preference`.
 *
 * The framework wires PreferenceScreen XML → on-screen list, navigation
 * dialogs, sub-screens and result persistence into SharedPreferences. We
 * mirror those values into our DataStore in [SettingsBridge] so the rest
 * of the app keeps reading from a single source.
 */
class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pull the latest persisted values into SharedPreferences before the
        // PreferenceFragments inflate, so they show fresh state every time
        // (including a token that was just written by PairActivity).
        SettingsBridge.hydrateFromDataStore(this)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, RootSettings())
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        SettingsBridge.attachListener(this)
    }

    override fun onStop() {
        super.onStop()
        SettingsBridge.detachListener(this)
    }

    /** Top-level container required by Leanback (handles back-stack of subscreens). */
    class RootSettings : LeanbackSettingsFragmentCompat() {
        override fun onPreferenceStartInitialScreen() {
            startPreferenceFragment(PrefsFragment())
        }

        override fun onPreferenceStartFragment(
            caller: PreferenceFragmentCompat,
            pref: Preference
        ): Boolean = false   // we don't use nested fragments

        override fun onPreferenceStartScreen(
            caller: PreferenceFragmentCompat,
            pref: PreferenceScreen
        ): Boolean = false   // single screen for now
    }

    class PrefsFragment : LeanbackPreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Update the RD-key summary so the user sees Configured/Not configured
            // without exposing the secret in plain text.
            findPreference<androidx.preference.EditTextPreference>("rd_key")?.let { pref ->
                pref.summaryProvider =
                    androidx.preference.Preference.SummaryProvider<androidx.preference.EditTextPreference> { p ->
                        if (p.text.isNullOrBlank()) getString(R.string.settings_rd_key_unset)
                        else getString(R.string.settings_rd_key_set)
                    }
            }

            // Launch the OAuth pairing screen.
            findPreference<androidx.preference.Preference>("rd_pair")?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(),
                    com.scrudio.tv.ui.pair.PairActivity::class.java))
                true
            }
        }

        override fun onResume() {
            super.onResume()
            // Re-hydrate when returning from PairActivity so the rd_key
            // summary reflects the freshly-saved token.
            SettingsBridge.hydrateFromDataStore(requireContext())
            findPreference<androidx.preference.EditTextPreference>("rd_key")?.let { p ->
                p.text = androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(requireContext())
                    .getString("rd_key", "")
            }
        }
    }
}
