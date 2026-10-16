package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AvatarGradients = listOf(
    listOf(Color(0xFF0284C7), Color(0xFF0369A1)),
    listOf(Color(0xFF6366F1), Color(0xFF4338CA)),
    listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
    listOf(Color(0xFFEC4899), Color(0xFFBE185D)),
    listOf(Color(0xFF10B981), Color(0xFF047857)),
    listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
    listOf(Color(0xFF14B8A6), Color(0xFF0F766E))
)

@Composable
fun ContactAvatar(
    nameOrNumber: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val initials = remember(nameOrNumber) {
        val trimmed = nameOrNumber.trim()
        if (trimmed.isEmpty()) "#"
        else {
            val parts = trimmed.split(" ").filter { it.isNotEmpty() }
            when {
                parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
                parts.isNotEmpty() -> "${parts[0].first().uppercaseChar()}"
                else -> "#"
            }
        }
    }

    val gradientColors = remember(nameOrNumber) {
        val hash = kotlin.math.abs(nameOrNumber.hashCode())
        AvatarGradients[hash % AvatarGradients.size]
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp
        )
    }
}
