package com.example.data.remote

import android.util.Log
import com.example.domain.model.LyricLine
import com.example.domain.model.LyricsState
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "LyricsRepository"

class LyricsRepository(
    private val okHttpClient: OkHttpClient
) {
    private val lrcLineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")

    suspend fun fetchLyrics(track: Track): LyricsState = withContext(Dispatchers.IO) {
        val cleanTitle = cleanQueryPart(track.title)
        val primaryArtist = cleanQueryPart(track.artist.substringBefore(","))

        // 1. Try exact match /api/get
        runCatching {
            val getUrl = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", cleanTitle)
                .addQueryParameter("artist_name", primaryArtist)
                .apply {
                    if (track.durationMs > 0) {
                        addQueryParameter("duration", (track.durationMs / 1000L).toString())
                    }
                }
                .build()

            executeSingle(getUrl.toString())?.let { return@withContext it }
        }.onFailure {
            Log.d(TAG, "Exact LRCLIB lookup missed: ${it.message}")
        }

        // 2. Fallback to /api/search
        runCatching {
            val searchUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", "$cleanTitle $primaryArtist")
                .build()

            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "LiquidMusicAndroid/1.0")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val array = JSONArray(body)
                    // Prefer an entry with syncedLyrics
                    var fallbackObj: JSONObject? = null
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        if (!obj.optString("syncedLyrics").isNullOrBlank()) {
                            return@withContext parseLyricsObject(obj)
                        }
                        if (fallbackObj == null) fallbackObj = obj
                    }
                    fallbackObj?.let { return@withContext parseLyricsObject(it) }
                }
            }
        }.onFailure {
            Log.w(TAG, "LRCLIB search failed: ${it.message}")
        }

        LyricsState(
            isLoading = false,
            errorMessage = "No synced lyrics found for \"${track.title}\"."
        )
    }

    private fun executeSingle(url: String): LyricsState? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LiquidMusicAndroid/1.0")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return parseLyricsObject(JSONObject(body))
        }
    }

    private fun parseLyricsObject(obj: JSONObject): LyricsState {
        val instrumental = obj.optBoolean("instrumental", false)
        val syncedRaw = obj.optString("syncedLyrics", "").takeIf { it != "null" }.orEmpty()
        val plainRaw = obj.optString("plainLyrics", "").takeIf { it != "null" }.orEmpty()

        val parsedLines = parseLrc(syncedRaw)
        return LyricsState(
            isLoading = false,
            syncedLines = parsedLines,
            plainLyrics = plainRaw,
            isInstrumental = instrumental && parsedLines.isEmpty() && plainRaw.isBlank(),
            errorMessage = if (!instrumental && parsedLines.isEmpty() && plainRaw.isBlank()) {
                "Lyrics unavailable for this track."
            } else null
        )
    }

    fun parseLrc(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        val result = mutableListOf<LyricLine>()
        lrcContent.lineSequence().forEach { line ->
            val match = lrcLineRegex.find(line.trim()) ?: return@forEach
            val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
            val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
            val fracStr = match.groupValues[3]
            val millis = when (fracStr.length) {
                0 -> 0L
                1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                else -> fracStr.take(3).toLongOrNull() ?: 0L
            }
            val text = match.groupValues[4].trim()
            if (text.isNotEmpty()) {
                val ts = minutes * 60_000L + seconds * 1_000L + millis
                result.add(LyricLine(timestampMs = ts, text = text))
            }
        }
        return result.sortedBy { it.timestampMs }
    }

    private fun cleanQueryPart(value: String): String {
        return value
            .replace(Regex("""\s*\(.*?\)"""), "")
            .replace(Regex("""\s*\[.*?]"""), "")
            .replace(Regex("""\s*-\s*From.*""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
