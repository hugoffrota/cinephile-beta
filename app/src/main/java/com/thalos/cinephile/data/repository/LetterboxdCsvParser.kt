package com.thalos.cinephile.data.repository

import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

class LetterboxdCsvParser {

    data class LetterboxdEntry(
        val title: String,
        val year: Int?,
        val rating: Double?, // 0.5-5.0
        val rewatch: Boolean,
        val watchedDate: String?,
        val tmdbId: Int? = null
    )

    fun parse(inputStream: java.io.InputStream): List<LetterboxdEntry> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines()
        if (lines.isEmpty()) return emptyList()

        val header = lines.first().split(",").map { it.trim().replace("\"", "").lowercase() }

        // Flexible header matching — handles both export formats
        val titleIdx = header.indexOfFirst { it == "name" || it == "title" }
        val yearIdx = header.indexOfFirst { it == "year" }
        val ratingIdx = header.indexOfFirst { it == "rating" }
        val rewatchIdx = header.indexOfFirst { it == "rewatch" }
        val dateIdx = header.indexOfFirst { it == "watched date" || it == "date" }

        if (titleIdx == -1 || yearIdx == -1) {
            return emptyList()
        }

        return lines.drop(1).mapNotNull { line ->
            try {
                val cols = parseCsvLine(line)
                val title = cols.getOrNull(titleIdx)?.trim() ?: return@mapNotNull null
                if (title.isBlank()) return@mapNotNull null

                val year = cols.getOrNull(yearIdx)?.toIntOrNull()
                val rating = if (ratingIdx != -1) cols.getOrNull(ratingIdx)?.toDoubleOrNull() else null
                val rewatch = if (rewatchIdx != -1) {
                    cols.getOrNull(rewatchIdx)?.equals("Yes", ignoreCase = true) == true
                } else false
                val date = if (dateIdx != -1) cols.getOrNull(dateIdx)?.trim() else null

                LetterboxdEntry(title, year, rating, rewatch, date)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(char)
            }
        }
        result.add(sb.toString().trim())
        return result
    }
}
