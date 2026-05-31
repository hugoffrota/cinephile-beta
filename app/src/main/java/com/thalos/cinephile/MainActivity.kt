package com.thalos.cinephile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thalos.cinephile.data.local.MovieEntity
import com.thalos.cinephile.ui.screens.HomeScreen
import com.thalos.cinephile.ui.screens.MovieDetailScreen
import com.thalos.cinephile.ui.screens.OnboardingScreen
import com.thalos.cinephile.ui.theme.CinephileTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CinephileTheme {
                val uiState by viewModel.uiState.collectAsState()
                val recommendations by viewModel.recommendations.collectAsState()
                var selectedMovie by remember { mutableStateOf<MovieEntity?>(null) }
                var selectedExplanation by remember { mutableStateOf<String?>(null) }

                BackHandler(enabled = selectedMovie != null) {
                    selectedMovie = null
                    selectedExplanation = null
                }

                when (val state = uiState) {
                    is MainViewModel.UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    is MainViewModel.UiState.Onboarding -> {
                        OnboardingScreen(
                            viewModel = viewModel,
                            onComplete = {}
                        )
                    }
                    is MainViewModel.UiState.Ready -> {
                        if (selectedMovie != null) {
                            MovieDetailScreen(
                                movie = selectedMovie!!,
                                explanation = selectedExplanation,
                                viewModel = viewModel,
                                onBack = { selectedMovie = null; selectedExplanation = null },
                                onMovieClick = { movie ->
                                    selectedMovie = movie
                                    selectedExplanation = null
                                }
                            )
                        } else {
                            HomeScreen(
                                viewModel = viewModel,
                                onMovieClick = { movie ->
                                    val scored = recommendations.find { it.movie.id == movie.id }
                                    selectedMovie = movie
                                    selectedExplanation = scored?.explanation
                                },
                                onReset = {
                                    selectedMovie = null
                                    selectedExplanation = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
