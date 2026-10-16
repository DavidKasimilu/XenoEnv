package com.example

import android.net.Uri
import com.example.data.models.IncomingCallPopup
import com.example.data.models.IncomingSmsPopup
import com.example.data.models.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun testMediaItemFormatting() {
        val videoItem = MediaItem(
            id = 100L,
            uri = Uri.parse("https://example.com/video.mp4"),
            displayName = "TestVideo.mp4",
            dateAdded = 1700000000L,
            size = 15500000L,
            durationMs = 95000L, // 1 min 35 sec
            isVideo = true,
            width = 1920,
            height = 1080
        )

        assertTrue(videoItem.isVideo)
        assertEquals("1:35", videoItem.formattedDuration)
        assertTrue(videoItem.formattedSize.contains("MB"))
        assertNotNull(videoItem.formattedDate)

        val photoItem = MediaItem(
            id = 200L,
            uri = Uri.parse("https://example.com/photo.jpg"),
            displayName = "TestPhoto.jpg",
            dateAdded = 1700000000L,
            size = 512000L,
            durationMs = null,
            isVideo = false
        )

        assertFalse(photoItem.isVideo)
        assertEquals("0:00", photoItem.formattedDuration)
        assertTrue(photoItem.formattedSize.contains("KB"))
    }

    @Test
    fun testIncomingPopupsCreation() {
        val smsPopup = IncomingSmsPopup(
            threadId = 1L,
            senderName = "Alice",
            senderNumber = "+15551234567",
            body = "Hello there!"
        )

        assertEquals("Alice", smsPopup.senderName)
        assertEquals("+15551234567", smsPopup.senderNumber)
        assertEquals("Hello there!", smsPopup.body)
        assertNotNull(smsPopup.id)

        val callPopup = IncomingCallPopup(
            callerName = "Bob",
            callerNumber = "+15559876543"
        )

        assertEquals("Bob", callPopup.callerName)
        assertEquals("+15559876543", callPopup.callerNumber)
        assertNotNull(callPopup.id)
    }

    @Test
    fun testGalleryNoDemoMediaAndDeletion() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = com.example.data.GalleryRepository(context)

        // Verify that default getMediaItems does not contain demo videos or photos
        val initialItems = repo.getMediaItems()
        assertTrue(initialItems.none { it.id in 8000L..9999L })
        assertTrue(initialItems.none { it.uri.toString().contains("BigBuckBunny") })
        assertTrue(initialItems.none { it.uri.toString().contains("unsplash") })

        // Verify adding and deleting custom media item
        val customItem = MediaItem(
            id = 12345L,
            uri = Uri.parse("content://media/external/images/media/12345"),
            displayName = "MyUserPhoto.jpg",
            dateAdded = System.currentTimeMillis() / 1000,
            size = 1024L,
            isVideo = false
        )
        repo.addCustomMediaItem(customItem)
        val withCustom = repo.getMediaItems()
        assertTrue(withCustom.any { it.id == 12345L })

        // Delete media item
        repo.deleteMediaItem(customItem)
        val afterDelete = repo.getMediaItems()
        assertFalse(afterDelete.any { it.id == 12345L })
    }
}
