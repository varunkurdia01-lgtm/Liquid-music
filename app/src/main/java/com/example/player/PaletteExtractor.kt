package com.example.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.ui.theme.ArcticCyan
import com.example.ui.theme.CrystalIce
import com.example.ui.theme.DeepIndigoAurora
import com.example.ui.theme.LiquidDynamicPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PaletteExtractor(
    private val context: Context,
    private val imageLoader: ImageLoader
) {

    suspend fun extractPalette(artworkUrl: String): LiquidDynamicPalette = withContext(Dispatchers.IO) {
        if (artworkUrl.isBlank()) {
            return@withContext defaultAuroraPalette()
        }
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(artworkUrl)
                .allowHardware(false)
                .size(180)
                .build()

            val result = imageLoader.execute(request) as? SuccessResult
            val bitmap: Bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
                ?: return@withContext defaultAuroraPalette()

            val palette = Palette.from(bitmap).generate()
            val vibrantInt = palette.getLightVibrantColor(
                palette.getVibrantColor(CrystalIce.toArgb())
            )
            val secondaryInt = palette.getVibrantColor(
                palette.getLightMutedColor(ArcticCyan.toArgb())
            )
            val darkAuroraInt = palette.getDarkVibrantColor(
                palette.getDarkMutedColor(DeepIndigoAurora.toArgb())
            )

            LiquidDynamicPalette(
                primaryGlow = ensureLuminous(Color(vibrantInt), minLightness = 0.72f),
                secondaryGlow = ensureLuminous(Color(secondaryInt), minLightness = 0.62f),
                deepAurora = ensureAuroraDepth(Color(darkAuroraInt))
            )
        }.getOrElse { defaultAuroraPalette() }
    }

    fun defaultAuroraPalette(): LiquidDynamicPalette = LiquidDynamicPalette(
        primaryGlow = CrystalIce,
        secondaryGlow = ArcticCyan,
        deepAurora = DeepIndigoAurora
    )

    private fun ensureLuminous(color: Color, minLightness: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[1] = hsl[1].coerceIn(0.45f, 0.92f)
        hsl[2] = hsl[2].coerceAtLeast(minLightness).coerceAtMost(0.88f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    private fun ensureAuroraDepth(color: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[1] = hsl[1].coerceIn(0.48f, 0.85f)
        hsl[2] = hsl[2].coerceIn(0.28f, 0.44f)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}
