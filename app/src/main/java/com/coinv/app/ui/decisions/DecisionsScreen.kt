package com.coinv.app.ui.decisions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
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
import com.coinv.app.ui.theme.CoinSuccess
import com.coinv.app.ui.theme.CoinWarning
import com.coinv.app.ui.theme.jetBrainsMono

@Composable
fun DecisionsScreen(
    modifier: Modifier = Modifier,
    showDecisionDialog: Boolean = false,
    showGoalDialog: Boolean = false,
    onDismissDialogs: () -> Unit = {},
    viewModel: DecisionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showFabMenu by remember { mutableStateOf(false) }
    var localDecisionDialog by remember { mutableStateOf(false) }
    var localGoalDialog by remember { mutableStateOf(false) }

    val decisionDialogOpen = showDecisionDialog || localDecisionDialog
    val goalDialogOpen = showGoalDialog || localGoalDialog

    if (decisionDialogOpen) {
        DecisionCreateDialog(
            isAnalyzing = state.isAnalyzing,
            onDismiss = {
                localDecisionDialog = false
                onDismissDialogs()
            },
            onCreate = { question, context ->
                viewModel.createDecision(question, context)
                localDecisionDialog = false
                onDismissDialogs()
            }
        )
    }

    if (goalDialogOpen) {
        GoalCreateDialog(
            onDismiss = {
                localGoalDialog = false
                onDismissDialogs()
            },
            onCreate = { title, desc ->
                viewModel.createGoal(title, desc)
                localGoalDialog = false
                onDismissDialogs()
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CoinBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFabMenu = !showFabMenu },
                containerColor = CoinBlue
            ) {
                Text("+", color = CoinChrome, fontSize = 22.sp)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (state.isAnalyzing) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = CoinBlue, strokeWidth = 2.dp)
                        Text("Analyzing decision with AI…", color = CoinChromeMuted, fontSize = 13.sp)
                    }
                }
            }

            if (showFabMenu) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { localDecisionDialog = true; showFabMenu = false }) {
                            Text("Analyze decision", color = CoinBlue)
                        }
                        TextButton(onClick = { localGoalDialog = true; showFabMenu = false }) {
                            Text("Create goal", color = CoinBlue)
                        }
                    }
                }
            }

            item { SectionHeader("Decision Center") }

            if (state.decisions.isEmpty()) {
                item {
                    Text(
                        "No decisions yet. Tap + to analyze your first decision.",
                        color = CoinChromeMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            items(state.decisions, key = { it.id }) { decision ->
                SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(
                        text = decision.question,
                        style = MaterialTheme.typography.titleMedium,
                        color = CoinChrome
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DecisionRow("Pros", decision.pros, CoinSuccess)
                    DecisionRow("Cons", decision.cons, CoinWarning)
                    DecisionRow("Risks", decision.risks, CoinWarning)
                    if (decision.opportunities.isNotBlank()) {
                        DecisionRow("Opportunities", decision.opportunities, CoinSuccess)
                    }
                    if (decision.missingInfo.isNotBlank()) {
                        DecisionRow("Missing Info", decision.missingInfo, CoinChromeMuted)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recommendation", fontFamily = jetBrainsMono, fontSize = 10.sp, color = CoinBlue)
                    Text(decision.recommendation, style = MaterialTheme.typography.bodyMedium, color = CoinChrome)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Confidence ${decision.confidenceScore}%",
                        fontFamily = jetBrainsMono,
                        fontSize = 11.sp,
                        color = CoinBlue
                    )
                    decision.outcome?.let {
                        Text("Outcome: $it", color = CoinSuccess, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            item { SectionHeader("Goal Command Center") }

            if (state.goals.isEmpty()) {
                item {
                    Text(
                        "No goals yet. Create one to track progress.",
                        color = CoinChromeMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            items(state.goals, key = { it.id }) { goal ->
                SurfaceCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium, color = CoinChrome)
                    Text(goal.description, style = MaterialTheme.typography.bodySmall, color = CoinChromeMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { goal.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                        color = CoinBlue,
                        trackColor = CoinBorder,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "${goal.progress}% · ${goal.status}",
                        fontFamily = jetBrainsMono,
                        fontSize = 11.sp,
                        color = CoinChromeMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionCreateDialog(
    isAnalyzing: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var context by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Analyze Decision", color = CoinChrome) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("What decision are you facing?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = context,
                    onValueChange = { context = it },
                    label = { Text("Context (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(question, context) },
                enabled = question.isNotBlank() && !isAnalyzing
            ) { Text("Analyze", color = CoinBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CoinChromeMuted) }
        },
        containerColor = CoinBackground
    )
}

@Composable
private fun GoalCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Goal", color = CoinChrome) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Goal title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, description) }, enabled = title.isNotBlank()) {
                Text("Create", color = CoinBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CoinChromeMuted) }
        },
        containerColor = CoinBackground
    )
}

@Composable
private fun DecisionRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    if (value.isBlank()) return
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            fontFamily = jetBrainsMono,
            fontSize = 10.sp,
            color = color,
            modifier = Modifier.width(100.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = CoinChrome)
    }
}
