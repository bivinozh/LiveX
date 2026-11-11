# LiveX

A streaming **learning lab** for Android TV (and phones/tablets): a Media3/ExoPlayer player with
every production concern exposed and explained on screen: HLS/DASH, ABR, buffering, live latency,
client- and server-side ads, Widevine DRM, QoE analytics, background playback and TV input.

Kotlin · Jetpack Compose for TV · MVI · Hilt · Media3 1.11 · Room · DataStore · minSdk 36

## Architecture

```
┌──────────────────────────────── UI (Compose, MVI) ─────────────────────────────────┐
│ HomeScreen ─► PlayerScreen ─► ManifestScreen / BufferSettingsScreen                 │
│      │             │  PlayerView ◄── MediaController ──┐        SessionsScreen       │
│      │             │  GuideOverlay · StatsOverlay      │        DeviceInfoScreen     │
│      ▼             ▼  VodProgressBar · LiveControlBar  │              │              │
│ HomeViewModel  PlayerViewModel (state/intent/effect)   │   SessionsVM · DeviceInfoVM │
└────────────────────────┬───────────────────────────────┼─────────────┬─────────────┘
                         │ start / seekBy / throttle ...  │ MediaSession │
┌────────────────────────▼───────────────────────────────┴───┐         │
│ PlaybackEngine (singleton)            PlaybackService      │         │
│  ExoPlaybackEngine ── ExoPlayer ◄──── (MediaSessionService: │         │
│   ├ DefaultLoadControl (BufferSettings, DataStore)          │  notification, lock screen,
│   ├ DefaultMediaSourceFactory                               │  media keys, PiP controls)
│   │   ├ ThrottlingDataSource ─► OkHttpDataSource (OkHttp)   │         │
│   │   ├ LiveXLoadErrorPolicy (retries + backoff)            │         │
│   │   ├ DefaultDrmSessionManagerProvider (Widevine)         │         │
│   │   ├ ImaAdsLoader (CSAI: VAST/VMAP)                      │         │
│   │   └ ImaServerSideAdInsertionMediaSource (SSAI: DAI)     │         │
│   ├ QoeTracker (AnalyticsListener) ─► QoeCalculator/StallTracker     │
│   └ QoeEventLogger ──┐                                      │         │
└──────────────────────┼──────────────────────────────────────┘         │
                       ▼                                                │
              QoeEventBus (SharedFlow) ─► QoePipeline ─┬─► Room (sessions, events) ◄┘
                                                       └─► QoeReporter(s): Fake / Conviva / Mux …
```

- **MVI**: `MviViewModel` holds one immutable state, takes intents, emits one-off effects.
  High-frequency telemetry (stats, live window, progress) are separate 500 ms flows, not state.
- **PlaybackEngine** hides ExoPlayer behind an interface, so ViewModels are tested with a fake.
  It's an app-wide singleton shared by the player screen and `PlaybackService`.
- **Pure-Kotlin cores** (stall classification, QoE events, ad seek policy, live window, video
  limits, device capability rules, manifest analysis) take time/values as input and are unit-tested
  on the JVM. Android-facing code is tested with Robolectric.

## Features

| Area | What's there | Where |
|---|---|---|
| Playback | HLS & DASH, VOD & live, sample catalog | `player/ExoPlaybackEngine`, `data/SampleStreams` |
| Errors | Friendly messages per error code (network, HTTP, decoder, DRM), retry, behind-live-window recovery | `player/PlaybackErrors`, `PlayerViewModel` |
| Stats overlay | Resolution, bitrate, bandwidth, buffer, live offset, speed, dropped frames, buffer chart | `qoe/StatsState`, `ui/player/StatsOverlay`, `BufferChart` |
| Stall detection | VST vs seek buffering vs real stalls, rebuffering ratio | `qoe/StallTracker` |
| Network lab | Throttle simulator (5 Mbps … unstable), custom retry/backoff policy | `network/*` |
| Buffer tuning | DefaultLoadControl sliders + presets, saved in DataStore | `settings/*` |
| Live latency | LiveConfiguration targets (3/10/30 s), GO LIVE, DVR seek bar | `player/live/*`, `LiveControlBar` |
| Ads | IMA CSAI (VAST/VMAP pre/mid/post), IMA DAI SSAI, ad markers, no seeking past unwatched ads | `player/ads/*` |
| DRM | Widevine DASH, L1/L3 detection, SD cap on L3, DRM error messages | `player/drm/*`, `VideoLimits` |
| QoE pipeline | Structured events → Room sessions screen → pluggable reporters | `qoe/QoeEventLogger`, `qoe/store/*`, `feature/sessions/*` |
| Background & PiP | MediaSessionService, MediaController, notification/lock screen, auto-enter PiP on phones | `playback/*`, `ui/player/PictureInPicture` |
| Android TV | Leanback launcher + banner, D-pad focus, remote-friendly controls, Back conventions | `AndroidManifest.xml`, `ui/player/*` |
| Device info | MediaCodecList: 4K, HEVC, HDR, tunneling, secure decoders; display; memory | `device/*` |
| Manifest viewer | Raw HLS/DASH with highlights and variant browsing | `manifest/*` |

## Build & test

```
./gradlew :app:assembleDebug            # build
./gradlew :app:testDebugUnitTest        # JVM + Robolectric tests
./gradlew :app:connectedDebugAndroidTest  # instrumentation tests (device/emulator)
```

Runs on an Android TV emulator (API 36+). Logcat tags: `EventLogger` (debug), `LiveX-Ads`,
`LiveX-DRM`, `LiveX-QoE`, `LiveX-Session`.

## Notes

- Sample streams and ad tags are public test assets (Apple, Unified Streaming, DASH-IF, Google IMA,
  Widevine UAT proxy). No API keys in the repo; real analytics keys belong in build/remote config.
- Emulators are Widevine L3 and have no HDR/4K display: HD Widevine is capped at SD there by design.
