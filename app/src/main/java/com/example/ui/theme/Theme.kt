package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VortexObsidianColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = TelemetryBlue,
    onSecondary = OnTelemetryBlue,
    secondaryContainer = TelemetryBlueContainer,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = BridgeAmber,
    onTertiary = OnBridgeAmber,
    tertiaryContainer = BridgeAmberContainer,
    onTertiaryContainer = BridgeAmber,
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceContainer,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    surfaceContainerHighest = ObsidianSurfaceElevated,
    outline = ObsidianHairline,
    outlineVariant = ObsidianHairline,
    error = CrimsonError,
    onError = OnCrimsonError,
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = CrimsonError
)

private val VortexShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun VortexTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VortexObsidianColorScheme,
        typography = VortexTypography,
        shapes = VortexShapes,
        content = content
    )
}
