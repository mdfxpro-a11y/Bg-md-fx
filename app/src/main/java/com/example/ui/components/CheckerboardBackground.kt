package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun CheckerboardBackground(
    modifier: Modifier = Modifier,
    squareSizePx: Float = 28f,
    lightColor: Color = Color(0xFF1E2232),
    darkColor: Color = Color(0xFF121420)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val numCols = (width / squareSizePx).toInt() + 1
        val numRows = (height / squareSizePx).toInt() + 1

        for (row in 0 until numRows) {
            for (col in 0 until numCols) {
                val isEven = (row + col) % 2 == 0
                val color = if (isEven) lightColor else darkColor
                drawRect(
                    color = color,
                    topLeft = Offset(col * squareSizePx, row * squareSizePx),
                    size = Size(squareSizePx, squareSizePx)
                )
            }
        }
    }
}
