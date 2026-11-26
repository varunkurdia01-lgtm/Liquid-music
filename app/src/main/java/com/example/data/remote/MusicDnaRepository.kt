package com.example.data.remote

import android.util.Log
import com.example.domain.model.Track
import com.example.domain.model.TrackDnaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "MusicDnaRepository"

/**
 * Echo Music "Music DNA" repository: fetches real song background, release context, annotations,
 * and credits from Genius public API and Wikipedia REST API with in-memory caching.
 */
class MusicDnaRepository(
    private val okHttpClient: OkHttpClient
) {
    private val cache = ConcurrentHashMap<String, TrackDnaInfo>()

    suspend fun fetchTrackDna(track: Track): TrackDnaInfo = withContext(Dispatchers.IO) {
        cache[track.id]?.let { return@withContext it }

        val cleanTitle = TrackMatcher.cleanTitleForSearch(track.title)
        val primaryArtist = track.artist.split(",", "&", "•").firstOrNull()?.trim().orEmpty()

        val result = coroutineScope {
            val geniusDef = async { fetchFromGenius(cleanTitle, primaryArtist) }
            val wikiDef = async { fetchFromWikipedia(cleanTitle, primaryArtist) }

            val genius = geniusDef.await()
            val wiki = wikiDef.await()

            val sources = buildList {
                if (genius != null) add("Genius")
                if (wiki != null) add("Wikipedia")
            }.joinToString(" · ").ifBlank { "Track Metadata" }

            val story = buildString {
                if (!wiki?.extract.isNullOrBlank()) {
                    append(wiki!!.extract.trim())
                }
                if (!genius?.description.isNullOrBlank() && genius!!.description != wiki?.extract) {
                    if (isNotEmpty()) append("\n\n")
                    append(genius.description.trim())
                }
            }.ifBlank {
                "\"${track.title}\" by ${track.artist}" +
                    (if (track.album.isNotBlank()) " from the album ${track.album}." else ".")
            }

            val annotations = buildList {
                genius?.annotations?.forEach { if (it.isNotBlank()) add(it) }
                if (wiki?.description?.isNotBlank() == true) {
                    add("Encyclopedia tag: ${wiki.description}")
                }
                if (track.album.isNotBlank()) {
                    add("Featured on ${track.album}")
                }
            }.distinct()

            TrackDnaInfo(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                storySummary = story,
                annotations = annotations,
                releaseDate = genius?.releaseDate.orEmpty(),
                writers = genius?.writers.orEmpty(),
                producers = genius?.producers.orEmpty(),
                albumContext = genius?.albumName?.ifBlank { track.album } ?: track.album,
                sourceLabel = sources,
                externalUrl = genius?.url?.ifBlank { wiki?.url.orEmpty() } ?: wiki?.url.orEmpty()
            )
        }

        cache[track.id] = result
        result
    }

    private data class GeniusSnippet(
        val description: String = "",
        val releaseDate: String = "",
        val albumName: String = "",
        val writers: List<String> = emptyList(),
        val producers: List<String> = emptyList(),
        val annotations: List<String> = emptyList(),
        val url: String = ""
    )

    private data class WikiSnippet(
        val extract: String = "",
        val description: String = "",
        val url: String = ""
    )

    private fun fetchFromGenius(title: String, artist: String): GeniusSnippet? {
        val query = "$title $artist".trim()
        if (query.isBlank()) return null
        return runCatching {
            val url = "https://genius.com/api/search/multi".toHttpUrl().newBuilder()
                .addQueryParameter("per_page", "5")
                .addQueryParameter("q", query)
                .build()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 15; Mobile) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body?.string().orEmpty())
                val sections = root.optJSONObject("response")?.optJSONArray("sections") ?: return@use null
                var songObj: JSONObject? = null
                for (i in 0 until sections.length()) {
                    val sec = sections.optJSONObject(i) ?: continue
                    val type = sec.optString("type")
                    if (type == "song" || type == "top_hit") {
                        val hits = sec.optJSONArray("hits") ?: continue
                        for (j in 0 until hits.length()) {
                            val hit = hits.optJSONObject(j) ?: continue
                            if (hit.optString("type") == "song") {
                                songObj = hit.optJSONObject("result")
                                if (songObj != null) break
                            }
                        }
                    }
                    if (songObj != null) break
                }
                val song = songObj ?: return@use null
                val songId = song.optLong("id", -1L)
                val songUrl = song.optString("url")
                val releaseDate = song.optString("release_date_for_display")
                    .ifBlank { song.optString("release_date_with_abbreviated_month_for_display") }
                val primaryArtistName = song.optJSONObject("primary_artist")?.optString("name").orEmpty()
                val featuredArtists = mutableListOf<String>()
                val featArr = song.optJSONArray("featured_artists")
                if (featArr != null) {
                    for (k in 0 until featArr.length()) {
                        featArr.optJSONObject(k)?.optString("name")?.takeIf { it.isNotBlank() }?.let {
                            featuredArtists.add(it)
                        }
                    }
                }

                // Try Genius public song detail endpoint for credits & description
                val details = if (songId > 0L) fetchGeniusSongDetail(songId) else null
                val annotations = buildList {
                    val pyongs = song.optInt("pyongs_count", 0)
                    val pageviews = song.optJSONObject("stats")?.optLong("pageviews", 0L) ?: 0L
                    if (pageviews > 0L) {
                        add("Genius lyric lookups: ${formatCount(pageviews)} views")
                    }
                    if (pyongs > 0) {
                        add("Community highlights: $pyongs Genius pyongs")
                    }
                    if (featuredArtists.isNotEmpty()) {
                        add("Featuring: ${featuredArtists.joinToString(", ")}")
                    }
                    details?.annotations?.forEach { add(it) }
                }

                GeniusSnippet(
                    description = details?.description.orEmpty(),
                    releaseDate = details?.releaseDate?.ifBlank { releaseDate } ?: releaseDate,
                    albumName = details?.albumName.orEmpty(),
                    writers = details?.writers.orEmpty().ifEmpty { listOfNotNull(primaryArtistName.takeIf { it.isNotBlank() }) },
                    producers = details?.producers.orEmpty(),
                    annotations = annotations,
                    url = songUrl
                )
            }
        }.onFailure {
            Log.d(TAG, "Genius DNA lookup skipped: ${it.message}")
        }.getOrNull()
    }

    private fun fetchGeniusSongDetail(songId: Long): GeniusSnippet? {
        return runCatching {
            val req = Request.Builder()
                .url("https://genius.com/api/songs/$songId")
                .header("User-Agent", "Mozilla/5.0 (Android 15; Mobile) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val song = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("response")
                    ?.optJSONObject("song")
                    ?: return@use null

                val descPlain = song.optJSONObject("description")?.optString("plain").orEmpty()
                    .takeIf { it.isNotBlank() && it != "?" }
                    .orEmpty()
                val albumName = song.optJSONObject("album")?.optString("name").orEmpty()
                val releaseDate = song.optString("release_date_for_display")
                val writers = extractArtistNames(song.optJSONArray("writer_artists"))
                val producers = extractArtistNames(song.optJSONArray("producer_artists"))
                val extraNotes = buildList {
                    val loc = song.optString("recording_location")
                    if (loc.isNotBlank()) add("Recorded at: $loc")
                    val rels = song.optJSONArray("song_relationships")
                    if (rels != null) {
                        for (i in 0 until rels.length()) {
                            val rel = rels.optJSONObject(i) ?: continue
                            val type = rel.optString("relationship_type")
                            val songs = rel.optJSONArray("songs") ?: continue
                            if (songs.length() > 0 && (type == "samples" || type == "sampled_in")) {
                                val firstTitle = songs.optJSONObject(0)?.optString("full_title").orEmpty()
                                if (firstTitle.isNotBlank()) {
                                    val label = if (type == "samples") "Samples" else "Sampled in"
                                    add("$label: $firstTitle")
                                }
                            }
                        }
                    }
                }
                GeniusSnippet(
                    description = descPlain,
                    releaseDate = releaseDate,
                    albumName = albumName,
                    writers = writers,
                    producers = producers,
                    annotations = extraNotes
                )
            }
        }.getOrNull()
    }

    private fun fetchFromWikipedia(title: String, artist: String): WikiSnippet? {
        if (title.isBlank()) return null
        val candidates = listOf(
            "$title (${artist} song)",
            "$title (song)",
            title
        )
        for (pageTitle in candidates) {
            val snippet = fetchWikipediaSummaryByTitle(pageTitle)
            if (snippet != null && snippet.extract.length > 60) {
                val lower = (snippet.extract + " " + snippet.description).lowercase()
                val artistToken = artist.lowercase().split(" ").firstOrNull { it.length >= 3 }.orEmpty()
                if (lower.contains("song") || lower.contains("single") || lower.contains("album") ||
                    (artistToken.isNotBlank() && lower.contains(artistToken))
                ) {
                    return snippet
                }
            }
        }

        // OpenSearch fallback
        return runCatching {
            val searchUrl = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "opensearch")
                .addQueryParameter("search", "$title $artist song")
                .addQueryParameter("limit", "3")
                .addQueryParameter("namespace", "0")
                .addQueryParameter("format", "json")
                .build()
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "LiquidMusicAndroid/3.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = org.json.JSONArray(resp.body?.string().orEmpty())
                val titles = arr.optJSONArray(1) ?: return@use null
                val firstTitle = titles.optString(0).takeIf { it.isNotBlank() } ?: return@use null
                fetchWikipediaSummaryByTitle(firstTitle)
            }
        }.getOrNull()
    }

    private fun fetchWikipediaSummaryByTitle(pageTitle: String): WikiSnippet? {
        return runCatching {
            val encoded = URLEncoder.encode(pageTitle.replace(" ", "_"), "UTF-8").replace("+", "%20")
            val req = Request.Builder()
                .url("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
                .header("User-Agent", "LiquidMusicAndroid/3.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val obj = JSONObject(resp.body?.string().orEmpty())
                val type = obj.optString("type")
                if (type == "disambiguation") return@use null
                val extract = obj.optString("extract").trim()
                if (extract.isBlank()) return@use null
                WikiSnippet(
                    extract = extract,
                    description = obj.optString("description").trim(),
                    url = obj.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page").orEmpty()
                )
            }
        }.getOrNull()
    }

    private fun extractArtistNames(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
        }.distinct()
    }

    private fun formatCount(n: Long): String = when {
        n >= 1_000_000L -> "${"%.1f".format(n / 1_000_000.0)}M"
        n >= 1_000L -> "${"%.1f".format(n / 1_000.0)}K"
        else -> n.toString()
    }
}
