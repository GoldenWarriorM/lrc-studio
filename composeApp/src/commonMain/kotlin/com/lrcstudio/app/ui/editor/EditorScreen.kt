package com.lrcstudio.app.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lrcstudio.app.data.parser.LrcParser
import com.lrcstudio.app.ui.picker.rememberLrcFileSaveLauncher
import com.lrcstudio.app.util.SetImmersiveMode
import com.lrcstudio.app.util.isDesktop
import com.lrcstudio.app.ui.player.PlaybackState
import com.lrcstudio.app.ui.player.PlayerBar
import com.lrcstudio.app.util.rememberFileExistsChecker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onImportAudioFile: () -> Unit,
    compactControls: Boolean = false,
    swipeDeleteThresholdDp: Int = 130,
    swipeActivationThresholdDp: Int = 20,
    swipeGesturesEnabled: Boolean = true,
    showSnapButton: Boolean = false,
    showClearDeleteButton: Boolean = false,
    swipeInstantDelete: Boolean = false,
    showDebugBorders: Boolean = false,
    showUndoRedo: Boolean = true,
    showVibrationToast: Boolean = false,
    lrcSaveDirectory: String? = null,
    forceLandscapeEditor: Boolean = false,
    landscapeInverted: Boolean = false,
    ignoreCutout: Boolean = false,
    landscapeSplitRatio: Float = 0.5f,
    landscapeOverlay: Boolean = false,
    disableFullscreen: Boolean = false,
    isEnhancedLrcEnabled: Boolean = false,
    skipStandalonePunctuation: Boolean = true
) {
    val state by viewModel.state.collectAsState()
    val playerState by viewModel.audioPlayer.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val checkFileExists = rememberFileExistsChecker(lrcSaveDirectory)

    var showShiftDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var isPreviewMode by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }
    var pendingOverwriteContent by remember { mutableStateOf<String?>(null) }
    var pendingOverwriteFileName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val timeInteractionSource = remember { MutableInteractionSource() }
    val isTimePressed by timeInteractionSource.collectIsPressedAsState()
    val timeScale by animateFloatAsState(
        targetValue = if (isTimePressed) 1f else 140f / 160f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "timeScale"
    )
    val saveLrcFile = rememberLrcFileSaveLauncher(
        defaultName = "${state.song?.title ?: "lyrics"}.lrc",
        directory = lrcSaveDirectory,
        onSuccess = { scope.launch { snackbarHostState.showSnackbar("LRC saved") } },
        onError = { msg -> scope.launch { snackbarHostState.showSnackbar("Save failed: $msg") } }
    )

    val displayItems = remember(state.lyrics, state.editingLineIndex) {
        buildList {
            state.lyrics.forEachIndexed { i, line ->
                if (i == state.editingLineIndex) {
                    add(DisplayItem.InsertAbove(i))
                }
                add(DisplayItem.Line(line, i))
                if (i == state.editingLineIndex) {
                    add(DisplayItem.InsertBelow(i))
                }
            }
        }
    }

    LaunchedEffect(state.currentLineIndex) {
        if (state.currentLineIndex >= 0 && playerState.state == PlaybackState.PLAYING && autoScrollEnabled) {
            centerItem(state.currentLineIndex, listState)
        }
    }

    LaunchedEffect(state.selectedLineIndex) {
        if (playerState.state != PlaybackState.PLAYING && state.selectedLineIndex >= 0 && autoScrollEnabled) {
            centerItem(state.selectedLineIndex, listState)
        }
    }

    val canCapture = state.lyrics.isNotEmpty() && !isPreviewMode
    val onVibrationToast: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val useLandscape = forceLandscapeEditor || (isLandscape && !isDesktop())

        SetImmersiveMode(useLandscape && !disableFullscreen)

        if (useLandscape) {
            val topBarHeight = 64.dp
            val topBarHeightPx = with(LocalDensity.current) { topBarHeight.toPx() }
            val totalWidthDp = maxWidth
            val overlayAnim = remember { Animatable(1f) }
            var overlayProgress by remember { mutableFloatStateOf(1f) }
            if (landscapeOverlay) {
                LaunchedEffect(overlayAnim) {
                    snapshotFlow { overlayAnim.value }
                        .collect { overlayProgress = it }
                }
            }
            val nestedScrollConnection = remember(landscapeOverlay) {
                if (landscapeOverlay) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                            val isNearTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < topBarHeightPx
                            val isMouseWheel = source == NestedScrollSource.UserInput
                            val scrollsAwayFromTop = if (isMouseWheel) available.y < 0f else available.y > 0f
                            val scrollsTowardTop = if (isMouseWheel) available.y > 0f else available.y < 0f
                            if (scrollsAwayFromTop && overlayProgress > 0.001f) {
                                val np = (overlayProgress - abs(available.y) / topBarHeightPx).coerceIn(0f, 1f)
                                scope.launch { overlayAnim.snapTo(np) }
                                return Offset(0f, available.y)
                            }
                            if (scrollsTowardTop && overlayProgress < 1f && isAtTop) {
                                val np = (overlayProgress + abs(available.y) / topBarHeightPx).coerceIn(0f, 1f)
                                scope.launch { overlayAnim.snapTo(np) }
                                return Offset(0f, available.y)
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                            if (atTop && overlayProgress < 1f) {
                                val isMouse = source == NestedScrollSource.UserInput
                                val towardTop = if (isMouse) consumed.y > 0f else consumed.y < 0f
                                if (towardTop) {
                                    scope.launch { overlayAnim.animateTo(1f, tween(durationMillis = 150)) }
                                }
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                            if (overlayProgress > 0f && overlayProgress < 1f) {
                                val target = if (atTop) 1f else if (overlayProgress > 0.5f) 1f else 0f
                                overlayAnim.animateTo(target, tween(durationMillis = 150))
                            }
                            if (overlayProgress <= 0f && atTop) {
                                overlayAnim.animateTo(1f, tween(durationMillis = 150))
                            }
                            return Velocity.Zero
                        }
                    }
                } else null
            }

            Box(modifier = Modifier
                .fillMaxSize()
                .then(if (!ignoreCutout) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier)
            ) {
                val lyricsSide = @Composable {
                    Column(modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lyrics (${state.lyrics.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(displayItems, key = { it.key }, contentType = { it::class }) { item ->
                                when (item) {
                                    is DisplayItem.Line -> {
                                        val i = item.index
                                        LyricLineCard(
                                            line = item.lrcLine,
                                            isCurrentLine = i == state.selectedLineIndex,
                                            isPlaybackLine = i == state.currentLineIndex && state.currentLineIndex >= 0,
                                            isEditing = state.editingLineIndex == i && !isPreviewMode,
                                            editingText = if (state.editingLineIndex == i) state.editingText else "",
                                            isPreviewMode = isPreviewMode,
                                            compactControls = compactControls,
                                            swipeDeleteThresholdDp = swipeDeleteThresholdDp,
                                            swipeActivationThresholdDp = swipeActivationThresholdDp,
                                            swipeGesturesEnabled = swipeGesturesEnabled,
                                            showSnapButton = showSnapButton,
                                            showClearDeleteButton = showClearDeleteButton,
                                            swipeInstantDelete = swipeInstantDelete,
                                            showDebugBorders = showDebugBorders,
                                            showVibrationToast = showVibrationToast,
                                            wordSyncMode = isEnhancedLrcEnabled && state.wordSyncMode && item.lrcLine.words.isNotEmpty(),
                                            wordCursorIndex = if (i == state.selectedLineIndex) state.wordCursorIndex else -1,
                                            currentWordIndex = if (i == state.currentLineIndex) state.currentWordIndex else -1,
                                            isPlaying = playerState.state == PlaybackState.PLAYING,
                                            currentPositionMs = playerState.currentPosition,

                                            onVibrationToast = onVibrationToast,
                                            onTimestampSet = { ms -> viewModel.setTimestamp(i, ms) },
                                            onEditStart = { viewModel.startEditing(i) },
                                            onEditChange = { viewModel.updateEditingText(it) },
                                            onEditDone = { viewModel.finishEditing() },
                                            onClearTimestamp = {
                                                if (state.wordSyncMode) viewModel.clearWordTimestamps(i)
                                                else viewModel.clearTimestamp(i)
                                            },
                                            onDelete = { showDeleteConfirm = i },
                                            onInstantDelete = { viewModel.deleteLine(i) },
                                            onClick = { viewModel.selectLine(i) },
                                            onSnapTimestamp = { viewModel.snapToCurrentPosition(i) },
                                            onTimestampMinus100 = { viewModel.shiftSingleTimestamp(i, -100L) },
                                            onTimestampPlus100 = { viewModel.shiftSingleTimestamp(i, 100L) },
                                            onWordClick = { wi ->
                                                if (wi in item.lrcLine.words.indices) {
                                                    viewModel.setWordCursor(i, wi)
                                                    viewModel.seekToWord(i, wi, beforeMs = 0L)
                                                }
                                            },
                                            onWordSeek = { wi ->
                                                viewModel.seekToWord(i, wi, beforeMs = 1500L)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    is DisplayItem.InsertAbove -> {
                                        if (!isPreviewMode) {
                                            InsertLineButton(
                                                text = "Insert line above",
                                                onClick = { viewModel.insertLineBefore(item.index) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    is DisplayItem.InsertBelow -> {
                                        if (!isPreviewMode) {
                                            InsertLineButton(
                                                text = "Insert line below",
                                                onClick = { viewModel.insertLineAfter(item.index) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.isRecording) {
                            RecordingBanner(
                                text = state.newLyricText,
                                onTextChange = { viewModel.updateNewLyricText(it) },
                                onCapture = { viewModel.captureTimestamp() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                val controlsOverlay = @Composable {
                    Column {
                        if (state.song?.audioPath.isNullOrEmpty()) {
                            ImportAudioButton(
                                onClick = onImportAudioFile,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        } else {
                            PlayerBar(
                                playerState = playerState,
                                onPlayPause = { viewModel.playPause() },
                                onSeek = { viewModel.seekTo(it) },
                                currentSpeed = speed,
                                onSpeedChange = {
                                    speed = it
                                    viewModel.audioPlayer.setSpeed(it)
                                },
                                onSpeedClick = { showSpeedDialog = true },
                                compactControls = compactControls,
                                speedBelowSeek = true,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        val toolbarScrollState = rememberScrollState()
                        val toolbarAtStart = toolbarScrollState.value <= 0
                        val toolbarAtEnd = toolbarScrollState.value >= toolbarScrollState.maxValue
                        val leftOpaqueAlpha by animateFloatAsState(
                            targetValue = if (toolbarAtStart) 1f else 0f,
                            animationSpec = tween(250)
                        )
                        val rightOpaqueAlpha by animateFloatAsState(
                            targetValue = if (toolbarAtEnd) 1f else 0f,
                            animationSpec = tween(250)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clipToBounds()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    val fadeWidth = 12.dp.toPx()
                                    val width = size.width
                                    if (width > 0f) {
                                        val ratio = fadeWidth / width
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to Color.Black.copy(alpha = leftOpaqueAlpha),
                                                ratio to Color.Black,
                                                (1f - ratio).coerceAtLeast(ratio) to Color.Black,
                                                1f to Color.Black.copy(alpha = rightOpaqueAlpha)
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(toolbarScrollState)
                                    .pointerInput(toolbarScrollState) {
                                        awaitEachGesture {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            if (event.type == PointerEventType.Scroll) {
                                                val delta = event.changes.firstOrNull()?.scrollDelta
                                                    ?: return@awaitEachGesture
                                                if (delta.y != 0f) {
                                                    toolbarScrollState.dispatchRawDelta(-delta.y * 10f)
                                                }
                                            }
                                        }
                                    },
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isPreviewMode) {
                                    FilledTonalIconButton(
                                        onClick = { showAddDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add manually")
                                    }
                                }

                                FilledTonalIconButton(
                                    onClick = { isPreviewMode = !isPreviewMode },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (isPreviewMode)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                        contentColor = if (isPreviewMode)
                                            MaterialTheme.colorScheme.onError
                                        else
                                            MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Icon(
                                        if (isPreviewMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isPreviewMode) "Preview off" else "Preview",
                                        tint = if (isPreviewMode)
                                            MaterialTheme.colorScheme.onError
                                        else
                                            MaterialTheme.colorScheme.secondary
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = { autoScrollEnabled = !autoScrollEnabled },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        if (autoScrollEnabled) Icons.Default.SwapVert else Icons.Default.Close,
                                        contentDescription = "Auto-scroll",
                                        tint = if (autoScrollEnabled)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline
                                    )
                                }

                                if (!isPreviewMode && isEnhancedLrcEnabled) {
                                    val wordSyncActive = state.wordSyncMode
                                    FilledTonalIconButton(
                                        onClick = { viewModel.toggleWordSyncMode(skipStandalonePunctuation) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = if (wordSyncActive)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            contentColor = if (wordSyncActive)
                                                MaterialTheme.colorScheme.onPrimary
                                            else
                                                MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.ShortText,
                                            contentDescription = "Word Sync",
                                        )
                                    }

                                }

                                if (!isPreviewMode) {
                                    FilledTonalIconButton(
                                        onClick = { showShiftDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(Icons.Default.Timer, contentDescription = "Batch shift")
                                    }

                                    FilledTonalIconButton(
                                        onClick = { showClearAllConfirm = true },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = "Clear all timestamps",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val timeOverlay = @Composable {
                    if (canCapture) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 24.dp)
                                .padding(horizontal = 16.dp)
                        ) {
                            val undoW = 48.dp + 8.dp + 48.dp
                            val rightEdge = maxWidth / 2 + 80.dp
                            val undoLeft = maxWidth - undoW - 8.dp
                            val shift = if (showUndoRedo && landscapeInverted && rightEdge > undoLeft)
                                rightEdge - undoLeft else 0.dp
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .graphicsLayer { translationX = -shift.toPx() }
                                    .width(160.dp)
                                    .height(56.dp)
                                    .clickable(
                                        interactionSource = timeInteractionSource,
                                        indication = null,
                                        onClick = {
                                            if (state.wordSyncMode && state.wordCursorIndex >= 0) {
                                                viewModel.captureWordTimestamp()
                                            } else {
                                                viewModel.captureCurrentLineTimestamp()
                                            }
                                        },
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(scaleX = timeScale)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Time",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            if (showUndoRedo) {
                                Row(
                                    modifier = Modifier.align(if (!landscapeInverted) Alignment.CenterStart else Alignment.CenterEnd),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val undoInteractionSource = remember { MutableInteractionSource() }
                                    val isUndoPressed by undoInteractionSource.collectIsPressedAsState()
                                    val undoScale by animateFloatAsState(
                                        targetValue = if (isUndoPressed) 52f / 48f else 1f,
                                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                                        label = "undoScale"
                                    )
                                    val redoInteractionSource = remember { MutableInteractionSource() }
                                    val isRedoPressed by redoInteractionSource.collectIsPressedAsState()
                                    val redoScale by animateFloatAsState(
                                        targetValue = if (isRedoPressed) 52f / 48f else 1f,
                                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                                        label = "redoScale"
                                    )
                                    val undoBgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    val undoIconColor = if (state.canUndo)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    val redoBgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    val redoIconColor = if (state.canRedo)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clickable(
                                                interactionSource = undoInteractionSource,
                                                indication = null,
                                                enabled = state.canUndo,
                                                onClick = { viewModel.undo() },
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer(
                                                    scaleX = undoScale,
                                                    scaleY = undoScale
                                                )
                                                .clip(CircleShape)
                                                .background(undoBgColor)
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = "Undo",
                                            tint = undoIconColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clickable(
                                                interactionSource = redoInteractionSource,
                                                indication = null,
                                                enabled = state.canRedo,
                                                onClick = { viewModel.redo() },
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer(
                                                    scaleX = redoScale,
                                                    scaleY = redoScale
                                                )
                                                .clip(CircleShape)
                                                .background(redoBgColor)
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Filled.Redo,
                                            contentDescription = "Redo",
                                            tint = redoIconColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val topBarContent = @Composable {
                    TopAppBar(
                        title = {
                            Text(
                                text = state.song?.title ?: "Editor",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = onImportAudioFile) {
                                Icon(Icons.Default.LibraryMusic, contentDescription = "Switch track")
                            }
                            IconButton(onClick = { showSaveDialog = true }) {
                                Icon(Icons.Default.Save, contentDescription = "Save LRC")
                            }
                        }
                    )
                }

                val controlsSide = @Composable {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.alpha(if (landscapeOverlay) 1f - overlayProgress else 1f)) {
                                topBarContent()
                            }
                            controlsOverlay()
                            Spacer(modifier = Modifier.weight(1f))
                            timeOverlay()
                        }
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                }

                val lyricsWeight = landscapeSplitRatio
                val controlsWeight = 1f - landscapeSplitRatio

                val lyricsSideWrapper = @Composable {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (landscapeOverlay) {
                            Spacer(modifier = Modifier.height(topBarHeight * overlayProgress))
                        }
                        lyricsSide()
                    }
                }

                Row(modifier = Modifier.fillMaxSize().then(
                    nestedScrollConnection?.let { Modifier.nestedScroll(it) } ?: Modifier
                )) {
                    if (!landscapeInverted) {
                        Box(modifier = Modifier.weight(controlsWeight)) { controlsSide() }
                        Box(modifier = Modifier.weight(lyricsWeight)) { lyricsSideWrapper() }
                    } else {
                        Box(modifier = Modifier.weight(lyricsWeight)) { lyricsSideWrapper() }
                        Box(modifier = Modifier.weight(controlsWeight)) { controlsSide() }
                    }
                }

                if (landscapeOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .alpha(overlayProgress)
                    ) {
                        topBarContent()
                    }
                }
            }
        }
    else {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = state.song?.title ?: "Editor",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onImportAudioFile) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = "Switch track")
                        }
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save LRC")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.song?.audioPath.isNullOrEmpty()) {
                        ImportAudioButton(
                            onClick = onImportAudioFile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        PlayerBar(
                            playerState = playerState,
                            onPlayPause = { viewModel.playPause() },
                            onSeek = { viewModel.seekTo(it) },
                            currentSpeed = speed,
                            onSpeedChange = {
                                speed = it
                                viewModel.audioPlayer.setSpeed(it)
                            },
                            onSpeedClick = { showSpeedDialog = true },
                            compactControls = compactControls,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Lyrics (${state.lyrics.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val toolbarScrollState = rememberScrollState()
                        val toolbarAtStart = toolbarScrollState.value <= 0
                        val toolbarAtEnd = toolbarScrollState.value >= toolbarScrollState.maxValue
                        val leftOpaqueAlpha by animateFloatAsState(
                            targetValue = if (toolbarAtStart) 1f else 0f,
                            animationSpec = tween(250)
                        )
                        val rightOpaqueAlpha by animateFloatAsState(
                            targetValue = if (toolbarAtEnd) 1f else 0f,
                            animationSpec = tween(250)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clipToBounds()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    val fadeWidth = 12.dp.toPx()
                                    val width = size.width
                                    if (width > 0f) {
                                        val ratio = fadeWidth / width
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to Color.Black.copy(alpha = leftOpaqueAlpha),
                                                ratio to Color.Black,
                                                (1f - ratio).coerceAtLeast(ratio) to Color.Black,
                                                1f to Color.Black.copy(alpha = rightOpaqueAlpha)
                                            ),
                                            blendMode = BlendMode.DstIn
                                        )
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(toolbarScrollState)
                                    .pointerInput(toolbarScrollState) {
                                        awaitEachGesture {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            if (event.type == PointerEventType.Scroll) {
                                                val delta = event.changes.firstOrNull()?.scrollDelta
                                                    ?: return@awaitEachGesture
                                                if (delta.y != 0f) {
                                                    toolbarScrollState.dispatchRawDelta(-delta.y * 10f)
                                                }
                                            }
                                        }
                                    },
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                if (!isPreviewMode) {
                                FilledTonalIconButton(
                                    onClick = { showAddDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add manually")
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { isPreviewMode = !isPreviewMode },
                                shape = RoundedCornerShape(12.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isPreviewMode)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    contentColor = if (isPreviewMode)
                                        MaterialTheme.colorScheme.onError
                                    else
                                        MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(
                                    if (isPreviewMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPreviewMode) "Preview off" else "Preview",
                                    tint = if (isPreviewMode)
                                        MaterialTheme.colorScheme.onError
                                    else
                                        MaterialTheme.colorScheme.secondary
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { autoScrollEnabled = !autoScrollEnabled },
                                shape = RoundedCornerShape(12.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    if (autoScrollEnabled) Icons.Default.SwapVert else Icons.Default.Close,
                                    contentDescription = "Auto-scroll",
                                    tint = if (autoScrollEnabled)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline
                                )
                            }

                            if (!isPreviewMode && isEnhancedLrcEnabled) {
                                val wordSyncActive = state.wordSyncMode
                                FilledTonalIconButton(
                                    onClick = { viewModel.toggleWordSyncMode(skipStandalonePunctuation) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (wordSyncActive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        contentColor = if (wordSyncActive)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.ShortText,
                                        contentDescription = "Word Sync",
                                    )
                                }
                            }

                            if (!isPreviewMode) {
                                FilledTonalIconButton(
                                    onClick = { showShiftDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.Timer, contentDescription = "Batch shift")
                                }

                                FilledTonalIconButton(
                                    onClick = { showClearAllConfirm = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "Clear all timestamps",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(displayItems, key = { it.key }, contentType = { it::class }) { item ->
                            when (item) {
                                is DisplayItem.Line -> {
                                    val i = item.index
                                    LyricLineCard(
                                        line = item.lrcLine,
                                        isCurrentLine = i == state.selectedLineIndex,
                                        isPlaybackLine = i == state.currentLineIndex && state.currentLineIndex >= 0,
                                        isEditing = state.editingLineIndex == i && !isPreviewMode,
                                        editingText = if (state.editingLineIndex == i) state.editingText else "",
                                        isPreviewMode = isPreviewMode,
                                        compactControls = compactControls,
                                        swipeDeleteThresholdDp = swipeDeleteThresholdDp,
                                        swipeActivationThresholdDp = swipeActivationThresholdDp,
                                        swipeGesturesEnabled = swipeGesturesEnabled,
                                        showSnapButton = showSnapButton,
                                        showClearDeleteButton = showClearDeleteButton,
                                        swipeInstantDelete = swipeInstantDelete,
                                        showDebugBorders = showDebugBorders,
                                        showVibrationToast = showVibrationToast,
                                        wordSyncMode = isEnhancedLrcEnabled && state.wordSyncMode && item.lrcLine.words.isNotEmpty(),
                                        wordCursorIndex = if (i == state.selectedLineIndex) state.wordCursorIndex else -1,
                                        currentWordIndex = if (i == state.currentLineIndex) state.currentWordIndex else -1,
                                        isPlaying = playerState.state == PlaybackState.PLAYING,
                                        currentPositionMs = playerState.currentPosition,

                                        onVibrationToast = onVibrationToast,
                                        onTimestampSet = { ms -> viewModel.setTimestamp(i, ms) },
                                        onEditStart = { viewModel.startEditing(i) },
                                        onEditChange = { viewModel.updateEditingText(it) },
                                        onEditDone = { viewModel.finishEditing() },
                                        onClearTimestamp = {
                                            if (state.wordSyncMode) viewModel.clearWordTimestamps(i)
                                            else viewModel.clearTimestamp(i)
                                        },
                                        onDelete = { showDeleteConfirm = i },
                                        onInstantDelete = { viewModel.deleteLine(i) },
                                        onClick = { viewModel.selectLine(i) },
                                        onSnapTimestamp = { viewModel.snapToCurrentPosition(i) },
                                        onTimestampMinus100 = { viewModel.shiftSingleTimestamp(i, -100L) },
                                        onTimestampPlus100 = { viewModel.shiftSingleTimestamp(i, 100L) },
                                        onWordClick = { wi ->
                                            if (wi in item.lrcLine.words.indices) {
                                                viewModel.setWordCursor(i, wi)
                                                viewModel.seekToWord(i, wi, beforeMs = 0L)
                                            }
                                        },
                                        onWordSeek = { wi ->
                                            viewModel.seekToWord(i, wi, beforeMs = 1500L)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is DisplayItem.InsertAbove -> {
                                    if (!isPreviewMode) {
                                        InsertLineButton(
                                            text = "Insert line above",
                                            onClick = { viewModel.insertLineBefore(item.index) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                is DisplayItem.InsertBelow -> {
                                    if (!isPreviewMode) {
                                        InsertLineButton(
                                            text = "Insert line below",
                                            onClick = { viewModel.insertLineAfter(item.index) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.isRecording) {
                        RecordingBanner(
                            text = state.newLyricText,
                            onTextChange = { viewModel.updateNewLyricText(it) },
                            onCapture = { viewModel.captureTimestamp() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                val bottomGradientColor = MaterialTheme.colorScheme.surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.BottomCenter)
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, bottomGradientColor)
                                )
                            )
                        }
                )

                if (canCapture) {
                    val undoInteractionSource = remember { MutableInteractionSource() }
                    val isUndoPressed by undoInteractionSource.collectIsPressedAsState()
                    val undoScale by animateFloatAsState(
                        targetValue = if (isUndoPressed) 52f / 48f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                        label = "undoScale"
                    )
                    val redoInteractionSource = remember { MutableInteractionSource() }
                    val isRedoPressed by redoInteractionSource.collectIsPressedAsState()
                    val redoScale by animateFloatAsState(
                        targetValue = if (isRedoPressed) 52f / 48f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                        label = "redoScale"
                    )
                    val undoBgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    val undoIconColor = if (state.canUndo)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    val redoBgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    val redoIconColor = if (state.canRedo)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        val undoW = 48.dp + 8.dp + 48.dp
                        val rightEdge = maxWidth / 2 + 80.dp
                        val undoLeft = maxWidth - undoW - 8.dp
                        val shift = if (showUndoRedo && rightEdge > undoLeft)
                            rightEdge - undoLeft else 0.dp
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .graphicsLayer { translationX = -shift.toPx() }
                                .width(160.dp)
                                .height(56.dp)
                                .clickable(
                                    interactionSource = timeInteractionSource,
                                    indication = null,
                                    onClick = { viewModel.captureCurrentLineTimestamp() },
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(scaleX = timeScale)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            ) {
                                Icon(
                                    Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Time",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        if (showUndoRedo) {
                            Row(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable(
                                        interactionSource = undoInteractionSource,
                                        indication = null,
                                        enabled = state.canUndo,
                                        onClick = { viewModel.undo() },
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = undoScale,
                                            scaleY = undoScale
                                        )
                                        .clip(CircleShape)
                                        .background(undoBgColor)
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = undoIconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable(
                                        interactionSource = redoInteractionSource,
                                        indication = null,
                                        enabled = state.canRedo,
                                        onClick = { viewModel.redo() },
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = redoScale,
                                            scaleY = redoScale
                                        )
                                        .clip(CircleShape)
                                        .background(redoBgColor)
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = redoIconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showShiftDialog) {
        ShiftTimestampsDialog(
            onConfirm = { offsetMs ->
                viewModel.shiftAllTimestamps(offsetMs)
                showShiftDialog = false
            },
            onDismiss = { showShiftDialog = false }
        )
    }

    if (showAddDialog) {
        AddLineDialog(
            currentPosition = playerState.currentPosition,
            onConfirm = { text ->
                viewModel.addLineAtPosition(text)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    showDeleteConfirm?.let { index ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete this line?") },
            text = { Text(state.lyrics.getOrNull(index)?.text ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLine(index)
                    showDeleteConfirm = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearAllConfirm) {
        val clearingWords = state.wordSyncMode
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(if (clearingWords) "Clear all word timestamps?" else "Clear all timestamps?") },
            text = {
                Text(
                    if (clearingWords) "This will remove all word-level timestamps from every line."
                    else "This will remove all timestamps from every line."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (clearingWords) viewModel.clearAllWordTimestamps()
                    else viewModel.clearAllTimestamps()
                    showClearAllConfirm = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSaveDialog) {
        val song = state.song
        val clipboardManager = LocalClipboardManager.current
        val isEnhanced = state.wordSyncMode
        SaveLrcDialog(
            isEnhanced = isEnhanced,
            initialTitle = song?.title ?: "",
            initialArtist = song?.artist ?: "",
            initialAlbum = song?.album ?: "",
            initialComposer = song?.composer ?: "",
            initialCreator = song?.creator ?: "",
            onConfirm = { title, artist, album, composer, creator ->
                showSaveDialog = false
                if (state.lyrics.isNotEmpty()) {
                    val lyricsForExport = if (isEnhanced) state.lyrics
                        else state.lyrics.map { it.copy(words = emptyList()) }
                    val lrc = LrcParser.generate(
                        lyrics = lyricsForExport,
                        title = title,
                        artist = artist,
                        album = album,
                        composer = composer,
                        creator = creator
                    )
                    val fileName = "${title.ifBlank { state.song?.title ?: "lyrics" }}.lrc"
                    if (lrcSaveDirectory != null && checkFileExists(fileName)) {
                        pendingOverwriteContent = lrc
                        pendingOverwriteFileName = fileName
                        showOverwriteConfirm = true
                    } else {
                        saveLrcFile(lrc)
                    }
                }
            },
            onCopyPlain = {
                if (state.lyrics.isNotEmpty()) {
                    val plain = LrcParser.generatePlain(
                        if (isEnhanced) state.lyrics
                        else state.lyrics.map { it.copy(words = emptyList()) }
                    )
                    clipboardManager.setText(AnnotatedString(plain))
                }
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showSpeedDialog) {
        var input by remember { mutableStateOf("%.2f".format(speed)) }
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("Playback Speed", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Speed (0.25 – 3.0)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newSpeed = input.toFloatOrNull()
                        if (newSpeed != null && newSpeed in 0.25f..3.0f) {
                            speed = newSpeed
                            viewModel.audioPlayer.setSpeed(newSpeed)
                            showSpeedDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = input.toFloatOrNull()?.let { it in 0.25f..3.0f } == true
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("File exists", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text("\"$pendingOverwriteFileName\" already exists. Overwrite?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingOverwriteContent?.let { saveLrcFile(it) }
                        showOverwriteConfirm = false
                        pendingOverwriteContent = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverwriteConfirm = false
                    pendingOverwriteContent = null
                }) { Text("Cancel") }
            }
        )
    }
}

private const val LYRICS_ANCHOR_RATIO = 0.35f

private suspend fun centerItem(index: Int, listState: LazyListState) {
    val layoutInfo = listState.layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val anchorY = (viewportHeight * LYRICS_ANCHOR_RATIO).toInt()
    val targetOffset: Int
    val currentOffset: Int
    if (itemInfo != null) {
        targetOffset = (anchorY - itemInfo.size / 2).coerceAtLeast(0)
        currentOffset = itemInfo.offset
    } else {
        listState.scrollToItem(index)
        val fresh = listState.layoutInfo
        val freshItem = fresh.visibleItemsInfo.find { it.index == index } ?: return
        targetOffset = (anchorY - freshItem.size / 2).coerceAtLeast(0)
        currentOffset = freshItem.offset
    }
    val delta = targetOffset - currentOffset
    if (delta != 0) {
        val startTimeMs = withFrameMillis { it }
        var lastScrolled = 0f
        while (true) {
            val nowMs = withFrameMillis { it }
            val elapsed = nowMs - startTimeMs
            val fraction = (elapsed.toFloat() / 250f).coerceAtMost(1f)
            val eased = FastOutSlowInEasing.transform(fraction)
            val targetScroll = eased * delta.toFloat()
            val step = targetScroll - lastScrolled
            if (abs(step) > 0.5f) {
                listState.scroll { scrollBy(-step) }
            }
            lastScrolled = targetScroll
            if (fraction >= 1f) break
        }
    }
}

private sealed class DisplayItem {
    abstract val key: String
    data class Line(val lrcLine: com.lrcstudio.app.data.model.LrcLine, val index: Int) : DisplayItem() {
        override val key get() = "line_$index"
    }
    data class InsertAbove(val index: Int) : DisplayItem() {
        override val key get() = "insert_above_$index"
    }
    data class InsertBelow(val index: Int) : DisplayItem() {
        override val key get() = "insert_below_$index"
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun LyricLineCard(
    line: com.lrcstudio.app.data.model.LrcLine,
    isCurrentLine: Boolean,
    isPlaybackLine: Boolean,
    isEditing: Boolean,
    editingText: String,
    isPreviewMode: Boolean = false,
    compactControls: Boolean = false,
    swipeDeleteThresholdDp: Int = 130,
    swipeActivationThresholdDp: Int = 20,
    swipeGesturesEnabled: Boolean = true,
    showSnapButton: Boolean = false,
    showClearDeleteButton: Boolean = false,
    swipeInstantDelete: Boolean = false,
    showDebugBorders: Boolean = false,
    showVibrationToast: Boolean = false,
    wordSyncMode: Boolean = false,
    wordCursorIndex: Int = -1,
    currentWordIndex: Int = -1,
    isPlaying: Boolean = false,
    currentPositionMs: Long = 0L,

    onVibrationToast: (String) -> Unit = {},
    onTimestampSet: (Long) -> Unit,
    onEditStart: () -> Unit,
    onEditChange: (String) -> Unit,
    onEditDone: () -> Unit,
    onClearTimestamp: () -> Unit,
    onDelete: () -> Unit,
    onInstantDelete: () -> Unit,
    onClick: () -> Unit,
    onSnapTimestamp: () -> Unit,
    onTimestampMinus100: () -> Unit,
    onTimestampPlus100: () -> Unit,
    onWordClick: ((Int) -> Unit)? = null,
    onWordSeek: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hasTimestamp = if (wordSyncMode) {
        val punctRegex = Regex("[.,!?;:\\-–—()\\[\\]{}「」『』《》【】\"'«»…]+")
        line.words.isNotEmpty() && line.words.all { word ->
            punctRegex.matches(word.text) || word.startTime > 0L
        }
    } else {
        line.timestamp > 0L
    }
    val indicatorColor = if (hasTimestamp) Color(0xFFA5D6A7) else Color.Transparent

    val flashAnim = remember { Animatable(0f) }
    var showTimestampDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeEnabled = !isPreviewMode && !isEditing && swipeGesturesEnabled
    val offsetAnimatable = remember { Animatable(0f) }
    var itemWidthPx by remember { mutableStateOf(0f) }
    var surfaceHeightPx by remember { mutableStateOf(0f) }
    var accumulatedDrag by remember { mutableStateOf(0f) }

    val currentOffsetPx = offsetAnimatable.value
    val revealRightPx = currentOffsetPx.coerceAtLeast(0f)
    val revealLeftPx = (-currentOffsetPx).coerceAtLeast(0f)
    val actionThresholdPx = with(density) { swipeActivationThresholdDp.dp.toPx() }
    val deleteOffsetPx = with(density) { swipeDeleteThresholdDp.dp.toPx() }
    val totalDeleteThresholdPx = actionThresholdPx + deleteOffsetPx
    val isInActionZone = itemWidthPx > 0f && abs(currentOffsetPx) >= actionThresholdPx
    val isLongSwipe = currentOffsetPx > 0f && abs(currentOffsetPx) >= totalDeleteThresholdPx
    val dismissThresholdPx = itemWidthPx * 0.40f
    val isInDismissZone = abs(accumulatedDrag) > dismissThresholdPx
    val rightColorFraction = if (totalDeleteThresholdPx > actionThresholdPx) {
        val t = ((abs(currentOffsetPx) - actionThresholdPx) / (totalDeleteThresholdPx - actionThresholdPx)).coerceIn(0f, 1f)
        t * t * t
    } else 0f
    val rightBgColor = lerp(Color(0xFFF9A825), Color(0xFFE53935), rightColorFraction)
    val swipeGapDp = 4.dp
    val haptic = LocalHapticFeedback.current
    val textScale = remember { Animatable(1f) }
    fun scopeLaunchBounce(target: Float = 1.2f) {
        scope.launch {
            textScale.snapTo(target)
            textScale.animateTo(1f, spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium / 9f
            ))
        }
    }

    // haptic when entering the action zone (Clear)
    var wasInActionZone by remember { mutableStateOf(false) }
    if (isInActionZone && !wasInActionZone) {
        wasInActionZone = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (showVibrationToast) {
            scope.launch { onVibrationToast("Swipe: action zone") }
        }
        scopeLaunchBounce()
    } else if (!isInActionZone) {
        wasInActionZone = false
    }

    LaunchedEffect(isLongSwipe) {
        if (isLongSwipe) {
            textScale.snapTo(1.3f)
            textScale.animateTo(1f, spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium / 9f
            ))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (showVibrationToast) {
                onVibrationToast("Swipe: delete zone")
            }
        }
    }

    val containerColor = if (isCurrentLine)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    LaunchedEffect(isPlaybackLine, wordSyncMode) {
        if (wordSyncMode && line.words.isNotEmpty()) {
            flashAnim.snapTo(0f)
        } else if (isPlaybackLine) {
            flashAnim.snapTo(1f)
            flashAnim.animateTo(0f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        } else {
            flashAnim.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val w = coords.size.width.toFloat()
                if (w != itemWidthPx) itemWidthPx = w
            }
    ) {
        if (revealRightPx > 0f && surfaceHeightPx > 0f) {
            val wDp = (with(density) { revealRightPx.toDp() } - swipeGapDp).coerceAtLeast(0.dp)
            val hDp = with(density) { surfaceHeightPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(wDp)
                    .height(hDp)
                    .clip(CircleShape)
                    .background(rightBgColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isLongSwipe) "Delete" else "Clear",
                        color = Color.White,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.graphicsLayer(scaleX = textScale.value, scaleY = textScale.value)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(24.dp)
                        .drawBehind {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, rightBgColor),
                                    endX = size.width
                                )
                            )
                        }
                        .then(if (showDebugBorders) Modifier.border(1.dp, Color.White) else Modifier)
                )
            }
        }

        if (revealLeftPx > 0f && surfaceHeightPx > 0f) {
            val wDp = (with(density) { revealLeftPx.toDp() } - swipeGapDp).coerceAtLeast(0.dp)
            val hDp = with(density) { surfaceHeightPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(wDp)
                    .height(hDp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Time",
                        color = MaterialTheme.colorScheme.onPrimary,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.graphicsLayer(scaleX = textScale.value, scaleY = textScale.value)
                    )
                }
                val primaryColor = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(24.dp)
                        .drawBehind {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, primaryColor),
                                    endX = size.width
                                )
                            )
                        }
                        .then(if (showDebugBorders) Modifier.border(1.dp, Color.White) else Modifier)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset(
                    x = with(density) { currentOffsetPx.toDp() }
                )
                .onGloballyPositioned { coords ->
                    val h = coords.size.height.toFloat()
                    if (h != surfaceHeightPx) surfaceHeightPx = h
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (isPreviewMode) null else onEditStart
                )
                .then(
                    if (swipeEnabled) Modifier.pointerInput(offsetAnimatable, itemWidthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                scope.launch { offsetAnimatable.stop() }
                                accumulatedDrag = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDrag += dragAmount
                                val absDrag = abs(accumulatedDrag)
                                val tensionThresholdPx = 60f * density.density

                                val effectiveOffset = if (absDrag < tensionThresholdPx) {
                                    val t = absDrag / tensionThresholdPx
                                    tensionThresholdPx * t * t
                                } else {
                                    absDrag
                                }
                                val signed = if (accumulatedDrag > 0f) effectiveOffset else -effectiveOffset
                                scope.launch { offsetAnimatable.snapTo(signed) }
                            },
                            onDragEnd = {
                                if (abs(accumulatedDrag) > actionThresholdPx) {
                                    if (accumulatedDrag > 0f) {
                                        if (abs(accumulatedDrag) >= totalDeleteThresholdPx) {
                                            if (swipeInstantDelete) onInstantDelete() else onDelete()
                                        } else onClearTimestamp()
                                    } else {
                                        onSnapTimestamp()
                                    }
                                }
                                accumulatedDrag = 0f
                                scope.launch {
                                    offsetAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                accumulatedDrag = 0f
                                scope.launch {
                                    offsetAnimatable.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        )
                    } else Modifier
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(indicatorColor)
                )
                Spacer(modifier = Modifier.width(8.dp))

                if (!isPreviewMode && !wordSyncMode) {
                    if (compactControls) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = line.timestampFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                                    .clickable { showTimestampDialog = true }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = onTimestampMinus100,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "-100ms",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = onTimestampPlus100,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "+100ms",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            IconButton(
                                onClick = onTimestampMinus100,
                                modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-100ms",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .wrapContentWidth()
                                .clickable { showTimestampDialog = true }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = line.timestampFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onTimestampPlus100,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+100ms",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (isEditing) {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = onEditChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onEditDone, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Done",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (wordSyncMode && line.words.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val punctRegex = remember { Regex("[.,!?;:\\-–—()\\[\\]{}「」『』《》【】\"'«»…]+") }
                        line.words.forEachIndexed { wi, word ->
                            val isPunct = punctRegex.matches(word.text)
                            val isWordCurrent = if (!isPunct) {
                                wi == currentWordIndex
                            } else {
                                currentWordIndex >= 0 && run {
                                    var prevTimedIdx = -1
                                    for (j in wi - 1 downTo 0) {
                                        val w = line.words[j]
                                        if (!punctRegex.matches(w.text) && w.startTime > 0L) {
                                            prevTimedIdx = j; break
                                        }
                                    }
                                    prevTimedIdx == currentWordIndex
                                }
                            }
                            val isWordCursor = wi == wordCursorIndex
                            val hasWordTime = word.startTime > 0L
                            val wordBg = when {
                                isPunct -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                isWordCursor -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                hasWordTime -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else -> Color.Transparent
                            }
                            val wordBorder = if (isWordCursor && !isPunct)
                                MaterialTheme.colorScheme.primary
                            else
                                Color.Transparent
                            val wordTextColor = when {
                                isPunct -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                hasWordTime -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            val animDuration = {
                                if (hasWordTime) {
                                    var next = word.startTime + 500L
                                    var n = wi + 1
                                    while (n < line.words.size) {
                                        if (!punctRegex.matches(line.words[n].text) && line.words[n].startTime > 0L) {
                                            next = line.words[n].startTime
                                            break
                                        }
                                        n++
                                    }
                                    (next - word.startTime).coerceAtLeast(30L)
                                } else if (isPunct) {
                                    var prevTime = word.startTime
                                    for (j in wi - 1 downTo 0) {
                                        if (!punctRegex.matches(line.words[j].text) && line.words[j].startTime > 0L) { prevTime = line.words[j].startTime; break }
                                    }
                                    var nextTime = prevTime + 500L
                                    for (j in wi + 1 until line.words.size) {
                                        if (!punctRegex.matches(line.words[j].text) && line.words[j].startTime > 0L) { nextTime = line.words[j].startTime; break }
                                    }
                                    (nextTime - prevTime).coerceAtLeast(30L)
                                } else 0L
                            }()
                            val canAnimate = if (isPunct) false
                            else hasWordTime && animDuration > 0L
                            val wordProgress = remember { Animatable(0f) }
                            val fillAlpha = remember { Animatable(0f) }
                            LaunchedEffect(isWordCurrent, word.startTime, isPlaying) {
                                if (isWordCurrent && canAnimate && isPlaying) {
                                    fillAlpha.snapTo(1f)
                                    val elapsed = (currentPositionMs - word.startTime).coerceAtLeast(0L)
                                    val initialProgress = if (animDuration > 0L) (elapsed.toFloat() / animDuration).coerceIn(0f, 1f) else 0f
                                    wordProgress.snapTo(initialProgress)
                                    if (initialProgress < 1f) {
                                        val remainingMs = (animDuration * (1f - initialProgress)).toInt().coerceAtLeast(50)
                                        wordProgress.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(remainingMs, easing = LinearEasing)
                                        )
                                    }
                                    fillAlpha.animateTo(0f, tween(500))
                                } else if (!isPlaying) {
                                    fillAlpha.snapTo(0f)
                                    wordProgress.snapTo(0f)
                                } else if (isWordCurrent && wordProgress.value > 0f && wordProgress.value < 0.99f) {
                                    val remainingMs = (animDuration * (1f - wordProgress.value)).toInt().coerceAtLeast(50)
                                    coroutineScope {
                                        launch {
                                            wordProgress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = tween(remainingMs, easing = LinearEasing)
                                            )
                                        }
                                        fillAlpha.animateTo(0f, tween(500))
                                    }
                                    wordProgress.snapTo(0f)
                                } else if (fillAlpha.value > 0f) {
                                    fillAlpha.animateTo(0f, tween(500))
                                    wordProgress.snapTo(0f)
                                } else {
                                    wordProgress.snapTo(0f)
                                    fillAlpha.snapTo(0f)
                                }
                            }
                            val karaokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp, bottom = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(wordBg)
                                    .drawBehind {
                                        val displayAlpha = fillAlpha.value.coerceAtLeast(0f)
                                        if (wordProgress.value > 0f && displayAlpha > 0f) {
                                            val fillWidth = size.width * wordProgress.value
                                            val gradientWidthPx = 60.dp.toPx()
                                            val gradientStartX = (fillWidth - gradientWidthPx).coerceAtLeast(0f)
                                            val overflowRight = size.width * 2f
                                            clipRect(right = fillWidth) {
                                                drawRect(
                                                    brush = Brush.horizontalGradient(
                                                        colorStops = arrayOf(
                                                            0.0f to karaokeColor.copy(alpha = 0.2f * displayAlpha),
                                                            gradientStartX / (fillWidth + overflowRight) to karaokeColor.copy(alpha = 0.2f * displayAlpha),
                                                            (gradientStartX + gradientWidthPx * 0.5f) / (fillWidth + overflowRight) to karaokeColor.copy(alpha = 0.5f * displayAlpha),
                                                            1.0f to karaokeColor.copy(alpha = 0.0f)
                                                        ),
                                                        startX = 0f,
                                                        endX = fillWidth + overflowRight
                                                    ),
                                                    size = Size(fillWidth + overflowRight, size.height)
                                                )
                                            }
                                        }
                                    }
                                    .border(
                                        width = if (isWordCursor && !isPunct) 1.5.dp else 0.dp,
                                        color = wordBorder,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .then(
                                        if (!isPunct) Modifier.pointerInput(wi) {
                                            detectTapGestures(
                                                onTap = { onWordClick?.invoke(wi) },
                                                onLongPress = { onWordSeek?.invoke(wi) }
                                            )
                                        } else Modifier
                                    )
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = word.text,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = wordTextColor
                                )
                            }
                        }
                    }

                    if (!isPreviewMode && showClearDeleteButton) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .combinedClickable(
                                    onClick = onClearTimestamp,
                                    onLongClick = onDelete
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear word timestamps",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    Text(
                        text = line.text.ifEmpty { "..." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (line.text.isEmpty())
                            MaterialTheme.colorScheme.outline
                        else
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (!isPreviewMode) {
                        if (showSnapButton) {
                            IconButton(
                                onClick = onSnapTimestamp,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.TouchApp,
                                    contentDescription = "Snap to current",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (showClearDeleteButton) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .combinedClickable(
                                        onClick = onClearTimestamp,
                                        onLongClick = onDelete
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear timestamp",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            if ((!wordSyncMode || line.words.isEmpty()) && flashAnim.value > 0.001f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = flashAnim.value * 0.25f)
                        )
                )
            }
        }
    }
    }

    if (showTimestampDialog) {
        var input by remember { mutableStateOf(line.timestampFormatted) }
        AlertDialog(
            onDismissRequest = { showTimestampDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("Edit Timestamp", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("MM:SS:ff") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ms = parseTimestampMs(input)
                        if (ms != null && ms >= 0L) {
                            onTimestampSet(ms)
                            showTimestampDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = parseTimestampMs(input) != null
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimestampDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun parseTimestampMs(input: String): Long? {
    val regex = Regex("""(\d+):(\d{2}):(\d{2})""")
    val match = regex.matchEntire(input.trim()) ?: return null
    val minutes = match.groupValues[1].toLongOrNull() ?: return null
    val seconds = match.groupValues[2].toLongOrNull() ?: return null
    val hundredths = match.groupValues[3].toLongOrNull() ?: return null
    if (seconds !in 0..59 || hundredths !in 0..99) return null
    return minutes * 60000 + seconds * 1000 + hundredths * 10
}

@Composable
private fun InsertLineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun RecordingBanner(
    text: String,
    onTextChange: (String) -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type lyric text and tap capture") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.errorContainer
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = onCapture,
                enabled = text.isNotBlank(),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    Icons.Default.FlashOn,
                    contentDescription = "Capture",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

@Composable
private fun ShiftTimestampsDialog(
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var offsetText by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shift All Timestamps") },
        text = {
            Column {
                Text(
                    "Enter offset in milliseconds (negative to shift earlier):",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = offsetText,
                    onValueChange = { offsetText = it },
                    label = { Text("Offset (ms)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val offset = offsetText.toLongOrNull() ?: 0L
                onConfirm(offset)
            }) {
                Text("Shift")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddLineDialog(
    currentPosition: Long,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val positionFormatted = remember {
        val totalMs = currentPosition
        val minutes = (totalMs / 60000).toInt()
        val seconds = ((totalMs % 60000) / 1000).toInt()
        val hundredths = ((totalMs % 1000) / 10).toInt()
        val minStr = minutes.toString().padStart(2, '0')
        val secStr = seconds.toString().padStart(2, '0')
        val fracStr = hundredths.toString().padStart(2, '0')
        "$minStr:$secStr.$fracStr"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Line @ $positionFormatted") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Lyric text") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SaveLrcDialog(
    isEnhanced: Boolean,
    initialTitle: String,
    initialArtist: String,
    initialAlbum: String,
    initialComposer: String,
    initialCreator: String,
    onConfirm: (title: String, artist: String, album: String, composer: String, creator: String) -> Unit,
    onCopyPlain: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }
    var album by remember { mutableStateOf(initialAlbum) }
    var composer by remember { mutableStateOf(initialComposer) }
    var creator by remember { mutableStateOf(initialCreator) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (isEnhanced) "Save Enhanced LRC" else "Save LRC", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Song name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = composer,
                    onValueChange = { composer = it },
                    label = { Text("Composer") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = creator,
                    onValueChange = { creator = it },
                    label = { Text("LRC creator") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopyPlain) {
                    Text("Copy plain LRC")
                }
                Button(
                    onClick = { onConfirm(title.trim(), artist.trim(), album.trim(), composer.trim(), creator.trim()) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ImportAudioButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Import music file",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
