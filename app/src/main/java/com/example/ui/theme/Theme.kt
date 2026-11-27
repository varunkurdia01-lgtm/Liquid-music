package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.GlassQualityTier
import com.example.domain.model.LiquidGlassConfig

@Immutable
data class LiquidDynamicPalette(
    val primaryGlow: Color = CrystalIce,
    val secondaryGlow: Color = ArcticCyan,
    val deepAurora: Color = DeepIndigoAurora
)

val LocalLiquidPalette = compositionLocalOf { LiquidDynamicPalette() }
val LocalGlassQuality = compositionLocalOf { GlassQualityTier.ULTRA_AGSL }
val LocalLiquidGlassConfig = compositionLocalOf { LiquidGlassConfig() }

private val LiquidShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun LiquidMusicTheme(
    targetPalette: LiquidDynamicPalette = LiquidDynamicPalette(),
    glassQualityTier: GlassQualityTier = GlassQualityTier.ULTRA_AGSL,
    glassConfig: LiquidGlassConfig = LiquidGlassConfig(),
    content: @Composable () -> Unit
) {
    val animPrimary by animateColorAsState(
        targetValue = targetPalette.primaryGlow,
        animationSpec = tween(durationMillis = 350),
        label = "palettePrimary"
    )
    val animSecondary by animateColorAsState(
        targetValue = targetPalette.secondaryGlow,
        animationSpec = tween(durationMillis = 350),
        label = "paletteSecondary"
    )
    val animAurora by animateColorAsState(
        targetValue = targetPalette.deepAurora,
        animationSpec = tween(durationMillis = 350),
        label = "paletteAurora"
    )

    val animatedPalette = remember(animPrimary, animSecondary, animAurora) {
        LiquidDynamicPalette(
            primaryGlow = animPrimary,
            secondaryGlow = animSecondary,
            deepAurora = animAurora
        )
    }

    val colorScheme = remember(animPrimary, animSecondary, animAurora) {
        darkColorScheme(
            primary = animPrimary,
            onPrimary = ObsidianBlack,
            primaryContainer = animAurora.copy(alpha = 0.45f),
            onPrimaryContainer = FrostWhite,
            secondary = animSecondary,
            onSecondary = ObsidianBlack,
            secondaryContainer = SmokedGlassElevated,
            onSecondaryContainer = FrostWhite,
            tertiary = LuminousRose,
            onTertiary = FrostWhite,
            background = ObsidianBlack,
            onBackground = FrostWhite,
            surface = DeepVoid,
            onSurface = FrostWhite,
            surfaceVariant = SmokedGlassSurface,
            onSurfaceVariant = MistSecondary,
            outline = GlassRimBright,
            outlineVariant = GlassRimSubtle,
            error = LuminousRose,
            onError = FrostWhite
        )
    }

    CompositionLocalProvider(
        LocalLiquidPalette provides animatedPalette,
        LocalGlassQuality provides glassQualityTier,
        LocalLiquidGlassConfig provides glassConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LiquidTypography,
            shapes = LiquidShapes,
            content = content
        )
    }
}
