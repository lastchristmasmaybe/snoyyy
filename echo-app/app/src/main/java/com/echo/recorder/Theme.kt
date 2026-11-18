package com.echo.recorder

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0B12)
val Surface1 = Color(0xFF15151F)
val Surface2 = Color(0xFF1E1E2B)
val RecRed = Color(0xFFFF2D46)
val RecRedSoft = Color(0xFFFF7A8A)
val Violet = Color(0xFF8B7CFF)
val TextDim = Color(0xFF9A9AB0)

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RecRed, onPrimary = Color.White,
            secondary = Violet, background = Ink, surface = Surface1,
            onBackground = Color.White, onSurface = Color.White,
            surfaceVariant = Surface2, onSurfaceVariant = TextDim,
        ),
        content = content,
    )
}
