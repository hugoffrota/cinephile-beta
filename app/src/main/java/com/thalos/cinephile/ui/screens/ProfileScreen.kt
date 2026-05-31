package com.thalos.cinephile.ui.screens

import androidx.compose.animation.core.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thalos.cinephile.MainViewModel
import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.data.remote.GenreMap
import com.thalos.cinephile.ui.components.MovieCard
import com.thalos.cinephile.ui.components.AnimatedEmptyState
import com.thalos.cinephile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onMovieClick: (MovieEntity) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userMovies by viewModel.userMovies.collectAsState(initial = emptyList())
    val watchlist by viewModel.watchlist.collectAsState(initial = emptyList())
    val userName by viewModel.userName.collectAsState(initial = "")
    val countryCode by viewModel.countryCode.collectAsState(initial = "BR")
    val importProgress by viewModel.importProgress.collectAsState()
    val dismissedIds by viewModel.dismissedMovieIds.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importLetterboxdCsv(it, context)
        }
    }

    var showResetDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val selectedSection by viewModel.profileSelectedSection.collectAsState()
    val selectedRating by viewModel.profileSelectedRating.collectAsState()

    val stats = remember(userMovies) { calculateStats(userMovies) }

    val displayedMovies = remember(userMovies, selectedRating) {
        val base = if (selectedRating != null) {
            userMovies.filter {
                it.userRating != null && kotlin.math.round(it.userRating).toInt() == selectedRating
            }
        } else userMovies
        base.sortedByDescending { it.userRating ?: 0.0 }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RichBlack),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(UltraViolet.copy(alpha = 0.2f), RichBlack)
                        )
                    )
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = UltraViolet,
                        modifier = Modifier.size(64.dp)
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Platinum
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = EerieBlack,
                            modifier = Modifier.background(EerieBlack)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Data", color = Platinum) },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Amethyst)
                                },
                                onClick = {
                                    showMenu = false
                                    val csv = viewModel.exportData(userMovies)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, csv)
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Cinephile Export")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share Export"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Return ${dismissedIds.size} dismissed", color = if (dismissedIds.isNotEmpty()) Platinum else CadetGrey.copy(alpha = 0.5f)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Undo, contentDescription = null, tint = if (dismissedIds.isNotEmpty()) Amethyst else CadetGrey.copy(alpha = 0.5f))
                                },
                                enabled = dismissedIds.isNotEmpty(),
                                onClick = {
                                    showMenu = false
                                    viewModel.clearAllDismissed()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh Streaming Data", color = Platinum) },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Amethyst)
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.refreshAllWatchProviders()
                                }
                            )
                            HorizontalDivider(color = Gunmetal)
                            DropdownMenuItem(
                                text = { Text("Reset All Data", color = Rose) },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Rose)
                                },
                                onClick = {
                                    showMenu = false
                                    showResetDialog = true
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (userName.orEmpty().isNotBlank()) "Hello, $userName" else "Your Profile",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Platinum
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${userMovies.size} movies rated • Avg ${"%.1f".format(stats.avgRating)} • ${watchlist.size} in watchlist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CadetGrey
                )
            }
        }

        // Stats cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.Star,
                    value = "${stats.fiveStarCount}",
                    label = "Top Tier",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.setProfileSection(0)
                        viewModel.setProfileRatingFilter(10)
                    }
                )
                StatCard(
                    icon = Icons.Default.Movie,
                    value = "${stats.totalWatched}",
                    label = "Watched",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.setProfileSection(0)
                        viewModel.setProfileRatingFilter(null)
                    }
                )
                StatCard(
                    icon = Icons.Default.Bookmark,
                    value = "${watchlist.size}",
                    label = "Watchlist",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.setProfileSection(1)
                        viewModel.setProfileRatingFilter(null)
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Rating distribution
        if (userMovies.any { it.userRating != null }) {
            item {
                Text(
                    text = "Rating Distribution",
                    style = MaterialTheme.typography.titleLarge,
                    color = Platinum,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                RatingDistributionChart(
                    movies = userMovies,
                    selectedRating = selectedRating,
                    onRatingSelected = { rating ->
                        viewModel.setProfileRatingFilter(if (selectedRating == rating) null else rating)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(140.dp)
                )
            }
            if (selectedRating != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing ${displayedMovies.size} rated $selectedRating/10",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Amethyst,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.setProfileRatingFilter(null) }) {
                            Text("Clear", color = Amethyst)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Favorite genres
        if (stats.genreCounts.isNotEmpty()) {
            item {
                Text(
                    text = "Top Genres",
                    style = MaterialTheme.typography.titleLarge,
                    color = Platinum,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stats.genreCounts.entries.sortedByDescending { it.value }.take(5).forEach { (genreId, count) ->
                        GenreBar(
                            genreName = GenreMap.name(genreId),
                            count = count,
                            maxCount = stats.genreCounts.values.maxOrNull() ?: 1
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // Section tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = selectedSection == 0,
                    onClick = { viewModel.setProfileSection(0) },
                    label = {
                        val label = if (selectedRating != null) "Rated $selectedRating (${displayedMovies.size})" else "Your Ratings (${userMovies.size})"
                        Text(label)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UltraViolet.copy(alpha = 0.2f),
                        selectedLabelColor = Amethyst
                    )
                )
                FilterChip(
                    selected = selectedSection == 1,
                    onClick = { viewModel.setProfileSection(1) },
                    label = { Text("Watchlist (${watchlist.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UltraViolet.copy(alpha = 0.2f),
                        selectedLabelColor = Amethyst
                    )
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Content based on selected section
        if (selectedSection == 0) {
            if (displayedMovies.isEmpty()) {
                item {
                    AnimatedEmptyState(
                        message = if (selectedRating != null) "No movies rated $selectedRating" else "No ratings yet",
                        subMessage = if (selectedRating != null) "Tap another bar in the chart" else "Import your Letterboxd diary to see your stats"
                    )
                }
            } else {
                    items(displayedMovies, key = { it.id }) { movie ->
                        MovieCard(
                            movie = movie,
                            showRating = true,
                            onClick = { onMovieClick(movie) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                if (watchlist.isEmpty()) {
                    item {
                        AnimatedEmptyState(
                            message = "Watchlist is empty",
                            subMessage = "Bookmark movies from recommendations or search"
                        )
                    }
                } else {
                    items(watchlist, key = { it.id }) { movie ->
                        MovieCard(
                            movie = movie,
                            onClick = { onMovieClick(movie) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }

        // Import & Version
        item {
            Spacer(modifier = Modifier.height(32.dp))
            if (importProgress.isImporting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { importProgress.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = UltraViolet,
                        trackColor = Gunmetal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = importProgress.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CadetGrey
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amethyst)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Letterboxd Diary")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cinephile v1.0.0-beta",
                style = MaterialTheme.typography.labelSmall,
                color = CadetGrey.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Everything?", color = Platinum) },
            text = { Text("This will delete all your ratings and recommendations. This cannot be undone.", color = CadetGrey) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onReset()
                    }
                ) {
                    Text("Reset", color = Rose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = CadetGrey)
                }
            },
            containerColor = EerieBlack
        )
    }
}

@Composable
fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = EerieBlack),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = UltraViolet, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = Platinum,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = CadetGrey)
        }
    }
}

@Composable
fun GenreBar(genreName: String, count: Int, maxCount: Int) {
    val animatedProgress = animateFloatAsState(
        targetValue = count.toFloat() / maxCount,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "genre_bar"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = genreName,
            style = MaterialTheme.typography.bodyMedium,
            color = Platinum,
            modifier = Modifier.width(100.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Gunmetal)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress.value)
                    .clip(RoundedCornerShape(4.dp))
                    .background(UltraViolet)
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            color = CadetGrey,
            modifier = Modifier.width(28.dp)
        )
    }
}

data class UserStats(
    val totalWatched: Int,
    val avgRating: Double,
    val fiveStarCount: Int,
    val rewatchCount: Int,
    val genreCounts: Map<Int, Int>
)

fun calculateStats(movies: List<MovieEntity>): UserStats {
    val rated = movies.filter { it.userRating != null }
    val avgRating = if (rated.isNotEmpty()) rated.map { it.userRating!! }.average() else 0.0
    val topTier = rated.count { it.userRating!! >= 9.0 }
    val rewatches = movies.count { it.rewatch }

    val genreCounts = mutableMapOf<Int, Int>()
    movies.forEach { movie ->
        movie.genres.split(",").forEach { genreStr ->
            genreStr.toIntOrNull()?.let { genreCounts[it] = genreCounts.getOrDefault(it, 0) + 1 }
        }
    }

    return UserStats(
        totalWatched = movies.size,
        avgRating = avgRating,
        fiveStarCount = topTier,
        rewatchCount = rewatches,
        genreCounts = genreCounts
    )
}

@Composable
fun RatingDistributionChart(
    movies: List<MovieEntity>,
    selectedRating: Int?,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val distribution = remember(movies) {
        (1..10).associateWith { rating ->
            movies.count { it.userRating != null && kotlin.math.round(it.userRating!!).toInt() == rating }
        }
    }
    val maxCount = distribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Row(
        modifier = modifier.height(180.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        distribution.forEach { (rating, count) ->
            val animatedHeight by animateFloatAsState(
                targetValue = count.toFloat() / maxCount,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label = "bar_$rating"
            )
            val isSelected = selectedRating == rating
            val barColor = when {
                isSelected -> UltraViolet
                rating >= 8 -> Emerald
                rating >= 5 -> Amber
                else -> Rose
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = { onRatingSelected(rating) })
            ) {
                // Count label — fixed height so it never gets clipped
                Box(
                    modifier = Modifier.height(20.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (count > 0) {
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) UltraViolet else Amethyst
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                // Bar area — fixed height so proportions are consistent
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(animatedHeight.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$rating",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Platinum else CadetGrey,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
