package com.example.data.remote

import android.text.Html
import android.util.Base64
import android.util.Log
import com.example.domain.model.MusicSource
import com.example.domain.model.SourceType
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

private const val TAG = "SaavnMusicRepo"

/**
 * High-bitrate 320kbps audio extension & JioSaavn fallback repository.
 * Supports:
 * 1. Custom user-configured 320kbps / lossless extension server URL (from Echo Music Extension Settings)
 * 2. saavn.sumit.co API
 * 3. Direct official www.jiosaavn.com API with native DES-ECB encrypted_media_url decryption (320kbps AAC/M4A)
 * Zero hardcoded dummy tracks.
 */
class SaavnMusicRepository(
    private val okHttpClient: OkHttpClient
) {
    @Volatile
    var customBaseUrl: String = "https://saavn.sumit.co/api"

    private val defaultEndpoints = listOf(
        "https://saavn.sumit.co/api/search/songs",
        "https://jiosaavn-api-privatecvc2.vercel.app/search/songs"
    )

    suspend fun searchSongs(query: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val customSearchEndpoint = customBaseUrl.trim().trimEnd('/').takeIf { it.startsWith("http") }?.let {
            if (it.endsWith("/search/songs")) it else "$it/search/songs"
        }
        val endpointsToTry = (listOfNotNull(customSearchEndpoint) + defaultEndpoints).distinct()

        for (endpoint in endpointsToTry) {
            try {
                val httpUrl = endpoint.toHttpUrlOrNull() ?: continue
                val url = httpUrl.newBuilder()
                    .addQueryParameter("query", trimmed)
                    .addQueryParameter("limit", limit.toString())
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) EchoLiquidMusic/2.0")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Endpoint $endpoint returned HTTP ${response.code}")
                        return@use
                    }
                    val raw = response.body?.string().orEmpty()
                    val parsed = parseSaavnResponse(raw)
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed.take(limit)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Endpoint $endpoint failed for query='$trimmed': ${e.message}")
            }
        }

        // Direct official JioSaavn v4 API fallback with native DES decryption
        searchOfficialJioSaavn(trimmed, limit)
    }

    private fun searchOfficialJioSaavn(query: String, limit: Int): List<Track> {
        return try {
            val url = "https://www.jiosaavn.com/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("__call", "search.getResults")
                .addQueryParameter("_format", "json")
                .addQueryParameter("_marker", "0")
                .addQueryParameter("api_version", "4")
                .addQueryParameter("ctx", "web6dot0")
                .addQueryParameter("cc", "in")
                .addQueryParameter("includeMetaTags", "1")
                .addQueryParameter("n", limit.toString())
                .addQueryParameter("p", "1")
                .addQueryParameter("q", query)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return emptyList()
                val root = JSONObject(raw)
                val results = root.optJSONArray("results") ?: return emptyList()
                val tracks = mutableListOf<Track>()
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val id = item.optString("id").ifBlank { continue }
                    val title = decodeHtml(item.optString("title").ifBlank { item.optString("song") })
                    if (title.isBlank()) continue
                    val moreInfo = item.optJSONObject("more_info")
                    val encryptedUrl = moreInfo?.optString("encrypted_media_url")
                        ?.ifBlank { item.optString("encrypted_media_url") }
                        .orEmpty()
                    val baseDecrypted = decryptSaavnMediaUrl(encryptedUrl)
                    if (baseDecrypted.isBlank()) continue
                    val has320 = moreInfo?.optString("320kbps").equals("true", ignoreCase = true) ||
                        item.optString("320kbps").equals("true", ignoreCase = true)
                    val suffixRegex = Regex("""_\d+(_p)?\.mp4$""")
                    val decrypted96 = suffixRegex.replace(baseDecrypted, "_96.mp4")
                    val decrypted160 = suffixRegex.replace(baseDecrypted, "_160.mp4")
                    val decrypted320 = if (has320) suffixRegex.replace(baseDecrypted, "_320.mp4") else decrypted160

                    val artist = decodeHtml(
                        moreInfo?.optJSONObject("artistMap")
                            ?.optJSONArray("primary_artists")
                            ?.let { arr ->
                                (0 until arr.length()).mapNotNull { idx ->
                                    arr.optJSONObject(idx)?.optString("name")?.takeIf { it.isNotBlank() }
                                }.joinToString(", ")
                            }
                            .orEmpty()
                            .ifBlank { item.optString("subtitle") }
                    ).ifBlank { "Unknown Artist" }

                    val album = decodeHtml(moreInfo?.optString("album").orEmpty())
                    val durationSec = moreInfo?.optLong("duration", 0L) ?: 0L
                    val artwork = item.optString("image").replace("150x150", "500x500")

                    tracks.add(
                        Track(
                            id = "saavn_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationSec * 1000L,
                            artworkUrl = artwork,
                            streamUrl96 = decrypted96,
                            streamUrl160 = decrypted160,
                            streamUrl320 = decrypted320,
                            sourceType = SourceType.ONLINE,
                            sourceProvider = MusicSource.JIOSAAVN,
                            providerTrackId = id
                        )
                    )
                }
                tracks
            }
        } catch (e: Exception) {
            Log.w(TAG, "Official JioSaavn fallback failed: ${e.message}")
            emptyList()
        }
    }

    private fun decryptSaavnMediaUrl(encryptedBase64: String): String {
        if (encryptedBase64.isBlank()) return ""
        return try {
            val keySpec = SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decoded = Base64.decode(encryptedBase64.trim(), Base64.DEFAULT)
            val decrypted = String(cipher.doFinal(decoded), Charsets.UTF_8).trim()
            decrypted.replace("http://", "https://")
        } catch (_: Exception) {
            ""
        }
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
                    sourceType = SourceType.ONLINE,
                    sourceProvider = MusicSource.JIOSAAVN,
                    providerTrackId = id
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

/**
 * Legacy reference stub — contains ZERO hardcoded or dummy songs so fake tracks never appear in UI.
 */
object FallbackMusicCatalog {
    val seedTracks: List<Track> = emptyList()
    fun filterByQuery(query: String): List<Track> = emptyList()
}
