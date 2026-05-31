package com.thalos.cinephile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "movies",
    indices = [Index(value = ["tmdbId"], unique = true)]
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tmdbId: Int,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val genres: String = "", // comma-separated genre IDs
    val runtime: Int? = null,
    val voteAverage: Double = 0.0,
    val voteCount: Int = 0,
    val popularity: Double = 0.0,
    val director: String? = null,
    val cast: String = "", // comma-separated
    val keywords: String = "", // comma-separated
    val embedding: String = "", // comma-separated floats
    val watchProviders: String = "", // JSON array of provider names
    val isFromUser: Boolean = false, // true if imported from Letterboxd
    val userRating: Double? = null, // 0.5-5.0 scale from Letterboxd
    val rewatch: Boolean = false,
    val watchedDate: String? = null,
    val isWatchlisted: Boolean = false
)
