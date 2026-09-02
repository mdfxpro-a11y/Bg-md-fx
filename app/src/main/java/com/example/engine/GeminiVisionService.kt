package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.model.GeminiContent
import com.example.data.model.GeminiGenerateRequest
import com.example.data.model.GeminiGenerateResponse
import com.example.data.model.GeminiGenerationConfig
import com.example.data.model.GeminiImageConfig
import com.example.data.model.GeminiInlineData
import com.example.data.model.GeminiPart
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiVisionService {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GeminiGenerateRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiGenerateResponse::class.java)

    /**
     * Tests the API key with a small validation ping.
     */
    suspend fun testApiKey(apiKey: String, model: String = "gemini-3.5-flash"): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("API Key cannot be empty"))
            }

            val requestBodyObj = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = "Hello! Verify connection status."))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.2f
                )
            )

            val jsonString = requestAdapter.toJson(requestBodyObj)
            val url = "$BASE_URL$model:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(jsonString.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errParsed = responseAdapter.fromJson(responseBody)
                    errParsed?.error?.message ?: "HTTP ${response.code}: ${response.message}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: ${response.message}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val parsed = responseAdapter.fromJson(responseBody)
            val text = parsed?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Connection established successfully!"

            Result.success("Success: $text")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends an image to Gemini with background removal / inpainting instruction.
     */
    suspend fun processImageWithGemini(
        apiKey: String,
        model: String,
        bitmap: Bitmap,
        prompt: String = "Perform high-definition background removal on this image. Keep the foreground subject crisp with clean edges.",
        resolution: String = "1K"
    ): Result<Bitmap?> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("API Key is missing. Please set your Gemini API key in Settings."))
            }

            // Downsample slightly to max 1200px before uploading to avoid huge payload
            val maxDim = Math.max(bitmap.width, bitmap.height)
            val uploadBmp = if (maxDim > 1200) {
                val scale = 1200f / maxDim
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }

            val base64 = uploadBmp.toBase64()
            if (uploadBmp != bitmap) {
                uploadBmp.recycle()
            }

            val requestObj = GeminiGenerateRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(
                                inlineData = GeminiInlineData(
                                    mimeType = "image/jpeg",
                                    data = base64
                                )
                            )
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    responseModalities = listOf("TEXT", "IMAGE"),
                    imageConfig = GeminiImageConfig(
                        aspectRatio = "1:1",
                        imageSize = resolution
                    )
                )
            )

            val jsonString = requestAdapter.toJson(requestObj)
            val url = "$BASE_URL$model:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(jsonString.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errParsed = try {
                    responseAdapter.fromJson(bodyString)?.error?.message
                } catch (e: Exception) {
                    null
                }
                val msg = errParsed ?: "API Error ${response.code}: ${response.message}"
                return@withContext Result.failure(Exception(msg))
            }

            val parsed = responseAdapter.fromJson(bodyString)
            val imagePart = parsed?.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.inlineData != null }

            if (imagePart?.inlineData?.data != null) {
                val imageBytes = Base64.decode(imagePart.inlineData.data, Base64.DEFAULT)
                val resultBmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                return@withContext Result.success(resultBmp)
            }

            // If no direct image output returned, the model returned text insights
            val textOutput = parsed?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Bitmap.toBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
