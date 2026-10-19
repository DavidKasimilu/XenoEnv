package com.example.ui.screens

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InstalledAppsManager
import com.example.data.models.ApkInstallState
import com.example.data.models.DownloadItem
import com.example.data.models.InstalledGameItem
import com.example.data.models.VirtualEnvProfile
import com.example.ui.components.AppIconImage
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamesScreen(
    installedGames: List<InstalledGameItem>,
    apkInstallState: ApkInstallState = ApkInstallState(),
    downloads: List<DownloadItem> = emptyList(),
    onPickApkClick: () -> Unit,
    onInstallApkUri: (Uri) -> Unit = {},
    onLaunchGame: (InstalledGameItem) -> Unit,
    onRemoveGame: (String) -> Unit = {},
    onImportDeviceApp: (packageName: String, appName: String) -> Unit = { _, _ -> },
    onUpdateEnvProfile: (packageName: String, profile: VirtualEnvProfile) -> Unit = { _, _ -> },
    onClearVirtualData: (packageName: String) -> Unit = {},
    onDismissInstallModal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pm = remember { context.packageManager }
    var showImportDialog by remember { mutableStateOf(false) }
    var gameToRemove by remember { mutableStateOf<InstalledGameItem?>(null) }
    var gameForEnvConfig by remember { mutableStateOf<InstalledGameItem?>(null) }

    val downloadedApks = remember(downloads) {
        downloads.filter {
            it.fileName.endsWith(".apk", ignoreCase = true) ||
                    it.mimeType.contains("package-archive", ignoreCase = true)
        }
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top Header & Virtualization Engine Status Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.SportsEsports,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Game Space • APK Virtualizer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (installedGames.isEmpty()) {
                                        "Virtual Environment Ready • Install APKs to run in-app"
                                    } else {
                                        "${installedGames.size} virtualized ${if (installedGames.size == 1) "package" else "packages"} • Isolated Sandbox"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "VM Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Emulation capabilities status row
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DEX ClassLoader • Virtual /data/ FS • GPU & Gamepad Emulation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPickApkClick,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("install_apk_top_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install APK")
                        }

                        FilledTonalButton(
                            onClick = { showImportDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_device_app_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeveloperBoard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Virtualize App")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Install banner if there are downloaded APKs from the built-in Browser
            if (downloadedApks.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Downloaded APK Files Ready to Install (${downloadedApks.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        downloadedApks.take(2).forEach { dl ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dl.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        val path = dl.filePath
                                        if (!path.isNullOrBlank() && File(path).exists()) {
                                            onInstallApkUri(Uri.fromFile(File(path)))
                                        }
                                    }
                                ) {
                                    Text("Install in VM")
                                }
                            }
                        }
                    }
                }
            }

            if (installedGames.isEmpty()) {
                // Clean Empty State focused on APK Installation & Virtualization
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.DeveloperBoard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Text(
                                text = "No Virtualized APKs Installed Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Install any .apk file from your device storage or virtualize an existing app. The APK Virtualizer unpacks DEX bytecode, native libraries, and assets into an isolated in-app environment so it runs directly inside this app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onPickApkClick,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FileOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select APK File")
                                }

                                OutlinedButton(
                                    onClick = { showImportDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Virtualize App")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(installedGames, key = { it.packageName }) { game ->
                        val appIcon = remember(game.packageName, game.iconPath) {
                            try {
                                if (!game.iconPath.isNullOrBlank() && File(game.iconPath).exists()) {
                                    val bmp = BitmapFactory.decodeFile(game.iconPath)
                                    if (bmp != null) BitmapDrawable(context.resources, bmp) else null
                                } else {
                                    pm.getApplicationIcon(game.packageName)
                                }
                            } catch (_: Exception) {
                                null
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLaunchGame(game) }
                                .testTag("game_card_${game.packageName}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    AppIconImage(
                                        iconDrawable = appIcon,
                                        fallbackName = game.appName,
                                        modifier = Modifier.size(54.dp)
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
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
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (!game.versionName.isNullOrBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                                ) {
                                                    Text(
                                                        text = "v${game.versionName}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                            ) {
                                                Text(
                                                    text = "${game.dexCount} DEX • ${game.envProfile.cpuAbi}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (!game.fileSizeFormatted.isNullOrBlank()) {
                                                Text(
                                                    text = game.fileSizeFormatted,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = { onLaunchGame(game) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        modifier = Modifier.testTag("play_game_${game.packageName}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Run In-App",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Run")
                                    }
                                }

                                // Bottom bar of Card: Emulated Environment details & controls
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${game.envProfile.deviceModel} • ${game.envProfile.allocatedRamMb}MB RAM • ${game.envProfile.gpuRenderer.substringBefore(" ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            TextButton(
                                                onClick = { gameForEnvConfig = game },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.SettingsSuggest,
                                                    contentDescription = "Configure Virtual Environment",
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "VM Config",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }

                                            IconButton(
                                                onClick = { gameToRemove = game },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline,
                                                    contentDescription = "Uninstall from Virtual Space",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button to install APK into Game Space
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
                contentDescription = "Install APK into Game Space"
            )
        }
    }

    // Live APK Virtual Installer & Unpacker Progress Modal
    if (apkInstallState.isInstalling || apkInstallState.completedItem != null || apkInstallState.error != null) {
        AlertDialog(
            onDismissRequest = {
                if (!apkInstallState.isInstalling) {
                    onDismissInstallModal()
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (apkInstallState.completedItem != null) Icons.Filled.CheckCircle else Icons.Filled.DeveloperBoard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when {
                            apkInstallState.isInstalling -> "Installing APK into Virtual Env..."
                            apkInstallState.completedItem != null -> "APK Virtualized & Ready"
                            else -> "APK Installation Error"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (apkInstallState.error != null) {
                        Text(
                            text = apkInstallState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = apkInstallState.currentStep,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        LinearProgressIndicator(
                            progress = { apkInstallState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )

                        // Completed APK Summary Card
                        apkInstallState.completedItem?.let { installed ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${installed.appName} (v${installed.versionName ?: "1.0"})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Package: ${installed.packageName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Entry Activity: ${(installed.mainActivityName ?: "MainActivity").substringAfterLast('.')}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Virtual Env: ${installed.dexCount} DEX • ${installed.nativeLibs.size} Native Libs • SDK ${installed.targetSdk}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Installer Terminal Output
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                apkInstallState.logs.forEach { line ->
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (apkInstallState.completedItem != null) {
                    Button(
                        onClick = {
                            val item = apkInstallState.completedItem
                            onDismissInstallModal()
                            onLaunchGame(item)
                        }
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Inside App")
                    }
                } else if (!apkInstallState.isInstalling) {
                    Button(onClick = onDismissInstallModal) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (!apkInstallState.isInstalling && apkInstallState.completedItem != null) {
                    TextButton(onClick = onDismissInstallModal) {
                        Text("Done")
                    }
                }
            }
        )
    }

    // Per-App Environment Emulation & Virtualization Configuration Dialog
    gameForEnvConfig?.let { target ->
        var selectedDevice by remember(target) { mutableStateOf(target.envProfile.deviceModel) }
        var selectedGpu by remember(target) { mutableStateOf(target.envProfile.gpuRenderer) }
        var selectedRam by remember(target) { mutableIntStateOf(target.envProfile.allocatedRamMb) }
        var gamepadEnabled by remember(target) { mutableStateOf(target.envProfile.virtualGamepadEnabled) }
        var forceLandscape by remember(target) { mutableStateOf(target.envProfile.forceLandscape) }

        val deviceOptions = listOf(
            "Pixel 8 Pro (Virtual)",
            "ROG Phone 8 Pro (Gaming)",
            "Galaxy S24 Ultra (Virtual)"
        )
        val gpuOptions = listOf(
            "Adreno (TM) 750 Virtualizer",
            "Immortalis-G720 Vulkan Engine",
            "OpenGL ES 3.2 SwiftShader"
        )
        val ramOptions = listOf(2048, 4096, 8192)

        AlertDialog(
            onDismissRequest = { gameForEnvConfig = null },
            title = {
                Column {
                    Text(
                        text = "Virtual Environment Config",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${target.appName} (${target.packageName})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Emulated Device Profile",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        deviceOptions.forEach { dev ->
                            FilterChip(
                                selected = selectedDevice == dev,
                                onClick = { selectedDevice = dev },
                                label = { Text(dev.substringBefore(" ("), fontSize = 11.sp) }
                            )
                        }
                    }

                    Text(
                        text = "GPU Renderer Emulation",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        gpuOptions.forEach { gpu ->
                            FilterChip(
                                selected = selectedGpu == gpu,
                                onClick = { selectedGpu = gpu },
                                label = { Text(gpu.substringBefore(" "), fontSize = 11.sp) }
                            )
                        }
                    }

                    Text(
                        text = "Virtual Heap / RAM Allocation",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ramOptions.forEach { ram ->
                            FilterChip(
                                selected = selectedRam == ram,
                                onClick = { selectedRam = ram },
                                label = { Text("${ram / 1024} GB RAM", fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Virtual Gamepad Overlay",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Show D-Pad and A/B/X/Y controls inside app",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = gamepadEnabled,
                            onCheckedChange = { gamepadEnabled = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wide Landscape Viewport",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Emulate widescreen display ratio",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = forceLandscape,
                            onCheckedChange = { forceLandscape = it }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onClearVirtualData(target.packageName)
                            gameForEnvConfig = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Wipe Isolated Virtual /data/ Storage")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedProfile = target.envProfile.copy(
                            deviceModel = selectedDevice,
                            gpuRenderer = selectedGpu,
                            allocatedRamMb = selectedRam,
                            virtualGamepadEnabled = gamepadEnabled,
                            forceLandscape = forceLandscape
                        )
                        onUpdateEnvProfile(target.packageName, updatedProfile)
                        gameForEnvConfig = null
                    }
                ) {
                    Text("Save VM Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { gameForEnvConfig = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirm Remove Dialog
    gameToRemove?.let { target ->
        AlertDialog(
            onDismissRequest = { gameToRemove = null },
            title = { Text("Uninstall ${target.appName}?") },
            text = {
                Text("This will uninstall ${target.appName} and wipe its isolated virtual filesystem (/data/user/0/${target.packageName}) from your Game Space.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveGame(target.packageName)
                        gameToRemove = null
                    }
                ) {
                    Text("Uninstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { gameToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Virtualize Installed App into Game Space Dialog
    if (showImportDialog) {
        val deviceApps = remember { InstalledAppsManager.getInstalledApps(context) }
        var searchQuery by remember { mutableStateOf("") }
        val filtered = remember(deviceApps, searchQuery) {
            if (searchQuery.isBlank()) deviceApps
            else deviceApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Install or Virtualize APK") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Pick a standalone .apk file from storage, or select a device app below to extract and virtualize its base.apk inside Game Space:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            showImportDialog = false
                            onPickApkClick()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browse Storage for .APK File")
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search installed packages to virtualize...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onImportDeviceApp(app.packageName, app.appName)
                                        showImportDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    AppIconImage(
                                        iconDrawable = app.icon,
                                        fallbackName = app.appName,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Virtualize",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
