# Liquid Music × Echo Architecture Overhaul

Rebuild Liquid Music's streaming backend, browse engine, audio pipeline, lyrics engine, and offline storage around Echo Music's modular architecture—combining a YouTube Music InnerTube primary provider with automatic JioSaavn failover and Local Audio—while preserving the iOS 26 AndroidLiquidGlass optical refraction UI, AI Vibe DJ, Anytime Wrapped, and Liquid Glass Studio.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All core architectural and product decisions below reflect your confirmed choices from Phase 1. Review the blueprint and approve to begin implementation.

- **Confirmed Visual Identity**: Keep the **AndroidLiquidGlass iOS 26 real-time optical refraction UI** (floating refracting bottom capsule, sliding droplet pill, full-bleed Now Playing glass controls, and Liquid Glass Studio) while rebuilding every screen's layout, navigation depth, and reliability.
- **Confirmed Modular Backend**: **YouTube Music InnerTube** as primary streaming/catalog provider + **JioSaavn** as automatic secondary fallback + **Local Audio** scanner, strictly isolated behind unified provider interfaces and unified domain models—zero provider-specific logic inside UI screens or the Media3 player.
- **Confirmed Feature Suite**: Full Browse (Home, Charts, Moods, Album Detail, Artist Detail, Endless Radio), Synced Karaoke Lyrics + AI Translation, Pro Audio Engine (Skip Silence, Volume Normalization, Gapless, Crossfade, EQ, 8D), Smart Playback Cache + Offline Downloads, and zero-permission SAF Database Backup/Restore & Playlist Import/Export.
- **Confirmed Existing Features**: Keep and deeply integrate **Gemini AI Vibe DJ** (now resolving mixes through the unified InnerTube/JioSaavn orchestrator), **Anytime Wrapped**, and **Liquid Glass Studio**.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms Liquid Music into a rock-solid, ad-free, multi-source Android music player modeled on Echo Music's clean separation of concerns. Users can browse live YouTube Music charts, moods, artists, and full albums, launch instant Endless Radio stations, search with live suggestions and category filters, sing along to word/line-synced karaoke lyrics with one-tap translation, listen offline via both automatic smart caching and explicit downloads, and export/restore their entire library and playlists anytime.
- **Target Audience / Persona**: Audiophiles and everyday Android listeners who want YouTube Music's global catalog depth, seamless fallback reliability, power-user playback features (skip silence, loudness normalization, offline cache, backups), and a fluid 120Hz iOS 26 Liquid Glass visual experience.
- **Key Value**: Eliminates broken streams and thin catalog screens by decoupling every music source behind a fault-tolerant provider orchestrator with automatic InnerTube → JioSaavn playback failover, lazy just-in-time stream URL resolution, and resilient local caching.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Rich Discovery & Deep Browse (Home & Explore)**:
   - Opening the app reveals an animated dynamic-palette aurora canvas behind **Quick Picks** (4-row swipeable song grid), **Continue Listening**, **Mood & Genre Pills**, **Global & Regional Charts**, and **Trending Albums & Artists**.
   - Tapping any **Album** opens a full-bleed **Album Detail View** (artwork hero, metadata, Play All, Shuffle, Download Album, Save to Library, full numbered tracklist).
   - Tapping any **Artist** opens an **Artist Detail View** (artist banner, Start Artist Radio, Top Songs, Albums & Singles carousel, Related Artists).
   - Tapping **Start Radio** on any track or artist seeds an **Endless Radio** queue that automatically fetches and appends similar songs before the queue runs out.
2. **Unified Search + AI Vibe DJ**:
   - Typing shows instant debounced **Search Suggestions** and recent search history chips.
   - Results are cleanly tabbed across **All**, **Songs**, **Albums**, **Artists**, **Playlists**, and **Local**, normalized across InnerTube and JioSaavn with clear loading shimmers and retryable error states.
   - Switching to the **Vibe DJ** pill opens the Gemini AI curator, which generates mood-matched tracks and resolves each pick through the unified provider orchestrator for immediate playback.
3. **Full-Bleed Now Playing, Queue & Synced Karaoke Lyrics**:
   - Expanding the floating Mini-Player reveals full-bleed album art captured into a live optical backdrop for all floating glass transport controls, refracting scrub slider, and action pills.
   - Switching to **Lyrics** opens a full-screen synchronized viewer with smooth auto-scroll, word/phrase karaoke glow progression, **tap-any-line to seek**, provider source badge (LRCLIB / InnerTube), and a **Translate / Original** toggle powered by Gemini AI (cached locally in Room so repeated plays translate instantaneously).
   - Switching to **Up Next** reveals the unified playback queue with drag-to-reorder, remove, Save Queue as Playlist, and an **Auto-Play Radio** toggle.
4. **Pro Audio Engine & Sound Lab**:
   - Dedicated toggles and refracting sliders for **Skip Silence**, **Audio Volume Normalization** (with target loudness gain), **Gapless Playback**, **Crossfade (0–12s)**, **5-Band Hardware Equalizer + Presets**, **Bass Boost**, **Reverb**, **Speed/Pitch**, **8D Spatial Aura**, **Stream Quality**, and **Sleep Timer**.
5. **Library, Smart Cache & Zero-Permission Backup/Restore**:
   - Library organizes **Liked Songs**, **Playlists** (with M3U8/JSON Import & Export), **Saved Albums**, **Downloads**, **Local Device Audio**, and **Recently Played**.
   - Profile houses **Anytime Wrapped**, **Liquid Glass Studio** (live draggable refractive preview pill, pill size, spring stiffness/damping, edge refraction, blur, RGB dispersion), **Smart Audio Cache Manager** (live cache footprint, size cap selector, one-tap Clear Cache), and **Backup & Restore** (export/import full library snapshots via Android Storage Access Framework).

### Visual Identity & Theme
- **Aesthetic Direction**: **Nocturnal Crystal Liquid Glass** — pure AMOLED obsidian depth layered with drifting cover-art-reactive aurora meshes and real-time `AndroidLiquidGlass` GPU lens refraction on floating chrome, buttons, sliders, and toggles.
- **Color Palette & Mood**:
  - Background / Void: Pure AMOLED Obsidian (`#04060D`) & Deep Void (`#0B1020`)
  - Primary Accent: Dynamic Cover-Art Glow defaulting to Crystal Ice (`#7BE8FF`) & Arctic Cyan (`#38D9F5`)
  - Secondary Accent: Deep Indigo Aurora (`#2B337A`) & Luminous Rose (`#F472B6`) for active highlights
  - Text Hierarchy: Frost White (`#F4F8FF`) primary, Mist Secondary (`#9BB0CC`) metadata, muted slate dividers
- **Typography & Hierarchy**:
  - Display / Headers: **Outfit** (Bold / SemiBold) for screen titles, album/artist heroes, and Wrapped callouts
  - Body / Metadata: **Plus Jakarta Sans** (Medium / Regular) for track rows, descriptions, and lyrics
  - Readouts / Timers: **JetBrains Mono** for timestamps, EQ dB bands, bitrates, and cache metrics
- **Component Styling & Layout**:
  - Edge-to-edge full-bleed layout respecting `WindowInsets.statusBars` and `navigationBarsPadding()` on the floating dock.
  - 8dp spatial grid, 24–35dp continuous capsule geometry, minimum 48dp touch targets on every icon button and interactive row.
  - Real-time GPU `drawBackdrop` + `lens` refraction reserved for floating chrome (bottom capsule, droplet pill, mini-player, Now Playing controls, sliders, toggles, studio preview), while dense scrolling track rows stay lightweight for locked 120Hz scrolling.
- **Interactive Feedback & Motion**:
  - Velocity-stretched spring physics driven live by `LiquidGlassConfig` (`springStiffness`, `springDamping`, `pillScale`).
  - Subtle haptic ticks on tab switches, drag snapping, scrubbing, and toggles; shimmer skeletons for network loading; clear inline retry banners when offline or when a provider fails over.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unified Provider Abstraction with Automatic Failover (`InnerTube` → `JioSaavn` → `Local`)**
  - *Chosen Approach*: Define strict provider contracts (`MusicCatalogProvider`, `StreamResolver`, `LyricsSourceProvider`) and an orchestration layer (`UnifiedMusicRepository`) that queries InnerTube first, normalizes all results into unified domain models (`Song`, `AlbumDetail`, `ArtistDetail`, `BrowseSection`), and transparently falls back to JioSaavn when InnerTube returns empty results, regional blocks, or expired/unplayable streams.
  - *Why*: YouTube Music InnerTube has unmatched global catalog, charts, album/artist graphs, and radio continuation, while JioSaavn provides fast direct high-bitrate streams as an instant safety net. Decoupling providers from UI and playback ensures adding future providers requires zero UI or player changes.
  - *Alternatives Considered*: Calling InnerTube or JioSaavn directly from ViewModels or ExoPlayer — rejected because it leaks provider quirks into UI state and causes playback failures when signed stream URLs expire.

- **Decision 2: Just-In-Time Stream Resolution & Media3 `SimpleCache`**
  - *Chosen Approach*: Store stable provider IDs (`providerId`, `source`) on every `Song` in queues and Room rather than trusting short-lived streaming URLs. Right before playback (and during preload of the next track), a custom Media3 `ResolvingDataSource` + `CacheDataSource` resolves the fresh audio stream URL (Local file → Offline Download → `SimpleCache` hit → InnerTube `player` clients with multi-client fallback `ANDROID_VR` / `IOS` / `WEB_REMIX` → JioSaavn fallback match).
  - *Why*: YouTube Music stream URLs expire after a few hours; resolving just-in-time inside the playback data pipeline guarantees playlists, liked songs, and paused queues resume cleanly days later, while `SimpleCache` makes replays instant and offline-resilient.
  - *Alternatives Considered*: Caching raw URLs in Room — rejected because saved songs break once URLs expire.

- **Decision 3: Multi-Provider Lyrics Engine + Cached Gemini AI Translation**
  - *Chosen Approach*: Cascade `LRCLIB` (rich line/word-synced LRC) → `InnerTube Lyrics` (`browse` lyrics tab) → plain fallback, parsed into a unified `SyncedLyrics` model supporting line-level and word-level karaoke highlighting, persisted in Room. When the user taps **Translate**, invoke Firebase AI Logic (`gemini-3.8-flash`, `Schema.obj` JSON array, `withTimeout(60_000L)`) to translate lines while preserving exact timestamps, and cache the translated track locally.
  - *Why*: Combines fast, accurate community-synced timestamps with on-demand multilingual translation/romanization without external translation API keys.

- **Decision 4: Zero-Permission Backup/Restore & Playlist Transfer via SAF**
  - *Chosen Approach*: Use Android Storage Access Framework (`CreateDocument` / `OpenDocument`) to export and import versioned JSON library backups (likes, playlists, history, saved albums, settings) and standard `.m3u8` / `.json` playlists.
  - *Why*: Fully Google Play policy compliant (zero broad storage permissions required) and works smoothly on modern Android 13–16 devices.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### System Architecture & Component Hierarchy

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                     UI LAYER (Jetpack Compose + M3 + LiquidGlass)            │
│  ┌───────────┐ ┌────────────┐ ┌────────────┐ ┌───────────┐ ┌──────────────┐  │
│  │ Home &    │ │ Search &   │ │ Library &  │ │ Sound Lab │ │ Profile,     │  │
│  │ Explore   │ │ AI Vibe DJ │ │ Backups    │ │ Pro DSP   │ │ Wrapped &    │  │
│  │ (Charts)  │ │ (Suggest)  │ │ (Import)   │ │ (EQ/Norm) │ │ Glass Studio │  │
│  └─────┬─────┘ └─────┬──────┘ └─────┬──────┘ └─────┬─────┘ └──────┬───────┘  │
│        └─────────────┴──────────────┴──────────────┴──────────────┘          │
│                                     │                                        │
│  ┌─────────────────────────┐  ┌─────▼─────────────────────────────────────┐  │
│  │ Detail Overlays         │  │ Floating Liquid Chrome (LayerBackdrop)    │  │
│  │ • Album Detail Screen   │  │ • Floating Mini-Player                    │  │
│  │ • Artist Detail Screen  │  │ • LiquidGlassBottomTabBar + Droplet Pill  │  │
│  │ • Full-Bleed NowPlaying │  │ • Synced Karaoke Lyrics & Queue Sheets    │  │
│  └────────────┬────────────┘  └────────────────────┬──────────────────────┘  │
└───────────────┼────────────────────────────────────┼─────────────────────────┘
                ▼                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     STATE & VIEWMODEL LAYER (StateFlow MVVM)                 │
│  BrowseViewModel · SearchViewModel · PlayerViewModel · LibraryViewModel      │
│  SoundLabViewModel · ProfileBackupViewModel                                  │
└───────────────┬────────────────────────────────────┬─────────────────────────┘
                ▼                                    ▼
┌────────────────────────────────────┐ ┌───────────────────────────────────────┐
│   UNIFIED DOMAIN & ORCHESTRATION   │ │      PLAYBACK & AUDIO ENGINE          │
│                                    │ │                                       │
│  ┌──────────────────────────────┐  │ │  ┌─────────────────────────────────┐  │
│  │ UnifiedMusicRepository       │  │ │  │ LiquidPlaybackService (Media3)  │  │
│  │ • Catalog & Search Merging   │  │ │  │ • ExoPlayer + Gapless           │  │
│  │ • Endless Radio Continuation │  │ │  │ • Skip Silence Processor        │  │
│  │ • Automatic Provider Fallback│  │ │  │ • LoudnessEnhancer Normalization│  │
│  └───────┬──────────┬───────────┘  │ │  │ • Crossfade & 5-Band EQ / 8D    │  │
│          │          │              │ │  └───────────────┬─────────────────┘  │
│   ┌──────▼───┐ ┌────▼─────┐ ┌────┐ │ │                  │                    │
│   │InnerTube │ │ JioSaavn │ │Loc.│ │ │  ┌───────────────▼─────────────────┐  │
│   │(Primary) │ │(Fallback)│ │Aud.│ │ │  │ Smart DataSource Chain          │  │
│   │• search  │ │• search  │ │Scan│ │ │  │ Local File → Offline Download → │  │
│   │• browse  │ │• charts  │ │    │ │ │  │ Media3 SimpleCache →            │  │
│   │• next    │ │• streams │ │    │ │ │  │ ResolvingDataSource (JIT URL)   │  │
│   │• player  │ │          │ │    │ │ │  └─────────────────────────────────┘  │
│   └──────────┘ └──────────┘ └────┘ │ └───────────────────────────────────────┘
│                                    │
│  ┌──────────────────────────────┐  │ ┌───────────────────────────────────────┐
│  │ Multi-Provider Lyrics Engine │  │ │   PERSISTENCE & AI INTEGRATIONS       │
│  │ • LRCLIB (Synced + Word LRC) │  │ │  • Room DB (Likes, Playlists, Saved   │
│  │ • InnerTube Lyrics Browse    │  │ │    Albums, History, Cached Lyrics,    │
│  │ • Gemini AI Line Translation │  │ │    Downloads)                         │
│  └──────────────────────────────┘  │ │  • DataStore (Audio DSP, Cache Cap,   │
│                                    │ │    Glass Studio Config, Profile)      │
│  ┌──────────────────────────────┐  │ │  • SAF Backup/Restore & M3U8 Transfer │
│  │ Gemini AI Vibe DJ (Firebase) │  │ │  • Firebase AI Logic + App Check      │
│  └──────────────────────────────┘  │ └───────────────────────────────────────┘
└────────────────────────────────────┘
```

### Unified Data Model & State
- **Provider-Agnostic Entities**:
  - `Track` (unified `id`, `sourceProvider: MusicSource [YOUTUBE_MUSIC, JIOSAAVN, LOCAL]`, `providerTrackId`, `title`, `artist`, `artistId`, `album`, `albumId`, `artworkUrl`, `durationMs`, `explicit`, `isDownloaded`, `localPath`, optional `streamUrl` resolved lazily).
  - `AlbumSummary` & `AlbumDetail` (`id`, `browseId`, `sourceProvider`, `title`, `artist`, `artistId`, `year`, `artworkUrl`, `tracks`, `description`).
  - `ArtistSummary` & `ArtistDetail` (`id`, `browseId`, `sourceProvider`, `name`, `thumbnailUrl`, `subscribersText`, `topSongs`, `albums`, `singles`, `relatedArtists`, `radioPlaylistId`).
  - `BrowseSection` (`title`, `subtitle`, `layoutType [QUICK_PICKS_GRID, CAROUSEL_ALBUMS, CAROUSEL_ARTISTS, CHART_LIST, MOOD_CHIPS]`, `items`).
  - `LyricsDocument` (`trackId`, `source`, `isSynced`, `lines: List<LyricLine>` where each `LyricLine` holds `startMs`, `endMs`, `text`, optional `words: List<LyricWord>` for karaoke fill, and optional `translatedText`).
  - `AudioEngineSettings` (extends `SoundLabSettings` with `skipSilenceEnabled`, `volumeNormalizationEnabled`, `gaplessPlaybackEnabled`, `autoPlayRadioEnabled`, `maxCacheSizeMb`, `streamQuality`).
- **Room Database Upgrade** (schema bumped cleanly with safe migration / fallback):
  - Existing tables preserved (`liked_tracks`, `playlists`, `playlist_tracks`, `play_history`, `downloads`) plus new tables: `saved_albums`, `cached_lyrics`, and `search_history`.

### Interactive Component & State Mapping
- **Home / Explore Browse**: Opening Home triggers parallel InnerTube Home + Charts/Moods fetches with automatic JioSaavn failover on network/parse error; UI renders `BrowseUiState` (`Loading` shimmers → `Success` sections → `Error` with retry button). Tapping an album/artist pushes an in-app detail sheet/screen backed by `BackHandler` and loads full `AlbumDetail` / `ArtistDetail` state.
- **Search & Suggestions**: Typing into Search debounces 280ms to fetch live InnerTube search suggestions, records submitted queries in Room `search_history`, and switches category tabs (`All`, `Songs`, `Albums`, `Artists`, `Playlists`, `Local`) against the unified orchestrator without re-fetching unnecessarily.
- **Playback, Radio & Fallback**: Tapping any song builds a unified queue in `PlayerViewModel`. If InnerTube stream resolution fails for a track, the orchestrator automatically searches JioSaavn by `title + artist` to resolve a playable stream seamlessly and displays a subtle non-intrusive status chip if fallback was used. When the queue nears its final track and **Auto-Play Radio** is enabled, the orchestrator calls InnerTube `next` (or JioSaavn recommendations) to append related songs automatically.
- **Lyrics & Translation**: Opening the Lyrics sheet loads Room `cached_lyrics` first; on miss, queries LRCLIB then InnerTube Lyrics and stores the result. Active line & karaoke word progress update at 60fps from ExoPlayer position. Tapping **Translate** triggers `Firebase.ai` (`gemini-3.8-flash`) with a loading indicator, persists translated lines to Room, and allows instant toggling between Original and Translated lyrics.
- **Audio DSP & Cache Controls**: Toggling Skip Silence, Normalization, Gapless, Crossfade, EQ, or 8D immediately persists to DataStore and updates `LiquidPlaybackService` / `SoundLabController` live without interrupting playback. Cache controls read real `SimpleCache.cacheSpace` bytes and support one-tap cache eviction.
- **Backup, Restore & Playlist Transfer**: Tapping **Export Backup** or **Export Playlist (M3U8/JSON)** launches SAF `CreateDocument`, streaming a structured snapshot directly to the user-selected URI; **Restore Backup** / **Import Playlist** launches SAF `OpenDocument`, validates JSON/M3U8 structure, merges records into Room transactionally, and shows a completion summary banner.

### Key Sequences & Integrations
- **YouTube Music InnerTube Protocol**: Clean Kotlin OkHttp/Moshi client targeting `https://music.youtube.com/youtubei/v1/{browse,search,next,player,music/get_search_suggestions}` using `WEB_REMIX` for rich catalog/browse/search/lyrics/radio metadata and multi-client stream extraction (`ANDROID_VR` / `IOS` / `WEB_REMIX`) for reliable direct audio stream URLs (`audio/mp4` / `audio/webm`) without external server dependencies.
- **Firebase AI Logic (Already Provisioned)**: Reuses existing `configureAppCheck(this, intent)` wiring in `MainActivity.onCreate()` and lazy `Firebase.ai.generativeModel("gemini-3.8-flash")` with `Schema.obj` structured JSON and `withTimeout(60_000L)` for both **AI Vibe DJ** and **Synced Lyrics Translation**.
