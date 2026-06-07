package com.thalos.cinephile.data.repository

import com.thalos.cinephile.data.local.MovieDao
import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.data.remote.TmdbApi
import com.thalos.cinephile.data.remote.TmdbService
import com.thalos.cinephile.data.remote.toTmdbImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MovieRepository(private val movieDao: MovieDao) {
    private val api: TmdbService = TmdbApi.create()

    suspend fun fetchTrendingMovies(apiKey: String, pages: Int = 2) {
        withContext(Dispatchers.IO) {
            for (page in 1..pages) {
                try {
                    val result = api.getTrendingMovies(apiKey, page)
                    val movies = result.results.map { movie ->
                        MovieEntity(
                            tmdbId = movie.id,
                            title = movie.title,
                            originalTitle = movie.originalTitle,
                            overview = movie.overview,
                            posterPath = movie.posterPath?.toTmdbImageUrl(),
                            backdropPath = movie.backdropPath?.toTmdbImageUrl("original"),
                            releaseDate = movie.releaseDate,
                            genres = movie.genreIds.joinToString(","),
                            voteAverage = movie.voteAverage,
                            voteCount = movie.voteCount,
                            popularity = movie.popularity
                        )
                    }
                    movieDao.insertCandidates(movies)
                } catch (_: Exception) { }
            }
        }
    }

    suspend fun fetchPopularMovies(apiKey: String, pages: Int = 5) {
        withContext(Dispatchers.IO) {
            for (page in 1..pages) {
                try {
                    val result = api.getPopularMovies(apiKey, page)
                    val movies = result.results.map { movie ->
                        MovieEntity(
                            tmdbId = movie.id,
                            title = movie.title,
                            originalTitle = movie.originalTitle,
                            overview = movie.overview,
                            posterPath = movie.posterPath?.toTmdbImageUrl(),
                            backdropPath = movie.backdropPath?.toTmdbImageUrl("original"),
                            releaseDate = movie.releaseDate,
                            genres = movie.genreIds.joinToString(","),
                            voteAverage = movie.voteAverage,
                            voteCount = movie.voteCount,
                            popularity = movie.popularity
                        )
                    }
                    movieDao.insertCandidates(movies)
                } catch (_: Exception) { }
            }
        }
    }

    suspend fun fetchTopRatedMovies(apiKey: String, pages: Int = 3) {
        withContext(Dispatchers.IO) {
            for (page in 1..pages) {
                try {
                    val result = api.getTopRatedMovies(apiKey, page)
                    val movies = result.results.map { movie ->
                        MovieEntity(
                            tmdbId = movie.id,
                            title = movie.title,
                            originalTitle = movie.originalTitle,
                            overview = movie.overview,
                            posterPath = movie.posterPath?.toTmdbImageUrl(),
                            backdropPath = movie.backdropPath?.toTmdbImageUrl("original"),
                            releaseDate = movie.releaseDate,
                            genres = movie.genreIds.joinToString(","),
                            voteAverage = movie.voteAverage,
                            voteCount = movie.voteCount,
                            popularity = movie.popularity
                        )
                    }
                    movieDao.insertCandidates(movies)
                } catch (_: Exception) { }
            }
        }
    }

    suspend fun fetchUpcomingMovies(apiKey: String, pages: Int = 2) {
        withContext(Dispatchers.IO) {
            for (page in 1..pages) {
                try {
                    val result = api.getUpcomingMovies(apiKey, page)
                    val movies = result.results.map { movie ->
                        MovieEntity(
                            tmdbId = movie.id,
                            title = movie.title,
                            originalTitle = movie.originalTitle,
                            overview = movie.overview,
                            posterPath = movie.posterPath?.toTmdbImageUrl(),
                            backdropPath = movie.backdropPath?.toTmdbImageUrl("original"),
                            releaseDate = movie.releaseDate,
                            genres = movie.genreIds.joinToString(","),
                            voteAverage = movie.voteAverage,
                            voteCount = movie.voteCount,
                            popularity = movie.popularity
                        )
                    }
                    movieDao.insertCandidates(movies)
                } catch (_: Exception) { }
            }
        }
    }

    suspend fun enrichMovieWithDetails(movie: MovieEntity, apiKey: String): MovieEntity {
        return withContext(Dispatchers.IO) {
            try {
                val detail = api.getMovieDetails(movie.tmdbId, apiKey)
                val director = detail.credits?.crew?.find { it.job == "Director" }?.name
                val cast = detail.credits?.cast?.take(8)?.map { it.name }?.joinToString(",") ?: ""
                val keywords = detail.keywords?.keywords?.map { it.name }?.joinToString(",") ?: ""
                movie.copy(
                    genres = detail.genres.joinToString(",") { it.id.toString() },
                    runtime = detail.runtime,
                    director = director,
                    cast = cast,
                    keywords = keywords,
                    voteAverage = detail.voteAverage,
                    voteCount = detail.voteCount,
                    popularity = detail.popularity
                )
            } catch (_: Exception) {
                movie
            }
        }
    }

    suspend fun searchAndFetchDetails(apiKey: String, query: String): com.thalos.cinephile.data.remote.TmdbMovieDetail? {
        return withContext(Dispatchers.IO) {
            try {
                val searchResult = api.searchMovie(apiKey, query)
                val first = searchResult.results.firstOrNull() ?: return@withContext null
                api.getMovieDetails(first.id, apiKey)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun getMovieVideos(tmdbId: Int, apiKey: String): List<com.thalos.cinephile.data.remote.TmdbVideo> {
        return withContext(Dispatchers.IO) {
            try {
                val result = api.getMovieVideos(tmdbId, apiKey)
                result.results.filter { it.site == "YouTube" && (it.type == "Trailer" || it.type == "Teaser") }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getSimilarMovies(tmdbId: Int, apiKey: String): List<MovieEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val result = api.getSimilarMovies(tmdbId, apiKey)
                result.results.map { movie ->
                    MovieEntity(
                        tmdbId = movie.id,
                        title = movie.title,
                        originalTitle = movie.originalTitle,
                        overview = movie.overview,
                        posterPath = movie.posterPath?.toTmdbImageUrl(),
                        backdropPath = movie.backdropPath?.toTmdbImageUrl("original"),
                        releaseDate = movie.releaseDate,
                        genres = movie.genreIds.joinToString(","),
                        voteAverage = movie.voteAverage,
                        voteCount = movie.voteCount,
                        popularity = movie.popularity
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun discoverMovies(
        apiKey: String,
        sortBy: String = "popularity.desc",
        page: Int = 1,
        minRating: Double? = null,
        yearGte: String? = null,
        yearLte: String? = null,
        minRuntime: Int? = null,
        maxRuntime: Int? = null,
        withGenres: Int? = null,
        withOriginalLanguage: String? = null,
        minVotes: Int = 20
    ): List<MovieEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val result = api.discoverMovies(
                    apiKey, sortBy, page, minVotes = minVotes, minRating, yearGte, yearLte, minRuntime, maxRuntime,
                    withGenres = withGenres?.toString(),
                    withOriginalLanguage = withOriginalLanguage
                )
                result.results.map { movie ->
                    MovieEntity(
                        tmdbId = movie.id,
                        title = movie.title,
                        originalTitle = movie.originalTitle,
                        overview = movie.overview,
                        posterPath = movie.posterPath?.toTmdbImageUrl(),
                        backdropPath = movie.backdropPath?.toTmdbImageUrl("original"),
                        releaseDate = movie.releaseDate,
                        genres = movie.genreIds.joinToString(","),
                        voteAverage = movie.voteAverage,
                        voteCount = movie.voteCount,
                        popularity = movie.popularity
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getAllMovies(): List<MovieEntity> {
        return movieDao.getAllMovies()
    }

    suspend fun getCandidateMovies(): List<MovieEntity> {
        return movieDao.getCandidateMovies()
    }

    suspend fun getUserMoviesOnce(): List<MovieEntity> {
        return movieDao.getUserMoviesOnce()
    }

    suspend fun updateEmbedding(tmdbId: Int, embedding: String) {
        movieDao.updateEmbedding(tmdbId, embedding)
    }

    suspend fun getCandidatesWithoutEmbeddings(): List<MovieEntity> {
        return movieDao.getCandidatesWithoutEmbeddings()
    }

    suspend fun rateMovie(movie: MovieEntity, rating: Double) {
        withContext(Dispatchers.IO) {
            val existing = movieDao.getMovieByTmdbId(movie.tmdbId)
            if (existing != null) {
                // Update existing to preserve id, embedding, watchlist state
                val updated = existing.copy(
                    isFromUser = true,
                    userRating = rating
                )
                movieDao.updateMovie(updated)
            } else {
                movieDao.insertMovie(movie.copy(isFromUser = true, userRating = rating))
            }
        }
    }

    suspend fun markWatched(movie: MovieEntity) {
        withContext(Dispatchers.IO) {
            val existing = movieDao.getMovieByTmdbId(movie.tmdbId)
            if (existing != null) {
                val updated = existing.copy(isFromUser = true)
                movieDao.updateMovie(updated)
            } else {
                movieDao.insertMovie(movie.copy(isFromUser = true, userRating = null))
            }
        }
    }

    suspend fun getMovieByTmdbId(tmdbId: Int): MovieEntity? {
        return withContext(Dispatchers.IO) {
            movieDao.getMovieByTmdbId(tmdbId)
        }
    }

    suspend fun insert(movie: MovieEntity) {
        movieDao.insertMovie(movie)
    }

    fun getWatchlist(): Flow<List<MovieEntity>> {
        return movieDao.getWatchlist()
    }

    suspend fun getWatchlistOnce(): List<MovieEntity> {
        return movieDao.getWatchlistOnce()
    }

    suspend fun searchTmdb(apiKey: String, query: String): List<MovieEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val result = api.searchMovie(apiKey, query)
                result.results.map { movie ->
                    MovieEntity(
                        tmdbId = movie.id,
                        title = movie.title,
                        originalTitle = movie.originalTitle,
                        overview = movie.overview,
                        posterPath = movie.posterPath?.toTmdbImageUrl(),
                        backdropPath = movie.backdropPath?.toTmdbImageUrl("original"),
                        releaseDate = movie.releaseDate,
                        genres = movie.genreIds.joinToString(","),
                        voteAverage = movie.voteAverage,
                        voteCount = movie.voteCount,
                        popularity = movie.popularity
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun toggleWatchlist(movie: MovieEntity) {
        withContext(Dispatchers.IO) {
            val existing = movieDao.getMovieByTmdbId(movie.tmdbId)
            if (existing == null) {
                movieDao.insertMovie(movie.copy(isWatchlisted = true))
            } else {
                movieDao.setWatchlisted(movie.tmdbId, !existing.isWatchlisted)
            }
        }
    }

    fun getUserMovies(): Flow<List<MovieEntity>> {
        return movieDao.getUserMovies()
    }

    suspend fun searchMovies(query: String): List<MovieEntity> {
        return movieDao.searchMovies("%$query%")
    }

    suspend fun fetchWatchProviders(tmdbId: Int, apiKey: String, countryCode: String = "BR"): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = api.getWatchProviders(tmdbId, apiKey)
                val country = result.results?.get(countryCode)
                val providers = country?.flatrate?.map { it.providerName } ?: emptyList()
                // Cache in DB
                if (providers.isNotEmpty()) {
                    movieDao.updateWatchProviders(tmdbId, providers.joinToString(","))
                }
                providers
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
