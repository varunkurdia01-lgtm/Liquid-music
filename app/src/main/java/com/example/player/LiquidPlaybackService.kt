package com.example.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.domain.model.SoundLabSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

object PlaybackDspBridge {
    val soundLabController = SoundLabController()

    @Volatile
    var activePlayer: ExoPlayer? = null
        internal set

    @Volatile
    var currentSettings: SoundLabSettings = SoundLabSettings()
        private set

    fun applySoundLab(settings: SoundLabSettings) {
        currentSettings = settings
        soundLabController.applySettings(settings)
        activePlayer?.let { player ->
            val safeSpeed = settings.playbackSpeed.coerceIn(0.5f, 2.0f)
            val safePitch = settings.playbackPitch.coerceIn(0.5f, 2.0f)
            player.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
        }
    }
}

class LiquidPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var auraModulationJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        PlaybackDspBridge.activePlayer = player

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                PlaybackDspBridge.soundLabController.attachToAudioSession(audioSessionId)
                PlaybackDspBridge.applySoundLab(PlaybackDspBridge.currentSettings)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val sessionId = player.audioSessionId
                    if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
                        PlaybackDspBridge.soundLabController.attachToAudioSession(sessionId)
                    }
                }
            }
        })

        // Launch activity when tapping media notification
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()

        startSpatialAuraLoop()
    }

    private fun startSpatialAuraLoop() {
        auraModulationJob?.cancel()
        auraModulationJob = serviceScope.launch {
            var phase = 0.0
            while (isActive) {
                val settings = PlaybackDspBridge.currentSettings
                val p = PlaybackDspBridge.activePlayer
                if (p != null && p.isPlaying) {
                    // Crossfade gentle fade-out/in around track transitions
                    val crossfadeMs = settings.crossfadeSeconds * 1000L
                    val duration = p.duration
                    val pos = p.currentPosition
                    var targetVolume = 1.0f

                    if (crossfadeMs > 0L && duration > crossfadeMs * 2L && pos > 0L) {
                        val remaining = duration - pos
                        if (remaining in 1..crossfadeMs) {
                            targetVolume = (remaining.toFloat() / crossfadeMs.toFloat()).coerceIn(0.18f, 1.0f)
                        } else if (pos in 1..(crossfadeMs / 2L)) {
                            targetVolume = (0.35f + 0.65f * (pos.toFloat() / (crossfadeMs / 2f))).coerceIn(0.35f, 1.0f)
                        }
                    }

                    // Subtle 8D breathing spacious wave when 8D mode is active
                    if (settings.eightDEnabled) {
                        phase += 0.14
                        val auraWave = (0.88f + 0.12f * sin(phase).toFloat())
                        targetVolume *= auraWave
                    }

                    p.volume = targetVolume.coerceIn(0.1f, 1.0f)
                }
                delay(120L)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        auraModulationJob?.cancel()
        serviceScope.cancel()
        PlaybackDspBridge.soundLabController.releaseEffects()
        PlaybackDspBridge.activePlayer = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
