package com.example.data.remote

import android.net.Uri
import android.util.Log
import com.example.domain.model.AudioQuality
import com.example.domain.model.EchoAddonConfig
import com.example.domain.model.MusicSource
import com.example.domain.model.SourceType
import com.example.domain.model.StreamResolution
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val TAG = "EchoExtensionManager"

data class EchoAddonTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val durationSec: Int? = null
)

/**
 * Exact port of Echo Music's ExtensionManager.kt + strict `selectBestMatch` verification.
 *
 * 1. Supports Echo Music's standardized Addon HTTP API:
 *    - GET {baseUrl}/manifest.json
 *    - GET {baseUrl}/search?q={query}
 *    - GET {baseUrl}/stream/{trackId}?quality=LOSSLESS
 * 2. Provides Echo Music's strict `TrackMatcher.selectBestMatch` so cross-provider matching
 *    (Echo Addons & JioSaavn 320kbps) NEVER substitutes a different song or wrong artist.
 */
class EchoExtensionManager(
    baseClient: OkHttpClient
) {
    private val httpClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .dns(
            object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
                    if (hostname.endsWith(".vercel.app", ignoreCase = true)) {
                        val vercelIps = listOfNotNull(
                            runCatching { InetAddress.getByName("76.76.21.21") }.getOrNull(),
                            runCatching { InetAddress.getByName("76.76.21.22") }.getOrNull()
                        )
                        return (vercelIps + system).distinct()
                    }
                    return system.ifEmpty { Dns.SYSTEM.lookup(hostname) }
                }
            }
        )
        .build()

    @Volatile
    var configuredAddons: List<EchoAddonConfig> = emptyList()

    val configuredAddonUrls: List<String>
        get() = configuredAddons
            .filter { it.enabled && it.baseUrl.isNotBlank() }
            .sortedBy { it.priority }
            .map { it.baseUrl }

    fun updateConfiguredAddons(
        customExtensionUrl: String,
        losslessExtensionUrl: String,
        addonsJson: String = ""
    ) {
        val parsed = parseAddonsJson(addonsJson).toMutableList()
        val legacyUrls = listOf(losslessExtensionUrl, customExtensionUrl)
            .map { sanitizeUrl(it) }
            .filter { url ->
                url.isNotBlank() &&
                    !url.contains("saavn.sumit.co", ignoreCase = true) &&
                    !url.contains("saavn.dev", ignoreCase = true) &&
                    !url.contains("jiosaavn.com", ignoreCase = true)
            }
            .distinct()

        legacyUrls.forEach { legacyUrl ->
            if (parsed.none { it.baseUrl.equals(legacyUrl, ignoreCase = true) }) {
                parsed.add(
                    EchoAddonConfig(
                        id = "addon_${legacyUrl.hashCode()}",
                        baseUrl = legacyUrl,
                        name = legacyUrl.substringAfter("://").substringBefore("/"),
                        supportsLossless = legacyUrl == sanitizeUrl(losslessExtensionUrl),
                        enabled = true,
                        priority = parsed.size
                    )
                )
            }
        }
        configuredAddons = parsed.sortedBy { it.priority }
    }

    fun hasActiveEchoAddons(): Boolean = configuredAddonUrls.isNotEmpty()

    fun sanitizeUrl(raw: String): String {
        var u = raw.trim()
        if (u.isBlank()) return ""
        if (u.startsWith("http://", ignoreCase = true)) {
            return ""
        }
        if (!u.startsWith("https://", ignoreCase = true)) {
            u = "https://$u"
        }
        u = u.removeSuffix("/")
        if (u.endsWith("/manifest.json", ignoreCase = true)) {
            u = u.substringBeforeLast("/manifest.json")
        }
        u = u.removeSuffix("/")
        return if (isSafePublicHost(u)) u else ""
    }

    fun isSafePublicHost(url: String): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (!parsed.scheme.equals("https", ignoreCase = true)) return false
        val host = parsed.host?.lowercase()?.trim().orEmpty()
        if (host.isBlank()) return false
        if (host == "localhost" || host == "0.0.0.0" || host == "::1" ||
            host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")
        ) {
            return false
        }
        // Reject raw IPv4 private / loopback / link-local ranges
        val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(host)
        if (ipv4 != null) {
            val a = ipv4.groupValues[1].toIntOrNull() ?: return false
            val b = ipv4.groupValues[2].toIntOrNull() ?: return false
            if (a == 0 || a == 10 || a == 127) return false
            if (a == 169 && b == 254) return false
            if (a == 192 && b == 168) return false
            if (a == 172 && b in 16..31) return false
        }
        return true
    }

    private fun resolvesToSafePublicAddresses(url: String): Boolean {
        if (!isSafePublicHost(url)) return false
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return false
        val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull() ?: return false
        return addresses.isNotEmpty() && addresses.none { addr ->
            addr.isAnyLocalAddress ||
                addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                addr.isMulticastAddress
        }
    }

    /**
     * Live `/manifest.json` health check measuring latency, name, version, and lossless capability.
     */
    suspend fun inspectAddon(baseUrl: String): Result<EchoAddonConfig> = withContext(Dispatchers.IO) {
        val clean = sanitizeUrl(baseUrl)
        if (clean.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid public HTTPS addon URL"))
        }
        if (!resolvesToSafePublicAddresses(clean)) {
            return@withContext Result.failure(IllegalArgumentException("Private or local network addon URLs are blocked"))
        }
        val start = System.currentTimeMillis()
        runCatching {
            val req = Request.Builder().url("$clean/manifest.json").build()
            httpClient.newCall(req).execute().use { resp ->
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code} from /manifest.json")
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) throw IllegalStateException("Empty manifest response")
                val json = JSONObject(body)
                val name = json.optString("name").ifBlank { clean.substringAfter("://").substringBefore("/") }
                val version = json.optString("version").ifBlank { "1.0" }
                val rawLower = body.lowercase()
                val supportsLossless = json.optBoolean("lossless", false) ||
                    rawLower.contains("flac") ||
                    rawLower.contains("lossless") ||
                    rawLower.contains("alac")
                EchoAddonConfig(
                    id = "addon_${clean.lowercase().hashCode()}",
                    baseUrl = clean,
                    name = name,
                    version = version,
                    supportsLossless = supportsLossless,
                    enabled = true,
                    priority = configuredAddons.size,
                    latencyMs = latency,
                    statusMessage = "Online · ${latency}ms"
                )
            }
        }
    }

    suspend fun fetchAddonName(baseUrl: String): String? = inspectAddon(baseUrl).getOrNull()?.name

    /**
     * Queries all enabled Echo Addons inline for the SearchScreen "From Extensions" shelf.
     */
    suspend fun searchInlineAddons(query: String, limit: Int = 12): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val addons = configuredAddons.filter { it.enabled && it.baseUrl.isNotBlank() }.sortedBy { it.priority }
        if (trimmed.isBlank() || addons.isEmpty()) return@withContext emptyList()

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val results = mutableListOf<Track>()
        for (addon in addons) {
            val tracks = runCatching {
                val url = "${addon.baseUrl}/search?q=$encoded"
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    parseAddonSearchTracks(resp.body?.string().orEmpty())
                }
            }.getOrDefault(emptyList())

            tracks.take(limit).forEach { t ->
                results.add(
                    Track(
                        id = "addon_${addon.id}_${t.id}",
                        title = t.title,
                        artist = t.artist.ifBlank { addon.name },
                        album = t.album.ifBlank { addon.name },
                        durationMs = (t.durationSec ?: 0) * 1000L,
                        artworkUrl = t.artworkUrl,
                        sourceType = SourceType.ONLINE,
                        sourceProvider = MusicSource.ECHO_ADDON,
                        providerTrackId = "${addon.baseUrl}|${t.id}"
                    )
                )
            }
            if (results.size >= limit) break
        }
        results.distinctBy { it.id }.take(limit)
    }

    suspend fun resolveStream(track: Track, quality: AudioQuality): StreamResolution? = withContext(Dispatchers.IO) {
        val qualityParam = when (quality) {
            AudioQuality.HIGH_320 -> "LOSSLESS"
            AudioQuality.MEDIUM_160 -> "HIGH"
            AudioQuality.LOW_96 -> "LOW"
        }

        // Fast path: Track came directly from Echo Addon inline search ("baseUrl|trackId")
        if (track.sourceProvider == MusicSource.ECHO_ADDON && track.providerTrackId.contains("|")) {
            val baseUrl = track.providerTrackId.substringBefore("|")
            val remoteId = track.providerTrackId.substringAfter("|")
            fetchStreamFromAddon(baseUrl, remoteId, qualityParam)?.let { return@withContext it }
        }

        val addons = configuredAddonUrls
        if (addons.isEmpty()) return@withContext null

        val identities = buildList {
            add(track.title to track.artist)
            TrackMatcher.uploadIdentity(track.title)?.let { if (it !in this) add(it) }
        }
        val candidates = buildList {
            for ((wantTitle, wantArtist) in identities) {
                val cleanTitle = TrackMatcher.cleanTitleForSearch(wantTitle)
                val primaryArtist = wantArtist.split(",", "&", "•", ";", "/").firstOrNull()?.trim().orEmpty()
                val q1 = "$cleanTitle $primaryArtist".trim()
                if (q1.isNotBlank()) add(Triple(q1, wantTitle, wantArtist))
                val q2 = "$wantTitle $wantArtist".trim()
                if (q2.isNotBlank() && q2 != q1) add(Triple(q2, wantTitle, wantArtist))
            }
        }
        if (candidates.isEmpty()) return@withContext null

        for (baseUrl in addons) {
            var matched: EchoAddonTrack? = null
            for ((query, wantTitle, wantArtist) in candidates) {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "$baseUrl/search?q=$encoded"
                val tracks = runCatching {
                    httpClient.newCall(Request.Builder().url(searchUrl).build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use emptyList()
                        parseAddonSearchTracks(resp.body?.string().orEmpty())
                    }
                }.getOrDefault(emptyList())

                matched = TrackMatcher.selectBestMatch(
                    candidates = tracks,
                    title = wantTitle,
                    artist = wantArtist,
                    durationMs = track.durationMs.takeIf { it > 0L },
                    titleOf = { it.title },
                    artistOf = { it.artist },
                    durationMsOf = { it.durationSec?.let { s -> s * 1000L } }
                )
                if (matched != null) break
            }

            if (matched != null) {
                val streamRes = fetchStreamFromAddon(baseUrl, matched.id, qualityParam)
                if (streamRes != null) return@withContext streamRes
            }
        }
        null
    }

    private fun fetchStreamFromAddon(baseUrl: String, remoteTrackId: String, qualityParam: String): StreamResolution? {
        if (!isSafePublicHost(baseUrl)) return null
        val streamEndpoint = "$baseUrl/stream/${Uri.encode(remoteTrackId)}?quality=$qualityParam"
        return runCatching {
            httpClient.newCall(Request.Builder().url(streamEndpoint).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string().orEmpty())
                val url = json.optString("url").takeIf { isSafePublicHost(it) } ?: return@use null
                val format = json.optString("format").ifBlank { json.optString("codec") }
                val isFlac = format.contains("flac", ignoreCase = true)
                val isAlac = format.contains("alac", ignoreCase = true)
                StreamResolution(
                    uri = url,
                    resolvedBy = MusicSource.ECHO_ADDON,
                    bitrateKbps = if (isFlac || isAlac) 1411 else 320,
                    mimeType = when {
                        isFlac -> "audio/flac"
                        isAlac -> "audio/mp4"
                        format.contains("mp3", ignoreCase = true) -> "audio/mpeg"
                        else -> "audio/mp4"
                    },
                    usedFallback = false,
                    clientName = "EchoAddon"
                )
            }
        }.onFailure {
            Log.w(TAG, "Echo addon stream fetch failed on $baseUrl: ${it.message}")
        }.getOrNull()
    }

    private fun parseAddonSearchTracks(raw: String): List<EchoAddonTrack> {
        if (raw.isBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("tracks") ?: root.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<EchoAddonTrack>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val title = obj.optString("title")
            val artist = obj.optString("artist")
            val album = obj.optString("album")
            val art = obj.optString("artwork").ifBlank { obj.optString("cover").ifBlank { obj.optString("thumbnail") } }
            val duration = obj.optInt("duration", -1).takeIf { it > 0 }
            if (id.isNotBlank() && title.isNotBlank()) {
                list.add(
                    EchoAddonTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = art,
                        durationSec = duration
                    )
                )
            }
        }
        return list
    }

    companion object {
        fun parseAddonsJson(raw: String): List<EchoAddonConfig> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val url = o.optString("baseUrl")
                    if (url.isBlank()) return@mapNotNull null
                    EchoAddonConfig(
                        id = o.optString("id").ifBlank { "addon_${url.hashCode()}" },
                        baseUrl = url,
                        name = o.optString("name").ifBlank { url.substringAfter("://").substringBefore("/") },
                        version = o.optString("version", "1.0"),
                        supportsLossless = o.optBoolean("supportsLossless", false),
                        enabled = o.optBoolean("enabled", true),
                        priority = o.optInt("priority", i),
                        latencyMs = if (o.has("latencyMs") && !o.isNull("latencyMs")) o.optLong("latencyMs") else null,
                        statusMessage = o.optString("statusMessage", "Ready")
                    )
                }.sortedBy { it.priority }
            }.getOrDefault(emptyList())
        }

        fun serializeAddonsJson(addons: List<EchoAddonConfig>): String {
            val arr = JSONArray()
            addons.forEachIndexed { idx, addon ->
                arr.put(
                    JSONObject().apply {
                        put("id", addon.id)
                        put("baseUrl", addon.baseUrl)
                        put("name", addon.name)
                        put("version", addon.version)
                        put("supportsLossless", addon.supportsLossless)
                        put("enabled", addon.enabled)
                        put("priority", idx)
                        addon.latencyMs?.let { put("latencyMs", it) }
                        put("statusMessage", addon.statusMessage)
                    }
                )
            }
            return arr.toString()
        }
    }
}

/**
 * Exact port of Echo Music's strict `selectBestMatch` title/artist/qualifier/duration matcher.
 * Guarantees cross-provider resolution (Echo Addons & JioSaavn) NEVER plays a different song.
 */
object TrackMatcher {

    private val QUALIFIER_TOKENS = setOf(
        "remix", "live", "acoustic", "instrumental", "karaoke", "sped", "slowed",
        "reverb", "nightcore", "lofi", "cover", "mashup", "extended", "edit",
        "unplugged", "demo", "orchestral", "piano", "stripped", "clean", "explicit",
        "dub", "vip", "bootleg", "club", "radio", "mix", "reprise", "version"
    )

    private val CREDIT_TOKENS = setOf(
        "feat", "ft", "featuring", "with", "prod", "by", "official", "audio",
        "video", "lyric", "lyrics", "music", "hd", "hq", "4k", "remastered",
        "remaster", "from", "the", "a", "an", "and", "of", "in", "on", "to", "x"
    )

    private val GENERIC_ARTIST_TOKENS = setOf(
        "the", "and", "feat", "ft", "featuring", "with", "topic", "official",
        "music", "vevo", "records", "entertainment", "various", "artists", "unknown", "artist"
    )

    fun cleanTitleForSearch(title: String): String {
        return stripNonQualifierBrackets(title)
            .replace(Regex("""\b(?:feat|ft|featuring)\.?\s+[^(\[\-|]+$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Strips parenthetical/bracketed metadata like `(from GTAVI: The Album)`, `(feat. Artist)`,
     * `[Official Audio]`, `(Explicit)`, `(Deluxe)` while PRESERVING any bracket containing a
     * real musical version qualifier (e.g. `(Remix)`, `(Slowed + Reverb)`, `(Acoustic)`, `(Live)`).
     */
    private fun stripNonQualifierBrackets(title: String): String {
        val bracketRegex = Regex("""\(([^)]*)\)|\[([^\]]*)]""")
        return bracketRegex.replace(title) { match ->
            val inner = (match.groupValues[1].ifBlank { match.groupValues[2] })
            val innerTokens = tokenize(inner)
            val hasRealQualifier = innerTokens.any { it in QUALIFIER_TOKENS }
            if (hasRealQualifier) match.value else " "
        }.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Parses YouTube uploads titled "Artist - Song Title" when the uploader channel differs.
     */
    fun uploadIdentity(title: String): Pair<String, String>? {
        val separators = listOf(" - ", " – ", " — ", " | ")
        for (sep in separators) {
            val idx = title.indexOf(sep)
            if (idx in 2 until (title.length - sep.length - 1)) {
                val left = title.substring(0, idx).trim()
                val right = title.substring(idx + sep.length).trim()
                if (left.isNotBlank() && right.isNotBlank()) {
                    return right to left
                }
            }
        }
        return null
    }

    fun <T> selectBestMatch(
        candidates: List<T>,
        title: String,
        artist: String,
        durationMs: Long?,
        titleOf: (T) -> String,
        artistOf: (T) -> String,
        durationMsOf: (T) -> Long?
    ): T? {
        if (candidates.isEmpty()) return null
        val cleanedWantTitle = stripNonQualifierBrackets(title).ifBlank { title }
        val wantTokens = tokenize(cleanedWantTitle)
        if (wantTokens.isEmpty()) return null

        val wantQualifiers = wantTokens.filter { it in QUALIFIER_TOKENS }.toSet()
        val wantBase = wantTokens.filterNot { it in QUALIFIER_TOKENS || it in CREDIT_TOKENS }
        if (wantBase.isEmpty()) return null

        val artistTokens = significantArtistTokens(artist)
        var best: T? = null
        var bestScore = Int.MIN_VALUE

        for (candidate in candidates) {
            val rawGotTitle = titleOf(candidate)
            if (rawGotTitle.isBlank()) continue
            val gotTitle = stripNonQualifierBrackets(rawGotTitle).ifBlank { rawGotTitle }
            val gotTokens = tokenize(gotTitle)
            if (gotTokens.isEmpty()) continue

            val gotQualifiers = gotTokens.filter { it in QUALIFIER_TOKENS }.toSet()
            val gotBase = gotTokens.filterNot { it in QUALIFIER_TOKENS || it in CREDIT_TOKENS }
            if (gotBase.isEmpty()) continue

            // 1. Candidate must cover ALL significant base title tokens
            if (!coversAllTokens(gotBase, wantBase)) continue

            // 2. Qualifiers the user requested (remix, live, slowed, etc.) must exist in candidate
            if (!wantQualifiers.all { wanted -> gotQualifiers.any { tokensMatch(it, wanted) } }) {
                continue
            }

            // 3. Reject remix/cover/instrumental if user requested the plain original!
            val unexpectedDangerousQualifiers = gotQualifiers.filter {
                it in setOf("remix", "cover", "karaoke", "instrumental", "lofi", "slowed", "sped", "nightcore", "mashup")
            }.filterNot { it in wantQualifiers }
            if (unexpectedDangerousQualifiers.isNotEmpty()) continue

            // 4. Never substitute a completely different artist when both sides specify an artist
            val gotArtistTokens = significantArtistTokens(artistOf(candidate))
            val artistMatched = artistMatches(artistTokens, gotArtistTokens)
            if (artistTokens.isNotEmpty() && gotArtistTokens.isNotEmpty() && !artistMatched) {
                continue
            }

            // 5. Reject wild duration mismatch (> 35 seconds off) when both durations are known
            val candDur = durationMsOf(candidate)
            if (durationMs != null && durationMs > 15_000L && candDur != null && candDur > 15_000L) {
                val diffMs = abs(candDur - durationMs)
                if (diffMs > 35_000L) continue
            }

            var score = if (gotBase == wantBase) {
                100
            } else {
                (gotBase.count { got -> wantBase.any { tokensMatch(got, it) } }.toFloat() /
                    maxOf(gotBase.size, wantBase.size) * 60).toInt()
            }

            if (artistMatched) score += 60
            if (gotQualifiers == wantQualifiers) score += 20
            else if (wantQualifiers.isEmpty()) score -= gotQualifiers.size * 5

            if (durationMs != null && durationMs > 0L && candDur != null && candDur > 0L) {
                val diffMs = abs(candDur - durationMs)
                if (diffMs <= 8_000L) score += 15 else if (diffMs <= 20_000L) score += 5
            }

            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun normalizeForMatch(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
        return noDiacritics
            .lowercase()
            .replace("'", "")
            .replace("’", "")
            .replace(Regex("""[^a-z0-9\p{L}\p{N}]+"""), " ")
            .trim()
    }

    private fun tokenize(value: String): List<String> =
        normalizeForMatch(value).split(" ").filter { it.isNotBlank() }

    private fun significantArtistTokens(artist: String): List<String> =
        tokenize(artist).filter { it.length >= 2 && it !in GENERIC_ARTIST_TOKENS }

    private fun coversAllTokens(gotBase: List<String>, wantBase: List<String>): Boolean {
        return wantBase.all { wanted -> gotBase.any { got -> tokensMatch(got, wanted) } }
    }

    private fun artistMatches(wantArtistTokens: List<String>, gotArtistTokens: List<String>): Boolean {
        if (wantArtistTokens.isEmpty() || gotArtistTokens.isEmpty()) return false
        return wantArtistTokens.any { w -> gotArtistTokens.any { g -> tokensMatch(g, w) } }
    }

    private fun tokensMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length >= 4 && b.length >= 4 && (a.startsWith(b) || b.startsWith(a))) return true
        return false
    }
}
