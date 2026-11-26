package com.example.data.remote

import android.text.Html
import android.util.Log
import com.example.domain.model.AudioQuality
import com.example.domain.model.MusicSource
import com.example.domain.model.StreamResolution
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "BilibiliFallback"
private const val BILI_WEB_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

/**
 * Zero-auth global full-track audio fallback powered by Bilibili's public search & DASH audio API.
 * Strictly verifies title, artist, qualifiers, and duration via [TrackMatcher.selectBestMatch] so
 * wrong songs, covers, or remixes are never substituted, and resolves direct `audio/mp4` (AAC M4A)
 * DASH audio streams with required `Referer: https://www.bilibili.com/` media headers.
 */
class BilibiliFallbackProvider(
    baseClient: OkHttpClient
) {
    private val httpClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var buvidCookie: String? = null

    private data class BiliCandidate(
        val bvid: String,
        val title: String,
        val author: String,
        val durationMs: Long
    )

    suspend fun resolveStream(track: Track, quality: AudioQuality): StreamResolution? = withContext(Dispatchers.IO) {
        val cookie = ensureBuvidCookie()

        val identities = buildList {
            add(track.title to track.artist)
            TrackMatcher.uploadIdentity(track.title)?.let { if (it !in this) add(it) }
        }

        for ((wantTitle, wantArtist) in identities) {
            val cleanTitle = TrackMatcher.cleanTitleForSearch(wantTitle)
            val cleanedArtist = wantArtist
                .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\bOfficial\b""", RegexOption.IGNORE_CASE), "")
                .trim()
            val primaryArtist = cleanedArtist.split(",", "&", "•", ";", "/").firstOrNull()?.trim().orEmpty()
                .takeUnless { it.equals("Unknown Artist", ignoreCase = true) }.orEmpty()

            val queries = buildList {
                val combined = "$cleanTitle $primaryArtist".trim()
                if (combined.isNotBlank()) add(combined)
                if (cleanTitle.length >= 3 && cleanTitle != combined) add(cleanTitle)
            }

            for (query in queries) {
                val candidates = searchVideos(query, cookie)
                if (candidates.isEmpty()) continue

                val best = TrackMatcher.selectBestMatch(
                    candidates = candidates,
                    title = wantTitle,
                    artist = cleanedArtist.ifBlank { wantArtist },
                    durationMs = track.durationMs.takeIf { it > 0L },
                    titleOf = { it.title },
                    artistOf = { cand ->
                        listOf(cand.author, cand.title).filter { it.isNotBlank() }.joinToString(" ")
                    },
                    durationMsOf = { it.durationMs.takeIf { d -> d > 0L } }
                ) ?: continue

                val resolved = resolveDashAudio(best.bvid, cookie, quality)
                if (resolved != null) return@withContext resolved
            }
        }
        null
    }

    private fun searchVideos(query: String, cookie: String): List<BiliCandidate> {
        val url = "https://api.bilibili.com/x/web-interface/search/type".toHttpUrl().newBuilder()
            .addQueryParameter("search_type", "video")
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", "1")
            .addQueryParameter("page_size", "15")
            .build()

        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BILI_WEB_UA)
                .header("Referer", "https://www.bilibili.com/")
                .header("Origin", "https://www.bilibili.com")
                .header("Cookie", cookie)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) return@use emptyList()
                val root = JSONObject(raw)
                if (root.optInt("code", -1) != 0) return@use emptyList()
                val results = root.optJSONObject("data")?.optJSONArray("result") ?: return@use emptyList()
                buildList {
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        val bvid = item.optString("bvid").trim()
                        val rawTitle = item.optString("title")
                        val cleanTitle = decodeHtml(rawTitle.replace(Regex("""<[^>]+>"""), "")).trim()
                        if (bvid.isBlank() || cleanTitle.isBlank()) continue
                        val author = decodeHtml(item.optString("author")).trim()
                        val durationMs = parseBiliDurationMs(item.optString("duration"))
                        // Skip tiny clips (<30s) or huge compilations (>15 min)
                        if (durationMs in 1..29_999L || durationMs > 900_000L) continue
                        add(BiliCandidate(bvid = bvid, title = cleanTitle, author = author, durationMs = durationMs))
                    }
                }
            }
        }.onFailure {
            Log.w(TAG, "Bilibili search failed for '$query': ${it.message}")
        }.getOrDefault(emptyList())
    }

    private fun resolveDashAudio(bvid: String, cookie: String, quality: AudioQuality): StreamResolution? {
        val cid = fetchFirstCid(bvid, cookie) ?: return null
        val url = "https://api.bilibili.com/x/player/playurl".toHttpUrl().newBuilder()
            .addQueryParameter("bvid", bvid)
            .addQueryParameter("cid", cid.toString())
            .addQueryParameter("fnval", "16")
            .addQueryParameter("fnver", "0")
            .addQueryParameter("fourk", "0")
            .addQueryParameter("qn", "64")
            .build()

        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BILI_WEB_UA)
                .header("Referer", "https://www.bilibili.com/video/$bvid/")
                .header("Origin", "https://www.bilibili.com")
                .header("Cookie", cookie)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) return@use null
                val root = JSONObject(raw)
                if (root.optInt("code", -1) != 0) return@use null
                val data = root.optJSONObject("data") ?: return@use null

                val mediaHeaders = mapOf(
                    "Referer" to "https://www.bilibili.com/video/$bvid/",
                    "Origin" to "https://www.bilibili.com",
                    "User-Agent" to BILI_WEB_UA
                )

                val dashAudio = data.optJSONObject("dash")?.optJSONArray("audio")
                if (dashAudio != null && dashAudio.length() > 0) {
                    var bestUrl: String? = null
                    var bestBps = -1
                    var bestMime = "audio/mp4"

                    for (i in 0 until dashAudio.length()) {
                        val a = dashAudio.optJSONObject(i) ?: continue
                        val baseUrl = a.optString("baseUrl").ifBlank { a.optString("base_url") }
                            .takeIf { it.startsWith("http") } ?: continue
                        val bandwidth = a.optInt("bandwidth", 128_000)
                        val mime = a.optString("mimeType").ifBlank { a.optString("mime_type") }
                            .ifBlank { "audio/mp4" }

                        val isBetter = when {
                            bestUrl == null -> true
                            quality == AudioQuality.LOW_96 -> bandwidth < bestBps && bandwidth >= 48_000
                            else -> bandwidth > bestBps
                        }
                        if (isBetter) {
                            bestUrl = baseUrl
                            bestBps = bandwidth
                            bestMime = mime
                        }
                    }

                    if (bestUrl != null) {
                        return@use StreamResolution(
                            uri = bestUrl,
                            resolvedBy = MusicSource.ECHO_ADDON,
                            bitrateKbps = (bestBps / 1000).coerceIn(64, 320),
                            mimeType = bestMime,
                            usedFallback = true,
                            headers = mediaHeaders,
                            clientName = "Bilibili_Audio"
                        )
                    }
                }

                // Fallback to direct progressive MP4 if DASH audio array wasn't returned
                val durl = data.optJSONArray("durl")?.optJSONObject(0)
                val fallbackUrl = durl?.optString("url")?.takeIf { it.startsWith("http") } ?: return@use null
                StreamResolution(
                    uri = fallbackUrl,
                    resolvedBy = MusicSource.ECHO_ADDON,
                    bitrateKbps = 128,
                    mimeType = "video/mp4",
                    usedFallback = true,
                    headers = mediaHeaders,
                    clientName = "Bilibili_MP4"
                )
            }
        }.onFailure {
            Log.w(TAG, "Bilibili playurl failed for $bvid: ${it.message}")
        }.getOrNull()
    }

    private fun fetchFirstCid(bvid: String, cookie: String): Long? {
        val url = "https://api.bilibili.com/x/player/pagelist".toHttpUrl().newBuilder()
            .addQueryParameter("bvid", bvid)
            .build()
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BILI_WEB_UA)
                .header("Referer", "https://www.bilibili.com/")
                .header("Cookie", cookie)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string().orEmpty()
                val root = JSONObject(raw)
                if (root.optInt("code", -1) != 0) return@use null
                root.optJSONArray("data")?.optJSONObject(0)?.optLong("cid", 0L)?.takeIf { it > 0L }
            }
        }.onFailure {
            Log.w(TAG, "Bilibili pagelist failed for $bvid: ${it.message}")
        }.getOrNull()
    }

    @Synchronized
    private fun ensureBuvidCookie(): String {
        buvidCookie?.let { return it }
        val fetched = runCatching {
            val req = Request.Builder()
                .url("https://api.bilibili.com/x/frontend/finger/spi")
                .header("User-Agent", BILI_WEB_UA)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val data = JSONObject(resp.body?.string().orEmpty()).optJSONObject("data") ?: return@use null
                val b3 = data.optString("b_3").trim()
                val b4 = data.optString("b_4").trim()
                if (b3.isNotBlank()) "buvid3=$b3; buvid4=$b4;" else null
            }
        }.getOrNull()
        val resolved = fetched ?: "buvid3=${UUID.randomUUID().toString().uppercase()}infoc;"
        buvidCookie = resolved
        return resolved
    }

    private fun parseBiliDurationMs(raw: String): Long {
        val parts = raw.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun decodeHtml(input: String): String {
        if (input.isBlank()) return ""
        return runCatching {
            Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString()
        }.getOrDefault(input)
    }
}
