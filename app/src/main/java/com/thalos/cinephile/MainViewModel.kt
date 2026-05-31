package com.thalos.cinephile

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.data.local.DismissedMovieEntity
import com.thalos.cinephile.data.repository.LetterboxdCsvParser
import com.thalos.cinephile.data.repository.MovieRepository
import com.thalos.cinephile.data.local.UserProfileRepository
import com.thalos.cinephile.data.remote.GenreMap
import com.thalos.cinephile.domain.engine.EmbeddingEngine
import com.thalos.cinephile.domain.engine.RecommendationEngine
import com.thalos.cinephile.domain.engine.SearchQueryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thalos.cinephile.ui.screens.isInTheaters

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CinephileApp
    private val movieRepository = app.movieRepository
    private val dismissedDao = app.database.dismissedMovieDao()
    private val userRepo = app.userProfileRepository
    private val embeddingEngine = app.embeddingEngine
    private val recommendationEngine = app.recommendationEngine

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _recommendations = MutableStateFlow<List<RecommendationEngine.ScoredMovie>>(emptyList())
    val recommendations: StateFlow<List<RecommendationEngine.ScoredMovie>> = _recommendations.asStateFlow()

    // Deep pool of scored candidates. Visible list is a filtered/sliced view.
    private var recommendationPool: List<RecommendationEngine.ScoredMovie> = emptyList()

    private val _trendingMovies = MutableStateFlow<List<MovieEntity>>(emptyList())
    val trendingMovies: StateFlow<List<MovieEntity>> = _trendingMovies.asStateFlow()

    private val _upcomingMovies = MutableStateFlow<List<MovieEntity>>(emptyList())
    val upcomingMovies: StateFlow<List<MovieEntity>> = _upcomingMovies.asStateFlow()

    private val _discoverMovies = MutableStateFlow<List<MovieEntity>>(emptyList())
    val discoverMovies: StateFlow<List<MovieEntity>> = _discoverMovies.asStateFlow()

    private val _similarMovies = MutableStateFlow<List<MovieEntity>>(emptyList())
    val similarMovies: StateFlow<List<MovieEntity>> = _similarMovies.asStateFlow()

    private val _movieVideos = MutableStateFlow<List<com.thalos.cinephile.data.remote.TmdbVideo>>(emptyList())
    val movieVideos: StateFlow<List<com.thalos.cinephile.data.remote.TmdbVideo>> = _movieVideos.asStateFlow()

    private val _trailerKey = MutableStateFlow<String?>(null)
    val trailerKey: StateFlow<String?> = _trailerKey.asStateFlow()

    private val _watchProviders = MutableStateFlow<List<String>>(emptyList())
    val watchProviders: StateFlow<List<String>> = _watchProviders.asStateFlow()

    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    private val _isLoadingRecs = MutableStateFlow(false)
    val isLoadingRecs: StateFlow<Boolean> = _isLoadingRecs.asStateFlow()

    private val _dismissedMovieIds = MutableStateFlow<Set<Int>>(emptySet())
    val dismissedMovieIds: StateFlow<Set<Int>> = _dismissedMovieIds.asStateFlow()

    private val _watchlistIds = MutableStateFlow<Set<Int>>(emptySet())
    val watchlistIds: StateFlow<Set<Int>> = _watchlistIds.asStateFlow()

    private val _hideTheaterOnly = MutableStateFlow(false)
    val hideTheaterOnly: StateFlow<Boolean> = _hideTheaterOnly.asStateFlow()

    private val _hideObscure = MutableStateFlow(false)
    val hideObscure: StateFlow<Boolean> = _hideObscure.asStateFlow()

    // Profile tab filter states (persist across tab switches)
    private val _profileSelectedSection = MutableStateFlow(0) // 0 = ratings, 1 = watchlist
    val profileSelectedSection: StateFlow<Int> = _profileSelectedSection.asStateFlow()

    private val _profileSelectedRating = MutableStateFlow<Int?>(null)
    val profileSelectedRating: StateFlow<Int?> = _profileSelectedRating.asStateFlow()

    val userMovies = movieRepository.getUserMovies()
    val watchlist = movieRepository.getWatchlist()
    val userName = userRepo.userName
    val countryCode = userRepo.countryCode

    init {
        viewModelScope.launch {
            val onboardingDone = userRepo.onboardingComplete.first()
            _hideTheaterOnly.value = userRepo.hideTheaterOnly.first()
            _hideObscure.value = userRepo.hideObscure.first()
            _dismissedMovieIds.value = dismissedDao.getDismissedTmdbIdsOnce().toSet()
            _watchlistIds.value = movieRepository.getWatchlistOnce().map { it.tmdbId }.toSet()
            if (onboardingDone) {
                _uiState.value = UiState.Ready
                loadRecommendations()
                fetchTrending()
                fetchUpcoming()
            } else {
                _uiState.value = UiState.Onboarding
            }
            // Keep watchlist IDs updated
            launch {
                movieRepository.getWatchlist().collect { list ->
                    _watchlistIds.value = list.map { it.tmdbId }.toSet()
                }
            }
        }
    }

    fun completeOnboarding(apiKey: String, userName: String) {
        viewModelScope.launch {
            userRepo.setApiKey(apiKey)
            userRepo.setUserName(userName)
            userRepo.setOnboardingComplete()
            _uiState.value = UiState.Ready
            fetchTrending()
            fetchUpcoming()
            // Don't auto-load recs here; user may import or browse first
        }
    }

    fun importLetterboxdCsv(uri: Uri, context: Context, providedApiKey: String? = null) {
        viewModelScope.launch {
            _importProgress.value = ImportProgress(isImporting = true, message = "Parsing CSV...")
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _importProgress.value = ImportProgress(
                        isImporting = false,
                        message = "Error: Could not open file.",
                        progress = 0f
                    )
                    Toast.makeText(context, "Could not open file stream", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val entries = LetterboxdCsvParser().parse(inputStream)
                if (entries.isEmpty()) {
                    _importProgress.value = ImportProgress(
                        isImporting = false,
                        message = "Error: No valid entries found.",
                        progress = 0f
                    )
                    Toast.makeText(context, "CSV parsed but found 0 entries", Toast.LENGTH_LONG).show()
                    return@launch
                }
                _importProgress.value = ImportProgress(isImporting = true, message = "Found ${entries.size} movies. Matching with TMDb...")

                val apiKey = providedApiKey ?: userRepo.apiKey.first()
                if (apiKey.isNullOrBlank()) {
                    _importProgress.value = ImportProgress(
                        isImporting = false,
                        message = "Error: No API key found.",
                        progress = 0f
                    )
                    Toast.makeText(context, "Missing TMDb API key", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Process in batches of 5 with concurrency
                val batchSize = 5
                val batches = entries.chunked(batchSize)
                var matched = 0
                var failed = 0
                val importedMovies = mutableListOf<MovieEntity>()

                batches.forEachIndexed { batchIndex, batch ->
                    _importProgress.value = ImportProgress(
                        isImporting = true,
                        message = "Matching batch ${batchIndex + 1}/${batches.size}...",
                        progress = ((batchIndex + 1).toFloat() / batches.size).coerceIn(0f, 0.95f)
                    )

                    // Parallel TMDb lookups within batch
                    val details = batch.map { entry ->
                        async(Dispatchers.IO) {
                            try {
                                movieRepository.searchAndFetchDetails(apiKey, entry.title) to entry
                            } catch (_: Exception) {
                                null to entry
                            }
                        }
                    }.awaitAll()

                    details.forEach { pair ->
                        val detail = pair.first
                        val entry = pair.second
                        if (detail != null) {
                            val enriched = movieRepository.enrichMovieWithDetails(
                                MovieEntity(
                                    tmdbId = detail.id,
                                    title = detail.title,
                                    originalTitle = detail.originalTitle,
                                    overview = detail.overview,
                                    posterPath = detail.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                                    backdropPath = detail.backdropPath?.let { "https://image.tmdb.org/t/p/original$it" },
                                    releaseDate = detail.releaseDate,
                                    genres = detail.genres.map { it.id }.joinToString(","),
                                    runtime = detail.runtime,
                                    voteAverage = detail.voteAverage,
                                    voteCount = detail.voteCount,
                                    popularity = detail.popularity,
                                    isFromUser = true,
                                    userRating = entry.rating?.times(2)?.coerceIn(1.0, 10.0),
                                    rewatch = entry.rewatch,
                                    watchedDate = entry.watchedDate
                                ),
                                apiKey
                            )
                            importedMovies.add(enriched)
                            matched++
                        } else {
                            failed++
                        }
                    }
                }

                if (matched == 0) {
                    _importProgress.value = ImportProgress(
                        isImporting = false,
                        message = "Error: 0 movies matched. Check API key and network.",
                        progress = 0f
                    )
                    Toast.makeText(context, "0 matched — bad API key or no network?", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Batch embed + insert (much faster)
                _importProgress.value = ImportProgress(isImporting = true, message = "Embedding $matched movies...", progress = 0.95f)
                importedMovies.forEach { movie ->
                    val text = "${movie.title}. ${movie.overview ?: ""} ${movie.keywords} ${movie.genres}"
                    val embedding = embeddingEngine.embed(text)
                    val withEmbedding = movie.copy(embedding = embeddingEngine.serializeEmbedding(embedding))
                    movieRepository.insert(withEmbedding)
                }

                // Don't auto-fetch candidates or generate recs here — do it lazily on For You tab
                _importProgress.value = ImportProgress(
                    isImporting = false,
                    message = "Matched $matched/${entries.size} movies! (failed: $failed)",
                    progress = 1f
                )
            } catch (e: Exception) {
                _importProgress.value = ImportProgress(
                    isImporting = false,
                    message = "Error: ${e.message}",
                    progress = 0f
                )
                Toast.makeText(context, "Import error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun fetchCandidates(apiKey: String) {
        viewModelScope.launch {
            _importProgress.value = ImportProgress(isImporting = true, message = "Fetching candidate movies...")
            try {
                movieRepository.fetchPopularMovies(apiKey, pages = 2)
                movieRepository.fetchTopRatedMovies(apiKey, pages = 1)
                ensureCandidates(apiKey)
                _importProgress.value = ImportProgress(isImporting = false, message = "Recommendations ready!")
                loadRecommendations()
            } catch (e: Exception) {
                _importProgress.value = ImportProgress(isImporting = false, message = "Rate limit? Try pull-to-refresh soon.")
            }
        }
    }

    fun fetchTrending() {
        viewModelScope.launch {
            try {
                val apiKey = userRepo.apiKey.first() ?: return@launch
                movieRepository.fetchTrendingMovies(apiKey)
                val trending = withContext(Dispatchers.IO) {
                    movieRepository.discoverMovies(apiKey, sortBy = "popularity.desc", page = 1)
                }
                _trendingMovies.value = trending.take(15)
            } catch (_: Exception) { }
        }
    }

    fun fetchUpcoming() {
        viewModelScope.launch {
            try {
                val apiKey = userRepo.apiKey.first() ?: return@launch
                movieRepository.fetchUpcomingMovies(apiKey)
                val upcoming = withContext(Dispatchers.IO) {
                    movieRepository.discoverMovies(apiKey, sortBy = "primary_release_date.desc", page = 1)
                }
                _upcomingMovies.value = upcoming.take(15)
            } catch (_: Exception) { }
        }
    }

    fun fetchDiscoverMovies(
        genreId: Int? = null,
        sortBy: String = "popularity",
        yearFrom: Int? = null,
        yearTo: Int? = null,
        maxRuntime: Int? = null
    ) {
        viewModelScope.launch {
            try {
                val apiKey = userRepo.apiKey.first() ?: return@launch
                val apiSort = when (sortBy) {
                    "rating" -> "vote_average.desc"
                    "year" -> "primary_release_date.desc"
                    "runtime" -> "runtime.desc"
                    else -> "popularity.desc"
                }
                val yearGte = yearFrom?.let { "$it-01-01" }
                val yearLte = yearTo?.let { "$it-12-31" }
                val movies = withContext(Dispatchers.IO) {
                    movieRepository.discoverMovies(
                        apiKey,
                        sortBy = apiSort,
                        page = 1,
                        withGenres = genreId,
                        yearGte = yearGte,
                        yearLte = yearLte,
                        maxRuntime = maxRuntime,
                        minVotes = 20
                    )
                }
                _discoverMovies.value = movies
            } catch (_: Exception) {
                _discoverMovies.value = emptyList()
            }
        }
    }

    private suspend fun ensureCandidates(apiKey: String) {
        val candidateCount = withContext(Dispatchers.IO) {
            movieRepository.getCandidateMovies().size
        }
        // Aggressively fetch candidates until we have a deep pool
        if (candidateCount < 500) {
            try {
                movieRepository.fetchPopularMovies(apiKey, pages = 5)
                movieRepository.fetchTopRatedMovies(apiKey, pages = 3)
                movieRepository.fetchTrendingMovies(apiKey, pages = 2)
                movieRepository.fetchUpcomingMovies(apiKey, pages = 2)
            } catch (_: Exception) { }
        }

        // Embed candidates missing embeddings (max 100 to avoid overload)
        val needEmbedding = withContext(Dispatchers.IO) {
            movieRepository.getCandidatesWithoutEmbeddings()
        }.take(100)

        if (needEmbedding.isNotEmpty()) {
            val batchSize = 5
            val batches = needEmbedding.chunked(batchSize)
            batches.forEach { batch ->
                val texts = batch.map {
                    "${it.title}. ${it.overview ?: ""} ${it.keywords} ${it.genres}".take(512)
                }
                val embeddings = embeddingEngine.embedBatch(texts)
                batch.forEachIndexed { index, movie ->
                    val serialized = embeddingEngine.serializeEmbedding(embeddings[index])
                    withContext(Dispatchers.IO) {
                        movieRepository.updateEmbedding(movie.tmdbId, serialized)
                    }
                }
            }
        }
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _isLoadingRecs.value = true
            try {
                val apiKey = userRepo.apiKey.first()
                if (!apiKey.isNullOrBlank()) {
                    ensureCandidates(apiKey)
                }
                val dismissed = _dismissedMovieIds.value
                val watchlisted = _watchlistIds.value
                val hideTheater = _hideTheaterOnly.value
                val hideObscure = _hideObscure.value
                val pool = recommendationEngine.generateRecommendations(500, dismissed, watchlisted)
                    .filter {
                        val theaterFilter = !(hideTheater && isInTheaters(it.movie.releaseDate) && it.movie.watchProviders.isEmpty())
                        val obscureFilter = !hideObscure || (it.movie.voteCount ?: 0) >= 500
                        theaterFilter && obscureFilter
                    }
                recommendationPool = pool
                var visible = pool.filter { it.movie.tmdbId !in dismissed && it.movie.tmdbId !in watchlisted }.take(15)
                // Fallback: pad with random popular candidates if scored pool is shallow
                if (visible.size < 15) {
                    val allCandidates = withContext(Dispatchers.IO) {
                        movieRepository.getAllMovies().filter { it.isFromUser == false && it.tmdbId !in dismissed && it.tmdbId !in watchlisted && (!hideObscure || (it.voteCount ?: 0) >= 500) }
                    }
                    val existingIds = visible.map { it.movie.tmdbId }.toSet()
                    val extras = allCandidates
                        .filter { it.tmdbId !in existingIds }
                        .shuffled()
                        .take(15 - visible.size)
                        .map { candidate ->
                            RecommendationEngine.ScoredMovie(
                                movie = candidate,
                                semanticScore = 0.0,
                                genreScore = 0.0,
                                castScore = 0.0,
                                eraScore = 0.0,
                                runtimeScore = 0.0,
                                ratingBoost = (candidate.voteAverage / 10.0).coerceIn(0.0, 1.0),
                                totalScore = 0.5,
                                explanation = "Popular discovery"
                            )
                        }
                    visible = visible + extras
                }
                _recommendations.value = visible.take(15)
            } catch (_: Exception) {
                _recommendations.value = emptyList()
            }
            _isLoadingRecs.value = false
        }
    }

    fun shuffleRecommendations() {
        viewModelScope.launch {
            _isLoadingRecs.value = true
            val dismissed = _dismissedMovieIds.value
            val watchlisted = _watchlistIds.value
            val hideTheater = _hideTheaterOnly.value
            val hideObscure = _hideObscure.value
            // Build a fresh pool on shuffle
            val pool = recommendationEngine.generateRecommendations(500, dismissed, watchlisted)
                .filter {
                    val theaterFilter = !(hideTheater && isInTheaters(it.movie.releaseDate) && it.movie.watchProviders.isEmpty())
                    val obscureFilter = !hideObscure || (it.movie.voteCount ?: 0) >= 500
                    theaterFilter && obscureFilter
                }
            recommendationPool = pool
            _recommendations.value = pool
                .filter { it.movie.tmdbId !in dismissed && it.movie.tmdbId !in watchlisted }
                .shuffled()
                .take(15)
            _isLoadingRecs.value = false
        }
    }

    fun dismissRecommendation(tmdbId: Int) {
        _dismissedMovieIds.value += tmdbId
        viewModelScope.launch {
            dismissedDao.insertDismissed(DismissedMovieEntity(tmdbId = tmdbId))
            val dismissed = _dismissedMovieIds.value
            val watchlisted = _watchlistIds.value
            val visible = _recommendations.value.filter { it.movie.tmdbId !in dismissed && it.movie.tmdbId !in watchlisted }
            // Backfill from pool if we dropped below 15
            var updated = visible
            if (updated.size < 15 && recommendationPool.isNotEmpty()) {
                val poolExtras = recommendationPool
                    .filter { it.movie.tmdbId !in dismissed && it.movie.tmdbId !in watchlisted && updated.none { v -> v.movie.tmdbId == it.movie.tmdbId } }
                    .take(15 - updated.size)
                updated = updated + poolExtras
            }
            // Ultimate fallback: pad with random popular candidates
            if (updated.size < 15) {
                val hideObscure = _hideObscure.value
                val allCandidates = withContext(Dispatchers.IO) {
                    movieRepository.getAllMovies().filter { it.isFromUser == false && it.tmdbId !in dismissed && it.tmdbId !in watchlisted && (!hideObscure || (it.voteCount ?: 0) >= 500) }
                }
                val existingIds = updated.map { it.movie.tmdbId }.toSet()
                val extras = allCandidates
                    .filter { it.tmdbId !in existingIds }
                    .shuffled()
                    .take(15 - updated.size)
                    .map { candidate ->
                        RecommendationEngine.ScoredMovie(
                            movie = candidate,
                            semanticScore = 0.0,
                            genreScore = 0.0,
                            castScore = 0.0,
                            eraScore = 0.0,
                            runtimeScore = 0.0,
                            ratingBoost = (candidate.voteAverage / 10.0).coerceIn(0.0, 1.0),
                            totalScore = 0.5,
                            explanation = "Popular discovery"
                        )
                    }
                updated = updated + extras
            }
            _recommendations.value = updated.take(15)
            // If pool is getting thin, regenerate in background
            val remainingInPool = recommendationPool.count { it.movie.tmdbId !in dismissed && it.movie.tmdbId !in watchlisted }
            if (remainingInPool < 30) {
                loadRecommendations()
            }
        }
    }

    fun undoDismiss(tmdbId: Int) {
        _dismissedMovieIds.value -= tmdbId
        viewModelScope.launch {
            dismissedDao.removeDismissed(tmdbId)
            val dismissed = _dismissedMovieIds.value
            val watchlisted = _watchlistIds.value
            val current = _recommendations.value
            // Re-insert the undone movie into visible list if it was in pool and not watchlisted
            val undoneMovie = recommendationPool.find { it.movie.tmdbId == tmdbId }
            if (undoneMovie != null && current.none { it.movie.tmdbId == tmdbId } && tmdbId !in watchlisted) {
                _recommendations.value = (current + undoneMovie).sortedByDescending { it.totalScore }.take(15)
            } else {
                loadRecommendations()
            }
        }
    }

    fun fetchSimilarMovies(tmdbId: Int) {
        viewModelScope.launch {
            try {
                val apiKey = userRepo.apiKey.first() ?: return@launch
                val similar = movieRepository.getSimilarMovies(tmdbId, apiKey)
                _similarMovies.value = similar
            } catch (_: Exception) {
                _similarMovies.value = emptyList()
            }
        }
    }

    fun fetchMovieVideos(tmdbId: Int) {
        viewModelScope.launch {
            try {
                val apiKey = userRepo.apiKey.first() ?: return@launch
                val videos = movieRepository.getMovieVideos(tmdbId, apiKey)
                _movieVideos.value = videos
                val trailer = videos.find { it.type == "Trailer" && it.site == "YouTube" }
                    ?: videos.find { it.site == "YouTube" }
                _trailerKey.value = trailer?.key
            } catch (_: Exception) {
                _movieVideos.value = emptyList()
                _trailerKey.value = null
            }
        }
    }

    fun fetchTrailer(tmdbId: Int) = fetchMovieVideos(tmdbId)

    fun exportData(movies: List<MovieEntity>): String {
        return buildString {
            appendLine("Title,Year,Rating,Runtime,Genres,Director,Rewatch,WatchedDate")
            movies.forEach { m ->
                appendLine("\"${m.title}\",${m.releaseDate?.take(4) ?: ""},${m.userRating ?: ""},${m.runtime ?: ""},\"${m.genres}\",\"${m.director ?: ""}\",${m.rewatch},${m.watchedDate ?: ""}")
            }
        }
    }

    fun fetchWatchProviders(tmdbId: Int) {
        viewModelScope.launch {
            try {
                val apiKey = userRepo.apiKey.first() ?: return@launch
                val country = userRepo.countryCode.first()
                val providers = movieRepository.fetchWatchProviders(tmdbId, apiKey, country)
                _watchProviders.value = providers
            } catch (_: Exception) {
                _watchProviders.value = emptyList()
            }
        }
    }

    fun clearSimilarAndVideos() {
        _similarMovies.value = emptyList()
        _movieVideos.value = emptyList()
    }

    suspend fun smartSearch(query: String): Pair<List<MovieEntity>, String> {
        return withContext(Dispatchers.IO) {
            val apiKey = userRepo.apiKey.first()
            val filters = SearchQueryParser.parse(query)

            if (!apiKey.isNullOrBlank() && SearchQueryParser.hasMeaningfulFilters(filters)) {
                // Use TMDb discover with parsed filters
                try {
                    // Format years as full dates for TMDb API (expects YYYY-MM-DD)
                    val releaseDateGte = filters.yearGte?.let { "$it-01-01" }
                    val releaseDateLte = filters.yearLte?.let { "$it-12-31" }
                    // Lower minVotes for language-specific searches to get more results
                    val minVotes = if (filters.language != null) 1 else 20
                    // Fetch up to 3 pages for more results
                    val allMovies = mutableListOf<MovieEntity>()
                    for (page in 1..3) {
                        val pageResults = movieRepository.discoverMovies(
                            apiKey = apiKey,
                            sortBy = filters.sortBy,
                            page = page,
                            minRating = filters.minRating,
                            yearGte = releaseDateGte,
                            yearLte = releaseDateLte,
                            minRuntime = filters.minRuntime,
                            maxRuntime = filters.maxRuntime,
                            withGenres = filters.withGenres,
                            withOriginalLanguage = filters.language,
                            minVotes = minVotes
                        )
                        if (pageResults.isEmpty()) break
                        allMovies.addAll(pageResults)
                    }
                    // Post-filter by keywords locally (TMDb discover doesn't support keyword search well)
                    val filtered = if (filters.keywords.isNotEmpty()) {
                        allMovies.filter { movie ->
                            val text = "${movie.title} ${movie.overview ?: ""} ${movie.keywords} ${movie.director ?: ""}".lowercase()
                            filters.keywords.any { kw ->
                                if (kw.startsWith("director:")) {
                                    val dir = kw.removePrefix("director:").lowercase()
                                    movie.director?.lowercase()?.contains(dir) == true
                                } else {
                                    text.contains(kw)
                                }
                            }
                        }
                    } else allMovies

                    val explanation = buildExplanation(filters)
                    Pair(filtered, explanation)
                } catch (_: Exception) {
                    // Fallback to title search
                    val results = searchMovies(query)
                    Pair(results, "")
                }
            } else {
                // No meaningful filters parsed — try local semantic search first, then title search
                val candidates = movieRepository.getAllMovies()
                if (candidates.isNotEmpty() && filters.queryText.isNotBlank()) {
                    val queryEmbedding = embeddingEngine.embed(filters.queryText)
                    val scored = candidates.map { candidate ->
                        val candidateEmbedding = if (candidate.embedding.isNotBlank()) {
                            embeddingEngine.parseEmbedding(candidate.embedding)
                        } else FloatArray(384) { 0f }
                        val score = embeddingEngine.cosineSimilarity(queryEmbedding, candidateEmbedding)
                        candidate to score
                    }.filter { it.second > 0.3f }
                        .sortedByDescending { it.second }
                        .take(30)
                        .map { it.first }

                    if (scored.isNotEmpty()) {
                        Pair(scored, "Semantic matches for \"${filters.queryText}\"")
                    } else {
                        Pair(searchMovies(query), "")
                    }
                } else {
                    Pair(searchMovies(query), "")
                }
            }
        }
    }

    private fun buildExplanation(filters: SearchQueryParser.SearchFilters): String {
        val parts = mutableListOf<String>()
        filters.withGenres?.let { parts.add(GenreMap.name(it)) }
        filters.language?.let { code ->
            val display = when (code) {
                "ko" -> "Korean"
                "ja" -> "Japanese"
                "zh" -> "Chinese"
                "fr" -> "French"
                "de" -> "German"
                "es" -> "Spanish"
                "it" -> "Italian"
                "hi" -> "Hindi"
                "en" -> "English"
                "ru" -> "Russian"
                "pt" -> "Portuguese"
                else -> code.uppercase()
            }
            parts.add(display)
        }
        filters.yearGte?.let { gte ->
            filters.yearLte?.let { lte ->
                if (gte == lte) parts.add("from $gte")
                else parts.add("$gte–$lte")
            } ?: parts.add("from $gte")
        }
        filters.minRating?.let { parts.add("rating ≥ $it") }
        filters.maxRuntime?.let { parts.add("≤ ${it}min") }
        filters.minRuntime?.let { parts.add("≥ ${it}min") }
        if (parts.isEmpty()) return "Search results"
        return parts.joinToString(" · ")
    }

    suspend fun searchMovies(query: String): List<MovieEntity> {
        return withContext(Dispatchers.IO) {
            val apiKey = userRepo.apiKey.first()
            if (!apiKey.isNullOrBlank()) {
                movieRepository.searchTmdb(apiKey, query)
            } else {
                movieRepository.searchMovies(query)
            }
        }
    }

    fun markWatched(movie: MovieEntity) {
        viewModelScope.launch {
            movieRepository.markWatched(movie)
            val updated = movieRepository.getMovieByTmdbId(movie.tmdbId)
            if (updated != null && updated.embedding.isBlank()) {
                val text = "${updated.title}. ${updated.overview ?: ""} ${updated.keywords} ${updated.genres}".take(512)
                val embedding = embeddingEngine.embed(text)
                val serialized = embeddingEngine.serializeEmbedding(embedding)
                movieRepository.updateEmbedding(updated.tmdbId, serialized)
            }
            loadRecommendations()
        }
    }

    fun rateMovie(movie: MovieEntity, rating: Double) {
        viewModelScope.launch {
            movieRepository.rateMovie(movie, rating)
            val updated = movieRepository.getMovieByTmdbId(movie.tmdbId)
            if (updated != null && updated.embedding.isBlank()) {
                val text = "${updated.title}. ${updated.overview ?: ""} ${updated.keywords} ${updated.genres}".take(512)
                val embedding = embeddingEngine.embed(text)
                val serialized = embeddingEngine.serializeEmbedding(embedding)
                movieRepository.updateEmbedding(updated.tmdbId, serialized)
            }
            loadRecommendations()
        }
    }

    fun toggleWatchlist(movie: MovieEntity) {
        viewModelScope.launch {
            movieRepository.toggleWatchlist(movie)
        }
    }

    fun setCountryCode(code: String) {
        viewModelScope.launch {
            userRepo.setCountryCode(code)
        }
    }


    fun refreshAllWatchProviders() {
        viewModelScope.launch {
            _importProgress.value = ImportProgress(isImporting = true, message = "Loading movie list...", progress = 0f)
            try {
                val apiKey = userRepo.apiKey.first()
                if (apiKey.isNullOrBlank()) {
                    _importProgress.value = ImportProgress(isImporting = false, message = "Error: No API key.", progress = 0f)
                    return@launch
                }
                val country = userRepo.countryCode.first()
                val allMovies = withContext(Dispatchers.IO) {
                    movieRepository.getAllMovies()
                }
                if (allMovies.isEmpty()) {
                    _importProgress.value = ImportProgress(isImporting = false, message = "No cached movies to refresh.", progress = 0f)
                    return@launch
                }

                val batchSize = 5
                val batches = allMovies.chunked(batchSize)
                var succeeded = 0
                var failed = 0

                batches.forEachIndexed { index, batch ->
                    val start = index * batchSize + 1
                    val end = (index + 1) * batchSize.coerceAtMost(allMovies.size)
                    _importProgress.value = ImportProgress(
                        isImporting = true,
                        message = "Refreshing $start-$end of ${allMovies.size}...",
                        progress = (index.toFloat() / batches.size).coerceIn(0f, 0.95f)
                    )

                    val results = batch.map { movie ->
                        async(Dispatchers.IO) {
                            try {
                                movieRepository.fetchWatchProviders(movie.tmdbId, apiKey, country)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }
                    }.awaitAll()

                    succeeded += results.count { it }
                    failed += results.count { !it }

                    // Throttle to respect TMDb rate limits (40 req / 10s)
                    if (index < batches.size - 1) {
                        kotlinx.coroutines.delay(600L)
                    }
                }

                _importProgress.value = ImportProgress(
                    isImporting = false,
                    message = "Done! Updated $succeeded/${allMovies.size} movies.",
                    progress = 1f
                )
            } catch (e: Exception) {
                _importProgress.value = ImportProgress(isImporting = false, message = "Error: ${e.message}", progress = 0f)
            }
        }
    }

    fun clearAllDismissed() {
        viewModelScope.launch {
            dismissedDao.clearAllDismissed()
            _dismissedMovieIds.value = emptySet()
            loadRecommendations()
        }
    }

    fun toggleHideTheaterOnly() {
        viewModelScope.launch {
            val newValue = !_hideTheaterOnly.value
            _hideTheaterOnly.value = newValue
            userRepo.setHideTheaterOnly(newValue)
            loadRecommendations()
        }
    }

    fun toggleHideObscure() {
        viewModelScope.launch {
            val newValue = !_hideObscure.value
            _hideObscure.value = newValue
            userRepo.setHideObscure(newValue)
            loadRecommendations()
        }
    }

    fun setProfileSection(section: Int) {
        _profileSelectedSection.value = section
    }

    fun setProfileRatingFilter(rating: Int?) {
        _profileSelectedRating.value = rating
    }

    fun resetData() {
        viewModelScope.launch {
            app.database.movieDao().clearUserMovies()
            app.database.movieDao().clearCandidateMovies()
            userRepo.clearAll()
            _uiState.value = UiState.Onboarding
            _recommendations.value = emptyList()
            _trendingMovies.value = emptyList()
            _upcomingMovies.value = emptyList()
            dismissedDao.clearAllDismissed()
            _dismissedMovieIds.value = emptySet()
        }
    }

    sealed class UiState {
        object Loading : UiState()
        object Onboarding : UiState()
        object Ready : UiState()
    }

    data class ImportProgress(
        val isImporting: Boolean = false,
        val message: String = "",
        val progress: Float = 0f
    )
}
