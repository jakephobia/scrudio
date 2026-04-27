package com.scrudio.tv.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.scrudio.tv.data.settings.ScrudioSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Two-way bridge between leanback-preference's [SharedPreferences] and our
 * canonical DataStore-backed [ScrudioSettings].
 *
 * - **DataStore → SharedPreferences** (on screen open): the prefs UI starts
 *   from the real persisted values. Crucially, this overwrites whatever
 *   stale SharedPreferences entry exists, so e.g. a freshly-paired OAuth
 *   token written by [com.scrudio.tv.ui.pair.PairActivity] shows up here.
 * - **SharedPreferences → DataStore** (on user edit): when the user changes
 *   a preference in the UI, mirror the new value into DataStore.
 *
 * Without this two-way design, the previous one-way bridge would clobber
 * the OAuth token every time the Settings screen re-opened.
 */
object SettingsBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var changeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Hydrate SharedPreferences from DataStore. Call when SettingsActivity opens. */
    fun hydrateFromDataStore(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val settings = ScrudioSettings.get(context)
        // Blocking is OK here: we're called from the main thread on Activity
        // create and the values are tiny / already-loaded by DataStore's flow.
        runBlocking {
            sp.edit()
                .putString("rd_key", settings.rdKey())
                .putString("language", settings.language())
                .putBoolean("hide_no_seeds", settings.hideNoSeeds())
                .apply()
        }
    }

    /** Start listening for UI-driven changes to mirror them back to DataStore. */
    fun attachListener(context: Context) {
        if (changeListener != null) return
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val settings = ScrudioSettings.get(context)

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            scope.launch {
                when (key) {
                    "rd_key" -> p.getString("rd_key", null)?.let { typed ->
                        // Manual paste mode: clear OAuth metadata so we don't try to
                        // refresh a private token that doesn't have a refresh_token.
                        if (typed.isBlank()) settings.clearRdAuth()
                        else settings.setRdKey(typed)
                    }
                    "language" -> p.getString("language", null)?.let { settings.setLanguage(it) }
                    "hide_no_seeds" -> settings.setHideNoSeeds(p.getBoolean("hide_no_seeds", true))
                }
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        changeListener = listener
    }

    fun detachListener(context: Context) {
        val listener = changeListener ?: return
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .unregisterOnSharedPreferenceChangeListener(listener)
        changeListener = null
    }
}
