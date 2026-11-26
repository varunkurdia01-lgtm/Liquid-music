package com.example.data.remote.innertube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.security.MessageDigest

private const val TAG = "InnerTubeClient"

internal enum class InnerTubeClientProfile(
    val clientName: String,
    val clientVersion: String,
    val clientIdHeader: String,
    val userAgent: String,
    val origin: String = "https://music.youtube.com",
    val hostUrl: String = "https://music.youtube.com/youtubei/v1",
    val sendVisitorData: Boolean = false,
    val allowCookieAuth: Boolean = false,
    val embedUrl: String? = null,
    val extraDeviceFields: Map<String, Any> = emptyMap()
) {
    WEB_REMIX(
        clientName = "WEB_REMIX",
        clientVersion = "1.20260213.01.00",
        clientIdHeader = "67",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
        origin = "https://music.youtube.com",
        hostUrl = "https://music.youtube.com/youtubei/v1",
        sendVisitorData = true,
        allowCookieAuth = true
    ),
    VISIONOS(
        clientName = "VISIONOS",
        clientVersion = "0.1",
        clientIdHeader = "101",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
        origin = "https://www.youtube.com",
        hostUrl = "https://www.youtube.com/youtubei/v1",
        sendVisitorData = false,
        extraDeviceFields = mapOf(
            "deviceMake" to "Apple",
            "deviceModel" to "RealityDevice14,1",
            "osName" to "visionOS",
            "osVersion" to "1.3.21O771"
        )
    ),
    ANDROID_VR_1_65_10(
        clientName = "ANDROID_VR",
        clientVersion = "1.65.10",
        clientIdHeader = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        origin = "https://www.youtube.com",
        hostUrl = "https://www.youtube.com/youtubei/v1",
        sendVisitorData = true,
        allowCookieAuth = true,
        extraDeviceFields = mapOf(
            "deviceMake" to "Oculus",
            "deviceModel" to "Quest 3",
            "osName" to "Android",
            "osVersion" to "12L",
            "androidSdkVersion" to 32
        )
    ),
    TVHTML5_SIMPLY_EMBEDDED_PLAYER(
        clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        clientVersion = "2.0",
        clientIdHeader = "85",
        userAgent = "Mozilla/5.0 (PlayStation; PlayStation 5/2.26) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0 Safari/605.1.15",
        origin = "https://www.youtube.com",
        hostUrl = "https://www.youtube.com/youtubei/v1",
        sendVisitorData = true,
        allowCookieAuth = true,
        embedUrl = "https://www.youtube.com"
    ),
    WEB_EMBEDDED_PLAYER(
        clientName = "WEB_EMBEDDED_PLAYER",
        clientVersion = "1.20260213.01.00",
        clientIdHeader = "56",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36",
        origin = "https://www.youtube.com",
        hostUrl = "https://www.youtube.com/youtubei/v1",
        sendVisitorData = true,
        allowCookieAuth = true,
        embedUrl = "https://www.youtube.com"
    ),
    TVHTML5(
        clientName = "TVHTML5",
        clientVersion = "7.20260213.00.00",
        clientIdHeader = "7",
        userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
        origin = "https://www.youtube.com",
        hostUrl = "https://www.youtube.com/youtubei/v1",
        sendVisitorData = false,
        allowCookieAuth = true
    ),
    IOS(
        clientName = "IOS",
        clientVersion = "21.03.1",
        clientIdHeader = "5",
        userAgent = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
        origin = "https://www.youtube.com",
        hostUrl = "https://www.youtube.com/youtubei/v1",
        sendVisitorData = false,
        extraDeviceFields = mapOf(
            "deviceMake" to "Apple",
            "deviceModel" to "iPhone16,2",
            "osName" to "iPhone",
            "osVersion" to "18.2.22C152"
        )
    ),
    ANDROID_MUSIC(
        clientName = "ANDROID_MUSIC",
        clientVersion = "8.39.42",
        clientIdHeader = "21",
        userAgent = "com.google.android.apps.youtube.music/8.39.42 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002) gzip",
        origin = "https://music.youtube.com",
        hostUrl = "https://music.youtube.com/youtubei/v1",
        sendVisitorData = false,
        allowCookieAuth = true,
        extraDeviceFields = mapOf(
            "osName" to "Android",
            "osVersion" to "15",
            "androidSdkVersion" to 35
        )
    )
}

/**
 * Echo Music-compatible YouTube Music InnerTube v1 protocol client.
 * Handles WEB_REMIX browsing/search/continuations, SAPISIDHASH cookie authentication,
 * sw.js_data visitorData bootstrapping, localization (gl/hl), embedded/VR/IOS player extraction,
 * and Piped + Invidious multi-mirror stream failover.
 */
class InnerTubeClient(
    private val okHttpClient: OkHttpClient
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val visitorRegex = Regex("^Cg[ts]")

    @Volatile
    var visitorData: String? = null

    @Volatile
    var contentCountry: String = "US"

    @Volatile
    var contentLanguage: String = "en"

    @Volatile
    var cookie: String? = null
        set(value) {
            if (field != value) {
                ytBotBlockedUntilMs = 0L
            }
            field = value
        }

    @Volatile
    var dataSyncId: String? = null

    @Volatile
    var forceOpusAudio: Boolean = false

    @Volatile
    var ytBotBlockedUntilMs: Long = 0L

    fun isAnonymousBotBlocked(): Boolean =
        cookie.isNullOrBlank() && System.currentTimeMillis() < ytBotBlockedUntilMs

    @Volatile
    private var activeHttpClient: OkHttpClient = okHttpClient

    @Volatile
    private var lastProxySignature: String = ""

    fun configureProxy(enabled: Boolean, proxyUrl: String) {
        val signature = if (enabled) proxyUrl.trim() else ""
        if (signature == lastProxySignature) return
        lastProxySignature = signature
        if (!enabled || signature.isBlank()) {
            activeHttpClient = okHttpClient
            return
        }
        runCatching {
            val normalized = if (signature.contains("://")) signature else "http://$signature"
            val uri = URI(normalized)
            val host = uri.host ?: return
            val port = if (uri.port > 0) uri.port else 8080
            val type = if (uri.scheme.orEmpty().startsWith("socks", ignoreCase = true)) {
                Proxy.Type.SOCKS
            } else {
                Proxy.Type.HTTP
            }
            activeHttpClient = okHttpClient.newBuilder()
                .proxy(Proxy(type, InetSocketAddress(host, port)))
                .build()
        }.onFailure {
            Log.w(TAG, "Invalid proxy URL '$proxyUrl': ${it.message}")
            activeHttpClient = okHttpClient
        }
    }

    suspend fun ensureVisitorData(): String? = withContext(Dispatchers.IO) {
        visitorData?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        try {
            val req = Request.Builder()
                .url("https://music.youtube.com/sw.js_data")
                .header("User-Agent", InnerTubeClientProfile.WEB_REMIX.userAgent)
                .build()
            activeHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                var raw = resp.body?.string().orEmpty()
                if (raw.startsWith(")]}'")) {
                    raw = raw.substringAfter("\n", raw.drop(4))
                }
                val arr = JSONArray(raw)
                val inner = arr.optJSONArray(0)?.optJSONArray(2) ?: return@withContext null
                for (i in 0 until inner.length()) {
                    val candidate = inner.optString(i)
                    if (candidate.isNotBlank() && visitorRegex.containsMatchIn(candidate)) {
                        visitorData = candidate
                        return@withContext candidate
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "sw.js_data visitorData bootstrap failed: ${e.message}")
        }
        null
    }

    fun resetVisitorData() {
        visitorData = null
    }

    internal suspend fun post(
        endpoint: String,
        payload: JSONObject,
        profile: InnerTubeClientProfile = InnerTubeClientProfile.WEB_REMIX,
        continuation: String? = null,
        hl: String = contentLanguage,
        gl: String = contentCountry
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            if (profile.sendVisitorData && visitorData.isNullOrBlank()) {
                ensureVisitorData()
            }

            val clientObj = JSONObject().apply {
                put("clientName", profile.clientName)
                put("clientVersion", profile.clientVersion)
                put("hl", hl.ifBlank { "en" })
                put("gl", gl.ifBlank { "US" })
                if (profile.sendVisitorData) {
                    visitorData?.takeIf { it.isNotBlank() }?.let { put("visitorData", it) }
                }
                profile.extraDeviceFields.forEach { (k, v) -> put(k, v) }
            }

            val contextObj = JSONObject().apply {
                put("client", clientObj)
                if (!profile.embedUrl.isNullOrBlank()) {
                    put("thirdParty", JSONObject().apply { put("embedUrl", profile.embedUrl) })
                }
            }

            val bodyJson = JSONObject(payload.toString()).apply {
                put("context", contextObj)
            }

            val urlBuilder = "${profile.hostUrl}/$endpoint".toHttpUrl().newBuilder()
                .addQueryParameter("prettyPrint", "false")
            if (!continuation.isNullOrBlank()) {
                urlBuilder.addQueryParameter("continuation", continuation)
                urlBuilder.addQueryParameter("ctoken", continuation)
            }

            val requestBuilder = Request.Builder()
                .url(urlBuilder.build())
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("User-Agent", profile.userAgent)
                .header("X-Goog-Api-Format-Version", "1")
                .header("X-YouTube-Client-Name", profile.clientIdHeader)
                .header("X-YouTube-Client-Version", profile.clientVersion)
                .header("Origin", profile.origin)
                .header("Referer", "${profile.origin}/")
                .header("Accept-Language", "$hl-$gl,$hl;q=0.9,en-US;q=0.8,en;q=0.7")

            if (profile.sendVisitorData) {
                visitorData?.takeIf { it.isNotBlank() }?.let {
                    requestBuilder.header("X-Goog-Visitor-Id", it)
                }
            }

            if (profile.allowCookieAuth) {
                val activeCookie = cookie?.trim().orEmpty()
                if (activeCookie.isNotBlank()) {
                    requestBuilder.header("Cookie", activeCookie)
                    requestBuilder.header("X-Goog-AuthUser", "0")
                    val pageId = dataSyncId?.substringBefore("||")?.trim().orEmpty()
                    if (pageId.isNotBlank()) {
                        requestBuilder.header("X-Goog-PageId", pageId)
                    }
                    extractSapisid(activeCookie)?.let { sapisid ->
                        val nowSec = System.currentTimeMillis() / 1000
                        val hash = sha1("$nowSec $sapisid ${profile.origin}")
                        requestBuilder.header("Authorization", "SAPISIDHASH ${nowSec}_$hash")
                    }
                }
            }

            activeHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "InnerTube $endpoint (${profile.clientName}) HTTP ${response.code}")
                    return@withContext null
                }
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return@withContext null

                val parsed = JSONObject(raw)
                if (profile.sendVisitorData) {
                    val vd = parsed.optJSONObject("responseContext")?.optString("visitorData")
                    if (!vd.isNullOrBlank()) {
                        visitorData = vd
                    }
                }
                parsed
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube $endpoint (${profile.clientName}) failed: ${e.message}")
            null
        }
    }

    suspend fun browse(
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null
    ): JSONObject? {
        val payload = JSONObject().apply {
            if (!browseId.isNullOrBlank()) put("browseId", browseId)
            if (!params.isNullOrBlank()) put("params", params)
        }
        return post(
            endpoint = "browse",
            payload = payload,
            profile = InnerTubeClientProfile.WEB_REMIX,
            continuation = continuation
        )
    }

    suspend fun search(
        query: String,
        params: String? = null,
        continuation: String? = null
    ): JSONObject? {
        val payload = JSONObject().apply {
            put("query", query)
            if (!params.isNullOrBlank()) put("params", params)
        }
        return post(
            endpoint = "search",
            payload = payload,
            profile = InnerTubeClientProfile.WEB_REMIX,
            continuation = continuation
        )
    }

    suspend fun searchSuggestions(input: String): JSONObject? {
        val payload = JSONObject().apply {
            put("input", input)
        }
        return post("music/get_search_suggestions", payload, InnerTubeClientProfile.WEB_REMIX)
    }

    suspend fun getAccountMenu(): JSONObject? {
        return post("account/account_menu", JSONObject(), InnerTubeClientProfile.WEB_REMIX)
    }

    suspend fun next(
        videoId: String? = null,
        playlistId: String? = null,
        params: String? = null,
        continuation: String? = null
    ): JSONObject? {
        val payload = JSONObject().apply {
            if (!videoId.isNullOrBlank()) put("videoId", videoId)
            if (!playlistId.isNullOrBlank()) put("playlistId", playlistId)
            if (!params.isNullOrBlank()) put("params", params)
            put("isAudioOnly", true)
            put("enablePersistentPlaylistPanel", true)
        }
        return post(
            endpoint = "next",
            payload = payload,
            profile = InnerTubeClientProfile.WEB_REMIX,
            continuation = continuation
        )
    }

    /**
     * Echo Music Measured Fallback Cascade (used when InnerTubeX needs a backup):
     * ANDROID_VR_1_65_10 -> TVHTML5_SIMPLY_EMBEDDED_PLAYER -> VISIONOS -> WEB_EMBEDDED_PLAYER -> TVHTML5 -> ANDROID_MUSIC -> IOS -> WEB_REMIX.
     * Accepts direct adaptive audio streams first, and falls back to muxed `formats` streams if adaptive formats are ciphered.
     */
    suspend fun player(videoId: String): JSONObject? {
        val profiles = listOf(
            InnerTubeClientProfile.ANDROID_VR_1_65_10,
            InnerTubeClientProfile.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            InnerTubeClientProfile.VISIONOS,
            InnerTubeClientProfile.WEB_EMBEDDED_PLAYER,
            InnerTubeClientProfile.TVHTML5,
            InnerTubeClientProfile.ANDROID_MUSIC,
            InnerTubeClientProfile.IOS,
            InnerTubeClientProfile.WEB_REMIX
        )
        val cpn = generateCpn()
        var muxedFallback: JSONObject? = null
        var hlsFallback: JSONObject? = null
        var botBlockCount = 0
        for (profile in profiles) {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("cpn", cpn)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            val result = post("player", payload, profile) ?: continue
            val playabilityObj = result.optJSONObject("playabilityStatus")
            val playability = playabilityObj?.optString("status")
            if (playability != "OK") {
                val reason = playabilityObj?.optString("reason").orEmpty()
                Log.w(TAG, "player($videoId) ${profile.clientName} -> $playability ($reason)")
                if (cookie.isNullOrBlank() && reason.contains("not a bot", ignoreCase = true)) {
                    botBlockCount++
                    if (botBlockCount >= 2) {
                        ytBotBlockedUntilMs = System.currentTimeMillis() + 90_000L
                        Log.w(TAG, "YouTube bot-check detected on anonymous IP — temporarily failing over to backup providers")
                        return null
                    }
                }
                continue
            }
            val streamingData = result.optJSONObject("streamingData") ?: continue

            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            if (adaptive != null && adaptive.length() > 0) {
                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.optJSONObject(i) ?: continue
                    val mime = fmt.optString("mimeType")
                    val directUrl = fmt.optString("url")
                    if (mime.startsWith("audio/") && directUrl.startsWith("http")) {
                        return result
                    }
                }
            }

            if (muxedFallback == null) {
                val formats = streamingData.optJSONArray("formats")
                if (formats != null && formats.length() > 0) {
                    for (i in 0 until formats.length()) {
                        val fmt = formats.optJSONObject(i) ?: continue
                        val directUrl = fmt.optString("url")
                        if (directUrl.startsWith("http")) {
                            muxedFallback = result
                            break
                        }
                    }
                }
            }

            if (hlsFallback == null) {
                val hlsUrl = streamingData.optString("hlsManifestUrl")
                if (hlsUrl.startsWith("http")) {
                    hlsFallback = result
                }
            }
        }
        return muxedFallback ?: hlsFallback
    }

    /**
     * Multi-mirror Piped + Invidious emergency audio stream fallback (resilient on datacenter/emulator IPs).
     */
    suspend fun fetchPipedAudioStream(videoId: String): Pair<String, Int>? = withContext(Dispatchers.IO) {
        val fastClient = activeHttpClient.newBuilder()
            .connectTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(3500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val pipedInstances = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks"
        )
        for (base in pipedInstances) {
            try {
                val req = Request.Builder()
                    .url("$base/streams/$videoId")
                    .header("User-Agent", InnerTubeClientProfile.WEB_REMIX.userAgent)
                    .build()
                fastClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use
                    val json = JSONObject(body)
                    val streams = json.optJSONArray("audioStreams") ?: return@use
                    var bestUrl: String? = null
                    var bestBitrate = 0
                    for (i in 0 until streams.length()) {
                        val s = streams.optJSONObject(i) ?: continue
                        val url = s.optString("url").takeIf { it.startsWith("http") } ?: continue
                        val bitrate = s.optInt("bitrate", 128_000)
                        if (bitrate > bestBitrate) {
                            bestBitrate = bitrate
                            bestUrl = url
                        }
                    }
                    if (bestUrl != null) {
                        return@withContext bestUrl to (bestBitrate / 1000).coerceAtLeast(64)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Piped fallback on $base failed: ${e.message}")
            }
        }

        val invidiousInstances = listOf(
            "https://yewtu.be"
        )
        for (base in invidiousInstances) {
            try {
                val req = Request.Builder()
                    .url("$base/api/v1/videos/$videoId?fields=adaptiveFormats,formatStreams")
                    .header("User-Agent", InnerTubeClientProfile.WEB_REMIX.userAgent)
                    .build()
                fastClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use
                    val json = JSONObject(body)
                    val adaptive = json.optJSONArray("adaptiveFormats")
                    var bestUrl: String? = null
                    var bestBitrate = 0
                    if (adaptive != null) {
                        for (i in 0 until adaptive.length()) {
                            val s = adaptive.optJSONObject(i) ?: continue
                            val type = s.optString("type")
                            val url = s.optString("url").takeIf { it.startsWith("http") } ?: continue
                            if (!type.startsWith("audio/")) continue
                            val bitrate = s.optString("bitrate").toIntOrNull() ?: 128_000
                            if (bitrate > bestBitrate) {
                                bestBitrate = bitrate
                                bestUrl = url
                            }
                        }
                    }
                    if (bestUrl == null) {
                        val muxed = json.optJSONArray("formatStreams")
                        if (muxed != null) {
                            for (i in 0 until muxed.length()) {
                                val s = muxed.optJSONObject(i) ?: continue
                                val url = s.optString("url").takeIf { it.startsWith("http") } ?: continue
                                bestUrl = url
                                bestBitrate = 128_000
                                break
                            }
                        }
                    }
                    if (bestUrl != null) {
                        return@withContext bestUrl to (bestBitrate / 1000).coerceAtLeast(64)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invidious fallback on $base failed: ${e.message}")
            }
        }
        null
    }

    private fun generateCpn(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..16).map { alphabet.random() }.joinToString("")
    }

    private fun extractSapisid(cookieStr: String): String? {
        return cookieStr.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() }
    }

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
