package com.panda.drivetv.player

import android.content.Context
import androidx.core.content.edit

/**
 * Guarda fileId → posición (ms) para reanudar (etapa 6, §6.8 de la guía).
 * Regla: se guarda el fileId + posición, NUNCA la URL autenticada.
 */
class ResumeManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(fileId: String, positionMs: Long, durationMs: Long = 0L) {
        // No guardar si está al inicio o a menos de 10 s del final.
        if (positionMs < 10_000) return
        if (durationMs > 0 && durationMs - positionMs < 10_000) {
            prefs.edit { remove(key(fileId)) }
            return
        }
        prefs.edit {
            putLong(key(fileId), positionMs)
            putLong(KEY_UPDATED, System.currentTimeMillis())
        }
    }

    /** Devuelve la posición guardada o 0 si no hay / es muy corta. */
    fun get(fileId: String): Long =
        prefs.getLong(key(fileId), 0L).takeIf { it >= 10_000 } ?: 0L

    fun clearAll() {
        prefs.edit { clear() }
    }

    private fun key(fileId: String) = "resume_$fileId"

    companion object {
        private const val PREFS = "drivetv_resume"
        private const val KEY_UPDATED = "updated_at"
    }
}
