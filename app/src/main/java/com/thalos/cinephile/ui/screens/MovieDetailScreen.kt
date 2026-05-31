package com.thalos.cinephile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.FlowRow
import coil.compose.AsyncImage
import com.thalos.cinephile.MainViewModel
import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.ui.components.RatingDialog
import com.thalos.cinephile.ui.theme.*
import com.thalos.cinephile.ui.util.hapticLight

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movie: MovieEntity,
    explanation: String?,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onMovieClick: (MovieEntity) -> Unit = {}
) {
    var showRatingDialog by remember { mutableStateOf(false) }
    val userMovies by viewModel.userMovies.collectAsState(initial = emptyList())
    val watchlist by viewModel.watchlist.collectAsState(initial = emptyList())
    val similar by viewModel.similarMovies.collectAsState()
    val trailerKey by viewModel.trailerKey.collectAsState()
    val watchProviders by viewModel.watchProviders.collectAsState()
    val userRating = remember(userMovies, movie.tmdbId) {
        userMovies.find { it.tmdbId == movie.tmdbId }?.userRating
    }
    val isWatchlisted = remember(watchlist, movie.tmdbId) {
        watchlist.any { it.tmdbId == movie.tmdbId }
    }
    val context = LocalContext.current

    LaunchedEffect(movie.tmdbId) {
        viewModel.fetchSimilarMovies(movie.tmdbId)
        viewModel.fetchTrailer(movie.tmdbId)
        viewModel.fetchWatchProviders(movie.tmdbId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        movie.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Platinum
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Platinum
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareText = "Check out ${movie.title} (${movie.releaseDate?.take(4) ?: ""}) on TMDb!"
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Platinum)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RichBlack)
            )
        },
        containerColor = RichBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Backdrop with gradient
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                AsyncImage(
                    model = movie.backdropPath ?: movie.posterPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    RichBlack.copy(alpha = 0.0f),
                                    RichBlack.copy(alpha = 0.7f),
                                    RichBlack
                                )
                            )
                        )
                )
                // Trailer play button
                if (trailerKey != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://www.youtube.com/watch?v=$trailerKey")
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = UltraViolet.copy(alpha = 0.9f)
                            )
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Watch trailer",
                                modifier = Modifier.size(32.dp),
                                tint = Platinum
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Platinum
                )
                if (!movie.originalTitle.isNullOrBlank() && movie.originalTitle != movie.title) {
                    Text(
                        text = "Original: ${movie.originalTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CadetGrey
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${String.format("%.1f", movie.voteAverage)}", color = Amber, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("${movie.releaseDate?.take(4) ?: "Unknown"}", color = CadetGrey)
                    if (movie.runtime != null) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("${movie.runtime} min", color = CadetGrey)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showRatingDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = UltraViolet),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (userRating != null) "Rated ${String.format("%.1f", userRating)}" else "Rate Movie")
                    }
                    OutlinedButton(
                        onClick = {
                            hapticLight(context)
                            viewModel.markWatched(movie)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (userRating != null) "Watched" else "Mark Watched")
                    }
                    OutlinedIconButton(
                        onClick = {
                            hapticLight(context)
                            viewModel.toggleWatchlist(movie)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isWatchlisted) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isWatchlisted) "Remove from watchlist" else "Add to watchlist",
                            tint = if (isWatchlisted) UltraViolet else CadetGrey
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (movie.genres.isNotBlank()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        movie.genres.split(",").forEach { genre ->
                            GenreChip(genre.trim())
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Watch Providers
                if (watchProviders.isNotEmpty()) {
                    Text("Watch on", style = MaterialTheme.typography.titleMedium, color = Platinum)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        watchProviders.forEach { provider ->
                            ProviderChip(provider) {
                                openStreamingApp(context, provider)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (isInTheaters(movie.releaseDate)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Rose.copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎬", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Only in Theaters",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Rose
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Not yet available on streaming or digital. Check your local theaters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CadetGrey
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (!explanation.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = UltraViolet.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Why this match?", style = MaterialTheme.typography.titleMedium, color = Amethyst)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(explanation, style = MaterialTheme.typography.bodyMedium, color = Platinum)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (!movie.overview.isNullOrBlank()) {
                    Text("Overview", style = MaterialTheme.typography.titleMedium, color = Platinum)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(movie.overview, style = MaterialTheme.typography.bodyLarge, color = CadetGrey, lineHeight = 24.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (!movie.director.isNullOrBlank()) {
                    Text("Director", style = MaterialTheme.typography.titleMedium, color = Platinum)
                    Text(movie.director, style = MaterialTheme.typography.bodyLarge, color = CadetGrey)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (movie.cast.isNotBlank()) {
                    Text("Cast", style = MaterialTheme.typography.titleMedium, color = Platinum)
                    Text(movie.cast.replace(",", ", "), style = MaterialTheme.typography.bodyLarge, color = CadetGrey)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Similar Movies
                if (similar.isNotEmpty()) {
                    Text("More Like This", style = MaterialTheme.typography.titleMedium, color = Platinum)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(similar, key = { it.tmdbId }) { simMovie ->
                            SimilarMovieCard(
                                movie = simMovie,
                                onClick = { onMovieClick(simMovie) }
                            )
                        }
                    }
                }
            }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Powered by TMDb. This product uses the TMDb API but is not endorsed or certified by TMDb.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CadetGrey.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
        }
    }

    if (showRatingDialog) {
        RatingDialog(
            movieTitle = movie.title,
            currentRating = userRating,
            onDismiss = { showRatingDialog = false },
            onRate = { rating ->
                viewModel.rateMovie(movie, rating)
                showRatingDialog = false
            }
        )
    }
}


@Composable
fun ProviderChip(name: String, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Platinum.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = Platinum)
    }
}
@Composable
fun SimilarMovieCard(movie: MovieEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = movie.posterPath,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(120.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Gunmetal)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = movie.title,
            style = MaterialTheme.typography.bodySmall,
            color = Platinum,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Amber, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text("${String.format("%.1f", movie.voteAverage)}", color = CadetGrey, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun GenreChip(genre: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(UltraViolet.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(genre, style = MaterialTheme.typography.labelLarge, color = Amethyst)
    }
}


fun openStreamingApp(context: android.content.Context, providerName: String) {
    val packageMap = mapOf(
        "Netflix" to "com.netflix.mediaclient",
        "Amazon Prime Video" to "com.amazon.avod.thirdpartyclient",
        "Max" to "com.hbo.hbonow",
        "HBO Max" to "com.hbo.hbonow",
        "Disney Plus" to "com.disney.disneyplus",
        "Hulu" to "com.hulu.plus",
        "Apple TV" to "com.apple.atve.android.appletv",
        "Apple TV Plus" to "com.apple.atve.android.appletv",
        "Paramount Plus" to "com.cbs.app",
        "Paramount+" to "com.cbs.app",
        "Peacock" to "com.peacocktv.peacockandroid",
        "Crunchyroll" to "com.crunchyroll.crunchyroid",
        "YouTube" to "com.google.android.youtube",
        "YouTube Premium" to "com.google.android.youtube",
        "Starz" to "com.bydeluxe.d3.android.program.starz",
        "Showtime" to "com.showtime.standalone",
        "AMC+" to "com.amc.amcandroid",
        "Mubi" to "com.mubi.android",
        "Criterion Channel" to "com.criterionchannel",
        "Tubi" to "com.tubitv",
        "Pluto TV" to "tv.pluto.android",
        "Shudder" to "com.shudder.google.tv",
        "Sling TV" to "com.sling",
        "fuboTV" to "com.fubo.tv",
        "BritBox" to "com.britbox.us",
        "Discovery Plus" to "com.discovery.discoveryplus.android",
        "ESPN" to "com.espn.score_center",
    )

    val packageName = packageMap.entries.find { providerName.contains(it.key, ignoreCase = true) }?.value
        ?: packageMap[providerName]

    if (packageName != null) {
        val pm = context.packageManager
        try {
            pm.getPackageInfo(packageName, 0)
            // App is installed — launch it
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: PackageManager.NameNotFoundException) {
            // App not installed — fall through to Play Store
        }
        // Open Play Store
        try {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("market://details?id=$packageName")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: android.content.ActivityNotFoundException) {
            // No Play Store — open web browser
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}

fun isInTheaters(releaseDate: String?): Boolean {
    if (releaseDate.isNullOrBlank()) return false
    return try {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val date = java.time.LocalDate.parse(releaseDate, formatter)
        val today = java.time.LocalDate.now()
        // In theaters if: releasing today/in future, OR released within last 90 days and no providers
        // But here we just check date window; caller checks providers separately
        !date.isBefore(today.minusDays(90))
    } catch (_: Exception) {
        false
    }
}
