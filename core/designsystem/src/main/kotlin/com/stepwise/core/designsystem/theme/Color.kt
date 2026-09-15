package com.stepwise.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Provisional seed palette — DEV-003 (Design System Foundation) needs real, usable
// color tokens to build a working theme against, but no brand identity has been
// decided in PRODUCT_CANON.md. This is a placeholder palette, not a locked brand
// decision: changing it means editing these two color schemes, not any screen.
// A calm indigo/teal pairing was chosen to read as "focus and forward progress"
// without asserting a specific brand identity.

private val Indigo40 = Color(0xFF3D5AFE)
private val Indigo80 = Color(0xFFBEC5FF)
private val Indigo20 = Color(0xFF00227A)
private val Indigo90 = Color(0xFFE0E0FF)

private val Teal40 = Color(0xFF00695C)
private val Teal80 = Color(0xFF4FD8C4)
private val Teal20 = Color(0xFF00201B)
private val Teal90 = Color(0xFFA6F2E4)

private val Amber40 = Color(0xFF8B5000)
private val Amber80 = Color(0xFFFFB870)
private val Amber20 = Color(0xFF2E1500)
private val Amber90 = Color(0xFFFFDDB8)

private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red20 = Color(0xFF690005)
private val Red90 = Color(0xFFFFDAD6)

private val Neutral10 = Color(0xFF1B1B1F)
private val Neutral20 = Color(0xFF303034)
private val Neutral90 = Color(0xFFE4E2E6)
private val Neutral95 = Color(0xFFF2F0F4)
private val Neutral99 = Color(0xFFFDFBFF)

// Dark-scheme-only "container" tones one step darker than their Neutral40
// equivalent would be — Material3's tonal system has no single named stop for
// these, so they're their own named constants rather than inline literals.
private val IndigoContainerDark = Color(0xFF1A3AA8)
private val TealContainerDark = Color(0xFF00504A)
private val AmberContainerDark = Color(0xFF6B3C00)
private val RedContainerDark = Color(0xFF93000A)

internal val StepwiseLightColorScheme =
    lightColorScheme(
        primary = Indigo40,
        onPrimary = Color.White,
        primaryContainer = Indigo90,
        onPrimaryContainer = Indigo20,
        secondary = Teal40,
        onSecondary = Color.White,
        secondaryContainer = Teal90,
        onSecondaryContainer = Teal20,
        tertiary = Amber40,
        onTertiary = Color.White,
        tertiaryContainer = Amber90,
        onTertiaryContainer = Amber20,
        error = Red40,
        onError = Color.White,
        errorContainer = Red90,
        onErrorContainer = Red20,
        background = Neutral99,
        onBackground = Neutral10,
        surface = Neutral99,
        onSurface = Neutral10,
        surfaceVariant = Neutral95,
        onSurfaceVariant = Neutral20,
    )

internal val StepwiseDarkColorScheme =
    darkColorScheme(
        primary = Indigo80,
        onPrimary = Indigo20,
        primaryContainer = IndigoContainerDark,
        onPrimaryContainer = Indigo90,
        secondary = Teal80,
        onSecondary = Teal20,
        secondaryContainer = TealContainerDark,
        onSecondaryContainer = Teal90,
        tertiary = Amber80,
        onTertiary = Amber20,
        tertiaryContainer = AmberContainerDark,
        onTertiaryContainer = Amber90,
        error = Red80,
        onError = Red20,
        errorContainer = RedContainerDark,
        onErrorContainer = Red90,
        background = Neutral10,
        onBackground = Neutral90,
        surface = Neutral10,
        onSurface = Neutral90,
        surfaceVariant = Neutral20,
        onSurfaceVariant = Neutral90,
    )
