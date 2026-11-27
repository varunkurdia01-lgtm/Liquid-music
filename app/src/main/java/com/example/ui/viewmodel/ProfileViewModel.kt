package com.example.ui.viewmodel

import android.net.Uri
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LiquidAppContainer
import com.example.data.remote.innertube.InnerTubeXResolver
import com.example.domain.model.TopStatItem
import com.example.domain.model.UserProfile
import com.example.domain.model.WrappedStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class BackupUiState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null
)

class ProfileViewModel(
    private val container: LiquidAppContainer
) : ViewModel() {

    val userProfile: StateFlow<UserProfile> = container.authRepository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    val wrappedStats: StateFlow<WrappedStats> = combine(
        container.dao.observeListeningEvents(),
        container.dao.observeTopSongs(),
        container.dao.observeTopArtists()
    ) { events, topSongs, topArtists ->
        val totalMs = events.sumOf { it.durationListenedMs }
        val totalMinutes = (totalMs / 60_000L).toInt()
        val totalPlays = events.size
        val streak = computeListeningStreakDays(events.map { it.timestamp })

        val mappedSongs = topSongs.map {
            TopStatItem(
                title = it.title,
                subtitle = it.artist,
                artworkUrl = it.artworkUrl,
                playCount = it.playCount,
                totalMinutes = (it.totalMs / 60_000L).toInt().coerceAtLeast(1)
            )
        }
        val mappedArtists = topArtists.map {
            TopStatItem(
                title = it.artist,
                subtitle = "${it.playCount} plays",
                artworkUrl = it.artworkUrl,
                playCount = it.playCount,
                totalMinutes = (it.totalMs / 60_000L).toInt().coerceAtLeast(1)
            )
        }

        val (personalityTitle, personalityDesc) = deriveListeningPersonality(
            totalPlays = totalPlays,
            totalMinutes = totalMinutes,
            topArtist = mappedArtists.firstOrNull()?.title
        )

        WrappedStats(
            totalMinutes = totalMinutes,
            totalPlays = totalPlays,
            streakDays = streak,
            topSongs = mappedSongs,
            topArtists = mappedArtists,
            personalityTitle = personalityTitle,
            personalityDescription = personalityDesc
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WrappedStats())

    fun exportFullBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupUiState(isBusy = true, statusMessage = "Exporting library backup…")
            val result = container.backupRestoreManager.exportFullBackup(uri)
            _backupState.value = result.fold(
                onSuccess = { s ->
                    BackupUiState(
                        isBusy = false,
                        statusMessage = "Backed up ${s.likedCount} likes, ${s.playlistsCount} playlists, ${s.savedAlbumsCount} albums & ${s.historyCount} plays."
                    )
                },
                onFailure = { e ->
                    BackupUiState(
                        isBusy = false,
                        statusMessage = "Backup failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            )
        }
    }

    fun restoreFullBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = BackupUiState(isBusy = true, statusMessage = "Restoring library backup…")
            val result = container.backupRestoreManager.importFullBackup(uri)
            _backupState.value = result.fold(
                onSuccess = { s ->
                    BackupUiState(
                        isBusy = false,
                        statusMessage = "Restored ${s.tracksCount} tracks, ${s.likedCount} likes, ${s.playlistsCount} playlists & ${s.savedAlbumsCount} albums."
                    )
                },
                onFailure = { e ->
                    BackupUiState(
                        isBusy = false,
                        statusMessage = "Restore failed: ${e.localizedMessage ?: "Invalid backup JSON"}"
                    )
                }
            )
        }
    }

    fun clearBackupMessage() {
        _backupState.value = _backupState.value.copy(statusMessage = null)
    }

    fun signInWithEmail(displayName: String, email: String) {
        viewModelScope.launch {
            container.authRepository.signInWithEmail(displayName, email)
        }
    }

    fun signInWithGoogleDemo() {
        viewModelScope.launch {
            container.authRepository.signInWithGoogleDemo()
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            container.authRepository.continueAsGuest()
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            container.authRepository.updateDisplayName(newName)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching {
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
            }
            container.authRepository.signOut()
            InnerTubeXResolver.syncSession(null, null, "US", "en")
            runCatching {
                val settings = container.preferencesRepository.soundLabSettings.first()
                container.musicRepository.applyEchoProviderSettings(settings)
            }
        }
    }

    fun buildShareableWrappedText(profile: UserProfile, stats: WrappedStats): String {
        val topSongLine = stats.topSongs.firstOrNull()?.let { "${it.title} — ${it.subtitle}" } ?: "Discovering new soundwaves"
        val topArtistLine = stats.topArtists.firstOrNull()?.title ?: "Liquid Aurora Mix"
        return buildString {
            appendLine("My Anytime Liquid Wrapped (${profile.displayName})")
            appendLine("Sonic Aura: ${stats.personalityTitle}")
            appendLine("Listening Time: ${stats.totalMinutes} mins across ${stats.totalPlays} plays")
            appendLine("Listening Streak: ${stats.streakDays} day(s)")
            appendLine("Top Track: $topSongLine")
            appendLine("Top Artist: $topArtistLine")
            append("Played in Liquid Music")
        }
    }

    private fun computeListeningStreakDays(timestamps: List<Long>): Int {
        if (timestamps.isEmpty()) return 0
        val zone = ZoneId.systemDefault()
        val uniqueDays = timestamps
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .distinct()
            .sortedDescending()

        val today = LocalDate.now(zone)
        var cursor = if (uniqueDays.first() == today || uniqueDays.first() == today.minusDays(1)) {
            uniqueDays.first()
        } else {
            return 0
        }

        var streak = 0
        for (day in uniqueDays) {
            if (day == cursor) {
                streak++
                cursor = cursor.minusDays(1)
            } else if (day.isBefore(cursor)) {
                break
            }
        }
        return streak
    }

    private fun deriveListeningPersonality(
        totalPlays: Int,
        totalMinutes: Int,
        topArtist: String?
    ): Pair<String, String> = when {
        totalPlays == 0 -> "Aurora Explorer" to "Play a few tracks to reveal your live Liquid Wrapped sonic signature."
        totalMinutes >= 60 -> "Midnight Audiophile" to "Deep, uninterrupted sessions anchored by ${topArtist ?: "atmospheric cuts"} and crisp studio highs."
        totalPlays >= 10 -> "Kinetic Curator" to "You flow effortlessly across moods, keeping ${topArtist ?: "fresh rhythms"} in constant rotation."
        else -> "Crystal Drift" to "Early soundwaves detected — ${topArtist ?: "your latest picks"} are already shaping your aurora palette."
    }
}
