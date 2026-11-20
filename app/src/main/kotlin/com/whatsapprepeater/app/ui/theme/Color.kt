package com.whatsapprepeater.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColorPalette(
  val background: Color,
  val surface: Color,
  val surfaceLow: Color,
  val cardSurface: Color,
  val cardSurfaceElevated: Color,
  val cardSurfaceHighest: Color,
  val outlineSubtle: Color,
  val outlineVariant: Color,
  val whatsAppGreen: Color,
  val whatsAppBright: Color,
  val whatsAppEmerald: Color,
  val whatsAppGreenContainer: Color,
  val onWhatsAppGreen: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val warningAmber: Color,
  val warningContainer: Color,
  val dangerRed: Color,
  val dangerCoral: Color,
  val dangerContainer: Color,
)

// Paleta Oscura OLED (Material Messaging Dark)
val DarkAppPalette = AppColorPalette(
  background = Color(0xFF101412),
  surface = Color(0xFF181C1A),
  surfaceLow = Color(0xFF1A211D),
  cardSurface = Color(0xFF1C201E),
  cardSurfaceElevated = Color(0xFF222B25),
  cardSurfaceHighest = Color(0xFF323633),
  outlineSubtle = Color(0xFF28342D),
  outlineVariant = Color(0xFF3C4A3D),
  whatsAppGreen = Color(0xFF25D366),
  whatsAppBright = Color(0xFF4FF07F),
  whatsAppEmerald = Color(0xFF128C50),
  whatsAppGreenContainer = Color(0xFF0E3B23),
  onWhatsAppGreen = Color(0xFF003919),
  textPrimary = Color(0xFFFFFFFF),
  textSecondary = Color(0xFFAAB5AE),
  warningAmber = Color(0xFFF59E0B),
  warningContainer = Color(0xFF352615),
  dangerRed = Color(0xFFEF4444),
  dangerCoral = Color(0xFFFFB4AB),
  dangerContainer = Color(0xFF381E1E),
)

// Paleta Clara Profesional (Material Messaging Light, contraste AA/AAA)
val LightAppPalette = AppColorPalette(
  background = Color(0xFFF4F7F5),
  surface = Color(0xFFFFFFFF),
  surfaceLow = Color(0xFFEDF3EF),
  cardSurface = Color(0xFFFFFFFF),
  cardSurfaceElevated = Color(0xFFE7F0EA),
  cardSurfaceHighest = Color(0xFFD9E6DE),
  outlineSubtle = Color(0xFFCCDBD1),
  outlineVariant = Color(0xFFB3C7BA),
  whatsAppGreen = Color(0xFF0E7A45),
  whatsAppBright = Color(0xFF0B6237),
  whatsAppEmerald = Color(0xFF0A6638),
  whatsAppGreenContainer = Color(0xFFD6F3E2),
  onWhatsAppGreen = Color(0xFFFFFFFF),
  textPrimary = Color(0xFF101B15),
  textSecondary = Color(0xFF465B4F),
  warningAmber = Color(0xFFB45309),
  warningContainer = Color(0xFFFEF3C7),
  dangerRed = Color(0xFFC81E1E),
  dangerCoral = Color(0xFFB91C1C),
  dangerContainer = Color(0xFFFEE2E2),
)

val LocalAppPalette = staticCompositionLocalOf { DarkAppPalette }

val BackgroundBlack: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.background

val SurfaceDark: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.surface

val SurfaceLow: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.surfaceLow

val CardSurface: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.cardSurface

val CardSurfaceElevated: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.cardSurfaceElevated

val CardSurfaceHighest: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.cardSurfaceHighest

val OutlineSubtle: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.outlineSubtle

val OutlineVariant: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.outlineVariant

val WhatsAppGreen: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.whatsAppGreen

val WhatsAppBright: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.whatsAppBright

val WhatsAppEmerald: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.whatsAppEmerald

val WhatsAppGreenContainer: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.whatsAppGreenContainer

val OnWhatsAppGreen: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onWhatsAppGreen

val TextPrimary: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.textPrimary

val TextSecondary: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.textSecondary

val WarningAmber: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.warningAmber

val WarningContainer: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.warningContainer

val DangerRed: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.dangerRed

val DangerCoral: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.dangerCoral

val DangerContainer: Color
  @Composable @ReadOnlyComposable get() = LocalAppPalette.current.dangerContainer
