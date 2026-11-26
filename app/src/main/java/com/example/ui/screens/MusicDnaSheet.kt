package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Track
import com.example.domain.model.TrackDnaInfo
import com.example.ui.components.EchoSurfaceCard
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.TrackArtwork
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.MistSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MusicDnaSheet(
    track: Track,
    dnaInfo: TrackDnaInfo?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val palette = LocalLiquidPalette.current
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0B0F1A),
        contentColor = FrostWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag("music_dna_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    TrackArtwork(
                        artworkUrl = track.artworkUrl,
                        contentDescription = track.title,
                        cornerRadius = 14.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = palette.secondaryGlow,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "MUSIC DNA",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.secondaryGlow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = FrostWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MistSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close Music DNA", tint = MistSecondary)
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = palette.primaryGlow, strokeWidth = 2.5.dp)
                        Text(
                            text = "Decoding Genius & Wikipedia song story…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MistSecondary
                        )
                    }
                }
            } else if (dnaInfo != null) {
                // Metadata badges
                if (dnaInfo.releaseDate.isNotBlank() || dnaInfo.albumContext.isNotBlank()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (dnaInfo.releaseDate.isNotBlank()) {
                            DnaBadge(
                                text = "Released ${dnaInfo.releaseDate}",
                                accent = palette.primaryGlow,
                                leadingIcon = Icons.Rounded.CalendarMonth
                            )
                        }
                        if (dnaInfo.albumContext.isNotBlank()) {
                            DnaBadge(
                                text = dnaInfo.albumContext,
                                accent = palette.secondaryGlow
                            )
                        }
                        if (dnaInfo.sourceLabel.isNotBlank()) {
                            DnaBadge(
                                text = dnaInfo.sourceLabel,
                                accent = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Story Summary
                EchoSurfaceCard(modifier = Modifier.fillMaxWidth(), elevated = true) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Behind the Track",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.primaryGlow
                        )
                        Text(
                            text = dnaInfo.storySummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FrostWhite.copy(alpha = 0.92f)
                        )
                    }
                }

                // Annotations & Facts
                if (dnaInfo.annotations.isNotEmpty()) {
                    EchoSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Highlights & Context",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.secondaryGlow
                            )
                            dnaInfo.annotations.forEach { note ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(palette.secondaryGlow)
                                    )
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FrostWhite.copy(alpha = 0.88f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Credits: Writers & Producers
                if (dnaInfo.writers.isNotEmpty() || dnaInfo.producers.isNotEmpty()) {
                    EchoSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Song Credits",
                                style = MaterialTheme.typography.titleSmall,
                                color = FrostWhite
                            )
                            if (dnaInfo.writers.isNotEmpty()) {
                                Text("Writers", style = MaterialTheme.typography.labelSmall, color = MistSecondary)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    dnaInfo.writers.take(8).forEach { w ->
                                        DnaBadge(text = w, accent = palette.primaryGlow)
                                    }
                                }
                            }
                            if (dnaInfo.producers.isNotEmpty()) {
                                Text("Producers", style = MaterialTheme.typography.labelSmall, color = MistSecondary)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    dnaInfo.producers.take(8).forEach { p ->
                                        DnaBadge(text = p, accent = palette.secondaryGlow)
                                    }
                                }
                            }
                        }
                    }
                }

                if (dnaInfo.externalUrl.startsWith("http")) {
                    LiquidGlassButton(
                        onClick = { runCatching { uriHandler.openUri(dnaInfo.externalUrl) } },
                        accentGlow = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                            Text("Read Full Story & Annotations", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun DnaBadge(
    text: String,
    accent: Color,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = FrostWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
