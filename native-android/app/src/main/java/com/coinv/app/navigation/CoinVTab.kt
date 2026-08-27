package com.coinv.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.ui.graphics.vector.ImageVector

enum class CoinVTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", "Dashboard", Icons.Outlined.Home),
    VOICE("voice", "Voice", Icons.Outlined.Mic),
    MEMORY("memory", "Memory", Icons.Outlined.Psychology),
    DECISIONS("decisions", "Decisions", Icons.Outlined.Lightbulb),
    PROFILE("profile", "Profile", Icons.Outlined.AccountCircle);

    companion object {
        val tabs: List<CoinVTab> = entries
    }
}
