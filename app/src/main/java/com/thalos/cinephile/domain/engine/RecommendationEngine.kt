package com.thalos.cinephile.domain.engine

import com.thalos.cinephile.data.local.MovieDao
import com.thalos.cinephile.data.local.MovieEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

class RecommendationEngine(
    private val movieDao: MovieDao,
    private val embeddingEngine: EmbeddingEngine
) {

    data class UserProfile(
        val embedding: FloatArray,
        val genreWeights: Map<String, Double>,
        val preferredDecades: List<Int>,
        val preferredRuntime: Int?,
        val favoriteDirectors: Set<String>,
        val favoriteCast: Set<String>,
        val avgRating: Double
    )

    data class ScoredMovie(
        val movie: MovieEntity,
        val semanticScore: Double,
        val genreScore: Double,
        val castScore: Double,
        val eraScore: Double,
        val runtimeScore: Double,
        val ratingBoost: Double,
        val totalScore: Double,
        val explanation: String
    )

    suspend fun generateRecommendations(
        count: Int = 10,
        dismissedIds: Set<Int> = emptySet(),
        watchlistedIds: Set<Int> = emptySet()
    ): List<ScoredMovie> = withContext(Dispatchers.Default) {
        val userMovies = movieDao.getUserMoviesOnce()
        val watchlistedMovies = movieDao.getWatchlistOnce()
        val allProfileMovies = userMovies + watchlistedMovies
        if (allProfileMovies.isEmpty()) return@withContext emptyList()

        val profile = buildProfile(userMovies, watchlistedMovies)
        val dismissedMovies = dismissedIds.mapNotNull { movieDao.getMovieByTmdbId(it) }
        val today = LocalDate.now().toString()
        val excludedIds = (userMovies.map { it.tmdbId } + dismissedIds + watchlistedIds).toSet()
        val candidates = movieDao.getCandidateMovies()
            .filter { c -> c.tmdbId !in excludedIds }
            .filter { c -> c.releaseDate.isNullOrBlank() || c.releaseDate <= today }

        if (candidates.isEmpty()) return@withContext emptyList()

        // Score all candidates (penalize similarity to dismissed movies)
        val scored = candidates.map { candidate ->
            scoreCandidate(candidate, profile, userMovies, dismissedMovies)
        }

        // Apply MMR for diversity
        applyMMR(scored, count)
    }

    private fun buildProfile(userMovies: List<MovieEntity>, watchlistedMovies: List<MovieEntity> = emptyList()): UserProfile {
        val ratedMovies = userMovies.filter { it.userRating != null && it.userRating >= 3.0 }
        // Blend watchlisted movies in with a default rating of 3.5 (indicates interest)
        val watchlistedWithRating = watchlistedMovies.map {
            if (it.userRating != null) it else it.copy(userRating = 3.5)
        }
        val allMovies = ratedMovies + watchlistedWithRating
        if (allMovies.isEmpty()) {
            // Use all movies if none rated highly
            return buildProfileFromMovies(userMovies)
        }
        return buildProfileFromMovies(allMovies)
    }

    private fun buildProfileFromMovies(movies: List<MovieEntity>): UserProfile {
        // Build embedding centroid
        val embeddings = movies.mapNotNull { movie ->
            if (movie.embedding.isNotBlank()) embeddingEngine.parseEmbedding(movie.embedding) else null
        }
        val centroid = if (embeddings.isNotEmpty()) {
            val dim = embeddings[0].size
            val avg = FloatArray(dim) { i ->
                embeddings.map { it[i] }.average().toFloat()
            }
            embeddingEngine.normalize(avg)
            avg
        } else FloatArray(384) { 0f }

        // Genre weights
        val genreCounts = mutableMapOf<String, Double>()
        movies.forEach { movie ->
            val weight = movie.userRating ?: 3.0
            movie.genres.split(",").filter { it.isNotBlank() }.forEach { genre ->
                genreCounts[genre] = genreCounts.getOrDefault(genre, 0.0) + weight
            }
        }
        val totalGenreWeight = genreCounts.values.sum()
        val genreWeights = if (totalGenreWeight > 0) {
            genreCounts.mapValues { it.value / totalGenreWeight }
        } else emptyMap()

        // Preferred decades
        val decades = movies.mapNotNull { it.releaseDate?.take(4)?.toIntOrNull()?.let { y -> (y / 10) * 10 } }
        val decadeCounts = decades.groupingBy { it }.eachCount()
        val preferredDecades = decadeCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }

        // Preferred runtime
        val runtimes = movies.mapNotNull { it.runtime }.filter { it > 0 }
        val preferredRuntime = if (runtimes.isNotEmpty()) runtimes.average().toInt() else null

        // Favorite directors and cast
        val directors = movies.mapNotNull { it.director }.toSet()
        val cast = movies.flatMap { it.cast.split(",") }.filter { it.isNotBlank() }.toSet()

        val avgRating = movies.mapNotNull { it.userRating }.average()

        return UserProfile(centroid, genreWeights, preferredDecades, preferredRuntime, directors, cast, avgRating)
    }

    private fun scoreCandidate(
        candidate: MovieEntity,
        profile: UserProfile,
        userMovies: List<MovieEntity>,
        dismissedMovies: List<MovieEntity> = emptyList()
    ): ScoredMovie {
        // Semantic similarity (40%)
        val candidateEmbedding = if (candidate.embedding.isNotBlank()) {
            embeddingEngine.parseEmbedding(candidate.embedding)
        } else FloatArray(384) { 0f }
        val semanticScore = embeddingEngine.cosineSimilarity(profile.embedding, candidateEmbedding).toDouble()

        // Genre match (25%)
        val candidateGenres = candidate.genres.split(",").filter { it.isNotBlank() }
        val genreOverlap = candidateGenres.sumOf { profile.genreWeights[it] ?: 0.0 }
        val genreScore = if (candidateGenres.isNotEmpty()) genreOverlap / candidateGenres.size else 0.0

        // Director/cast bonus (15%)
        var castScore = 0.0
        if (candidate.director != null && profile.favoriteDirectors.contains(candidate.director)) {
            castScore += 0.7
        }
        val candidateCast = candidate.cast.split(",").filter { it.isNotBlank() }
        val castOverlap = candidateCast.count { profile.favoriteCast.contains(it) }
        if (candidateCast.isNotEmpty()) {
            castScore += 0.3 * (castOverlap.toDouble() / candidateCast.size)
        }
        castScore = castScore.coerceIn(0.0, 1.0)

        // Era fit (10%)
        val candidateYear = candidate.releaseDate?.take(4)?.toIntOrNull()
        val candidateDecade = candidateYear?.let { (it / 10) * 10 }
        val eraScore = if (candidateDecade != null && profile.preferredDecades.contains(candidateDecade)) 1.0 else {
            if (candidateDecade != null && profile.preferredDecades.isNotEmpty()) {
                val closest = profile.preferredDecades.minByOrNull { abs(it - candidateDecade) } ?: candidateDecade
                1.0 - (abs(closest - candidateDecade) / 50.0).coerceIn(0.0, 1.0)
            } else 0.5
        }

        // Runtime fit (10%)
        val runtimeScore = if (candidate.runtime != null && profile.preferredRuntime != null) {
            val diff = abs(candidate.runtime - profile.preferredRuntime)
            1.0 - (diff / 60.0).coerceIn(0.0, 1.0)
        } else 0.5

        // Rating boost - prefer well-rated candidates
        val ratingBoost = (candidate.voteAverage / 10.0).coerceIn(0.0, 1.0)

        // Penalize candidates similar to dismissed movies
        val dismissalPenalty = if (dismissedMovies.isNotEmpty()) {
            dismissedMovies.maxOf { dismissed -> movieSimilarity(candidate, dismissed) } * 0.35
        } else 0.0

        val totalScore = (
            semanticScore * 0.40 +
            genreScore * 0.25 +
            castScore * 0.15 +
            eraScore * 0.10 +
            runtimeScore * 0.10 -
            dismissalPenalty
        ).coerceAtLeast(0.0)

        val explanation = buildExplanation(candidate, semanticScore, genreScore, castScore, eraScore, profile)

        return ScoredMovie(candidate, semanticScore, genreScore, castScore, eraScore, runtimeScore, ratingBoost, totalScore, explanation)
    }

    private fun buildExplanation(
        candidate: MovieEntity,
        semanticScore: Double,
        genreScore: Double,
        castScore: Double,
        eraScore: Double,
        profile: UserProfile
    ): String {
        val parts = mutableListOf<String>()
        if (semanticScore > 0.65) parts.add("matches the tone of your highly-rated films")
        if (genreScore > 0.6) parts.add("aligns with your preferred genres")
        if (castScore > 0.5) {
            val dir = candidate.director
            if (dir != null && profile.favoriteDirectors.contains(dir)) parts.add("from director $dir")
            else parts.add("features familiar cast")
        }
        if (eraScore > 0.7) parts.add("from your preferred era")
        if (parts.isEmpty()) parts.add("a well-rated discovery outside your usual picks")
        return parts.joinToString("; ").replaceFirstChar { it.uppercase() }
    }

    private fun applyMMR(scoredMovies: List<ScoredMovie>, count: Int): List<ScoredMovie> {
        val remaining = scoredMovies.sortedByDescending { it.totalScore }.toMutableList()
        val selected = mutableListOf<ScoredMovie>()
        val lambda = 0.5 // Balance between relevance and diversity

        while (selected.size < count && remaining.isNotEmpty()) {
            val best = remaining.maxByOrNull { item ->
                val relevance = item.totalScore
                val diversity = if (selected.isEmpty()) 1.0 else {
                    selected.minOf { selectedItem ->
                        val sim = movieSimilarity(item.movie, selectedItem.movie)
                        sim
                    }
                }
                lambda * relevance - (1 - lambda) * diversity
            } ?: break

            selected.add(best)
            remaining.remove(best)
        }

        return selected
    }

    private fun movieSimilarity(a: MovieEntity, b: MovieEntity): Double {
        // Jaccard on genres
        val genresA = a.genres.split(",").filter { it.isNotBlank() }.toSet()
        val genresB = b.genres.split(",").filter { it.isNotBlank() }.toSet()
        val genreJaccard = if (genresA.isEmpty() || genresB.isEmpty()) 0.0 else {
            val intersection = genresA.intersect(genresB).size.toDouble()
            val union = genresA.union(genresB).size.toDouble()
            intersection / union
        }

        // Director match
        val directorMatch = if (a.director != null && a.director == b.director) 1.0 else 0.0

        // Year proximity
        val yearA = a.releaseDate?.take(4)?.toIntOrNull()
        val yearB = b.releaseDate?.take(4)?.toIntOrNull()
        val yearSim = if (yearA != null && yearB != null) {
            1.0 - (abs(yearA - yearB) / 30.0).coerceIn(0.0, 1.0)
        } else 0.5

        return (genreJaccard * 0.5 + directorMatch * 0.3 + yearSim * 0.2)
    }
}
