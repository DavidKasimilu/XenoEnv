package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.viewmodel.XenoTab

@Composable
fun FloatingWindowTopBar(
    currentTab: XenoTab,
    isFitToScreen: Boolean,
    windowScale: Float,
    onToggleFit: () -> Unit,
    onScaleStep: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabTitle = when (currentTab) {
        XenoTab.MESSAGES -> "Messages"
        XenoTab.BROWSER -> "Browser"
        XenoTab.CLONE_SPACE -> "Clone Space"
        else -> "Rabbit Floating App"
    }

    val tabIcon: ImageVector = when (currentTab) {
        XenoTab.MESSAGES -> Icons.Filled.Message
        XenoTab.BROWSER -> Icons.Filled.Public
        XenoTab.CLONE_SPACE -> Icons.Filled.Layers
        else -> Icons.Filled.Message
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("floating_window_top_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Rabbit Icon / Active Tab Icon and Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.black_rabbit_icon),
                        contentDescription = "Rabbit Logo",
                        modifier = Modifier
                            .size(26.dp)
                            .padding(2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = tabIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = tabTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Right: Scale controls, Fit Icon, Settings Icon, Minimize Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Resize scale step controls (maintains device ratio)
                if (!isFitToScreen) {
                    IconButton(
                        onClick = { onScaleStep(-0.06f) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("floating_scale_down_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Scale down window",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onScaleStep(0.06f) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("floating_scale_up_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Scale up window",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Fit to Full Screen / Exit Fit Icon
                IconButton(
                    onClick = onToggleFit,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("floating_fit_button")
                ) {
                    Icon(
                        imageVector = if (isFitToScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFitToScreen) "Exit full screen fit" else "Fit to full screen",
                        tint = if (isFitToScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Floating App Settings Icon
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("floating_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Floating App Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Minimize Icon (returns to floating bubble with active tab icon)
                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("floating_minimize_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "Minimize to floating icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
