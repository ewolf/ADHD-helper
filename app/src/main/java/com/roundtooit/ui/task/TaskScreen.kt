package com.roundtooit.ui.task

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.roundtooit.data.local.entity.TaskEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    onBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::toggleAddForm) {
                Icon(
                    if (uiState.showAddForm) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (uiState.showAddForm) "Cancel" else "Add Task",
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
            // Add form
            item {
                AnimatedVisibility(visible = uiState.showAddForm) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = uiState.newTitle,
                                onValueChange = viewModel::onTitleChanged,
                                label = { Text("Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = {
                                    Text("${uiState.newTitle.length}/500")
                                },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.newDescription,
                                onValueChange = viewModel::onDescriptionChanged,
                                label = { Text("Description (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = viewModel::addTask,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = uiState.newTitle.isNotBlank(),
                            ) {
                                Text("Add Task")
                            }
                        }
                    }
                }
            }

            // Task list
            itemsIndexed(uiState.tasks, key = { _, t -> t.serverId }) { index, task ->
                TaskItem(
                    task = task,
                    position = index + 1,
                    onComplete = { viewModel.completeTask(task) },
                    onDelay = { viewModel.delayTask(task) },
                )
            }

            if (uiState.tasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No tasks yet. Tap + to add one.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskItem(
    task: TaskEntity,
    position: Int,
    onComplete: () -> Unit,
    onDelay: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$position.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onComplete) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Complete", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelay) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Delay", tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
