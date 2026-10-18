package com.example.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.InstalledGameItem
import com.example.ui.components.AppIconImage

data class WebArcadeGame(
    val title: String,
    val url: String,
    val description: String,
    val isOffline: Boolean = false
)

@Composable
fun GamesScreen(
    installedGames: List<InstalledGameItem>,
    onPickApkClick: () -> Unit,
    onLaunchGame: (String) -> Unit,
    onPlayWebGame: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pm = remember { context.packageManager }

    var showAddCustomGameDialog by remember { mutableStateOf(false) }
    var customGameTitle by remember { mutableStateOf("") }
    var customGameUrl by remember { mutableStateOf("") }

    val featuredWebGames = remember {
        listOf(
            WebArcadeGame(
                title = "Retro Snake",
                url = "file:///android_asset/games/snake.html",
                description = "Offline • Smooth canvas arcade snake with touch D-pad & scores",
                isOffline = true
            ),
            WebArcadeGame(
                title = "2048 Puzzle",
                url = "file:///android_asset/games/2048.html",
                description = "Offline • Classic swipe & touch number tile merging",
                isOffline = true
            ),
            WebArcadeGame(
                title = "Neon Tic-Tac-Toe",
                url = "file:///android_asset/games/tictactoe.html",
                description = "Offline • Play vs Smart AI or 2-player pass & play",
                isOffline = true
            ),
            WebArcadeGame(
                title = "Brick Breaker Arcade",
                url = "file:///android_asset/games/brickbreaker.html",
                description = "Offline • Breakout touch paddle action with brick destruction",
                isOffline = true
            ),
            WebArcadeGame(
                title = "Retro Pacman",
                url = "https://elgoog.im/pacman/",
                description = "Online Arcade • Original maze dot-eating action",
                isOffline = false
            ),
            WebArcadeGame(
                title = "Minesweeper Classic",
                url = "https://minesweeper.online/",
                description = "Online Arcade • Logic tile clearing strategy game",
                isOffline = false
            ),
            WebArcadeGame(
                title = "World of Solitaire",
                url = "https://worldofsolitaire.com/",
                description = "Online Arcade • Classic Klondike card game with animations",
                isOffline = false
            ),
            WebArcadeGame(
                title = "T-Rex Dino Runner",
                url = "https://elgoog.im/t-rex/",
                description = "Online Arcade • Endless jump & duck obstacle runner",
                isOffline = false
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("games_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Games Hub",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "All games launch strictly inside this app • ${featuredWebGames.size} in-app games",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = { showAddCustomGameDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Game URL", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Section 1: Featured In-App Web & Offline Arcade Games
                item {
                    Text(
                        text = "Instant In-App Games",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(featuredWebGames) { game ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("web_game_${game.title}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (game.isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (game.isOffline) Icons.Filled.Gamepad else Icons.Filled.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = game.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (game.isOffline) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "OFFLINE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = game.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { onPlayWebGame(game.title, game.url) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("play_web_${game.title}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play In-App",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play In-App")
                            }
                        }
                    }
                }

                // Section 2: Installed APK / Custom Apps
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Installed Apps & Games (In-App Launch)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (installedGames.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No custom APK games installed yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap the + button below to pick and install open-source APKs from your device downloads.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(installedGames, key = { it.packageName }) { game ->
                        val appIcon = remember(game.packageName) {
                            try {
                                pm.getApplicationIcon(game.packageName)
                            } catch (_: Exception) {
                                null
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("game_card_${game.packageName}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                AppIconImage(
                                    iconDrawable = appIcon,
                                    fallbackName = game.appName,
                                    modifier = Modifier.size(48.dp)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = game.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = game.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!game.versionName.isNullOrBlank()) {
                                        Text(
                                            text = "v${game.versionName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        // Launch inside our app
                                        val inAppUrl = when {
                                            game.appName.contains("snake", ignoreCase = true) -> "file:///android_asset/games/snake.html"
                                            game.appName.contains("2048", ignoreCase = true) -> "file:///android_asset/games/2048.html"
                                            game.appName.contains("tic", ignoreCase = true) -> "file:///android_asset/games/tictactoe.html"
                                            game.appName.contains("brick", ignoreCase = true) || game.appName.contains("pong", ignoreCase = true) -> "file:///android_asset/games/brickbreaker.html"
                                            game.appName.contains("pacman", ignoreCase = true) -> "https://elgoog.im/pacman/"
                                            game.appName.contains("mine", ignoreCase = true) -> "https://minesweeper.online/"
                                            game.appName.contains("solitaire", ignoreCase = true) -> "https://worldofsolitaire.com/"
                                            else -> "file:///android_asset/games/2048.html"
                                        }
                                        onPlayWebGame(game.appName, inAppUrl)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("play_game_${game.packageName}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play In-App",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play In-App")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button to pick APK file
        FloatingActionButton(
            onClick = onPickApkClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("install_apk_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Install APK Game"
            )
        }
    }

    // Add Custom Game URL Dialog
    if (showAddCustomGameDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomGameDialog = false },
            title = { Text("Add Game to Play In-App") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a web game URL (e.g., HTML5 game from GitHub or web arcade) to play it directly inside this app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customGameTitle,
                        onValueChange = { customGameTitle = it },
                        label = { Text("Game Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customGameUrl,
                        onValueChange = { customGameUrl = it },
                        label = { Text("Game Web URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = customGameTitle.ifBlank { "Custom Game" }
                        val url = if (customGameUrl.startsWith("http://") || customGameUrl.startsWith("https://") || customGameUrl.startsWith("file://")) {
                            customGameUrl
                        } else {
                            "https://$customGameUrl"
                        }
                        showAddCustomGameDialog = false
                        customGameTitle = ""
                        customGameUrl = ""
                        onPlayWebGame(title, url)
                    },
                    enabled = customGameUrl.isNotBlank()
                ) {
                    Text("Launch In-App")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomGameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
