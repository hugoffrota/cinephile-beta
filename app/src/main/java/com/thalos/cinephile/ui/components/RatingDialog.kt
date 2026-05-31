package com.thalos.cinephile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thalos.cinephile.ui.theme.*

@Composable
fun RatingDialog(
    movieTitle: String,
    currentRating: Double?,
    onDismiss: () -> Unit,
    onRate: (Double) -> Unit
) {
    var selectedValue by remember { mutableStateOf((currentRating ?: 5.0).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate $movieTitle", color = Platinum) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val displayValue = kotlin.math.round(selectedValue * 10f) / 10f
                Text(
                    "%.1f".format(displayValue),
                    style = MaterialTheme.typography.displayLarge,
                    color = UltraViolet
                )
                Text(
                    "/ 10",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CadetGrey
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = selectedValue,
                    onValueChange = { selectedValue = kotlin.math.round(it * 10f) / 10f },
                    valueRange = 1f..10f,
                    steps = 90,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rounded = kotlin.math.round(selectedValue * 10) / 10.0
                    onRate(rounded)
                },
                colors = ButtonDefaults.buttonColors(containerColor = UltraViolet)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CadetGrey)
            }
        },
        containerColor = EerieBlack,
        shape = RoundedCornerShape(16.dp)
    )
}
