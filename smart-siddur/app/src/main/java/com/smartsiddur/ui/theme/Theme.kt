package com.smartsiddur.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.smartsiddur.R

private val Light = lightColorScheme(
    primary = Color(0xFF1F3A5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3F7),
    onPrimaryContainer = Color(0xFF0A1F38),
    secondary = Color(0xFF8A6A1F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E2B5),
    onSecondaryContainer = Color(0xFF2C2000),
    tertiary = Color(0xFF5E5A8F),
    tertiaryContainer = Color(0xFFE3DFFF),
    onTertiaryContainer = Color(0xFF1A1645),
    error = Color(0xFFA33A2C),
    errorContainer = Color(0xFFF9DAD4),
    onErrorContainer = Color(0xFF3E0A03),
    background = Color(0xFFFBF5EA),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFBF5EA),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEDE3CF),
    onSurfaceVariant = Color(0xFF5B5243),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFF8F1E4),
    surfaceContainer = Color(0xFFF3ECDD),
    surfaceContainerHigh = Color(0xFFEDE5D4),
    surfaceContainerHighest = Color(0xFFE7DFCD),
    outline = Color(0xFF8D826F),
    outlineVariant = Color(0xFFD5CAB4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9C7F0),
    onPrimary = Color(0xFF0D2A4A),
    primaryContainer = Color(0xFF26446B),
    onPrimaryContainer = Color(0xFFD6E3F7),
    secondary = Color(0xFFE2C474),
    onSecondary = Color(0xFF3E2E00),
    secondaryContainer = Color(0xFF4F3D0B),
    onSecondaryContainer = Color(0xFFF3E2B5),
    tertiary = Color(0xFFC6C1FF),
    tertiaryContainer = Color(0xFF45417A),
    onTertiaryContainer = Color(0xFFE3DFFF),
    error = Color(0xFFFFB4A8),
    errorContainer = Color(0xFF7A2418),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFE9E1D4),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFE9E1D4),
    surfaceVariant = Color(0xFF4C4639),
    onSurfaceVariant = Color(0xFFCFC5B1),
    surfaceContainerLowest = Color(0xFF100E0B),
    surfaceContainerLow = Color(0xFF1C1A16),
    surfaceContainer = Color(0xFF221F1A),
    surfaceContainerHigh = Color(0xFF2C2924),
    surfaceContainerHighest = Color(0xFF37332D),
    outline = Color(0xFF989080),
    outlineVariant = Color(0xFF4C4639),
)

/** Noto Serif Hebrew (bundled), used throughout the app. */
val SiddurSerif = FontFamily(
    Font(R.font.noto_serif_hebrew_regular, FontWeight.Normal),
    Font(R.font.noto_serif_hebrew_bold, FontWeight.Bold),
)

private val SerifTypography = Typography().let { t ->
    fun TextStyle.serif() = copy(fontFamily = SiddurSerif)
    Typography(
        displayLarge = t.displayLarge.serif(), displayMedium = t.displayMedium.serif(), displaySmall = t.displaySmall.serif(),
        headlineLarge = t.headlineLarge.serif(), headlineMedium = t.headlineMedium.serif(), headlineSmall = t.headlineSmall.serif(),
        titleLarge = t.titleLarge.serif(), titleMedium = t.titleMedium.serif(), titleSmall = t.titleSmall.serif(),
        bodyLarge = t.bodyLarge.serif(), bodyMedium = t.bodyMedium.serif(), bodySmall = t.bodySmall.serif(),
        labelLarge = t.labelLarge.serif(), labelMedium = t.labelMedium.serif(), labelSmall = t.labelSmall.serif(),
    )
}

@Composable
fun SiddurTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = SerifTypography,
        content = content,
    )
}
