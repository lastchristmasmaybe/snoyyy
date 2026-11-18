package com.echo.recorder

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette sampled from the user's own Echo design.
val Cream = Color(0xFFF2EEDD)
val CreamCard = Color(0xFFFBF8EC)
val NavSurface = Color(0xFFFDF9EC)
val NavPill = Color(0xFFE1E6D3)
val Ink = Color(0xFF1E3C2B)
val Hero = Color(0xFF224A40)
val TextDim = Color(0xFF5E6B5A)
val Hairline = Color(0xFFE0DCC4)
val Olive = Color(0xFF98AB3F)
val Plum = Color(0xFF4A3470)
val RecRed = Color(0xFFE5263F)
val RecRedSoft = Color(0xFFFF6B7D)
val Sage = Color(0xFFA7C3AC)
// kept so older call-sites still compile
val Surface1 = CreamCard
val Surface2 = NavPill
val Violet = Plum

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RecRed, onPrimary = Color.White,
            secondary = Plum, background = Cream, surface = CreamCard,
            onBackground = Ink, onSurface = Ink,
            surfaceVariant = NavPill, onSurfaceVariant = TextDim,
        ),
        content = content,
    )
}
