package com.thalos.cinephile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.ui.theme.*

@Composable
fun MovieCard(
    movie: MovieEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    explanation: String? = null,
    matchPercent: Int? = null,
    showRating: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = EerieBlack),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Poster
            AsyncImage(
                model = movie.posterPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(90.dp)
                    .height(135.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Gunmetal)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Platinum,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (onDismiss != null) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Not interested",
                                tint = CadetGrey,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${String.format("%.1f", movie.voteAverage)}", color = Amber, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(movie.releaseDate?.take(4) ?: "", color = CadetGrey, style = MaterialTheme.typography.bodySmall)
                    if (movie.runtime != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${movie.runtime}m", color = CadetGrey, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (showRating && movie.userRating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = UltraViolet, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${String.format("%.1f", movie.userRating)}", color = UltraViolet, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (!movie.overview.isNullOrBlank()) {
                    Text(
                        text = movie.overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = CadetGrey,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (!explanation.isNullOrBlank()) {
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = Amethyst,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (matchPercent != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    MatchBadge(percent = matchPercent)
                }
            }
        }
    }
}

@Composable
fun MatchBadge(percent: Int) {
    val color = when {
        percent >= 85 -> Color(0xFF22C55E)
        percent >= 70 -> Amber
        else -> CadetGrey
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${percent}% match",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
