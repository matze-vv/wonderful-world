package de.wonderfulworld.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import de.wonderfulworld.app.R
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
    background = Color(0xFFF8F4EE),
    onBackground = Color(0xFF2F2A24),
    surface = Color(0xFFF8F4EE),
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

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: FontWeight) = Font(
    R.font.figtree, weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Figtree – serifenlos, freundlich. Variable Schrift, alle Stärken aus einer Datei. */
val Figtree = FontFamily(
    figtree(FontWeight.Normal), figtree(FontWeight.Medium), figtree(FontWeight.SemiBold),
    figtree(FontWeight.Bold), figtree(FontWeight.ExtraBold),
)

private val AppTypography = Typography().run {
    fun TextStyle.f(w: FontWeight? = null) = copy(fontFamily = Figtree, fontWeight = w ?: fontWeight)
    copy(
        displayLarge = displayLarge.f(FontWeight.ExtraBold),
        displayMedium = displayMedium.f(FontWeight.ExtraBold),
        displaySmall = displaySmall.f(FontWeight.ExtraBold),
        headlineLarge = headlineLarge.f(FontWeight.ExtraBold),
        headlineMedium = headlineMedium.copy(fontFamily = Figtree, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp),
        headlineSmall = headlineSmall.f(FontWeight.ExtraBold),
        titleLarge = titleLarge.f(FontWeight.ExtraBold),
        titleMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleSmall = titleSmall.f(FontWeight.Bold),
        bodyLarge = bodyLarge.f(FontWeight.Medium),
        bodyMedium = bodyMedium.f(),
        bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(FontWeight.Bold),
        labelMedium = labelMedium.f(FontWeight.Bold),
        labelSmall = labelSmall.f(FontWeight.Bold),
    )
}

/** Farben der drei Bereiche: Rituale rötlich, Missionen gelblich, Tagebuch bläulich. */
@Immutable
data class AreaColors(
    val container: Color,
    val content: Color,
    val muted: Color,
    val accent: Color,
    val track: Color,
    val background: Color,
)

@Immutable
data class WwColors(val ritual: AreaColors, val mission: AreaColors, val journal: AreaColors)

private val LightAreas = WwColors(
    ritual = AreaColors(Color(0xFFF4D3C8), Color(0xFF4A1C10), Color(0xFF7A4536), Color(0xFFC4472D), Color(0xFFEBB6A5), Color(0xFFFBF1ED)),
    mission = AreaColors(Color(0xFFF8E7B0), Color(0xFF3D2C00), Color(0xFF7A6320), Color(0xFFC99A06), Color(0xFFEFD88A), Color(0xFFFDF6E1)),
    journal = AreaColors(Color(0xFFD6E5F2), Color(0xFF1E3D5A), Color(0xFF4A6683), Color(0xFF3E6A93), Color(0xFFB8CFE4), Color(0xFFEEF4FA)),
)

private val DarkAreas = WwColors(
    ritual = AreaColors(Color(0xFF4A2419), Color(0xFFF8DDD3), Color(0xFFD9A898), Color(0xFFF08A6E), Color(0xFF6B3626), Color(0xFF241713)),
    mission = AreaColors(Color(0xFF453812), Color(0xFFF7E7B5), Color(0xFFD5C084), Color(0xFFE8BE3A), Color(0xFF5E4E1C), Color(0xFF221D0F)),
    journal = AreaColors(Color(0xFF1F3549), Color(0xFFDCEAF6), Color(0xFFA6C0D8), Color(0xFF7FB0DD), Color(0xFF2E4B66), Color(0xFF141E28)),
)

val LocalWwColors = staticCompositionLocalOf { LightAreas }

/** Zugriff: `Ww.colors.ritual.container` usw. */
object Ww {
    val colors: WwColors
        @Composable @ReadOnlyComposable get() = LocalWwColors.current
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun WonderfulWorldTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWwColors provides if (dark) DarkAreas else LightAreas) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
