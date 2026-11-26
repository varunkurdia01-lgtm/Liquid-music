package com.example.data.remote

import android.util.Log
import com.example.domain.model.AudioQuality
import com.example.domain.model.MusicSource
import com.example.domain.model.StreamResolution
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "AudiusFallbackProvider"

/**
 * Open, zero-auth decentralized full-track music stream fallback powered by Audius Discovery Nodes.
 * Strictly verified via [TrackMatcher.selectBestMatch] so it never substitutes an unrelated song.
 */
class AudiusFallbackProvider(
    baseClient: OkHttpClient
) {
    private val httpClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedHost: String? = null

    private data class AudiusTrackCandidate(
        val id: String,
        val title: String,
        val artist: String,
        val durationMs: Long
    )

    suspend fun resolveStream(track: Track, quality: AudioQuality): StreamResolution? = withContext(Dispatchers.IO) {
        val host = resolveDiscoveryHost() ?: return@withContext null

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
                val candidates = searchTracks(host, query)
                val best = TrackMatcher.selectBestMatch(
                    candidates = candidates,
                    title = wantTitle,
                    artist = cleanedArtist.ifBlank { wantArtist },
                    durationMs = track.durationMs.takeIf { it > 0L },
                    titleOf = { it.title },
                    artistOf = { it.artist },
                    durationMsOf = { it.durationMs.takeIf { d -> d > 0L } }
                ) ?: continue

                val streamUrl = "$host/v1/tracks/${best.id}/stream?app_name=LiquidMusic"
                return@withContext StreamResolution(
                    uri = streamUrl,
                    resolvedBy = MusicSource.ECHO_ADDON,
                    bitrateKbps = quality.kbps.coerceAtMost(320),
                    mimeType = "audio/mpeg",
                    usedFallback = true,
                    clientName = "Audius"
                )
            }
        }
        null
    }

    private fun resolveDiscoveryHost(): String? {
        cachedHost?.let { return it }
        runCatching {
            val req = Request.Builder()
                .url("https://api.audius.co")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string().orEmpty()
                val arr = JSONObject(body).optJSONArray("data") ?: return@use
                for (i in 0 until arr.length()) {
                    val host = arr.optString(i).trim().trimEnd('/')
                    if (host.startsWith("https://")) {
                        cachedHost = host
                        return host
                    }
                }
            }
        }.onFailure {
            Log.w(TAG, "Audius host registry lookup failed: ${it.message}")
        }
        return "https://discoveryprovider.audius.co"
    }

    private fun searchTracks(host: String, query: String): List<AudiusTrackCandidate> {
        val url = "$host/v1/tracks/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("query", query)
            ?.addQueryParameter("app_name", "LiquidMusic")
            ?.build() ?: return emptyList()

        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val raw = resp.body?.string().orEmpty()
                if (raw.isBlank()) return@use emptyList()
                val data = JSONObject(raw).optJSONArray("data") ?: return@use emptyList()
                buildList {
                    for (i in 0 until minOf(data.length(), 12)) {
                        val obj = data.optJSONObject(i) ?: continue
                        val id = obj.optString("id").trim()
                        val title = obj.optString("title").trim()
                        val isStreamable = obj.optBoolean("is_streamable", true)
                        if (id.isBlank() || title.isBlank() || !isStreamable) continue
                        val artist = obj.optJSONObject("user")?.optString("name").orEmpty().trim()
                        val durationSec = obj.optLong("duration", 0L)
                        add(
                            AudiusTrackCandidate(
                                id = id,
                                title = title,
                                artist = artist,
                                durationMs = durationSec * 1000L
                            )
                        )
                    }
                }
            }
        }.onFailure {
            Log.w(TAG, "Audius search failed for '$query': ${it.message}")
        }.getOrDefault(emptyList())
    }
}
