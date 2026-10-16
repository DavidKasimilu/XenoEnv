package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.data.models.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Global in-memory cache for extracted video thumbnails
object VideoThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available runtime memory
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? = memoryCache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        memoryCache.put(key, bitmap)
    }
}

/**
 * Loads video thumbnail Bitmap via MediaMetadataRetriever or ContentResolver as fallback
 */
suspend fun extractVideoThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    val key = uri.toString()
    VideoThumbnailCache.get(key)?.let { return@withContext it }

    var bitmap: Bitmap? = null

    // Try ContentResolver loadThumbnail on Android 10+ for content URIs
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
        try {
            bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 384), null)
        } catch (_: Exception) {}
    }

    // Try MediaMetadataRetriever if not loaded yet
    if (bitmap == null) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            if (uri.scheme == "content" || uri.scheme == "android.resource") {
                retriever.setDataSource(context, uri)
            } else if (uri.scheme == "http" || uri.scheme == "https") {
                retriever.setDataSource(uri.toString(), HashMap())
            } else {
                uri.path?.let { retriever.setDataSource(it) }
            }
            bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
    }

    if (bitmap != null) {
        // Downscale large extracted frames to max 512px width for optimal memory footprint and scroll performance
        if (bitmap.width > 640 || bitmap.height > 640) {
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
            val targetWidth = 512
            val targetHeight = (512 / aspect).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            if (scaled != bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }
        VideoThumbnailCache.put(key, bitmap)
    }
    bitmap
}

@Composable
fun VideoThumbnailImage(
    item: MediaItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var extractedBitmap by remember(item.uri) {
        mutableStateOf(VideoThumbnailCache.get(item.uri.toString()))
    }

    // ImageLoader configured with VideoFrameDecoder
    val videoImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    LaunchedEffect(item.uri) {
        if (extractedBitmap == null && item.customThumbnailUri == null) {
            extractedBitmap = extractVideoThumbnail(context, item.uri)
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF1A1A1A))
    ) {
        when {
            // 1. Explicit poster thumbnail URI provided
            item.customThumbnailUri != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.customThumbnailUri)
                        .size(512, 384)
                        .memoryCacheKey("poster_${item.customThumbnailUri}")
                        .diskCacheKey("poster_${item.customThumbnailUri}")
                        .crossfade(true)
                        .build(),
                    contentDescription = item.displayName,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // 2. Extracted bitmap from MediaMetadataRetriever
            extractedBitmap != null -> {
                Image(
                    bitmap = extractedBitmap!!.asImageBitmap(),
                    contentDescription = item.displayName,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // 3. Coil VideoFrameDecoder fallback
            else -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(512, 384)
                        .memoryCacheKey("vframe_${item.uri}")
                        .diskCacheKey("vframe_${item.uri}")
                        .videoFrameMillis(1000)
                        .crossfade(true)
                        .build(),
                    imageLoader = videoImageLoader,
                    contentDescription = item.displayName,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Subtle gradient overlay at bottom for badge readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )
    }
}

/**
 * Full video card for the Videos tab in Gallery
 */
@Composable
fun VideoGridItemCard(
    item: MediaItem,
    onClick: () -> Unit,
    onDelete: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("video_item_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 16:9 Video Thumbnail with Play Button & Duration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            ) {
                VideoThumbnailImage(
                    item = item,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Play Button in Center
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Top Left: HD or Video tag
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (item.width >= 1280 || item.height >= 720) "HD Video" else "Video",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom Right: Duration Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = item.formattedDuration,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Video Meta Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.formattedSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )

                        if (onDelete != null) {
                            IconButton(
                                onClick = { onDelete(item) },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("delete_video_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete video",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
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
