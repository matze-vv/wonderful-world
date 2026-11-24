package de.wonderfulworld.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Ruhig & warm: Papier, Terrakotta, Olive, Ocker.
private val Light = lightColorScheme(
    primary = Color(0xFFA4532F),
    onPrimary = Color(0xFFFFFBF6),
    primaryContainer = Color(0xFFF3DCCB),
    onPrimaryContainer = Color(0xFF3E1A08),
    secondary = Color(0xFF66733F),
    onSecondary = Color(0xFFFFFBF6),
    secondaryContainer = Color(0xFFE3E6CC),
    onSecondaryContainer = Color(0xFF232A0C),
    tertiary = Color(0xFFB08423),
    onTertiary = Color(0xFFFFFBF6),
    tertiaryContainer = Color(0xFFF5E5BE),
    onTertiaryContainer = Color(0xFF3A2A00),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF2F2A24),
    surface = Color(0xFFF6F1E7),
    onSurface = Color(0xFF2F2A24),
    surfaceVariant = Color(0xFFEAE2D3),
    onSurfaceVariant = Color(0xFF6A6155),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFFCF8F1),
    surfaceContainer = Color(0xFFF2EBDF),
    surfaceContainerHigh = Color(0xFFEDE5D8),
    surfaceContainerHighest = Color(0xFFE7DED0),
    outline = Color(0xFFB4A894),
    outlineVariant = Color(0xFFDDD3C2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE0A07E),
    onPrimary = Color(0xFF4A1E07),
    primaryContainer = Color(0xFF6B3519),
    onPrimaryContainer = Color(0xFFF7DCCB),
    secondary = Color(0xFFBCC793),
    onSecondary = Color(0xFF2B330E),
    secondaryContainer = Color(0xFF424C22),
    onSecondaryContainer = Color(0xFFE3E6CC),
    tertiary = Color(0xFFE2C06E),
    onTertiary = Color(0xFF3D2D00),
    tertiaryContainer = Color(0xFF5A4511),
    onTertiaryContainer = Color(0xFFF5E5BE),
    background = Color(0xFF1D1A16),
    onBackground = Color(0xFFEDE4D6),
    surface = Color(0xFF1D1A16),
    onSurface = Color(0xFFEDE4D6),
    surfaceVariant = Color(0xFF3A342C),
    onSurfaceVariant = Color(0xFFC9BDAB),
    surfaceContainerLowest = Color(0xFF171410),
    surfaceContainerLow = Color(0xFF25211C),
    surfaceContainer = Color(0xFF2A251F),
    surfaceContainerHigh = Color(0xFF332D26),
    surfaceContainerHighest = Color(0xFF3D362E),
    outline = Color(0xFF8C8170),
    outlineVariant = Color(0xFF4A433A),
)

private val Serif = FontFamily.Serif

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = Serif),
        headlineLarge = headlineLarge.copy(fontFamily = Serif),
        headlineMedium = headlineMedium.copy(fontFamily = Serif),
        headlineSmall = headlineSmall.copy(fontFamily = Serif),
        titleLarge = titleLarge.copy(fontFamily = Serif),
        titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun WonderfulWorldTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
