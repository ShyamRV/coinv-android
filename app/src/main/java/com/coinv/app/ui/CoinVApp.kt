package com.coinv.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coinv.app.navigation.CoinVTab
import com.coinv.app.ui.dashboard.DashboardScreen
import com.coinv.app.ui.decisions.DecisionsScreen
import com.coinv.app.ui.memory.MemoryScreen
import com.coinv.app.ui.profile.ProfileScreen
import com.coinv.app.ui.theme.CoinBackground
import com.coinv.app.ui.theme.CoinBlue
import com.coinv.app.ui.theme.CoinChrome
import com.coinv.app.ui.theme.CoinChromeMuted
import com.coinv.app.ui.theme.CoinSurface
import com.coinv.app.ui.voice.VoiceScreen
import com.coinv.app.ui.voice.VoiceViewModel
import com.coinv.app.voice.VoiceListener
import com.coinv.app.voice.VoiceSpeaker

@Composable
fun CoinVApp(
    voiceListener: VoiceListener,
    voiceSpeaker: VoiceSpeaker,
    onSpeechResult: (handler: (String) -> Unit) -> Unit = {},
    onSpeechError: (handler: (String) -> Unit) -> Unit = {},
    onPartialResult: (handler: (String) -> Unit) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: CoinVTab.DASHBOARD.route
    val activity = LocalContext.current as ComponentActivity
    val voiceViewModel: VoiceViewModel = hiltViewModel(activity)
    val context = LocalContext.current

    var showIdeaDialog by remember { mutableStateOf(false) }
    var ideaText by remember { mutableStateOf("") }
    var showDecisionPrompt by remember { mutableStateOf(false) }
    var showGoalPrompt by remember { mutableStateOf(false) }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            voiceListener.startListening()
            voiceViewModel.onListeningStarted()
            voiceViewModel.updateStatus("listening")
        }
    }

    DisposableEffect(voiceViewModel) {
        onSpeechResult { text -> voiceViewModel.onUserSpeechResult(text) }
        onSpeechError { message -> voiceViewModel.onSpeechError(message) }
        onPartialResult { partial -> voiceViewModel.onPartialTranscript(partial) }
        onDispose {
            onSpeechResult { }
            onSpeechError { }
            onPartialResult { }
        }
    }

    LaunchedEffect(voiceViewModel.status) {
        if (voiceViewModel.status == "speaking") {
            val last = voiceViewModel.messages.lastOrNull { it.role == "assistant" }
            if (last != null) {
                voiceSpeaker.speak(last.text) { voiceViewModel.onSpeakingComplete() }
            } else {
                voiceViewModel.onSpeakingComplete()
            }
        }
    }

    LaunchedEffect(voiceViewModel.listeningMode, voiceViewModel.status) {
        if (voiceViewModel.listeningMode in listOf("always_listening", "wake_word") &&
            voiceViewModel.status == "monitoring" &&
            hasAudioPermission
        ) {
            voiceListener.startListening()
            voiceViewModel.updateStatus("listening")
        }
    }

    fun handleOrbClick() {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        when (voiceViewModel.status) {
            "idle", "error", "monitoring" -> {
                voiceViewModel.clearError()
                voiceViewModel.onListeningStarted()
                voiceListener.startListening()
                voiceViewModel.updateStatus("listening")
            }
            "listening" -> {
                voiceListener.stopListening()
                voiceViewModel.updateStatus(
                    if (voiceViewModel.listeningMode in listOf("always_listening", "wake_word")) "monitoring" else "idle"
                )
            }
        }
    }

    fun navigateTo(tab: CoinVTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (showIdeaDialog) {
        AlertDialog(
            onDismissRequest = { showIdeaDialog = false },
            title = { Text("Capture Idea", color = CoinChrome) },
            text = {
                OutlinedTextField(
                    value = ideaText,
                    onValueChange = { ideaText = it },
                    label = { Text("What's on your mind?") },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ideaText.isNotBlank()) {
                        voiceViewModel.captureIdeaText(ideaText)
                        ideaText = ""
                    }
                    showIdeaDialog = false
                    navigateTo(CoinVTab.MEMORY)
                }) { Text("Save", color = CoinBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showIdeaDialog = false }) {
                    Text("Cancel", color = CoinChromeMuted)
                }
            },
            containerColor = CoinBackground
        )
    }

    Scaffold(
        containerColor = CoinBackground,
        bottomBar = {
            if (currentRoute != "timeline") {
                NavigationBar(containerColor = CoinSurface) {
                CoinVTab.tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CoinBlue,
                            selectedTextColor = CoinBlue,
                            unselectedIconColor = CoinChromeMuted,
                            unselectedTextColor = CoinChromeMuted,
                            indicatorColor = CoinSurface
                        )
                    )
                }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = CoinVTab.DASHBOARD.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(CoinVTab.DASHBOARD.route) {
                DashboardScreen(
                    voiceListener = voiceListener,
                    voiceStatus = voiceViewModel.status,
                    onOrbClick = { handleOrbClick() },
                    onQuickAction = { actionId ->
                        when (actionId) {
                            "voice" -> {
                                navigateTo(CoinVTab.VOICE)
                                handleOrbClick()
                            }
                            "idea" -> showIdeaDialog = true
                            "decision" -> {
                                showDecisionPrompt = true
                                navigateTo(CoinVTab.DECISIONS)
                            }
                            "goal" -> {
                                showGoalPrompt = true
                                navigateTo(CoinVTab.DECISIONS)
                            }
                        }
                    },
                    onNavigate = { tab -> navigateTo(tab) },
                    onOpenTimeline = { navController.navigate("timeline") }
                )
            }
            composable(CoinVTab.VOICE.route) {
                VoiceScreen(
                    voiceListener = voiceListener,
                    voiceSpeaker = voiceSpeaker,
                    viewModel = voiceViewModel
                )
            }
            composable(CoinVTab.MEMORY.route) {
                MemoryScreen(
                    showCaptureDialog = showIdeaDialog,
                    onDismissCapture = { showIdeaDialog = false }
                )
            }
            composable(CoinVTab.DECISIONS.route) {
                DecisionsScreen(
                    showDecisionDialog = showDecisionPrompt,
                    showGoalDialog = showGoalPrompt,
                    onDismissDialogs = {
                        showDecisionPrompt = false
                        showGoalPrompt = false
                    }
                )
            }
            composable(CoinVTab.PROFILE.route) {
                ProfileScreen()
            }
            composable("timeline") {
                com.coinv.app.ui.timeline.TimelineScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
