package com.panda.drivetv.ui.tv

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Sustituto de androidx.tv (etapa 4 de la guía).
 *
 * Las librerías androidx.tv:tv-material/tv-foundation exigen compileSdk 35 y
 * el aapt2 de Termux (v2.19) no puede leer el android.jar de API 35, así que
 * la UI de TV se construye con Compose material3 estándar más este foco
 * visual propio: borde blanco + ligera escala cuando el D-pad enfoca.
 */
@Composable
fun Modifier.tvFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = if (focused) 1.04f else 1f
            scaleY = if (focused) 1.04f else 1f
        }
        .border(
            width = if (focused) 4.dp else 1.dp,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp)
        )
}

/** Botón con foco visible para D-pad. */
@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.tvFocus(),
        content = content
    )
}

/** Tarjeta clicable con foco visible para D-pad. */
@Composable
fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.tvFocus(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        content = content
    )
}
