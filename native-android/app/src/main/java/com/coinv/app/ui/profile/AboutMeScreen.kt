package com.coinv.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinv.app.ui.components.SectionHeader
import com.coinv.app.ui.components.SurfaceCard
import com.coinv.app.ui.theme.CoinBackground
import com.coinv.app.ui.theme.CoinBlue
import com.coinv.app.ui.theme.CoinChrome
import com.coinv.app.ui.theme.CoinChromeMuted
import com.coinv.app.ui.theme.CoinSuccess
import com.coinv.app.ui.theme.jetBrainsMono

@Composable
fun AboutMeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutMeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val valueMemories by viewModel.valueMemories.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CoinBackground),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = CoinChrome)
                }
                Text("About Me", style = MaterialTheme.typography.titleLarge, color = CoinChrome)
            }
            Text(
                "These facts are stored as value memories and injected into every AI response via ContextAssembler.",
                fontSize = 12.sp,
                color = CoinChromeMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        items(AboutMeField.entries, key = { it.name }) { field ->
            AboutMeFieldInput(
                field = field,
                value = uiState.fields[field].orEmpty(),
                saveStatus = uiState.saveStatus[field],
                onValueChange = { viewModel.updateField(field, it) },
                onSave = { viewModel.saveField(field) }
            )
        }

        item { SectionHeader("What CoinV knows about you") }
        item {
            SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (valueMemories.isEmpty()) {
                    Text(
                        "No value memories yet. Save a field above to verify wiring.",
                        fontSize = 12.sp,
                        color = CoinChromeMuted
                    )
                } else {
                    valueMemories.forEach { memory ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                memory.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoinChrome
                            )
                            Text(
                                "layer=${memory.layer} · sourceId=${memory.sourceId ?: "none"}",
                                fontSize = 10.sp,
                                fontFamily = jetBrainsMono,
                                color = CoinBlue,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    "This list is read live from semantic_memories — the same data ContextAssembler injects.",
                    fontSize = 10.sp,
                    color = CoinChromeMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutMeFieldInput(
    field: AboutMeField,
    value: String,
    saveStatus: String?,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val hadFocus = remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(field.label, style = MaterialTheme.typography.titleSmall, color = CoinChrome)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(field.hint, color = CoinChromeMuted) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .onFocusChanged { focus ->
                    if (hadFocus.value && !focus.isFocused && value.isNotBlank()) {
                        onSave()
                    }
                    hadFocus.value = focus.isFocused
                },
            minLines = if (field == AboutMeField.GENERAL_NOTE) 3 else 1
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            saveStatus?.let { status ->
                Text(
                    status,
                    fontSize = 11.sp,
                    color = if (status == "Saved") CoinSuccess else CoinChromeMuted,
                    fontFamily = jetBrainsMono
                )
            } ?: Text("", fontSize = 11.sp)
            TextButton(onClick = onSave, enabled = value.isNotBlank()) {
                Text("Save", color = CoinBlue, fontSize = 12.sp)
            }
        }
    }
}
