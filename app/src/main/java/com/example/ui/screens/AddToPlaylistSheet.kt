package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Playlist
import com.example.domain.model.Track
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.TrackArtwork
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.MistSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddToExisting: (Long, Track) -> Unit,
    onCreateAndAdd: (name: String, Track) -> Unit
) {
    val palette = LocalLiquidPalette.current
    var quickPlaylistName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeepVoid,
        contentColor = FrostWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add to Playlist", style = MaterialTheme.typography.headlineMedium, color = FrostWhite)
            Text(
                text = "${track.title} · ${track.artist}",
                style = MaterialTheme.typography.bodySmall,
                color = MistSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Quick create new playlist row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = quickPlaylistName,
                    onValueChange = { quickPlaylistName = it },
                    placeholder = { Text("New playlist name…", color = MistSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FrostWhite,
                        unfocusedTextColor = FrostWhite,
                        focusedBorderColor = palette.primaryGlow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassButton(
                    onClick = {
                        onCreateAndAdd(quickPlaylistName, track)
                        onDismiss()
                    },
                    enabled = quickPlaylistName.isNotBlank(),
                    accentGlow = true,
                    cornerRadius = 16.dp,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Create and add", tint = FrostWhite, modifier = Modifier.align(Alignment.Center))
                }
            }

            if (playlists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x44141A2E))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                            .clickable {
                                onAddToExisting(playlist.id, track)
                                onDismiss()
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtwork(
                            artworkUrl = playlist.coverArtUri.ifBlank { playlist.tracks.firstOrNull()?.artworkUrl.orEmpty() },
                            contentDescription = playlist.name,
                            cornerRadius = 10.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleSmall, color = FrostWhite)
                            Text("${playlist.tracks.size} tracks", style = MaterialTheme.typography.bodySmall, color = MistSecondary)
                        }
                    }
                }
            }
        }
    }
}
