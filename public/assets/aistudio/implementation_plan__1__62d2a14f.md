# Liquid Music — Native Android Streaming & Local Audio Player

Liquid Music is a native Android music streaming and local audio player built with Kotlin, Jetpack Compose (Material 3), and AndroidX Media3 ExoPlayer. It combines an authentic Apple iOS 26 "Liquid Glass" AGSL optical refraction engine and 120Hz spring physics with full online streaming, local MediaStore audio mixing, synced tap-to-seek lyrics, a real-time DSP Sound Lab, a live Gemini-powered AI Vibe DJ, and an Anytime "Liquid Wrapped" listening analytics dashboard.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The product and aesthetic decisions below incorporate your Phase 1 answers alongside key technical defaults. Please review and approve to begin implementation (note: when implementation begins, a one-click Firebase setup dialog will appear to provision **Firebase AI Logic** for the live Gemini Vibe DJ with zero API keys required).

- **Confirmed — AI Vibe DJ Engine**: Powered by live **Gemini (`gemini-3.8-flash`)** via **Firebase AI Logic + App Check** (no API key needed), paired with a smart built-in offline Vibe Intent Engine fallback so playlist curation works immediately even without connectivity.
- **Confirmed — Default Idle Accent Palette**: **Crystal Ice & Deep Indigo Aurora** (`#000000` AMOLED void, Crystal Ice `#D6F0FF`, Arctic Cyan `#7BDFF2`, Deep Indigo `#3B429F`) that smoothly crossfades (700ms) into dynamic colors extracted from each playing track's album art via AndroidX Palette.
- **Confirmed — Default Home Discovery Shelves**: **Global Pop, Hip-Hop & Electronic** leading the Home discovery carousels alongside Jump Back In, Chill/Late Night, and On Your Device local audio.
- **Confirmed — Full-Screen Now Playing Layout**: **Full-bleed Cover Art with floating Liquid Glass controls**, featuring edge-to-edge album artwork, dynamic Palette vignette glow, floating refractive glass transport/scrub controls, and one-tap/swipe access to full-screen interactive synced lyrics and the draggable queue sheet.
- **Technical Default — SDK Compatibility**: Preserves the project's validated Gradle/AGP `compileSdk 36` / `targetSdk 36` toolchain (fully compatible with Android 15/16 APIs and `androidx.core 1.18.0`) with `minSdk 26` support and graceful three-tier glass rendering across API 33+, API 31–32, and API 26–30.

---

## 1. Overview & Core Concept

- **What It Does**: Unifies online music streaming (via open Saavn streaming endpoints with 96/160/320 kbps quality selection and offline downloads), on-device local audio playback (MP3, FLAC, WAV, M4A, OGG via MediaStore), real-time hardware DSP effects (Equalizer, Bass Boost, Reverb, Speed/Pitch presets), interactive synced lyrics (LRCLIB), live Gemini AI playlist curation, and local listening statistics into a single tactile, 120Hz Liquid Glass interface.
- **Target Audience / Persona**: Mobile audiophiles, everyday music listeners, and design-conscious Android users who want both Spotify-class streaming/library management and power-user local playback + Sound Lab effects (Slowed + Reverb, Nightcore, 8D Spacious, custom EQ) without paywalls or sluggish UI chrome.
- **Key Value**: Delivers fluid 120Hz optical glass physics, seamless mixing of local device files and streamed tracks inside the same playlists and queue, and real-time DSP + AI curation in a self-contained, zero-paywall native Android experience.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **First-Launch Liquid Glass Onboarding & Local Sign-In**:
   - Opens over a slow-moving Crystal Ice & Deep Indigo aurora mesh canvas.
   - Displays the "Liquid Music" glass wordmark, refractive glass fields for Display Name, Email, and Password, a primary **Sign In / Create Account** glass button, **Continue with Google (Demo)**, and **Skip for Now (Guest Mode)**.
   - Any selection persists the profile locally via DataStore and smoothly transitions into the main player workspace.
2. **Home Discovery & Instant Playback**:
   - Time-of-day greeting with profile glass avatar and quick settings trigger.
   - Mood/genre glass filter chips (`All`, `Pop`, `Hip-Hop`, `Electronic`, `Chill`, `Local Files`).
   - 2-column Quick Picks grid (6 recent/favorite tracks) followed by horizontal carousels: *Jump Back In*, *Global Pop Hits*, *Hip-Hop & Rap Pulse*, *Electronic & Synthwave*, and *On Your Device*.
   - Tapping any track immediately starts Media3 playback, animates waveform visualizer bars on the active row, and reveals the floating Liquid Glass Mini-Player above the bottom dock.
3. **Signature iOS 26 Liquid Glass Bottom Tab Bar**:
   - Floating refractive capsule pinned cleanly above system navigation gesture insets with 5 tabs: **Home**, **Search**, **Library**, **Sound Lab**, and **Profile**.
   - Pressing and holding morphs the active frosted indicator into an enlarged crystal-clear **Liquid Glass Droplet Lens**; horizontal dragging glides continuously with zero snapping, applying velocity-based liquid stretch (`scaleX` up to `1.18f`) and squash (`scaleY` down to `0.90f`), crisp haptic ticks on each tab boundary, icon magnification/illumination under the lens, and a velocity-aware spring launch (`dampingRatio = 0.62f, stiffness = 380f`) on release.
4. **Floating Mini-Player & Full-Bleed Now Playing Sheet**:
   - **Mini-Player**: Docked right above the floating tab bar; supports horizontal swipe left/right to skip Next/Previous with album-art parallax and haptics, play/pause toggle, like burst, and vertical drag up to expand.
   - **Full-Screen Now Playing (Full-Bleed Cover Art)**: Edge-to-edge album art anchored behind a soft adaptive gradient scrim and Palette-extracted ambient aurora glow. Floating Liquid Glass top bar (collapse, quality badge, Sleep Timer badge, Queue trigger), floating glass playback controls (play/pause, skip, shuffle, repeat, heart burst, offline download toggle, speed/reverb quick indicator), and zero-recomposition scrub bar.
   - **Interactive Synced Lyrics**: Instant toggle/swipe reveals full-screen Apple Music-style lyrics fetched from LRCLIB—active line glows crisp white at `1.06f` scale, auto-scrolls smoothly to center, and **tapping any line** immediately seeks playback to that exact millisecond timestamp with a spring pulse and haptic click.
   - **Draggable Queue Sheet**: Accessible from Now Playing to inspect upcoming tracks, tap to jump, swipe to remove, or drag handles to reorder the live playback queue.
5. **Search + Live Gemini AI Vibe DJ**:
   - Floating glass search bar performs debounced live queries across Online API and Local Device tracks simultaneously with `All` / `Online` / `Local` filter pills.
   - Prominent **AI Vibe DJ** glass card accepts natural-language moods/scenarios (e.g., *"midnight highway synth drive"*, *"heavy gym phonk"*, *"rainy lo-fi coding"*). Gemini generates a curated playlist title, vibe manifesto, and targeted search queries in structured JSON, which automatically resolve into a playable 12-track playlist ready to play or save to Library.
   - Colorful glass Genre Browse grid appears when the search field is empty.
6. **Library & Custom Playlist Studio**:
   - Segmented glass chips for **Playlists**, **Liked Songs**, **Downloaded (Offline)**, and **Local Files** (with graceful in-app permission card when audio access isn't yet granted).
   - Full CRUD playlist creator/editor allowing custom title, description, zero-permission **Android Photo Picker** custom cover art, adding/removing both online and local tracks into the same playlist, and direct play/shuffle.
7. **Sound Lab Console**:
   - Real-time hardware-bound audio workbench: multi-band vertical glass Equalizer with 7 presets (*Flat, Bass Boost, Phonk Heavy, Vocal Clarity, Late Night, Studio, Custom*), tactile Bass Boost strength dial (`0–1000`), Spatial Hall Reverb pills (*None, Small Room, Medium Hall, Large Hall, Plate*), independent Speed (`0.5x–2.0x`) & Pitch (`0.5x–2.0x`) sliders, one-tap magic cards (**Normal**, **Slowed + Reverb**, **Nightcore**, **8D / Spacious** with subtle stereo pan modulation), Crossfade duration (`Off–12s`), Sleep Timer (`Off, 10m–60m, End of Track`), and Streaming Quality (`96 / 160 / 320 kbps`).
8. **Profile & Anytime "Liquid Wrapped"**:
   - Live listening analytics from Room: total minutes listened, total plays, daily listening streak badge, animated Top 5 Songs and Top 5 Artists cards with luminous glass progress bars, dynamic "Listening Personality" vibe card, and one-tap shareable Wrapped Summary export.
   - Account controls (edit display name, switch profile, sign out) and **Glass Quality** performance selector (*Ultra AGSL 120Hz*, *Balanced*, *Battery Saver*).

### Visual Identity & Theme

- **Aesthetic Direction**: Pitch-black AMOLED luxury dark minimalism fused with Apple iOS 26 optical Liquid Glass—refractive capsule edges, crisp Fresnel specular highlights, and dynamic album-art atmospheric glows.
- **Color Palette & Mood**:
  - `--background`: Pure AMOLED Obsidian `#000000`
  - `--surface`: Translucent Smoked Glass `rgba(18, 22, 36, 0.56)` with adaptive rim bevels
  - `--primary`: Crystal Ice `#D6F0FF`
  - `--secondary`: Arctic Cyan `#7BDFF2`
  - `--accent`: Deep Indigo Aurora `#3B429F` / `#5A67D8` (smoothly crossfading to track Palette vibrant/muted swatches over 700ms during playback)
  - `--on-surface`: Crisp Frost White `#F4F8FF` (primary text) and `#9EB0C8` (secondary metadata)
  - `--danger` / Like Glow: Luminous Rose `#FF4D8D`
- **Typography & Hierarchy**:
  - **Display & Headings**: `Outfit` (bundled locally via `font-util`) — tight geometric tracking for song titles, greetings, and Wrapped hero stats.
  - **Body & Controls**: `Plus Jakarta Sans` — clean, crisp legibility across carousels, lyrics, and settings.
  - **Technical Readouts**: `JetBrains Mono` — tabular numerals for timestamps, EQ Hz bands, playback speed (`0.84x`), and bitrate badges (`320 kbps`).
- **Interactive Feedback & Motion**:
  - Automatic highest-refresh-rate display mode selection (`120Hz` / `144Hz`) in `MainActivity`.
  - All continuous gesture animations (tab droplet drag, mini-player swipe/expand, seekbar scrubbing, Gel Press shader bulge) run inside `Modifier.graphicsLayer` / `DrawScope` backed by `Animatable` with zero per-frame layout recomposition.
  - Pre-compiled AGSL `RuntimeShader`s remembered across compositions; spring press feedback (`0.96f` scale + subtle haptic ticks) on all primary glass surfaces.

---

## 3. Key Product Decisions & Trade-Offs

- **Self-Contained 3-Tier Liquid Glass Pipeline**:
  - *Chosen Approach*: Custom AGSL `RuntimeShader` + `RenderEffect` refraction/chromatic-dispersion/Gel-Press pipeline on API 33+, `RenderEffect.createBlurEffect` + specular stroke on API 31–32, and translucent obsidian glass with multi-stop 1dp Fresnel borders on API 26–30, plus a user-configurable Glass Quality toggle.
  - *Why*: Eliminates fragile third-party glass library crashes while delivering true optical edge bending, RGB dispersion, and interactive finger refraction on modern Android devices and rock-solid performance on older GPUs.
  - *Alternatives Considered*: Third-party blur wrappers (often break under AGP 9 / Compose BOM updates or lack SDF lens refraction).
- **Live Gemini via Firebase AI Logic + Offline Vibe Fallback**:
  - *Chosen Approach*: `Firebase.ai.generativeModel("gemini-3.8-flash")` with strict `Schema.obj(...)` structured JSON output and App Check initialization in `MainActivity.onCreate()`, backed by a local Vibe Intent Engine when offline.
  - *Why*: Zero API key configuration for the end user, guaranteed parseable playlist plans (title, description, search queries), and uninterrupted functionality offline.
  - *Alternatives Considered*: Hardcoded keyword matching only (less creative for nuanced prompts) or raw REST API keys (requires manual key entry and leaks secrets).
- **Local Room + DataStore Persistence with Swappable `AuthRepository`**:
  - *Chosen Approach*: Room Database (KSP) for playlists, playlist-track cross-references, liked songs, offline downloads, and listening analytics; Jetpack DataStore Preferences for fake local auth session, Sound Lab DSP settings, audio quality, crossfade, and glass tier.
  - *Why*: Matches your explicit request for a fast local-first player with an immediately swappable `AuthRepository` interface ready for a future Firebase Auth swap, while keeping offline downloads and listening history private and instant.
  - *Alternatives Considered*: Cloud-gated Firestore (unnecessary friction since you requested instant demo/guest onboarding and local-first playlists/stats).
- **Play-Policy-Compliant Permissions & Media Selection**:
  - *Chosen Approach*: Zero-permission Android Photo Picker (`PickVisualMedia`) for custom playlist cover art; scoped `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` capped at `maxSdkVersion="32"` solely for scanning local audio files; explicit `foregroundServiceType="mediaPlayback"` for `LiquidPlaybackService`.
  - *Why*: Full Google Play Developer Policy compliance without requesting prohibited photo/video storage permissions.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                         UI LAYER (Jetpack Compose M3)                        │
│  ┌─────────────────────┐  ┌───────────────────────────────────────────────┐  │
│  │  LiquidGlassEngine  │  │ 5 Main Tabs + Onboarding + Overlay Sheets     │  │
│  │  • AGSL SDF Shader  │  │ • OnboardingAuth  • Home   • Search + Vibe DJ │  │
│  │  • 3-Tier Fallback  │  │ • Library & CRUD  • Sound Lab  • Profile/Wrap │  │
│  │  • Droplet Tab Bar  │  │ • Floating Mini-Player <-> Full-Bleed Player  │  │
│  │  • Gel Press Scale  │  │ • Synced Lyrics Overlay  • Draggable Queue    │  │
│  └─────────┬───────────┘  └──────────────────────┬────────────────────────┘  │
└────────────┼─────────────────────────────────────┼───────────────────────────┘
             │ Unidirectional StateFlow            │ User Actions / Gestures
             ▼                                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                       VIEWMODEL & STATE LAYER (MVVM)                         │
│  • PlayerViewModel (Playback, Queue, Crossfade, Sleep Timer, Lyrics, Palette)│
│  • DiscoveryViewModel (Home Carousels, Dual Search, Gemini AI Vibe DJ)       │
│  • LibraryViewModel (Playlists CRUD, Likes, Offline Downloads, Local Files)  │
│  • SoundLabViewModel (EQ Bands, BassBoost, Reverb, Speed/Pitch, 8D Pan)      │
│  • ProfileViewModel (Auth Session, Anytime Wrapped Analytics, Glass Tier)    │
└───────┬──────────────────────────┬───────────────────────────┬───────────────┘
        │                          │                           │
        ▼                          ▼                           ▼
┌────────────────────┐  ┌─────────────────────┐  ┌─────────────────────────────┐
│ AUDIO & DSP ENGINE │  │   DATA & NETWORK    │  │    LOCAL STORAGE & AI       │
│ • Media3 ExoPlayer │  │ • SaavnMusicApi     │  │ • Room DB (KSP): Playlists, │
│ • MediaSessionSvc  │  │   (sumit.co -> .dev)│  │   Tracks, Likes, Downloads, │
│ • Equalizer DSP    │  │ • Offline MP3 Seed  │  │   ListeningEventEntity      │
│ • BassBoost DSP    │  │ • LrcLibApi (LRC)   │  │ • DataStore: Fake Auth,     │
│ • PresetReverb DSP │  │ • MediaStore Audio  │  │   DSP, Quality, Glass Tier  │
│ • Crossfade/Sleep  │  │ • Offline File Mgr  │  │ • Firebase AI Logic (Gemini)│
└────────────────────┘  └─────────────────────┘  └─────────────────────────────┘
```

### Data Model & State

- **Unified `Track` Model (`@Immutable`)**: `id`, `title`, `artist`, `album`, `durationMs`, `artworkUrl`, `streamUrl96`, `streamUrl160`, `streamUrl320`, `localUri`, `downloadedFilePath`, `sourceType` (`ONLINE` | `LOCAL`), `addedAt`. Enables seamless mixing of online streams, downloaded files, and local MediaStore audio in any playlist or queue.
- **Room Database Entities**:
  - `TrackEntity`: Cached metadata for liked, downloaded, and playlist-referenced tracks (preserving both online stream URLs and local URIs).
  - `PlaylistEntity` & `PlaylistTrackCrossRef`: Custom playlists (id, name, description, custom cover URI, createdAt) and ordered track associations.
  - `LikedTrackEntity`: Liked track IDs with timestamp for instant heart state and Liked Songs collection.
  - `DownloadedTrackEntity`: Tracks saved locally under `filesDir/downloads/` with file path, bitrate, and byte size.
  - `ListeningEventEntity`: Automatically logged on track completion or after meaningful listening thresholds (`trackId`, `title`, `artist`, `artworkUrl`, `durationListenedMs`, `timestamp`) to power Anytime "Liquid Wrapped" stats and streaks.
- **DataStore Preferences**:
  - `UserProfile` (`displayName`, `email`, `handle`, `avatarSeed`, `isGuest`, `isSignedIn`) via `AuthRepository` / `MockLocalAuthRepository`.
  - `AudioSettings` (`audioQuality`, `crossfadeSeconds`, `glassQualityTier`, `eqPreset`, `eqBandLevels`, `bassBoostStrength`, `reverbPreset`, `playbackSpeed`, `playbackPitch`, `eightDEnabled`).

### Interactive Component & State Mapping

| Component / Surface | User Interaction | State Mutation & Runtime Behavior |
| :--- | :--- | :--- |
| **LiquidGlassBottomTabBar** | Hold + horizontal drag + release | Animates droplet scale/refraction & velocity-driven `scaleX`/`scaleY` inside `graphicsLayer`; triggers haptic ticks on tab crossings; commits `selectedTab` on spring settle. |
| **Mini-Player & Full-Bleed Player** | Swipe left/right, vertical drag up/down, scrub seekbar, tap play/next/prev/like/download | Connects to `MediaController` / `LiquidPlaybackService`; updates Palette ambient glow over 700ms; triggers file download worker to `filesDir/downloads/` and persists in Room. |
| **Synced Lyrics Sheet** | Auto-track position or tap any lyric line | `derivedStateOf` tracks active `LyricLine` index; auto-scrolls `LazyColumn`; tapping seeks ExoPlayer immediately to `line.timestampMs` with spring pulse + haptic feedback. |
| **Draggable Queue Sheet** | Drag reorder handle, tap track, remove item | Mutates ExoPlayer `MediaItem` order directly via `MediaController.moveMediaItem` / `removeMediaItem` so live playback continues uninterrupted. |
| **Search & AI Vibe DJ** | Type query, filter `All`/`Online`/`Local`, or submit vibe prompt | Parallel debounced queries to `SaavnMusicApi` + `LocalAudioRepository`; AI Vibe DJ calls `Firebase.ai` (`gemini-3.8-flash` JSON schema, 60s timeout) -> resolves 12 playable tracks -> saves/plays instant playlist; falls back cleanly to local Vibe Intent Engine on offline/error with clear status feedback. |
| **Sound Lab Controls** | Adjust EQ bands, Bass dial, Reverb pill, Speed/Pitch sliders, Magic presets, Crossfade, Sleep Timer | Binds directly to ExoPlayer `audioSessionId` (`Equalizer`, `BassBoost`, `PresetReverb`), applies `PlaybackParameters(speed, pitch)`, toggles 8D stereo balance modulation coroutine, and persists settings to DataStore. |
| **Library & Playlist Studio** | Create/edit/delete playlist, pick cover via `PickVisualMedia`, add/remove tracks, grant audio permission | Updates Room DAOs reactively via `Flow`; permission card triggers `RequestPermission` (`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` ≤ API 32) and triggers immediate MediaStore scan on grant. |
| **Profile & Wrapped** | Edit name, switch account, sign out, share Wrapped card, toggle Glass Quality | Updates `AuthRepository` / DataStore, computes Wrapped aggregations (total minutes, streak, Top 5 tracks/artists, listening personality), and invokes native Android Share Sheet (`Intent.ACTION_SEND`). |

### Key Sequences & Integrations

- **Online Streaming & Resilient Fallback**: Requests target `https://saavn.sumit.co/api/search/songs` via Retrofit/OkHttp with automatic retry/failover to `https://saavn.dev`; HTML entities are decoded and stream quality (`96`/`160`/`320 kbps`) is chosen dynamically. If offline or rate-limited, a built-in curated catalog of direct royalty-free MP3 streams ensures immediate playback.
- **Background Playback, Crossfade & Sleep Timer**: `LiquidPlaybackService` extends `MediaSessionService`, hosts ExoPlayer with audio focus and becoming-noisy handling, runs a coroutine monitor for smooth volume ramping during crossfade transitions and sleep timer fade-outs, and exposes `audioSessionId` to the DSP `SoundLabController`.
- **Firebase AI Logic Setup & App Check**: On approval, Firebase AI Logic (`requested_products: ["ai_logic"]`) will be provisioned via the interactive Firebase setup flow, and `configureAppCheck(this, intent)` will run as the first step in `MainActivity.onCreate()` before UI/ViewModels initialize so live Gemini requests pass attestation reliably.
