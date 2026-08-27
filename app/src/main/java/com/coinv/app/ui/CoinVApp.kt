package com.coinv.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coinv.app.LaunchProbe
import com.coinv.app.domain.AppMode
import com.coinv.app.domain.VoicePhase
import com.coinv.app.engine.ContextEngine
import com.coinv.app.navigation.CoinVTab
import com.coinv.app.ui.dashboard.DashboardScreen
import com.coinv.app.ui.decisions.DecisionsScreen
import com.coinv.app.ui.memory.MemoryScreen
import com.coinv.app.ui.profile.AboutMeScreen
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
import dagger.hilt.android.EntryPointAccessors
import com.coinv.app.di.ContextEngineEntryPoint
import kotlinx.coroutines.delay

@Composable
fun CoinVApp(
    voiceListener: VoiceListener,
    voiceSpeaker: VoiceSpeaker,
    onSpeechResult: (handler: (String) -> Unit) -> Unit = {},
    onSpeechError: (handler: (String) -> Unit) -> Unit = {},
    onPartialResult: (handler: (String) -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    var graphReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        LaunchProbe.mark(context, "compose_boot_splash")
        delay(50)
        graphReady = true
        LaunchProbe.mark(context, "compose_graph_ready")
    }

    if (!graphReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CoinBackground),
            contentAlignment = Alignment.Center
        ) {
            Text("CoinV", color = CoinChrome, fontSize = 22.sp)
        }
        return
    }

    CoinVAppGraph(
        voiceListener = voiceListener,
        voiceSpeaker = voiceSpeaker,
        onSpeechResult = onSpeechResult,
        onSpeechError = onSpeechError,
        onPartialResult = onPartialResult
    )
}

@Composable
private fun CoinVAppGraph(
    voiceListener: VoiceListener,
    voiceSpeaker: VoiceSpeaker,
    onSpeechResult: (handler: (String) -> Unit) -> Unit,
    onSpeechError: (handler: (String) -> Unit) -> Unit,
    onPartialResult: (handler: (String) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: CoinVTab.DASHBOARD.route
    val activity = LocalContext.current as ComponentActivity
    val voiceViewModel: VoiceViewModel = hiltViewModel(activity)
    val context = LocalContext.current
    val modeState by voiceViewModel.modeState.collectAsState()

    LaunchedEffect(Unit) {
        LaunchProbe.mark(context, "voice_viewmodel_ok")
    }

    val contextEngine = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ContextEngineEntryPoint::class.java
        ).contextEngine()
    }

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
            voiceViewModel.enterListening("permission")
            voiceListener.startListening()
            voiceViewModel.onListeningStarted()
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

    // Activity is a LifecycleOwner — avoid Compose LocalLifecycleOwner (version-fragile).
    val lifecycleOwner = context as LifecycleOwner
    DisposableEffect(lifecycleOwner, voiceViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) voiceViewModel.onAppResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(modeState.phase) {
        if (modeState.phase == VoicePhase.Speaking) {
            val last = voiceViewModel.messages.lastOrNull { it.role == "assistant" }
            if (last != null) {
                voiceSpeaker.speak(last.text) { voiceViewModel.onSpeakingComplete() }
            } else {
                voiceViewModel.onSpeakingComplete()
            }
        }
    }

    LaunchedEffect(modeState.mode, modeState.phase, hasAudioPermission) {
        val needsMic = modeState.mode in listOf(AppMode.Listening, AppMode.Monitoring) &&
            modeState.phase in listOf(VoicePhase.Capturing, VoicePhase.None) &&
            hasAudioPermission
        if (needsMic && modeState.phase != VoicePhase.Speaking && modeState.phase != VoicePhase.Thinking) {
            voiceListener.startListening()
            voiceViewModel.onListeningStarted()
        }
    }

    LaunchedEffect(currentRoute) {
        contextEngine.recordNavigation(currentRoute, modeState.mode)
    }

    LaunchedEffect(Unit) {
        voiceViewModel.micRestart.collect {
            if (modeState.mode == AppMode.Monitoring && hasAudioPermission) {
                voiceListener.startListening()
                voiceViewModel.onListeningStarted()
            }
        }
    }

    fun handleOrbClick() {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        voiceViewModel.clearError()
        when (modeState.mode) {
            AppMode.Idle -> {
                voiceViewModel.enterListening("orb")
                voiceListener.startListening()
                voiceViewModel.onListeningStarted()
            }
            AppMode.Listening, AppMode.Monitoring -> {
                voiceListener.stopListening()
                voiceViewModel.enterIdle("orb")
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

    fun stopMonitoring() {
        voiceListener.stopListening()
        voiceViewModel.enterIdle("mode_bar")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ModeIndicatorBar(
            modeState = modeState,
            onStopMonitoring = { stopMonitoring() }
        )
        Scaffold(
        containerColor = CoinBackground,
        bottomBar = {
            if (currentRoute != "timeline" && currentRoute != "about_me") {
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
                    modeState = modeState,
                    onOrbClick = { handleOrbClick() },
                    onEnterListening = {
                        if (!hasAudioPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else {
                            voiceViewModel.enterListening("dashboard")
                            voiceListener.startListening()
                            voiceViewModel.onListeningStarted()
                        }
                    },
                    onEnterMonitoring = {
                        if (!hasAudioPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else {
                            voiceViewModel.enterMonitoring("dashboard")
                            voiceListener.startListening()
                            voiceViewModel.onListeningStarted()
                        }
                    },
                    onStopMode = {
                        voiceListener.stopListening()
                        voiceViewModel.enterIdle("dashboard")
                    },
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
                    viewModel = voiceViewModel,
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
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
                ProfileScreen(
                    onStopMonitoring = { stopMonitoring() },
                    onOpenAboutMe = { navController.navigate("about_me") }
                )
            }
            composable("about_me") {
                AboutMeScreen(onBack = { navController.popBackStack() })
            }
            composable("timeline") {
                com.coinv.app.ui.timeline.TimelineScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
    }
}
