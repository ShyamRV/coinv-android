package com.coinv.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinv.app.data.Message
import com.coinv.app.ui.CoinOrb
import com.coinv.app.ui.components.SectionHeader
import com.coinv.app.ui.components.WaveformVisualizer
import com.coinv.app.ui.theme.CoinBackground
import com.coinv.app.ui.theme.CoinBlue
import com.coinv.app.ui.theme.CoinBorder
import com.coinv.app.ui.theme.CoinChrome
import com.coinv.app.ui.theme.CoinChromeMuted
import com.coinv.app.ui.theme.CoinError
import com.coinv.app.ui.theme.CoinSurface
import com.coinv.app.ui.theme.CoinSurfaceRaised
import com.coinv.app.ui.theme.jetBrainsMono
import com.coinv.app.voice.VoiceListener
import com.coinv.app.voice.VoiceSpeaker
import kotlinx.coroutines.launch

private val QUICK_ACTIONS = listOf(
    "Summarize my day",
    "Capture this idea",
    "Analyze a decision",
    "Create a goal",
    "Review my notes"
)

private val LISTENING_MODES = listOf(
    "push_to_talk" to "Push To Talk",
    "wake_word" to "Wake Word",
    "always_listening" to "Always On"
)

@Composable
fun VoiceScreen(
    voiceListener: VoiceListener,
    voiceSpeaker: VoiceSpeaker,
    viewModel: VoiceViewModel = hiltViewModel(LocalContext.current as androidx.activity.ComponentActivity)
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

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
        if (granted && viewModel.status in listOf("idle", "error")) {
            voiceListener.startListening()
            viewModel.onListeningStarted()
            viewModel.updateStatus("listening")
        }
    }

    LaunchedEffect(viewModel.errorText) {
        viewModel.errorText?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearError()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoinBackground)
    ) {
        if (!hasAudioPermission) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CoinV needs microphone access.", color = CoinChrome, textAlign = TextAlign.Center)
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Grant access", color = CoinBlue)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (viewModel.liveTranscript.isNotBlank() && viewModel.status == "listening") {
                    item {
                        Text(
                            text = viewModel.liveTranscript,
                            fontFamily = jetBrainsMono,
                            fontSize = 13.sp,
                            color = CoinBlue,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        )
                    }
                }
                items(viewModel.messages, key = { it.timestamp }) { message ->
                    VoiceMessageBubble(message, screenWidth * 0.85f)
                }
            }
        }

        SectionHeader("Listening Mode")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LISTENING_MODES) { (mode, label) ->
                FilterChip(
                    selected = viewModel.listeningMode == mode,
                    onClick = { viewModel.updateListeningMode(mode) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CoinSurfaceRaised,
                        selectedLabelColor = CoinBlue,
                        containerColor = CoinSurface,
                        labelColor = CoinChromeMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = viewModel.listeningMode == mode,
                        borderColor = CoinBorder,
                        selectedBorderColor = CoinBlue
                    )
                )
            }
        }

        SectionHeader("Quick Actions")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(QUICK_ACTIONS) { action ->
                TextButton(
                    onClick = {
                        when (action) {
                            "Capture this idea" -> {
                                val last = viewModel.messages.lastOrNull { it.role == "user" }?.text
                                if (last != null) viewModel.captureIdea() else viewModel.updateStatus("listening")
                            }
                            "Summarize my day" -> {
                                viewModel.sendPrompt("Summarize my cognitive activity today.")
                            }
                            "Analyze a decision" -> {
                                viewModel.sendPrompt("Help me analyze an important decision I'm facing.")
                            }
                            "Create a goal" -> {
                                viewModel.sendPrompt("Help me define a clear goal with actionable steps.")
                            }
                            "Review my notes" -> {
                                viewModel.sendPrompt("Review my recent memories and highlight key themes.")
                            }
                        }
                    },
                    modifier = Modifier
                        .border(1.dp, CoinBorder, RoundedCornerShape(8.dp))
                        .background(CoinSurface, RoundedCornerShape(8.dp))
                ) {
                    Text(action, fontSize = 12.sp, color = CoinChrome)
                }
            }
        }

        val orbEnabled = viewModel.status in listOf("idle", "error", "listening", "monitoring")
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = viewModel.status.uppercase(),
                fontFamily = jetBrainsMono,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                color = if (viewModel.status in listOf("listening", "thinking", "speaking")) CoinBlue else CoinChromeMuted
            )
            WaveformVisualizer(active = viewModel.status in listOf("listening", "speaking"))
            Spacer(modifier = Modifier.height(8.dp))
            CoinOrb(
                status = viewModel.status,
                enabled = orbEnabled,
                reduceMotion = reduceMotion,
                onClick = {
                    if (!hasAudioPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@CoinOrb
                    }
                    when (viewModel.status) {
                        "idle", "error", "monitoring" -> {
                            viewModel.clearError()
                            viewModel.onListeningStarted()
                            voiceListener.startListening()
                            viewModel.updateStatus("listening")
                        }
                        "listening" -> {
                            voiceListener.stopListening()
                            viewModel.updateStatus("idle")
                        }
                    }
                },
                sizeDp = 200.dp
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(8.dp)) { data ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CoinSurfaceRaised)
                    .border(1.dp, CoinError, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Box(Modifier.widthIn(min = 2.dp).height(24.dp).background(CoinError))
                Text(data.visuals.message, color = CoinChrome, modifier = Modifier.padding(start = 10.dp).weight(1f))
            }
        }
    }
}

@Composable
private fun VoiceMessageBubble(message: Message, maxWidth: androidx.compose.ui.unit.Dp) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(Modifier.widthIn(min = 2.dp).height(40.dp).background(CoinBlue))
        }
        Surface(
            modifier = Modifier.widthIn(max = maxWidth),
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (isUser) CoinSurfaceRaised else CoinSurface
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = CoinChrome
            )
        }
    }
}
