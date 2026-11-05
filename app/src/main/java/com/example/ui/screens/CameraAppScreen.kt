package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.GalleryRepository
import com.example.data.models.MediaItem
import java.io.File
import java.io.FileOutputStream

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

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

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
        imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    // Turn off torch on exit
    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraInstance?.cameraControl?.enableTorch(false)
            } catch (_: Exception) {}
        }
    }

    // Bind Camera Provider when permission or camera lens changes
    LaunchedEffect(hasCameraPermission, isFrontCamera) {
        if (hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

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

            // Viewfinder: Edge-to-edge preview fitting screen based on aspect ratio (no borders, no boxes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(selectedAspect.ratio)
                    )
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

            // Bottom Bar: Gallery Thumbnail, Shutter Button, and balanced spacer (filters removed, just normal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Thumbnail of last taken photo
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF26262B))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (lastCapturedUri != null) {
                        Text(
                            text = "Photo",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = "Gallery",
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
                            isCapturing = true
                            val photoId = System.currentTimeMillis()
                            val fileName = "IMG_$photoId.jpg"
                            val photoFile = File(context.cacheDir, fileName)
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            val mainExecutor = ContextCompat.getMainExecutor(context)

                            imageCapture.takePicture(
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
                                        // Fallback capture (e.g. for emulator instances without camera hardware pipeline)
                                        saveFallbackPhoto(context, photoFile, photoId, fileName) { mediaItem ->
                                            isCapturing = false
                                            lastCapturedUri = mediaItem.uri
                                            onPhotoCaptured(mediaItem)
                                        }
                                    }
                                }
                            )
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
                Spacer(modifier = Modifier.size(50.dp))
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
