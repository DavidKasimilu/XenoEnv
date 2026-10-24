package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ClonedAppInstance
import com.example.ui.components.AppIconImage
import com.example.ui.components.parseHexColor
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpaceSandboxScreen(
    clones: List<ClonedAppInstance>,
    activeDockIds: List<String>,
    minimizedCloneIds: List<String> = emptyList(),
    onLaunchInstance: (String) -> Unit,
    onOpenAddClone: () -> Unit,
    onOpenEditClone: (ClonedAppInstance) -> Unit,
    onDeleteClone: (String) -> Unit,
    onCloseFromDock: (String) -> Unit,
    onOpenMedia: () -> Unit = {},
    onOpenDocs: () -> Unit = {},
    onOpenMusic: () -> Unit = {},
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
                    isMinimized = clone.instanceId in minimizedCloneIds,
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

            MusicShortcutIconItem(
                onClick = onOpenMusic
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

@Composable
private fun MusicShortcutIconItem(
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("clone_space_music_shortcut")
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
                                Color(0xFFEC4899),
                                Color(0xFF8B5CF6)
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Headphones,
                    contentDescription = "Music",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Music",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Renders the authentic brand icon for a ClonedAppInstance (Instagram, WhatsApp, Gmail,
 * Facebook, YouTube, Discord, or installed package icon). Used in both the Clone Space
 * grid and the Floating Minimized App Icon overlay.
 */
@Composable
fun CloneAppVisualIcon(
    instance: ClonedAppInstance,
    iconSize: Dp = 64.dp,
    cornerRadius: Dp = 18.dp,
    showBadgeDot: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val badgeColor = remember(instance.badgeColorHex) { parseHexColor(instance.badgeColorHex) }

    val isInstagram = remember(instance) {
        instance.packageName.contains("instagram", ignoreCase = true) ||
                instance.originalName.contains("instagram", ignoreCase = true) ||
                instance.displayName.contains("instagram", ignoreCase = true) ||
                instance.targetUrl.contains("instagram.com", ignoreCase = true)
    }

    val isWhatsapp = remember(instance) {
        instance.packageName.contains("whatsapp", ignoreCase = true) ||
                instance.originalName.contains("whatsapp", ignoreCase = true) ||
                instance.displayName.contains("whatsapp", ignoreCase = true) ||
                instance.targetUrl.contains("whatsapp.com", ignoreCase = true)
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

    val glyphSize = iconSize * 0.5f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(iconSize + 4.dp)
    ) {
        if (appIcon != null) {
            AppIconImage(
                iconDrawable = appIcon,
                fallbackName = instance.originalName,
                modifier = Modifier.size(iconSize)
            )
        } else if (isInstagram) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(cornerRadius))
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
                    modifier = Modifier.size(glyphSize)
                )
            }
        } else if (isWhatsapp) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(cornerRadius))
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
                    modifier = Modifier.size(glyphSize * 0.95f)
                )
            }
        } else if (isGmail) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(cornerRadius))
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
                    modifier = Modifier.size(glyphSize * 0.95f)
                )
            }
        } else if (isFacebook) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(cornerRadius))
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
                    fontSize = (iconSize.value * 0.65f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.offset(y = 2.dp)
                )
            }
        } else if (isYoutube) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(cornerRadius))
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
                        .width(iconSize * 0.65f)
                        .height(iconSize * 0.46f)
                        .clip(RoundedCornerShape(cornerRadius * 0.5f))
                        .background(Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "YouTube",
                        tint = Color(0xFFFF0000),
                        modifier = Modifier.size(iconSize * 0.38f)
                    )
                }
            }
        } else if (isDiscord) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(cornerRadius))
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
                    modifier = Modifier.size(glyphSize * 1.05f)
                )
            }
        } else {
            AppIconImage(
                iconDrawable = null,
                fallbackName = instance.originalName,
                modifier = Modifier.size(iconSize)
            )
        }

        if (showBadgeDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}

fun resolveCloneCleanLabel(instance: ClonedAppInstance): String {
    val isInstagram = instance.packageName.contains("instagram", ignoreCase = true) ||
            instance.originalName.contains("instagram", ignoreCase = true) ||
            instance.displayName.contains("instagram", ignoreCase = true)
    val isWhatsapp = instance.packageName.contains("whatsapp", ignoreCase = true) ||
            instance.originalName.contains("whatsapp", ignoreCase = true) ||
            instance.displayName.contains("whatsapp", ignoreCase = true)
    val isGmail = instance.packageName.contains("com.google.android.gm", ignoreCase = true) ||
            instance.originalName.contains("gmail", ignoreCase = true) ||
            instance.displayName.contains("gmail", ignoreCase = true) ||
            instance.targetUrl.contains("mail.google.com", ignoreCase = true)
    val isFacebook = instance.packageName.contains("facebook", ignoreCase = true) ||
            instance.originalName.contains("facebook", ignoreCase = true) ||
            instance.displayName.contains("facebook", ignoreCase = true) ||
            instance.targetUrl.contains("facebook.com", ignoreCase = true)
    val isYoutube = instance.packageName.contains("youtube", ignoreCase = true) ||
            instance.originalName.contains("youtube", ignoreCase = true) ||
            instance.displayName.contains("youtube", ignoreCase = true) ||
            instance.targetUrl.contains("youtube.com", ignoreCase = true) ||
            instance.targetUrl.contains("youtu.be", ignoreCase = true)
    val isDiscord = instance.packageName.contains("discord", ignoreCase = true) ||
            instance.originalName.contains("discord", ignoreCase = true) ||
            instance.displayName.contains("discord", ignoreCase = true) ||
            instance.targetUrl.contains("discord.com", ignoreCase = true) ||
            instance.targetUrl.contains("discord.gg", ignoreCase = true)

    return when {
        isInstagram && instance.displayName == "Instagram (Clone 1)" -> "Instagram"
        isWhatsapp && instance.displayName == "WhatsApp (Personal)" -> "WhatsApp"
        isGmail && instance.displayName.startsWith("Gmail", ignoreCase = true) -> "Gmail"
        isFacebook && instance.displayName.startsWith("Facebook", ignoreCase = true) -> "Facebook"
        isYoutube && instance.displayName.startsWith("YouTube", ignoreCase = true) -> "YouTube"
        isDiscord && instance.displayName.startsWith("Discord", ignoreCase = true) -> "Discord"
        else -> instance.displayName.ifBlank { instance.originalName }
    }
}

/**
 * Floating Minimized Clone App Icons Overlay.
 * Displays each minimized clone app as a draggable, tappable floating icon of its own brand icon
 * (e.g., Instagram icon for Instagram, WhatsApp icon for WhatsApp).
 * Tapping a floating icon immediately resizes/restores that clone app to full normal view.
 */
@Composable
fun MinimizedCloneFloatingOverlay(
    minimizedClones: List<ClonedAppInstance>,
    onRestoreClone: (String) -> Unit,
    onCloseMinimizedClone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (minimizedClones.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(end = 14.dp, bottom = 92.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            minimizedClones.forEach { clone ->
                DraggableFloatingCloneIcon(
                    instance = clone,
                    onRestore = { onRestoreClone(clone.instanceId) },
                    onClose = { onCloseMinimizedClone(clone.instanceId) }
                )
            }
        }
    }
}

@Composable
private fun DraggableFloatingCloneIcon(
    instance: ClonedAppInstance,
    onRestore: () -> Unit,
    onClose: () -> Unit
) {
    var offsetX by remember(instance.instanceId) { mutableFloatStateOf(0f) }
    var offsetY by remember(instance.instanceId) { mutableFloatStateOf(0f) }
    val cleanLabel = remember(instance) { resolveCloneCleanLabel(instance) }
    val badgeColor = remember(instance.badgeColorHex) { parseHexColor(instance.badgeColorHex) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(instance.instanceId) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .testTag("floating_minimized_clone_${instance.instanceId}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onRestore)
                .padding(4.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, badgeColor.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
            ) {
                CloneAppVisualIcon(
                    instance = instance,
                    iconSize = 58.dp,
                    cornerRadius = 16.dp,
                    showBadgeDot = false
                )

                // Subtle expand indicator in bottom-right corner
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .size(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.OpenInFull,
                            contentDescription = "Restore $cleanLabel",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 4.dp,
                shadowElevation = 3.dp
            ) {
                Text(
                    text = cleanLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }

        // Small Close (X) button on top-right corner of the floating icon
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .testTag("floating_minimized_close_${instance.instanceId}")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close minimized $cleanLabel",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloneAppIconItem(
    instance: ClonedAppInstance,
    isMinimized: Boolean = false,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    val cleanLabel = remember(instance) { resolveCloneCleanLabel(instance) }

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
            Box(contentAlignment = Alignment.Center) {
                CloneAppVisualIcon(
                    instance = instance,
                    iconSize = 64.dp,
                    cornerRadius = 18.dp,
                    showBadgeDot = true
                )
                if (isMinimized) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                    ) {
                        Text(
                            text = "MIN",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
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
