package com.example.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.EchoSurfaceCard
import com.example.ui.glass.LiquidGlassButton
import com.example.ui.theme.FrostWhite
import com.example.ui.theme.LocalLiquidPalette
import com.example.ui.theme.MistSecondary

private const val YTM_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

private fun isEmulatorEnvironment(): Boolean {
    val fingerprint = Build.FINGERPRINT.orEmpty()
    val model = Build.MODEL.orEmpty()
    val hardware = Build.HARDWARE.orEmpty()
    val product = Build.PRODUCT.orEmpty()
    return fingerprint.startsWith("generic", ignoreCase = true) ||
        fingerprint.contains("emulator", ignoreCase = true) ||
        model.contains("Emulator", ignoreCase = true) ||
        model.contains("Android SDK built for", ignoreCase = true) ||
        hardware.contains("ranchu", ignoreCase = true) ||
        hardware.contains("goldfish", ignoreCase = true) ||
        product.contains("sdk", ignoreCase = true) ||
        product.contains("emulator", ignoreCase = true)
}

/**
 * Echo Music in-app WebView YouTube Music login flow with safe emulator/GPU crash recovery
 * (`onRenderProcessGone`), software-layer fallback, proper WebView teardown, and manual cookie fallback.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(
    onLoginCaptured: (cookie: String, visitorData: String, dataSyncId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalLiquidPalette.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(YTM_LOGIN_URL) }
    var capturedAlready by remember { mutableStateOf(false) }
    var rendererCrashed by remember { mutableStateOf(false) }
    var forceSoftwareLayer by remember { mutableStateOf(isEmulatorEnvironment()) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var showManualCookieMode by remember { mutableStateOf(false) }
    var manualCookieInput by remember { mutableStateOf("") }
    var statusBanner by remember { mutableStateOf("Sign in to your Google / YouTube Music account") }

    DisposableEffect(reloadNonce, showManualCookieMode) {
        onDispose {
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.webViewClient = WebViewClient()
                webViewRef?.webChromeClient = null
                webViewRef?.destroy()
            }
            webViewRef = null
        }
    }

    fun attemptSessionCapture(view: WebView?, forceNotifyIfMissing: Boolean = false) {
        if (capturedAlready || view == null) return
        val cm = CookieManager.getInstance()
        val rawCookies = cm.getCookie("https://music.youtube.com").orEmpty()
            .ifBlank { cm.getCookie("https://www.youtube.com").orEmpty() }
        val hasAuthCookie = rawCookies.contains("SAPISID=") || rawCookies.contains("__Secure-3PAPISID=")
        if (!hasAuthCookie) {
            if (forceNotifyIfMissing) {
                statusBanner = "Finish signing in until YouTube Music loads, then tap Capture."
            }
            return
        }
        capturedAlready = true
        statusBanner = "Session verified! Capturing account & syncing library…"
        runCatching {
            view.evaluateJavascript(
                "(function(){try{var v=(window.ytcfg&&ytcfg.get)?(ytcfg.get('VISITOR_DATA')||''):'';" +
                    "var d=(window.ytcfg&&ytcfg.get)?(ytcfg.get('DATASYNC_ID')||''):'';" +
                    "return v+'|||'+d;}catch(e){return '|||';}})();"
            ) { jsResult ->
                val cleaned = jsResult?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: "|||"
                val parts = cleaned.split("|||")
                val vd = parts.getOrNull(0).orEmpty().takeIf { it != "null" && it != "undefined" }.orEmpty()
                val syncId = parts.getOrNull(1).orEmpty().takeIf { it != "null" && it != "undefined" }.orEmpty()
                onLoginCaptured(rawCookies, vd, syncId)
            }
        }.onFailure {
            onLoginCaptured(rawCookies, "", "")
        }
    }

    BackHandler {
        val wv = webViewRef
        if (!showManualCookieMode && !rendererCrashed && wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("youtube_login_webview_screen")
    ) {
        // Material 3 Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1422))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close YouTube Login", tint = FrostWhite)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "YouTube Music Sign-In",
                        style = MaterialTheme.typography.titleMedium,
                        color = FrostWhite,
                        maxLines = 1
                    )
                    Text(
                        text = statusBanner,
                        style = MaterialTheme.typography.labelSmall,
                        color = MistSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { showManualCookieMode = !showManualCookieMode }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = "Paste Cookie Manually",
                        tint = if (showManualCookieMode) palette.primaryGlow else MistSecondary
                    )
                }
                if (capturedAlready) {
                    CircularProgressIndicator(
                        color = palette.primaryGlow,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (!showManualCookieMode && !rendererCrashed) {
                    LiquidGlassButton(
                        onClick = { attemptSessionCapture(webViewRef, forceNotifyIfMissing = true) },
                        accentGlow = true,
                        cornerRadius = 16.dp,
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("ytm_capture_session_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(16.dp))
                            Text("Capture", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                        }
                    }
                }
            }
        }

        if (!showManualCookieMode && !rendererCrashed && loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                color = palette.primaryGlow,
                trackColor = Color(0xFF141B2D),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                showManualCookieMode -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        EchoSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Manual YouTube Music Cookie",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = FrostWhite
                                )
                                Text(
                                    text = "Paste your Cookie header from music.youtube.com (must contain SAPISID or __Secure-3PAPISID).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MistSecondary
                                )
                                OutlinedTextField(
                                    value = manualCookieInput,
                                    onValueChange = { manualCookieInput = it },
                                    placeholder = { Text("SAPISID=...; HSID=...; SSID=...", color = MistSecondary.copy(alpha = 0.6f)) },
                                    minLines = 3,
                                    maxLines = 6,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = FrostWhite,
                                        unfocusedTextColor = FrostWhite,
                                        focusedBorderColor = palette.primaryGlow,
                                        unfocusedBorderColor = Color(0xFF2B354C)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LiquidGlassButton(
                                        onClick = { showManualCookieMode = false },
                                        accentGlow = false,
                                        cornerRadius = 16.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Text(
                                            text = "Back to WebView",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = FrostWhite,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                    LiquidGlassButton(
                                        onClick = {
                                            val trimmed = manualCookieInput.trim()
                                            if (trimmed.contains("SAPISID=") || trimmed.contains("__Secure-3PAPISID=")) {
                                                onLoginCaptured(trimmed, "", "")
                                            } else {
                                                statusBanner = "Cookie must include SAPISID or __Secure-3PAPISID"
                                            }
                                        },
                                        accentGlow = true,
                                        cornerRadius = 16.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Text(
                                            text = "Save & Sync",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = FrostWhite,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                rendererCrashed -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EchoSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Browser Renderer Interrupted",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = FrostWhite
                                )
                                Text(
                                    text = "The embedded browser renderer restarted (common on emulators without hardware GPU acceleration). Reload in safe software mode or paste your cookie directly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MistSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LiquidGlassButton(
                                    onClick = {
                                        forceSoftwareLayer = true
                                        rendererCrashed = false
                                        statusBanner = "Reloading in safe software mode…"
                                        reloadNonce++
                                    },
                                    accentGlow = true,
                                    cornerRadius = 18.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.align(Alignment.Center),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = FrostWhite, modifier = Modifier.size(18.dp))
                                        Text("Reload Sign-In (Safe Mode)", style = MaterialTheme.typography.labelLarge, color = FrostWhite)
                                    }
                                }
                                LiquidGlassButton(
                                    onClick = { showManualCookieMode = true },
                                    accentGlow = false,
                                    cornerRadius = 18.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Text(
                                        text = "Paste Cookie Manually",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = FrostWhite,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    key(reloadNonce) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    webViewRef = this
                                    if (forceSoftwareLayer) {
                                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    // Preserve Chromium's exact engine version while removing '; wv' so Google allows Sign-In cleanly
                                    settings.userAgentString = settings.userAgentString.replace("; wv", "")

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            loadProgress = newProgress
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            val loaded = url.orEmpty()
                                            currentUrl = loaded
                                            if (loaded.startsWith("https://music.youtube.com")) {
                                                attemptSessionCapture(view, forceNotifyIfMissing = false)
                                            }
                                        }

                                        override fun onRenderProcessGone(
                                            view: WebView?,
                                            detail: RenderProcessGoneDetail?
                                        ): Boolean {
                                            runCatching {
                                                view?.stopLoading()
                                                view?.destroy()
                                            }
                                            webViewRef = null
                                            rendererCrashed = true
                                            statusBanner = "Browser renderer paused — tap Reload (Safe Mode)"
                                            return true
                                        }
                                    }
                                    loadUrl(YTM_LOGIN_URL)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
