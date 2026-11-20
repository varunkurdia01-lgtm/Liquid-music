package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.SourceType
import com.example.domain.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String,
    val streamUrl96: String,
    val streamUrl160: String,
    val streamUrl320: String,
    val localUri: String,
    val downloadedFilePath: String,
    val sourceType: String,
    val addedAt: Long
) {
    fun toDomain(overrideDownloadPath: String? = null): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        streamUrl96 = streamUrl96,
        streamUrl160 = streamUrl160,
        streamUrl320 = streamUrl320,
        localUri = localUri,
        downloadedFilePath = overrideDownloadPath ?: downloadedFilePath,
        sourceType = runCatching { SourceType.valueOf(sourceType) }.getOrDefault(SourceType.ONLINE),
        addedAt = addedAt
    )

    companion object {
        fun fromDomain(track: Track): TrackEntity = TrackEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            artworkUrl = track.artworkUrl,
            streamUrl96 = track.streamUrl96,
            streamUrl160 = track.streamUrl160,
            streamUrl320 = track.streamUrl320,
            localUri = track.localUri,
            downloadedFilePath = track.downloadedFilePath,
            sourceType = track.sourceType.name,
            addedAt = track.addedAt
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val coverArtUri: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "liked_tracks")
data class LikedTrackEntity(
    @PrimaryKey val trackId: String,
    val likedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val trackId: String,
    val filePath: String,
    val bitrateKbps: Int,
    val fileSizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "listening_events")
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val durationListenedMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class SongStatRow(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val playCount: Int,
    val totalMs: Long
)

data class ArtistStatRow(
    val artist: String,
    val artworkUrl: String,
    val playCount: Int,
    val totalMs: Long
)
