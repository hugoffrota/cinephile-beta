package com.thalos.cinephile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.thalos.cinephile.MainViewModel
import com.thalos.cinephile.ui.components.CinematicBackground
import com.thalos.cinephile.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val apiKey = "proxy"
    var userName by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val importProgress by viewModel.importProgress.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri = it }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        CinematicBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                0 -> WelcomeStep(onNext = { step = 1 })
                1 -> NameStep(
                    userName = userName,
                    onNameChange = { userName = it },
                    onNext = { step = 2 }
                )
                2 -> ImportStep(
                    selectedUri = selectedUri,
                    onPickFile = { filePicker.launch("*/*") },
                    onSkip = {
                        viewModel.completeOnboarding(apiKey, userName)
                        viewModel.fetchCandidates(apiKey)
                        onComplete()
                    },
                    onImport = {
                        selectedUri?.let { uri ->
                            viewModel.completeOnboarding(apiKey, userName)
                            viewModel.importLetterboxdCsv(uri, context.applicationContext as android.app.Application, apiKey)
                        }
                    },
                    importProgress = importProgress,
                    onDone = onComplete
                )
            }
        }
    }
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Movie,
            contentDescription = null,
            tint = UltraViolet,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Cinephile",
            style = MaterialTheme.typography.displayLarge,
            color = Platinum
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your personal movie recommender.\nPowered by your taste.",
            style = MaterialTheme.typography.bodyLarge,
            color = CadetGrey,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(containerColor = UltraViolet)
        ) {
            Text("Get Started", modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun ApiKeyStep(apiKey: String, onApiKeyChange: (String) -> Unit, onNext: () -> Unit) {
    var testState by remember { mutableStateOf<TestState>(TestState.Idle) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.VpnKey,
            contentDescription = null,
            tint = UltraViolet,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "TMDb API Key",
            style = MaterialTheme.typography.headlineLarge,
            color = Platinum
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Cinephile uses a secure Cloudflare proxy for TMDb movie data.\nNo personal API key is needed for this beta.",
            style = MaterialTheme.typography.bodyMedium,
            color = CadetGrey,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                onApiKeyChange(it)
                testState = TestState.Idle
            },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UltraViolet,
                focusedLabelColor = Amethyst,
                cursorColor = UltraViolet,
                errorBorderColor = Cinnabar,
                errorLabelColor = Cinnabar
            ),
            isError = testState is TestState.Error,
            supportingText = {
                when (val s = testState) {
                    is TestState.Error -> Text(s.message, color = Cinnabar)
                    is TestState.Success -> Text("Key valid!", color = Emerald)
                    else -> {}
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        testState = TestState.Testing
                        try {
                            val api = com.thalos.cinephile.data.remote.TmdbApi.create()
                            api.getMovieDetails(550, apiKey)
                            testState = TestState.Success
                        } catch (e: Exception) {
                            testState = TestState.Error(e.message ?: "Network error")
                        }
                    }
                },
                enabled = apiKey.length >= 20 && testState != TestState.Testing,
                modifier = Modifier.weight(1f)
            ) {
                if (testState == TestState.Testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = UltraViolet
                    )
                } else {
                    Text("Test Key")
                }
            }
            Button(
                onClick = onNext,
                enabled = apiKey.length >= 20,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = UltraViolet)
            ) {
                Text("Continue")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onNext) {
            Text("Skip for now", color = CadetGrey)
        }
    }
}

sealed class TestState {
    object Idle : TestState()
    object Testing : TestState()
    object Success : TestState()
    data class Error(val message: String) : TestState()
}

@Composable
fun NameStep(userName: String, onNameChange: (String) -> Unit, onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = UltraViolet,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "What should we call you?",
            style = MaterialTheme.typography.headlineLarge,
            color = Platinum
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = userName,
            onValueChange = onNameChange,
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UltraViolet,
                focusedLabelColor = Amethyst,
                cursorColor = UltraViolet
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNext,
            enabled = userName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = UltraViolet)
        ) {
            Text("Continue")
        }
    }
}

@Composable
fun ImportStep(
    selectedUri: Uri?,
    onPickFile: () -> Unit,
    onSkip: () -> Unit,
    onImport: () -> Unit,
    importProgress: MainViewModel.ImportProgress,
    onDone: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (importProgress.isImporting) {
            CircularProgressIndicator(color = UltraViolet, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = importProgress.message,
                style = MaterialTheme.typography.bodyLarge,
                color = Platinum,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { importProgress.progress },
                modifier = Modifier.fillMaxWidth(0.8f),
                color = UltraViolet,
                trackColor = Gunmetal
            )
        } else if (importProgress.message.contains("Matched") || importProgress.message.contains("cached")) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Emerald,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = importProgress.message,
                style = MaterialTheme.typography.bodyLarge,
                color = Platinum,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald)
            ) {
                Text("Go to Recommendations")
            }
        } else {
            Icon(
                imageVector = Icons.Default.UploadFile,
                contentDescription = null,
                tint = UltraViolet,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Import your Letterboxd",
                style = MaterialTheme.typography.headlineLarge,
                color = Platinum
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Export your diary from Letterboxd Settings \u2192 Export Data, then upload ratings.csv",
                style = MaterialTheme.typography.bodyMedium,
                color = CadetGrey,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (selectedUri != null) {
                Text(
                    text = "Selected: ${selectedUri.lastPathSegment ?: "file"}",
                    color = Amethyst,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = UltraViolet)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedUri != null) "Choose different file" else "Select ratings.csv")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedUri != null) {
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                ) {
                    Text("Import & Match")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now", color = CadetGrey)
            }
        }
    }
}
