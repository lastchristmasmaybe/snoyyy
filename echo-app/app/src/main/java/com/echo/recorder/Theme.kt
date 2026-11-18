package com.echo.recorder

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette sampled from the user's own Echo design.
val Cream = Color(0xFFF2EEDD)
val CreamCard = Color(0xFFFBF8EC)
val NavSurface = Color(0xFFFDF9EC)
val NavPill = Color(0xFFE8DCC4)      // Sand Beige
val Ink = Color(0xFF1F3D2B)          // Forest Green
val Hero = Color(0xFF1F3D2B)
val HeroTop = Color(0xFF2A5440)
val TextDim = Color(0xFF5E6B5A)
val Hairline = Color(0xFFE0DCC4)
val Olive = Color(0xFF98AB3F)
val Violet = Color(0xFF8B5CF6)        // Electric Purple (fills)
val Plum = Color(0xFF6A3BD4)          // deeper purple for text on sand (AA)
val RecRed = Color(0xFFE5263F)
val RecRedSoft = Color(0xFFFF6B7D)
val Sage = Color(0xFFA7C3AC)
// kept so older call-sites still compile
val Surface1 = CreamCard
val Surface2 = NavPill

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
