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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.coinv.app.ui.theme.CoinError
import com.coinv.app.ui.theme.CoinWarning
import com.coinv.app.ui.theme.CoinSuccess
import com.coinv.app.ui.theme.CoinSurface
import com.coinv.app.ui.theme.jetBrainsMono

private val INTEGRATIONS = listOf(
    "Google Calendar", "Gmail", "Notion", "Slack", "GitHub", "Zoom"
)

@Composable
fun ProfileScreen(
    onStopMonitoring: () -> Unit = {},
    onOpenAboutMe: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile
    val settings = state.settings

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CoinBackground),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            SectionHeader("Profile")
            SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                var editingName by remember { mutableStateOf(false) }
                var nameInput by remember { mutableStateOf(profile?.name ?: "Shyam") }
                if (editingName) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = {
                        viewModel.updateName(nameInput)
                        editingName = false
                    }) { Text("Save", color = CoinBlue) }
                } else {
                    Text(profile?.name ?: "Shyam", style = MaterialTheme.typography.headlineMedium, color = CoinChrome)
                    TextButton(onClick = { editingName = true; nameInput = profile?.name ?: "Shyam" }) {
                        Text("Edit name", color = CoinBlue, fontSize = 12.sp)
                    }
                }
                Text(
                    "Cognitive State: ${profile?.cognitiveState ?: "Ready"}",
                    fontFamily = jetBrainsMono,
                    fontSize = 12.sp,
                    color = CoinBlue,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item { SectionHeader("AI Coaches") }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AI_COACHES.take(3).forEach { coach ->
                    CoachChip(coach, profile?.activeCoach == coach) { viewModel.selectCoach(coach) }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AI_COACHES.drop(3).forEach { coach ->
                    CoachChip(coach, profile?.activeCoach == coach) { viewModel.selectCoach(coach) }
                }
            }
        }

        item { SectionHeader("Assistant Modes") }
        item {
            SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Listening — responds when you ask. Monitoring — collects context silently.",
                    fontSize = 12.sp,
                    color = CoinChromeMuted
                )
                Text(
                    "Headset: 1× play/pause = Listening · 2× = Monitoring",
                    fontFamily = jetBrainsMono,
                    fontSize = 11.sp,
                    color = CoinBlue,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item { SectionHeader("Privacy & Monitoring") }
        item {
            SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingRow("Allow monitoring mode") {
                    Switch(
                        checked = settings.monitoringEnabled,
                        onCheckedChange = { viewModel.setMonitoringEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CoinBlue)
                    )
                }
                SettingRow("Local-only processing") {
                    Switch(
                        checked = settings.localOnlyProcessing,
                        onCheckedChange = { viewModel.setLocalOnlyProcessing(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CoinBlue)
                    )
                }
                SettingRow("Stop monitoring now") {
                    TextButton(onClick = onStopMonitoring) {
                        Text("Disable", color = CoinWarning, fontSize = 12.sp)
                    }
                }
                Text(
                    "Monitoring shows a visible indicator and never speaks unless you ask directly.",
                    fontSize = 11.sp,
                    color = CoinChromeMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item { SectionHeader("Legacy Listening") }
        item {
            val modes = listOf(
                "off" to "Off",
                "push_to_talk" to "Push To Talk",
                "wake_word" to "Wake Word",
                "always_listening" to "Always On"
            )
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modes.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (mode, label) ->
                            FilterChip(
                                selected = profile?.listeningMode == mode,
                                onClick = { viewModel.updateListeningMode(mode) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CoinSurface,
                                    selectedLabelColor = CoinBlue,
                                    containerColor = CoinBackground,
                                    labelColor = CoinChromeMuted
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = profile?.listeningMode == mode,
                                    borderColor = CoinBorder,
                                    selectedBorderColor = CoinBlue
                                )
                            )
                        }
                    }
                }
                Text(
                    "A microphone indicator appears when background listening is active.",
                    fontSize = 11.sp,
                    color = CoinChromeMuted
                )
            }
        }

        if (state.learning.isNotEmpty()) {
            item { SectionHeader("Learning Hub") }
            items(state.learning, key = { it.id }) { item ->
                SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(item.topic, style = MaterialTheme.typography.titleMedium, color = CoinChrome)
                    Text(item.summary, style = MaterialTheme.typography.bodySmall, color = CoinChromeMuted)
                    Text(
                        "${item.progress}% · ${item.flashcardsCount} flashcards",
                        fontFamily = jetBrainsMono,
                        fontSize = 11.sp,
                        color = CoinSuccess,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item { SectionHeader("Integrations") }
        item {
            Text(
                "Coming soon — connect your tools to enrich cognitive context.",
                color = CoinChromeMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                INTEGRATIONS.take(3).forEach { name ->
                    Text(name, fontSize = 11.sp, color = CoinChromeMuted)
                }
            }
        }

        item { SectionHeader("About Me") }
        item {
            SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Tell CoinV about yourself — stored as value memories and injected into every AI call.",
                    fontSize = 12.sp,
                    color = CoinChromeMuted
                )
                TextButton(onClick = onOpenAboutMe) {
                    Text("Edit About Me", color = CoinBlue, fontSize = 12.sp)
                }
            }
        }

        item { SectionHeader("What CoinV knows about you") }
        item {
            val valueMemories by viewModel.valueMemories.collectAsState()
            SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (valueMemories.isEmpty()) {
                    Text(
                        "No value memories yet. Fill in About Me to verify wiring.",
                        fontSize = 12.sp,
                        color = CoinChromeMuted
                    )
                } else {
                    valueMemories.forEach { memory ->
                        Text(
                            memory.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = CoinChrome,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        item { SectionHeader("Active behaviors") }
        item {
            val behaviorStats by viewModel.behaviorStats.collectAsState()
            SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (behaviorStats.isEmpty()) {
                    Text("Loading behavior stats…", fontSize = 12.sp, color = CoinChromeMuted)
                } else {
                    behaviorStats.forEach { stat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stat.label, color = CoinChrome, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${stat.shownCount} shown · ${stat.dismissedCount} dismissed (30d)",
                                    fontSize = 11.sp,
                                    color = CoinChromeMuted,
                                    fontFamily = jetBrainsMono
                                )
                            }
                            Text(
                                text = if (stat.isActive) "active" else "quiet for you",
                                fontSize = 10.sp,
                                fontFamily = jetBrainsMono,
                                color = if (stat.isActive) CoinBlue else CoinChromeMuted
                            )
                        }
                    }
                }
                TextButton(onClick = { viewModel.refreshBehaviorStats() }) {
                    Text("Refresh", color = CoinBlue, fontSize = 12.sp)
                }
            }
        }

        item { SectionHeader("Settings") }
        item {
            Text(
                text = "Memories: ${state.semanticMemoryCount} stored",
                color = CoinBlue,
                fontSize = 12.sp,
                fontFamily = jetBrainsMono,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        item {
            SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingRow("Theme") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("dark" to "Dark", "light" to "Light").forEach { (value, label) ->
                            FilterChip(
                                selected = settings.theme == value,
                                onClick = { viewModel.setTheme(value) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
                SettingRow("Privacy analytics") {
                    Switch(
                        checked = settings.privacyAnalytics,
                        onCheckedChange = { viewModel.setPrivacyAnalytics(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CoinBlue)
                    )
                }
                SettingRow("Notifications") {
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotifications(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CoinBlue)
                    )
                }
                SettingRow("Data export") {
                    TextButton(onClick = { viewModel.exportData() }) {
                        Text("Export profile", color = CoinBlue, fontSize = 12.sp)
                    }
                }
                SettingRow("Clear memory") {
                    TextButton(onClick = { viewModel.clearMemory() }) {
                        Text("Clear all", color = CoinWarning, fontSize = 12.sp)
                    }
                }
                SettingRow("Reset app") {
                    TextButton(onClick = { viewModel.resetApp() }) {
                        Text("Reset", color = CoinError, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CoinChrome)
        content()
    }
}

@Composable
private fun CoachChip(name: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(name, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CoinSurface,
            selectedLabelColor = CoinBlue,
            containerColor = CoinBackground,
            labelColor = CoinChromeMuted
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CoinBorder,
            selectedBorderColor = CoinBlue
        )
    )
}
