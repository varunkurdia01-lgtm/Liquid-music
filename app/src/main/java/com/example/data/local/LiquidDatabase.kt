package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        LikedTrackEntity::class,
        DownloadedTrackEntity::class,
        ListeningEventEntity::class,
        SavedAlbumEntity::class,
        CachedLyricsEntity::class,
        SearchHistoryEntity::class,
        PlaybackQueueEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LiquidDatabase : RoomDatabase() {
    abstract fun liquidDao(): LiquidDao

    companion object {
        @Volatile
        private var INSTANCE: LiquidDatabase? = null

        fun getInstance(context: Context): LiquidDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LiquidDatabase::class.java,
                    "liquid_music.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
