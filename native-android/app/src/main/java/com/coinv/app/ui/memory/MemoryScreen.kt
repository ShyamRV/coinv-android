package com.coinv.app.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinv.app.ui.components.SectionHeader
import com.coinv.app.ui.components.SurfaceCard
import com.coinv.app.ui.theme.CoinBackground
import com.coinv.app.ui.theme.CoinBlue
import com.coinv.app.ui.theme.CoinBorder
import com.coinv.app.ui.theme.CoinChrome
import com.coinv.app.ui.theme.CoinChromeMuted
import com.coinv.app.ui.theme.CoinSurface
import com.coinv.app.ui.theme.jetBrainsMono

@Composable
fun MemoryScreen(
    modifier: Modifier = Modifier,
    showCaptureDialog: Boolean = false,
    onDismissCapture: () -> Unit = {},
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsState()
    var search by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    if (showCaptureDialog || showDialog) {
        CaptureMemoryDialog(
            onDismiss = {
                showDialog = false
                onDismissCapture()
            },
            onSave = { title, content, category ->
                viewModel.saveMemory(title, content, category)
                showDialog = false
                onDismissCapture()
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CoinBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = CoinBlue
            ) {
                Text("+", color = CoinChrome, fontSize = 22.sp)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SectionHeader("Memory Vault")
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    viewModel.search(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                placeholder = { Text("Search memories, ideas, tags…", color = CoinChromeMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CoinBlue,
                    unfocusedBorderColor = CoinBorder,
                    focusedTextColor = CoinChrome,
                    unfocusedTextColor = CoinChrome,
                    cursorColor = CoinBlue,
                    focusedContainerColor = CoinSurface,
                    unfocusedContainerColor = CoinSurface
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            if (memories.isEmpty()) {
                Text(
                    text = "No memories yet. Capture your first idea with +.",
                    color = CoinChromeMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(memories, key = { it.id }) { memory ->
                    SurfaceCard {
                        Text(
                            text = memory.category.uppercase(),
                            fontFamily = jetBrainsMono,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = CoinBlue
                        )
                        Text(
                            text = memory.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = CoinChrome,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Text(
                            text = memory.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoinChromeMuted
                        )
                        if (memory.tags.isNotBlank()) {
                            Text(
                                text = memory.tags,
                                fontFamily = jetBrainsMono,
                                fontSize = 10.sp,
                                color = CoinChromeMuted,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("idea") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Capture Memory", color = CoinChrome) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, content, category) }, enabled = content.isNotBlank()) {
                Text("Save", color = CoinBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CoinChromeMuted) }
        },
        containerColor = CoinBackground
    )
}
