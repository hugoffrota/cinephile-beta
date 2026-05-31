# Cinephile

> An Android movie recommendation app powered by local ONNX embeddings, TMDb, and a custom hybrid recommendation engine.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin)
![Architecture](https://img.shields.io/badge/architecture-MVVM-blue)
![Edge](https://img.shields.io/badge/edge-Cloudflare%20Workers-orange?logo=cloudflare)
![License](https://img.shields.io/badge/license-MIT-green)

---

## What It Does

**Cinephile** is a personal movie discovery assistant. It learns your taste from your Letterboxd ratings, builds a local vector embedding of your preferences using a local ONNX model (`all-MiniLM-L6-v2`), and recommends movies you'll actually want to watch — all without sending your viewing history to any cloud service.

### Key Features

| Feature | Description |
|---------|-------------|
| **Smart Recommendations** | Hybrid scoring: content similarity + genre affinity + rating quality + cast/director overlap + MMR diversity |
| **Local Embeddings** | Runs `all-MiniLM-L6-v2` via ONNX Runtime entirely on-device. No network needed for inference. |
| **Letterboxd Import** | One-tap CSV import. Matches your rated movies against TMDb automatically. |
| **Discover & Search** | Search TMDb's catalog directly, browse by genre, and manually add movies to your taste profile. |
| **In-App Rating** | Rate any movie inline. Your rating immediately feeds back into the recommendation engine. |
| **Match Score** | Every recommendation shows a "Match %" badge — transparent, no black box. |
| **Match Explanations** | Tells you *why* a movie was recommended (e.g. "Because you liked The Matrix and enjoy Sci-Fi"). |
| **Beautiful UI** | Jetpack Compose with a dark cinematic theme, animated backgrounds, shimmer loading, and smooth transitions. |
| **Offline First** | All data (movies, embeddings, ratings) lives in a local Room database. |

---

## Screenshots

*Screenshots will be added here.*

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────────┐ │
│  │  Onboarding │  │ HomeScreen │  │ MovieDetailScreen      │ │
│  │  (Compose)  │  │ (Compose)  │  │ (Compose)              │ │
│  └────────────┘  └─────┬──────┘  └────────────────────────┘ │
│                        │                                     │
│              ┌─────────┴──────────┐                          │
│              │   MainViewModel    │                          │
│              │  (StateFlow + MVVM)│                          │
│              └─────────┬──────────┘                          │
└────────────────────────┼────────────────────────────────────┘
                         │
┌────────────────────────┼────────────────────────────────────┐
│              Domain Layer                                   │
│  ┌─────────────────────┴────────────────────────────────┐   │
│  │              RecommendationEngine                      │   │
│  │  • CandidateScorer (vector similarity)                │   │
│  │  • GenreAffinity (genre preference match)             │   │
│  │  • MMRSelector (diversity re-ranking)                 │   │
│  │  • Explanations (natural language why)                │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              EmbeddingEngine (ONNX)                   │   │
│  │  • all-MiniLM-L6-v2 on-device                         │   │
│  │  • Cosine similarity search                           │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                         │
┌────────────────────────┼────────────────────────────────────┐
│              Data Layer                                     │
│  ┌──────────────┐  ┌─────────────────┐  ┌───────────────┐   │
│  │  MovieDao    │  │ MovieRepository │  │  TmdbApi      │   │
│  │  (Room)      │  │                 │  │  (Retrofit)   │   │
│  └──────────────┘  └─────────────────┘  └───────────────┘   │
│  ┌────────────────────────────────────────────────────────┐   │
│  │              LetterboxdCsvParser                        │   │
│  │  • CSV column auto-detection                            │   │
│  │  • Title/year fuzzy matching to TMDb                    │   │
│  └────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Edge Proxy Architecture

For a seamless "no API key" experience, this beta points at a deployed **Cloudflare Worker** proxy. The Worker source is included under `proxy/` so you can deploy your own if needed:

```
┌─────────────────────────────────────────────────────────────────┐
│                         Android App                            │
│              (no TMDb API key in APK → secure)                  │
└─────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│              Cloudflare Worker (edge-deployed)                 │
│  ┌───────────┐  ┌───────────┐  ┌────────────────┐   │
│  │ KV Cache    │  │ KV Rate   │  │ Secret Key     │   │
│  │ (6h TTL)    │  │ Limit     │  │ (injected)     │   │
│  └───────────┘  └───────────┘  └────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                        TMDb API                                 │
└─────────────────────────────────────────────────────────────────┘
```

- **Security**: API key never touches the APK — impossible to extract via reverse engineering.
- **Performance**: KV caching at 300+ Cloudflare edge locations means popular queries (trending, movie details) return in <50ms without hitting TMDb directly.
- **Resilience**: Sliding-window rate limiting (35 req/10s per IP) buffers you from TMDb's 40 req/10s global limit.
- **Cost**: Runs entirely on Cloudflare Workers free tier (100K req/day).

The current Android build uses `https://cinephile-proxy.thalos-cinephile.workers.dev/3/`. See [`proxy/README.md`](proxy/README.md) if you want to deploy and point the app at your own Worker.

---

## Tech Stack

| Layer | Tech |
|-------|------|
| **UI** | Jetpack Compose, Material 3, Coil |
| **Architecture** | MVVM, ViewModel, StateFlow |
| **Local DB** | Room (SQLite) |
| **Prefs** | DataStore |
| **Networking** | Retrofit + OkHttp + Gson |
| **Edge Proxy** | Cloudflare Worker (caching, rate limiting, key hiding) |
| **ML Inference** | ONNX Runtime Android (all-MiniLM-L6-v2) |
| **DI** | Manual (Application-level singletons) |
| **Build** | Gradle with KSP |

---

## Setup

### Prerequisites

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- No personal TMDb key is required for the public beta build; the app uses the included Cloudflare Worker proxy URL

### Build

```bash
git clone <repo>
cd cinephile

# The ONNX model asset is included in this repository.
# If you publish to GitHub, note that app/src/main/assets/model.onnx is ~86 MB.

./gradlew :app:assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## How Recommendations Work

1. **Candidate Pool**: Fetches ~200 popular/highly-rated movies from TMDb (configurable).
2. **Vector Similarity**: Embeds each candidate's plot + genres using the local ONNX model. Compares cosine similarity to your rated movies' embeddings.
3. **Genre Affinity**: Boosts candidates in genres you rate highly.
4. **Quality Filter**: Drops candidates below 6.5 TMDb rating unless they match your taste very strongly.
5. **MMR Diversity**: Re-ranks the top results to balance relevance vs. diversity (so you don't get 15 identical action movies).
6. **Explanations**: Generates a human-readable reason for each pick based on the strongest matching signals.

---

## Project Structure

```
app/src/main/java/com/thalos/cinephile/
├── MainActivity.kt              # Entry point, navigation host
├── MainViewModel.kt             # UI state + business logic
├── CinephileApp.kt              # Application, DI container
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt       # Room database
│   │   ├── MovieDao.kt          # DB access
│   │   └── MovieEntity.kt       # Data model
│   ├── remote/
│   │   ├── TmdbApi.kt           # Retrofit interface
│   │   ├── TmdbModels.kt        # DTOs
│   │   └── GenreMap.kt          # Genre ID <-> name
│   └── repository/
│       ├── MovieRepository.kt   # Data layer facade
│       └── LetterboxdCsvParser.kt
├── domain/
│   └── engine/
│       ├── EmbeddingEngine.kt   # ONNX model wrapper
│       └── RecommendationEngine.kt
└── ui/
    ├── components/              # Reusable UI pieces
    │   ├── MovieCard.kt
    │   ├── MoviePoster.kt
    │   ├── RatingDialog.kt
    │   └── AnimatedBackground.kt
    ├── screens/
    │   ├── OnboardingScreen.kt
    │   ├── HomeScreen.kt
    │   ├── DiscoverScreen.kt
    │   ├── ProfileScreen.kt
    │   └── MovieDetailScreen.kt
    └── theme/                   # Cinematic color palette
        ├── Color.kt
        ├── Theme.kt
        └── Typography.kt
```

---

## Roadmap

- [x] Hybrid recommendation engine
- [x] Local ONNX embeddings
- [x] Letterboxd CSV import
- [x] Discover / search tab
- [x] Profile with stats
- [x] In-app rating
- [x] Match explanations
- [x] Swipe-to-dismiss recommendations
- [x] Watchlist
- [x] Cloudflare Worker proxy with KV caching
- [ ] Release notifications
- [ ] Export recommendations to list
- [ ] Additional importers (IMDb, Trakt)

---

## License

MIT
