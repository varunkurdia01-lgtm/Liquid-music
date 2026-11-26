package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.player.LiquidPlaybackService
import com.example.player.PlaybackDspBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URL

/**
 * Home Screen Mini-Player AppWidgetProvider for Liquid Music.
 * Syncs live track metadata, album artwork, and transport controls with LiquidPlaybackService.
 */
class LiquidMusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, "Ready to Play").orEmpty().ifBlank { "Ready to Play" }
        val artist = prefs.getString(KEY_ARTIST, "Tap to open Liquid Music").orEmpty()
        val badge = prefs.getString(KEY_BADGE, "LIQUID MUSIC").orEmpty().ifBlank { "LIQUID MUSIC" }
        val artworkUrl = prefs.getString(KEY_ARTWORK, "").orEmpty()
        val isPlaying = prefs.getBoolean(KEY_PLAYING, false)

        for (widgetId in appWidgetIds) {
            val views = buildRemoteViews(context, title, artist, badge, isPlaying, null)
            appWidgetManager.updateAppWidget(widgetId, views)
        }

        if (artworkUrl.startsWith("http")) {
            loadArtworkAndPush(context, appWidgetManager, appWidgetIds, title, artist, badge, artworkUrl, isPlaying)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE,
            ACTION_WIDGET_NEXT,
            ACTION_WIDGET_PREV -> {
                val player = PlaybackDspBridge.activePlayer
                if (player != null) {
                    when (intent.action) {
                        ACTION_WIDGET_PLAY_PAUSE -> {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                        ACTION_WIDGET_NEXT -> {
                            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                        }
                        ACTION_WIDGET_PREV -> {
                            if (player.currentPosition > 3_500L) {
                                player.seekTo(0L)
                            } else if (player.hasPreviousMediaItem()) {
                                player.seekToPreviousMediaItem()
                            }
                        }
                    }
                } else {
                    val serviceIntent = Intent(context, LiquidPlaybackService::class.java).apply {
                        action = intent.action
                    }
                    runCatching { context.startService(serviceIntent) }
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.widget.ACTION_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.widget.ACTION_NEXT"
        const val ACTION_WIDGET_PREV = "com.example.widget.ACTION_PREV"

        private const val PREFS_NAME = "liquid_widget_prefs"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_BADGE = "badge"
        private const val KEY_ARTWORK = "artwork"
        private const val KEY_PLAYING = "playing"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile
        private var cachedArtworkUrl: String = ""
        @Volatile
        private var cachedArtworkBitmap: Bitmap? = null

        fun updateWidgetState(
            context: Context,
            title: String,
            artist: String,
            badge: String = "LIQUID MUSIC",
            artworkUrl: String = "",
            isPlaying: Boolean
        ) {
            val appCtx = context.applicationContext
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_ARTIST, artist)
                .putString(KEY_BADGE, badge)
                .putString(KEY_ARTWORK, artworkUrl)
                .putBoolean(KEY_PLAYING, isPlaying)
                .apply()

            val manager = AppWidgetManager.getInstance(appCtx)
            val component = ComponentName(appCtx, LiquidMusicWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val immediateBitmap = if (artworkUrl.isNotBlank() && artworkUrl == cachedArtworkUrl) cachedArtworkBitmap else null
            for (id in ids) {
                manager.updateAppWidget(id, buildRemoteViews(appCtx, title, artist, badge, isPlaying, immediateBitmap))
            }

            if (artworkUrl.startsWith("http") && artworkUrl != cachedArtworkUrl) {
                loadArtworkAndPush(appCtx, manager, ids, title, artist, badge, artworkUrl, isPlaying)
            }
        }

        private fun loadArtworkAndPush(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            title: String,
            artist: String,
            badge: String,
            artworkUrl: String,
            isPlaying: Boolean
        ) {
            widgetScope.launch {
                val bmp = runCatching {
                    val conn = URL(artworkUrl).openConnection().apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    conn.getInputStream().use { stream ->
                        val raw = BitmapFactory.decodeStream(stream) ?: return@use null
                        Bitmap.createScaledBitmap(raw, 144, 144, true)
                    }
                }.getOrNull() ?: return@launch

                cachedArtworkUrl = artworkUrl
                cachedArtworkBitmap = bmp
                for (id in ids) {
                    manager.updateAppWidget(id, buildRemoteViews(context, title, artist, badge, isPlaying, bmp))
                }
            }
        }

        private fun buildRemoteViews(
            context: Context,
            title: String,
            artist: String,
            badge: String,
            isPlaying: Boolean,
            artworkBitmap: Bitmap?
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_liquid_music)
            views.setTextViewText(R.id.widget_title, title.ifBlank { "Ready to Play" })
            views.setTextViewText(R.id.widget_artist, artist.ifBlank { "Liquid Music" })
            views.setTextViewText(R.id.widget_badge, badge.ifBlank { "LIQUID MUSIC" })
            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            if (artworkBitmap != null) {
                views.setImageViewBitmap(R.id.widget_artwork, artworkBitmap)
            } else {
                views.setImageViewResource(R.id.widget_artwork, R.mipmap.ic_launcher_round)
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val openAppPi = PendingIntent.getActivity(
                context,
                100,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_info_container, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_artwork, openAppPi)

            views.setOnClickPendingIntent(R.id.widget_btn_prev, buildActionPendingIntent(context, ACTION_WIDGET_PREV, 101))
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, buildActionPendingIntent(context, ACTION_WIDGET_PLAY_PAUSE, 102))
            views.setOnClickPendingIntent(R.id.widget_btn_next, buildActionPendingIntent(context, ACTION_WIDGET_NEXT, 103))

            return views
        }

        private fun buildActionPendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, LiquidMusicWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
