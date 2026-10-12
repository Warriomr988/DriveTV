package com.panda.drivetv.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Ajustes propios de la app en SharedPreferences (v0.6.0).
 * Patrón copiado de PrefsTokenStore; misma nota: para producción migrar a
 * EncryptedSharedPreferences si se guardan secretos sensibles.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getTmdbApiKey(): String? =
        prefs.getString(KEY_TMDB, null)?.takeIf { it.isNotBlank() }

    fun setTmdbApiKey(key: String) {
        prefs.edit { putString(KEY_TMDB, key.trim()) }
    }

    /** "Mi lista" de Netflix (v0.7.0): ids de Drive marcados como favoritos. */
    fun getFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    fun isFavorite(driveId: String): Boolean =
        getFavorites().contains(driveId)

    /** Alterna el favorito; devuelve el nuevo estado (true = quedó en la lista). */
    fun toggleFavorite(driveId: String): Boolean {
        val next = getFavorites().toMutableSet()
        val now = if (next.contains(driveId)) {
            next.remove(driveId)
            false
        } else {
            next.add(driveId)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
        return now
    }

    companion object {
        private const val PREFS = "drivetv_settings"
        private const val KEY_TMDB = "tmdb_api_key"
        private const val KEY_FAVORITES = "favorites"
    }
}
