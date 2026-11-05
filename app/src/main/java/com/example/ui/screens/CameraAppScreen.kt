package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GalleryRepository
import com.example.data.models.MediaItem
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class CameraFilter(val label: String) {
    NORMAL("Normal"),
    BANDW("B&W"),
    SEPIA("Sepia"),
    COOL("Cool"),
    WARM("Warm")
}

enum class CameraAspect(val label: String, val ratio: Float) {
    RATIO_1_1("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f)
}

@Composable
fun CameraAppScreen(
    onClose: () -> Unit,
    onPhotoCaptured: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(CameraFilter.NORMAL) }
    var selectedAspect by remember { mutableStateOf(CameraAspect.RATIO_4_3) }
    var flashOn by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var showThumbnailPreview by remember { mutableStateOf(false) }

    BackHandler {
        onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_app_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Back, Flash, Aspect Ratio
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close Camera",
                        tint = Color.White
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { flashOn = !flashOn }) {
                        Icon(
                            imageVector = Icons.Filled.FlashOn,
                            contentDescription = "Flash",
                            tint = if (flashOn) Color(0xFFFFD700) else Color.White
                        )
                    }

                    // Aspect Ratio switcher
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.DarkGray.copy(alpha = 0.6f),
                        modifier = Modifier.clickable {
                            selectedAspect = when (selectedAspect) {
                                CameraAspect.RATIO_4_3 -> CameraAspect.RATIO_1_1
                                CameraAspect.RATIO_1_1 -> CameraAspect.RATIO_16_9
                                CameraAspect.RATIO_16_9 -> CameraAspect.RATIO_4_3
                            }
                        }
                    ) {
                        Text(
                            text = selectedAspect.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Viewfinder Preview Box with Aspect Ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(selectedAspect.ratio)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            when (selectedFilter) {
                                CameraFilter.BANDW -> Color.Gray
                                CameraFilter.SEPIA -> Color(0xFF704214)
                                CameraFilter.COOL -> Color(0xFF1A365D)
                                CameraFilter.WARM -> Color(0xFF744210)
                                else -> Color(0xFF1E1E24)
                            }
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Camera Mode Badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FlipCameraAndroid,
                                contentDescription = null,
                                tint = if (isFrontCamera) Color(0xFF10B981) else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isFrontCamera) "Front / Selfie" else "Rear / Main",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Simulated camera preview scene or canvas drawing
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Live Viewfinder (${selectedFilter.label})",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Filters Strip & Bottom Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(vertical = 20.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Filter selection chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CameraFilter.values().forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Color.White else Color.DarkGray,
                            modifier = Modifier.clickable { selectedFilter = filter }
                        ) {
                            Text(
                                text = filter.label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom row: Thumbnail gallery preview, Shutter button, Switch camera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail preview stub
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.DarkGray)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (lastCapturedUri != null) {
                            Text(
                                text = "Photo",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Shutter Button
                    Surface(
                        modifier = Modifier
                            .size(76.dp)
                            .clickable {
                                // Capture photo action
                                val photoId = System.currentTimeMillis()
                                val fileName = "IMG_$photoId.jpg"
                                val file = File(context.cacheDir, fileName)
                                try {
                                    val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bitmap)
                                    canvas.drawColor(
                                        when (selectedFilter) {
                                            CameraFilter.BANDW -> android.graphics.Color.DKGRAY
                                            CameraFilter.SEPIA -> android.graphics.Color.rgb(112, 66, 20)
                                            CameraFilter.COOL -> android.graphics.Color.rgb(26, 54, 93)
                                            CameraFilter.WARM -> android.graphics.Color.rgb(116, 66, 16)
                                            else -> android.graphics.Color.rgb(40, 40, 45)
                                        }
                                    )
                                    FileOutputStream(file).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                    }
                                    val uri = Uri.fromFile(file)
                                    lastCapturedUri = uri
                                    val mediaItem = MediaItem(
                                        id = photoId,
                                        uri = uri,
                                        displayName = fileName,
                                        dateAdded = System.currentTimeMillis(),
                                        isVideo = false,
                                        durationMs = 0L,
                                        size = file.length()
                                    )
                                    val repo = GalleryRepository(context)
                                    repo.addCustomMediaItem(mediaItem)
                                    onPhotoCaptured(mediaItem)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(4.dp, Color.White)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        ) {}
                    }

                    // Camera Switcher
                    IconButton(
                        onClick = { isFrontCamera = !isFrontCamera },
                        modifier = Modifier.testTag("switch_camera_facing_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FlipCameraAndroid,
                            contentDescription = "Switch Camera",
                            tint = if (isFrontCamera) Color(0xFF10B981) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
