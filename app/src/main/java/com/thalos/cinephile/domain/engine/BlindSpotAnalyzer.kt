package com.thalos.cinephile.domain.engine

import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.data.remote.GenreMap
import kotlin.math.ln

object BlindSpotAnalyzer {
    enum class BlindSpotType { GENRE, ERA }

    data class BlindSpot(
        val type: BlindSpotType,
        val title: String,
        val reason: String,
        val score: Double,
        val sampleMovieIds: List<Int>,
        val sampleTitles: List<String>,
        val sampleMovies: List<MovieEntity>
    )

    fun analyze(
        userMovies: List<MovieEntity>,
        candidates: List<MovieEntity>,
        excludedIds: Set<Int> = emptySet(),
        maxResults: Int = 8
    ): List<BlindSpot> {
        if (userMovies.size < 2 || candidates.isEmpty()) return emptyList()

        val watchedIds = userMovies.map { it.tmdbId }.toSet()
        val ratedMovies = userMovies.filter { it.userRating != null }
        val likedMovies = ratedMovies.filter { (it.userRating ?: 0.0) >= 7.0 }
            .ifEmpty { ratedMovies.filter { (it.userRating ?: 0.0) >= 6.0 } }
            .ifEmpty { userMovies }

        val userGenreCounts = userMovies
            .flatMap { it.genreIds() }
            .groupingBy { it }
            .eachCount()
        val likedGenreCounts = likedMovies
            .flatMap { it.genreIds() }
            .groupingBy { it }
            .eachCount()
        val topLikedGenres = likedGenreCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .toSet()
        if (topLikedGenres.isEmpty()) return emptyList()

        // Blind Spot is the "prestige homework" lane: strong public validation, not already rated,
        // and not currently shown in For You. That avoids the annoying duplicate carousel effect.
        val candidatePool = candidates
            .filter { it.tmdbId !in watchedIds && it.tmdbId !in excludedIds }
            .filter { it.voteAverage >= 7.0 && (it.voteCount ?: 0) >= 300 }
            .distinctBy { it.tmdbId }
        if (candidatePool.isEmpty()) return emptyList()

        val eraGaps = buildEraGaps(userMovies, candidatePool, likedGenreCounts)
        val genreGaps = buildGenreGaps(candidatePool, userGenreCounts, topLikedGenres)
        val broadGaps = buildBroadGenreGaps(candidatePool, userGenreCounts, topLikedGenres)

        return (eraGaps + genreGaps + broadGaps)
            .distinctBy { it.type to it.title }
            .sortedByDescending { it.score }
            .take(maxResults.coerceAtLeast(7))
    }

    private fun buildEraGaps(
        userMovies: List<MovieEntity>,
        candidates: List<MovieEntity>,
        likedGenreCounts: Map<Int, Int>
    ): List<BlindSpot> {
        val watchedGenreDecadeCounts = userMovies
            .flatMap { movie -> movie.genreIds().mapNotNull { genre -> movie.decade()?.let { genre to it } } }
            .groupingBy { it }
            .eachCount()

        return likedGenreCounts
            .filter { it.value >= 2 }
            .flatMap { (genreId, likedCount) ->
                candidates
                    .filter { genreId in it.genreIds() }
                    .groupBy { it.decade() }
                    .filterKeys { it != null }
                    .mapNotNull { (decade, moviesInDecade) ->
                        val actualDecade = decade ?: return@mapNotNull null
                        val watchedInThisLane = watchedGenreDecadeCounts[genreId to actualDecade] ?: 0
                        if (watchedInThisLane > 1) return@mapNotNull null
                        val strongSamples = moviesInDecade
                            .distinctBy { it.tmdbId }
                            .sortedWith(compareByDescending<MovieEntity> { qualityScore(it) }.thenBy { it.releaseDate ?: "" })
                            .take(5)
                        if (strongSamples.size < 5) return@mapNotNull null
                        val genreName = GenreMap.name(genreId)
                        val avgQuality = strongSamples.sumOf { qualityScore(it) } / strongSamples.size
                        BlindSpot(
                            type = BlindSpotType.ERA,
                            title = "${actualDecade}s $genreName",
                            reason = "you like $genreName, but you have barely rated this era; these are high-rated entry points.",
                            score = 120.0 + likedCount * 6.0 + avgQuality - watchedInThisLane * 12.0,
                            sampleMovieIds = strongSamples.map { it.tmdbId },
                            sampleTitles = strongSamples.map { it.title },
                            sampleMovies = strongSamples
                        )
                    }
            }
    }

    private fun buildGenreGaps(
        candidates: List<MovieEntity>,
        userGenreCounts: Map<Int, Int>,
        topLikedGenres: Set<Int>
    ): List<BlindSpot> {
        return candidates
            .flatMap { candidate -> candidate.genreIds().map { it to candidate } }
            .groupBy({ it.first }, { it.second })
            .filterKeys { genre -> (userGenreCounts[genre] ?: 0) <= 1 }
            .mapNotNull { (gapGenre, gapMovies) ->
                val adjacentGenre = topLikedGenres
                    .maxByOrNull { likedGenre -> gapMovies.count { likedGenre in it.genreIds() } }
                    ?.takeIf { likedGenre -> gapMovies.any { likedGenre in it.genreIds() } }
                    ?: return@mapNotNull null
                val samples = gapMovies
                    .filter { adjacentGenre in it.genreIds() }
                    .distinctBy { it.tmdbId }
                    .sortedWith(compareByDescending<MovieEntity> { qualityScore(it) }.thenByDescending { it.voteCount ?: 0 })
                    .take(5)
                if (samples.size < 5) return@mapNotNull null
                val gapName = GenreMap.name(gapGenre)
                val adjacentName = GenreMap.name(adjacentGenre)
                val watchedCount = userGenreCounts[gapGenre] ?: 0
                val avgQuality = samples.sumOf { qualityScore(it) } / samples.size
                BlindSpot(
                    type = BlindSpotType.GENRE,
                    title = gapName,
                    reason = "$gapName is adjacent to $adjacentName in your taste, but you have only rated $watchedCount film${if (watchedCount == 1) "" else "s"} there; these are high-rated gateways.",
                    score = 90.0 + avgQuality - watchedCount * 15.0 + samples.size * 4.0,
                    sampleMovieIds = samples.map { it.tmdbId },
                    sampleTitles = samples.map { it.title },
                    sampleMovies = samples
                )
            }
    }


    private fun buildBroadGenreGaps(
        candidates: List<MovieEntity>,
        userGenreCounts: Map<Int, Int>,
        topLikedGenres: Set<Int>
    ): List<BlindSpot> {
        return candidates
            .flatMap { candidate -> candidate.genreIds().map { it to candidate } }
            .groupBy({ it.first }, { it.second })
            .filterKeys { genre -> GenreMap.name(genre) != "Unknown" }
            .mapNotNull { (gapGenre, gapMovies) ->
                val samples = gapMovies
                    .distinctBy { it.tmdbId }
                    .sortedWith(compareByDescending<MovieEntity> { qualityScore(it) }.thenByDescending { it.voteCount ?: 0 })
                    .take(5)
                if (samples.size < 5) return@mapNotNull null
                val gapName = GenreMap.name(gapGenre)
                val watchedCount = userGenreCounts[gapGenre] ?: 0
                val adjacentOverlap = samples.sumOf { movie -> movie.genreIds().count { it in topLikedGenres } }
                BlindSpot(
                    type = BlindSpotType.GENRE,
                    title = gapName,
                    reason = "$gapName is lighter in your ratings than your main comfort zones; these are five high-rated entry points.",
                    score = 72.0 + (samples.sumOf { qualityScore(it) } / samples.size) + adjacentOverlap * 2.0 - watchedCount * 4.0,
                    sampleMovieIds = samples.map { it.tmdbId },
                    sampleTitles = samples.map { it.title },
                    sampleMovies = samples
                )
            }
    }


    private fun qualityScore(movie: MovieEntity): Double {
        val voteCount = (movie.voteCount ?: 0).coerceAtLeast(1)
        val confidence = (ln(voteCount.toDouble()) / ln(10_000.0)).coerceIn(0.0, 1.0)
        return movie.voteAverage * 10.0 + confidence * 12.0
    }

    private fun MovieEntity.genreIds(): List<Int> = genres
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }

    private fun MovieEntity.decade(): Int? = releaseDate
        ?.take(4)
        ?.toIntOrNull()
        ?.let { (it / 10) * 10 }
}
