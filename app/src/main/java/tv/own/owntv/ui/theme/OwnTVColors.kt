package tv.own.owntv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * OwnTV's resolved Material 3 color roles for the current theme + accent. Read as `OwnTVTheme.colors`.
 *
 * Exposes the full M3 surface-container tiers and primary/secondary/tertiary roles the MD3 UI needs.
 * A few legacy aliases (`panel`/`card`/`rail`/`textPrimary`/`textSecondary`/`accent`) map onto M3
 * roles so older components keep working.
 */
@Immutable
data class OwnTVColors(
    val isDark: Boolean,
    // Surfaces
    val background: Color,
    val surface: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    // Primary
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // Secondary
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    // Tertiary
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    // Focus / status
    val focusBorder: Color,
    val focusGlow: Color,
    val favorite: Color,
) {
    // Legacy aliases used by existing components.
    val textPrimary: Color get() = onSurface
    val textSecondary: Color get() = onSurfaceVariant
    val panel: Color get() = surfaceContainerLow
    val card: Color get() = surfaceContainerHigh
    val rail: Color get() = surfaceContainer
    val accent: Color get() = primary
}

/** Build the resolved M3 tokens for a theme (dark/light) and accent preset. */
fun ownTvColors(isDark: Boolean, accent: AccentColor): OwnTVColors {
    val primary = accent.primary(isDark)
    return if (isDark) {
        OwnTVColors(
            isDark = true,
            background = DarkBackground,
            surface = DarkSurface,
            surfaceContainerLowest = DarkSurfaceContainerLowest,
            surfaceContainerLow = DarkSurfaceContainerLow,
            surfaceContainer = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceContainerHigh,
            surfaceContainerHighest = DarkSurfaceContainerHighest,
            onSurface = DarkOnSurface,
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant,
            primary = primary,
            onPrimary = accent.onPrimary(true),
            primaryContainer = accent.primaryContainer(true),
            onPrimaryContainer = accent.onPrimaryContainer(true),
            secondary = DarkSecondary,
            onSecondary = DarkOnSecondary,
            secondaryContainer = DarkSecondaryContainer,
            onSecondaryContainer = DarkOnSecondaryContainer,
            tertiary = DarkTertiary,
            onTertiary = DarkOnTertiary,
            tertiaryContainer = DarkTertiaryContainer,
            onTertiaryContainer = DarkOnTertiaryContainer,
            focusBorder = primary,
            focusGlow = primary.copy(alpha = 0.40f),
            favorite = DarkError,
        )
    } else {
        OwnTVColors(
            isDark = false,
            background = LightBackground,
            surface = LightSurface,
            surfaceContainerLowest = LightSurfaceContainerLowest,
            surfaceContainerLow = LightSurfaceContainerLow,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHigh,
            surfaceContainerHighest = LightSurfaceContainerHighest,
            onSurface = LightOnSurface,
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = LightOutline,
            outlineVariant = LightOutlineVariant,
            primary = primary,
            onPrimary = accent.onPrimary(false),
            primaryContainer = accent.primaryContainer(false),
            onPrimaryContainer = accent.onPrimaryContainer(false),
            secondary = LightSecondary,
            onSecondary = LightOnSecondary,
            secondaryContainer = LightSecondaryContainer,
            onSecondaryContainer = LightOnSecondaryContainer,
            tertiary = LightTertiary,
            onTertiary = LightOnTertiary,
            tertiaryContainer = LightTertiaryContainer,
            onTertiaryContainer = LightOnTertiaryContainer,
            focusBorder = primary,
            focusGlow = primary.copy(alpha = 0.28f),
            favorite = LightError,
        )
    }
}

val LocalOwnTVColors = staticCompositionLocalOf { ownTvColors(isDark = true, accent = AccentColor.TEAL) }
