package com.whatsapprepeater.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.whatsapprepeater.core.model.ThemeMode

private val DarkColorPalette = darkColorScheme(
  primary = DarkAppPalette.whatsAppGreen,
  onPrimary = DarkAppPalette.onWhatsAppGreen,
  primaryContainer = DarkAppPalette.whatsAppGreenContainer,
  onPrimaryContainer = DarkAppPalette.whatsAppBright,
  secondary = DarkAppPalette.whatsAppEmerald,
  onSecondary = DarkAppPalette.textPrimary,
  secondaryContainer = DarkAppPalette.cardSurfaceElevated,
  onSecondaryContainer = DarkAppPalette.textPrimary,
  tertiary = DarkAppPalette.warningAmber,
  background = DarkAppPalette.background,
  onBackground = DarkAppPalette.textPrimary,
  surface = DarkAppPalette.surface,
  onSurface = DarkAppPalette.textPrimary,
  surfaceVariant = DarkAppPalette.cardSurface,
  onSurfaceVariant = DarkAppPalette.textSecondary,
  outline = DarkAppPalette.outlineSubtle,
  error = DarkAppPalette.dangerRed,
  onError = DarkAppPalette.textPrimary,
  errorContainer = DarkAppPalette.dangerContainer,
  onErrorContainer = DarkAppPalette.dangerCoral,
)

private val LightColorPalette = lightColorScheme(
  primary = LightAppPalette.whatsAppGreen,
  onPrimary = LightAppPalette.onWhatsAppGreen,
  primaryContainer = LightAppPalette.whatsAppGreenContainer,
  onPrimaryContainer = LightAppPalette.whatsAppBright,
  secondary = LightAppPalette.whatsAppEmerald,
  onSecondary = LightAppPalette.onWhatsAppGreen,
  secondaryContainer = LightAppPalette.cardSurfaceElevated,
  onSecondaryContainer = LightAppPalette.textPrimary,
  tertiary = LightAppPalette.warningAmber,
  background = LightAppPalette.background,
  onBackground = LightAppPalette.textPrimary,
  surface = LightAppPalette.surface,
  onSurface = LightAppPalette.textPrimary,
  surfaceVariant = LightAppPalette.cardSurface,
  onSurfaceVariant = LightAppPalette.textSecondary,
  outline = LightAppPalette.outlineSubtle,
  error = LightAppPalette.dangerRed,
  onError = LightAppPalette.onWhatsAppGreen,
  errorContainer = LightAppPalette.dangerContainer,
  onErrorContainer = LightAppPalette.dangerCoral,
)

private val AppTypography = Typography(
  headlineLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
  ),
  headlineMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 32.sp,
  ),
  headlineSmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 28.sp,
  ),
  titleLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.5.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.25.sp,
  ),
  bodySmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp,
  ),
)

@Composable
fun WhatsAppRepeaterTheme(
  themeMode: ThemeMode = ThemeMode.DARK,
  content: @Composable () -> Unit,
) {
  val useDark = when (themeMode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
  }
  val appPalette = if (useDark) DarkAppPalette else LightAppPalette
  val materialScheme = if (useDark) DarkColorPalette else LightColorPalette

  CompositionLocalProvider(LocalAppPalette provides appPalette) {
    MaterialTheme(
      colorScheme = materialScheme,
      typography = AppTypography,
      content = content,
    )
  }
}
