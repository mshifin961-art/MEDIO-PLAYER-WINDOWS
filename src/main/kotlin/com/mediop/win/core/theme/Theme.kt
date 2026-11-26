package com.mediop.win.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mediop.win.core.model.ThemeMode

val PureBlack = Color(0xFF000000)

private val CinematicDarkBackground = Color(0xFF0C0D11)
private val CinematicDarkSurface = Color(0xFF15171E)
private val CinematicDarkSurfaceVariant = Color(0xFF1E212B)
private val CinematicDarkSurfaceHighlight = Color(0xFF282C3A)

object ThemeColorState {
    var isAmoled: Boolean = false
}

val DarkBackground: Color
    get() = if (ThemeColorState.isAmoled) PureBlack else CinematicDarkBackground

val DarkSurface: Color
    get() = if (ThemeColorState.isAmoled) Color(0xFF080808) else CinematicDarkSurface

val DarkSurfaceVariant: Color
    get() = if (ThemeColorState.isAmoled) Color(0xFF101010) else CinematicDarkSurfaceVariant

val DarkSurfaceHighlight: Color
    get() = if (ThemeColorState.isAmoled) Color(0xFF181818) else CinematicDarkSurfaceHighlight

val PrimaryCyan = Color(0xFF00E5FF)
val PrimaryBlue = Color(0xFF2979FF)
val PrimaryAccentGlow = Color(0x3300E5FF)

val SecondaryEmerald = Color(0xFF00E676)
val AccentCoral = Color(0xFFFF5252)
val AccentAmber = Color(0xFFFFD600)

val TextPrimary = Color(0xFFF0F2F8)
val TextSecondary = Color(0xFF9DA3B4)
val TextTertiary = Color(0xFF656B7C)

val GlassBorder = Color(0x1FFFFFFF)
val GlassOverlay = Color(0x0DFFFFFF)
val PlayerOverlayBackground = Color(0xCC000000)

val MedioTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
        color = TextTertiary
    )
)

private fun cinematicDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = PureBlack,
    secondary = SecondaryEmerald,
    onSecondary = PureBlack,
    tertiary = PrimaryBlue,
    background = CinematicDarkBackground,
    onBackground = TextPrimary,
    surface = CinematicDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = CinematicDarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder
)

private fun amoledColorScheme(): ColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = PureBlack,
    secondary = SecondaryEmerald,
    onSecondary = PureBlack,
    tertiary = PrimaryBlue,
    background = PureBlack,
    onBackground = TextPrimary,
    surface = Color(0xFF080808),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF101010),
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder
)

@Composable
fun MedioPlayerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    ThemeColorState.isAmoled = themeMode == ThemeMode.AMOLED
    val colorScheme = if (themeMode == ThemeMode.AMOLED) {
        amoledColorScheme()
    } else {
        cinematicDarkColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MedioTypography,
        content = content
    )
}
