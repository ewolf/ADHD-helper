package com.roundtooit.ui.event

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val event = uiState.event
        if (event == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }
        } else {
            val timeFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy h:mm a", Locale.getDefault()) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "${timeFormat.format(Date(event.startTime))} - ${
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.endTime))
                    }",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!event.description.isNullOrBlank()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (!event.location.isNullOrBlank()) {
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Reminders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                // 1-hour reminder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("1 hour before")
                    Switch(
                        checked = uiState.reminder1hEnabled,
                        onCheckedChange = { viewModel.setReminder1hEnabled(it) },
                    )
                }

                // 5-minute reminder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("5 minutes before")
                    Switch(
                        checked = uiState.reminder5mEnabled,
                        onCheckedChange = { viewModel.setReminder5mEnabled(it) },
                    )
                }

                // Reminder mode
                Text(
                    text = "Reminder Type",
                    style = MaterialTheme.typography.titleSmall,
                )

                val modes = listOf("voice" to "Voice", "chime" to "Chime", "buzz" to "Buzz", "both" to "Chime & Buzz", "none" to "Notification Only")

                modes.forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label)
                        RadioButton(
                            selected = uiState.reminderMode == value,
                            onClick = { viewModel.setReminderMode(value) },
                        )
                    }
                }
            }
        }
    }
}
