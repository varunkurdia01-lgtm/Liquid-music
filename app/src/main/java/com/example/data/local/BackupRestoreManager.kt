package com.example.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.repository.UnifiedMusicRepository
import com.example.domain.model.MusicSource
import com.example.domain.model.Playlist
import com.example.domain.model.SourceType
import com.example.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "BackupRestoreMgr"

data class BackupSummary(
    val tracksCount: Int,
    val likedCount: Int,
    val playlistsCount: Int,
    val savedAlbumsCount: Int,
    val historyCount: Int
)

/**
 * Zero-permission Storage Access Framework (SAF) Backup, Restore, and M3U8/JSON Playlist Transfer.
 */
class BackupRestoreManager(
    private val context: Context,
    private val dao: LiquidDao,
    private val musicRepository: UnifiedMusicRepository
) {
    suspend fun exportFullBackup(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val tracks = dao.getAllStoredTracksOnce()
            val likes = dao.getAllLikesOnce()
            val playlists = dao.getAllPlaylistsOnce()
            val refs = dao.getAllPlaylistRefsOnce()
            val albums = dao.getAllSavedAlbumsOnce()
            val history = dao.getListeningEventsOnce()
            val lyrics = dao.getAllCachedLyricsOnce()

            val root = JSONObject().apply {
                put("app", "LiquidMusic")
                put("schemaVersion", 2)
                put("exportedAt", System.currentTimeMillis())

                put("tracks", JSONArray().apply {
                    tracks.forEach { t ->
                        put(
                            JSONObject().apply {
                                put("id", t.id)
                                put("title", t.title)
                                put("artist", t.artist)
                                put("album", t.album)
                                put("durationMs", t.durationMs)
                                put("artworkUrl", t.artworkUrl)
                                put("streamUrl96", t.streamUrl96)
                                put("streamUrl160", t.streamUrl160)
                                put("streamUrl320", t.streamUrl320)
                                put("localUri", t.localUri)
                                put("sourceType", t.sourceType)
                                put("sourceProvider", t.sourceProvider)
                                put("providerTrackId", t.providerTrackId)
                                put("artistId", t.artistId)
                                put("albumId", t.albumId)
                                put("explicit", t.explicit)
                                put("addedAt", t.addedAt)
                            }
                        )
                    }
                })

                put("likes", JSONArray().apply {
                    likes.forEach { l ->
                        put(JSONObject().apply {
                            put("trackId", l.trackId)
                            put("likedAt", l.likedAt)
                        })
                    }
                })

                put("playlists", JSONArray().apply {
                    val refsByPlaylist = refs.groupBy { it.playlistId }
                    playlists.forEach { pl ->
                        put(JSONObject().apply {
                            put("name", pl.name)
                            put("description", pl.description)
                            put("coverArtUri", pl.coverArtUri)
                            put("createdAt", pl.createdAt)
                            put("trackIds", JSONArray().apply {
                                refsByPlaylist[pl.id].orEmpty().sortedBy { it.position }.forEach { r ->
                                    put(r.trackId)
                                }
                            })
                        })
                    }
                })

                put("savedAlbums", JSONArray().apply {
                    albums.forEach { a ->
                        put(JSONObject().apply {
                            put("id", a.id)
                            put("browseId", a.browseId)
                            put("title", a.title)
                            put("artist", a.artist)
                            put("artistId", a.artistId)
                            put("year", a.year)
                            put("artworkUrl", a.artworkUrl)
                            put("sourceProvider", a.sourceProvider)
                            put("savedAt", a.savedAt)
                        })
                    }
                })

                put("listeningEvents", JSONArray().apply {
                    history.forEach { e ->
                        put(JSONObject().apply {
                            put("trackId", e.trackId)
                            put("title", e.title)
                            put("artist", e.artist)
                            put("artworkUrl", e.artworkUrl)
                            put("durationListenedMs", e.durationListenedMs)
                            put("timestamp", e.timestamp)
                        })
                    }
                })

                put("cachedLyrics", JSONArray().apply {
                    lyrics.forEach { l ->
                        put(JSONObject().apply {
                            put("trackId", l.trackId)
                            put("sourceName", l.sourceName)
                            put("syncedJson", l.syncedJson)
                            put("plainLyrics", l.plainLyrics)
                            put("translatedJson", l.translatedJson)
                            put("translationLanguage", l.translationLanguage)
                            put("isInstrumental", l.isInstrumental)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(root.toString(2).toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: throw IllegalStateException("Unable to open output stream for backup.")

            Result.success(
                BackupSummary(
                    tracksCount = tracks.size,
                    likedCount = likes.size,
                    playlistsCount = playlists.size,
                    savedAlbumsCount = albums.size,
                    historyCount = history.size
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Backup export failed", e)
            Result.failure(e)
        }
    }

    suspend fun importFullBackup(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: throw IllegalStateException("Unable to read backup file.")

            val root = JSONObject(raw)
            var trackCount = 0
            var likeCount = 0
            var playlistCount = 0
            var albumCount = 0
            var historyCount = 0

            val tracksArr = root.optJSONArray("tracks")
            if (tracksArr != null) {
                val entities = mutableListOf<TrackEntity>()
                for (i in 0 until tracksArr.length()) {
                    val o = tracksArr.optJSONObject(i) ?: continue
                    val id = o.optString("id").ifBlank { continue }
                    entities.add(
                        TrackEntity(
                            id = id,
                            title = o.optString("title", "Unknown"),
                            artist = o.optString("artist", "Unknown Artist"),
                            album = o.optString("album"),
                            durationMs = o.optLong("durationMs", 0L),
                            artworkUrl = o.optString("artworkUrl"),
                            streamUrl96 = o.optString("streamUrl96"),
                            streamUrl160 = o.optString("streamUrl160"),
                            streamUrl320 = o.optString("streamUrl320"),
                            localUri = o.optString("localUri"),
                            downloadedFilePath = "",
                            sourceType = o.optString("sourceType", SourceType.ONLINE.name),
                            sourceProvider = o.optString("sourceProvider", MusicSource.YOUTUBE_MUSIC.name),
                            providerTrackId = o.optString("providerTrackId"),
                            artistId = o.optString("artistId"),
                            albumId = o.optString("albumId"),
                            explicit = o.optBoolean("explicit", false),
                            addedAt = o.optLong("addedAt", System.currentTimeMillis())
                        )
                    )
                }
                if (entities.isNotEmpty()) {
                    dao.upsertTracks(entities)
                    trackCount = entities.size
                }
            }

            val likesArr = root.optJSONArray("likes")
            if (likesArr != null) {
                for (i in 0 until likesArr.length()) {
                    val o = likesArr.optJSONObject(i) ?: continue
                    val tid = o.optString("trackId").ifBlank { continue }
                    dao.insertLike(LikedTrackEntity(tid, o.optLong("likedAt", System.currentTimeMillis())))
                    likeCount++
                }
            }

            val playlistsArr = root.optJSONArray("playlists")
            if (playlistsArr != null) {
                for (i in 0 until playlistsArr.length()) {
                    val o = playlistsArr.optJSONObject(i) ?: continue
                    val name = o.optString("name").ifBlank { continue }
                    val newId = dao.insertPlaylist(
                        PlaylistEntity(
                            name = name,
                            description = o.optString("description"),
                            coverArtUri = o.optString("coverArtUri"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                    val ids = o.optJSONArray("trackIds")
                    if (ids != null) {
                        for (j in 0 until ids.length()) {
                            val tid = ids.optString(j).ifBlank { continue }
                            if (dao.getTrackById(tid) != null) {
                                dao.insertPlaylistTrackRef(
                                    PlaylistTrackCrossRef(playlistId = newId, trackId = tid, position = j)
                                )
                            }
                        }
                    }
                    playlistCount++
                }
            }

            val albumsArr = root.optJSONArray("savedAlbums")
            if (albumsArr != null) {
                for (i in 0 until albumsArr.length()) {
                    val o = albumsArr.optJSONObject(i) ?: continue
                    val id = o.optString("id").ifBlank { continue }
                    dao.upsertSavedAlbum(
                        SavedAlbumEntity(
                            id = id,
                            browseId = o.optString("browseId", id),
                            title = o.optString("title", "Album"),
                            artist = o.optString("artist", "Artist"),
                            artistId = o.optString("artistId"),
                            year = o.optString("year"),
                            artworkUrl = o.optString("artworkUrl"),
                            sourceProvider = o.optString("sourceProvider", MusicSource.YOUTUBE_MUSIC.name),
                            savedAt = o.optLong("savedAt", System.currentTimeMillis())
                        )
                    )
                    albumCount++
                }
            }

            val historyArr = root.optJSONArray("listeningEvents")
            if (historyArr != null) {
                for (i in 0 until historyArr.length()) {
                    val o = historyArr.optJSONObject(i) ?: continue
                    val tid = o.optString("trackId").ifBlank { continue }
                    dao.insertListeningEvent(
                        ListeningEventEntity(
                            trackId = tid,
                            title = o.optString("title"),
                            artist = o.optString("artist"),
                            artworkUrl = o.optString("artworkUrl"),
                            durationListenedMs = o.optLong("durationListenedMs", 30_000L),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                    historyCount++
                }
            }

            val lyricsArr = root.optJSONArray("cachedLyrics")
            if (lyricsArr != null) {
                for (i in 0 until lyricsArr.length()) {
                    val o = lyricsArr.optJSONObject(i) ?: continue
                    val tid = o.optString("trackId").ifBlank { continue }
                    dao.upsertCachedLyrics(
                        CachedLyricsEntity(
                            trackId = tid,
                            sourceName = o.optString("sourceName"),
                            syncedJson = o.optString("syncedJson"),
                            plainLyrics = o.optString("plainLyrics"),
                            translatedJson = o.optString("translatedJson"),
                            translationLanguage = o.optString("translationLanguage"),
                            isInstrumental = o.optBoolean("isInstrumental", false)
                        )
                    )
                }
            }

            Result.success(
                BackupSummary(
                    tracksCount = trackCount,
                    likedCount = likeCount,
                    playlistsCount = playlistCount,
                    savedAlbumsCount = albumCount,
                    historyCount = historyCount
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Backup restore failed", e)
            Result.failure(e)
        }
    }

    suspend fun exportPlaylistAsM3u8(playlist: Playlist, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            sb.appendLine("#EXTM3U")
            sb.appendLine("#PLAYLIST:${playlist.name}")
            playlist.tracks.forEach { track ->
                val sec = (track.durationMs / 1000L).coerceAtLeast(0L)
                sb.appendLine("#EXTINF:$sec,${track.artist} - ${track.title}")
                val ref = when {
                    track.sourceProvider == MusicSource.YOUTUBE_MUSIC -> "https://music.youtube.com/watch?v=${track.providerTrackId}"
                    track.localUri.isNotBlank() -> track.localUri
                    else -> "liquid://track/${track.id}"
                }
                sb.appendLine(ref)
            }
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(sb.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: throw IllegalStateException("Unable to open playlist export URI.")
            Result.success(playlist.tracks.size)
        } catch (e: Exception) {
            Log.e(TAG, "Playlist export failed", e)
            Result.failure(e)
        }
    }

    suspend fun importPlaylistFromUri(uri: Uri, fallbackName: String = "Imported Playlist"): Result<Pair<String, Int>> =
        withContext(Dispatchers.IO) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: throw IllegalStateException("Unable to read playlist file.")

                val trimmed = raw.trim()
                val tracks = mutableListOf<Track>()
                var playlistName = fallbackName

                if (trimmed.startsWith("{")) {
                    // JSON playlist format
                    val obj = JSONObject(trimmed)
                    playlistName = obj.optString("name", fallbackName).ifBlank { fallbackName }
                    val arr = obj.optJSONArray("tracks") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val t = arr.optJSONObject(i) ?: continue
                        val title = t.optString("title").ifBlank { continue }
                        val artist = t.optString("artist", "Unknown Artist")
                        val id = t.optString("id").ifBlank { "imp_${title.hashCode()}_${artist.hashCode()}" }
                        tracks.add(
                            Track(
                                id = id,
                                title = title,
                                artist = artist,
                                album = t.optString("album"),
                                durationMs = t.optLong("durationMs", 0L),
                                artworkUrl = t.optString("artworkUrl"),
                                sourceType = SourceType.ONLINE,
                                sourceProvider = runCatching { MusicSource.valueOf(t.optString("sourceProvider")) }
                                    .getOrDefault(MusicSource.YOUTUBE_MUSIC),
                                providerTrackId = t.optString("providerTrackId", id)
                            )
                        )
                    }
                } else {
                    // M3U / M3U8 format
                    var pendingArtistTitle: Pair<String, String>? = null
                    trimmed.lineSequence().forEach { rawLine ->
                        val line = rawLine.trim()
                        when {
                            line.startsWith("#PLAYLIST:") -> {
                                val n = line.removePrefix("#PLAYLIST:").trim()
                                if (n.isNotBlank()) playlistName = n
                            }
                            line.startsWith("#EXTINF:") -> {
                                val afterComma = line.substringAfter(",", "").trim()
                                if (afterComma.contains(" - ")) {
                                    val artist = afterComma.substringBefore(" - ").trim()
                                    val title = afterComma.substringAfter(" - ").trim()
                                    pendingArtistTitle = artist to title
                                } else if (afterComma.isNotBlank()) {
                                    pendingArtistTitle = "Unknown Artist" to afterComma
                                }
                            }
                            line.isNotBlank() && !line.startsWith("#") -> {
                                val pair = pendingArtistTitle
                                val ytId = Regex("""[?&]v=([a-zA-Z0-9_-]{8,})""").find(line)?.groupValues?.getOrNull(1)
                                if (ytId != null) {
                                    tracks.add(
                                        Track(
                                            id = "ytm_$ytId",
                                            title = pair?.second ?: "YouTube Track $ytId",
                                            artist = pair?.first ?: "YouTube Music",
                                            sourceType = SourceType.ONLINE,
                                            sourceProvider = MusicSource.YOUTUBE_MUSIC,
                                            providerTrackId = ytId
                                        )
                                    )
                                } else if (pair != null) {
                                    // Resolve track via unified search (InnerTube -> JioSaavn)
                                    val query = "${pair.second} ${pair.first}".trim()
                                    val found = musicRepository.searchUnified(query, limit = 1).songs.firstOrNull()
                                    if (found != null) tracks.add(found)
                                }
                                pendingArtistTitle = null
                            }
                        }
                    }
                }

                if (tracks.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("No playable tracks found in playlist file."))
                }

                val cover = tracks.firstOrNull { it.artworkUrl.isNotBlank() }?.artworkUrl.orEmpty()
                val newId = dao.insertPlaylist(
                    PlaylistEntity(
                        name = playlistName,
                        description = "Imported (${tracks.size} tracks)",
                        coverArtUri = cover
                    )
                )
                tracks.distinctBy { it.id }.forEachIndexed { idx, track ->
                    dao.upsertTrack(TrackEntity.fromDomain(track))
                    dao.insertPlaylistTrackRef(
                        PlaylistTrackCrossRef(playlistId = newId, trackId = track.id, position = idx)
                    )
                }
                musicRepository.registerTracks(tracks)
                Result.success(playlistName to tracks.size)
            } catch (e: Exception) {
                Log.e(TAG, "Playlist import failed", e)
                Result.failure(e)
            }
        }
}
