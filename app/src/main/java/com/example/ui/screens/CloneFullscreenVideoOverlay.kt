package com.example.ui.screens

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CloneFullscreenVideoOverlay(
    activeCustomView: View,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("clone_fullscreen_video_overlay")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                (activeCustomView.parent as? ViewGroup)?.removeView(activeCustomView)
                activeCustomView
            }
        )
    }
}
