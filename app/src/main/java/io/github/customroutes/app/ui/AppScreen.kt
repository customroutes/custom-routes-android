package io.github.customroutes.app.ui

import android.graphics.Bitmap
import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.customroutes.app.R
import io.github.customroutes.app.data.ProjectSummary
import io.github.customroutes.app.data.ProjectThumbnailUiState
import io.github.customroutes.app.data.ThumbnailSignature
import io.github.customroutes.app.data.toAlphaBitmap
import io.github.customroutes.app.domain.AppearanceSettings
import io.github.customroutes.app.domain.BORDER_WIDTH_STEP_PERCENT
import io.github.customroutes.app.domain.MAX_BORDER_WIDTH_PERCENT
import io.github.customroutes.app.domain.MAX_EXPORT_DIMMING_PERCENT
import io.github.customroutes.app.domain.MIN_BORDER_WIDTH_PERCENT
import io.github.customroutes.app.domain.MIN_EXPORT_DIMMING_PERCENT
import io.github.customroutes.app.domain.EXPORT_DIMMING_STEP_PERCENT
import io.github.customroutes.app.domain.HoldRole
import io.github.customroutes.app.domain.ManualHoldDraft
import io.github.customroutes.app.domain.MaskRaster
import io.github.customroutes.app.domain.RouteProject
import io.github.customroutes.app.domain.ROLE_COLOR_CHOICES
import io.github.customroutes.app.domain.SourcePoint
import io.github.customroutes.app.domain.SourceRect
import io.github.customroutes.app.domain.adaptiveBorderBands
import io.github.customroutes.app.domain.alphaRaster
import io.github.customroutes.app.domain.outerContour
import io.github.customroutes.app.ml.ModelStatus
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class NoticeDocument(
    val title: String,
    val detail: String,
    @param:RawRes val resourceId: Int,
) {
    EFFICIENTSAM_MODEL(
        title = "EfficientSAM-Ti model details",
        detail = "Download URLs, sizes, hashes, license evidence, and training provenance.",
        resourceId = R.raw.efficientsam_model_details,
    ),
    EFFICIENTSAM_LICENSE(
        title = "EfficientSAM Apache License 2.0",
        detail = "License text supplied for the optional model files.",
        resourceId = R.raw.efficientsam_license,
    ),
    ONNXRUNTIME_LICENSE(
        title = "ONNX Runtime 1.29.0 MIT License",
        detail = "Microsoft's license for the packaged Android runtime.",
        resourceId = R.raw.onnxruntime_license,
    ),
    ONNXRUNTIME_NOTICES(
        title = "ONNX Runtime 1.29.0 third-party notices",
        detail = "Complete version-matched notices for incorporated components.",
        resourceId = R.raw.onnxruntime_third_party_notices,
    ),
}

@Composable
fun CustomRoutesApp(
    state: AppUiState,
    viewModel: AppViewModel,
    onPickPhoto: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    var roleColorTipPresented by rememberSaveable { mutableStateOf(false) }
    val roleColorTipMessage = "Tip: Long-press a role to change its color, or use Hold colors in Settings."
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val roleControlsVisible = state.project != null && state.bitmap != null &&
            !state.showSettings && state.exportPreview == null &&
            (state.editorMode == EditorMode.ADD ||
                    (state.editorMode == EditorMode.EDIT && state.selectedHoldId != null))
    LaunchedEffect(state.shouldShowRoleColorTip, roleControlsVisible) {
        if (state.shouldShowRoleColorTip && roleControlsVisible && !roleColorTipPresented) {
            coroutineScope {
                val snackbarJob = launch {
                    snackbarHost.showSnackbar(
                        message = roleColorTipMessage,
                        actionLabel = "Got it",
                        duration = SnackbarDuration.Long,
                    )
                }
                snapshotFlow { snackbarHost.currentSnackbarData?.visuals?.message }
                    .first { it == roleColorTipMessage }
                roleColorTipPresented = true
                viewModel.markRoleColorTipShown()
                snackbarJob.join()
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.project == null || state.bitmap == null) {
                if (state.showPrivacyData) {
                    BackHandler(enabled = !state.showSettings, onBack = viewModel::closePrivacyData)
                    PrivacyDataScreen(state, viewModel)
                } else {
                    HomeScreen(
                        projects = state.projects,
                        roleColors = state.roleColors,
                        appearanceSettings = state.appearanceSettings,
                        projectThumbnails = state.projectThumbnails,
                        onPickPhoto = onPickPhoto,
                        onOpenProject = viewModel::openProject,
                        onDeleteProject = viewModel::requestProjectDeletion,
                        onThumbnailVisible = viewModel::requestProjectThumbnail,
                        onThumbnailDisposed = viewModel::releaseProjectThumbnail,
                        onOpenSettings = viewModel::openSettings,
                        onOpenPrivacy = viewModel::openPrivacyData,
                    )
                }
            } else {
                BackHandler(
                    enabled = !state.showSettings && state.exportPreview == null,
                    onBack = viewModel::closeProject,
                )
                EditorScreen(state, viewModel)
            }

            if (state.exportPreview != null) {
                BackHandler(enabled = !state.showSettings, onBack = viewModel::closeExportPreview)
                ExportPreviewScreen(state, viewModel)
            }

            if (state.showSettings) {
                BackHandler(onBack = viewModel::closeSettings)
                SettingsScreen(state, viewModel)
            }

            state.busyMessage?.let { message ->
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxSize().clickable(enabled = true, onClick = {}),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(message, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    state.pendingProjectDeletion?.let { pending ->
        AlertDialog(
            onDismissRequest = { if (!state.isDeletingProject) viewModel.dismissProjectDeletion() },
            title = { Text("Delete local project?") },
            text = {
                Text(
                    "${pending.name ?: "Untitled route"} and its private photo copy will be deleted. " +
                            "Original photos and exported images will remain.",
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmProjectDeletion,
                    enabled = !state.isDeletingProject
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = viewModel::dismissProjectDeletion,
                    enabled = !state.isDeletingProject
                ) { Text("Cancel") }
            },
        )
    }

    if (state.pendingAppearanceReset) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAppearanceReset,
            title = { Text("Reset all appearance settings?") },
            text = {
                Text(
                    "This restores the default role colors, 100% border width, " +
                            "small-hold adjustment, and 60% export dimming. Projects and photos will not change.",
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmAppearanceReset,
                    enabled = !state.isResettingAppearance,
                ) {
                    if (state.isResettingAppearance) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Reset settings")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = viewModel::dismissAppearanceReset,
                    enabled = !state.isResettingAppearance,
                ) { Text("Cancel") }
            },
        )
    }

    if (state.pendingAiRequest != null &&
        (state.modelStatus is ModelStatus.Missing || state.modelStatus is ModelStatus.Failed)
    ) {
        val failure = state.modelStatus as? ModelStatus.Failed
        AlertDialog(
            onDismissRequest = viewModel::dismissModelDownload,
            title = { Text(if (failure == null) "Download AI model?" else "Download failed") },
            text = {
                Text(
                    buildString {
                        if (failure != null) append(failure.message).append("\n\n")
                        append(
                            "AI hold selection needs a one-time 41 MB model download. " +
                                    "The AI model is downloaded directly from Hugging Face and is not " +
                                    "supplied or verified by F-Droid. Your photos stay on this device, and " +
                                    "AI tools work offline afterward. Custom Routes verifies each file's " +
                                    "size and SHA-256 before use. Manual marking works without the download. " +
                                    "License and model details are always available in Privacy & Data.",
                        )
                    },
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmModelDownload) {
                    Text(if (failure == null) "Download" else "Retry")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissModelDownload) { Text("Not now") }
            },
        )
    }

    state.pendingPrivacyAction?.let { action ->
        val finalConfirmation = state.privacyConfirmationStep == action.confirmationSteps
        LaunchedEffect(action, state.privacyConfirmationStep) {
            viewModel.markPrivacyConfirmationShown(action, state.privacyConfirmationStep)
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissPrivacyAction,
            title = { Text(privacyDialogTitle(action, finalConfirmation)) },
            text = { Text(privacyDialogText(action, finalConfirmation, state.storedProjectCount)) },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmPrivacyAction,
                    enabled = !state.isApplyingPrivacyAction && state.privacyConfirmationReady,
                ) {
                    if (state.isApplyingPrivacyAction) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(privacyConfirmLabel(action, finalConfirmation))
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = viewModel::dismissPrivacyAction,
                    enabled = !state.isApplyingPrivacyAction,
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HomeScreen(
    projects: List<ProjectSummary>,
    roleColors: Map<HoldRole, Int>,
    appearanceSettings: AppearanceSettings,
    projectThumbnails: Map<String, ProjectThumbnailUiState>,
    onPickPhoto: () -> Unit,
    onOpenProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onThumbnailVisible: (String) -> Unit,
    onThumbnailDisposed: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    var homeMenuExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CUSTOM ROUTES",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { homeMenuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Home menu")
                    }
                    DropdownMenu(
                        expanded = homeMenuExpanded,
                        onDismissRequest = { homeMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                homeMenuExpanded = false
                                onOpenSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Privacy & data") },
                            leadingIcon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                            onClick = {
                                homeMenuExpanded = false
                                onOpenPrivacy()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onPickPhoto,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Open a gym photo", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            Text("LOCAL PROJECTS", style = MaterialTheme.typography.labelMedium)
        }

        if (projects.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "No routes yet. Start with a wide, well-lit photo of the wall.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(projects, key = ProjectSummary::id) { project ->
            ProjectCard(
                project = project,
                thumbnail = projectThumbnails[project.id],
                thumbnailSignature = ThumbnailSignature.forProject(project, roleColors, appearanceSettings),
                onClick = { onOpenProject(project.id) },
                onDelete = { onDeleteProject(project.id) },
                onThumbnailVisible = onThumbnailVisible,
                onThumbnailDisposed = onThumbnailDisposed,
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, viewModel: AppViewModel) {
    val settings = state.appearanceSettings
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::closeSettings, enabled = !state.isResettingAppearance) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
        }
        item { SettingsExportPreview(state.roleColors, settings, viewModel::setRoleColor) }
        item { Text("BORDERS", style = MaterialTheme.typography.labelMedium) }
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Adjust borders for small holds", fontWeight = FontWeight.Bold)
                            Text(
                                "Makes borders thinner on small or narrow holds.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.adjustSmallHolds,
                            onCheckedChange = viewModel::setAdjustSmallHolds,
                        )
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Border width", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("${settings.borderWidthPercent}%", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = settings.borderWidthPercent.toFloat(),
                        onValueChange = { viewModel.setBorderWidthPercent(it.roundToInt()) },
                        valueRange = MIN_BORDER_WIDTH_PERCENT.toFloat()..MAX_BORDER_WIDTH_PERCENT.toFloat(),
                        steps = (MAX_BORDER_WIDTH_PERCENT - MIN_BORDER_WIDTH_PERCENT) / BORDER_WIDTH_STEP_PERCENT - 1,
                    )
                    SliderLabels("Thin 50%", "Current 100%", "Thick 200%")
                }
            }
        }
        item { Text("EXPORT", style = MaterialTheme.typography.labelMedium) }
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Dim wall in exported JPEG", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("${settings.exportDimmingPercent}%", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "Keeps route holds and borders at full brightness.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = settings.exportDimmingPercent.toFloat(),
                        onValueChange = { viewModel.setExportDimmingPercent(it.roundToInt()) },
                        valueRange = MIN_EXPORT_DIMMING_PERCENT.toFloat()..MAX_EXPORT_DIMMING_PERCENT.toFloat(),
                        steps = (MAX_EXPORT_DIMMING_PERCENT - MIN_EXPORT_DIMMING_PERCENT) /
                                EXPORT_DIMMING_STEP_PERCENT - 1,
                    )
                    SliderLabels("Off 0%", "", "Darker 60%")
                }
            }
        }
        item { Text("AI", style = MaterialTheme.typography.labelMedium) }
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Improve AI detail when zoomed in", fontWeight = FontWeight.Bold)
                        Text(
                            "Uses extra detail for potentially better small-hold outlines. " +
                                    "Turn it off on lower-end devices to favor speed; segmentation quality may be lower.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.improveAiDetailWhenZoomed,
                        onCheckedChange = viewModel::setImproveAiDetailWhenZoomed,
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = viewModel::requestAppearanceReset,
                enabled = !state.isResettingAppearance,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Reset all appearance settings")
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SettingsExportPreview(
    roleColors: Map<HoldRole, Int>,
    settings: AppearanceSettings,
    onColorChange: (HoldRole, Int) -> Unit,
) {
    var editingColorFor by remember { mutableStateOf<HoldRole?>(null) }
    val photo = ImageBitmap.imageResource(R.drawable.settings_preview_photo)
    val routeMask = ImageBitmap.imageResource(R.drawable.settings_preview_route_mask)
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Export preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Example route", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(SETTINGS_PREVIEW_WIDTH / SETTINGS_PREVIEW_HEIGHT)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                val destination = IntSize(size.width.roundToInt(), size.height.roundToInt())
                drawImage(image = photo, dstSize = destination)
                if (settings.exportDimmingPercent > 0) {
                    drawRect(Color.Black.copy(alpha = settings.exportDimmingPercent / 100f))
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                    drawImage(image = photo, dstSize = destination)
                    drawImage(image = routeMask, dstSize = destination, blendMode = BlendMode.DstIn)
                    drawContext.canvas.restore()
                }
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                SETTINGS_PREVIEW_HOLDS.forEach { hold ->
                    val points = hold.points.map { point ->
                        SourcePoint(
                            point.x / SETTINGS_PREVIEW_WIDTH * size.width,
                            point.y / SETTINGS_PREVIEW_HEIGHT * size.height,
                        )
                    }
                    drawBorderPath(
                        points = points,
                        color = Color(roleColors[hold.role] ?: hold.role.argb),
                        selected = false,
                        appearanceSettings = settings,
                    )
                }
                drawImage(image = routeMask, dstSize = destination, blendMode = BlendMode.DstOut)
                drawContext.canvas.restore()
            }
            PreviewColorKey(roleColors) { editingColorFor = it }
        }
    }
    editingColorFor?.let { role ->
        RoleColorDialog(
            role = role,
            colors = roleColors,
            onColorChange = onColorChange,
            onDismiss = { editingColorFor = null },
        )
    }
}

@Composable
private fun PreviewColorKey(roleColors: Map<HoldRole, Int>, onEditColor: (HoldRole) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hold colors", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Tap a hold type to change its color. Changes apply to all projects.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PreviewColorKeyItem(HoldRole.START, "Start", roleColors, onEditColor, Modifier.weight(1f))
            PreviewColorKeyItem(HoldRole.FINISH, "Finish", roleColors, onEditColor, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PreviewColorKeyItem(HoldRole.REGULAR, "Regular", roleColors, onEditColor, Modifier.weight(1f))
            PreviewColorKeyItem(HoldRole.FEET_ONLY, "Feet-only", roleColors, onEditColor, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewColorKeyItem(
    role: HoldRole,
    label: String,
    roleColors: Map<HoldRole, Int>,
    onEditColor: (HoldRole) -> Unit,
    modifier: Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable { onEditColor(role) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(roleColors[role] ?: role.argb),
                shape = CircleShape,
                modifier = Modifier.size(18.dp),
            ) {}
            Spacer(Modifier.width(7.dp))
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SliderLabels(start: String, middle: String, end: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(start, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        Text(middle, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        Text(
            end,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun PrivacyDataScreen(state: AppUiState, viewModel: AppViewModel) {
    val actionsEnabled = !state.isApplyingPrivacyAction
    var showOpenSourceLicenses by rememberSaveable { mutableStateOf(false) }
    if (showOpenSourceLicenses) {
        BackHandler { showOpenSourceLicenses = false }
        OpenSourceLicensesScreen(onBack = { showOpenSourceLicenses = false })
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::closePrivacyData, enabled = actionsEnabled) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Privacy & Data",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("LOCAL BY DESIGN", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    Text("Photos, projects, masks, preferences, and AI inference stay on this device.")
                    Text(
                        "The only network operation is a model download you approve. " +
                                "There are no analytics, ads, accounts, or photo uploads.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "JPEG exports are public media. Gallery or cloud-photo apps may index or synchronize them.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Text("OPEN SOURCE", style = MaterialTheme.typography.labelMedium)
        }
        item {
            OutlinedButton(
                onClick = { showOpenSourceLicenses = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("AI & runtime licenses and model details")
            }
        }
        item {
            Text("STORED ON THIS DEVICE", style = MaterialTheme.typography.labelMedium)
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StoredDataRow("AI model", modelStatusLabel(state.modelStatus, state.hasModelData))
                    StoredDataRow(
                        "Local projects",
                        if (state.storedProjectCount == 1) "1 project" else "${state.storedProjectCount} projects",
                    )
                }
            }
        }
        item {
            Text("DATA CONTROLS", style = MaterialTheme.typography.labelMedium)
        }
        item {
            PrivacyActionCard(
                icon = Icons.Default.CloudDownload,
                title = if (state.modelStatus is ModelStatus.Downloading) {
                    "Cancel download and delete model"
                } else {
                    "Delete AI model"
                },
                detail = "Manual editing will keep working. AI tools can download it again later.",
                enabled = actionsEnabled && state.modelStatus !is ModelStatus.Checking && state.hasModelData,
            ) { viewModel.requestPrivacyAction(PrivacyAction.DELETE_MODEL) }
        }
        item {
            PrivacyActionCard(
                icon = Icons.Default.Storage,
                title = "Delete all projects",
                detail = "Removes every private photo copy, route, and mask stored by the app.",
                enabled = actionsEnabled && state.storedProjectCount > 0,
            ) { viewModel.requestPrivacyAction(PrivacyAction.DELETE_PROJECTS) }
        }
        item {
            PrivacyActionCard(
                icon = Icons.Default.Delete,
                title = "Delete all local data",
                detail = "Uses Android to clear all private app data, then closes the app.",
                enabled = actionsEnabled,
            ) { viewModel.requestPrivacyAction(PrivacyAction.DELETE_ALL_DATA) }
        }
        item {
            Text(
                "Original photos and exported JPEGs are never deleted by these controls.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    var selectedDocumentName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDocument = NoticeDocument.entries.firstOrNull { it.name == selectedDocumentName }
    BackHandler {
        if (selectedDocument == null) onBack() else selectedDocumentName = null
    }
    if (selectedDocument != null) {
        NoticeDocumentScreen(selectedDocument, onBack = { selectedDocumentName = null })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "AI & runtime notices",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        item {
            Text(
                "These documents are packaged with the app and remain available offline.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(NoticeDocument.entries, key = { it.name }) { document ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().clickable { selectedDocumentName = document.name },
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(document.title, fontWeight = FontWeight.Bold)
                    Text(
                        document.detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun NoticeDocumentScreen(document: NoticeDocument, onBack: () -> Unit) {
    val context = LocalContext.current
    val lines by produceState<List<String>?>(initialValue = null, document) {
        value = withContext(Dispatchers.IO) {
            context.resources.openRawResource(document.resourceId)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readLines() }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    document.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        if (lines == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            items(lines.orEmpty()) { line ->
                if (line.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun StoredDataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacyActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    enabled: Boolean,
    destructive: Boolean = true,
    onClick: () -> Unit,
) {
    val actionColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
            alpha = 0.38f
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = actionColor, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun modelStatusLabel(status: ModelStatus, hasStoredData: Boolean): String = when (status) {
    ModelStatus.Checking -> "Checking"
    ModelStatus.Missing -> if (hasStoredData) "Incomplete files stored" else "Not installed"
    ModelStatus.Ready -> "Installed (41 MB)"
    is ModelStatus.Downloading -> "Downloading ${(status.fraction * 100).toInt()}%"
    is ModelStatus.Failed -> "Download failed"
}

private fun privacyDialogTitle(action: PrivacyAction, finalConfirmation: Boolean): String = when (action) {
    PrivacyAction.DELETE_MODEL -> "Delete AI model?"
    PrivacyAction.DELETE_PROJECTS -> if (finalConfirmation) "Confirm project deletion" else "Delete all projects?"
    PrivacyAction.DELETE_ALL_DATA -> if (finalConfirmation) "Final confirmation" else "Delete all local data?"
}

private fun privacyDialogText(action: PrivacyAction, finalConfirmation: Boolean, projectCount: Int): String {
    val preserved = "Original photos and exported JPEGs will not be deleted."
    return when (action) {
        PrivacyAction.DELETE_MODEL ->
            "This removes the installed model and any partial download. Manual tools will remain available. $preserved"

        PrivacyAction.DELETE_PROJECTS -> if (finalConfirmation) {
            "This removes all $projectCount local projects. This cannot be undone. $preserved"
        } else {
            "This removes all private photo copies, routes, and masks from the app. $preserved"
        }

        PrivacyAction.DELETE_ALL_DATA -> if (finalConfirmation) {
            "This removes every private app file and preference, then closes the app. $preserved"
        } else {
            "This removes all projects, private photo copies, model files, and preferences. $preserved"
        }
    }
}

private fun privacyConfirmLabel(action: PrivacyAction, finalConfirmation: Boolean): String = when {
    !finalConfirmation -> "Continue"
    action == PrivacyAction.DELETE_MODEL -> "Delete model"
    action == PrivacyAction.DELETE_PROJECTS -> "Delete all projects"
    else -> "Delete all local data"
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    thumbnail: ProjectThumbnailUiState?,
    thumbnailSignature: ThumbnailSignature,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onThumbnailVisible: (String) -> Unit,
    onThumbnailDisposed: (String) -> Unit,
) {
    LaunchedEffect(project.id, thumbnailSignature) {
        onThumbnailVisible(project.id)
    }
    DisposableEffect(project.id) {
        onDispose { onThumbnailDisposed(project.id) }
    }
    val thumbnailBitmap = thumbnail?.bitmap
    val thumbnailImage = remember(thumbnailBitmap) { thumbnailBitmap?.asImageBitmap() }
    val date = remember(project.updatedAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(project.updatedAtEpochMillis))
    }
    val holdCount = pluralStringResource(R.plurals.marked_hold_count, project.holdCount, project.holdCount)
    val status = stringResource(if (project.isComplete) R.string.route_status_ready else R.string.route_status_draft)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        var menuExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF151B18)),
                contentAlignment = Alignment.Center,
            ) {
                thumbnailImage?.let { image ->
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                Text(
                    project.name ?: "Untitled route",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$holdCount  /  $status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Project menu")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete local project") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(state: AppUiState, viewModel: AppViewModel) {
    val project = checkNotNull(state.project)
    val bitmap = checkNotNull(state.bitmap)
    var showRename by remember { mutableStateOf(false) }
    var renameValue by remember(project.id) { mutableStateOf(project.name.orEmpty()) }
    var projectMenuExpanded by remember { mutableStateOf(false) }
    val strokeTool = when (state.editorMode) {
        EditorMode.ADD -> StrokeTool.ADD_MANUAL.takeIf { state.addMethod == AddMethod.MANUAL_PAINT }
        EditorMode.EDIT -> when (state.editAction) {
            EditAction.PAINT -> StrokeTool.EDIT_PAINT
            EditAction.ERASE -> StrokeTool.EDIT_ERASE
            else -> null
        }

        EditorMode.MOVE -> null
    }
    val interaction = when {
        state.editorMode == EditorMode.MOVE -> CanvasInteraction.MOVE
        strokeTool != null -> CanvasInteraction.STROKE
        else -> CanvasInteraction.TAP
    }
    val brushRadiusPx = with(LocalDensity.current) { state.brushSize.radiusDp.dp.toPx() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::closeProject) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = project.name ?: "Untitled route",
                modifier = Modifier.weight(1f).clickable {
                    renameValue = project.name.orEmpty()
                    showRename = true
                }.padding(horizontal = 8.dp, vertical = 12.dp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            TextButton(onClick = viewModel::openExportPreview) { Text("Export") }
            Box {
                IconButton(onClick = { projectMenuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Project menu")
                }
                DropdownMenu(
                    expanded = projectMenuExpanded,
                    onDismissRequest = { projectMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = {
                            projectMenuExpanded = false
                            viewModel.openSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete local project") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            projectMenuExpanded = false
                            viewModel.requestProjectDeletion(project.id)
                        },
                    )
                }
            }
        }

        EditorCanvas(
            project = project,
            bitmap = bitmap,
            interaction = interaction,
            strokeTool = strokeTool,
            brushRadiusPx = brushRadiusPx,
            selectedHoldId = state.selectedHoldId,
            manualDraft = state.manualDraft,
            strokePreview = state.strokePreview,
            roleColors = state.roleColors,
            appearanceSettings = state.appearanceSettings,
            dimPhoto = state.editorMode == EditorMode.EDIT && state.selectedHoldId != null,
            onTap = { point, zoom, bounds -> viewModel.onImageTap(point.x, point.y, zoom, bounds) },
            onStrokeStart = viewModel::beginStroke,
            onPaint = viewModel::paintAt,
            onStrokeEnd = viewModel::endStroke,
            onStrokeCancel = viewModel::cancelStroke,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        ContextualEditorPanel(state, project, viewModel)
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename route") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    placeholder = { Text("Untitled route") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameProject(renameValue)
                    showRename = false
                }) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRename = false }) { Text("Cancel") }
            },
        )
    }

}

@Composable
private fun ExportPreviewScreen(state: AppUiState, viewModel: AppViewModel) {
    val project = checkNotNull(state.project)
    val preview = checkNotNull(state.exportPreview)
    val saving = (preview as? ExportPreviewUiState.Ready)?.isSaving == true
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::closeExportPreview, enabled = !saving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Export preview",
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = viewModel::openSettings, enabled = !saving) {
                    Icon(Icons.Default.Palette, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Appearance")
                }
            }

            when (preview) {
                ExportPreviewUiState.Loading -> ExportPreviewLoading()
                is ExportPreviewUiState.Failed -> ExportPreviewFailure(preview.message, viewModel)
                is ExportPreviewUiState.Ready -> ExportPreviewReady(project, preview, viewModel)
            }
        }
    }
}

@Composable
private fun ExportPreviewLoading() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Preparing full-resolution JPEG", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ExportPreviewFailure(message: String, viewModel: AppViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Preview could not be created", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = viewModel::closeExportPreview) { Text("Back") }
            Button(onClick = viewModel::retryExportPreview) { Text("Retry") }
        }
    }
}

@Composable
private fun ColumnScope.ExportPreviewReady(
    project: RouteProject,
    preview: ExportPreviewUiState.Ready,
    viewModel: AppViewModel,
) {
    val bitmap = preview.bitmap
    DisposableEffect(preview.bitmap) {
        onDispose {
            val retained = (viewModel.state.value.exportPreview as? ExportPreviewUiState.Ready)?.bitmap === bitmap
            if (!retained && !bitmap.isRecycled) bitmap.recycle()
        }
    }
    ExportPreviewImage(
        bitmap = bitmap,
        modifier = Modifier.fillMaxWidth().weight(1f),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(preview.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${preview.width} x ${preview.height}  •  ${formatExportSize(preview.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Pictures/Custom Routes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        incompleteExportMessage(project)?.let { warning ->
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(14.dp)) {
                Text(
                    warning,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Button(
            onClick = viewModel::saveExportPreview,
            enabled = !preview.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (preview.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Saving")
            } else {
                Text(if (project.isComplete) "Save JPEG" else "Save anyway")
            }
        }
    }
}

@Composable
private fun ExportPreviewImage(bitmap: Bitmap, modifier: Modifier = Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var pan by remember(bitmap) { mutableStateOf(SourcePoint(0f, 0f)) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier
            .background(Color(0xFF080D0A))
            .clipToBounds()
            .onSizeChanged { size ->
                viewportSize = size
                pan = clampPan(pan, size.width, size.height, bitmap.width, bitmap.height, zoom)
            }
            .pointerInput(bitmap) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val oldZoom = zoom
                    val newZoom = (zoom * zoomChange).coerceIn(1f, 8f)
                    val ratio = newZoom / oldZoom
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val currentPan = Offset(pan.x, pan.y)
                    val nextPan = (currentPan - (centroid - center)) * ratio +
                            (centroid - center) + panChange
                    zoom = newZoom
                    pan = clampPan(
                        SourcePoint(nextPan.x, nextPan.y),
                        size.width,
                        size.height,
                        bitmap.width,
                        bitmap.height,
                        zoom,
                    )
                }
            }
            .pointerInput(bitmap) {
                detectTapGestures(
                    onDoubleTap = {
                        zoom = 1f
                        pan = SourcePoint(0f, 0f)
                    },
                )
            },
    ) {
        val transform = imageTransform(
            viewportSize.width,
            viewportSize.height,
            bitmap.width,
            bitmap.height,
            zoom,
            pan,
        )
        drawImage(
            image = image,
            dstOffset = IntOffset(transform.left.toInt(), transform.top.toInt()),
            dstSize = IntSize(transform.width.toInt(), transform.height.toInt()),
        )
    }
}

internal fun incompleteExportMessage(project: RouteProject): String? {
    if (project.isComplete) return null
    val missing = buildList {
        if (project.holds.none { it.role == HoldRole.START }) add("a Start hold")
        if (project.holds.none { it.role == HoldRole.FINISH }) add("a Finish hold")
    }
    return "This route is missing ${missing.joinToString(" and ")}. You can save the draft anyway."
}

internal fun formatExportSize(sizeBytes: Long): String = when {
    sizeBytes < 1024L -> "$sizeBytes B"
    sizeBytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024f)
    else -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f))
}

@Composable
private fun ContextualEditorPanel(
    state: AppUiState,
    project: RouteProject,
    viewModel: AppViewModel,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeButton("Add", state.editorMode == EditorMode.ADD, Modifier.weight(1f)) {
                    viewModel.setEditorMode(EditorMode.ADD)
                }
                ModeButton("Edit", state.editorMode == EditorMode.EDIT, Modifier.weight(1f)) {
                    viewModel.setEditorMode(EditorMode.EDIT)
                }
                ModeButton("Move", state.editorMode == EditorMode.MOVE, Modifier.weight(1f)) {
                    viewModel.setEditorMode(EditorMode.MOVE)
                }
            }
            Spacer(Modifier.height(6.dp))
            when (state.editorMode) {
                EditorMode.ADD -> AddPanel(state, viewModel)
                EditorMode.EDIT -> EditPanel(state, project, viewModel)
                EditorMode.MOVE -> Guidance(
                    if (state.isPreparingAi) "Preparing AI" else "Drag with one finger. Pinch or pan with two fingers.",
                )
            }
        }
    }
}

@Composable
private fun AddPanel(state: AppUiState, viewModel: AppViewModel) {
    val draft = state.manualDraft
    if (draft == null) {
        val downloading = state.modelStatus as? ModelStatus.Downloading
        val canUseAiControl = state.modelStatus !is ModelStatus.Checking && downloading == null
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PanelActionButton(
                label = "AI tap",
                icon = if (state.modelStatus is ModelStatus.Missing || state.modelStatus is ModelStatus.Failed) {
                    Icons.Default.CloudDownload
                } else {
                    Icons.Default.AutoFixHigh
                },
                selected = state.modelStatus is ModelStatus.Ready && state.addMethod == AddMethod.AI_TAP,
                enabled = canUseAiControl,
                progress = downloading?.fraction,
                modifier = Modifier.weight(1f),
            ) { viewModel.setAddMethod(AddMethod.AI_TAP) }
            PanelActionButton(
                label = "Manual paint",
                icon = Icons.Default.Brush,
                selected = state.addMethod == AddMethod.MANUAL_PAINT,
                modifier = Modifier.weight(1f),
            ) { viewModel.setAddMethod(AddMethod.MANUAL_PAINT) }
        }
        Spacer(Modifier.height(6.dp))
        RoleRow(state.activeRole, state.roleColors, enabled = true, viewModel::setRole, viewModel::setRoleColor)
        Guidance(
            when {
                state.isPreparingAi -> "Preparing AI"
                downloading != null -> "AI model downloading. Manual paint remains available."
                state.addMethod == AddMethod.AI_TAP -> "Tap an unmarked hold for AI segmentation."
                else -> "Paint on empty space to start a manual hold."
            },
        )
        return
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PanelActionButton(
            "Paint",
            Icons.Default.Brush,
            state.draftAction == DraftAction.PAINT,
            modifier = Modifier.weight(1f)
        ) {
            viewModel.setDraftAction(DraftAction.PAINT)
        }
        PanelActionButton(
            "Erase",
            Icons.Default.Brush,
            state.draftAction == DraftAction.ERASE,
            brushOffIcon = true,
            modifier = Modifier.weight(1f),
        ) { viewModel.setDraftAction(DraftAction.ERASE) }
    }
    Spacer(Modifier.height(6.dp))
    BrushSizeRow(state.brushSize, viewModel::setBrushSize)
    Spacer(Modifier.height(6.dp))
    RoleRow(draft.role, state.roleColors, enabled = true, viewModel::setRole, viewModel::setRoleColor)
    Guidance(if (state.draftAction == DraftAction.PAINT) "Drag to paint this new hold." else "Drag to erase this new hold.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::cancelManualDraft, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(Modifier.width(5.dp))
            Text("Cancel")
        }
        Button(onClick = viewModel::doneManualDraft, enabled = draft.canCommit, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(Modifier.width(5.dp))
            Text("Done")
        }
    }
}

@Composable
private fun EditPanel(state: AppUiState, project: RouteProject, viewModel: AppViewModel) {
    val selected = project.holds.firstOrNull { it.id == state.selectedHoldId }
    Guidance(
        text = if (state.isPreparingAi) {
            "Preparing AI"
        } else {
            when (state.editAction) {
                EditAction.SELECT -> "Tap a hold to select it; tap empty space to clear."
                EditAction.AI_INCLUDE -> "Tap an area the AI should include."
                EditAction.PAINT -> "Drag to paint pixels into the selected mask."
                EditAction.ERASE -> "Drag to erase pixels from the selected mask."
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        EditAction.entries.forEach { action ->
            val label = when (action) {
                EditAction.SELECT -> "Select"
                EditAction.AI_INCLUDE -> "AI Include"
                EditAction.PAINT -> "Paint"
                EditAction.ERASE -> "Erase"
            }
            val icon = when (action) {
                EditAction.SELECT -> Icons.Default.PanTool
                EditAction.AI_INCLUDE -> Icons.Default.AutoFixHigh
                EditAction.PAINT -> Icons.Default.Brush
                EditAction.ERASE -> Icons.Default.Brush
            }
            val needsSelection = action != EditAction.SELECT
            val needsModel = action == EditAction.AI_INCLUDE
            val downloading = state.modelStatus as? ModelStatus.Downloading
            val modelControlEnabled = state.modelStatus !is ModelStatus.Checking && downloading == null
            PanelActionButton(
                label = label,
                icon = if (needsModel &&
                    (state.modelStatus is ModelStatus.Missing || state.modelStatus is ModelStatus.Failed)
                ) {
                    Icons.Default.CloudDownload
                } else {
                    icon
                },
                selected = state.editAction == action && (!needsModel || state.modelStatus is ModelStatus.Ready),
                enabled = (!needsSelection || selected != null) && (!needsModel || modelControlEnabled),
                progress = downloading?.fraction.takeIf { needsModel },
                brushOffIcon = action == EditAction.ERASE,
                modifier = Modifier.weight(1f),
            ) { viewModel.setEditAction(action) }
        }
        PanelActionButton(
            label = "Delete",
            icon = Icons.Default.Delete,
            selected = false,
            enabled = selected != null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        ) { viewModel.removeSelectedHold() }
    }
    if (selected != null) {
        if (state.editAction == EditAction.PAINT || state.editAction == EditAction.ERASE) {
            Spacer(Modifier.height(6.dp))
            BrushSizeRow(state.brushSize, viewModel::setBrushSize)
        }
        Spacer(Modifier.height(6.dp))
        RoleRow(selected.role, state.roleColors, enabled = true, viewModel::setRole, viewModel::setRoleColor)
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) FilledTonalButton(onClick = onClick, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
}

@Composable
private fun PanelActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean = true,
    progress: Float? = null,
    brushOffIcon: Boolean = false,
    tint: Color? = null,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            tint != null -> tint
            else -> MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (progress == null) {
                if (brushOffIcon) {
                    BrushOffIcon(Modifier.size(19.dp))
                } else {
                    Icon(icon, contentDescription = null, Modifier.size(19.dp))
                }
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(19.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}

@Composable
private fun BrushOffIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Box(modifier) {
        Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.matchParentSize())
        Canvas(Modifier.matchParentSize()) {
            drawLine(
                color = color,
                start = Offset(size.width * 0.12f, size.height * 0.12f),
                end = Offset(size.width * 0.88f, size.height * 0.88f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun BrushSizeRow(selected: BrushSize, onSelect: (BrushSize) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BrushSize.entries.forEach { size ->
            ModeButton(
                label = size.name.lowercase().replaceFirstChar(Char::uppercase),
                selected = selected == size,
                modifier = Modifier.weight(1f),
            ) { onSelect(size) }
        }
    }
}

@Composable
private fun RoleRow(
    selected: HoldRole,
    colors: Map<HoldRole, Int>,
    enabled: Boolean,
    onSelect: (HoldRole) -> Unit,
    onColorChange: (HoldRole, Int) -> Unit,
) {
    var editingColorFor by remember { mutableStateOf<HoldRole?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        RoleButton(
            "Start",
            HoldRole.START,
            selected,
            colors,
            enabled,
            onSelect,
            { editingColorFor = it },
            Modifier.weight(1f)
        )
        RoleButton(
            "Finish",
            HoldRole.FINISH,
            selected,
            colors,
            enabled,
            onSelect,
            { editingColorFor = it },
            Modifier.weight(1f)
        )
        RoleButton(
            "Regular",
            HoldRole.REGULAR,
            selected,
            colors,
            enabled,
            onSelect,
            { editingColorFor = it },
            Modifier.weight(1f)
        )
        RoleButton(
            "Feet",
            HoldRole.FEET_ONLY,
            selected,
            colors,
            enabled,
            onSelect,
            { editingColorFor = it },
            Modifier.weight(1f)
        )
    }
    editingColorFor?.let { role ->
        RoleColorDialog(
            role = role,
            colors = colors,
            onColorChange = onColorChange,
            onDismiss = { editingColorFor = null },
        )
    }
}

@Composable
private fun RoleColorDialog(
    role: HoldRole,
    colors: Map<HoldRole, Int>,
    onColorChange: (HoldRole, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${role.displayName} color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ROLE_COLOR_CHOICES.chunked(4).forEach { rowColors ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowColors.forEach { argb ->
                            val selectedColor = colors[role] == argb
                            Surface(
                                color = Color(argb),
                                contentColor = contrastingContentColor(Color(argb)),
                                shape = CircleShape,
                                border = BorderStroke(
                                    if (selectedColor) 3.dp else 1.dp,
                                    if (selectedColor) MaterialTheme.colorScheme.onSurface else Color.Black.copy(
                                        alpha = 0.35f
                                    ),
                                ),
                                modifier = Modifier.size(48.dp).clickable {
                                    onColorChange(role, argb)
                                    onDismiss()
                                },
                            ) {
                                if (selectedColor) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected color")
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Applies to all projects", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoleButton(
    label: String,
    role: HoldRole,
    selected: HoldRole,
    colors: Map<HoldRole, Int>,
    enabled: Boolean,
    onSelect: (HoldRole) -> Unit,
    onEditColor: (HoldRole) -> Unit,
    modifier: Modifier,
) {
    val color = Color(colors[role] ?: role.argb)
    Surface(
        color = if (selected == role) color else color.copy(alpha = 0.18f),
        contentColor = if (selected == role) contrastingContentColor(color) else color,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.combinedClickable(
            enabled = enabled,
            onClick = { onSelect(role) },
            onLongClick = { onEditColor(role) },
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 9.dp),
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Guidance(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun EditorCanvas(
    project: RouteProject,
    bitmap: android.graphics.Bitmap,
    interaction: CanvasInteraction,
    strokeTool: StrokeTool?,
    brushRadiusPx: Float,
    selectedHoldId: String?,
    manualDraft: ManualHoldDraft?,
    strokePreview: StrokePreview?,
    roleColors: Map<HoldRole, Int>,
    appearanceSettings: AppearanceSettings,
    dimPhoto: Boolean,
    onTap: (SourcePoint, Float, SourceRect) -> Unit,
    onStrokeStart: (StrokeTool) -> Unit,
    onPaint: (Float, Float, Float) -> Unit,
    onStrokeEnd: () -> Unit,
    onStrokeCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val stableOutlines = remember(
        project.holds,
        manualDraft,
        strokePreview != null,
        strokePreview?.holdId,
        selectedHoldId,
        roleColors,
    ) {
        buildList {
            project.holds.forEach { hold ->
                if (hold.id != strokePreview?.holdId) {
                    add(
                        HoldOutline(
                            color = Color(roleColors[hold.role] ?: hold.role.argb),
                            points = hold.maskRegion.outerContour(),
                            mask = hold.maskRegion.alphaRaster().renderMask(),
                            selected = hold.id == selectedHoldId,
                        ),
                    )
                }
            }
            if (manualDraft != null && strokePreview?.holdId != null) {
                add(
                    HoldOutline(
                        Color(roleColors[manualDraft.role] ?: manualDraft.role.argb),
                        manualDraft.maskRegion.outerContour(),
                        manualDraft.maskRegion.alphaRaster().renderMask(),
                        selected = true,
                    ),
                )
            } else if (manualDraft != null && strokePreview == null) {
                add(
                    HoldOutline(
                        Color(roleColors[manualDraft.role] ?: manualDraft.role.argb),
                        manualDraft.maskRegion.outerContour(),
                        manualDraft.maskRegion.alphaRaster().renderMask(),
                        selected = true,
                    ),
                )
            }
        }.sortedBy { it.selected }
    }
    DisposableEffect(stableOutlines) {
        onDispose { stableOutlines.forEach { it.mask.bitmap.recycle() } }
    }
    val previewMask = remember(strokePreview) { strokePreview?.raster?.renderMask() }
    DisposableEffect(previewMask) {
        onDispose { previewMask?.bitmap?.recycle() }
    }
    val outlines = remember(stableOutlines, strokePreview, previewMask) {
        strokePreview?.let {
            stableOutlines + HoldOutline(
                Color(roleColors[it.role] ?: it.role.argb),
                it.outline,
                checkNotNull(previewMask),
                selected = true,
            )
        }
            ?: stableOutlines
    }
    var zoom by rememberSaveable(bitmap) { mutableFloatStateOf(1f) }
    var pan by rememberSaveable(
        bitmap,
        stateSaver = listSaver(
            save = { listOf(it.x, it.y) },
            restore = { SourcePoint(it[0], it[1]) },
        ),
    ) { mutableStateOf(SourcePoint(0f, 0f)) }

    Canvas(
        modifier
            .background(Color(0xFF080D0A))
            .clipToBounds()
            .onSizeChanged { newSize ->
                pan = clampPan(pan, newSize.width, newSize.height, bitmap.width, bitmap.height, zoom)
            }
            .pointerInput(interaction, strokeTool, bitmap, brushRadiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastPosition = down.position
                    var transformed = false
                    var tapCandidate = true
                    var strokeActive = interaction == CanvasInteraction.STROKE
                    try {
                        if (strokeActive) {
                            onStrokeStart(checkNotNull(strokeTool))
                            paintAtPosition(
                                position = down.position,
                                viewportWidth = size.width,
                                viewportHeight = size.height,
                                project = project,
                                bitmap = bitmap,
                                zoom = zoom,
                                pan = pan,
                                brushRadiusPx = brushRadiusPx,
                                onPaint = onPaint,
                            )
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull()?.let { lastPosition = it.position }
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                if (strokeActive) {
                                    onStrokeCancel()
                                    strokeActive = false
                                }
                                transformed = true
                                tapCandidate = false
                                val oldZoom = zoom
                                val newZoom = (zoom * event.calculateZoom()).coerceIn(1f, 8f)
                                val ratio = newZoom / oldZoom
                                val centroid = event.calculateCentroid()
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val currentPan = Offset(pan.x, pan.y)
                                val nextPan = (currentPan - (centroid - center)) * ratio +
                                        (centroid - center) + event.calculatePan()
                                zoom = newZoom
                                pan = clampPan(
                                    SourcePoint(nextPan.x, nextPan.y),
                                    size.width,
                                    size.height,
                                    bitmap.width,
                                    bitmap.height,
                                    zoom,
                                )
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size == 1 && !transformed) {
                                val change = pressed.single()
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    tapCandidate = false
                                }
                                when (interaction) {
                                    CanvasInteraction.MOVE -> {
                                        val changePan = change.positionChange()
                                        pan = clampPan(
                                            SourcePoint(pan.x + changePan.x, pan.y + changePan.y),
                                            size.width,
                                            size.height,
                                            bitmap.width,
                                            bitmap.height,
                                            zoom,
                                        )
                                        change.consume()
                                    }

                                    CanvasInteraction.STROKE -> {
                                        paintAtPosition(
                                            position = change.position,
                                            viewportWidth = size.width,
                                            viewportHeight = size.height,
                                            project = project,
                                            bitmap = bitmap,
                                            zoom = zoom,
                                            pan = pan,
                                            brushRadiusPx = brushRadiusPx,
                                            onPaint = onPaint,
                                        )
                                        change.consume()
                                    }

                                    CanvasInteraction.TAP -> Unit
                                }
                            } else if (pressed.isEmpty()) {
                                if (strokeActive) {
                                    strokeActive = false
                                    onStrokeEnd()
                                }
                                if (!transformed && tapCandidate && interaction == CanvasInteraction.TAP) {
                                    val transform = imageTransform(
                                        size.width,
                                        size.height,
                                        bitmap.width,
                                        bitmap.height,
                                        zoom,
                                        pan,
                                    )
                                    screenToSource(
                                        SourcePoint(lastPosition.x, lastPosition.y),
                                        transform,
                                        project.sourceWidth,
                                        project.sourceHeight,
                                    )?.let { point ->
                                        onTap(
                                            point,
                                            zoom,
                                            visibleSourceBounds(
                                                transform,
                                                size.width,
                                                size.height,
                                                project.sourceWidth,
                                                project.sourceHeight,
                                            ),
                                        )
                                    }
                                }
                                break
                            }
                        }
                    } finally {
                        if (strokeActive) onStrokeCancel()
                    }
                }
            },
    ) {
        val transform = imageTransform(size.width.toInt(), size.height.toInt(), bitmap.width, bitmap.height, zoom, pan)
        drawImage(
            image = image,
            dstOffset = IntOffset(transform.left.toInt(), transform.top.toInt()),
            dstSize = IntSize(transform.width.toInt(), transform.height.toInt()),
        )
        if (dimPhoto) {
            drawRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = Offset(transform.left, transform.top),
                size = Size(transform.width, transform.height),
            )
        }
        val selectedOutline = outlines.lastOrNull { it.selected }
        if (dimPhoto && selectedOutline != null) {
            val maskBounds = selectedOutline.mask.screenRect(transform, project.sourceWidth, project.sourceHeight)
            drawContext.canvas.saveLayer(
                Rect(transform.left, transform.top, transform.left + transform.width, transform.top + transform.height),
                Paint(),
            )
            clipRect(maskBounds.left, maskBounds.top, maskBounds.right, maskBounds.bottom) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset(transform.left.toInt(), transform.top.toInt()),
                    dstSize = IntSize(transform.width.toInt(), transform.height.toInt()),
                )
            }
            drawRenderMask(
                selectedOutline.mask,
                transform,
                project.sourceWidth,
                project.sourceHeight,
                blendMode = AndroidBlendMode.DST_IN,
            )
            drawContext.canvas.restore()
        }
        if (selectedOutline != null) {
            drawRenderMask(
                selectedOutline.mask,
                transform,
                project.sourceWidth,
                project.sourceHeight,
                alpha = 0.10f,
                color = selectedOutline.color,
            )
        }
        clipRect(
            transform.left,
            transform.top,
            transform.left + transform.width,
            transform.top + transform.height,
        ) {
            drawContext.canvas.saveLayer(
                Rect(transform.left, transform.top, transform.left + transform.width, transform.top + transform.height),
                Paint(),
            )
            outlines.forEach { outline ->
                drawHoldBorder(
                    outline,
                    transform,
                    project.sourceWidth,
                    project.sourceHeight,
                    appearanceSettings,
                )
            }
            outlines.forEach { outline ->
                drawRenderMask(
                    outline.mask,
                    transform,
                    project.sourceWidth,
                    project.sourceHeight,
                    blendMode = AndroidBlendMode.DST_OUT,
                )
            }
            drawContext.canvas.restore()
        }
    }
}

private data class HoldOutline(
    val color: Color,
    val points: List<SourcePoint>,
    val mask: RenderMask,
    val selected: Boolean,
)

private data class RenderMask(
    val bitmap: Bitmap,
    val sourceBounds: SourceRect,
)

private fun MaskRaster.renderMask(): RenderMask = RenderMask(toAlphaBitmap(), sourceBounds)

private fun RenderMask.screenRect(
    transform: ImageTransform,
    sourceWidth: Int,
    sourceHeight: Int,
): Rect = Rect(
    left = transform.left + sourceBounds.left / sourceWidth * transform.width,
    top = transform.top + sourceBounds.top / sourceHeight * transform.height,
    right = transform.left + sourceBounds.right / sourceWidth * transform.width,
    bottom = transform.top + sourceBounds.bottom / sourceHeight * transform.height,
)

private fun DrawScope.drawRenderMask(
    mask: RenderMask,
    transform: ImageTransform,
    sourceWidth: Int,
    sourceHeight: Int,
    alpha: Float = 1f,
    color: Color? = null,
    blendMode: AndroidBlendMode = AndroidBlendMode.SRC_OVER,
) {
    val bounds = mask.screenRect(transform, sourceWidth, sourceHeight)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = false
        this.blendMode = blendMode
        color?.let { this.color = it.toArgb() }
        this.alpha = (alpha * 255f).roundToInt()
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawBitmap(
            mask.bitmap,
            null,
            RectF(bounds.left, bounds.top, bounds.right, bounds.bottom),
            paint,
        )
    }
}

private fun DrawScope.drawHoldBorder(
    outline: HoldOutline,
    transform: ImageTransform,
    sourceWidth: Int,
    sourceHeight: Int,
    appearanceSettings: AppearanceSettings,
) {
    if (outline.points.size < 3) return
    val points = outline.points.map { point ->
        SourcePoint(
            x = transform.left + point.x / sourceWidth * transform.width,
            y = transform.top + point.y / sourceHeight * transform.height,
        )
    }
    drawBorderPath(points, outline.color, outline.selected, appearanceSettings)
}

private fun DrawScope.drawBorderPath(
    points: List<SourcePoint>,
    color: Color,
    selected: Boolean,
    appearanceSettings: AppearanceSettings,
) {
    if (points.size < 3) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    val bands = adaptiveBorderBands(points, size.width, size.height, appearanceSettings)
    if (selected) {
        drawPath(
            path,
            Color.White.copy(alpha = 0.78f),
            style = Stroke(
                width = 2f * (bands.normalTotal + bands.selection),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
    drawPath(
        path,
        Color.Black.copy(alpha = 0.88f),
        style = Stroke(
            width = 2f * bands.normalTotal,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    drawPath(
        path,
        color,
        style = Stroke(
            width = 2f * bands.role,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun paintAtPosition(
    position: Offset,
    viewportWidth: Int,
    viewportHeight: Int,
    project: RouteProject,
    bitmap: android.graphics.Bitmap,
    zoom: Float,
    pan: SourcePoint,
    brushRadiusPx: Float,
    onPaint: (Float, Float, Float) -> Unit,
) {
    val transform = imageTransform(viewportWidth, viewportHeight, bitmap.width, bitmap.height, zoom, pan)
    screenToSource(
        SourcePoint(position.x, position.y),
        transform,
        project.sourceWidth,
        project.sourceHeight,
    )?.let { point ->
        onPaint(point.x, point.y, brushRadiusPx / transform.width * project.sourceWidth)
    }
}

private enum class CanvasInteraction { MOVE, TAP, STROKE }

private val HoldRole.displayName: String
    get() = when (this) {
        HoldRole.START -> "Start"
        HoldRole.FINISH -> "Finish"
        HoldRole.REGULAR -> "Regular"
        HoldRole.FEET_ONLY -> "Feet"
    }

private fun contrastingContentColor(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF101010) else Color.White
