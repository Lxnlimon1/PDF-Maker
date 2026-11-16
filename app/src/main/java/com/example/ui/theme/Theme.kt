package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CrimsonPrimary,
    onPrimary = CrimsonOnPrimary,
    primaryContainer = CrimsonContainer,
    onPrimaryContainer = CrimsonOnContainer,
    secondary = CobaltSecondary,
    onSecondary = CobaltOnSecondary,
    secondaryContainer = CobaltContainer,
    onSecondaryContainer = CobaltOnContainer,
    tertiary = EmeraldTertiary,
    onTertiary = EmeraldOnTertiary,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldOnContainer,
    background = StudioPaper,
    onBackground = StudioInk,
    surface = StudioWhite,
    onSurface = StudioInk,
    surfaceVariant = StudioSlateContainer,
    onSurfaceVariant = StudioInkMuted,
    outline = StudioOutline,
    outlineVariant = StudioOutline.copy(alpha = 0.55f)
)

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonDarkPrimary,
    onPrimary = CrimsonDarkOnPrimary,
    primaryContainer = CrimsonDarkContainer,
    onPrimaryContainer = CrimsonDarkOnContainer,
    secondary = CobaltDarkSecondary,
    onSecondary = CobaltOnContainer,
    secondaryContainer = CobaltDarkContainer,
    onSecondaryContainer = CobaltDarkOnContainer,
    tertiary = EmeraldDarkTertiary,
    onTertiary = EmeraldOnContainer,
    tertiaryContainer = EmeraldDarkContainer,
    onTertiaryContainer = EmeraldDarkOnContainer,
    background = MidnightBg,
    onBackground = MidnightOnSurface,
    surface = MidnightSurface,
    onSurface = MidnightOnSurface,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = MidnightOnSurfaceMuted,
    outline = MidnightOutline,
    outlineVariant = MidnightOutline.copy(alpha = 0.55f)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PdfMasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
