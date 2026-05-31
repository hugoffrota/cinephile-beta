package com.thalos.cinephile.domain.engine

import com.thalos.cinephile.data.remote.GenreMap

object SearchQueryParser {

    data class SearchFilters(
        val withGenres: Int? = null,
        val yearGte: String? = null,
        val yearLte: String? = null,
        val minRating: Double? = null,
        val minRuntime: Int? = null,
        val maxRuntime: Int? = null,
        val keywords: List<String> = emptyList(),
        val language: String? = null,
        val sortBy: String = "popularity.desc",
        val queryText: String = ""
    )

    private val genreKeywords = mapOf(
        "action" to 28,
        "adventure" to 12,
        "animation" to 16,
        "animated" to 16,
        "anime" to 16,
        "comedy" to 35,
        "funny" to 35,
        "crime" to 80,
        "documentary" to 99,
        "doc" to 99,
        "drama" to 18,
        "family" to 10751,
        "kids" to 10751,
        "fantasy" to 14,
        "history" to 36,
        "historical" to 36,
        "horror" to 27,
        "scary" to 27,
        "music" to 10402,
        "musical" to 10402,
        "mystery" to 9648,
        "romance" to 10749,
        "romantic" to 10749,
        "love" to 10749,
        "sci fi" to 878,
        "scifi" to 878,
        "science fiction" to 878,
        "science-fiction" to 878,
        "space" to 878,
        "tv movie" to 10770,
        "thriller" to 53,
        "suspense" to 53,
        "war" to 10752,
        "western" to 37,
        "cowboy" to 37
    )

    private val languageKeywords = mapOf(
        "korean" to "ko",
        "japanese" to "ja",
        "chinese" to "zh",
        "french" to "fr",
        "german" to "de",
        "spanish" to "es",
        "italian" to "it",
        "indian" to "hi",
        "bollywood" to "hi",
        "british" to "en",
        "american" to "en",
        "russian" to "ru",
        "portuguese" to "pt",
        "brazilian" to "pt"
    )

    private val decadePatterns = listOf(
        Regex("\\b(\\d{2})s\\b"),
        Regex("\\b(19|20)(\\d{2})s\\b"),
        Regex("from the (\\d{2}|19|20)(\\d{2})s", RegexOption.IGNORE_CASE),
        Regex("in the (\\d{2}|19|20)(\\d{2})s", RegexOption.IGNORE_CASE),
        Regex("(\\d{2}|19|20)(\\d{2})'s", RegexOption.IGNORE_CASE)
    )

    private val yearPatterns = listOf(
        Regex("from (19|20)(\\d{2})", RegexOption.IGNORE_CASE),
        Regex("after (19|20)(\\d{2})", RegexOption.IGNORE_CASE),
        Regex("since (19|20)(\\d{2})", RegexOption.IGNORE_CASE),
        Regex("before (19|20)(\\d{2})", RegexOption.IGNORE_CASE),
        Regex("until (19|20)(\\d{2})", RegexOption.IGNORE_CASE),
        Regex("in (19|20)(\\d{2})", RegexOption.IGNORE_CASE),
        Regex("year (19|20)(\\d{2})", RegexOption.IGNORE_CASE),
        Regex("(19|20)(\\d{2}) to (19|20)(\\d{2})", RegexOption.IGNORE_CASE)
    )

    private val ratingPatterns = listOf(
        Regex("high rated", RegexOption.IGNORE_CASE),
        Regex("top rated", RegexOption.IGNORE_CASE),
        Regex("best", RegexOption.IGNORE_CASE),
        Regex("good", RegexOption.IGNORE_CASE),
        Regex("above (\\d(\\.\\d)?)", RegexOption.IGNORE_CASE),
        Regex("rating (\\d(\\.\\d)?)", RegexOption.IGNORE_CASE),
        Regex("rated (\\d(\\.\\d)?)", RegexOption.IGNORE_CASE)
    )

    private val runtimePatterns = listOf(
        Regex("under (\\d+) (min|minute|hours?)", RegexOption.IGNORE_CASE),
        Regex("less than (\\d+) (min|minute|hours?)", RegexOption.IGNORE_CASE),
        Regex("over (\\d+) (min|minute|hours?)", RegexOption.IGNORE_CASE),
        Regex("more than (\\d+) (min|minute|hours?)", RegexOption.IGNORE_CASE),
        Regex("shorter? than (\\d+) (min|minute|hours?)", RegexOption.IGNORE_CASE),
        Regex("longer? than (\\d+) (min|minute|hours?)", RegexOption.IGNORE_CASE),
        Regex("short", RegexOption.IGNORE_CASE),
        Regex("long", RegexOption.IGNORE_CASE)
    )

    private val keywordPatterns = listOf(
        Regex("silent", RegexOption.IGNORE_CASE),
        Regex("black and white", RegexOption.IGNORE_CASE),
        Regex("b&w", RegexOption.IGNORE_CASE),
        Regex("colorful", RegexOption.IGNORE_CASE),
        Regex("violent", RegexOption.IGNORE_CASE),
        Regex("gory", RegexOption.IGNORE_CASE),
        Regex("psychological", RegexOption.IGNORE_CASE),
        Regex("supernatural", RegexOption.IGNORE_CASE),
        Regex("zombie", RegexOption.IGNORE_CASE),
        Regex("monster", RegexOption.IGNORE_CASE),
        Regex("alien", RegexOption.IGNORE_CASE),
        Regex("time travel", RegexOption.IGNORE_CASE),
        Regex("based on true", RegexOption.IGNORE_CASE),
        Regex("true story", RegexOption.IGNORE_CASE),
        Regex("cult", RegexOption.IGNORE_CASE),
        Regex("indie", RegexOption.IGNORE_CASE),
        Regex("independent", RegexOption.IGNORE_CASE),
        Regex("blockbuster", RegexOption.IGNORE_CASE),
        Regex("classic", RegexOption.IGNORE_CASE),
        Regex("masterpiece", RegexOption.IGNORE_CASE),
        Regex("award winning", RegexOption.IGNORE_CASE),
        Regex("oscar", RegexOption.IGNORE_CASE)
    )

    fun parse(query: String): SearchFilters {
        if (query.isBlank()) return SearchFilters()
        val lower = query.lowercase()
        val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }

        var genreId: Int? = null
        var yearGte: String? = null
        var yearLte: String? = null
        var minRating: Double? = null
        var minRuntime: Int? = null
        var maxRuntime: Int? = null
        val keywords = mutableListOf<String>()
        var sortBy = "popularity.desc"

        // --- Genre detection ---
        for ((keyword, id) in genreKeywords) {
            if (lower.contains(" $keyword ") || lower.startsWith("$keyword ") || lower.endsWith(" $keyword") || lower == keyword) {
                genreId = id
                break
            }
        }

        // --- Language detection ---
        var languageCode: String? = null
        for ((keyword, code) in languageKeywords) {
            if (lower.contains(" $keyword ") || lower.startsWith("$keyword ") || lower.endsWith(" $keyword") || lower == keyword) {
                languageCode = code
                break
            }
        }

        // --- Decade detection ---
        for (pattern in decadePatterns) {
            val match = pattern.find(query)
            if (match != null) {
                val groups = match.groupValues.filter { it.isNotBlank() && it != match.value }
                val decadeStr = groups.lastOrNull { it.matches(Regex("\\d{2}")) }
                if (decadeStr != null) {
                    val prefix = if (decadeStr.toInt() >= 50) "19" else "20"
                    val fullYear = "$prefix$decadeStr".toInt()
                    yearGte = fullYear.toString()
                    yearLte = (fullYear + 9).toString()
                }
                break
            }
        }

        // --- Exact year detection ---
        // "from 1995" / "after 1995" / "since 1995"
        Regex("(from|after|since) (19|20)(\\d{2})", RegexOption.IGNORE_CASE).find(query)?.let {
            val year = "${it.groupValues[2]}${it.groupValues[3]}"
            yearGte = year
        }
        // "before 2000" / "until 2000"
        Regex("(before|until) (19|20)(\\d{2})", RegexOption.IGNORE_CASE).find(query)?.let {
            val year = "${it.groupValues[2]}${it.groupValues[3]}"
            yearLte = year
        }
        // "in 1999"
        Regex("\\bin (19|20)(\\d{2})\\b", RegexOption.IGNORE_CASE).find(query)?.let {
            val year = "${it.groupValues[1]}${it.groupValues[2]}"
            yearGte = year
            yearLte = year
        }
        // "1995 to 1999"
        Regex("(19|20)(\\d{2}) to (19|20)(\\d{2})", RegexOption.IGNORE_CASE).find(query)?.let {
            yearGte = "${it.groupValues[1]}${it.groupValues[2]}"
            yearLte = "${it.groupValues[3]}${it.groupValues[4]}"
        }

        // --- Rating detection ---
        if (lower.contains("high rated") || lower.contains("top rated") || lower.contains("best") || lower.contains(" masterpiece") || lower.contains("award winning") || lower.contains("oscar")) {
            minRating = 7.0
            sortBy = "vote_average.desc"
        }
        if (lower.contains("good")) {
            if (minRating == null) minRating = 6.0
        }
        Regex("above (\\d(\\.\\d)?)", RegexOption.IGNORE_CASE).find(query)?.let {
            it.groupValues[1].toDoubleOrNull()?.let { r -> minRating = r }
        }
        Regex("rating (\\d(\\.\\d)?)", RegexOption.IGNORE_CASE).find(query)?.let {
            it.groupValues[1].toDoubleOrNull()?.let { r -> minRating = r }
        }

        // --- Runtime detection ---
        Regex("under (\\d+) (hour|hours)", RegexOption.IGNORE_CASE).find(query)?.let {
            it.groupValues[1].toIntOrNull()?.let { h -> maxRuntime = h * 60 }
        }
        Regex("under (\\d+) (min|minutes?)", RegexOption.IGNORE_CASE).find(query)?.let {
            it.groupValues[1].toIntOrNull()?.let { m -> maxRuntime = m }
        }
        Regex("less than (\\d+) (hour|hours)", RegexOption.IGNORE_CASE).find(query)?.let {
            it.groupValues[1].toIntOrNull()?.let { h -> maxRuntime = h * 60 }
        }
        Regex("less than (\\d+) (min|minutes?)", RegexOption.IGNORE_CASE).find(query)?.let {
            it.groupValues[1].toIntOrNull()?.let { m -> maxRuntime = m }
        }
        Regex("over (\\d+) (hour|hours)", RegexOption.IGNORE_CASE).find(query)?.let {
            it.groupValues[1].toIntOrNull()?.let { h -> minRuntime = h * 60 }
        }
        Regex("over (\\d+) (min|minutes?)", RegexOption.IGNORE_CASE).find(query)?.let {
            it.groupValues[1].toIntOrNull()?.let { m -> minRuntime = m }
        }
        Regex("more than (\\d+) (hour|hours)", RegexOption.IGNORE_CASE).find(query)?.let {
            it.groupValues[1].toIntOrNull()?.let { h -> minRuntime = h * 60 }
        }
        Regex("more than (\\d+) (min|minutes?)", RegexOption.IGNORE_CASE).find(query)?.let {
            it.groupValues[1].toIntOrNull()?.let { m -> minRuntime = m }
        }
        if (lower.contains(" short ") || lower.endsWith(" short") || lower == "short") {
            maxRuntime = 90
        }
        if (lower.contains(" long ") || lower.endsWith(" long") || lower == "long") {
            minRuntime = 120
        }

        // --- Keywords ---
        for (pattern in keywordPatterns) {
            if (pattern.containsMatchIn(query)) {
                val word = pattern.find(query)?.value?.lowercase()?.trim()
                if (word != null && word !in keywords) keywords.add(word)
            }
        }
        // Remove language-matched words from keywords (they're handled via with_original_language API param)
        val detectedLanguageWords = languageKeywords.keys.filter { langWord ->
            lower.contains(" $langWord ") || lower.startsWith("$langWord ") || lower.endsWith(" $langWord") || lower == langWord
        }
        keywords.removeAll { kw -> detectedLanguageWords.any { langWord -> kw.contains(langWord) } }

        // --- Director hint ---
        Regex("(?:by|directed by) ([A-Za-z\\s]+?)(?:\\s+from|\\s+in|\\s+with|\\$|$)", RegexOption.IGNORE_CASE).find(query)?.let {
            val director = it.groupValues[1].trim()
            if (director.length > 2) keywords.add("director:$director")
        }

        // Clean up query text for fallback search (remove parsed parts)
        var cleanQuery = query
            .replace(Regex("from the \\'?\\d{2}s", RegexOption.IGNORE_CASE), "")
            .replace(Regex("in the \\'?\\d{2}s", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b\\d{2}s\\b"), "")
            .replace(Regex("from \\'?\\d{4}", RegexOption.IGNORE_CASE), "")
            .replace(Regex("after \\'?\\d{4}", RegexOption.IGNORE_CASE), "")
            .replace(Regex("before \\'?\\d{4}", RegexOption.IGNORE_CASE), "")
            .replace(Regex("in \\'?\\d{4}", RegexOption.IGNORE_CASE), "")
            .replace(Regex("under \\'?\\d+ \\'?(hour|hours|min|minutes)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("by [A-Za-z\\s]+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("directed by [A-Za-z\\s]+", RegexOption.IGNORE_CASE), "")
            .trim()

        // Remove genre words from clean query
        genreKeywords.keys.forEach { g ->
            cleanQuery = cleanQuery.replace(Regex("\\b$g\\b", RegexOption.IGNORE_CASE), "")
        }
        cleanQuery = cleanQuery.replace(Regex("\\s+"), " ").trim()

        return SearchFilters(
            withGenres = genreId,
            yearGte = yearGte,
            yearLte = yearLte,
            minRating = minRating,
            minRuntime = minRuntime,
            maxRuntime = maxRuntime,
            keywords = keywords,
            language = languageCode,
            sortBy = sortBy,
            queryText = cleanQuery
        )
    }

    fun hasMeaningfulFilters(filters: SearchFilters): Boolean {
        return filters.withGenres != null ||
               filters.yearGte != null ||
               filters.yearLte != null ||
               filters.minRating != null ||
               filters.minRuntime != null ||
               filters.maxRuntime != null ||
               filters.language != null ||
               filters.keywords.isNotEmpty()
    }
}
