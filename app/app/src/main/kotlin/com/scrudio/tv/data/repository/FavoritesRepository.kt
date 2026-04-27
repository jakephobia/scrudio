package com.scrudio.tv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scrudio.tv.data.api.HttpModule
import com.scrudio.tv.data.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

/**
 * Persists a small list of favorited [MediaItem]s as a single JSON blob in
 * a dedicated DataStore (kept separate from `scrudio_settings` so wiping
 * favorites doesn't lose the RD token).
 *
 * Capped at [MAX] entries to keep the I/O cheap on 1 GB devices.
 */
class FavoritesRepository private constructor(private val context: Context) {

    private val ds: DataStore<Preferences> get() = context.favoritesDataStore
    private val serializer = ListSerializer(MediaItem.serializer())

    fun favoritesFlow(): Flow<List<MediaItem>> = ds.data.map { prefs ->
        decode(prefs[KEY_JSON].orEmpty())
    }

    suspend fun favorites(): List<MediaItem> = favoritesFlow().first()

    suspend fun isFavorite(item: MediaItem): Boolean =
        favorites().any { it.cardId == item.cardId }

    suspend fun toggle(item: MediaItem): Boolean {
        var nowFavorite = false
        ds.edit { prefs ->
            val current = decode(prefs[KEY_JSON].orEmpty()).toMutableList()
            val idx = current.indexOfFirst { it.cardId == item.cardId }
            if (idx >= 0) {
                current.removeAt(idx)
                nowFavorite = false
            } else {
                current.add(0, item)
                while (current.size > MAX) current.removeLast()
                nowFavorite = true
            }
            prefs[KEY_JSON] = HttpModule.json.encodeToString(serializer, current)
        }
        return nowFavorite
    }

    private fun decode(raw: String): List<MediaItem> =
        if (raw.isEmpty()) emptyList()
        else try {
            HttpModule.json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            android.util.Log.w("FavoritesRepository", "decode failed", e)
            emptyList()
        }

    companion object {
        private const val MAX = 100
        private val KEY_JSON = stringPreferencesKey("favorites_json")
        private val Context.favoritesDataStore: DataStore<Preferences>
                by preferencesDataStore(name = "scrudio_favorites")

        @Volatile private var instance: FavoritesRepository? = null
        fun get(context: Context): FavoritesRepository =
            instance ?: synchronized(this) {
                instance ?: FavoritesRepository(context.applicationContext).also { instance = it }
            }
    }
}
