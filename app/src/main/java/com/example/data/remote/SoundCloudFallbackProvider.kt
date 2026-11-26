package com.example.data.remote

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
import java.util.concurrent.TimeUnit

private const val TAG = "SoundCloudFallback"
private const val SC_WEB_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

/**
 * Dynamic, zero-auth global audio stream failover powered by SoundCloud's public web API v2.
 * Automatically scrapes & caches a fresh public `client_id` from SoundCloud's web bundle,
 * filters out 30-second `SNIP` previews so only full-length tracks play, verifies exact
 * title/artist/duration identity via [TrackMatcher.selectBestMatch], and resolves either
 * progressive MP3 or 160kbps AAC HLS streams.
 */
class SoundCloudFallbackProvider(
    baseClient: OkHttpClient
) {
    private val httpClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedClientId: String? = null

    @Volatile
    private var clientIdFetchedAtMs: Long = 0L

    private data class ScTranscoding(
        val url: String,
        val protocol: String,
        val mimeType: String,
        val preset: String,
        val snipped: Boolean
    )

    private data class ScCandidate(
        val id: Long,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val fullDurationMs: Long,
        val policy: String,
        val transcodings: List<ScTranscoding>
    )

    suspend fun resolveStream(track: Track, quality: AudioQuality): StreamResolution? = withContext(Dispatchers.IO) {
        val clientId = ensureClientId() ?: return@withContext null

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
                val rawCandidates = searchTracks(query, clientId)
                // Only keep full-length playable tracks (strictly skip 30-second Go+ SNIP previews!)
                val fullCandidates = rawCandidates.filter { cand ->
                    val isSnip = cand.policy.equals("SNIP", ignoreCase = true) ||
                        cand.policy.equals("BLOCK", ignoreCase = true) ||
                        (cand.fullDurationMs > 60_000L && cand.durationMs in 1..35_000L)
                    !isSnip && cand.transcodings.any { !it.snipped }
                }
                if (fullCandidates.isEmpty()) continue

                // SoundCloud uploads often format title as "Artist - Song Title" under a repost/archive account.
                // Include parsed title artist tokens alongside uploader name while keeping strict TrackMatcher rules.
                val best = TrackMatcher.selectBestMatch(
                    candidates = fullCandidates,
                    title = wantTitle,
                    artist = cleanedArtist.ifBlank { wantArtist },
                    durationMs = track.durationMs.takeIf { it > 0L },
                    titleOf = { it.title },
                    artistOf = { cand ->
                        listOf(cand.artist, cand.title).filter { it.isNotBlank() }.joinToString(" ")
                    },
                    durationMsOf = { it.durationMs.takeIf { d -> d > 0L } }
                ) ?: continue

                val resolved = resolveBestTranscoding(best, clientId, quality)
                if (resolved != null) return@withContext resolved
            }
        }
        null
    }

    private fun resolveBestTranscoding(
        candidate: ScCandidate,
        clientId: String,
        quality: AudioQuality
    ): StreamResolution? {
        val playable = candidate.transcodings.filter { !it.snipped && it.url.startsWith("http") }
        if (playable.isEmpty()) return null

        // Prefer direct progressive MP3 first (instant seeking + SimpleCache), then 160k AAC HLS, then MP3 HLS
        val ordered = playable.sortedWith(
            compareByDescending<ScTranscoding> { it.protocol == "progressive" }
                .thenByDescending { it.preset.contains("aac_160", ignoreCase = true) }
                .thenByDescending { it.preset.contains("mp3", ignoreCase = true) }
                .thenByDescending { it.preset.contains("aac", ignoreCase = true) }
        )

        for (tc in ordered) {
            val streamUrl = fetchSignedStreamUrl(tc.url, clientId) ?: continue
            val isHls = tc.protocol == "hls" ||
                streamUrl.contains(".m3u8", ignoreCase = true) ||
                streamUrl.contains("media-streaming.soundcloud.cloud", ignoreCase = true)
            val kbps = when {
                tc.preset.contains("160") -> 160
                tc.preset.contains("96") -> 96
                tc.preset.contains("mp3") -> 128
                else -> quality.kbps.coerceAtMost(160)
            }
            return StreamResolution(
                uri = streamUrl,
                resolvedBy = MusicSource.ECHO_ADDON,
                bitrateKbps = kbps,
                mimeType = if (isHls) "application/x-mpegURL" else tc.mimeType.ifBlank { "audio/mpeg" },
                usedFallback = true,
                headers = mapOf("User-Agent" to SC_WEB_UA),
                clientName = if (isHls) "SoundCloud_HLS" else "SoundCloud"
            )
        }
        return null
    }

    private fun fetchSignedStreamUrl(transcodingUrl: String, clientId: String): String? {
        val sep = if (transcodingUrl.contains("?")) "&" else "?"
        val url = "${transcodingUrl}${sep}client_id=$clientId"
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", SC_WEB_UA)
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    cachedClientId = null
                    return@use null
                }
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                JSONObject(body).optString("url").takeIf { it.startsWith("http") }
            }
        }.onFailure {
            Log.w(TAG, "SoundCloud transcoding resolve failed: ${it.message}")
        }.getOrNull()
    }

    private fun searchTracks(query: String, clientId: String): List<ScCandidate> {
        val url = "https://api-v2.soundcloud.com/search/tracks".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("limit", "12")
            .build()

        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", SC_WEB_UA)
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    cachedClientId = null
                    return@use emptyList()
                }
                if (!resp.isSuccessful) return@use emptyList()
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) return@use emptyList()
                val collection = JSONObject(raw).optJSONArray("collection") ?: return@use emptyList()
                buildList {
                    for (i in 0 until collection.length()) {
                        val item = collection.optJSONObject(i) ?: continue
                        val id = item.optLong("id", 0L)
                        val title = item.optString("title").trim()
                        if (id <= 0L || title.isBlank()) continue
                        val artist = item.optJSONObject("user")?.optString("username").orEmpty().trim()
                        val durationMs = item.optLong("duration", 0L)
                        val fullDurationMs = item.optLong("full_duration", durationMs)
                        val policy = item.optString("policy", "ALLOW")
                        val tcArr = item.optJSONObject("media")?.optJSONArray("transcodings") ?: continue
                        val tcs = mutableListOf<ScTranscoding>()
                        for (j in 0 until tcArr.length()) {
                            val tc = tcArr.optJSONObject(j) ?: continue
                            val tcUrl = tc.optString("url")
                            if (!tcUrl.startsWith("http")) continue
                            val fmt = tc.optJSONObject("format")
                            val protocol = fmt?.optString("protocol").orEmpty()
                            val mime = fmt?.optString("mime_type").orEmpty()
                            val preset = tc.optString("preset").orEmpty()
                            val snipped = tc.optBoolean("snipped", false)
                            if (protocol == "progressive" || protocol == "hls") {
                                tcs.add(ScTranscoding(tcUrl, protocol, mime, preset, snipped))
                            }
                        }
                        if (tcs.isNotEmpty()) {
                            add(ScCandidate(id, title, artist, durationMs, fullDurationMs, policy, tcs))
                        }
                    }
                }
            }
        }.onFailure {
            Log.w(TAG, "SoundCloud search failed for '$query': ${it.message}")
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun ensureClientId(): String? {
        val now = System.currentTimeMillis()
        val existing = cachedClientId
        if (!existing.isNullOrBlank() && (now - clientIdFetchedAtMs) < CLIENT_ID_TTL_MS) {
            return existing
        }
        return runCatching {
            val html = httpClient.newCall(
                Request.Builder().url("https://soundcloud.com").header("User-Agent", SC_WEB_UA).build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                resp.body?.string().orEmpty()
            }
            val scriptUrls = Regex("""https://a-v2\.sndcdn\.com/assets/[^"]+\.js""")
                .findAll(html)
                .map { it.value }
                .toList()
            for (jsUrl in scriptUrls.takeLast(6).reversed()) {
                val js = httpClient.newCall(
                    Request.Builder().url(jsUrl).header("User-Agent", SC_WEB_UA).build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use ""
                    resp.body?.string().orEmpty()
                }
                val match = Regex("""client_id:"([a-zA-Z0-9]{20,40})"""").find(js)
                    ?: Regex("""client_id=([a-zA-Z0-9]{20,40})""").find(js)
                if (match != null) {
                    val found = match.groupValues[1]
                    cachedClientId = found
                    clientIdFetchedAtMs = now
                    return@runCatching found
                }
            }
            null
        }.onFailure {
            Log.w(TAG, "SoundCloud client_id discovery failed: ${it.message}")
        }.getOrNull()
    }

    companion object {
        private const val CLIENT_ID_TTL_MS = 6L * 60L * 60L * 1000L
    }
}
