package com.example.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.AiAnswerCard
import com.example.ui.components.EmptyWelcomeCanvas
import com.example.ui.components.ErrorAnswerBanner
import com.example.ui.components.FollowUpChipsRow
import com.example.ui.components.HistorySidebarContent
import com.example.ui.components.LuminaryBackdrop
import com.example.ui.components.QuestionComposerBar
import com.example.ui.components.SavedAnswersView
import com.example.ui.components.StreamingAnswerCard
import com.example.ui.components.UserQuestionBubble
import com.example.ui.studios.CreateStudioScreen
import com.example.ui.studios.SqlStudioScreen
import com.example.ui.studios.ThreeDStudioScreen
import com.example.ui.studios.VoiceStudioScreen
import com.example.ui.theme.BackgroundStyle
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.launch
import java.io.File

private fun LuminaryTab.icon(): ImageVector = when (this) {
    LuminaryTab.CHAT -> Icons.Outlined.ChatBubbleOutline
    LuminaryTab.CREATE -> Icons.Outlined.AutoAwesome
    LuminaryTab.VOICE -> Icons.Outlined.GraphicEq
    LuminaryTab.THREE_D -> Icons.Outlined.ViewInAr
    LuminaryTab.SQL -> Icons.Outlined.Storage
}

private fun LuminaryTab.menuTitle(): String = when (this) {
    LuminaryTab.CHAT -> "Chatbot"
    LuminaryTab.CREATE -> "Images & Video (Veo)"
    LuminaryTab.VOICE -> "Voice & Transcribe"
    LuminaryTab.THREE_D -> "3D Scenes (Three.js)"
    LuminaryTab.SQL -> "SQLite Database"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuminaryScreen(
    viewModel: LuminaryViewModel,
    studioViewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val activeThreadId by viewModel.activeThreadId.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val bookmarkedMessages by viewModel.bookmarkedMessages.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
    val backgroundStyle by viewModel.backgroundStyle.collectAsStateWithLifecycle()
    val useGoogleSearch by viewModel.useGoogleSearch.collectAsStateWithLifecycle()
    val useGoogleMaps by viewModel.useGoogleMaps.collectAsStateWithLifecycle()
    val locationBias by viewModel.locationBias.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val followUps by viewModel.followUps.collectAsStateWithLifecycle()
    val attachedImage by viewModel.attachedImage.collectAsStateWithLifecycle()
    val attachmentError by viewModel.attachmentError.collectAsStateWithLifecycle()
    val ttsSpeakingMessageId by viewModel.ttsSpeakingMessageId.collectAsStateWithLifecycle()

    val creations by studioViewModel.creations.collectAsStateWithLifecycle()
    val imageState by studioViewModel.imageState.collectAsStateWithLifecycle()
    val videoState by studioViewModel.videoState.collectAsStateWithLifecycle()
    val voiceState by studioViewModel.voiceState.collectAsStateWithLifecycle()
    val threeDState by studioViewModel.threeDState.collectAsStateWithLifecycle()
    val sqlState by studioViewModel.sqlState.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var showingSavedScreen by rememberSaveable { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableStateOf(LuminaryTab.CHAT) }
    var studiosMenuExpanded by remember { mutableStateOf(false) }
    var toolsPanelExpanded by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshDeviceLocation()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Location permission denied; Maps will answer without GPS bias.")
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImageFromUri(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            viewModel.attachImageFromUri(uri)
        }
    }

    LaunchedEffect(attachmentError) {
        val err = attachmentError
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.dismissAttachmentError()
        }
    }

    BackHandler(enabled = showingSavedScreen || drawerState.isOpen || activeTab != LuminaryTab.CHAT || toolsPanelExpanded) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            toolsPanelExpanded -> toolsPanelExpanded = false
            showingSavedScreen -> showingSavedScreen = false
            activeTab != LuminaryTab.CHAT -> activeTab = LuminaryTab.CHAT
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpandedLayout = maxWidth >= 840.dp

        val mainContent: @Composable () -> Unit = {
            Box(modifier = Modifier.fillMaxSize()) {
                LuminaryBackdrop(backgroundStyle = backgroundStyle)
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        CenterAlignedTopAppBar(
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent
                            ),
                        navigationIcon = {
                            if (activeTab != LuminaryTab.CHAT) {
                                IconButton(
                                    onClick = { activeTab = LuminaryTab.CHAT },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("back_to_chat_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.action_back_to_chat)
                                    )
                                }
                            } else if (!isExpandedLayout) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("history_drawer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Menu,
                                        contentDescription = stringResource(R.string.action_history)
                                    )
                                }
                            }
                        },
                        title = {
                            Text(
                                text = if (activeTab == LuminaryTab.CHAT) {
                                    stringResource(R.string.app_name)
                                } else {
                                    activeTab.menuTitle()
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        },
                        actions = {
                            Box {
                                IconButton(
                                    onClick = { studiosMenuExpanded = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("studios_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Apps,
                                        contentDescription = stringResource(R.string.action_studios_menu),
                                        tint = if (activeTab != LuminaryTab.CHAT) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                DropdownMenu(
                                    expanded = studiosMenuExpanded,
                                    onDismissRequest = { studiosMenuExpanded = false }
                                ) {
                                    LuminaryTab.entries.forEach { tab ->
                                        DropdownMenuItem(
                                            text = { Text(tab.menuTitle()) },
                                            leadingIcon = {
                                                Icon(tab.icon(), contentDescription = null)
                                            },
                                            trailingIcon = {
                                                if (activeTab == tab && !showingSavedScreen) {
                                                    Icon(
                                                        Icons.Outlined.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
                                            onClick = {
                                                studiosMenuExpanded = false
                                                showingSavedScreen = false
                                                activeTab = tab
                                            },
                                            modifier = Modifier.testTag("menu_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    activeTab = LuminaryTab.CHAT
                                    showingSavedScreen = !showingSavedScreen
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("saved_answers_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkBorder,
                                    contentDescription = stringResource(R.string.action_saved),
                                    tint = if (showingSavedScreen) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            IconButton(
                                onClick = { viewModel.cycleThemePreference() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = when (themePreference) {
                                        ThemePreference.SYSTEM -> Icons.Outlined.BrightnessAuto
                                        ThemePreference.DARK -> Icons.Outlined.DarkMode
                                        ThemePreference.LIGHT -> Icons.Outlined.LightMode
                                    },
                                    contentDescription = stringResource(R.string.action_toggle_theme),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (activeTab == LuminaryTab.CHAT) {
                                IconButton(
                                    onClick = {
                                        showingSavedScreen = false
                                        viewModel.startNewQuestion()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("new_question_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = stringResource(R.string.action_new_question)
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (activeTab == LuminaryTab.CHAT && !showingSavedScreen) {
                        val isGenerating = generationState is GenerationState.Loading ||
                            generationState is GenerationState.Streaming
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            QuestionComposerBar(
                                inputText = inputText,
                                onInputChange = { inputText = it },
                                attachedImage = attachedImage,
                                isGenerating = isGenerating,
                                selectedMode = selectedMode,
                                showCompactModeRow = messages.isNotEmpty(),
                                onSelectMode = { viewModel.selectMode(it) },
                                includeNavBarsPadding = true,
                                toolsPanelExpanded = toolsPanelExpanded,
                                onToggleToolsPanel = { toolsPanelExpanded = !toolsPanelExpanded },
                                toolsPanelContent = {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Live Data",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.horizontalScroll(rememberScrollState())
                                            ) {
                                                FilterChip(
                                                    selected = useGoogleSearch,
                                                    onClick = { viewModel.toggleGoogleSearch() },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Outlined.Public,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    },
                                                    label = { Text(stringResource(R.string.grounding_search)) },
                                                    modifier = Modifier.testTag("toggle_google_search_chip")
                                                )
                                                FilterChip(
                                                    selected = useGoogleMaps,
                                                    onClick = { viewModel.toggleGoogleMaps() },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Outlined.Map,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    },
                                                    label = { Text(stringResource(R.string.grounding_maps)) },
                                                    modifier = Modifier.testTag("toggle_google_maps_chip")
                                                )
                                                if (useGoogleMaps) {
                                                    FilterChip(
                                                        selected = locationBias != null,
                                                        onClick = {
                                                            if (locationBias != null) {
                                                                viewModel.clearLocationBias()
                                                            } else {
                                                                val granted = ContextCompat.checkSelfPermission(
                                                                    context,
                                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                                ) == PackageManager.PERMISSION_GRANTED
                                                                if (granted) {
                                                                    viewModel.refreshDeviceLocation()
                                                                } else {
                                                                    locationPermissionLauncher.launch(
                                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Outlined.LocationOn,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        label = {
                                                            Text(
                                                                if (locationBias != null) "GPS On"
                                                                else stringResource(R.string.grounding_location)
                                                            )
                                                        },
                                                        modifier = Modifier.testTag("toggle_location_bias_chip")
                                                    )
                                                }
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Text(
                                                text = "AI Studios",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.horizontalScroll(rememberScrollState())
                                            ) {
                                                LuminaryTab.entries
                                                    .filter { it != LuminaryTab.CHAT }
                                                    .forEach { tab ->
                                                        AssistChip(
                                                            onClick = {
                                                                toolsPanelExpanded = false
                                                                showingSavedScreen = false
                                                                activeTab = tab
                                                            },
                                                            leadingIcon = {
                                                                Icon(
                                                                    tab.icon(),
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            },
                                                            label = { Text(tab.label) },
                                                            modifier = Modifier.testTag("panel_studio_${tab.name.lowercase()}")
                                                        )
                                                    }
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Text(
                                                text = "Background",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.horizontalScroll(rememberScrollState())
                                            ) {
                                                BackgroundStyle.entries.forEach { style ->
                                                    val isSelected = backgroundStyle == style
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = { viewModel.selectBackgroundStyle(style) },
                                                        leadingIcon = if (isSelected) {
                                                            {
                                                                Icon(
                                                                    Icons.Outlined.Palette,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        } else null,
                                                        label = { Text(style.label) },
                                                        modifier = Modifier.testTag("bg_style_${style.name.lowercase()}")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                onCameraClick = {
                                    try {
                                        val captureDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
                                        val captureFile = File.createTempFile("capture_", ".jpg", captureDir)
                                        val captureUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            captureFile
                                        )
                                        pendingCameraUri = captureUri
                                        cameraLauncher.launch(captureUri)
                                    } catch (_: ActivityNotFoundException) {
                                        pendingCameraUri = null
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.error_no_camera))
                                        }
                                    } catch (_: Exception) {
                                        pendingCameraUri = null
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.error_no_camera))
                                        }
                                    }
                                },
                                onAttachClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onClearAttachment = { viewModel.clearAttachment() },
                                onSend = {
                                    val query = inputText
                                    inputText = ""
                                    viewModel.askQuestion(query)
                                },
                                onStop = { viewModel.stopGenerating() },
                                modifier = Modifier.widthIn(max = 780.dp)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .then(
                            if (activeTab != LuminaryTab.CHAT || showingSavedScreen) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (showingSavedScreen) {
                        SavedAnswersView(
                            bookmarks = bookmarkedMessages,
                            onBack = { showingSavedScreen = false },
                            onOpenThread = { threadId ->
                                showingSavedScreen = false
                                activeTab = LuminaryTab.CHAT
                                viewModel.openThread(threadId)
                            },
                            onRemoveBookmark = { viewModel.toggleBookmark(it) },
                            modifier = Modifier.widthIn(max = 780.dp)
                        )
                    } else {
                        when (activeTab) {
                            LuminaryTab.CHAT -> {
                                if (messages.isEmpty() && generationState is GenerationState.Idle) {
                                    EmptyWelcomeCanvas(
                                        selectedMode = selectedMode,
                                        onSelectMode = { viewModel.selectMode(it) },
                                        onSelectStarter = { starter ->
                                            viewModel.askQuestion(
                                                rawQuestion = starter.question,
                                                overrideMode = starter.mode
                                            )
                                        }
                                    )
                                } else {
                                    val listState = rememberLazyListState()
                                    val lastModelIndex = messages.indexOfLast { it.role == "model" }

                                    LaunchedEffect(messages.size, generationState) {
                                        val totalItems = messages.size +
                                            (if (generationState !is GenerationState.Idle) 1 else 0) +
                                            (if (followUps.isNotEmpty()) 1 else 0)
                                        if (totalItems > 0) {
                                            listState.animateScrollToItem((totalItems - 1).coerceAtLeast(0))
                                        }
                                    }

                                    LazyColumn(
                                        state = listState,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .widthIn(max = 780.dp)
                                    ) {
                                        itemsIndexed(
                                            items = messages,
                                            key = { _, item -> item.id }
                                        ) { index, message ->
                                            if (message.role == "user") {
                                                UserQuestionBubble(message = message)
                                            } else {
                                                AiAnswerCard(
                                                    message = message,
                                                    isLastAnswer = index == lastModelIndex &&
                                                        generationState is GenerationState.Idle,
                                                    isSpeaking = ttsSpeakingMessageId == message.id,
                                                    onToggleBookmark = { viewModel.toggleBookmark(message) },
                                                    onToggleSpeak = { viewModel.toggleSpeak(message.id, message.text) },
                                                    onRegenerate = { viewModel.regenerateLastAnswer() }
                                                )
                                            }
                                        }

                                        when (val state = generationState) {
                                            is GenerationState.Loading -> {
                                                item(key = "streaming_loading") {
                                                    StreamingAnswerCard(
                                                        text = null,
                                                        modeLabel = selectedMode.label
                                                    )
                                                }
                                            }
                                            is GenerationState.Streaming -> {
                                                item(key = "streaming_active") {
                                                    StreamingAnswerCard(
                                                        text = state.text,
                                                        modeLabel = state.modeLabel,
                                                        thoughts = state.thoughts,
                                                        thinkingSeconds = state.thinkingSeconds
                                                    )
                                                }
                                            }
                                            is GenerationState.Error -> {
                                                item(key = "generation_error") {
                                                    ErrorAnswerBanner(
                                                        message = state.message,
                                                        onRetry = { viewModel.retryLastError() }
                                                    )
                                                }
                                            }
                                            GenerationState.Idle -> Unit
                                        }

                                        if (followUps.isNotEmpty() && generationState is GenerationState.Idle) {
                                            item(key = "followup_suggestions") {
                                                FollowUpChipsRow(
                                                    followUps = followUps,
                                                    onSelectFollowUp = { followUp ->
                                                        viewModel.askQuestion(followUp)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            LuminaryTab.CREATE -> {
                                CreateStudioScreen(
                                    studioViewModel = studioViewModel,
                                    imageState = imageState,
                                    videoState = videoState,
                                    creations = creations
                                )
                            }

                            LuminaryTab.VOICE -> {
                                VoiceStudioScreen(
                                    studioViewModel = studioViewModel,
                                    voiceState = voiceState,
                                    creations = creations,
                                    onAskInChatbot = { prompt ->
                                        activeTab = LuminaryTab.CHAT
                                        viewModel.startNewQuestion()
                                        viewModel.askQuestion(prompt)
                                    }
                                )
                            }

                            LuminaryTab.THREE_D -> {
                                ThreeDStudioScreen(
                                    studioViewModel = studioViewModel,
                                    state = threeDState,
                                    creations = creations
                                )
                            }

                            LuminaryTab.SQL -> {
                                SqlStudioScreen(
                                    studioViewModel = studioViewModel,
                                    state = sqlState
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        if (isExpandedLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                HistorySidebarContent(
                    threads = threads,
                    activeThreadId = activeThreadId,
                    savedCount = bookmarkedMessages.size,
                    onNewQuestion = {
                        showingSavedScreen = false
                        activeTab = LuminaryTab.CHAT
                        viewModel.startNewQuestion()
                    },
                    onSelectThread = { id ->
                        showingSavedScreen = false
                        activeTab = LuminaryTab.CHAT
                        viewModel.openThread(id)
                    },
                    onDeleteThread = { viewModel.deleteThread(it) },
                    onOpenSaved = {
                        activeTab = LuminaryTab.CHAT
                        showingSavedScreen = true
                    },
                    modifier = Modifier
                        .width(284.dp)
                        .fillMaxHeight()
                        .statusBarsPadding()
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(modifier = Modifier.weight(1f)) {
                    mainContent()
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.safeDrawing
                    ) {
                        HistorySidebarContent(
                            threads = threads,
                            activeThreadId = activeThreadId,
                            savedCount = bookmarkedMessages.size,
                            onNewQuestion = {
                                showingSavedScreen = false
                                activeTab = LuminaryTab.CHAT
                                viewModel.startNewQuestion()
                                scope.launch { drawerState.close() }
                            },
                            onSelectThread = { id ->
                                showingSavedScreen = false
                                activeTab = LuminaryTab.CHAT
                                viewModel.openThread(id)
                                scope.launch { drawerState.close() }
                            },
                            onDeleteThread = { viewModel.deleteThread(it) },
                            onOpenSaved = {
                                activeTab = LuminaryTab.CHAT
                                showingSavedScreen = true
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.width(300.dp)
                        )
                    }
                }
            ) {
                mainContent()
            }
        }
    }
}
