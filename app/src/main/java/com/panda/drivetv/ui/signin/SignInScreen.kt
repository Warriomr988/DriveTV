package com.panda.drivetv.ui.signin

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.panda.drivetv.BuildConfig
import com.panda.drivetv.DriveTVApp
import com.panda.drivetv.R
import com.panda.drivetv.auth.AuthManager
import com.panda.drivetv.ui.tv.TvButton
import kotlinx.coroutines.launch

/**
 * Pantalla de login (etapa 2 de la guía).
 *
 * Dos caminos:
 * 1. "Continuar con Google": Google Sign-In (scope drive.readonly) y el token
 *    se obtiene con GoogleAuthUtil. No requiere client secret en la app.
 * 2. "Token manual": pegar un access token (p. ej. de OAuth Playground) para
 *    probar sin configurar Google Cloud.
 */
@Composable
fun SignInScreen(
    app: DriveTVApp,
    onSignedIn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var manualToken by remember { mutableStateOf("") }
    var pendingAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    lateinit var fetchToken: (GoogleSignInAccount) -> Unit

    val recoveryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingAccount?.let { fetchToken(it) }
        } else {
            error = "Autorización de Google cancelada."
        }
    }

    fetchToken = { account ->
        scope.launch {
            loading = true
            error = null
            val result = app.authManager.fetchTokenWithGoogle(account)
            loading = false
            result
                .onSuccess { onSignedIn() }
                .onFailure { e ->
                    if (e is UserRecoverableAuthException) {
                        // Google pide consentimiento extra: se lanza y se reintenta.
                        pendingAccount = account
                        val intent = e.intent
                        if (intent != null) {
                            recoveryLauncher.launch(intent)
                        } else {
                            error = "Google pidió autorización adicional pero no se pudo abrir la pantalla de permiso. Usa el token manual."
                        }
                    } else {
                        error = e.message ?: "No se pudo obtener el token."
                    }
                }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            fetchToken(account)
        } catch (e: ApiException) {
            error = if (e.statusCode == 12501) {
                "Inicio de sesión cancelado."
            } else {
                "Google Sign-In falló (${e.statusCode})."
            }
        }
    }

    fun startGoogleSignIn() {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(AuthManager.SCOPE_DRIVE_READONLY))
        if (BuildConfig.WEB_CLIENT_ID.isNotBlank()) {
            builder.requestIdToken(BuildConfig.WEB_CLIENT_ID)
        }
        val client = GoogleSignIn.getClient(context, builder.build())
        signInLauncher.launch(client.signInIntent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("DriveTV", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.signin_subtitle),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(32.dp))

        if (loading) {
            CircularProgressIndicator()
        } else {
            TvButton(onClick = ::startGoogleSignIn) {
                Text(stringResource(R.string.signin_google_button))
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.signin_manual_hint),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = manualToken,
            onValueChange = { manualToken = it },
            placeholder = { Text(stringResource(R.string.signin_manual_placeholder)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        Spacer(Modifier.height(8.dp))
        TvButton(
            onClick = {
                val t = manualToken.trim()
                if (t.length < 20) {
                    error = "Ese token parece incompleto."
                } else {
                    app.authManager.saveManualToken(t)
                    onSignedIn()
                }
            }
        ) {
            Text(stringResource(R.string.signin_manual_button))
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
