package com.example.data.remote

import android.util.Base64
import android.util.Log
import com.example.data.local.CachedLyricsEntity
import com.example.data.local.LiquidDao
import com.example.domain.model.LyricLine
import com.example.domain.model.LyricWord
import com.example.domain.model.LyricsSearchCandidate
import com.example.domain.model.LyricsState
import com.example.domain.model.SoundLabSettings
import com.example.domain.model.Track
import com.example.domain.provider.LyricsSourceProvider
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "LyricsRepository"

/**
 * Multi-provider Synced Karaoke Lyrics Engine with Room persistence, user-configurable provider priority
 * (LRCLIB, KuGou, SimpMusic/YouLy+, YouTube Music), manual candidate picker, and Gemini AI Translation.
 */
class LyricsRepository(
    private val okHttpClient: OkHttpClient,
    private val dao: LiquidDao,
    private val secondaryProviders: List<LyricsSourceProvider> = emptyList()
) {
    private val lrcLineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")
    private val enhancedWordRegex = Regex("""<(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?>([^<]*)""")

    private val translationSchema = Schema.obj(
        properties = mapOf(
            "translations" to Schema.array(
                items = Schema.string(),
                description = "Translated lyric lines in the exact same order and count as the input lines"
            )
        )
    )

    private val translationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.25f
            }
        )
    }

    suspend fun fetchLyrics(
        track: Track,
        settings: SoundLabSettings = SoundLabSettings(),
        forceRefresh: Boolean = false
    ): LyricsState = withContext(Dispatchers.IO) {
        // 1. Check Room cache first for instant offline/repeat availability
        if (!forceRefresh) {
            runCatching { dao.getCachedLyrics(track.id) }.getOrNull()?.let { cached ->
                val restored = decodeCachedEntity(cached)
                if (restored.syncedLines.isNotEmpty() || restored.plainLyrics.isNotBlank() || restored.isInstrumental) {
                    return@withContext restored
                }
            }
        }

        // 2. Follow user-configured provider priority order
        val orderedProviders = settings.lyricsProviderPriority
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("LRCLIB", "KuGou", "SimpMusic", "YouTube Music") }

        var plainFallback: LyricsState? = null

        for (providerKey in orderedProviders) {
            val normalized = providerKey.lowercase()
            val candidate: LyricsState? = when {
                normalized.contains("lrclib") && settings.enableLrcLib -> fetchFromLrcLib(track)
                normalized.contains("kugou") && settings.enableKuGou -> fetchFromKuGou(track)
                (normalized.contains("simp") || normalized.contains("youly") || normalized.contains("better")) && settings.enableYouLyPlus ->
                    fetchFromSimpMusicOrBetter(track)
                normalized.contains("youtube") || normalized.contains("yt") -> fetchFromSecondaryProviders(track)
                else -> null
            }

            if (candidate != null) {
                if (candidate.syncedLines.isNotEmpty() || candidate.isInstrumental) {
                    cacheLyrics(track.id, candidate)
                    return@withContext candidate
                }
                if (candidate.plainLyrics.isNotBlank() && plainFallback == null) {
                    plainFallback = candidate
                }
            }
        }

        if (plainFallback != null) {
            cacheLyrics(track.id, plainFallback)
            return@withContext plainFallback
        }

        // Final safety fallback if user disabled named providers
        fetchFromSecondaryProviders(track)?.let { secondary ->
            cacheLyrics(track.id, secondary)
            return@withContext secondary
        }

        LyricsState(
            isLoading = false,
            errorMessage = "No synced lyrics found for \"${track.title}\"."
        )
    }

    /**
     * Manual lyrics search across LRCLIB and KuGou for the Now Playing Lyrics Picker sheet.
     */
    suspend fun searchLyricsCandidates(query: String): List<LyricsSearchCandidate> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        val results = mutableListOf<LyricsSearchCandidate>()

        // 1. LRCLIB Search
        runCatching {
            val searchUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", trimmed)
                .build()
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "LiquidMusicAndroid/3.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val arr = JSONArray(resp.body?.string().orEmpty())
                for (i in 0 until minOf(arr.length(), 12)) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val synced = obj.optString("syncedLyrics", "").takeIf { it != "null" }.orEmpty()
                    val plain = obj.optString("plainLyrics", "").takeIf { it != "null" }.orEmpty()
                    if (synced.isBlank() && plain.isBlank()) continue
                    results.add(
                        LyricsSearchCandidate(
                            id = "lrclib_${obj.optLong("id", i.toLong())}",
                            title = obj.optString("trackName").ifBlank { obj.optString("name", trimmed) },
                            artist = obj.optString("artistName", "Unknown Artist"),
                            album = obj.optString("albumName", ""),
                            durationSec = obj.optDouble("duration", 0.0).toInt(),
                            syncedLyrics = synced,
                            plainLyrics = plain,
                            sourceName = "LRCLIB"
                        )
                    )
                }
            }
        }.onFailure { Log.w(TAG, "LRCLIB candidate search failed: ${it.message}") }

        // 2. KuGou Search
        runCatching {
            val kgUrl = "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("man", "yes")
                .addQueryParameter("client", "mobi")
                .addQueryParameter("keyword", trimmed)
                .build()
            val req = Request.Builder()
                .url(kgUrl)
                .header("User-Agent", "LiquidMusicAndroid/3.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val root = JSONObject(resp.body?.string().orEmpty())
                val arr = root.optJSONArray("candidates") ?: return@use
                for (i in 0 until minOf(arr.length(), 4)) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val accessKey = obj.optString("accesskey")
                    if (id.isBlank() || accessKey.isBlank()) continue
                    val lrc = downloadKuGouLrc(id, accessKey).orEmpty()
                    if (lrc.isBlank()) continue
                    results.add(
                        LyricsSearchCandidate(
                            id = "kugou_$id",
                            title = obj.optString("song").ifBlank { trimmed },
                            artist = obj.optString("singer").ifBlank { "KuGou" },
                            durationSec = (obj.optLong("duration", 0L) / 1000L).toInt(),
                            syncedLyrics = lrc,
                            plainLyrics = lrc.replace(Regex("""\[[^\]]+]"""), "").trim(),
                            sourceName = "KuGou"
                        )
                    )
                }
            }
        }.onFailure { Log.d(TAG, "KuGou candidate search skipped: ${it.message}") }

        results.sortedByDescending { it.hasSynced }
    }

    suspend fun applyManualLyrics(track: Track, candidate: LyricsSearchCandidate): LyricsState = withContext(Dispatchers.IO) {
        val parsedLines = parseLrcWithKaraoke(candidate.syncedLyrics, track.durationMs)
        val state = LyricsState(
            isLoading = false,
            syncedLines = parsedLines,
            plainLyrics = candidate.plainLyrics.ifBlank { parsedLines.joinToString("\n") { it.text } },
            isInstrumental = false,
            sourceName = "${candidate.sourceName} (Manual)",
            errorMessage = null
        )
        cacheLyrics(track.id, state)
        state
    }

    /**
     * Translates synced or plain lyrics using Firebase AI Logic (gemini-3.8-flash) while preserving exact timestamps,
     * and persists the translated result into Room so future plays toggle instantaneously.
     */
    suspend fun translateLyrics(
        trackId: String,
        currentState: LyricsState,
        targetLanguage: String = "English",
        translateMode: String = "Natural / Poetic"
    ): Result<LyricsState> = withContext(Dispatchers.IO) {
        val sourceLines = if (currentState.syncedLines.isNotEmpty()) {
            currentState.syncedLines.map { it.text }
        } else {
            currentState.plainLyrics.lines().map { it.trim() }.filter { it.isNotBlank() }
        }
        if (sourceLines.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No lyric lines available to translate."))
        }

        try {
            val indexedInput = sourceLines.mapIndexed { idx, line -> "${idx + 1}. $line" }.joinToString("\n")
            val prompt = """
                Translate the following song lyrics into $targetLanguage using style: $translateMode.
                Return a JSON object with "translations" containing EXACTLY ${sourceLines.size} strings, matching the input line-by-line in the same order.
                If a line is already in $targetLanguage, keep it natural and clear without numbering prefixes.

                Lyrics:
                $indexedInput
            """.trimIndent()

            val response = withTimeout(60_000L) {
                translationModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Translation returned empty response.")
            val arr = JSONObject(rawJson).optJSONArray("translations")
                ?: throw IllegalStateException("Malformed translation JSON.")

            val translatedList = (0 until arr.length()).map { arr.optString(it).trim() }

            val updatedSynced = if (currentState.syncedLines.isNotEmpty()) {
                currentState.syncedLines.mapIndexed { idx, line ->
                    line.copy(translatedText = translatedList.getOrNull(idx)?.ifBlank { line.text } ?: line.text)
                }
            } else {
                emptyList()
            }

            val updatedPlain = if (currentState.syncedLines.isEmpty() && translatedList.isNotEmpty()) {
                translatedList.joinToString("\n")
            } else {
                currentState.plainLyrics
            }

            val updatedState = currentState.copy(
                syncedLines = updatedSynced,
                plainLyrics = updatedPlain,
                isTranslating = false,
                showTranslation = true,
                translationLanguage = targetLanguage,
                translationError = null
            )

            cacheLyrics(trackId, updatedState)
            Result.success(updatedState)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Lyrics translation timed out", e)
            Result.failure(Exception("Translation timed out after 60s. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Lyrics translation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to translate lyrics."))
        }
    }

    private suspend fun fetchFromSecondaryProviders(track: Track): LyricsState? {
        for (provider in secondaryProviders) {
            val secondary = runCatching { provider.fetchLyrics(track) }.getOrNull()
            if (secondary != null && (secondary.syncedLines.isNotEmpty() || secondary.plainLyrics.isNotBlank())) {
                return secondary
            }
        }
        return null
    }

    private fun fetchFromLrcLib(track: Track): LyricsState? {
        val cleanTitle = cleanQueryPart(track.title)
        val primaryArtist = cleanQueryPart(track.artist.substringBefore(","))

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
            executeSingle(getUrl.toString())?.let { return it }
        }.onFailure {
            Log.d(TAG, "Exact LRCLIB lookup missed: ${it.message}")
        }

        runCatching {
            val searchUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", "$cleanTitle $primaryArtist")
                .build()
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "LiquidMusicAndroid/3.0")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val array = JSONArray(body)
                    var fallbackObj: JSONObject? = null
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        if (!obj.optString("syncedLyrics").isNullOrBlank()) {
                            return parseLyricsObject(obj, track.durationMs)
                        }
                        if (fallbackObj == null) fallbackObj = obj
                    }
                    fallbackObj?.let { return parseLyricsObject(it, track.durationMs) }
                }
            }
        }.onFailure {
            Log.w(TAG, "LRCLIB search failed: ${it.message}")
        }
        return null
    }

    /**
     * KuGou synced LRC lyrics provider (popular across Echo Music / OuterTune / InnerTune for CJK, Pop & Global tracks).
     */
    private fun fetchFromKuGou(track: Track): LyricsState? {
        val cleanTitle = cleanQueryPart(track.title)
        val primaryArtist = cleanQueryPart(track.artist.substringBefore(","))
        val keyword = "$cleanTitle $primaryArtist".trim()
        if (keyword.isBlank()) return null

        return runCatching {
            val searchUrl = "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("man", "yes")
                .addQueryParameter("client", "mobi")
                .addQueryParameter("keyword", keyword)
                .apply {
                    if (track.durationMs > 0L) {
                        addQueryParameter("duration", track.durationMs.toString())
                    }
                }
                .build()
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "LiquidMusicAndroid/3.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body?.string().orEmpty())
                val arr = root.optJSONArray("candidates") ?: return@use null
                val best = arr.optJSONObject(0) ?: return@use null
                val id = best.optString("id")
                val accessKey = best.optString("accesskey")
                if (id.isBlank() || accessKey.isBlank()) return@use null
                val lrc = downloadKuGouLrc(id, accessKey) ?: return@use null
                val lines = parseLrcWithKaraoke(lrc, track.durationMs)
                if (lines.isEmpty()) return@use null
                LyricsState(
                    isLoading = false,
                    syncedLines = lines,
                    plainLyrics = lines.joinToString("\n") { it.text },
                    isInstrumental = false,
                    sourceName = "KuGou Synced"
                )
            }
        }.onFailure {
            Log.d(TAG, "KuGou lyrics lookup skipped: ${it.message}")
        }.getOrNull()
    }

    private fun downloadKuGouLrc(id: String, accessKey: String): String? {
        val url = "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
            .addQueryParameter("ver", "1")
            .addQueryParameter("client", "pc")
            .addQueryParameter("id", id)
            .addQueryParameter("accesskey", accessKey)
            .addQueryParameter("fmt", "lrc")
            .addQueryParameter("charset", "utf8")
            .build()
        val req = Request.Builder().url(url).header("User-Agent", "LiquidMusicAndroid/3.0").build()
        return okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val contentB64 = JSONObject(resp.body?.string().orEmpty()).optString("content")
            if (contentB64.isBlank()) return@use null
            String(Base64.decode(contentB64, Base64.DEFAULT), Charsets.UTF_8)
        }
    }

    /**
     * SimpMusic / YouLy+ community synced lyrics API (videoId-indexed synced lyrics).
     */
    private fun fetchFromSimpMusicOrBetter(track: Track): LyricsState? {
        val videoId = track.providerTrackId.takeIf { it.length in 8..16 } ?: return null
        return runCatching {
            val url = "https://api-lyrics.simpmusic.org/v1/$videoId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "LiquidMusicAndroid/3.0")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val root = JSONObject(body)
                val dataArr = root.optJSONArray("data")
                val entry = dataArr?.optJSONObject(0) ?: root
                val synced = entry.optString("syncedLyrics").ifBlank { entry.optString("richSyncLyrics") }
                val plain = entry.optString("plainLyrics")
                val lines = parseLrcWithKaraoke(synced, track.durationMs)
                if (lines.isEmpty() && plain.isBlank()) return@use null
                LyricsState(
                    isLoading = false,
                    syncedLines = lines,
                    plainLyrics = plain.ifBlank { lines.joinToString("\n") { it.text } },
                    isInstrumental = false,
                    sourceName = "SimpMusic / YouLy+"
                )
            }
        }.onFailure {
            Log.d(TAG, "SimpMusic lyrics lookup skipped: ${it.message}")
        }.getOrNull()
    }

    private fun executeSingle(url: String): LyricsState? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LiquidMusicAndroid/3.0")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return parseLyricsObject(JSONObject(body), 0L)
        }
    }

    private fun parseLyricsObject(obj: JSONObject, trackDurationMs: Long): LyricsState {
        val instrumental = obj.optBoolean("instrumental", false)
        val syncedRaw = obj.optString("syncedLyrics", "").takeIf { it != "null" }.orEmpty()
        val plainRaw = obj.optString("plainLyrics", "").takeIf { it != "null" }.orEmpty()

        val parsedLines = parseLrcWithKaraoke(syncedRaw, trackDurationMs)
        return LyricsState(
            isLoading = false,
            syncedLines = parsedLines,
            plainLyrics = plainRaw,
            isInstrumental = instrumental && parsedLines.isEmpty() && plainRaw.isBlank(),
            sourceName = "LRCLIB",
            errorMessage = if (!instrumental && parsedLines.isEmpty() && plainRaw.isBlank()) {
                "Lyrics unavailable for this track."
            } else null
        )
    }

    fun parseLrcWithKaraoke(lrcContent: String, trackDurationMs: Long = 0L): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()
        val rawEntries = mutableListOf<Pair<Long, String>>()

        lrcContent.lineSequence().forEach { line ->
            val match = lrcLineRegex.find(line.trim()) ?: return@forEach
            val ts = parseTimeTag(match.groupValues[1], match.groupValues[2], match.groupValues[3]) ?: return@forEach
            val body = match.groupValues[4].trim()
            if (body.isNotEmpty()) {
                rawEntries.add(ts to body)
            }
        }

        val sorted = rawEntries.sortedBy { it.first }
        return sorted.mapIndexed { idx, (startMs, rawBody) ->
            val nextStartMs = sorted.getOrNull(idx + 1)?.first
                ?: (if (trackDurationMs > startMs) trackDurationMs else startMs + 4_500L)
            val endMs = nextStartMs.coerceAtLeast(startMs + 800L)

            val enhancedMatches = enhancedWordRegex.findAll(rawBody).toList()
            if (enhancedMatches.isNotEmpty()) {
                val words = enhancedMatches.mapIndexedNotNull { wIdx, m ->
                    val wStart = parseTimeTag(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: return@mapIndexedNotNull null
                    val wEnd = enhancedMatches.getOrNull(wIdx + 1)?.let {
                        parseTimeTag(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                    } ?: endMs
                    val wText = m.groupValues[4].trim()
                    if (wText.isEmpty()) null else LyricWord(wStart, wEnd.coerceAtLeast(wStart + 120L), wText)
                }
                val cleanLineText = words.joinToString(" ") { it.text }.ifBlank { rawBody.replace(enhancedWordRegex, "$4").trim() }
                LyricLine(timestampMs = startMs, endMs = endMs, text = cleanLineText, words = words)
            } else {
                val tokens = rawBody.split(Regex("""\s+""")).filter { it.isNotBlank() }
                val totalChars = tokens.sumOf { it.length }.coerceAtLeast(1)
                val lineSpan = (endMs - startMs).coerceIn(600L, 8_000L)
                var cursor = startMs
                val words = tokens.map { token ->
                    val share = ((token.length.toFloat() / totalChars.toFloat()) * lineSpan).toLong().coerceAtLeast(120L)
                    val wEnd = (cursor + share).coerceAtMost(endMs)
                    val word = LyricWord(startMs = cursor, endMs = wEnd, text = token)
                    cursor = wEnd
                    word
                }
                LyricLine(timestampMs = startMs, endMs = endMs, text = rawBody, words = words)
            }
        }
    }

    private fun parseTimeTag(minStr: String, secStr: String, fracStr: String): Long? {
        val minutes = minStr.toLongOrNull() ?: return null
        val seconds = secStr.toLongOrNull() ?: return null
        val millis = when (fracStr.length) {
            0 -> 0L
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> fracStr.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }

    private suspend fun cacheLyrics(trackId: String, state: LyricsState) {
        runCatching {
            val syncedArr = JSONArray()
            val transArr = JSONArray()
            state.syncedLines.forEach { line ->
                syncedArr.put(
                    JSONObject().apply {
                        put("ts", line.timestampMs)
                        put("end", line.endMs)
                        put("text", line.text)
                        val wArr = JSONArray()
                        line.words.forEach { w ->
                            wArr.put(JSONObject().apply {
                                put("s", w.startMs)
                                put("e", w.endMs)
                                put("t", w.text)
                            })
                        }
                        put("words", wArr)
                    }
                )
                transArr.put(line.translatedText.orEmpty())
            }
            val hasTranslations = state.syncedLines.any { !it.translatedText.isNullOrBlank() }
            dao.upsertCachedLyrics(
                CachedLyricsEntity(
                    trackId = trackId,
                    sourceName = state.sourceName,
                    syncedJson = syncedArr.toString(),
                    plainLyrics = state.plainLyrics,
                    translatedJson = if (hasTranslations) transArr.toString() else "",
                    translationLanguage = if (hasTranslations) state.translationLanguage else "",
                    isInstrumental = state.isInstrumental
                )
            )
        }
    }

    private fun decodeCachedEntity(entity: CachedLyricsEntity): LyricsState {
        val translations = runCatching {
            if (entity.translatedJson.isBlank()) emptyList()
            else {
                val arr = JSONArray(entity.translatedJson)
                (0 until arr.length()).map { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            }
        }.getOrDefault(emptyList())

        val lines = runCatching {
            val arr = JSONArray(entity.syncedJson)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val wArr = obj.optJSONArray("words")
                val words = if (wArr != null) {
                    (0 until wArr.length()).mapNotNull { wi ->
                        val wo = wArr.optJSONObject(wi) ?: return@mapNotNull null
                        LyricWord(wo.optLong("s"), wo.optLong("e"), wo.optString("t"))
                    }
                } else emptyList()
                LyricLine(
                    timestampMs = obj.optLong("ts"),
                    endMs = obj.optLong("end"),
                    text = obj.optString("text"),
                    words = words,
                    translatedText = translations.getOrNull(i)
                )
            }
        }.getOrDefault(emptyList())

        return LyricsState(
            isLoading = false,
            syncedLines = lines,
            plainLyrics = entity.plainLyrics,
            isInstrumental = entity.isInstrumental,
            sourceName = entity.sourceName.ifBlank { "Cached" },
            translationLanguage = entity.translationLanguage.ifBlank { "English" },
            showTranslation = false
        )
    }

    private fun cleanQueryPart(value: String): String {
        return value
            .replace(Regex("""\s*\(.*?\)"""), "")
            .replace(Regex("""\s*\[.*?]"""), "")
            .replace(Regex("""\s*-\s*From.*""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
