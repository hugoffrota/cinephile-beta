package com.thalos.cinephile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.thalos.cinephile.MainViewModel
import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.data.remote.GenreMap
import com.thalos.cinephile.ui.components.MoviePoster
import com.thalos.cinephile.ui.components.AnimatedEmptyState
import com.thalos.cinephile.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    viewModel: MainViewModel,
    onMovieClick: (MovieEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<MovieEntity>>(emptyList()) }
    var selectedGenre by remember { mutableStateOf<Int?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("popularity") }
    var yearFrom by remember { mutableStateOf(1900f) }
    var yearTo by remember { mutableStateOf(2026f) }
    var maxRuntime by remember { mutableStateOf(300f) }

    val scope = rememberCoroutineScope()
    val discoverMovies by viewModel.discoverMovies.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchDiscoverMovies(
            genreId = selectedGenre,
            sortBy = sortBy,
            yearFrom = yearFrom.toInt(),
            yearTo = yearTo.toInt(),
            maxRuntime = maxRuntime.toInt()
        )
    }

    Column(modifier = modifier.fillMaxSize().background(RichBlack)) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search movies by title...", color = CadetGrey) },
            leadingIcon = {
                IconButton(onClick = {
                    if (searchQuery.isNotBlank()) {
                        scope.launch {
                            isSearching = true
                            searchResults = viewModel.searchMovies(searchQuery)
                            isSearching = false
                        }
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = if (searchQuery.isNotBlank()) UltraViolet else CadetGrey)
                }
            },
            trailingIcon = {
                Row {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = CadetGrey)
                        }
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters", tint = if (showFilters || selectedGenre != null) UltraViolet else CadetGrey)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UltraViolet,
                unfocusedBorderColor = Gunmetal,
                focusedContainerColor = EerieBlack,
                unfocusedContainerColor = EerieBlack
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                scope.launch {
                    isSearching = true
                    searchResults = viewModel.searchMovies(searchQuery)
                    isSearching = false
                }
            })
        )

        // Filters panel
        AnimatedVisibility(visible = showFilters, enter = fadeIn(), exit = fadeOut()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Sort
                Text("Sort by", style = MaterialTheme.typography.labelLarge, color = Platinum)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sorts = listOf("popularity" to "Popularity", "rating" to "Rating", "year" to "Year", "runtime" to "Runtime")
                    items(sorts) { (key, label) ->
                        GenreFilterChip(
                            label = label,
                            selected = sortBy == key,
                            onClick = { sortBy = key }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Year range
                Text("Year: ${yearFrom.toInt()} - ${yearTo.toInt()}", style = MaterialTheme.typography.labelLarge, color = Platinum)
                RangeSlider(
                    value = yearFrom..yearTo,
                    onValueChange = { yearFrom = it.start; yearTo = it.endInclusive },
                    valueRange = 1900f..2026f,
                    steps = 0,
                    colors = SliderDefaults.colors(
                        thumbColor = UltraViolet,
                        activeTrackColor = UltraViolet,
                        inactiveTrackColor = Gunmetal
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Runtime filter
                Text("Max Runtime: ${maxRuntime.toInt()} min", style = MaterialTheme.typography.labelLarge, color = Platinum)
                Slider(
                    value = maxRuntime,
                    onValueChange = { maxRuntime = it },
                    valueRange = 30f..300f,
                    steps = 26,
                    colors = SliderDefaults.colors(
                        thumbColor = UltraViolet,
                        activeTrackColor = UltraViolet,
                        inactiveTrackColor = Gunmetal
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Genre chips
                Text("Genre", style = MaterialTheme.typography.labelLarge, color = Platinum)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        GenreFilterChip(
                            label = "All",
                            selected = selectedGenre == null,
                            onClick = { selectedGenre = null }
                        )
                    }
                    items(GenreMap.all().entries.toList(), key = { it.key }) { (id, name) ->
                        GenreFilterChip(
                            label = name,
                            selected = selectedGenre == id,
                            onClick = { selectedGenre = if (selectedGenre == id) null else id }
                        )
                    }
                }
            }
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = UltraViolet)
            }
        } else if (searchQuery.isNotEmpty() && searchResults.isNotEmpty()) {
            val filtered = searchResults.filter { movie ->
                val year = movie.releaseDate?.take(4)?.toIntOrNull() ?: 0
                val genreMatch = selectedGenre == null || movie.genres.split(",").any { it.trim().toIntOrNull() == selectedGenre }
                val yearMatch = year == 0 || (year >= yearFrom.toInt() && year <= yearTo.toInt())
                val runtimeMatch = movie.runtime == null || movie.runtime <= maxRuntime.toInt()
                genreMatch && yearMatch && runtimeMatch
            }.let { list ->
                when (sortBy) {
                    "rating" -> list.sortedByDescending { it.voteAverage }
                    "year" -> list.sortedByDescending { it.releaseDate ?: "" }
                    "runtime" -> list.sortedByDescending { it.runtime ?: 0 }
                    else -> list.sortedByDescending { it.popularity }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.tmdbId }) { movie ->
                    CompactMovieCard(movie = movie, onClick = { onMovieClick(movie) })
                }
            }
        } else if (searchQuery.isNotEmpty() && searchResults.isEmpty() && !isSearching) {
            AnimatedEmptyState(
                message = "No results for \"$searchQuery\"",
                subMessage = "Try a different title or adjust filters"
            )
        } else {
            BrowseContent(
                viewModel = viewModel,
                onMovieClick = onMovieClick,
                selectedGenre = selectedGenre,
                onGenreSelected = {
                    selectedGenre = if (selectedGenre == it) null else it
                    viewModel.fetchDiscoverMovies(
                        genreId = if (selectedGenre == it) null else it,
                        sortBy = sortBy,
                        yearFrom = yearFrom.toInt(),
                        yearTo = yearTo.toInt(),
                        maxRuntime = maxRuntime.toInt()
                    )
                },
                yearFrom = yearFrom,
                yearTo = yearTo,
                maxRuntime = maxRuntime,
                sortBy = sortBy
            )
        }
    }
}

@Composable
fun GenreFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) UltraViolet else Gunmetal)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else CadetGrey,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun CompactMovieCard(movie: MovieEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EerieBlack)
    ) {
        Row(modifier = Modifier.height(100.dp)) {
            MoviePoster(
                url = movie.posterPath,
                title = movie.title,
                modifier = Modifier
                    .width(70.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Platinum,
                        maxLines = 1
                    )
                    Text(
                        text = "${movie.releaseDate?.take(4) ?: ""} \u2022 ${movie.voteAverage.takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CadetGrey
                    )
                }
                val genreNames = movie.genres.split(",").mapNotNull { it.toIntOrNull()?.let { id -> GenreMap.name(id) } }
                if (genreNames.isNotEmpty()) {
                    Text(
                        text = genreNames.take(3).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Amethyst
                    )
                }
            }
        }
    }
}

@Composable
fun BrowseContent(
    viewModel: MainViewModel,
    onMovieClick: (MovieEntity) -> Unit,
    selectedGenre: Int?,
    onGenreSelected: (Int?) -> Unit,
    yearFrom: Float = 1900f,
    yearTo: Float = 2026f,
    maxRuntime: Float = 300f,
    sortBy: String = "popularity"
) {
    val discoverMovies by viewModel.discoverMovies.collectAsState()
    val filteredDiscover = discoverMovies.filter { movie ->
        val year = movie.releaseDate?.take(4)?.toIntOrNull() ?: 0
        val genreMatch = selectedGenre == null || movie.genres.split(",").any { it.trim().toIntOrNull() == selectedGenre }
        val yearMatch = year == 0 || (year >= yearFrom.toInt() && year <= yearTo.toInt())
        val runtimeMatch = movie.runtime == null || movie.runtime <= maxRuntime.toInt()
        genreMatch && yearMatch && runtimeMatch
    }.let { list ->
        when (sortBy) {
            "rating" -> list.sortedByDescending { it.voteAverage }
            "year" -> list.sortedByDescending { it.releaseDate ?: "" }
            "runtime" -> list.sortedByDescending { it.runtime ?: 0 }
            else -> list.sortedByDescending { it.popularity }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Browse by Genre",
                    style = MaterialTheme.typography.titleLarge,
                    color = Platinum
                )
                if (selectedGenre != null) {
                    TextButton(onClick = { onGenreSelected(null) }) {
                        Text("Clear", color = Amethyst)
                    }
                }
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(GenreMap.all().entries.toList().take(6), key = { it.key }) { (id, name) ->
                    GenreBrowseCard(
                        genreName = name,
                        isSelected = selectedGenre == id,
                        onClick = { onGenreSelected(id) }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (selectedGenre != null) "${GenreMap.name(selectedGenre)} Movies" else "Trending Now",
                style = MaterialTheme.typography.titleLarge,
                color = Platinum,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item {
            if (filteredDiscover.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { viewModel.fetchDiscoverMovies(selectedGenre) },
                    colors = CardDefaults.cardColors(containerColor = UltraViolet.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Amethyst,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap to load trending movies from TMDb",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Amethyst
                        )
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(filteredDiscover, key = { it.tmdbId }) { movie ->
                        MoviePoster(
                            url = movie.posterPath,
                            title = movie.title,
                            modifier = Modifier
                                .width(130.dp)
                                .height(195.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onMovieClick(movie) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GenreBrowseCard(genreName: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(120.dp, 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = if (isSelected) {
                        listOf(UltraViolet, ElectricViolet)
                    } else {
                        listOf(UltraViolet.copy(alpha = 0.8f), ElectricViolet.copy(alpha = 0.4f))
                    }
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = genreName,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}
