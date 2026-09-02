package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.PureWhite

@Composable
fun ComparisonSlider(
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    modifier: Modifier = Modifier,
    isTransparentBg: Boolean = true,
    showLabels: Boolean = true
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val splitX = widthPx * splitFraction

        // Transparent Checkerboard under afterBitmap
        if (isTransparentBg) {
            CheckerboardBackground()
        }

        // 1. After (Processed / AI Cutout) Layer on bottom
        Image(
            bitmap = afterBitmap.asImageBitmap(),
            contentDescription = "Cutout Result",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // 2. Before (Original) Layer clipped to left side
        Image(
            bitmap = beforeBitmap.asImageBitmap(),
            contentDescription = "Original Photo",
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val clipRect = Path().apply {
                        addRect(Rect(0f, 0f, splitX, size.height))
                    }
                    clipPath(clipRect) {
                        this@drawWithContent.drawContent()
                    }
                },
            contentScale = ContentScale.Fit
        )

        // 3. Central Divider Line
        Box(
            modifier = Modifier
                .offset { IntOffset(splitX.toInt() - with(density) { 1.dp.roundToPx() }, 0) }
                .width(2.dp)
                .fillMaxSize()
                .background(PureWhite)
        )

        // 4. Draggable Center Knob
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (splitX - with(density) { 20.dp.roundToPx() }).toInt(),
                        (heightPx / 2f - with(density) { 20.dp.roundToPx() }).toInt()
                    )
                }
                .size(40.dp)
                .clip(CircleShape)
                .background(ElectricViolet)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newSplit = (splitX + dragAmount.x) / widthPx
                        splitFraction = newSplit.coerceIn(0.05f, 0.95f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Drag to compare",
                tint = PureWhite,
                modifier = Modifier.size(22.dp)
            )
        }

        // 5. Left & Right Badges
        if (showLabels) {
            Surface(
                color = ObsidianBackground.copy(alpha = 0.75f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = "ORIGINAL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Surface(
                color = NeonCyan.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Text(
                    text = "AI CUTOUT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ObsidianBackground,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
