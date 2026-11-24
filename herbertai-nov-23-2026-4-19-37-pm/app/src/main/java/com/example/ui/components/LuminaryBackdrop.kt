package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.ui.theme.BackgroundStyle

@Composable
fun LuminaryBackdrop(
    backgroundStyle: BackgroundStyle,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.background
    val isDark = bgColor.luminance() < 0.5f
    val primaryGlow = MaterialTheme.colorScheme.primary
    val tertiaryGlow = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        when (backgroundStyle) {
            BackgroundStyle.AMBIENT -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tertiaryGlow.copy(alpha = if (isDark) 0.14f else 0.10f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.85f, h * 0.12f),
                            radius = maxOf(w, h) * 0.65f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryGlow.copy(alpha = if (isDark) 0.12f else 0.07f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.15f, h * 0.48f),
                            radius = maxOf(w, h) * 0.7f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tertiaryGlow.copy(alpha = if (isDark) 0.08f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.72f, h * 0.88f),
                            radius = maxOf(w, h) * 0.55f
                        )
                    )
                }
            }

            BackgroundStyle.NEBULA -> {
                Image(
                    painter = painterResource(id = R.drawable.img_bg_cosmic_nebula_1795312684736),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (isDark) 0.52f else 0.20f,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    bgColor.copy(alpha = if (isDark) 0.45f else 0.76f),
                                    bgColor.copy(alpha = if (isDark) 0.30f else 0.68f),
                                    bgColor.copy(alpha = if (isDark) 0.65f else 0.85f)
                                )
                            )
                        )
                )
            }

            BackgroundStyle.AURORA -> {
                Image(
                    painter = painterResource(id = R.drawable.img_bg_silk_aurora_1795312685416),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (isDark) 0.54f else 0.22f,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    bgColor.copy(alpha = if (isDark) 0.42f else 0.74f),
                                    bgColor.copy(alpha = if (isDark) 0.28f else 0.66f),
                                    bgColor.copy(alpha = if (isDark) 0.62f else 0.84f)
                                )
                            )
                        )
                )
            }

            BackgroundStyle.MINIMAL -> Unit
        }
    }
}
