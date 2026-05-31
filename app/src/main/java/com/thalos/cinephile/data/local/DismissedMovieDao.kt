package com.thalos.cinephile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DismissedMovieDao {
    @Query("SELECT tmdbId FROM dismissed_movies")
    fun getDismissedTmdbIds(): Flow<List<Int>>

    @Query("SELECT tmdbId FROM dismissed_movies")
    suspend fun getDismissedTmdbIdsOnce(): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDismissed(entity: DismissedMovieEntity)

    @Query("DELETE FROM dismissed_movies WHERE tmdbId = :tmdbId")
    suspend fun removeDismissed(tmdbId: Int)

    @Query("DELETE FROM dismissed_movies")
    suspend fun clearAllDismissed()

    @Query("SELECT COUNT(*) FROM dismissed_movies")
    suspend fun getDismissedCount(): Int
}
