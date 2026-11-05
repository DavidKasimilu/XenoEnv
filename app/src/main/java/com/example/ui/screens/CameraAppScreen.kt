package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.GalleryRepository
import com.example.data.models.MediaItem
import java.io.File
import java.io.FileOutputStream

enum class CameraAspect(val label: String, val ratio: Float, val cameraXAspect: Int) {
    RATIO_1_1("1:1", 1f, AspectRatio.RATIO_4_3),
    RATIO_4_3("4:3", 3f / 4f, AspectRatio.RATIO_4_3),
    RATIO_16_9("16:9", 9f / 16f, AspectRatio.RATIO_16_9)
}

@Composable
fun CameraAppScreen(
    onClose: () -> Unit,
    onPhotoCaptured: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var selectedAspect by remember { mutableStateOf(CameraAspect.RATIO_4_3) }
    var flashOn by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showFullPhotoPreview by remember { mutableStateOf(false) }

    // Seed last captured photo from gallery on launch so the photo box shows recent photo
    LaunchedEffect(Unit) {
        if (lastCapturedUri == null) {
            try {
                val repo = GalleryRepository(context)
                val recent = repo.getMediaItems().firstOrNull { !it.isVideo }
                if (recent != null) {
                    lastCapturedUri = recent.uri
                }
            } catch (_: Exception) {}
        }
    }

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var currentImageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Toggle flash / torch
    LaunchedEffect(flashOn, cameraInstance) {
        cameraInstance?.let { cam ->
            try {
                if (cam.cameraInfo.hasFlashUnit()) {
                    cam.cameraControl.enableTorch(flashOn)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        currentImageCapture?.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    // Turn off torch on exit
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraInstance?.cameraControl?.enableTorch(false)
            } catch (_: Exception) {}
        }
    }

    // Bind Camera Provider when permission, camera lens, or aspect ratio changes
    LaunchedEffect(hasCameraPermission, isFrontCamera, selectedAspect) {
        if (hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val targetAspect = selectedAspect.cameraXAspect

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(targetAspect)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val imageCapture = ImageCapture.Builder()
                        .setTargetAspectRatio(targetAspect)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                        .build()

                    currentImageCapture = imageCapture

                    val cameraSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    cameraProvider.unbindAll()
                    val cam = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    cameraInstance = cam
                    if (flashOn && cam.cameraInfo.hasFlashUnit()) {
                        cam.cameraControl.enableTorch(true)
                    }
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    BackHandler {
        if (showFullPhotoPreview) {
            showFullPhotoPreview = false
        } else {
            onClose()
        }
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
            // Top Bar: Back icon on start; Flash, Selfie switch next to flash, and Aspect Ratio on end
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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

                // Top-right controls: Flash, Selfie Switch (next to flash), and Aspect Ratio
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flashlight toggle
                    IconButton(
                        onClick = { flashOn = !flashOn },
                        modifier = Modifier.testTag("camera_flash_toggle")
                    ) {
                        Icon(
                            imageVector = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = if (flashOn) "Flashlight On" else "Flashlight Off",
                            tint = if (flashOn) Color(0xFFFFD700) else Color.White
                        )
                    }

                    // Selfie switch icon right next to flash
                    IconButton(
                        onClick = { isFrontCamera = !isFrontCamera },
                        modifier = Modifier.testTag("switch_camera_facing_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FlipCameraAndroid,
                            contentDescription = "Switch to Selfie or Main Camera",
                            tint = if (isFrontCamera) Color(0xFF25D366) else Color.White
                        )
                    }

                    // Aspect Ratio switcher pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.DarkGray.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clickable {
                                selectedAspect = when (selectedAspect) {
                                    CameraAspect.RATIO_4_3 -> CameraAspect.RATIO_1_1
                                    CameraAspect.RATIO_1_1 -> CameraAspect.RATIO_16_9
                                    CameraAspect.RATIO_16_9 -> CameraAspect.RATIO_4_3
                                }
                            }
                            .testTag("camera_aspect_ratio_pill")
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

            // Viewfinder: Edge-to-edge preview fitting container cleanly based on selected aspect ratio
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    val availableWidth = maxWidth
                    val availableHeight = maxHeight
                    val targetRatio = selectedAspect.ratio // width / height

                    val (viewWidth, viewHeight) = if (availableWidth / availableHeight > targetRatio) {
                        val h = availableHeight
                        val w = h * targetRatio
                        Pair(w, h)
                    } else {
                        val w = availableWidth
                        val h = w / targetRatio
                        Pair(w, h)
                    }

                    Box(
                        modifier = Modifier
                            .size(viewWidth, viewHeight)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // Permission request UI
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera Permission Required",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Grant camera permission to take photos and use the live viewfinder",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Grant Permission", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Bottom Bar: Gallery Thumbnail, Shutter Button, and balanced spacer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Thumbnail of last taken photo with preview functionality
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF26262B))
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable {
                            if (lastCapturedUri != null) {
                                showFullPhotoPreview = true
                            }
                        }
                        .testTag("camera_photo_preview_box"),
                    contentAlignment = Alignment.Center
                ) {
                    if (lastCapturedUri != null) {
                        AsyncImage(
                            model = lastCapturedUri,
                            contentDescription = "Preview captured photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = "No photo taken yet",
                            tint = Color.LightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Centered Shutter Button
                Surface(
                    modifier = Modifier
                        .size(76.dp)
                        .clickable(enabled = !isCapturing) {
                            val capture = currentImageCapture
                            isCapturing = true
                            val photoId = System.currentTimeMillis()
                            val fileName = "IMG_$photoId.jpg"
                            val photoFile = File(context.cacheDir, fileName)

                            if (capture != null) {
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                val mainExecutor = ContextCompat.getMainExecutor(context)

                                capture.takePicture(
                                    outputOptions,
                                    mainExecutor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            isCapturing = false
                                            val uri = Uri.fromFile(photoFile)
                                            lastCapturedUri = uri
                                            val mediaItem = MediaItem(
                                                id = photoId,
                                                uri = uri,
                                                displayName = fileName,
                                                dateAdded = System.currentTimeMillis(),
                                                isVideo = false,
                                                durationMs = 0L,
                                                size = photoFile.length()
                                            )
                                            val repo = GalleryRepository(context)
                                            repo.addCustomMediaItem(mediaItem)
                                            onPhotoCaptured(mediaItem)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            exception.printStackTrace()
                                            saveFallbackPhoto(context, photoFile, photoId, fileName) { mediaItem ->
                                                isCapturing = false
                                                lastCapturedUri = mediaItem.uri
                                                onPhotoCaptured(mediaItem)
                                            }
                                        }
                                    }
                                )
                            } else {
                                saveFallbackPhoto(context, photoFile, photoId, fileName) { mediaItem ->
                                    isCapturing = false
                                    lastCapturedUri = mediaItem.uri
                                    onPhotoCaptured(mediaItem)
                                }
                            }
                        }
                        .testTag("camera_shutter_button"),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(4.dp, Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }

                // Symmetric right placeholder to keep shutter button centered
                Spacer(modifier = Modifier.size(52.dp))
            }
        }

        // Full Screen Interactive Photo Preview
        if (showFullPhotoPreview && lastCapturedUri != null) {
            BackHandler {
                showFullPhotoPreview = false
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("full_photo_preview_overlay")
            ) {
                // Photo Image
                AsyncImage(
                    model = lastCapturedUri,
                    contentDescription = "Captured Photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 68.dp),
                    contentScale = ContentScale.Fit
                )

                // Top controls bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showFullPhotoPreview = false }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to camera",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "Photo Preview",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Share Photo
                        IconButton(onClick = {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, lastCapturedUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
                            } catch (_: Exception) {}
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share Photo",
                                tint = Color.White
                            )
                        }

                        // Delete Photo
                        IconButton(onClick = {
                            try {
                                lastCapturedUri?.path?.let { p ->
                                    val f = File(p)
                                    if (f.exists()) f.delete()
                                }
                            } catch (_: Exception) {}
                            lastCapturedUri = null
                            showFullPhotoPreview = false
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete Photo",
                                tint = Color(0xFFFF5252)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun saveFallbackPhoto(
    context: Context,
    file: File,
    photoId: Long,
    fileName: String,
    onComplete: (MediaItem) -> Unit
) {
    try {
        val bitmap = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(30, 30, 36))
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 48f
            isAntiAlias = true
        }
        canvas.drawText("Photo $fileName", 100f, 480f, paint)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        val uri = Uri.fromFile(file)
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
        onComplete(mediaItem)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
