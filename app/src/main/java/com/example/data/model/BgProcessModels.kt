package com.example.data.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class MediaType {
    IMAGE,
    VIDEO
}

enum class BackgroundType {
    TRANSPARENT,
    SOLID_COLOR,
    GRADIENT,
    SCENIC_WALLPAPER,
    BLUR,
    CUSTOM_IMAGE
}

enum class FilterEffect(val displayName: String) {
    NONE("Original"),
    VIVID_HDR("Vivid HDR"),
    CYBERPUNK("Cyberpunk"),
    NOIR("Noir B&W"),
    WARM_VINTAGE("Warm Golden"),
    COOL_MINT("Cool Mint"),
    PASTEL_DREAM("Pastel"),
    CINEMATIC("Cinematic")
}

enum class EdgeEffect(val displayName: String) {
    NONE("None"),
    STICKER_WHITE("White Sticker"),
    NEON_CYAN("Cyan Glow"),
    NEON_PURPLE("Purple Glow"),
    NEON_GOLD("Gold Glow"),
    DROP_SHADOW("Drop Shadow")
}

enum class OutputResolution(val label: String, val badge: String, val maxDimension: Int) {
    HD_1080P("Full HD 1080p", "1080p", 1920),
    ULTRA_2K("Ultra HD 2K", "2K", 2560),
    SUPER_4K("Super Ultra 4K", "4K", 3840)
}

data class BackgroundPreset(
    val id: String,
    val name: String,
    val type: BackgroundType,
    val primaryColor: Color = Color.Transparent,
    val secondaryColor: Color = Color.Transparent,
    val wallpaperResId: String = "",
    val blurRadius: Float = 25f
)

data class VideoFrameData(
    val index: Int,
    val timestampMs: Long,
    val originalBitmap: Bitmap,
    var cutoutBitmap: Bitmap? = null,
    var compositeBitmap: Bitmap? = null
)

data class ProcessedMediaState(
    val id: String = UUID.randomUUID().toString(),
    val mediaType: MediaType = MediaType.IMAGE,
    val originalBitmap: Bitmap? = null,
    val cutoutBitmap: Bitmap? = null,
    val compositeBitmap: Bitmap? = null,
    val selectedBgType: BackgroundType = BackgroundType.TRANSPARENT,
    val selectedBgPreset: BackgroundPreset = BackgroundPreset(
        id = "transparent",
        name = "Transparent",
        type = BackgroundType.TRANSPARENT
    ),
    val customBgBitmap: Bitmap? = null,
    val selectedFilter: FilterEffect = FilterEffect.NONE,
    val selectedEdgeEffect: EdgeEffect = EdgeEffect.NONE,
    val blurRadius: Float = 25f,
    val feathering: Float = 1.5f,
    val edgeThreshold: Float = 0.5f,
    val edgeGlowRadius: Float = 12f,
    val outputResolution: OutputResolution = OutputResolution.HD_1080P,
    // Video-specific states
    val videoFrames: List<VideoFrameData> = emptyList(),
    val currentFrameIndex: Int = 0,
    val isPlayingVideo: Boolean = false,
    val videoFps: Int = 15,
    val isProcessing: Boolean = false,
    val processingProgress: Float = 0f,
    val processingStatusText: String = "",
    val errorMessage: String? = null
)
