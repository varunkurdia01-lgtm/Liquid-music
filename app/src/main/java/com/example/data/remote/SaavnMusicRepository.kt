package com.example.data.remote

import android.text.Html
import android.util.Log
import com.example.domain.model.SourceType
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "SaavnMusicRepo"

class SaavnMusicRepository(
    private val okHttpClient: OkHttpClient
) {
    private val endpoints = listOf(
        "https://saavn.sumit.co/api/search/songs",
        "https://saavn.dev/api/search/songs"
    )

    suspend fun searchSongs(query: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        for (endpoint in endpoints) {
            try {
                val url = endpoint.toHttpUrl().newBuilder()
                    .addQueryParameter("query", trimmed)
                    .addQueryParameter("limit", limit.toString())
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "LiquidMusicAndroid/1.0")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Endpoint $endpoint returned HTTP ${response.code}")
                        return@use
                    }
                    val raw = response.body?.string().orEmpty()
                    val parsed = parseSaavnResponse(raw)
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Endpoint $endpoint failed for query='$trimmed': ${e.message}")
            }
        }

        // Resilient offline/rate-limit fallback using playable royalty-free streams
        FallbackMusicCatalog.filterByQuery(trimmed).take(limit)
    }

    private fun parseSaavnResponse(rawJson: String): List<Track> {
        if (rawJson.isBlank()) return emptyList()
        val root = JSONObject(rawJson)
        val data = root.optJSONObject("data") ?: return emptyList()
        val results: JSONArray = data.optJSONArray("results") ?: return emptyList()

        val tracks = mutableListOf<Track>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val id = item.optString("id").ifBlank { continue }
            val title = decodeHtml(item.optString("name").ifBlank { item.optString("title") })
            if (title.isBlank()) continue

            val artist = extractArtists(item)
            val album = decodeHtml(item.optJSONObject("album")?.optString("name").orEmpty())
            val durationSec = item.optLong("duration", 0L)
            val durationMs = if (durationSec in 1..36000) durationSec * 1000L else durationSec

            val artworkUrl = extractBestArtwork(item.optJSONArray("image"))
            val urls = extractDownloadUrls(item.optJSONArray("downloadUrl"))
            val url96 = urls["96kbps"].orEmpty()
            val url160 = urls["160kbps"].orEmpty()
            val url320 = urls["320kbps"].orEmpty()

            if (url96.isBlank() && url160.isBlank() && url320.isBlank()) continue

            tracks.add(
                Track(
                    id = "saavn_$id",
                    title = title,
                    artist = artist.ifBlank { "Unknown Artist" },
                    album = album,
                    durationMs = durationMs,
                    artworkUrl = artworkUrl,
                    streamUrl96 = url96,
                    streamUrl160 = url160,
                    streamUrl320 = url320,
                    sourceType = SourceType.ONLINE
                )
            )
        }
        return tracks
    }

    private fun extractArtists(item: JSONObject): String {
        val artistsObj = item.optJSONObject("artists")
        val primary = artistsObj?.optJSONArray("primary")
        if (primary != null && primary.length() > 0) {
            val names = (0 until primary.length()).mapNotNull { idx ->
                primary.optJSONObject(idx)?.optString("name")?.takeIf { it.isNotBlank() }
            }
            if (names.isNotEmpty()) return decodeHtml(names.joinToString(", "))
        }
        val legacy = item.optString("primaryArtists")
        return decodeHtml(legacy)
    }

    private fun extractBestArtwork(images: JSONArray?): String {
        if (images == null || images.length() == 0) return ""
        var bestUrl = ""
        for (i in 0 until images.length()) {
            val obj = images.optJSONObject(i) ?: continue
            val url = obj.optString("url").ifBlank { obj.optString("link") }
            val quality = obj.optString("quality")
            if (quality.contains("500")) return url
            if (url.isNotBlank()) bestUrl = url
        }
        return bestUrl
    }

    private fun extractDownloadUrls(downloads: JSONArray?): Map<String, String> {
        if (downloads == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (i in 0 until downloads.length()) {
            val obj = downloads.optJSONObject(i) ?: continue
            val quality = obj.optString("quality")
            val url = obj.optString("url").ifBlank { obj.optString("link") }
            if (quality.isNotBlank() && url.isNotBlank()) {
                map[quality] = url
            }
        }
        return map
    }

    private fun decodeHtml(input: String): String {
        if (input.isBlank()) return ""
        return Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }
}

object FallbackMusicCatalog {
    val seedTracks: List<Track> = listOf(
        Track(
            id = "seed_midnight_crystal",
            title = "Midnight Crystal",
            artist = "Aether Pulse",
            album = "Aurora Drift",
            durationMs = 372_000L,
            artworkUrl = "https://images.unsplash.com/photo-1614149162883-504ce4d13909?w=600&q=80",
            streamUrl96 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            streamUrl160 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            streamUrl320 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            sourceType = SourceType.ONLINE
        ),
        Track(
            id = "seed_neon_horizon",
            title = "Neon Horizon",
            artist = "Kairo Vance",
            album = "Electric Glass",
            durationMs = 425_000L,
            artworkUrl = "https://images.unsplash.com/photo-1557672172-298e090bd0f1?w=600&q=80",
            streamUrl96 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            streamUrl160 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            streamUrl320 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            sourceType = SourceType.ONLINE
        ),
        Track(
            id = "seed_velvet_bassline",
            title = "Velvet Bassline",
            artist = "Nova & The Waves",
            album = "City Lights Pop",
            durationMs = 344_000L,
            artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80",
            streamUrl96 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            streamUrl160 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            streamUrl320 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            sourceType = SourceType.ONLINE
        ),
        Track(
            id = "seed_chrome_velocity",
            title = "Chrome Velocity",
            artist = "Riku Beats",
            album = "Subzero Trap",
            durationMs = 302_000L,
            artworkUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=600&q=80",
            streamUrl96 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            streamUrl160 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            streamUrl320 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            sourceType = SourceType.ONLINE
        ),
        Track(
            id = "seed_cobalt_rain",
            title = "Cobalt Rain",
            artist = "Sora Lo-Fi",
            album = "After Hours Chill",
            durationMs = 353_000L,
            artworkUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&q=80",
            streamUrl96 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            streamUrl160 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            streamUrl320 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            sourceType = SourceType.ONLINE
        ),
        Track(
            id = "seed_starlight_synth",
            title = "Starlight Synth",
            artist = "Vex & Lumina",
            album = "Synthwave Odyssey",
            durationMs = 280_000L,
            artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&q=80",
            streamUrl96 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            streamUrl160 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            streamUrl320 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            sourceType = SourceType.ONLINE
        ),
        Track(
            id = "seed_glacial_echoes",
            title = "Glacial Echoes",
            artist = "Mirei",
            album = "Deep Space Ambient",
            durationMs = 420_000L,
            artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&q=80",
            streamUrl96 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            streamUrl160 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            streamUrl320 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            sourceType = SourceType.ONLINE
        ),
        Track(
            id = "seed_hyperdrive_anthem",
            title = "Hyperdrive Anthem",
            artist = "DJ Solis",
            album = "Club Pulse 2026",
            durationMs = 325_000L,
            artworkUrl = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=600&q=80",
            streamUrl96 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            streamUrl160 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            streamUrl320 = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            sourceType = SourceType.ONLINE
        )
    )

    fun filterByQuery(query: String): List<Track> {
        val tokens = query.lowercase().split(" ").filter { it.length >= 2 }
        if (tokens.isEmpty()) return seedTracks
        val matched = seedTracks.filter { track ->
            val haystack = "${track.title} ${track.artist} ${track.album}".lowercase()
            tokens.any { haystack.contains(it) }
        }
        return matched.ifEmpty { seedTracks }
    }
}
