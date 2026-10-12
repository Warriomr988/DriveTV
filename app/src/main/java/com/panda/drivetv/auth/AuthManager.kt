package com.panda.drivetv.auth

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestiona la sesión de Google (etapa 2 de la guía).
 *
 * - El token se obtiene con GoogleAuthUtil contra la cuenta elegida en el
 *   Google Sign-In (scope drive.readonly). GoogleAuthUtil refresca el token
 *   internamente cuando caduca.
 * - Como alternativa de prueba existe el "token manual" (OAuth Playground),
 *   útil sin configurar nada en Google Cloud.
 * - El token NUNCA se pasa por Intent ni se escribe en logs.
 */
class AuthManager(
    private val context: Context,
    private val tokenStore: TokenStore
) {

    fun isSignedIn(): Boolean =
        !tokenStore.getAccessToken().isNullOrBlank()

    fun accessToken(): String? =
        tokenStore.getAccessToken()

    /** Guarda un token pegado a mano (modo prueba). */
    fun saveManualToken(token: String) {
        tokenStore.saveAccessToken(token)
    }

    /**
     * Intercambia la cuenta de Google por un access token de Drive.
     * Debe llamarse desde una corrutina (hace red).
     */
    suspend fun fetchTokenWithGoogle(account: GoogleSignInAccount): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val gAccount = account.account
                    ?: throw IllegalStateException("La cuenta de Google no trae identificador.")
                // "oauth2:" + scope → GoogleAuthUtil devuelve un access token fresco.
                val token = GoogleAuthUtil.getToken(context, gAccount, "oauth2:$SCOPE_DRIVE_READONLY")
                tokenStore.saveAccessToken(token)
                token
            }
        }

    /**
     * Invalida el token en caché para forzar un refresh en el próximo uso.
     * Se llama cuando Drive o el reproductor devuelven 401.
     */
    fun invalidateToken() {
        tokenStore.clear()
    }

    fun signOut() {
        tokenStore.clear()
        // Cierra también la sesión de Google en el dispositivo.
        runCatching {
            GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
        }
    }

    companion object {
        const val SCOPE_DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
    }
}
