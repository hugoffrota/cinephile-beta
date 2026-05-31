package com.thalos.cinephile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.thalos.cinephile.data.local.AppDatabase
import com.thalos.cinephile.data.local.dataStore
import com.thalos.cinephile.data.remote.TmdbApi
import com.thalos.cinephile.data.repository.MovieRepository
import com.thalos.cinephile.data.local.UserProfileRepository
import com.thalos.cinephile.domain.engine.EmbeddingEngine
import com.thalos.cinephile.domain.engine.RecommendationEngine

class CinephileApp : Application(), ImageLoaderFactory {
    val database by lazy { AppDatabase.getDatabase(this) }
    val tmdbApi by lazy { TmdbApi.create() }
    val movieRepository by lazy { MovieRepository(database.movieDao()) }
    val userProfileRepository by lazy { UserProfileRepository(dataStore) }
    val embeddingEngine by lazy { EmbeddingEngine(this) }
    val recommendationEngine by lazy { RecommendationEngine(database.movieDao(), embeddingEngine) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512MB
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
