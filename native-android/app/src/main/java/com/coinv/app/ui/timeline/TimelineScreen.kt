package com.coinv.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinv.app.ui.components.EmptyState
import com.coinv.app.ui.components.TimelineItem
import com.coinv.app.ui.theme.CoinBackground
import com.coinv.app.ui.theme.CoinBlue
import com.coinv.app.ui.theme.CoinChrome
import com.coinv.app.ui.theme.CoinChromeMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onBack: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()

    Scaffold(
        containerColor = CoinBackground,
        topBar = {
            TopAppBar(
                title = { Text("Cognitive Timeline", color = CoinChrome) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CoinBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CoinBackground)
            )
        }
    ) { padding ->
        if (events.isEmpty()) {
            EmptyState(
                message = "Your timeline will populate as you use voice, memory, goals, and decisions.",
                modifier = Modifier.padding(padding).fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    TimelineItem(
                        type = event.type,
                        title = event.title,
                        description = event.description,
                        timeLabel = formatTime(event.timestamp)
                    )
                }
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    val fmt = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
    return fmt.format(Date(ts))
}
