package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.repository.UnifiedMusicRepository
import com.example.domain.model.Track
import com.example.domain.model.VibeDjResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "VibeDjRepository"

data class VibePlan(
    val title: String,
    val manifesto: String,
    val moodTags: List<String>,
    val searchQueries: List<String>,
    val poweredByGemini: Boolean
)

/**
 * Gemini AI Vibe DJ powered by Firebase AI Logic (`gemini-3.8-flash`), resolving curated picks
 * through the unified Echo Music orchestrator (InnerTube primary -> JioSaavn fallback).
 */
class VibeDjRepository(
    private val context: Context,
    private val musicRepository: UnifiedMusicRepository
) {
    private val vibeSchema = Schema.obj(
        properties = mapOf(
            "playlistTitle" to Schema.string(description = "A poetic, sleek 2-4 word playlist title"),
            "manifesto" to Schema.string(description = "A crisp 1-sentence sonic vibe description"),
            "moodTags" to Schema.array(
                items = Schema.string(),
                description = "3 short mood/genre tags"
            ),
            "searchQueries" to Schema.array(
                items = Schema.string(),
                description = "6 specific song title + artist search queries matching the user's vibe"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = vibeSchema
                temperature = 0.85f
            }
        )
    }

    suspend fun curateVibePlaylist(
        prompt: String,
        localTracks: List<Track> = emptyList()
    ): Result<VibeDjResult> {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            return Result.failure(IllegalArgumentException("Describe a mood or vibe to curate a playlist."))
        }

        if (!isNetworkAvailable()) {
            val offlinePlan = buildOfflineVibePlan(cleanPrompt)
            val resolved = resolveTracksForPlan(offlinePlan, localTracks)
            return Result.success(
                VibeDjResult(
                    title = offlinePlan.title,
                    manifesto = "${offlinePlan.manifesto} (Offline Vibe Engine)",
                    moodTags = offlinePlan.moodTags,
                    tracks = resolved,
                    poweredByGemini = false
                )
            )
        }

        return try {
            val geminiPrompt = """
                You are Liquid Music's AI Vibe DJ.
                Curate a cohesive playlist for this listener mood/scenario: "$cleanPrompt".
                Return 6 diverse, popular, real song+artist search queries that match the tempo, energy, and atmosphere.
                Keep titles concise and authentic.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(geminiPrompt)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Gemini returned an empty vibe curation.")

            val json = JSONObject(jsonText)
            val title = json.optString("playlistTitle", "Liquid Vibe Mix").ifBlank { "Liquid Vibe Mix" }
            val manifesto = json.optString("manifesto", "Curated specifically for your atmosphere.")
            val tagsArr = json.optJSONArray("moodTags")
            val tags = buildList {
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        val t = tagsArr.optString(i).trim()
                        if (t.isNotBlank()) add(t)
                    }
                }
            }.ifEmpty { listOf("Curated", "Aurora", "Vibe") }

            val queriesArr = json.optJSONArray("searchQueries")
            val queries = buildList {
                if (queriesArr != null) {
                    for (i in 0 until queriesArr.length()) {
                        val q = queriesArr.optString(i).trim()
                        if (q.isNotBlank()) add(q)
                    }
                }
            }.ifEmpty { listOf(cleanPrompt) }

            val plan = VibePlan(
                title = title,
                manifesto = manifesto,
                moodTags = tags.take(4),
                searchQueries = queries.take(6),
                poweredByGemini = true
            )
            val tracks = resolveTracksForPlan(plan, localTracks)
            Result.success(
                VibeDjResult(
                    title = plan.title,
                    manifesto = plan.manifesto,
                    moodTags = plan.moodTags,
                    tracks = tracks,
                    poweredByGemini = true
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini Vibe DJ timed out", e)
            Result.failure(Exception("Gemini Vibe DJ timed out after 60s. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Vibe DJ failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Gemini Vibe DJ failed to respond."))
        }
    }

    suspend fun curateOfflineVibe(prompt: String, localTracks: List<Track>): VibeDjResult {
        val plan = buildOfflineVibePlan(prompt)
        val resolved = resolveTracksForPlan(plan, localTracks)
        return VibeDjResult(
            title = plan.title,
            manifesto = plan.manifesto,
            moodTags = plan.moodTags,
            tracks = resolved,
            poweredByGemini = false
        )
    }

    private suspend fun resolveTracksForPlan(
        plan: VibePlan,
        localTracks: List<Track>
    ): List<Track> = coroutineScope {
        val fetchedGroups = plan.searchQueries.map { query ->
            async {
                runCatching { musicRepository.searchUnified(query, limit = 3).songs }
                    .getOrDefault(emptyList())
            }
        }.awaitAll()

        val combined = LinkedHashMap<String, Track>()
        fetchedGroups.flatten().forEach { track ->
            if (combined.size < 14) combined[track.id] = track
        }
        localTracks.take(4).forEach { local ->
            if (combined.size < 14) combined.putIfAbsent(local.id, local)
        }
        if (combined.isEmpty()) {
            val fallbackSearch = runCatching {
                musicRepository.searchUnified(plan.searchQueries.firstOrNull() ?: "top global hits", limit = 10).songs
            }.getOrDefault(emptyList())
            fallbackSearch.forEach { combined[it.id] = it }
        }
        val list = combined.values.toList().take(14)
        musicRepository.registerTracks(list)
        list
    }

    private fun buildOfflineVibePlan(prompt: String): VibePlan {
        val lower = prompt.lowercase()
        return when {
            lower.contains("gym") || lower.contains("phonk") || lower.contains("workout") -> VibePlan(
                title = "Hyperdrive Phonk",
                manifesto = "Relentless bass pressure and kinetic tempo built for peak output.",
                moodTags = listOf("Phonk", "High Energy", "Bass"),
                searchQueries = listOf("phonk bass", "travis scott", "hip hop gym", "electronic workout"),
                poweredByGemini = false
            )
            lower.contains("rain") || lower.contains("chill") || lower.contains("study") || lower.contains("lofi") -> VibePlan(
                title = "Cobalt Rain Focus",
                manifesto = "Warm vinyl textures and velvet chords for late-night deep work.",
                moodTags = listOf("Lo-Fi", "Chill", "Ambient"),
                searchQueries = listOf("lofi chill", "ambient piano", "billie eilish", "late night chill"),
                poweredByGemini = false
            )
            lower.contains("drive") || lower.contains("night") || lower.contains("synth") || lower.contains("highway") -> VibePlan(
                title = "Midnight Synth Drive",
                manifesto = "Neon-drenched arpeggios and gliding basslines for open highways.",
                moodTags = listOf("Synthwave", "Night Drive", "Electronic"),
                searchQueries = listOf("the weeknd blinding lights", "daft punk", "synthwave", "kavinsky nightcall"),
                poweredByGemini = false
            )
            else -> VibePlan(
                title = "Crystal Aurora Mix",
                manifesto = "A fluid blend of luminous pop hooks, electronic atmosphere, and rhythm.",
                moodTags = listOf("Pop", "Electronic", "Aurora"),
                searchQueries = listOf(prompt, "global pop hits", "electronic dance", "hip hop essentials"),
                poweredByGemini = false
            )
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
