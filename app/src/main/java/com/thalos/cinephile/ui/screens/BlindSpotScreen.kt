package com.thalos.cinephile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thalos.cinephile.MainViewModel
import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.domain.engine.BlindSpotAnalyzer
import com.thalos.cinephile.ui.components.AnimatedEmptyState
import com.thalos.cinephile.ui.components.MovieCard
import com.thalos.cinephile.ui.theme.*

@Composable
fun BlindSpotScreen(
    viewModel: MainViewModel,
    onMovieClick: (MovieEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val blindSpots by viewModel.blindSpots.collectAsState()
    val isLoading by viewModel.isLoadingBlindSpots.collectAsState()
    val userMovies by viewModel.userMovies.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        viewModel.loadBlindSpots()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RichBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Blind Spot",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Platinum
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "High-rated films in eras and genres you have barely rated — kept separate from For You when possible.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CadetGrey
                    )
                }
                IconButton(onClick = { viewModel.loadBlindSpots() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Amethyst)
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = UltraViolet)
                }
            }
        } else if (userMovies.size < 2) {
            item {
                AnimatedEmptyState(
                    message = "Not enough taste data yet",
                    subMessage = "Import or rate a few films first. Blind Spot needs at least a small watched history before it can call out your cinematic homework. Yes, homework. Fun homework.",
                    modifier = Modifier.padding(top = 48.dp)
                )
            }
        } else if (blindSpots.isEmpty()) {
            item {
                AnimatedEmptyState(
                    message = "No blind spots found",
                    subMessage = "Your candidate pool may be too small. Refresh For You first so Cinephile has more films to compare against.",
                    modifier = Modifier.padding(top = 48.dp)
                )
            }
        } else {
            item {
                Text(
                    text = "${blindSpots.size} gaps found",
                    style = MaterialTheme.typography.labelLarge,
                    color = Amethyst
                )
            }
            items(blindSpots, key = { "${it.type}_${it.title}" }) { blindSpot ->
                BlindSpotCard(
                    blindSpot = blindSpot,
                    onMovieClick = onMovieClick
                )
            }
        }
    }
}

@Composable
private fun BlindSpotCard(
    blindSpot: BlindSpotAnalyzer.BlindSpot,
    onMovieClick: (MovieEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EerieBlack),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when (blindSpot.type) {
                                BlindSpotAnalyzer.BlindSpotType.ERA -> "Era gap"
                                BlindSpotAnalyzer.BlindSpotType.GENRE -> "Genre gap"
                            },
                            color = Platinum
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(containerColor = UltraViolet.copy(alpha = 0.20f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Score ${"%.0f".format(blindSpot.score)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CadetGrey,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Open",
                    tint = Amethyst
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = blindSpot.title,
                style = MaterialTheme.typography.titleLarge,
                color = Platinum,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = blindSpot.reason.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = CadetGrey
            )
            if (blindSpot.sampleTitles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (expanded) "Five picks for this style" else "Tap to open: ${blindSpot.sampleTitles.take(5).joinToString(" • ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Amethyst
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                blindSpot.sampleMovies.take(5).forEach { movie ->
                    MovieCard(
                        movie = movie,
                        onClick = { onMovieClick(movie) },
                        explanation = "Blind Spot pick for ${blindSpot.title}",
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}
