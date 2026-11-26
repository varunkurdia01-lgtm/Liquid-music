package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.EchoAddonConfig
import com.example.ui.components.EchoSurfaceCard
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.LiquidGlassToggle
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.LuminousRose
import com.example.ui.theme.MistSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoAddonManagerSheet(
    addons: List<EchoAddonConfig>,
    onAddAndTestAddon: (String, (String) -> Unit) -> Unit,
    onToggleAddon: (String) -> Unit,
    onMovePriority: (String, Boolean) -> Unit,
    onRemoveAddon: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalLiquidPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var urlInput by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }

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
                .testTag("echo_addon_manager_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(palette.primaryGlow.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Extension, contentDescription = null, tint = palette.primaryGlow)
                    }
                    Column {
                        Text("Echo Addon Manager", style = MaterialTheme.typography.titleLarge, color = FrostWhite)
                        Text(
                            "Verify /manifest.json, reorder priority & enable inline search",
                            style = MaterialTheme.typography.bodySmall,
                            color = MistSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close Addon Manager", tint = MistSecondary)
                }
            }

            // Add & Verify Addon Input Card
            EchoSurfaceCard(modifier = Modifier.fillMaxWidth(), elevated = true) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Install Echo Extension Server", style = MaterialTheme.typography.titleSmall, color = FrostWhite)
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("https://your-echo-addon.vercel.app", color = MistSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = FrostWhite,
                            unfocusedTextColor = FrostWhite,
                            focusedBorderColor = palette.primaryGlow,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("addon_url_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidGlassButton(
                            onClick = {
                                if (urlInput.isNotBlank() && !isTesting) {
                                    isTesting = true
                                    testMessage = null
                                    onAddAndTestAddon(urlInput.trim()) { msg ->
                                        isTesting = false
                                        testMessage = msg
                                        if (msg.startsWith("Verified")) urlInput = ""
                                    }
                                }
                            },
                            accentGlow = true,
                            enabled = urlInput.isNotBlank() && !isTesting,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("verify_addon_button")
                        ) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!isTesting) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = if (isTesting) "Verifying…" else "Verify & Install",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = FrostWhite
                                )
                            }
                        }
                        if (isTesting) {
                            CircularProgressIndicator(
                                color = palette.secondaryGlow,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    if (!testMessage.isNullOrBlank()) {
                        Text(
                            text = testMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (testMessage?.startsWith("Verified") == true) EmeraldPulse else LuminousRose
                        )
                    }
                }
            }

            // Installed Addons List
            if (addons.isEmpty()) {
                EchoSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No Custom Echo Addons Installed", style = MaterialTheme.typography.titleSmall, color = FrostWhite)
                        Text(
                            "Paste any Echo-compatible /manifest.json server URL above to unlock FLAC/Lossless failover and inline extension search results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MistSecondary
                        )
                    }
                }
            } else {
                Text(
                    "Installed Extensions (${addons.size}) — Top priority runs first",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.secondaryGlow
                )
                addons.forEachIndexed { index, addon ->
                    EchoSurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevated = addon.enabled,
                        accentBorder = if (addon.enabled) palette.primaryGlow.copy(alpha = 0.35f) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}. ${addon.name}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = FrostWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (addon.supportsLossless) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(EmeraldPulse.copy(alpha = 0.18f))
                                                    .border(1.dp, EmeraldPulse.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    "FLAC",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldPulse,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = addon.baseUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MistSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                LiquidGlassToggle(
                                    checked = addon.enabled,
                                    onCheckedChange = { onToggleAddon(addon.id) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldPulse,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "v${addon.version} · ${addon.statusMessage}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MistSecondary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = { onAddAndTestAddon(addon.baseUrl) {} },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Re-test addon", tint = palette.secondaryGlow, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { onMovePriority(addon.id, true) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ArrowUpward,
                                            contentDescription = "Move up",
                                            tint = if (index > 0) FrostWhite else MistSecondary.copy(alpha = 0.3f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMovePriority(addon.id, false) },
                                        enabled = index < addons.lastIndex,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ArrowDownward,
                                            contentDescription = "Move down",
                                            tint = if (index < addons.lastIndex) FrostWhite else MistSecondary.copy(alpha = 0.3f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveAddon(addon.id) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove addon", tint = LuminousRose, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
