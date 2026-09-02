package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BackgroundPreset
import com.example.data.model.BackgroundType
import com.example.data.model.EdgeEffect
import com.example.data.model.FilterEffect
import com.example.data.model.OutputResolution
import com.example.ui.components.CheckerboardBackground
import com.example.ui.components.ComparisonSlider
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaState by viewModel.mediaState.collectAsState()
    val apiSettings by viewModel.apiSettings.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: BG, 1: Filters, 2: Edge Polish, 3: AI Magic
    var isCompareMode by remember { mutableStateOf(false) }
    var resolutionMenuExpanded by remember { mutableStateOf(false) }
    var customAiPrompt by remember { mutableStateOf("Inpaint a luxury modern photo studio background with warm soft lighting") }

    val customBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                stream?.close()
                if (bmp != null) {
                    viewModel.selectCustomBackground(bmp)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading background image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PureWhite
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "HD Editor",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        // Resolution Badge with Dropdown
                        Box {
                            Surface(
                                color = ObsidianSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.clickable { resolutionMenuExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Hd,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = mediaState.outputResolution.badge,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = resolutionMenuExpanded,
                                onDismissRequest = { resolutionMenuExpanded = false },
                                modifier = Modifier.background(ObsidianSurface)
                            ) {
                                OutputResolution.values().forEach { res ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = res.label, color = TextPrimary, fontSize = 13.sp)
                                                if (mediaState.outputResolution == res) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = NeonCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.setOutputResolution(res)
                                            resolutionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Compare Slider Toggle
                    IconButton(
                        onClick = { isCompareMode = !isCompareMode },
                        modifier = Modifier.testTag("compare_mode_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Compare",
                            tint = if (isCompareMode) NeonCyan else TextSecondary
                        )
                    }

                    // Share
                    IconButton(
                        onClick = { viewModel.shareCreation(context) },
                        modifier = Modifier.testTag("share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextSecondary
                        )
                    }

                    // Save HD
                    Button(
                        onClick = {
                            viewModel.saveCreationToDevice(
                                onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
                                onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_hd_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save HD",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBackground
                )
            )
        },
        containerColor = ObsidianBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Preview Canvas (Takes top area)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                val orig = mediaState.originalBitmap
                val cutout = mediaState.cutoutBitmap
                val composite = mediaState.compositeBitmap

                if (orig != null && cutout != null) {
                    if (isCompareMode) {
                        val targetDisplay = if (mediaState.selectedBgType == BackgroundType.TRANSPARENT) {
                            cutout
                        } else {
                            composite ?: cutout
                        }
                        ComparisonSlider(
                            beforeBitmap = orig,
                            afterBitmap = targetDisplay,
                            isTransparentBg = mediaState.selectedBgType == BackgroundType.TRANSPARENT
                        )
                    } else {
                        if (mediaState.selectedBgType == BackgroundType.TRANSPARENT) {
                            CheckerboardBackground()
                            Image(
                                bitmap = cutout.asImageBitmap(),
                                contentDescription = "Cutout Result",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (composite != null) {
                            Image(
                                bitmap = composite.asImageBitmap(),
                                contentDescription = "Composite Result",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Processing Spinner Overlay
                if (mediaState.isProcessing) {
                    Surface(
                        color = ObsidianBackground.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = mediaState.processingStatusText.ifBlank { "Processing AI cutout..." },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Bottom Floating Studio Controls Panel
            Card(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = NeonCyan,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                height = 3.dp,
                                color = NeonCyan
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Backgrounds", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            selectedContentColor = NeonCyan,
                            unselectedContentColor = TextMuted
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Filters", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Filter, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            selectedContentColor = NeonCyan,
                            unselectedContentColor = TextMuted
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Edge Polish", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            selectedContentColor = NeonCyan,
                            unselectedContentColor = TextMuted
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("AI Magic", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            selectedContentColor = ElectricVioletLight,
                            unselectedContentColor = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab 0: Background Options
                    if (selectedTab == 0) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = ObsidianSurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clickable {
                                                customBgPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                            .testTag("upload_custom_bg")
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddPhotoAlternate,
                                                contentDescription = null,
                                                tint = NeonCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Upload",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }

                                items(viewModel.backgroundPresets) { preset ->
                                    val isSelected = mediaState.selectedBgPreset.id == preset.id
                                    BackgroundPresetChip(
                                        preset = preset,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectBackgroundPreset(preset) }
                                    )
                                }
                            }

                            if (mediaState.selectedBgType == BackgroundType.BLUR) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Bokeh Blur: ${(mediaState.blurRadius).toInt()}px",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.width(100.dp)
                                    )
                                    Slider(
                                        value = mediaState.blurRadius,
                                        onValueChange = { viewModel.setBlurRadius(it) },
                                        valueRange = 5f..60f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = NeonCyan,
                                            activeTrackColor = NeonCyan
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Tab 1: Filters
                    if (selectedTab == 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(FilterEffect.values()) { filter ->
                                val isSelected = mediaState.selectedFilter == filter
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) ElectricViolet.copy(alpha = 0.25f) else ObsidianSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        if (isSelected) ElectricViolet else ObsidianBorder
                                    ),
                                    modifier = Modifier
                                        .clickable { viewModel.selectFilter(filter) }
                                        .testTag("filter_${filter.name}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = filter.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) ElectricVioletLight else TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Tab 2: Edge Polish & Glow
                    if (selectedTab == 2) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Feather: ${String.format("%.1f", mediaState.feathering)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.width(90.dp)
                                )
                                Slider(
                                    value = mediaState.feathering,
                                    onValueChange = { viewModel.setFeathering(it) },
                                    valueRange = 0.5f..5.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ElectricViolet,
                                        activeTrackColor = ElectricViolet
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "EDGE STYLING & GLOW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(EdgeEffect.values()) { edge ->
                                    val isSelected = mediaState.selectedEdgeEffect == edge
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else ObsidianSurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.5.dp,
                                            if (isSelected) NeonCyan else ObsidianBorder
                                        ),
                                        modifier = Modifier.clickable { viewModel.selectEdgeEffect(edge) }
                                    ) {
                                        Text(
                                            text = edge.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) NeonCyan else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Tab 3: Gemini AI Magic Inpainting
                    if (selectedTab == 3) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = ElectricVioletLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Model: ${apiSettings.activeModel}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ElectricVioletLight
                                    )
                                }
                                Text(
                                    text = "Configure Key",
                                    fontSize = 11.sp,
                                    color = NeonCyan,
                                    modifier = Modifier.clickable { onNavigateToSettings() }
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = customAiPrompt,
                                onValueChange = { customAiPrompt = it },
                                placeholder = { Text("Enter prompt for background generation...", fontSize = 12.sp) },
                                singleLine = false,
                                maxLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricViolet,
                                    unfocusedBorderColor = ObsidianBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.triggerGeminiEnhance(customAiPrompt) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricViolet
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gemini_ai_generate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Generate with Gemini Vision AI",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundPresetChip(
    preset: BackgroundPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (isSelected) NeonCyan else ObsidianBorder
        ),
        modifier = Modifier
            .size(64.dp)
            .clickable { onClick() }
            .testTag("preset_${preset.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (preset.type) {
                BackgroundType.TRANSPARENT -> {
                    CheckerboardBackground(squareSizePx = 14f)
                }
                BackgroundType.SOLID_COLOR -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(preset.primaryColor)
                    )
                }
                BackgroundType.GRADIENT -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(listOf(preset.primaryColor, preset.secondaryColor))
                            )
                    )
                }
                BackgroundType.BLUR -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF334155)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Blur", fontSize = 11.sp, color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
                BackgroundType.SCENIC_WALLPAPER -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF06B6D4)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Wall", fontSize = 10.sp, color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
                BackgroundType.CUSTOM_IMAGE -> {}
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Text(
                    text = preset.name,
                    fontSize = 8.sp,
                    color = PureWhite,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                )
            }
        }
    }
}
