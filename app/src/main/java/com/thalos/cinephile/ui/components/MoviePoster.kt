package com.thalos.cinephile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.thalos.cinephile.ui.theme.Gunmetal
import com.thalos.cinephile.ui.theme.Platinum

@Composable
fun MoviePoster(
    url: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Gunmetal), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
            error = rememberAsyncImagePainter(
                model = "https://placehold.co/200x300/1A1A2E/7C3AED?text=${title.take(2)}"
            )
        )
    }
}
