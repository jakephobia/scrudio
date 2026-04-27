package com.scrudio.tv.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scrudio.tv.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * User-settable runtime preferences.
 *
 * Mirrors `settings.py` from the Kodi addon, minus the Kodi-specific flags
 * (cache TTL, hide-no-seeds toggles…) which are enforced in code here.
 */
class ScrudioSettings private constructor(private val context: Context) {

    private val ds get() = context.scrudioDataStore

    fun tmdbKeyFlow(): Flow<String> = ds.data.map { p ->
        p[KEY_TMDB].orEmpty().ifEmpty { BuildConfig.TMDB_DEFAULT_KEY }
    }

    fun rdKeyFlow(): Flow<String> = ds.data.map { p -> p[KEY_RD].orEmpty() }

    fun languageFlow(): Flow<String> = ds.data.map { p -> p[KEY_LANG] ?: defaultLanguage() }

    fun hideNoSeedsFlow(): Flow<Boolean> = ds.data.map { p -> p[KEY_HIDE_NO_SEEDS] ?: true }

    suspend fun tmdbKey(): String = tmdbKeyFlow().first()
    suspend fun rdKey(): String = rdKeyFlow().first()
    suspend fun language(): String = languageFlow().first()
    suspend fun hideNoSeeds(): Boolean = hideNoSeedsFlow().first()
    suspend fun hasRd(): Boolean = rdKey().isNotBlank()

    suspend fun setTmdbKey(key: String) = ds.edit { it[KEY_TMDB] = key.trim() }
    suspend fun setRdKey(key: String) = ds.edit { it[KEY_RD] = key.trim() }
    suspend fun setLanguage(lang: String) = ds.edit { it[KEY_LANG] = lang }
    suspend fun setHideNoSeeds(value: Boolean) = ds.edit { it[KEY_HIDE_NO_SEEDS] = value }

    // ── Real-Debrid OAuth bundle ──────────────────────────────────────────
    // The four fields below are written *together* by [setRdAuth]: an
    // access_token without its refresh metadata is useless once it expires.
    suspend fun rdRefreshToken(): String = ds.data.first()[KEY_RD_REFRESH].orEmpty()
    suspend fun rdClientId(): String = ds.data.first()[KEY_RD_CLIENT_ID].orEmpty()
    suspend fun rdClientSecret(): String = ds.data.first()[KEY_RD_CLIENT_SECRET].orEmpty()
    suspend fun rdExpiresAt(): Long = ds.data.first()[KEY_RD_EXPIRES_AT] ?: 0L

    /** Saves the full OAuth bundle from a successful pair / refresh. */
    suspend fun setRdAuth(
        accessToken: String,
        refreshToken: String,
        clientId: String,
        clientSecret: String,
        expiresAtMillis: Long
    ) = ds.edit {
        it[KEY_RD] = accessToken
        it[KEY_RD_REFRESH] = refreshToken
        it[KEY_RD_CLIENT_ID] = clientId
        it[KEY_RD_CLIENT_SECRET] = clientSecret
        it[KEY_RD_EXPIRES_AT] = expiresAtMillis
    }

    /** Wipes everything Real-Debrid related (manual disconnect). */
    suspend fun clearRdAuth() = ds.edit {
        it.remove(KEY_RD)
        it.remove(KEY_RD_REFRESH)
        it.remove(KEY_RD_CLIENT_ID)
        it.remove(KEY_RD_CLIENT_SECRET)
        it.remove(KEY_RD_EXPIRES_AT)
    }

    private fun defaultLanguage(): String {
        val sysLang = java.util.Locale.getDefault().language
        // TMDB language codes — pick IT for Italian users, English-US otherwise.
        return if (sysLang == "it") "it-IT" else "en-US"
    }

    companion object {
        private val Context.scrudioDataStore: androidx.datastore.core.DataStore<Preferences>
                by preferencesDataStore(name = "scrudio_settings")

        private val KEY_TMDB = stringPreferencesKey("tmdb_key")
        private val KEY_RD = stringPreferencesKey("rd_key")
        private val KEY_LANG = stringPreferencesKey("language")
        private val KEY_HIDE_NO_SEEDS = booleanPreferencesKey("hide_no_seeds")
        private val KEY_RD_REFRESH = stringPreferencesKey("rd_refresh_token")
        private val KEY_RD_CLIENT_ID = stringPreferencesKey("rd_client_id")
        private val KEY_RD_CLIENT_SECRET = stringPreferencesKey("rd_client_secret")
        private val KEY_RD_EXPIRES_AT = longPreferencesKey("rd_expires_at")

        @Volatile private var instance: ScrudioSettings? = null
        fun get(context: Context): ScrudioSettings =
            instance ?: synchronized(this) {
                instance ?: ScrudioSettings(context.applicationContext).also { instance = it }
            }
    }
}
