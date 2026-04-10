package com.roundtooit.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.roundtooit.data.local.entity.CachedEmailEntity
import com.roundtooit.data.local.entity.CachedEventEntity
import com.roundtooit.data.local.entity.TaskEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTasks: () -> Unit,
    onNavigateToNotes: () -> Unit,
    onNavigateToEvent: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Round Tooit") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Home") },
                    selected = true,
                    onClick = {},
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Tasks") },
                    label = { Text("Tasks") },
                    selected = false,
                    onClick = onNavigateToTasks,
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Notes") },
                    label = { Text("Notes") },
                    selected = false,
                    onClick = onNavigateToNotes,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            // --- Upcoming Events ---
            item {
                SectionHeader("Upcoming Events")
            }

            if (uiState.todayEvents.isEmpty()) {
                item {
                    Text(
                        "No events today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.todayEvents, key = { it.googleEventId }) { event ->
                    EventCard(event = event, onClick = { onNavigateToEvent(event.googleEventId) })
                }
            }

            // --- Coming Up (next 3 days with events) ---
            if (uiState.upcomingDays.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(uiState.upcomingDays, key = { it.label }) { dayGroup ->
                    DayGroupCard(dayGroup = dayGroup)
                }
            }

            // --- Unread Emails ---
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("Messages")
            }

            if (uiState.unreadEmails.isEmpty()) {
                item {
                    Text(
                        "No messages from contacts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.unreadEmails, key = { it.gmailMessageId }) { email ->
                    EmailCard(
                        email = email,
                        onDone = { viewModel.markEmailDone(email) },
                    )
                }
            }

            // --- Top Tasks ---
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("Tasks", action = "View All", onAction = onNavigateToTasks)
            }

            if (uiState.topTasks.isEmpty()) {
                item {
                    Text(
                        "No tasks in queue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.topTasks, key = { it.serverId }) { task ->
                    TaskCard(
                        task = task,
                        onComplete = { viewModel.completeTask(task) },
                        onDelay = { viewModel.delayTask(task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action)
            }
        }
    }
}

@Composable
private fun EventCard(event: CachedEventEntity, onClick: () -> Unit) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Event, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${timeFormat.format(Date(event.startTime))} - ${timeFormat.format(Date(event.endTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayGroupCard(dayGroup: UpcomingDayGroup) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp, 12.dp)) {
            Text(
                text = dayGroup.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (dayGroup.periods.morning.isNotEmpty()) {
                PeriodRow("morning", dayGroup.periods.morning)
            }
            if (dayGroup.periods.afternoon.isNotEmpty()) {
                PeriodRow("afternoon", dayGroup.periods.afternoon)
            }
            if (dayGroup.periods.evening.isNotEmpty()) {
                PeriodRow("evening", dayGroup.periods.evening)
            }
        }
    }
}

@Composable
private fun PeriodRow(period: String, events: List<String>) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$period:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            modifier = Modifier.width(76.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = events.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EmailCard(email: CachedEmailEntity, onDone: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (email.isUnread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .padding(end = 4.dp)
                                .then(
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primary,
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = email.senderName ?: email.senderEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = email.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = email.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDone) {
                Icon(
                    Icons.Default.Done,
                    contentDescription = "Mark done",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    onComplete: () -> Unit,
    onDelay: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onComplete) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Complete",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelay) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "Delay",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
