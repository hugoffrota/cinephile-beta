package com.thalos.cinephile.data.remote

import com.google.gson.annotations.SerializedName

data class TmdbMovieResult(
    val page: Int,
    val results: List<TmdbMovie>,
    @SerializedName("total_pages") val totalPages: Int
)

data class TmdbMovie(
    val id: Int,
    val title: String,
    @SerializedName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList(),
    val runtime: Int? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0
)

data class TmdbMovieDetail(
    val id: Int,
    val title: String,
    @SerializedName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val runtime: Int? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerializedName("credits") val credits: TmdbCredits? = null,
    @SerializedName("keywords") val keywords: TmdbKeywords? = null
)

data class TmdbGenre(val id: Int, val name: String)
data class TmdbCredits(val cast: List<TmdbCastMember>, val crew: List<TmdbCrewMember>)
data class TmdbCastMember(val name: String, val order: Int)
data class TmdbCrewMember(val name: String, val job: String, val department: String)
data class TmdbKeywords(val keywords: List<TmdbKeyword>)
data class TmdbKeyword(val id: Int, val name: String)
data class TmdbSearchResult(val results: List<TmdbMovie>)

data class TmdbVideoResult(
    val id: Int,
    val results: List<TmdbVideo>
)

data class TmdbVideo(
    val key: String,
    val name: String,
    val site: String,
    val type: String
)

// --- Watch Providers ---

data class TmdbWatchProvidersResult(
    val id: Int,
    val results: Map<String, TmdbCountryProviders>? = null
)

data class TmdbCountryProviders(
    val link: String? = null,
    @SerializedName("flatrate") val flatrate: List<TmdbProvider>? = null,
    @SerializedName("rent") val rent: List<TmdbProvider>? = null,
    @SerializedName("buy") val buy: List<TmdbProvider>? = null
)

data class TmdbProvider(
    @SerializedName("provider_id") val providerId: Int,
    @SerializedName("provider_name") val providerName: String,
    @SerializedName("logo_path") val logoPath: String? = null
)
