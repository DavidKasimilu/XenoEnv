package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ClonedAppInstance
import com.example.ui.components.AppIconImage
import com.example.ui.components.parseHexColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpaceSandboxScreen(
    clones: List<ClonedAppInstance>,
    activeDockIds: List<String>,
    onLaunchInstance: (String) -> Unit,
    onOpenAddClone: () -> Unit,
    onOpenEditClone: (ClonedAppInstance) -> Unit,
    onDeleteClone: (String) -> Unit,
    onCloseFromDock: (String) -> Unit,
    onOpenMedia: () -> Unit = {},
    onOpenDocs: () -> Unit = {},
    onBackToBrowser: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackToBrowser()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("space_sandbox_screen")
    ) {
        // Minimal Header: Back button, Title, and + Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBackToBrowser,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Browser"
                    )
                }
                Text(
                    text = "Clone Space",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            FilledTonalIconButton(
                onClick = onOpenAddClone,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("add_clone_header_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add Clone"
                )
            }
        }

        // Clean, Minimal App Icon Grid
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            clones.forEach { clone ->
                CloneAppIconItem(
                    instance = clone,
                    onLaunch = { onLaunchInstance(clone.instanceId) },
                    onEdit = { onOpenEditClone(clone) },
                    onDelete = { onDeleteClone(clone.instanceId) }
                )
            }

            MediaShortcutIconItem(
                onClick = onOpenMedia
            )

            DocsShortcutIconItem(
                onClick = onOpenDocs
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Tap an icon to launch • Long-press to edit or delete",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MediaShortcutIconItem(
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("clone_space_media_shortcut")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(68.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF7C4DFF),
                                Color(0xFF536DFE)
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.SmartDisplay,
                    contentDescription = "Media",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Media",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DocsShortcutIconItem(
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("clone_space_docs_shortcut")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(68.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0EA5E9),
                                Color(0xFF2563EB)
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = "Docs",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Docs",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloneAppIconItem(
    instance: ClonedAppInstance,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    val badgeColor = parseHexColor(instance.badgeColorHex)

    val isInstagram = remember(instance) {
        instance.packageName.contains("instagram", ignoreCase = true) ||
                instance.originalName.contains("instagram", ignoreCase = true) ||
                instance.displayName.contains("instagram", ignoreCase = true)
    }

    val isWhatsapp = remember(instance) {
        instance.packageName.contains("whatsapp", ignoreCase = true) ||
                instance.originalName.contains("whatsapp", ignoreCase = true) ||
                instance.displayName.contains("whatsapp", ignoreCase = true)
    }

    val isGmail = remember(instance) {
        instance.packageName.contains("com.google.android.gm", ignoreCase = true) ||
                instance.originalName.contains("gmail", ignoreCase = true) ||
                instance.displayName.contains("gmail", ignoreCase = true) ||
                instance.targetUrl.contains("mail.google.com", ignoreCase = true)
    }

    val isFacebook = remember(instance) {
        instance.packageName.contains("facebook", ignoreCase = true) ||
                instance.originalName.contains("facebook", ignoreCase = true) ||
                instance.displayName.contains("facebook", ignoreCase = true) ||
                instance.targetUrl.contains("facebook.com", ignoreCase = true)
    }

    val isYoutube = remember(instance) {
        instance.packageName.contains("youtube", ignoreCase = true) ||
                instance.originalName.contains("youtube", ignoreCase = true) ||
                instance.displayName.contains("youtube", ignoreCase = true) ||
                instance.targetUrl.contains("youtube.com", ignoreCase = true) ||
                instance.targetUrl.contains("youtu.be", ignoreCase = true)
    }

    val isDiscord = remember(instance) {
        instance.packageName.contains("discord", ignoreCase = true) ||
                instance.originalName.contains("discord", ignoreCase = true) ||
                instance.displayName.contains("discord", ignoreCase = true) ||
                instance.targetUrl.contains("discord.com", ignoreCase = true) ||
                instance.targetUrl.contains("discord.gg", ignoreCase = true)
    }

    val appIcon = remember(instance.packageName) {
        try {
            if (instance.packageName.isNotBlank()) {
                context.packageManager.getApplicationIcon(instance.packageName)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    val cleanLabel = remember(
        instance.displayName,
        instance.originalName,
        isInstagram,
        isWhatsapp,
        isGmail,
        isFacebook,
        isYoutube,
        isDiscord
    ) {
        when {
            isInstagram && instance.displayName == "Instagram (Clone 1)" -> "Instagram"
            isWhatsapp && instance.displayName == "WhatsApp (Personal)" -> "WhatsApp"
            isGmail && instance.displayName.startsWith("Gmail", ignoreCase = true) -> "Gmail"
            isFacebook && instance.displayName.startsWith("Facebook", ignoreCase = true) -> "Facebook"
            isYoutube && instance.displayName.startsWith("YouTube", ignoreCase = true) -> "YouTube"
            isDiscord && instance.displayName.startsWith("Discord", ignoreCase = true) -> "Discord"
            else -> instance.displayName.ifBlank { instance.originalName }
        }
    }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    onClick = onLaunch,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                )
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .testTag("clone_card_${instance.instanceId}")
        ) {
            // App Icon Container
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(68.dp)
            ) {
                if (appIcon != null) {
                    AppIconImage(
                        iconDrawable = appIcon,
                        fallbackName = instance.originalName,
                        modifier = Modifier.size(64.dp)
                    )
                } else if (isInstagram) {
                    // Authentic Instagram Gradient Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF833AB4),
                                        Color(0xFFE1306C),
                                        Color(0xFFF77737),
                                        Color(0xFFFCAF45)
                                    )
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Instagram",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else if (isWhatsapp) {
                    // Authentic WhatsApp Green Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF25D366),
                                        Color(0xFF128C7E)
                                    )
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Chat,
                            contentDescription = "WhatsApp",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                } else if (isGmail) {
                    // Authentic Gmail Red Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFEA4335),
                                        Color(0xFFC5221F)
                                    )
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Email,
                            contentDescription = "Gmail",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                } else if (isFacebook) {
                    // Authentic Facebook Royal Blue Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1877F2),
                                        Color(0xFF0C5DC7)
                                    )
                                )
                            )
                    ) {
                        Text(
                            text = "f",
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.offset(y = 2.dp)
                        )
                    }
                } else if (isYoutube) {
                    // Authentic YouTube Crimson Red Play Button Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFFF0000),
                                        Color(0xFFCC0000)
                                    )
                                )
                            )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(42.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "YouTube",
                                tint = Color(0xFFFF0000),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else if (isDiscord) {
                    // Authentic Discord Blurple Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF5865F2),
                                        Color(0xFF4752C4)
                                    )
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = "Discord",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                } else {
                    AppIconImage(
                        iconDrawable = null,
                        fallbackName = instance.originalName,
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Subtle indicator dot in bottom-right corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = cleanLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // Long-press Context Menu (Edit or Delete)
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}
