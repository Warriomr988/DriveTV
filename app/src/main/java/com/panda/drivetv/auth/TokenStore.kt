package com.panda.drivetv.auth

/**
 * Frontera de almacenamiento del token (etapa 2 de la guía).
 * La implementación real vive en [PrefsTokenStore]; la UI y el reproductor
 * solo conocen esta interfaz.
 */
interface TokenStore {
    fun getAccessToken(): String?
    fun saveAccessToken(token: String)
    fun clear()
}
