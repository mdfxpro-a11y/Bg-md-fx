package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

data class SampleDemoItem(
    val id: String,
    val title: String,
    val category: String,
    val subtitle: String
)

object SampleDemoAssets {

    val sampleList = listOf(
        SampleDemoItem(
            id = "demo_portrait",
            title = "Studio Portrait",
            category = "People",
            subtitle = "HD Hair & Subject Edge Cutout"
        ),
        SampleDemoItem(
            id = "demo_product",
            title = "Luxury Product",
            category = "E-Commerce",
            subtitle = "Clean White & Studio BG"
        ),
        SampleDemoItem(
            id = "demo_pet",
            title = "Golden Puppy",
            category = "Animals",
            subtitle = "Fur Edge & Texture Isolation"
        ),
        SampleDemoItem(
            id = "demo_car",
            title = "Cyber Supercar",
            category = "Automotive",
            subtitle = "Reflective Surface Cutout"
        )
    )

    fun createSampleBitmap(id: String): Bitmap {
        val w = 800
        val h = 800
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (id) {
            "demo_portrait" -> {
                // Background: Teal-Green outdoor tone
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    AndroidColor.parseColor("#4A707A"), AndroidColor.parseColor("#7697A0"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

                // Head & Hair
                paint.shader = null
                paint.color = AndroidColor.parseColor("#2D1E18") // Dark hair
                canvas.drawCircle(400f, 320f, 160f, paint)

                // Face Skin Tone
                paint.color = AndroidColor.parseColor("#F3C9A8")
                canvas.drawOval(RectF(290f, 250f, 510f, 490f), paint)

                // Eyes
                paint.color = AndroidColor.parseColor("#3E2723")
                canvas.drawOval(RectF(335f, 345f, 375f, 365f), paint)
                canvas.drawOval(RectF(425f, 345f, 465f, 365f), paint)

                // Smile
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                paint.color = AndroidColor.parseColor("#D81B60")
                canvas.drawArc(RectF(360f, 400f, 440f, 440f), 0f, 180f, false, paint)

                // Body / Stylish Jacket
                paint.style = Paint.Style.FILL
                paint.color = AndroidColor.parseColor("#E91E63")
                val jacketPath = Path().apply {
                    moveTo(240f, 800f)
                    lineTo(300f, 510f)
                    lineTo(500f, 510f)
                    lineTo(560f, 800f)
                    close()
                }
                canvas.drawPath(jacketPath, paint)
            }
            "demo_product" -> {
                // Background: Soft pastel desk
                paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    AndroidColor.parseColor("#E0E5EC"), AndroidColor.parseColor("#A3B1C6"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

                // Bottle Base Shadow
                paint.shader = null
                paint.color = AndroidColor.parseColor("#33000000")
                canvas.drawOval(RectF(260f, 650f, 540f, 700f), paint)

                // Perfume / Serum Bottle Body
                paint.color = AndroidColor.parseColor("#D97706") // Amber Gold Glass
                canvas.drawRoundRect(RectF(300f, 320f, 500f, 660f), 30f, 30f, paint)

                // Bottle Neck & Cap
                paint.color = AndroidColor.parseColor("#F59E0B")
                canvas.drawRect(360f, 240f, 440f, 320f, paint)
                paint.color = AndroidColor.parseColor("#1F2937") // Matte Black Cap
                canvas.drawRoundRect(RectF(340f, 140f, 460f, 240f), 12f, 12f, paint)

                // Glass Highlight reflection
                paint.color = AndroidColor.parseColor("#55FFFFFF")
                canvas.drawRoundRect(RectF(320f, 340f, 350f, 640f), 10f, 10f, paint)
            }
            "demo_pet" -> {
                // Background: Green grass lawn
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    AndroidColor.parseColor("#56AB2F"), AndroidColor.parseColor("#A8E063"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

                // Golden Fur Dog Face
                paint.shader = null
                paint.color = AndroidColor.parseColor("#E67E22")
                canvas.drawCircle(400f, 380f, 180f, paint)

                // Ears
                val earPath = Path().apply {
                    moveTo(240f, 300f)
                    lineTo(200f, 520f)
                    lineTo(300f, 420f)
                    close()
                }
                canvas.drawPath(earPath, paint)

                val rightEarPath = Path().apply {
                    moveTo(560f, 300f)
                    lineTo(600f, 520f)
                    lineTo(500f, 420f)
                    close()
                }
                canvas.drawPath(rightEarPath, paint)

                // Snout
                paint.color = AndroidColor.parseColor("#F5B041")
                canvas.drawOval(RectF(320f, 380f, 480f, 500f), paint)

                // Nose & Eyes
                paint.color = AndroidColor.parseColor("#1C2833")
                canvas.drawOval(RectF(370f, 410f, 430f, 450f), paint)
                canvas.drawCircle(335f, 340f, 20f, paint)
                canvas.drawCircle(465f, 340f, 20f, paint)

                // Body
                paint.color = AndroidColor.parseColor("#D35400")
                canvas.drawRoundRect(RectF(260f, 520f, 540f, 800f), 60f, 60f, paint)
            }
            "demo_car" -> {
                // Background: City street asphalt
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    AndroidColor.parseColor("#34495E"), AndroidColor.parseColor("#1B2631"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

                // Car Body (Electric Neon Red/Cyan)
                paint.shader = null
                paint.color = AndroidColor.parseColor("#FF0055")
                val carPath = Path().apply {
                    moveTo(120f, 560f)
                    lineTo(180f, 460f)
                    lineTo(300f, 380f)
                    lineTo(520f, 380f)
                    lineTo(640f, 460f)
                    lineTo(700f, 560f)
                    close()
                }
                canvas.drawPath(carPath, paint)

                // Windshield Glass
                paint.color = AndroidColor.parseColor("#2C3E50")
                val glassPath = Path().apply {
                    moveTo(310f, 390f)
                    lineTo(510f, 390f)
                    lineTo(600f, 460f)
                    lineTo(240f, 460f)
                    close()
                }
                canvas.drawPath(glassPath, paint)

                // Wheels
                paint.color = AndroidColor.parseColor("#111111")
                canvas.drawCircle(240f, 580f, 70f, paint)
                canvas.drawCircle(580f, 580f, 70f, paint)
                paint.color = AndroidColor.parseColor("#00FFFF") // Neon Rims
                canvas.drawCircle(240f, 580f, 35f, paint)
                canvas.drawCircle(580f, 580f, 35f, paint)
            }
        }

        return bmp
    }
}
