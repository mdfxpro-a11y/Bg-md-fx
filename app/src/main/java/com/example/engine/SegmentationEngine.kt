package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.example.data.model.BackgroundPreset
import com.example.data.model.BackgroundType
import com.example.data.model.EdgeEffect
import com.example.data.model.FilterEffect
import com.example.data.model.OutputResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object SegmentationEngine {

    /**
     * Extracts foreground cutout bitmap with high-definition alpha transparency.
     */
    suspend fun extractCutout(
        source: Bitmap,
        feathering: Float = 1.5f,
        sensitivity: Float = 0.5f
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height

        // Downscale slightly for fast saliency calculation if huge, but render full res output
        val calcScale = if (width > 1200 || height > 1200) {
            1200f / max(width, height)
        } else {
            1.0f
        }

        val calcW = (width * calcScale).toInt().coerceAtLeast(100)
        val calcH = (height * calcScale).toInt().coerceAtLeast(100)

        val workingBmp = if (calcScale < 1.0f) {
            Bitmap.createScaledBitmap(source, calcW, calcH, true)
        } else {
            source
        }

        val pixels = IntArray(calcW * calcH)
        workingBmp.getPixels(pixels, 0, calcW, 0, 0, calcW, calcH)

        // 1. Sample perimeter border pixels to learn background palette
        val bgSamples = sampleBorderColors(pixels, calcW, calcH)

        // 2. Compute saliency & foreground mask
        val mask = FloatArray(calcW * calcH)
        val centerX = calcW / 2f
        val centerY = calcH / 2f
        val maxDist = sqrt(centerX * centerX + centerY * centerY)

        for (y in 0 until calcH) {
            for (x in 0 until calcW) {
                val idx = y * calcW + x
                val pixel = pixels[idx]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Minimum color distance to background samples
                var minBgDist = Float.MAX_VALUE
                for (bg in bgSamples) {
                    val bgr = (bg shr 16) and 0xFF
                    val bgg = (bg shr 8) and 0xFF
                    val bgb = bg and 0xFF
                    val dist = colorDistance(r, g, b, bgr, bgg, bgb)
                    if (dist < minBgDist) minBgDist = dist
                }

                // Center proximity prior (subjects are generally centered)
                val distFromCenter = sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY))
                val centerWeight = 1.0f - (distFromCenter / maxDist * 0.45f)

                // Edge penalty for borders
                val borderMargin = min(min(x, calcW - 1 - x), min(y, calcH - 1 - y))
                val borderFactor = (borderMargin / (min(calcW, calcH) * 0.08f)).coerceIn(0.05f, 1.0f)

                val threshold = (65f * (1.2f - sensitivity * 0.4f))
                val rawScore = (minBgDist / threshold) * centerWeight * borderFactor

                mask[idx] = rawScore.coerceIn(0f, 1f)
            }
        }

        // 3. Smooth & refine alpha mask
        val smoothedMask = applyBoxBlur(mask, calcW, calcH, radius = (feathering * 2).toInt().coerceAtLeast(1))

        // 4. Create high-def output bitmap
        val outputBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outputPixels = IntArray(width * height)
        source.getPixels(outputPixels, 0, width, 0, 0, width, height)

        val finalPixels = IntArray(width * height)

        for (y in 0 until height) {
            val maskY = (y * calcScale).toInt().coerceIn(0, calcH - 1)
            for (x in 0 until width) {
                val maskX = (x * calcScale).toInt().coerceIn(0, calcW - 1)
                val outIdx = y * width + x
                val origPixel = outputPixels[outIdx]

                val alphaVal = smoothedMask[maskY * calcW + maskX]
                // Apply soft sigmoid contrast curve to alpha
                val refinedAlpha = if (alphaVal > 0.45f) {
                    ((alphaVal - 0.45f) / 0.55f).coerceIn(0f, 1f)
                } else {
                    (alphaVal / 0.45f * 0.2f).coerceIn(0f, 1f)
                }

                val finalAlphaInt = (refinedAlpha * 255).toInt().coerceIn(0, 255)
                val rgbOnly = origPixel and 0x00FFFFFF
                finalPixels[outIdx] = (finalAlphaInt shl 24) or rgbOnly
            }
        }

        outputBmp.setPixels(finalPixels, 0, width, 0, 0, width, height)
        if (workingBmp != source) {
            workingBmp.recycle()
        }

        outputBmp
    }

    /**
     * Composites foreground with chosen background type, preset, edge effects, and filters.
     */
    suspend fun compositeImage(
        original: Bitmap,
        cutout: Bitmap,
        preset: BackgroundPreset,
        customBg: Bitmap? = null,
        filter: FilterEffect = FilterEffect.NONE,
        edgeEffect: EdgeEffect = EdgeEffect.NONE,
        blurRadius: Float = 25f,
        targetResolution: OutputResolution = OutputResolution.HD_1080P
    ): Bitmap = withContext(Dispatchers.Default) {
        val origW = cutout.width
        val origH = cutout.height

        // Calculate target dimensions
        val maxDim = max(origW, origH)
        val targetScale = if (maxDim > targetResolution.maxDimension) {
            targetResolution.maxDimension.toFloat() / maxDim
        } else {
            1.0f
        }

        val outW = (origW * targetScale).toInt().coerceAtLeast(100)
        val outH = (origH * targetScale).toInt().coerceAtLeast(100)

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Draw Background
        when (preset.type) {
            BackgroundType.TRANSPARENT -> {
                // Keep transparent for export
            }
            BackgroundType.SOLID_COLOR -> {
                canvas.drawColor(preset.primaryColor.toArgb())
            }
            BackgroundType.GRADIENT -> {
                val paint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, outW.toFloat(), outH.toFloat(),
                        preset.primaryColor.toArgb(),
                        preset.secondaryColor.toArgb(),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), paint)
            }
            BackgroundType.BLUR -> {
                val blurred = blurBitmap(original, blurRadius)
                val srcRect = Rect(0, 0, blurred.width, blurred.height)
                val dstRect = Rect(0, 0, outW, outH)
                canvas.drawBitmap(blurred, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            BackgroundType.SCENIC_WALLPAPER -> {
                drawScenicWallpaper(canvas, outW, outH, preset.id)
            }
            BackgroundType.CUSTOM_IMAGE -> {
                if (customBg != null) {
                    val srcRect = Rect(0, 0, customBg.width, customBg.height)
                    val dstRect = Rect(0, 0, outW, outH)
                    canvas.drawBitmap(customBg, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
                } else {
                    canvas.drawColor(AndroidColor.DKGRAY)
                }
            }
        }

        // 2. Draw Edge Effect Behind Cutout if selected
        if (edgeEffect != EdgeEffect.NONE) {
            drawEdgeEffect(canvas, cutout, outW, outH, edgeEffect)
        }

        // 3. Draw Cutout Foreground with Filter
        val foregroundPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = getFilterColorMatrix(filter)
        }

        val cutoutSrc = Rect(0, 0, cutout.width, cutout.height)
        val cutoutDst = Rect(0, 0, outW, outH)
        canvas.drawBitmap(cutout, cutoutSrc, cutoutDst, foregroundPaint)

        output
    }

    private fun drawEdgeEffect(canvas: Canvas, cutout: Bitmap, width: Int, height: Int, effect: EdgeEffect) {
        val edgeColor = when (effect) {
            EdgeEffect.STICKER_WHITE -> AndroidColor.WHITE
            EdgeEffect.NEON_CYAN -> AndroidColor.CYAN
            EdgeEffect.NEON_PURPLE -> AndroidColor.parseColor("#A855F7")
            EdgeEffect.NEON_GOLD -> AndroidColor.parseColor("#F59E0B")
            EdgeEffect.DROP_SHADOW -> AndroidColor.parseColor("#80000000")
            EdgeEffect.NONE -> return
        }

        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(edgeColor, PorterDuff.Mode.SRC_IN)
        }

        val offsets = if (effect == EdgeEffect.DROP_SHADOW) {
            listOf(Pair(12f, 16f))
        } else {
            val radius = 8f
            listOf(
                Pair(-radius, 0f), Pair(radius, 0f),
                Pair(0f, -radius), Pair(0f, radius),
                Pair(-radius * 0.7f, -radius * 0.7f), Pair(radius * 0.7f, radius * 0.7f),
                Pair(-radius * 0.7f, radius * 0.7f), Pair(radius * 0.7f, -radius * 0.7f)
            )
        }

        for ((dx, dy) in offsets) {
            val dst = RectF(dx, dy, width + dx, height + dy)
            canvas.drawBitmap(cutout, null, dst, paint)
        }
    }

    private fun drawScenicWallpaper(canvas: Canvas, w: Int, h: Int, wallpaperId: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (wallpaperId) {
            "wall_cyber_city" -> {
                // Futuristic neon dark cityscape gradient & beams
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    AndroidColor.parseColor("#090A1A"), AndroidColor.parseColor("#1B1035"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

                paint.shader = LinearGradient(0f, h * 0.6f, w.toFloat(), h.toFloat(),
                    AndroidColor.parseColor("#06B6D4"), AndroidColor.parseColor("#EC4899"), Shader.TileMode.CLAMP)
                paint.alpha = 70
                canvas.drawCircle(w * 0.5f, h * 0.85f, w * 0.6f, paint)
            }
            "wall_sunset_beach" -> {
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    intArrayOf(
                        AndroidColor.parseColor("#FF512F"),
                        AndroidColor.parseColor("#F09819"),
                        AndroidColor.parseColor("#70A1FF"),
                        AndroidColor.parseColor("#1E3799")
                    ),
                    floatArrayOf(0f, 0.45f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            "wall_modern_loft" -> {
                paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    AndroidColor.parseColor("#2C3E50"), AndroidColor.parseColor("#BDC3C7"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            "wall_luxury_office" -> {
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    AndroidColor.parseColor("#141E30"), AndroidColor.parseColor("#243B55"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            "wall_aesthetic_pastel" -> {
                paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    AndroidColor.parseColor("#FAD0C4"), AndroidColor.parseColor("#FFD1FF"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            else -> {
                paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    AndroidColor.parseColor("#1E1B4B"), AndroidColor.parseColor("#4C1D95"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
        }
    }

    private fun getFilterColorMatrix(filter: FilterEffect): ColorMatrixColorFilter? {
        val cm = ColorMatrix()
        return when (filter) {
            FilterEffect.NONE -> null
            FilterEffect.VIVID_HDR -> {
                cm.set(floatArrayOf(
                    1.25f, 0f, 0f, 0f, 10f,
                    0f, 1.25f, 0f, 0f, 10f,
                    0f, 0f, 1.25f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                ColorMatrixColorFilter(cm)
            }
            FilterEffect.CYBERPUNK -> {
                cm.set(floatArrayOf(
                    1.4f, 0f, 0.2f, 0f, 20f,
                    0f, 0.9f, 0.4f, 0f, -10f,
                    0.3f, 0.1f, 1.6f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                ColorMatrixColorFilter(cm)
            }
            FilterEffect.NOIR -> {
                cm.setSaturation(0f)
                val contrast = 1.4f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                val contrastMatrix = ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(contrastMatrix)
                ColorMatrixColorFilter(cm)
            }
            FilterEffect.WARM_VINTAGE -> {
                cm.set(floatArrayOf(
                    1.3f, 0f, 0f, 0f, 25f,
                    0f, 1.1f, 0f, 0f, 15f,
                    0f, 0f, 0.8f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                ColorMatrixColorFilter(cm)
            }
            FilterEffect.COOL_MINT -> {
                cm.set(floatArrayOf(
                    0.85f, 0f, 0f, 0f, -10f,
                    0f, 1.2f, 0f, 0f, 15f,
                    0f, 0f, 1.35f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                ColorMatrixColorFilter(cm)
            }
            FilterEffect.PASTEL_DREAM -> {
                cm.set(floatArrayOf(
                    1.1f, 0.1f, 0.1f, 0f, 30f,
                    0.1f, 1.1f, 0.1f, 0f, 30f,
                    0.1f, 0.1f, 1.15f, 0f, 35f,
                    0f, 0f, 0f, 1f, 0f
                ))
                ColorMatrixColorFilter(cm)
            }
            FilterEffect.CINEMATIC -> {
                cm.set(floatArrayOf(
                    1.15f, 0f, 0f, 0f, 5f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.25f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                ))
                ColorMatrixColorFilter(cm)
            }
        }
    }

    private fun blurBitmap(src: Bitmap, radius: Float): Bitmap {
        val downscale = 0.25f
        val w = (src.width * downscale).toInt().coerceAtLeast(10)
        val h = (src.height * downscale).toInt().coerceAtLeast(10)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val blurred = applyFastStackBlur(small, (radius * 0.4f).toInt().coerceAtLeast(2))
        return Bitmap.createScaledBitmap(blurred, src.width, src.height, true)
    }

    private fun sampleBorderColors(pixels: IntArray, width: Int, height: Int): List<Int> {
        val samples = mutableListOf<Int>()
        val stepX = max(1, width / 20)
        val stepY = max(1, height / 20)

        // Top & Bottom rows
        for (x in 0 until width step stepX) {
            samples.add(pixels[x])
            samples.add(pixels[(height - 1) * width + x])
        }
        // Left & Right columns
        for (y in 0 until height step stepY) {
            samples.add(pixels[y * width])
            samples.add(pixels[y * width + (width - 1)])
        }
        return samples
    }

    private fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Float {
        val dr = (r1 - r2).toFloat()
        val dg = (g1 - g2).toFloat()
        val db = (b1 - b2).toFloat()
        // Weighted Euclidean distance for human perception
        return sqrt(0.299f * dr * dr + 0.587f * dg * dg + 0.114f * db * db)
    }

    private fun applyBoxBlur(src: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val r = radius.coerceIn(1, 10)
        val temp = FloatArray(width * height)
        val out = FloatArray(width * height)

        // Horizontal pass
        for (y in 0 until height) {
            val yOffset = y * width
            for (x in 0 until width) {
                var sum = 0f
                var count = 0
                for (kx in -r..r) {
                    val px = (x + kx).coerceIn(0, width - 1)
                    sum += src[yOffset + px]
                    count++
                }
                temp[yOffset + x] = sum / count
            }
        }

        // Vertical pass
        for (y in 0 until height) {
            val yOffset = y * width
            for (x in 0 until width) {
                var sum = 0f
                var count = 0
                for (ky in -r..r) {
                    val py = (y + ky).coerceIn(0, height - 1)
                    sum += temp[py * width + x]
                    count++
                }
                out[yOffset + x] = sum / count
            }
        }
        return out
    }

    private fun applyFastStackBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val pix = IntArray(w * h)
        out.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yw = 0
        yi = 0

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var rbs: Int
        val routsum = IntArray(1)
        val goutsum = IntArray(1)
        val boutsum = IntArray(1)
        val rinsum = IntArray(1)
        val ginsum = IntArray(1)
        val binsum = IntArray(1)

        for (curY in 0 until h) {
            rinsum[0] = 0
            ginsum[0] = 0
            binsum[0] = 0
            routsum[0] = 0
            goutsum[0] = 0
            boutsum[0] = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (k in -radius..radius) {
                p = pix[yi + min(wm, max(k, 0))]
                val sir = stack[k + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = radius + 1 - abs(k)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (k > 0) {
                    rinsum[0] += sir[0]
                    ginsum[0] += sir[1]
                    binsum[0] += sir[2]
                } else {
                    routsum[0] += sir[0]
                    goutsum[0] += sir[1]
                    boutsum[0] += sir[2]
                }
            }
            stackpointer = radius

            for (curX in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum[0]
                gsum -= goutsum[0]
                bsum -= boutsum[0]

                stackstart = stackpointer - radius + div
                val sir = stack[stackstart % div]

                routsum[0] -= sir[0]
                goutsum[0] -= sir[1]
                boutsum[0] -= sir[2]

                if (curY == 0) {
                    vmin[curX] = min(curX + radius + 1, wm)
                }
                p = pix[yw + vmin[curX]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum[0] += sir[0]
                ginsum[0] += sir[1]
                binsum[0] += sir[2]

                rsum += rinsum[0]
                gsum += ginsum[0]
                bsum += binsum[0]

                stackpointer = (stackpointer + 1) % div
                val sirNext = stack[stackpointer % div]

                routsum[0] += sirNext[0]
                goutsum[0] += sirNext[1]
                boutsum[0] += sirNext[2]

                rinsum[0] -= sirNext[0]
                ginsum[0] -= sirNext[1]
                binsum[0] -= sirNext[2]

                yi++
            }
            yw += w
        }

        for (curX in 0 until w) {
            rinsum[0] = 0
            ginsum[0] = 0
            binsum[0] = 0
            routsum[0] = 0
            goutsum[0] = 0
            boutsum[0] = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (k in -radius..radius) {
                yi = max(0, yp) + curX
                val sir = stack[k + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = radius + 1 - abs(k)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                if (k > 0) {
                    rinsum[0] += sir[0]
                    ginsum[0] += sir[1]
                    binsum[0] += sir[2]
                } else {
                    routsum[0] += sir[0]
                    goutsum[0] += sir[1]
                    boutsum[0] += sir[2]
                }
                if (k < hm) {
                    yp += w
                }
            }
            yi = curX
            stackpointer = radius
            for (curY in 0 until h) {
                pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum[0]
                gsum -= goutsum[0]
                bsum -= boutsum[0]

                stackstart = stackpointer - radius + div
                val sir = stack[stackstart % div]

                routsum[0] -= sir[0]
                goutsum[0] -= sir[1]
                boutsum[0] -= sir[2]

                if (curX == 0) {
                    vmin[curY] = min(curY + radius + 1, hm) * w
                }
                p = curX + vmin[curY]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum[0] += sir[0]
                ginsum[0] += sir[1]
                binsum[0] += sir[2]

                rsum += rinsum[0]
                gsum += ginsum[0]
                bsum += binsum[0]

                stackpointer = (stackpointer + 1) % div
                val sirNext = stack[stackpointer]

                routsum[0] += sirNext[0]
                goutsum[0] += sirNext[1]
                boutsum[0] += sirNext[2]

                rinsum[0] -= sirNext[0]
                ginsum[0] -= sirNext[1]
                binsum[0] -= sirNext[2]

                yi += w
            }
        }

        out.setPixels(pix, 0, w, 0, 0, w, h)
        return out
    }
}
