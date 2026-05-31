package com.thalos.cinephile.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.thalos.cinephile.MainViewModel
import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.domain.engine.RecommendationEngine
import com.thalos.cinephile.ui.components.MovieCard
import com.thalos.cinephile.ui.components.TrendingRow
import com.thalos.cinephile.ui.components.AnimatedEmptyState
import com.thalos.cinephile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onMovieClick: (MovieEntity) -> Unit,
    onReset: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val recommendations by viewModel.recommendations.collectAsState()
    val isLoadingRecs by viewModel.isLoadingRecs.collectAsState()
    val trending by viewModel.trendingMovies.collectAsState()
    val upcoming by viewModel.upcomingMovies.collectAsState()
    val dismissedIds by viewModel.dismissedMovieIds.collectAsState()
    val userMovies by viewModel.userMovies.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cinephile",
                        style = MaterialTheme.typography.titleLarge,
                        color = Platinum
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RichBlack,
                    titleContentColor = Platinum
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = EerieBlack) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Recommend, contentDescription = null) },
                    label = { Text("For You") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = UltraViolet,
                        selectedTextColor = Amethyst,
                        indicatorColor = UltraViolet.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                    label = { Text("Discover") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = UltraViolet,
                        selectedTextColor = Amethyst,
                        indicatorColor = UltraViolet.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    label = { Text("Experiment") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = UltraViolet,
                        selectedTextColor = Amethyst,
                        indicatorColor = UltraViolet.copy(alpha = 0.15f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Profile") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = UltraViolet,
                        selectedTextColor = Amethyst,
                        indicatorColor = UltraViolet.copy(alpha = 0.15f)
                    )
                )
            }
        },
        containerColor = RichBlack
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "tab_switch"
        ) { tab ->
            when (tab) {
                0 -> RecommendationsTab(
                    recommendations = recommendations,
                    trending = trending,
                    upcoming = upcoming,
                    dismissedIds = dismissedIds,
                    isLoading = isLoadingRecs,
                    hasUserMovies = userMovies.isNotEmpty(),
                    hideTheaterOnly = viewModel.hideTheaterOnly.collectAsState().value,
                    onToggleHideTheaterOnly = { viewModel.toggleHideTheaterOnly() },
                    hideObscure = viewModel.hideObscure.collectAsState().value,
                    onToggleHideObscure = { viewModel.toggleHideObscure() },
                    onMovieClick = onMovieClick,
                    onRefresh = { viewModel.loadRecommendations() },
                    onDismiss = { viewModel.dismissRecommendation(it) },
                    onUndoDismiss = { viewModel.undoDismiss(it) },
                    onSurpriseMe = { viewModel.shuffleRecommendations() },
                    modifier = Modifier.padding(padding)
                )
                1 -> DiscoverScreen(
                    viewModel = viewModel,
                    onMovieClick = onMovieClick,
                    modifier = Modifier.padding(padding)
                )
                2 -> ExperimentalScreen(
                    viewModel = viewModel,
                    onMovieClick = onMovieClick,
                    modifier = Modifier.padding(padding)
                )
                3 -> ProfileScreen(
                    viewModel = viewModel,
                    onMovieClick = onMovieClick,
                    onReset = onReset,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsTab(
    recommendations: List<RecommendationEngine.ScoredMovie>,
    trending: List<MovieEntity>,
    upcoming: List<MovieEntity>,
    dismissedIds: Set<Int>,
    isLoading: Boolean,
    hasUserMovies: Boolean = false,
    hideTheaterOnly: Boolean = false,
    onToggleHideTheaterOnly: () -> Unit = {},
    hideObscure: Boolean = false,
    onToggleHideObscure: () -> Unit = {},
    onMovieClick: (MovieEntity) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: (Int) -> Unit,
    onUndoDismiss: (Int) -> Unit,
    onSurpriseMe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredRecs = recommendations
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RichBlack,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = EerieBlack,
                    contentColor = Platinum,
                    actionColor = Amethyst,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + padding.calculateBottomPadding())
        ) {
            // Surprise Me Button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onSurpriseMe,
                        colors = ButtonDefaults.buttonColors(containerColor = UltraViolet),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Surprise Me")
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amethyst)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = hideTheaterOnly,
                        onClick = onToggleHideTheaterOnly,
                        label = { Text("Hide in theaters") },
                        leadingIcon = if (hideTheaterOnly) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UltraViolet.copy(alpha = 0.25f),
                            selectedLabelColor = Platinum,
                            containerColor = EerieBlack,
                            labelColor = CadetGrey
                        )
                    )
                    FilterChip(
                        selected = hideObscure,
                        onClick = onToggleHideObscure,
                        label = { Text("Hide obscure") },
                        leadingIcon = if (hideObscure) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UltraViolet.copy(alpha = 0.25f),
                            selectedLabelColor = Platinum,
                            containerColor = EerieBlack,
                            labelColor = CadetGrey
                        )
                    )
                }
            }

            // Trending Section
            if (trending.isNotEmpty()) {
                item {
                    TrendingRow(
                        title = "Trending This Week",
                        movies = trending,
                        onMovieClick = onMovieClick,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // Upcoming Section
            if (upcoming.isNotEmpty()) {
                item {
                    TrendingRow(
                        title = "Coming Soon",
                        movies = upcoming,
                        onMovieClick = onMovieClick,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // Recommendations Header
            item {
                Text(
                    text = "Recommended for You",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Platinum,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                isLoading -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = UltraViolet, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Analyzing your taste...", color = CadetGrey)
                        }
                    }
                }
                filteredRecs.isEmpty() -> {
                    item {
                        val emptyMessage = if (hasUserMovies) {
                            Pair("Building recommendations...", "Candidate movies are loading. Pull to refresh in a moment.")
                        } else {
                            Pair("No recommendations yet", "Import your Letterboxd ratings or rate some movies")
                        }
                        AnimatedEmptyState(
                            message = emptyMessage.first,
                            subMessage = emptyMessage.second,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
                else -> {
                    items(filteredRecs, key = { it.movie.tmdbId }) { scored ->
                        MovieCard(
                            movie = scored.movie,
                            onClick = { onMovieClick(scored.movie) },
                            explanation = scored.explanation,
                            matchPercent = (scored.totalScore * 100).toInt(),
                            onDismiss = {
                                onDismiss(scored.movie.tmdbId)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "${scored.movie.title} dismissed",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onUndoDismiss(scored.movie.tmdbId)
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
