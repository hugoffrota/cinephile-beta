package com.thalos.cinephile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE isFromUser = 1 ORDER BY userRating DESC, title ASC")
    fun getUserMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE isFromUser = 1 ORDER BY userRating DESC, title ASC")
    suspend fun getUserMoviesOnce(): List<MovieEntity>

    @Query("SELECT * FROM movies ORDER BY popularity DESC LIMIT 500")
    suspend fun getAllMovies(): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE isFromUser = 0 ORDER BY popularity DESC LIMIT 1000")
    suspend fun getCandidateMovies(): List<MovieEntity>

    @Query("SELECT COUNT(*) FROM movies WHERE isFromUser = 1")
    suspend fun getUserMovieCount(): Int

    @Query("SELECT * FROM movies WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getMovieByTmdbId(tmdbId: Int): MovieEntity?

    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun getMovieById(id: Long): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: MovieEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<MovieEntity>)

    @Update
    suspend fun updateMovie(movie: MovieEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCandidates(movies: List<MovieEntity>)

    @Query("UPDATE movies SET embedding = :embedding WHERE tmdbId = :tmdbId")
    suspend fun updateEmbedding(tmdbId: Int, embedding: String)

    @Query("SELECT * FROM movies WHERE isFromUser = 0 AND embedding = '' ORDER BY popularity DESC")
    suspend fun getCandidatesWithoutEmbeddings(): List<MovieEntity>

    @Query("DELETE FROM movies WHERE isFromUser = 1")
    suspend fun clearUserMovies()

    @Query("DELETE FROM movies WHERE isFromUser = 0")
    suspend fun clearCandidateMovies()

    @Query("SELECT * FROM movies WHERE isWatchlisted = 1 ORDER BY voteAverage DESC")
    fun getWatchlist(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE isWatchlisted = 1 ORDER BY voteAverage DESC")
    suspend fun getWatchlistOnce(): List<MovieEntity>

    @Query("UPDATE movies SET isWatchlisted = :watchlisted WHERE tmdbId = :tmdbId")
    suspend fun setWatchlisted(tmdbId: Int, watchlisted: Boolean)

    @Query("SELECT * FROM movies WHERE title LIKE :query OR originalTitle LIKE :query LIMIT 20")
    suspend fun searchMovies(query: String): List<MovieEntity>

    @Query("UPDATE movies SET watchProviders = :providers WHERE tmdbId = :tmdbId")
    suspend fun updateWatchProviders(tmdbId: Int, providers: String)
}
