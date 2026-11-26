package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.glass.LiquidAuroraCanvas
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.glass.LiquidGlassSurface
import com.example.ui.glass.LocalLiquidBackdrop
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.MistSecondary
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun OnboardingScreen(
    onSignInWithEmail: (displayName: String, email: String) -> Unit,
    onContinueWithGoogleDemo: () -> Unit,
    onSkipAsGuest: () -> Unit,
    isBackdropReady: Boolean = true
) {
    val palette = LocalLiquidPalette.current
    val onboardingBackdrop = rememberLayerBackdrop()
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidAuroraCanvas(
            modifier = if (isBackdropReady) Modifier.layerBackdrop(onboardingBackdrop) else Modifier,
            intensity = 1.2f
        )

        CompositionLocalProvider(
            LocalLiquidBackdrop provides if (isBackdropReady) onboardingBackdrop else null
        ) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Luminous Emblem
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                palette.primaryGlow.copy(alpha = 0.28f),
                                palette.deepAurora.copy(alpha = 0.65f)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), palette.secondaryGlow)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Liquid Music Logo",
                    tint = FrostWhite,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Liquid Music",
                style = MaterialTheme.typography.displayMedium,
                color = FrostWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Optical glass streaming, local lossless playback, DSP Sound Lab & Gemini Vibe DJ",
                style = MaterialTheme.typography.bodyMedium,
                color = MistSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                cornerRadius = 28.dp,
                surfaceAlpha = 0.64f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Enter Your Soundspace",
                        style = MaterialTheme.typography.titleLarge,
                        color = FrostWhite
                    )

                    GlassTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = "Display Name",
                        icon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = palette.primaryGlow) },
                        modifier = Modifier.testTag("onboarding_name_input")
                    )

                    GlassTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        keyboardType = KeyboardType.Email,
                        icon = { Icon(Icons.Rounded.Email, contentDescription = null, tint = palette.secondaryGlow) },
                        modifier = Modifier.testTag("onboarding_email_input")
                    )

                    GlassTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        isPassword = true,
                        icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = MistSecondary) },
                        modifier = Modifier.testTag("onboarding_password_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LiquidGlassButton(
                        onClick = { onSignInWithEmail(displayName, email) },
                        accentGlow = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("onboarding_sign_in_button")
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_sign_in),
                            style = MaterialTheme.typography.titleMedium,
                            color = FrostWhite,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    LiquidGlassButton(
                        onClick = onContinueWithGoogleDemo,
                        accentGlow = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_google_demo_button")
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = palette.secondaryGlow,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.onboarding_google_demo),
                                style = MaterialTheme.typography.labelLarge,
                                color = FrostWhite
                            )
                        }
                    }

                    TextButton(
                        onClick = onSkipAsGuest,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("onboarding_skip_guest_button")
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip_guest),
                            style = MaterialTheme.typography.labelLarge,
                            color = MistSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    icon: @Composable () -> Unit
) {
    val palette = LocalLiquidPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = MistSecondary) },
        leadingIcon = icon,
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = FrostWhite,
            unfocusedTextColor = FrostWhite,
            focusedContainerColor = Color(0x5512182C),
            unfocusedContainerColor = Color(0x40101526),
            focusedBorderColor = palette.primaryGlow.copy(alpha = 0.7f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            cursorColor = palette.secondaryGlow
        ),
        modifier = modifier.fillMaxWidth()
    )
}
