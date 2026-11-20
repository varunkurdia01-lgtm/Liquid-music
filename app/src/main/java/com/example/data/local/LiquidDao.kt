package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LiquidDao {
    // Tracks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): TrackEntity?

    // Liked Tracks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: LikedTrackEntity)

    @Query("DELETE FROM liked_tracks WHERE trackId = :trackId")
    suspend fun deleteLike(trackId: String)

    @Query("SELECT trackId FROM liked_tracks")
    fun observeLikedTrackIds(): Flow<List<String>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN liked_tracks l ON t.id = l.trackId
        ORDER BY l.likedAt DESC
        """
    )
    fun observeLikedTracks(): Flow<List<TrackEntity>>

    // Downloaded Tracks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(download: DownloadedTrackEntity)

    @Query("DELETE FROM downloaded_tracks WHERE trackId = :trackId")
    suspend fun deleteDownload(trackId: String)

    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadedTrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN downloaded_tracks d ON t.id = d.trackId
        ORDER BY d.downloadedAt DESC
        """
    )
    fun observeDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE trackId = :trackId")
    suspend fun getDownloadById(trackId: String): DownloadedTrackEntity?

    // Playlists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_track_cross_ref ORDER BY position ASC, addedAt ASC")
    fun observeAllPlaylistRefs(): Flow<List<PlaylistTrackCrossRef>>

    @Query("SELECT * FROM tracks")
    fun observeAllStoredTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackRef(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun getMaxPlaylistPosition(playlistId: Long): Int

    // Listening Events & Anytime Wrapped Analytics
    @Insert
    suspend fun insertListeningEvent(event: ListeningEventEntity)

    @Query("SELECT * FROM listening_events ORDER BY timestamp DESC")
    fun observeListeningEvents(): Flow<List<ListeningEventEntity>>

    @Query(
        """
        SELECT trackId, title, artist, artworkUrl, COUNT(*) as playCount, SUM(durationListenedMs) as totalMs
        FROM listening_events
        GROUP BY trackId
        ORDER BY playCount DESC, totalMs DESC
        LIMIT 5
        """
    )
    fun observeTopSongs(): Flow<List<SongStatRow>>

    @Query(
        """
        SELECT artist, MAX(artworkUrl) as artworkUrl, COUNT(*) as playCount, SUM(durationListenedMs) as totalMs
        FROM listening_events
        GROUP BY artist
        ORDER BY totalMs DESC, playCount DESC
        LIMIT 5
        """
    )
    fun observeTopArtists(): Flow<List<ArtistStatRow>>

    @Query("SELECT DISTINCT trackId FROM listening_events ORDER BY timestamp DESC LIMIT 12")
    fun observeRecentTrackIds(): Flow<List<String>>
}
