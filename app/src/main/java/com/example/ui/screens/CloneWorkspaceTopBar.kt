package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ClonedAppInstance

@Composable
fun CloneWorkspaceTopBar(
    instance: ClonedAppInstance,
    badgeColor: Color,
    currentTitle: String,
    currentUrl: String,
    isWhatsapp: Boolean,
    isWhatsAppPhoneUi: Boolean,
    isWhatsAppChatOpen: Boolean,
    isYoutube: Boolean,
    isYoutubeShortsPage: Boolean,
    showYoutubeOfflineLibrary: Boolean,
    offlineVideosCount: Int,
    isDownloadingAny: Boolean,
    isCallActive: Boolean,
    isVideoCallActive: Boolean,
    isSpeakerphoneOn: Boolean,
    hasMicPermission: Boolean,
    hasCameraPermission: Boolean,
    onBackClick: () -> Unit,
    onChatsClick: () -> Unit,
    onYoutubeDownloadClick: () -> Unit,
    onToggleYoutubeOfflineLibrary: () -> Unit,
    onToggleCallSpeakerphone: () -> Unit,
    onSwitchCamera: () -> Unit,
    onCallPermissionsClick: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onReloadClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("workspace_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isWhatsapp && isWhatsAppChatOpen) "Back to Chats" else "Back",
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            CloneAppVisualIcon(
                instance = instance,
                iconSize = 24.dp,
                cornerRadius = 7.dp,
                showBadgeDot = false
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val cleanTitle = remember(instance.displayName, instance.originalName) {
                    val raw = instance.displayName.ifBlank { instance.originalName }
                    raw.replace(" Clone", "", ignoreCase = true)
                        .replace("Clone", "", ignoreCase = true)
                        .replace("(Clone)", "", ignoreCase = true)
                        .trim()
                        .ifBlank { instance.originalName }
                }

                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = instance.badgeTag,
                        color = badgeColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                if (isWhatsapp && isWhatsAppPhoneUi && isWhatsAppChatOpen) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF25D366).copy(alpha = 0.18f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onChatsClick)
                            .testTag("whatsapp_back_to_chats_pill")
                    ) {
                        Text(
                            text = "Chats",
                            color = Color(0xFF25D366),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isYoutube) {
                    val isWatchingShortsNow = isYoutubeShortsPage || currentUrl.contains("/shorts", ignoreCase = true)
                    if (!isWatchingShortsNow) {
                        IconButton(
                            onClick = onYoutubeDownloadClick,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("youtube_download_video_btn")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDownloadingAny) Color(0xFFFF0000).copy(alpha = 0.2f) else Color.Transparent,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isDownloadingAny) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFFF0000),
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = "Download YouTube Video for Offline",
                                            tint = Color(0xFFFF0000),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleYoutubeOfflineLibrary,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("youtube_offline_library_btn")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (showYoutubeOfflineLibrary) Color(0xFFFF0000).copy(alpha = 0.22f) else Color.Transparent,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (offlineVideosCount > 0) Icons.Filled.DownloadDone else Icons.Filled.VideoLibrary,
                                    contentDescription = "Offline Downloaded Videos ($offlineVideosCount)",
                                    tint = if (showYoutubeOfflineLibrary || offlineVideosCount > 0) Color(0xFFFF0000) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                if (offlineVideosCount > 0) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF0000))
                                    ) {
                                        Text(
                                            text = offlineVideosCount.coerceAtMost(99).toString(),
                                            color = Color.White,
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isWhatsapp) {
                    if (isCallActive) {
                        IconButton(
                            onClick = onToggleCallSpeakerphone,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("whatsapp_call_speaker_btn")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSpeakerphoneOn) Color(0xFF25D366).copy(alpha = 0.2f) else Color.Transparent,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSpeakerphoneOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.PhoneInTalk,
                                        contentDescription = "Toggle Call Speakerphone",
                                        tint = if (isSpeakerphoneOn) Color(0xFF25D366) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (isVideoCallActive) {
                            IconButton(
                                onClick = onSwitchCamera,
                                modifier = Modifier
                                .size(30.dp)
                                .testTag("whatsapp_switch_camera_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Cameraswitch,
                                    contentDescription = "Switch Front/Back Camera",
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onCallPermissionsClick,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("whatsapp_call_permissions_btn")
                    ) {
                        val callsReady = hasMicPermission && hasCameraPermission
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (callsReady) Color(0xFF25D366).copy(alpha = 0.16f) else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (callsReady) Icons.Filled.Videocam else Icons.Filled.Mic,
                                    contentDescription = if (callsReady) "Voice & Video Calls Ready" else "Grant Mic & Camera Permissions",
                                    tint = if (callsReady) Color(0xFF25D366) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onToggleDesktopMode,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("toggle_desktop_mode_btn")
                ) {
                    val isPhoneUiHighlighted = isWhatsapp && isWhatsAppPhoneUi
                    val isDesktopHighlighted = (!isWhatsapp && instance.isDesktopMode) || (isWhatsapp && !isWhatsAppPhoneUi)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isPhoneUiHighlighted -> Color(0xFF25D366).copy(alpha = 0.16f)
                            isDesktopHighlighted -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDesktopHighlighted) Icons.Filled.DesktopMac else Icons.Filled.PhoneAndroid,
                                contentDescription = if (isDesktopHighlighted) "Desktop View Active" else "Phone Screen UI Active",
                                tint = when {
                                    isPhoneUiHighlighted -> Color(0xFF25D366)
                                    isDesktopHighlighted -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onReloadClick,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("workspace_reload_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reload",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("workspace_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close to Space",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
