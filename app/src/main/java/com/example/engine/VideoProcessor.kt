package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.model.BackgroundPreset
import com.example.data.model.EdgeEffect
import com.example.data.model.FilterEffect
import com.example.data.model.OutputResolution
import com.example.data.model.VideoFrameData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoProcessor {

    /**
     * Extracts keyframes from a video URI.
     */
    suspend fun extractFramesFromUri(
        context: Context,
        videoUri: Uri,
        maxFrames: Int = 18,
        onProgress: (Float, String) -> Unit
    ): List<VideoFrameData> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val frameList = mutableListOf<VideoFrameData>()

        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 3000L

            val stepMs = (durationMs / maxFrames).coerceAtLeast(100L)

            for (i in 0 until maxFrames) {
                val timeUs = (i * stepMs * 1000L).coerceAtMost(durationMs * 1000L)
                val frameBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(timeUs)

                if (frameBmp != null) {
                    // Downscale slightly for smooth video frame playback
                    val scaled = if (frameBmp.width > 720 || frameBmp.height > 720) {
                        val scale = 720f / Math.max(frameBmp.width, frameBmp.height)
                        Bitmap.createScaledBitmap(frameBmp, (frameBmp.width * scale).toInt(), (frameBmp.height * scale).toInt(), true)
                    } else {
                        frameBmp
                    }

                    frameList.add(
                        VideoFrameData(
                            index = i,
                            timestampMs = i * stepMs,
                            originalBitmap = scaled
                        )
                    )
                }

                val progress = (i + 1).toFloat() / maxFrames
                onProgress(progress * 0.4f, "Extracting video frames (${i + 1}/$maxFrames)...")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }

        frameList
    }

    /**
     * Processes AI background removal for all extracted frames in background.
     */
    suspend fun processVideoFrames(
        frames: List<VideoFrameData>,
        preset: BackgroundPreset,
        customBg: Bitmap? = null,
        filter: FilterEffect = FilterEffect.NONE,
        edgeEffect: EdgeEffect = EdgeEffect.NONE,
        feathering: Float = 1.5f,
        onProgress: (Float, String) -> Unit
    ): List<VideoFrameData> = withContext(Dispatchers.Default) {
        val total = frames.size
        val processedList = mutableListOf<VideoFrameData>()

        for ((idx, frame) in frames.withIndex()) {
            val cutout = frame.cutoutBitmap ?: SegmentationEngine.extractCutout(
                source = frame.originalBitmap,
                feathering = feathering
            )

            val composite = SegmentationEngine.compositeImage(
                original = frame.originalBitmap,
                cutout = cutout,
                preset = preset,
                customBg = customBg,
                filter = filter,
                edgeEffect = edgeEffect,
                targetResolution = OutputResolution.HD_1080P
            )

            processedList.add(
                frame.copy(
                    cutoutBitmap = cutout,
                    compositeBitmap = composite
                )
            )

            val progress = 0.4f + ((idx + 1).toFloat() / total) * 0.6f
            onProgress(progress, "Removing video background (${idx + 1}/$total)...")
        }

        processedList
    }
}
