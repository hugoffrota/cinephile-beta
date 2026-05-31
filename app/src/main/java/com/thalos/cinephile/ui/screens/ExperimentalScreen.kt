package com.thalos.cinephile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.thalos.cinephile.MainViewModel
import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.data.remote.GenreMap
import com.thalos.cinephile.ui.components.MoviePoster
import com.thalos.cinephile.ui.components.AnimatedEmptyState
import com.thalos.cinephile.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExperimentalScreen(
    viewModel: MainViewModel,
    onMovieClick: (MovieEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<MovieEntity>>(emptyList()) }
    var searchExplanation by remember { mutableStateOf<String>("") }
    var resultCount by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RichBlack)
            .padding(16.dp)
    ) {
        Text(
            text = "Smart Search",
            style = MaterialTheme.typography.headlineMedium,
            color = Platinum
        )
        Text(
            text = "Type naturally, like \"Korean horror from the 90s\" or \"short comedies under 90 minutes\"",
            style = MaterialTheme.typography.bodyMedium,
            color = CadetGrey,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it; if (it.isEmpty()) { searchResults = emptyList(); searchExplanation = ""; resultCount = 0 } },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Try 'Korean horror' or 'sci fi from the 70s'...", color = CadetGrey) },
            leadingIcon = {
                IconButton(onClick = {
                    if (searchQuery.isNotBlank()) {
                        scope.launch {
                            isSearching = true
                            val (results, explanation) = viewModel.smartSearch(searchQuery)
                            searchResults = results
                            searchExplanation = explanation
                            resultCount = results.size
                            isSearching = false
                        }
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = if (searchQuery.isNotBlank()) UltraViolet else CadetGrey)
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = ""; searchExplanation = ""; searchResults = emptyList(); resultCount = 0 }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = CadetGrey)
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
                    val (results, explanation) = viewModel.smartSearch(searchQuery)
                    searchResults = results
                    searchExplanation = explanation
                    resultCount = results.size
                    isSearching = false
                }
            })
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Example chips
        if (searchQuery.isEmpty() && searchResults.isEmpty()) {
            Text("Examples:", style = MaterialTheme.typography.labelLarge, color = Platinum)
            Spacer(modifier = Modifier.height(8.dp))
            val examples = listOf(
                "Korean dramas",
                "90s horror",
                "Sci fi before 2000",
                "Short comedies",
                "High rated thrillers",
                "Japanese animation"
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                examples.forEach { example ->
                    SuggestionChip(
                        onClick = {
                            searchQuery = example
                            scope.launch {
                                isSearching = true
                                val (results, explanation) = viewModel.smartSearch(example)
                                searchResults = results
                                searchExplanation = explanation
                                resultCount = results.size
                                isSearching = false
                            }
                        },
                        label = { Text(example, color = CadetGrey) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = EerieBlack
                        )
                    )
                }
            }
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = UltraViolet)
            }
        } else if (searchQuery.isNotEmpty()) {
            if (searchExplanation.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = searchExplanation,
                        style = MaterialTheme.typography.labelLarge,
                        color = Amethyst
                    )
                    if (resultCount > 0) {
                        Text(
                            text = "$resultCount results",
                            style = MaterialTheme.typography.labelMedium,
                            color = CadetGrey
                        )
                    }
                }
            }

            if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults, key = { it.tmdbId }) { movie ->
                        CompactMovieCard(movie = movie, onClick = { onMovieClick(movie) })
                    }
                }
            } else if (!isSearching) {
                AnimatedEmptyState(
                    message = "No results for \"$searchQuery\"",
                    subMessage = "Try a different query or check your filters"
                )
            }
        }
    }
}
