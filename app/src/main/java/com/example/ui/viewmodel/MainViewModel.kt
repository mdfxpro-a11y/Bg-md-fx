package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.ProcessedItemEntity
import com.example.data.model.ApiKeySettings
import com.example.data.model.BackgroundPreset
import com.example.data.model.BackgroundType
import com.example.data.model.EdgeEffect
import com.example.data.model.FilterEffect
import com.example.data.model.MediaType
import com.example.data.model.OutputResolution
import com.example.data.model.ProcessedMediaState
import com.example.engine.GeminiVisionService
import com.example.engine.SegmentationEngine
import com.example.engine.VideoProcessor
import com.example.ui.components.SampleDemoAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val historyDao = db.historyDao()
    private val preferencesManager = PreferencesManager(application)

    val apiSettings: StateFlow<ApiKeySettings> = preferencesManager.settingsFlow
    val historyList: StateFlow<List<ProcessedItemEntity>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _mediaState = MutableStateFlow(ProcessedMediaState())
    val mediaState: StateFlow<ProcessedMediaState> = _mediaState.asStateFlow()

    private val _apiTestStatus = MutableStateFlow<String?>(null)
    val apiTestStatus: StateFlow<String?> = _apiTestStatus.asStateFlow()

    private val _isTestingApiKey = MutableStateFlow(false)
    val isTestingApiKey: StateFlow<Boolean> = _isTestingApiKey.asStateFlow()

    private var videoPlaybackJob: Job? = null

    // Predefined Presets
    val backgroundPresets = listOf(
        BackgroundPreset(id = "transparent", name = "Transparent", type = BackgroundType.TRANSPARENT),
        BackgroundPreset(id = "solid_white", name = "Pure White", type = BackgroundType.SOLID_COLOR, primaryColor = Color(0xFFFFFFFF)),
        BackgroundPreset(id = "solid_black", name = "Studio Black", type = BackgroundType.SOLID_COLOR, primaryColor = Color(0xFF0F172A)),
        BackgroundPreset(id = "solid_chroma_green", name = "Green Screen", type = BackgroundType.SOLID_COLOR, primaryColor = Color(0xFF00FF00)),
        BackgroundPreset(id = "solid_chroma_blue", name = "Blue Screen", type = BackgroundType.SOLID_COLOR, primaryColor = Color(0xFF0055FF)),
        BackgroundPreset(id = "solid_yellow", name = "Cyber Yellow", type = BackgroundType.SOLID_COLOR, primaryColor = Color(0xFFFACC15)),
        BackgroundPreset(id = "solid_pink", name = "Neon Pink", type = BackgroundType.SOLID_COLOR, primaryColor = Color(0xFFF43F5E)),
        BackgroundPreset(id = "solid_teal", name = "Electric Cyan", type = BackgroundType.SOLID_COLOR, primaryColor = Color(0xFF06B6D4)),
        BackgroundPreset(id = "grad_sunset", name = "Sunset Glow", type = BackgroundType.GRADIENT, primaryColor = Color(0xFFFF512F), secondaryColor = Color(0xFFDD2476)),
        BackgroundPreset(id = "grad_aurora", name = "Aurora Neon", type = BackgroundType.GRADIENT, primaryColor = Color(0xFF00F2FE), secondaryColor = Color(0xFF4FACFE)),
        BackgroundPreset(id = "grad_cosmic", name = "Cosmic Purple", type = BackgroundType.GRADIENT, primaryColor = Color(0xFF7F00FF), secondaryColor = Color(0xFFE100FF)),
        BackgroundPreset(id = "grad_emerald", name = "Emerald Forest", type = BackgroundType.GRADIENT, primaryColor = Color(0xFF11998E), secondaryColor = Color(0xFF38EF7D)),
        BackgroundPreset(id = "blur_bokeh", name = "Bokeh Blur", type = BackgroundType.BLUR, blurRadius = 25f),
        BackgroundPreset(id = "wall_cyber_city", name = "Cyber City", type = BackgroundType.SCENIC_WALLPAPER),
        BackgroundPreset(id = "wall_sunset_beach", name = "Sunset Beach", type = BackgroundType.SCENIC_WALLPAPER),
        BackgroundPreset(id = "wall_luxury_office", name = "Luxury Office", type = BackgroundType.SCENIC_WALLPAPER),
        BackgroundPreset(id = "wall_modern_loft", name = "Modern Studio", type = BackgroundType.SCENIC_WALLPAPER),
        BackgroundPreset(id = "wall_aesthetic_pastel", name = "Pastel Aesthetic", type = BackgroundType.SCENIC_WALLPAPER)
    )

    fun loadSampleDemo(demoId: String) {
        viewModelScope.launch {
            _mediaState.value = _mediaState.value.copy(
                isProcessing = true,
                processingStatusText = "Loading high-definition sample asset..."
            )
            val sampleBmp = SampleDemoAssets.createSampleBitmap(demoId)
            processNewImageBitmap(sampleBmp)
        }
    }

    fun loadFromUri(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            if (isVideo) {
                processNewVideoUri(context, uri)
            } else {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (bitmap != null) {
                        processNewImageBitmap(bitmap)
                    } else {
                        _mediaState.value = _mediaState.value.copy(
                            isProcessing = false,
                            errorMessage = "Could not decode selected image."
                        )
                    }
                } catch (e: Exception) {
                    _mediaState.value = _mediaState.value.copy(
                        isProcessing = false,
                        errorMessage = "Error loading image: ${e.message}"
                    )
                }
            }
        }
    }

    fun processNewImageBitmap(source: Bitmap) {
        viewModelScope.launch {
            _mediaState.value = ProcessedMediaState(
                mediaType = MediaType.IMAGE,
                originalBitmap = source,
                isProcessing = true,
                processingProgress = 0.2f,
                processingStatusText = "Detecting foreground subject with AI..."
            )

            val cutout = SegmentationEngine.extractCutout(
                source = source,
                feathering = _mediaState.value.feathering
            )

            _mediaState.value = _mediaState.value.copy(
                processingProgress = 0.7f,
                processingStatusText = "Rendering high-definition transparent cutout..."
            )

            val composite = SegmentationEngine.compositeImage(
                original = source,
                cutout = cutout,
                preset = _mediaState.value.selectedBgPreset,
                filter = _mediaState.value.selectedFilter,
                edgeEffect = _mediaState.value.selectedEdgeEffect,
                blurRadius = _mediaState.value.blurRadius,
                targetResolution = _mediaState.value.outputResolution
            )

            _mediaState.value = _mediaState.value.copy(
                cutoutBitmap = cutout,
                compositeBitmap = composite,
                isProcessing = false,
                processingProgress = 1.0f,
                processingStatusText = "Completed"
            )
        }
    }

    private suspend fun processNewVideoUri(context: Context, videoUri: Uri) {
        _mediaState.value = ProcessedMediaState(
            mediaType = MediaType.VIDEO,
            isProcessing = true,
            processingProgress = 0.05f,
            processingStatusText = "Analyzing video stream & extracting frames..."
        )

        val extracted = VideoProcessor.extractFramesFromUri(
            context = context,
            videoUri = videoUri,
            maxFrames = 15
        ) { progress, status ->
            _mediaState.value = _mediaState.value.copy(
                processingProgress = progress,
                processingStatusText = status
            )
        }

        if (extracted.isEmpty()) {
            _mediaState.value = _mediaState.value.copy(
                isProcessing = false,
                errorMessage = "Failed to extract frames from video. Please try another video."
            )
            return
        }

        val processed = VideoProcessor.processVideoFrames(
            frames = extracted,
            preset = BackgroundPreset(id = "solid_chroma_green", name = "Green Screen", type = BackgroundType.SOLID_COLOR, primaryColor = Color(0xFF00FF00)),
            filter = FilterEffect.NONE,
            edgeEffect = EdgeEffect.NONE,
            feathering = 1.5f
        ) { progress, status ->
            _mediaState.value = _mediaState.value.copy(
                processingProgress = progress,
                processingStatusText = status
            )
        }

        _mediaState.value = _mediaState.value.copy(
            videoFrames = processed,
            currentFrameIndex = 0,
            originalBitmap = processed.firstOrNull()?.originalBitmap,
            cutoutBitmap = processed.firstOrNull()?.cutoutBitmap,
            compositeBitmap = processed.firstOrNull()?.compositeBitmap,
            selectedBgPreset = BackgroundPreset(id = "solid_chroma_green", name = "Green Screen", type = BackgroundType.SOLID_COLOR, primaryColor = Color(0xFF00FF00)),
            isProcessing = false,
            processingProgress = 1.0f,
            processingStatusText = "Video ready!"
        )

        startVideoPlayback()
    }

    fun selectBackgroundPreset(preset: BackgroundPreset) {
        val current = _mediaState.value
        _mediaState.value = current.copy(selectedBgPreset = preset, selectedBgType = preset.type)
        recomputeComposite()
    }

    fun selectCustomBackground(bitmap: Bitmap) {
        val current = _mediaState.value
        val customPreset = BackgroundPreset(id = "custom", name = "Custom Gallery", type = BackgroundType.CUSTOM_IMAGE)
        _mediaState.value = current.copy(
            selectedBgPreset = customPreset,
            selectedBgType = BackgroundType.CUSTOM_IMAGE,
            customBgBitmap = bitmap
        )
        recomputeComposite()
    }

    fun selectFilter(filter: FilterEffect) {
        _mediaState.value = _mediaState.value.copy(selectedFilter = filter)
        recomputeComposite()
    }

    fun selectEdgeEffect(edge: EdgeEffect) {
        _mediaState.value = _mediaState.value.copy(selectedEdgeEffect = edge)
        recomputeComposite()
    }

    fun setBlurRadius(radius: Float) {
        _mediaState.value = _mediaState.value.copy(blurRadius = radius)
        if (_mediaState.value.selectedBgType == BackgroundType.BLUR) {
            recomputeComposite()
        }
    }

    fun setFeathering(feather: Float) {
        _mediaState.value = _mediaState.value.copy(feathering = feather)
        val orig = _mediaState.value.originalBitmap ?: return
        viewModelScope.launch {
            val newCutout = SegmentationEngine.extractCutout(orig, feathering = feather)
            _mediaState.value = _mediaState.value.copy(cutoutBitmap = newCutout)
            recomputeComposite()
        }
    }

    fun setOutputResolution(resolution: OutputResolution) {
        _mediaState.value = _mediaState.value.copy(outputResolution = resolution)
        recomputeComposite()
    }

    fun triggerGeminiEnhance(customPrompt: String) {
        val orig = _mediaState.value.originalBitmap ?: return
        val key = getEffectiveApiKey()
        if (key.isBlank()) {
            _mediaState.value = _mediaState.value.copy(
                errorMessage = "Gemini API key is required. Go to Settings to enter your API key."
            )
            return
        }

        viewModelScope.launch {
            _mediaState.value = _mediaState.value.copy(
                isProcessing = true,
                processingStatusText = "Sending to Gemini AI Vision (${apiSettings.value.activeModel})..."
            )

            val result = GeminiVisionService.processImageWithGemini(
                apiKey = key,
                model = apiSettings.value.activeModel,
                bitmap = orig,
                prompt = customPrompt
            )

            result.onSuccess { returnedBitmap ->
                if (returnedBitmap != null) {
                    val newCutout = SegmentationEngine.extractCutout(returnedBitmap, _mediaState.value.feathering)
                    _mediaState.value = _mediaState.value.copy(
                        originalBitmap = returnedBitmap,
                        cutoutBitmap = newCutout,
                        isProcessing = false,
                        processingStatusText = "Gemini AI enhancement applied!"
                    )
                    recomputeComposite()
                } else {
                    _mediaState.value = _mediaState.value.copy(
                        isProcessing = false,
                        processingStatusText = "AI Vision processing completed."
                    )
                }
            }.onFailure { err ->
                _mediaState.value = _mediaState.value.copy(
                    isProcessing = false,
                    errorMessage = "Gemini AI error: ${err.message}"
                )
            }
        }
    }

    private fun recomputeComposite() {
        val state = _mediaState.value
        val orig = state.originalBitmap ?: return
        val cutout = state.cutoutBitmap ?: return

        viewModelScope.launch {
            if (state.mediaType == MediaType.IMAGE) {
                val composite = SegmentationEngine.compositeImage(
                    original = orig,
                    cutout = cutout,
                    preset = state.selectedBgPreset,
                    customBg = state.customBgBitmap,
                    filter = state.selectedFilter,
                    edgeEffect = state.selectedEdgeEffect,
                    blurRadius = state.blurRadius,
                    targetResolution = state.outputResolution
                )
                _mediaState.value = _mediaState.value.copy(compositeBitmap = composite)
            } else {
                // Update current video frame composite
                val updatedFrames = state.videoFrames.map { frame ->
                    val frameCutout = frame.cutoutBitmap ?: cutout
                    val comp = SegmentationEngine.compositeImage(
                        original = frame.originalBitmap,
                        cutout = frameCutout,
                        preset = state.selectedBgPreset,
                        customBg = state.customBgBitmap,
                        filter = state.selectedFilter,
                        edgeEffect = state.selectedEdgeEffect,
                        blurRadius = state.blurRadius,
                        targetResolution = OutputResolution.HD_1080P
                    )
                    frame.copy(compositeBitmap = comp)
                }
                _mediaState.value = _mediaState.value.copy(
                    videoFrames = updatedFrames,
                    compositeBitmap = updatedFrames.getOrNull(state.currentFrameIndex)?.compositeBitmap
                )
            }
        }
    }

    fun startVideoPlayback() {
        videoPlaybackJob?.cancel()
        _mediaState.value = _mediaState.value.copy(isPlayingVideo = true)
        videoPlaybackJob = viewModelScope.launch {
            while (_mediaState.value.isPlayingVideo && _mediaState.value.videoFrames.isNotEmpty()) {
                val frames = _mediaState.value.videoFrames
                val nextIdx = (_mediaState.value.currentFrameIndex + 1) % frames.size
                val nextFrame = frames[nextIdx]
                _mediaState.value = _mediaState.value.copy(
                    currentFrameIndex = nextIdx,
                    compositeBitmap = nextFrame.compositeBitmap ?: nextFrame.originalBitmap
                )
                delay(1000L / _mediaState.value.videoFps)
            }
        }
    }

    fun pauseVideoPlayback() {
        videoPlaybackJob?.cancel()
        _mediaState.value = _mediaState.value.copy(isPlayingVideo = false)
    }

    fun seekVideoFrame(index: Int) {
        val frames = _mediaState.value.videoFrames
        if (index in frames.indices) {
            _mediaState.value = _mediaState.value.copy(
                currentFrameIndex = index,
                compositeBitmap = frames[index].compositeBitmap ?: frames[index].originalBitmap
            )
        }
    }

    fun saveCreationToDevice(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val state = _mediaState.value
        val bitmapToSave = if (state.selectedBgType == BackgroundType.TRANSPARENT) {
            state.cutoutBitmap
        } else {
            state.compositeBitmap
        } ?: state.originalBitmap

        if (bitmapToSave == null) {
            onError("No image available to save.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val isPng = state.selectedBgType == BackgroundType.TRANSPARENT
                val ext = if (isPng) "png" else "jpg"
                val filename = "BG_Remover_${timestamp}.$ext"

                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                val outputFile = File(picturesDir, filename)

                FileOutputStream(outputFile).use { out ->
                    if (isPng) {
                        bitmapToSave.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } else {
                        bitmapToSave.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                }

                // Save to Room database history
                val entity = ProcessedItemEntity(
                    id = state.id,
                    title = if (state.mediaType == MediaType.VIDEO) "Video Cutout $timestamp" else "HD Image $timestamp",
                    mediaType = state.mediaType.name,
                    timestamp = System.currentTimeMillis(),
                    imagePath = outputFile.absolutePath,
                    thumbnailPath = outputFile.absolutePath,
                    bgType = state.selectedBgType.name,
                    filterName = state.selectedFilter.displayName,
                    resolution = state.outputResolution.badge,
                    frameCount = if (state.mediaType == MediaType.VIDEO) state.videoFrames.size else 1
                )
                historyDao.insertItem(entity)

                withContext(Dispatchers.Main) {
                    onSuccess("Saved in High-Definition: ${outputFile.name}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Failed to save: ${e.message}")
                }
            }
        }
    }

    fun shareCreation(context: Context) {
        val state = _mediaState.value
        val bmp = if (state.selectedBgType == BackgroundType.TRANSPARENT) {
            state.cutoutBitmap
        } else {
            state.compositeBitmap
        } ?: state.originalBitmap ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "shared_bg_remover.png")
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, "Share Cutout HD Image"))
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun testApiKey(key: String, model: String) {
        viewModelScope.launch {
            _isTestingApiKey.value = true
            _apiTestStatus.value = "Testing API connection with $model..."
            val result = GeminiVisionService.testApiKey(key, model)
            result.onSuccess { msg ->
                _apiTestStatus.value = "Success! API Key is active & verified."
            }.onFailure { err ->
                _apiTestStatus.value = "Failed: ${err.message}"
            }
            _isTestingApiKey.value = false
        }
    }

    fun saveApiKey(key: String) = preferencesManager.saveApiKey(key)
    fun updateApiKey(key: String) = preferencesManager.saveApiKey(key)
    fun saveModel(model: String) = preferencesManager.saveModel(model)
    fun updateModel(model: String) = preferencesManager.saveModel(model)
    fun saveQuality(quality: OutputResolution) = preferencesManager.saveQuality(quality)
    fun updateQuality(quality: OutputResolution) = preferencesManager.saveQuality(quality)
    fun toggleAutoFeathering(enabled: Boolean) = preferencesManager.saveAutoFeather(enabled)
    fun saveLanguage(lang: String) = preferencesManager.saveLanguage(lang)
    fun setPreferredLanguage(lang: String) = preferencesManager.saveLanguage(lang)

    fun deleteHistoryItem(item: ProcessedItemEntity) {
        viewModelScope.launch {
            historyDao.deleteItem(item)
            try {
                File(item.imagePath).delete()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun clearErrorMessage() {
        _mediaState.value = _mediaState.value.copy(errorMessage = null)
    }

    fun resetState() {
        videoPlaybackJob?.cancel()
        _mediaState.value = ProcessedMediaState()
    }

    fun getEffectiveApiKey(): String {
        val custom = apiSettings.value.customApiKey
        if (custom.isNotBlank()) return custom
        return try {
            com.example.BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }
}
