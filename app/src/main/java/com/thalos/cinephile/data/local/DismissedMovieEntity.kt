package com.thalos.cinephile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dismissed_movies")
data class DismissedMovieEntity(
    @PrimaryKey
    val tmdbId: Int,
    val dismissedAt: Long = System.currentTimeMillis()
)
