package com.thalos.cinephile.domain.engine

import com.thalos.cinephile.data.local.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindSpotAnalyzerTest {
    @Test
    fun `detects adjacent decade gap inside a liked genre` () {
        val userMovies = listOf(
            movie(1, "Arrival", "2016-11-10", genres = "878,18", rating = 9.0),
            movie(2, "Blade Runner 2049", "2017-10-06", genres = "878,53", rating = 9.0),
            movie(3, "Ex Machina", "2015-01-21", genres = "878,53", rating = 8.0)
        )
        val candidates = listOf(
            movie(10, "Solaris", "1972-03-20", genres = "878,18", votes = 1200, voteAverage = 8.1),
            movie(11, "Stalker", "1979-05-25", genres = "878,18", votes = 1800, voteAverage = 8.2),
            movie(12, "Silent Running", "1972-03-09", genres = "878,18", votes = 900, voteAverage = 7.4),
            movie(13, "The Man Who Fell to Earth", "1976-03-18", genres = "878,18", votes = 950, voteAverage = 7.2),
            movie(14, "A Clockwork Orange", "1971-12-19", genres = "878,18", votes = 5000, voteAverage = 8.0),
            movie(15, "Generic Action", "2018-01-01", genres = "28", votes = 2000, voteAverage = 6.1)
        )

        val blindSpots = BlindSpotAnalyzer.analyze(userMovies, candidates)

        val top = blindSpots.first()
        assertEquals(BlindSpotAnalyzer.BlindSpotType.ERA, top.type)
        assertEquals("1970s Science Fiction", top.title)
        assertTrue(top.reason.contains("you like Science Fiction"))
        assertEquals(listOf(11, 14, 10, 12, 13), top.sampleMovieIds)
        assertEquals(5, top.sampleMovies.size)
    }

    @Test
    fun `detects unexplored genre that appears near the user's taste` () {
        val userMovies = listOf(
            movie(1, "Se7en", "1995-09-22", genres = "80,53", rating = 9.0),
            movie(2, "Zodiac", "2007-03-02", genres = "80,53", rating = 8.0),
            movie(3, "Memories of Murder", "2003-05-02", genres = "80,53", rating = 9.0)
        )
        val candidates = listOf(
            movie(20, "The Vanishing", "1988-10-27", genres = "9648,53", votes = 900, voteAverage = 7.7),
            movie(21, "Cure", "1997-11-06", genres = "9648,53", votes = 1100, voteAverage = 7.8),
            movie(22, "Burning", "2018-05-17", genres = "9648,53", votes = 1600, voteAverage = 7.4),
            movie(23, "Decision to Leave", "2022-06-29", genres = "9648,53", votes = 1700, voteAverage = 7.3),
            movie(24, "The Wailing", "2016-05-12", genres = "9648,53", votes = 1800, voteAverage = 7.5),
            movie(25, "Random Romance", "2001-01-01", genres = "10749", votes = 1200, voteAverage = 6.2)
        )

        val blindSpots = BlindSpotAnalyzer.analyze(userMovies, candidates)

        val mysteryGap = blindSpots.first { it.title == "Mystery" }
        assertEquals(BlindSpotAnalyzer.BlindSpotType.GENRE, mysteryGap.type)
        assertTrue(mysteryGap.reason.contains("adjacent to Thriller"))
        assertEquals(listOf(21, 20, 24, 22, 23), mysteryGap.sampleMovieIds)
        assertEquals(5, mysteryGap.sampleMovies.size)
    }

    private fun movie(
        tmdbId: Int,
        title: String,
        releaseDate: String,
        genres: String,
        rating: Double? = null,
        votes: Int = 100,
        voteAverage: Double = 7.0
    ) = MovieEntity(
        tmdbId = tmdbId,
        title = title,
        releaseDate = releaseDate,
        genres = genres,
        userRating = rating,
        voteCount = votes,
        voteAverage = voteAverage,
        isFromUser = rating != null
    )
}
