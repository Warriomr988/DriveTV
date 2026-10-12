package com.panda.drivetv.auth

import android.content.Context
import androidx.core.content.edit

/**
 * TokenStore basado en SharedPreferences privadas de la app.
 *
 * NOTA de seguridad: para producción conviene migrar a
 * EncryptedSharedPreferences (androidx.security:security-crypto). Se deja
 * como mejora documentada para no añadir peso en la v0.1.0.
 */
class PrefsTokenStore(context: Context) : TokenStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun getAccessToken(): String? =
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun saveAccessToken(token: String) {
        prefs.edit { putString(KEY_TOKEN, token.trim()) }
    }

    override fun clear() {
        prefs.edit { remove(KEY_TOKEN) }
    }

    companion object {
        private const val PREFS = "drivetv_auth"
        private const val KEY_TOKEN = "access_token"
    }
}
