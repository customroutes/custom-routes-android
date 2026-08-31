package io.github.customroutes.app.ui

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.customroutes.app.data.AiPreferences
import io.github.customroutes.app.data.EditorHintPreferences
import io.github.customroutes.app.data.ProjectRepository
import io.github.customroutes.app.data.ProjectSummary
import io.github.customroutes.app.data.AppearancePreferences
import io.github.customroutes.app.data.ProjectThumbnailCache
import io.github.customroutes.app.data.ProjectThumbnailUiState
import io.github.customroutes.app.data.RouteImageRenderer
import io.github.customroutes.app.data.RoleColorPreferences
import io.github.customroutes.app.data.RouteExportCandidate
import io.github.customroutes.app.data.RouteExporter
import io.github.customroutes.app.data.ThumbnailSignature
import io.github.customroutes.app.data.thumbnailDimmingAlpha
import io.github.customroutes.app.data.thumbnailDecodeSize
import io.github.customroutes.app.domain.DEFAULT_ROLE_COLORS
import io.github.customroutes.app.domain.DEFAULT_APPEARANCE_SETTINGS
import io.github.customroutes.app.domain.AppearanceSettings
import io.github.customroutes.app.domain.EditorHistory
import io.github.customroutes.app.domain.HoldRole
import io.github.customroutes.app.domain.ManualDraftSession
import io.github.customroutes.app.domain.ManualHoldDraft
import io.github.customroutes.app.domain.MaskRaster
import io.github.customroutes.app.domain.MaskRegion
import io.github.customroutes.app.domain.RouteHold
import io.github.customroutes.app.domain.RouteProject
import io.github.customroutes.app.domain.ROLE_COLOR_CHOICES
import io.github.customroutes.app.domain.SourcePoint
import io.github.customroutes.app.domain.SourceRect
import io.github.customroutes.app.domain.alphaRaster
import io.github.customroutes.app.domain.canReuseDetailCrop
import io.github.customroutes.app.domain.outerContour
import io.github.customroutes.app.domain.segmentationBounds
import io.github.customroutes.app.domain.normalizeBorderWidthPercent
import io.github.customroutes.app.domain.normalizeExportDimmingPercent
import io.github.customroutes.app.ml.EmbeddingSlot
import io.github.customroutes.app.ml.EfficientSamSegmenter
import io.github.customroutes.app.ml.AiPreparationEligibility
import io.github.customroutes.app.ml.AiPreparationFailureLatch
import io.github.customroutes.app.ml.ForegroundPreparationOrder
import io.github.customroutes.app.ml.FullImagePreparationState
import io.github.customroutes.app.ml.HoldSegmenter
import io.github.customroutes.app.ml.MemoryCleanup
import io.github.customroutes.app.ml.MemoryPressure
import io.github.customroutes.app.ml.ModelManager
import io.github.customroutes.app.ml.ModelStatus
import io.github.customroutes.app.ml.PreparationCancellation
import io.github.customroutes.app.ml.SegmentationPrompt
import io.github.customroutes.app.ml.aiPreparationDelayMillis
import io.github.customroutes.app.ml.awaitPreparationAndOwnResources
import io.github.customroutes.app.ml.embeddingSlot
import io.github.customroutes.app.ml.foregroundPreparationOrder
import io.github.customroutes.app.ml.memoryCleanup
import io.github.customroutes.app.ml.ownAiResources
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private const val STROKE_PREVIEW_PUBLISH_INTERVAL_NANOS = 25_000_000L

enum class EditorMode { ADD, EDIT, MOVE }
enum class AddMethod { AI_TAP, MANUAL_PAINT }
enum class EditAction { SELECT, AI_INCLUDE, AI_EXCLUDE, PAINT, ERASE }
internal enum class StrokeTool { ADD_MANUAL, EDIT_PAINT, EDIT_ERASE }
enum class AiTool { TAP, INCLUDE, EXCLUDE }
data class PendingAiRequest(val tool: AiTool, val projectId: String?, val holdId: String?)
enum class DraftAction { PAINT, ERASE }
enum class BrushSize(val radiusDp: Float) { SMALL(8f), MEDIUM(16f), LARGE(28f) }
enum class PrivacyAction { DELETE_MODEL, DELETE_PROJECTS, DELETE_ALL_DATA }

internal val PrivacyAction.confirmationSteps: Int
    get() = if (this == PrivacyAction.DELETE_PROJECTS || this == PrivacyAction.DELETE_ALL_DATA) 2 else 1

internal fun defaultAddMethod(modelStatus: ModelStatus): AddMethod =
    if (modelStatus is ModelStatus.Ready) AddMethod.AI_TAP else AddMethod.MANUAL_PAINT

internal fun AppUiState.supersedePendingAiRequest(): AppUiState =
    if (pendingAiRequest != null) copy(pendingAiRequest = null) else this

internal fun AppUiState.supersedePendingAiWithStroke(tool: StrokeTool): AppUiState =
    when (tool) {
        StrokeTool.ADD_MANUAL -> copy(
            editorMode = EditorMode.ADD,
            addMethod = AddMethod.MANUAL_PAINT,
            pendingAiRequest = null,
        )
        StrokeTool.EDIT_PAINT -> copy(
            editorMode = EditorMode.EDIT,
            editAction = EditAction.PAINT,
            pendingAiRequest = null,
        )
        StrokeTool.EDIT_ERASE -> copy(
            editorMode = EditorMode.EDIT,
            editAction = EditAction.ERASE,
            pendingAiRequest = null,
        )
    }

internal fun strokePreviewDelayNanos(lastPublishNanos: Long, nowNanos: Long): Long =
    if (lastPublishNanos == 0L) 0L
    else (STROKE_PREVIEW_PUBLISH_INTERVAL_NANOS - (nowNanos - lastPublishNanos)).coerceAtLeast(0L)

internal fun AppUiState.completeModelDownload(): AppUiState {
    val request = pendingAiRequest
    val readyState = copy(
        modelStatus = ModelStatus.Ready,
        hasModelData = true,
        pendingAiRequest = null,
        message = "AI tools ready offline",
    )
    val sameProject = request?.projectId != null && project?.id == request.projectId
    val sameHold = sameProject && selectedHoldId != null && selectedHoldId == request?.holdId
    return when {
        request?.tool == AiTool.TAP && sameProject && manualDraft == null ->
            readyState.copy(addMethod = AddMethod.AI_TAP)
        request?.tool == AiTool.INCLUDE && sameHold && manualDraft == null -> readyState.copy(
            editorMode = EditorMode.EDIT,
            editAction = EditAction.AI_INCLUDE,
        )
        request?.tool == AiTool.EXCLUDE && sameHold && manualDraft == null -> readyState.copy(
            editorMode = EditorMode.EDIT,
            editAction = EditAction.AI_EXCLUDE,
        )
        else -> readyState
    }
}

data class PendingProjectDeletion(val id: String, val name: String?, val isOpen: Boolean)
data class StrokePreview(
    val holdId: String?,
    val role: HoldRole,
    val maskRegion: MaskRegion,
    val outline: List<SourcePoint> = emptyList(),
    val raster: MaskRaster? = null,
    val pointCount: Int = 0,
    val rejected: Boolean = false,
)

sealed interface ExportPreviewUiState {
    data object Loading : ExportPreviewUiState

    data class Ready(
        val bitmap: Bitmap,
        val displayName: String,
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
        val isSaving: Boolean = false,
    ) : ExportPreviewUiState

    data class Failed(val message: String) : ExportPreviewUiState
}

internal data class ExportCandidateKey(
    val projectId: String,
    val updatedAtEpochMillis: Long,
    val roleColors: Map<HoldRole, Int>,
    val appearanceSettings: AppearanceSettings,
)

internal fun exportCandidateKey(
    project: RouteProject,
    roleColors: Map<HoldRole, Int>,
    appearanceSettings: AppearanceSettings,
): ExportCandidateKey = ExportCandidateKey(
    projectId = project.id,
    updatedAtEpochMillis = project.updatedAtEpochMillis,
    roleColors = roleColors.toMap(),
    appearanceSettings = appearanceSettings,
)

data class AppUiState(
    val projects: List<ProjectSummary> = emptyList(),
    val storedProjectCount: Int = 0,
    val projectThumbnails: Map<String, ProjectThumbnailUiState> = emptyMap(),
    val project: RouteProject? = null,
    val bitmap: Bitmap? = null,
    val roleColors: Map<HoldRole, Int> = DEFAULT_ROLE_COLORS,
    val appearanceSettings: AppearanceSettings = DEFAULT_APPEARANCE_SETTINGS,
    val shouldShowRoleColorTip: Boolean = true,
    val improveAiDetailWhenZoomed: Boolean = true,
    val activeRole: HoldRole = HoldRole.REGULAR,
    val editorMode: EditorMode = EditorMode.ADD,
    val addMethod: AddMethod = AddMethod.AI_TAP,
    val editAction: EditAction = EditAction.SELECT,
    val draftAction: DraftAction = DraftAction.PAINT,
    val brushSize: BrushSize = BrushSize.MEDIUM,
    val manualDraft: ManualHoldDraft? = null,
    val strokePreview: StrokePreview? = null,
    val selectedHoldId: String? = null,
    val modelStatus: ModelStatus = ModelStatus.Checking,
    val hasModelData: Boolean = false,
    val pendingAiRequest: PendingAiRequest? = null,
    val busyMessage: String? = null,
    val isPreparingAi: Boolean = false,
    val message: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val exportPreview: ExportPreviewUiState? = null,
    val pendingProjectDeletion: PendingProjectDeletion? = null,
    val isDeletingProject: Boolean = false,
    val showPrivacyData: Boolean = false,
    val showSettings: Boolean = false,
    val pendingAppearanceReset: Boolean = false,
    val isResettingAppearance: Boolean = false,
    val pendingPrivacyAction: PrivacyAction? = null,
    val privacyConfirmationStep: Int = 0,
    val privacyConfirmationReady: Boolean = false,
    val isApplyingPrivacyAction: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProjectRepository(application)
    private val exporter = RouteExporter(application, repository)
    private val modelManager = ModelManager(application)
    private val aiPreferences = AiPreferences(application)
    private val editorHintPreferences = EditorHintPreferences(application)
    private val roleColorPreferences = RoleColorPreferences(application)
    private val appearancePreferences = AppearancePreferences(application)
    private val thumbnailCache = ProjectThumbnailCache(application)
    private val saveMutex = Mutex()
    private val segmentationMutex = Mutex()
    private val strokePreviewMutex = Mutex()
    private val thumbnailSemaphore = Semaphore(MAX_CONCURRENT_THUMBNAILS)
    private val promptsByHold = mutableMapOf<String, MutableList<SegmentationPrompt>>()
    private val strokePoints = mutableListOf<SourcePoint>()
    private var history: EditorHistory<RouteProject>? = null
    private var manualDraftSession: ManualDraftSession? = null
    private var segmenter: HoldSegmenter? = null
    @Volatile
    private var preparedProjectId: String? = null
    @Volatile
    private var preparedCrop: PreparedCrop? = null
    private var strokeStart: RouteProject? = null
    private var strokeRadius = 0f
    private var strokeValue = false
    private var strokePreviewBase: StrokePreview? = null
    private var strokePreviewJob: Job? = null
    private var strokePreviewLastPublishNanos = 0L
    private var strokeGeneration = 0L
    private var segmentationRequest = 0L
    private var addToolInteracted = false
    private var modelDownloadJob: Job? = null
    private var aiPreparationJob: Job? = null
    @Volatile
    private var aiPreparationStarted = false
    @Volatile
    private var aiPreparationGeneration = 0L
    @Volatile
    private var activePreparationCancellation: PreparationCancellation? = null
    private val aiPreparationFailures = AiPreparationFailureLatch()
    private var appInForeground = false
    private var latestProjectSave: Job? = null
    private var projectListRefreshJob: Job? = null
    private var exportPreviewJob: Job? = null
    private var exportCandidate: RouteExportCandidate? = null
    private var exportPreviewKey: ExportCandidateKey? = null
    private var exportPreviewGeneration = 0L
    @Volatile
    private var projectListRefreshGeneration = 0L
    private val saveGenerations = ConcurrentHashMap<String, Long>()
    private val thumbnailJobs = ConcurrentHashMap<ThumbnailJobKey, Job>()
    private val thumbnailJobsInFlight = ConcurrentHashMap<Job, ThumbnailJobKey>()
    private val visibleThumbnailIds = ConcurrentHashMap.newKeySet<String>()
    private val blockedThumbnailIds = ConcurrentHashMap.newKeySet<String>()

    private val _state = MutableStateFlow(
        AppUiState(
            roleColors = roleColorPreferences.load(),
            appearanceSettings = appearancePreferences.load(),
            shouldShowRoleColorTip = editorHintPreferences.shouldShowRoleColorTip(),
            improveAiDetailWhenZoomed = aiPreferences.loadImproveAiDetailWhenZoomed(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Triple(
                        repository.list(),
                        modelManager.status(),
                        repository.storedProjectCount() to modelManager.hasStoredData(),
                    )
                }
            }.onSuccess { (projects, modelStatus, storedData) ->
                _state.update { state ->
                    state.copy(
                        projects = projects,
                        storedProjectCount = storedData.first,
                        modelStatus = modelStatus,
                        hasModelData = storedData.second,
                        addMethod = if (state.project != null && !addToolInteracted) {
                            defaultAddMethod(modelStatus)
                        } else {
                            state.addMethod
                        },
                    )
                }
                if (modelStatus is ModelStatus.Ready && _state.value.project != null) {
                    scheduleAiPreparation(freshActivation = false)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        modelStatus = ModelStatus.Failed(error.message ?: "Model verification failed"),
                        message = "Local data could not be checked",
                    )
                }
            }
        }
    }

    fun onAppForegrounded() {
        appInForeground = true
        scheduleAiPreparation(freshActivation = false)
    }

    fun onAppBackgrounded() {
        appInForeground = false
        cancelSpeculativePreparation()
    }

    fun onMemoryPressure(level: Int) {
        val pressure = when {
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryPressure.CRITICAL
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryPressure.LOW
            else -> MemoryPressure.NORMAL
        }
        applyMemoryCleanup(memoryCleanup(pressure))
    }

    fun onLowMemory() {
        applyMemoryCleanup(MemoryCleanup.RUNTIME)
    }

    private fun applyMemoryCleanup(cleanup: MemoryCleanup) {
        when (cleanup) {
            MemoryCleanup.NONE -> return
            MemoryCleanup.EMBEDDINGS -> releaseAiResources(closeRuntime = false)
            MemoryCleanup.RUNTIME -> releaseAiResources(closeRuntime = true)
        }
        scheduleAiPreparation(freshActivation = false)
    }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            setBusy("Importing photo")
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.import(uri).let { it to repository.loadWorkingBitmap(it) }
                }
            }
                .onSuccess { (project, bitmap) -> activateProject(project, bitmap) }
                .onFailure { showError(it, "Photo import failed") }
            setBusy(null)
        }
    }

    fun openProject(id: String) {
        viewModelScope.launch {
            setBusy("Opening project")
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.load(id).let { it to repository.loadWorkingBitmap(it) }
                }
            }
                .onSuccess { (project, bitmap) -> activateProject(project, bitmap) }
                .onFailure { showError(it, "Project could not be opened") }
            setBusy(null)
        }
    }

    fun requestProjectThumbnail(id: String) {
        synchronized(thumbnailJobs) {
            if (blockedThumbnailIds.contains(id)) return
            visibleThumbnailIds += id
            requestProjectThumbnailInternal(id)
        }
    }

    private fun requestProjectThumbnailInternal(id: String) {
        val snapshot = _state.value
        val summary = snapshot.projects.firstOrNull { it.id == id } ?: run {
            visibleThumbnailIds -= id
            return
        }
        val signature = ThumbnailSignature.forProject(
            project = summary,
            roleColors = snapshot.roleColors,
            appearanceSettings = snapshot.appearanceSettings,
        )
        visibleThumbnailIds += id

        val existing = snapshot.projectThumbnails[id]
        if (existing?.bitmapSignature == signature && existing.bitmap != null) return
        _state.update { state ->
            val current = state.projectThumbnails[id]
            state.copy(
                projectThumbnails = state.projectThumbnails + (
                    id to ProjectThumbnailUiState(
                        signature = signature,
                        bitmap = current?.bitmap,
                        bitmapSignature = current?.bitmapSignature,
                    )
                ),
            )
        }
        scheduleProjectThumbnail(
            id = id,
            signature = signature,
            roleColors = snapshot.roleColors,
            appearanceSettings = snapshot.appearanceSettings,
        )
    }

    fun releaseProjectThumbnail(id: String) {
        synchronized(thumbnailJobs) {
            visibleThumbnailIds -= id
            thumbnailJobsInFlight.entries
                .filter { it.value.projectId == id }
                .forEach { entry -> entry.key.cancel() }
            thumbnailJobs.entries
                .filter { it.key.projectId == id && it.value.isCompleted }
                .forEach { entry -> thumbnailJobs.remove(entry.key, entry.value) }
        }
        _state.update { state ->
            if (id !in state.projectThumbnails) state
            else state.copy(projectThumbnails = state.projectThumbnails - id)
        }
    }

    private fun invalidateProjectListRefresh() {
        projectListRefreshGeneration++
        projectListRefreshJob?.cancel()
        projectListRefreshJob = null
    }

    private fun refreshProjectList(afterSave: Job? = null) {
        val generation = ++projectListRefreshGeneration
        projectListRefreshJob?.cancel()
        projectListRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            afterSave?.join()
            val projects = repository.list()
            val storedProjectCount = repository.storedProjectCount()
            val projectIds = projects.mapTo(mutableSetOf(), ProjectSummary::id)
            _state.update {
                if (generation != projectListRefreshGeneration) {
                    it
                } else {
                    it.copy(
                        projects = projects,
                        storedProjectCount = storedProjectCount,
                        projectThumbnails = it.projectThumbnails.filterKeys(projectIds::contains),
                    )
                }
            }
        }
    }

    private fun scheduleProjectThumbnail(
        id: String,
        signature: ThumbnailSignature,
        roleColors: Map<HoldRole, Int>,
        appearanceSettings: AppearanceSettings,
    ) {
        val key = ThumbnailJobKey(id, signature)
        synchronized(thumbnailJobs) {
            val obsoleteEntries = thumbnailJobs.entries
                .filter { it.key.projectId == id && it.key != key }
            obsoleteEntries.forEach { entry ->
                thumbnailJobs.remove(entry.key, entry.value)
                entry.value.cancel()
            }
            thumbnailJobs[key]?.let { existingJob ->
                if (!existingJob.isCompleted) {
                    existingJob.invokeOnCompletion {
                        synchronized(thumbnailJobs) {
                            if (visibleThumbnailIds.contains(id) && !blockedThumbnailIds.contains(id)) {
                                requestProjectThumbnailInternal(id)
                            }
                        }
                    }
                    return
                }
                thumbnailJobs.remove(key, existingJob)
            }

            val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                val runningJob = checkNotNull(coroutineContext[Job])
                try {
                    obsoleteEntries.map { it.value }.joinAll()
                    thumbnailSemaphore.withPermit {
                        ensureActive()
                        val project = repository.load(id)
                        if (project.updatedAtEpochMillis != signature.updatedAtEpochMillis) return@withPermit
                        val expectedSize = thumbnailDecodeSize(project.sourceWidth, project.sourceHeight)
                        val cached = thumbnailCache.read(id, expectedSize)
                        val cachedIsCurrent = cached?.signature == signature
                        if (cached != null) {
                            val retained = publishThumbnail(id, signature, cached.signature, cached.bitmap)
                            if (!retained) cached.bitmap.recycle()
                        }

                        if (!cachedIsCurrent) {
                            val output = repository.loadThumbnailBitmap(project)
                            var published = false
                            try {
                                withContext(Dispatchers.Default) {
                                    RouteImageRenderer.draw(
                                        bitmap = output,
                                        project = project,
                                        roleColors = roleColors,
                                        appearanceSettings = appearanceSettings,
                                        dimmingAlpha = thumbnailDimmingAlpha(),
                                        checkCancelled = { ensureActive() },
                                    )
                                }
                                ensureActive()
                                thumbnailCache.write(id, signature, output)
                                published = publishThumbnail(id, signature, signature, output)
                            } finally {
                                if (!published && !output.isRecycled) output.recycle()
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    clearFailedThumbnail(id, signature)
                } finally {
                    synchronized(thumbnailJobs) {
                        thumbnailJobsInFlight.remove(runningJob)
                        if (thumbnailJobs[key] == runningJob) {
                            thumbnailJobs.remove(key)
                        }
                    }
                }
            }
            thumbnailJobs[key] = job
            thumbnailJobsInFlight[job] = key
            job.invokeOnCompletion {
                synchronized(thumbnailJobs) {
                    thumbnailJobsInFlight.remove(job)
                    if (thumbnailJobs[key] == job) thumbnailJobs.remove(key)
                }
            }
            job.start()
        }
    }

    private fun publishThumbnail(
        id: String,
        signature: ThumbnailSignature,
        bitmapSignature: ThumbnailSignature,
        bitmap: Bitmap,
    ): Boolean {
        while (true) {
            if (!visibleThumbnailIds.contains(id)) return false
            val state = _state.value
            val current = state.projectThumbnails[id]
            if (!visibleThumbnailIds.contains(id) ||
                current?.signature != signature ||
                state.projects.none { it.id == id }
            ) {
                return false
            }
            val updated = state.copy(
                projectThumbnails = state.projectThumbnails + (
                    id to current.copy(bitmap = bitmap, bitmapSignature = bitmapSignature)
                ),
            )
            if (_state.compareAndSet(state, updated)) return true
        }
    }

    private fun clearFailedThumbnail(id: String, signature: ThumbnailSignature) {
        while (true) {
            val state = _state.value
            val current = state.projectThumbnails[id] ?: return
            if (current.signature != signature || current.bitmap == null) return
            val updated = state.copy(
                projectThumbnails = state.projectThumbnails + (
                    id to current.copy(bitmap = null, bitmapSignature = null)
                ),
            )
            if (_state.compareAndSet(state, updated)) return
        }
    }

    private suspend fun cancelThumbnailJobsAndWait(projectIds: Set<String>? = null) {
        val jobs = synchronized(thumbnailJobs) {
            thumbnailJobsInFlight.entries
                .filter { projectIds == null || it.value.projectId in projectIds }
                .toList()
                .also { entries ->
                    entries.forEach { entry -> entry.key.cancel() }
                }
                .map { it.key }
        }
        jobs.joinAll()
    }

    fun closeProject() {
        cancelStroke()
        if (!requireResolvedDraft()) return
        closeProjectNow()
    }

    private fun closeProjectNow(refreshProjects: Boolean = true) {
        clearExportPreview()
        releaseAiResources(closeRuntime = false)
        val saveJob = latestProjectSave
        history = null
        manualDraftSession = null
        promptsByHold.clear()
        aiPreparationFailures.clear()
        segmentationRequest++
        _state.update {
            it.copy(
                project = null,
                bitmap = null,
                manualDraft = null,
                selectedHoldId = null,
                canUndo = false,
                canRedo = false,
                busyMessage = null,
            )
        }
        if (refreshProjects) {
            refreshProjectList(afterSave = saveJob)
        }
    }

    fun confirmModelDownload() {
        if (_state.value.pendingAiRequest == null) return
        if (_state.value.modelStatus is ModelStatus.Downloading) return
        _state.update { it.copy(modelStatus = ModelStatus.Downloading(0f), hasModelData = true) }
        modelDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                modelManager.download(
                    onProgress = { fraction ->
                        ensureActive()
                        _state.update { it.copy(modelStatus = ModelStatus.Downloading(fraction.coerceIn(0f, 1f))) }
                    },
                    checkCancelled = { ensureActive() },
                )
                cancelSpeculativePreparation()
                segmentationMutex.withLock {
                    segmenter?.close()
                    segmenter = null
                    preparedProjectId = null
                    preparedCrop = null
                }
                withContext(Dispatchers.Main.immediate) {
                    aiPreparationFailures.clear()
                    _state.update(AppUiState::completeModelDownload)
                    scheduleAiPreparation(freshActivation = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        modelStatus = ModelStatus.Failed(error.message ?: "Download failed"),
                        hasModelData = modelManager.hasStoredData(),
                        pendingAiRequest = null,
                        message = "Model download failed",
                    )
                }
            } finally {
                modelDownloadJob = null
            }
        }
    }

    fun dismissModelDownload() {
        _state.update { it.copy(pendingAiRequest = null) }
    }

    fun setRole(role: HoldRole) {
        cancelStroke()
        val snapshot = _state.value
        val draft = manualDraftSession
        if (draft != null) {
            draft.setRole(role)
            _state.update { it.copy(activeRole = role) }
            updateDraftState()
            return
        }
        if (snapshot.editorMode == EditorMode.EDIT) {
            val project = snapshot.project ?: return
            val selectedId = snapshot.selectedHoldId ?: return
            val hold = project.holds.firstOrNull { it.id == selectedId } ?: return
            if (hold.role != role) commit(project.changeRole(selectedId, role, System.currentTimeMillis()))
            return
        }
        _state.update { it.copy(activeRole = role) }
    }

    fun setRoleColor(role: HoldRole, argb: Int) {
        if (argb !in ROLE_COLOR_CHOICES || _state.value.roleColors[role] == argb) return
        roleColorPreferences.set(role, argb)
        _state.update { state -> state.copy(roleColors = state.roleColors + (role to argb)) }
    }

    fun setAdjustSmallHolds(enabled: Boolean) {
        if (_state.value.appearanceSettings.adjustSmallHolds == enabled) return
        appearancePreferences.setAdjustSmallHolds(enabled)
        _state.update {
            it.copy(appearanceSettings = it.appearanceSettings.copy(adjustSmallHolds = enabled))
        }
    }

    fun setBorderWidthPercent(percent: Int) {
        val normalized = normalizeBorderWidthPercent(percent)
        if (_state.value.appearanceSettings.borderWidthPercent == normalized) return
        appearancePreferences.setBorderWidthPercent(normalized)
        _state.update {
            it.copy(appearanceSettings = it.appearanceSettings.copy(borderWidthPercent = normalized))
        }
    }

    fun setExportDimmingPercent(percent: Int) {
        val normalized = normalizeExportDimmingPercent(percent)
        if (_state.value.appearanceSettings.exportDimmingPercent == normalized) return
        appearancePreferences.setExportDimmingPercent(normalized)
        _state.update {
            it.copy(appearanceSettings = it.appearanceSettings.copy(exportDimmingPercent = normalized))
        }
    }

    fun setImproveAiDetailWhenZoomed(enabled: Boolean) {
        if (_state.value.improveAiDetailWhenZoomed == enabled) return
        aiPreferences.setImproveAiDetailWhenZoomed(enabled)
        _state.update { it.copy(improveAiDetailWhenZoomed = enabled) }
        if (!enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                segmentationMutex.withLock {
                    segmenter?.release(EmbeddingSlot.CROP)
                    preparedCrop = null
                }
            }
        }
    }

    fun setEditorMode(mode: EditorMode) {
        cancelStroke()
        if (_state.value.editorMode == mode) {
            _state.update(AppUiState::supersedePendingAiRequest)
            return
        }
        if (!requireResolvedDraft()) return
        invalidateSegmentation()
        _state.update {
            it.copy(
                editorMode = mode,
                editAction = if (mode == EditorMode.EDIT) EditAction.SELECT else it.editAction,
            ).supersedePendingAiRequest()
        }
    }

    fun setAddMethod(method: AddMethod) {
        cancelStroke()
        if (!requireResolvedDraft()) return
        if (method == AddMethod.AI_TAP && requestModelSetup(AiTool.TAP)) return
        addToolInteracted = true
        invalidateSegmentation()
        _state.update {
            it.copy(addMethod = method).let { state ->
                if (method == AddMethod.MANUAL_PAINT) state.supersedePendingAiRequest() else state
            }
        }
    }

    fun setEditAction(action: EditAction) {
        cancelStroke()
        if (_state.value.manualDraft != null) return
        val aiTool = when (action) {
            EditAction.AI_INCLUDE -> AiTool.INCLUDE
            EditAction.AI_EXCLUDE -> AiTool.EXCLUDE
            else -> null
        }
        if (aiTool != null && requestModelSetup(aiTool)) return
        invalidateSegmentation()
        _state.update {
            it.copy(editAction = action).let { state ->
                if (aiTool == null) state.supersedePendingAiRequest() else state
            }
        }
    }

    private fun requestModelSetup(tool: AiTool): Boolean = when (_state.value.modelStatus) {
        ModelStatus.Ready -> false
        ModelStatus.Checking, is ModelStatus.Downloading -> true
        ModelStatus.Missing, is ModelStatus.Failed -> {
            _state.update { state ->
                state.copy(
                    pendingAiRequest = PendingAiRequest(
                        tool = tool,
                        projectId = state.project?.id,
                        holdId = state.selectedHoldId.takeIf { tool != AiTool.TAP },
                    ),
                )
            }
            true
        }
    }

    fun setDraftAction(action: DraftAction) {
        cancelStroke()
        if (_state.value.manualDraft == null) return
        _state.update { it.copy(draftAction = action) }
    }

    fun setBrushSize(size: BrushSize) {
        cancelStroke()
        _state.update { it.copy(brushSize = size) }
    }

    fun renameProject(name: String) {
        val current = _state.value.project ?: return
        val renamed = current.rename(name, System.currentTimeMillis())
        if (renamed.name != current.name) commit(renamed)
    }

    fun onImageTap(x: Float, y: Float, zoom: Float, visibleBounds: SourceRect) {
        val snapshot = _state.value
        val project = snapshot.project ?: return
        val point = SourcePoint(x, y)
        if (!SourceRect.full(project.sourceWidth, project.sourceHeight).contains(point)) return
        when (snapshot.editorMode) {
            EditorMode.ADD -> if (snapshot.addMethod == AddMethod.AI_TAP) {
                assignOrSegment(project, point, zoom, visibleBounds)
            }
            EditorMode.EDIT -> when (snapshot.editAction) {
                EditAction.SELECT -> selectHold(project, x, y)
                EditAction.AI_INCLUDE -> refine(project, point, zoom, visibleBounds, positive = true)
                EditAction.AI_EXCLUDE -> refine(project, point, zoom, visibleBounds, positive = false)
                EditAction.PAINT, EditAction.ERASE -> Unit
            }
            EditorMode.MOVE -> Unit
        }
    }

    internal fun beginStroke(tool: StrokeTool) {
        cancelSpeculativePreparation()
        _state.update { it.supersedePendingAiWithStroke(tool) }
        val snapshot = _state.value
        strokeStart = snapshot.project
        strokePoints.clear()
        strokeRadius = 0f
        strokePreviewLastPublishNanos = 0L
        if (snapshot.editorMode == EditorMode.ADD) {
            addToolInteracted = true
        }
        strokeValue = when {
            snapshot.manualDraft != null -> snapshot.draftAction == DraftAction.PAINT
            snapshot.editorMode == EditorMode.ADD -> true
            else -> snapshot.editAction == EditAction.PAINT
        }
        strokePreviewBase = snapshot.manualDraft?.let { StrokePreview(null, it.role, it.maskRegion) }
            ?: snapshot.project?.holds
                ?.firstOrNull { it.id == snapshot.selectedHoldId }
                ?.takeIf {
                    snapshot.editorMode == EditorMode.EDIT &&
                        (snapshot.editAction == EditAction.PAINT || snapshot.editAction == EditAction.ERASE)
                }
                ?.let { StrokePreview(it.id, it.role, it.maskRegion) }
        strokeGeneration++
        strokePreviewJob?.cancel()
        strokePreviewJob = null
        if (snapshot.strokePreview != null) _state.update { it.copy(strokePreview = null) }
    }

    fun paintAt(x: Float, y: Float, radius: Float) {
        val snapshot = _state.value
        if (!acceptsStroke(snapshot)) return
        strokePoints += SourcePoint(x, y)
        strokeRadius = radius
        scheduleStrokePreview(snapshot)
    }

    fun endStroke() {
        try {
            val before = strokeStart ?: return
            strokeStart = null
            if (strokePoints.isEmpty()) {
                clearStrokePreview()
                return
            }
            val snapshot = _state.value
            val completedPreview = snapshot.strokePreview?.takeIf {
                it.pointCount == strokePoints.size && !it.rejected
            }
            if (snapshot.editorMode == EditorMode.ADD && snapshot.addMethod == AddMethod.MANUAL_PAINT) {
                applyManualStroke(before, completedPreview?.maskRegion)
                strokePoints.clear()
                clearStrokePreview()
                return
            }
            val selectedId = _state.value.selectedHoldId
            val hold = selectedId?.let { id -> before.holds.firstOrNull { it.id == id } }
            if (hold == null) {
                strokePoints.clear()
                clearStrokePreview()
                return
            }
            val editedMask = completedPreview
                ?.takeIf { it.holdId == hold.id }
                ?.maskRegion
                ?: hold.maskRegion.paintSourceCircles(
                    centers = strokePoints,
                    radius = strokeRadius,
                    value = strokeValue,
                    sourceWidth = before.sourceWidth,
                    sourceHeight = before.sourceHeight,
                )
            if (!editedMask.mask.hasForeground()) {
                strokePoints.clear()
                clearStrokePreview()
                _state.update { it.copy(message = "Use Delete hold to remove the complete hold") }
                return
            }
            val after = before.withHold(
                hold.copy(maskRegion = editedMask),
                System.currentTimeMillis(),
            )
            strokePoints.clear()
            clearStrokePreview()
            commit(after)
        } finally {
            scheduleAiPreparation(freshActivation = false)
        }
    }

    fun cancelStroke() {
        strokeStart = null
        strokePoints.clear()
        clearStrokePreview()
        scheduleAiPreparation(freshActivation = false)
    }

    fun doneManualDraft() {
        cancelStroke()
        val session = manualDraftSession ?: return
        if (!session.draft.canCommit) return
        val project = _state.value.project ?: return
        val hold = session.toHold(UUID.randomUUID().toString())
        manualDraftSession = null
        _state.update { it.copy(manualDraft = null, selectedHoldId = hold.id, draftAction = DraftAction.PAINT) }
        commit(project.withHold(hold, System.currentTimeMillis()))
        scheduleAiPreparation(freshActivation = false)
    }

    fun cancelManualDraft() {
        cancelStroke()
        if (manualDraftSession == null) return
        manualDraftSession = null
        _state.update {
            it.copy(
                manualDraft = null,
                selectedHoldId = null,
                draftAction = DraftAction.PAINT,
                canUndo = history?.canUndo == true,
                canRedo = history?.canRedo == true,
            )
        }
        scheduleAiPreparation(freshActivation = false)
    }

    fun removeSelectedHold() {
        val snapshot = _state.value
        val project = snapshot.project ?: return
        val selectedId = snapshot.selectedHoldId ?: return
        commit(project.removeHold(selectedId, System.currentTimeMillis()))
        promptsByHold.remove(selectedId)
        _state.update { it.copy(selectedHoldId = null) }
    }

    fun undo() {
        cancelStroke()
        manualDraftSession?.let {
            it.undo()
            updateDraftState()
            return
        }
        invalidateSegmentation()
        val value = history?.undo() ?: return
        updateHistoryState(value)
        persist(value)
    }

    fun redo() {
        cancelStroke()
        manualDraftSession?.let {
            it.redo()
            updateDraftState()
            return
        }
        invalidateSegmentation()
        val value = history?.redo() ?: return
        updateHistoryState(value)
        persist(value)
    }

    fun openExportPreview() {
        cancelStroke()
        if (!requireResolvedDraft()) return
        if (_state.value.project == null) return
        invalidateSegmentation()
        generateExportPreview(cancelSpeculativePreparation())
    }

    fun retryExportPreview() {
        if (_state.value.exportPreview == null) return
        generateExportPreview()
    }

    fun closeExportPreview() {
        if ((_state.value.exportPreview as? ExportPreviewUiState.Ready)?.isSaving == true) return
        clearExportPreview()
        scheduleAiPreparation(freshActivation = false)
    }

    fun saveExportPreview() {
        val snapshot = _state.value
        val ready = snapshot.exportPreview as? ExportPreviewUiState.Ready ?: return
        if (ready.isSaving) return
        val project = snapshot.project ?: return
        val key = exportCandidateKey(project, snapshot.roleColors, snapshot.appearanceSettings)
        val candidate = exportCandidate
        if (candidate == null || exportPreviewKey != key || !exporter.isUsable(candidate)) {
            generateExportPreview()
            return
        }
        _state.update { state ->
            val current = state.exportPreview as? ExportPreviewUiState.Ready
            if (current == ready) state.copy(exportPreview = current.copy(isSaving = true)) else state
        }
        if ((_state.value.exportPreview as? ExportPreviewUiState.Ready)?.isSaving != true) return
        val generation = ++exportPreviewGeneration
        exportPreviewJob?.cancel()
        exportPreviewJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = exporter.publish(candidate) { ensureActive() }
                ensureActive()
                val total = result.totalMillis / 1000f
                val load = result.loadMillis / 1000f
                val borders = result.borderMillis / 1000f
                val encode = result.encodeMillis / 1000f
                withContext(Dispatchers.Main.immediate) {
                    if (generation != exportPreviewGeneration || exportCandidate !== candidate) return@withContext
                    val cleanupError = runCatching { exporter.discard(candidate) }.exceptionOrNull()
                    exportCandidate = null
                    exportPreviewKey = null
                    _state.update { state ->
                        state.copy(
                            exportPreview = null,
                            showSettings = false,
                            message = String.format(
                                Locale.US,
                                "JPEG saved to Pictures/Custom Routes in %.1fs (image %.1f / borders %.1f / JPEG %.1f)",
                                total,
                                load,
                                borders,
                                encode,
                            ) + if (cleanupError == null) "" else " Temporary preview cleanup will be retried later.",
                        )
                    }
                    scheduleAiPreparation(freshActivation = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation == exportPreviewGeneration) {
                    _state.update { state ->
                        val current = state.exportPreview as? ExportPreviewUiState.Ready
                        state.copy(
                            exportPreview = current?.copy(isSaving = false),
                            message = error.message?.takeIf(String::isNotBlank) ?: "Export failed",
                        )
                    }
                }
            } finally {
                if (generation == exportPreviewGeneration) exportPreviewJob = null
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun markRoleColorTipShown() {
        editorHintPreferences.markRoleColorTipShown()
    }

    fun openPrivacyData() {
        if (_state.value.project != null) return
        _state.update { it.copy(showPrivacyData = true) }
    }

    fun openSettings() {
        cancelStroke()
        _state.update { it.copy(showSettings = true) }
    }

    fun closeSettings() {
        if (_state.value.isResettingAppearance) return
        _state.update { it.copy(showSettings = false, pendingAppearanceReset = false) }
        val snapshot = _state.value
        val project = snapshot.project
        if (snapshot.exportPreview != null && project != null) {
            val currentKey = exportCandidateKey(project, snapshot.roleColors, snapshot.appearanceSettings)
            if (currentKey != exportPreviewKey) generateExportPreview()
        }
    }

    fun requestAppearanceReset() {
        if (_state.value.isResettingAppearance) return
        _state.update { it.copy(pendingAppearanceReset = true) }
    }

    fun dismissAppearanceReset() {
        if (_state.value.isResettingAppearance) return
        _state.update { it.copy(pendingAppearanceReset = false) }
    }

    fun confirmAppearanceReset() {
        if (_state.value.isResettingAppearance || !_state.value.pendingAppearanceReset) return
        _state.update { it.copy(isResettingAppearance = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    appearancePreferences.reset()
                    roleColorPreferences.reset()
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        roleColors = DEFAULT_ROLE_COLORS,
                        appearanceSettings = DEFAULT_APPEARANCE_SETTINGS,
                        message = "Appearance settings reset",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(message = error.message ?: "Appearance settings could not be reset") }
            }
            _state.update { it.copy(isResettingAppearance = false, pendingAppearanceReset = false) }
        }
    }

    fun closePrivacyData() {
        if (_state.value.isApplyingPrivacyAction) return
        _state.update {
            it.copy(
                showPrivacyData = false,
                pendingPrivacyAction = null,
                privacyConfirmationStep = 0,
                privacyConfirmationReady = false,
            )
        }
    }

    fun requestPrivacyAction(action: PrivacyAction) {
        if (_state.value.isApplyingPrivacyAction) return
        _state.update {
            it.copy(
                pendingPrivacyAction = action,
                privacyConfirmationStep = 1,
                privacyConfirmationReady = true,
            )
        }
    }

    fun dismissPrivacyAction() {
        if (_state.value.isApplyingPrivacyAction) return
        _state.update {
            it.copy(
                pendingPrivacyAction = null,
                privacyConfirmationStep = 0,
                privacyConfirmationReady = false,
            )
        }
    }

    fun markPrivacyConfirmationShown(action: PrivacyAction, step: Int) {
        _state.update { state ->
            if (state.pendingPrivacyAction != action ||
                state.privacyConfirmationStep != step ||
                state.privacyConfirmationReady
            ) state
            else state.copy(privacyConfirmationReady = true)
        }
    }

    fun confirmPrivacyAction() {
        val snapshot = _state.value
        val action = snapshot.pendingPrivacyAction ?: return
        if (snapshot.isApplyingPrivacyAction) return
        if (snapshot.privacyConfirmationStep < action.confirmationSteps) {
            _state.update {
                it.copy(
                    privacyConfirmationStep = it.privacyConfirmationStep + 1,
                    privacyConfirmationReady = false,
                )
            }
            return
        }
        if (!snapshot.privacyConfirmationReady) return
        applyPrivacyAction(action)
    }

    private fun applyPrivacyAction(action: PrivacyAction) {
        if (action == PrivacyAction.DELETE_ALL_DATA || action == PrivacyAction.DELETE_PROJECTS) {
            invalidateProjectListRefresh()
        }
        if (action == PrivacyAction.DELETE_ALL_DATA || action == PrivacyAction.DELETE_PROJECTS) {
            synchronized(thumbnailJobs) {
                _state.value.projects.forEach { project -> blockedThumbnailIds += project.id }
                visibleThumbnailIds.clear()
            }
        }
        _state.update { it.copy(isApplyingPrivacyAction = true, privacyConfirmationReady = false) }
        viewModelScope.launch {
            if (action == PrivacyAction.DELETE_ALL_DATA) {
                runCatching { cancelModelDownloadAndWait() }
                invalidateSegmentation()
                cancelThumbnailJobsAndWait()
                runCatching {
                    withContext(Dispatchers.IO) {
                        segmentationMutex.withLock {
                            segmenter?.close()
                            segmenter = null
                            preparedProjectId = null
                            preparedCrop = null
                        }
                    }
                }
                runCatching { withContext(Dispatchers.IO) { thumbnailCache.deleteAll() } }
                val cleared = runCatching {
                    checkNotNull(getApplication<Application>().getSystemService(ActivityManager::class.java))
                        .clearApplicationUserData()
                }.getOrDefault(false)
                if (!cleared) {
                    synchronized(thumbnailJobs) { blockedThumbnailIds.clear() }
                    _state.update {
                        it.copy(
                            isApplyingPrivacyAction = false,
                            pendingPrivacyAction = null,
                            privacyConfirmationStep = 0,
                            privacyConfirmationReady = false,
                            message = "Local data could not be cleared",
                        )
                    }
                }
                return@launch
            }

            runCatching {
                when (action) {
                    PrivacyAction.DELETE_MODEL -> deleteModelData()
                    PrivacyAction.DELETE_PROJECTS -> withContext(Dispatchers.IO) {
                        cancelThumbnailJobsAndWait()
                        saveMutex.withLock {
                            repository.deleteAll()
                            saveGenerations.clear()
                        }
                    }
                    PrivacyAction.DELETE_ALL_DATA -> Unit
                }
            }.onSuccess {
                if (action == PrivacyAction.DELETE_PROJECTS) {
                    synchronized(thumbnailJobs) { blockedThumbnailIds.clear() }
                }
                _state.update { state ->
                    when (action) {
                        PrivacyAction.DELETE_MODEL -> state.copy(
                            modelStatus = ModelStatus.Missing,
                            hasModelData = false,
                            addMethod = AddMethod.MANUAL_PAINT,
                            pendingAiRequest = null,
                            message = "AI model deleted",
                        )
                        PrivacyAction.DELETE_PROJECTS -> state.copy(
                            projects = emptyList(),
                            storedProjectCount = 0,
                            projectThumbnails = emptyMap(),
                            message = "All local projects deleted",
                        )
                        PrivacyAction.DELETE_ALL_DATA -> state
                    }
                }
            }.onFailure { error ->
                if (action == PrivacyAction.DELETE_PROJECTS) {
                    synchronized(thumbnailJobs) { blockedThumbnailIds.clear() }
                }
                _state.update { it.copy(message = error.message ?: "Local data could not be changed") }
            }
            _state.update {
                it.copy(
                    isApplyingPrivacyAction = false,
                    pendingPrivacyAction = null,
                    privacyConfirmationStep = 0,
                    privacyConfirmationReady = false,
                )
            }
        }
    }

    private suspend fun deleteModelData() {
        cancelModelDownloadAndWait()
        invalidateSegmentation()
        cancelSpeculativePreparation()
        preparedProjectId = null
        preparedCrop = null
        withContext(Dispatchers.IO) {
            ownAiResources(segmentationMutex) {
                segmenter?.close()
                segmenter = null
                preparedProjectId = null
                preparedCrop = null
                modelManager.delete()
            }
        }
    }

    private suspend fun cancelModelDownloadAndWait() {
        val job = modelDownloadJob
        job?.cancel()
        modelManager.cancelDownload()
        job?.join()
    }

    fun requestProjectDeletion(id: String) {
        cancelStroke()
        val snapshot = _state.value
        val open = snapshot.project?.takeIf { it.id == id }
        val summary = snapshot.projects.firstOrNull { it.id == id }
        if (open == null && summary == null) return
        _state.update {
            it.copy(
                pendingProjectDeletion = PendingProjectDeletion(
                    id = id,
                    name = open?.name ?: summary?.name,
                    isOpen = open != null,
                ),
            )
        }
    }

    fun dismissProjectDeletion() {
        _state.update { it.copy(pendingProjectDeletion = null) }
    }

    fun confirmProjectDeletion() {
        cancelStroke()
        if (_state.value.isDeletingProject) return
        val pending = _state.value.pendingProjectDeletion ?: return
        synchronized(thumbnailJobs) {
            blockedThumbnailIds += pending.id
            visibleThumbnailIds -= pending.id
        }
        invalidateProjectListRefresh()
        invalidateSegmentation()
        _state.update { it.copy(isDeletingProject = true) }
        viewModelScope.launch {
            setBusy("Deleting local project")
            runCatching {
                cancelThumbnailJobsAndWait(setOf(pending.id))
                withContext(Dispatchers.IO) {
                    saveMutex.withLock {
                        repository.delete(pending.id)
                        saveGenerations.merge(pending.id, 1L, Long::plus)
                    }
                    repository.list() to repository.storedProjectCount()
                }
            }.onSuccess { (projects, storedProjectCount) ->
                synchronized(thumbnailJobs) { blockedThumbnailIds.remove(pending.id) }
                if (pending.isOpen) closeProjectNow(refreshProjects = false)
                _state.update {
                    it.copy(
                        projects = projects,
                        storedProjectCount = storedProjectCount,
                        projectThumbnails = it.projectThumbnails - pending.id,
                        pendingProjectDeletion = null,
                    )
                }
            }.onFailure {
                synchronized(thumbnailJobs) {
                    blockedThumbnailIds.remove(pending.id)
                    if (!pending.isOpen) {
                        visibleThumbnailIds += pending.id
                        requestProjectThumbnailInternal(pending.id)
                    }
                }
                showError(it, "Project could not be deleted")
            }
            _state.update { it.copy(isDeletingProject = false) }
            setBusy(null)
        }
    }

    private fun activateProject(project: RouteProject, bitmap: Bitmap) {
        clearExportPreview()
        releaseAiResources(closeRuntime = false)
        history = EditorHistory(project)
        manualDraftSession = null
        promptsByHold.clear()
        aiPreparationFailures.clear()
        addToolInteracted = false
        _state.update {
            it.copy(
                project = project,
                bitmap = bitmap,
                activeRole = HoldRole.REGULAR,
                editorMode = EditorMode.ADD,
                addMethod = defaultAddMethod(_state.value.modelStatus),
                editAction = EditAction.SELECT,
                draftAction = DraftAction.PAINT,
                brushSize = BrushSize.MEDIUM,
                manualDraft = null,
                selectedHoldId = null,
                canUndo = false,
                canRedo = false,
            )
        }
        scheduleAiPreparation(freshActivation = true)
    }

    private fun selectHold(project: RouteProject, x: Float, y: Float) {
        val point = SourcePoint(x, y)
        val selected = project.holdAt(point)
        _state.update { it.copy(selectedHoldId = selected?.id) }
    }

    private fun assignOrSegment(project: RouteProject, point: SourcePoint, zoom: Float, visibleBounds: SourceRect) {
        val existing = project.holdAt(point)
        if (existing != null) {
            invalidateSegmentation()
            _state.update {
                it.copy(
                    editorMode = EditorMode.EDIT,
                    editAction = EditAction.SELECT,
                    selectedHoldId = existing.id,
                )
            }
            return
        }
        if (_state.value.modelStatus !is ModelStatus.Ready) {
            _state.update { it.copy(message = "Download the segmentation model first") }
            return
        }
        val holdId = UUID.randomUUID().toString()
        val prompts = mutableListOf(SegmentationPrompt(point.x, point.y, true))
        promptsByHold[holdId] = prompts
        runSegmentation(project, holdId, _state.value.activeRole, prompts, zoom, visibleBounds, null)
    }

    private fun refine(
        project: RouteProject,
        point: SourcePoint,
        zoom: Float,
        visibleBounds: SourceRect,
        positive: Boolean,
    ) {
        if (_state.value.modelStatus !is ModelStatus.Ready) {
            _state.update { it.copy(message = "Download the segmentation model first") }
            return
        }
        val selectedId = _state.value.selectedHoldId
        if (selectedId == null) {
            _state.update { it.copy(message = "Select a hold before refining its mask") }
            return
        }
        val hold = project.holds.firstOrNull { it.id == selectedId } ?: return
        val prompts = promptsByHold.getOrPut(selectedId) { mutableListOf() }
        if (prompts.isEmpty()) {
            hold.maskRegion.centroid()?.let { center ->
                prompts += SegmentationPrompt(center.x, center.y, true)
            }
        }
        if (!positive && prompts.none { it.positive }) {
            _state.update { it.copy(message = "This empty mask needs a positive point first") }
            return
        }
        if (prompts.size == MAX_PROMPTS) {
            prompts.removeAt(if (prompts.first().positive) 1.coerceAtMost(prompts.lastIndex) else 0)
        }
        prompts += SegmentationPrompt(point.x, point.y, positive)
        runSegmentation(project, selectedId, hold.role, prompts, zoom, visibleBounds, hold.maskRegion)
    }

    private fun runSegmentation(
        project: RouteProject,
        holdId: String,
        role: HoldRole,
        prompts: List<SegmentationPrompt>,
        zoom: Float,
        visibleBounds: SourceRect,
        existingMask: MaskRegion?,
    ) {
        val snapshot = _state.value
        val bitmap = snapshot.bitmap ?: return
        val latestPrompt = prompts.lastOrNull() ?: return
        val tap = SourcePoint(latestPrompt.x, latestPrompt.y)
        val slot = embeddingSlot(snapshot.improveAiDetailWhenZoomed, zoom)
        val requestedBounds = segmentationBounds(
            if (slot == EmbeddingSlot.CROP) zoom else 1f,
            tap,
            visibleBounds,
            project.sourceWidth,
            project.sourceHeight,
        )
        val reusableCrop = preparedCrop?.takeIf {
            slot == EmbeddingSlot.CROP && it.canReuse(project, requestedBounds, tap)
        }
        val plannedBounds = reusableCrop?.sourceBounds ?: requestedBounds
        val promptSnapshot = prompts.toList()
        val hasPositivePrompt = promptSnapshot.any {
            it.positive && plannedBounds.contains(SourcePoint(it.x, it.y))
        }
        if (!hasPositivePrompt && existingMask?.foregroundPointInside(plannedBounds) == null) {
            _state.update { it.copy(message = "This detailed area needs a positive point on the hold") }
            return
        }
        val preparationState = fullImagePreparationState(project.id)
        val preparationOrder = foregroundPreparationOrder(preparationState, slot)
        val preparationToAwait = aiPreparationJob.takeIf {
            preparationState == FullImagePreparationState.RUNNING
        }
        if (preparationState == FullImagePreparationState.SCHEDULED) {
            cancelSpeculativePreparation()
        }
        val request = ++segmentationRequest
        viewModelScope.launch {
            val isPrepared = if (slot == EmbeddingSlot.FULL_IMAGE) {
                preparedProjectId == project.id
            } else {
                preparedCrop?.canReuse(project, requestedBounds, tap) == true
            }
            setBusy(
                if (isPrepared) "Refining hold"
                else if (
                    preparationOrder == ForegroundPreparationOrder.WAIT_FOR_FULL_IMAGE &&
                    slot == EmbeddingSlot.CROP
                ) {
                    "Preparing full image, then image detail"
                }
                else if (slot == EmbeddingSlot.CROP) "Preparing image detail"
                else "Preparing image",
            )
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    awaitPreparationAndOwnResources(preparationToAwait, segmentationMutex) {
                        aiPreparationFailures.foregroundTakesOwnership(project.id)
                        if (request != segmentationRequest) throw CancellationException("Segmentation superseded")
                        val activeSegmenter = segmenter ?: EfficientSamSegmenter(modelManager.files()).also {
                            segmenter = it
                        }
                        if (slot == EmbeddingSlot.FULL_IMAGE && preparedProjectId != project.id) {
                            activeSegmenter.prepare(slot, bitmap)
                            if (request != segmentationRequest || _state.value.project?.id != project.id) {
                                activeSegmenter.release(slot)
                                throw CancellationException("Segmentation superseded")
                            }
                            preparedProjectId = project.id
                        } else if (
                            slot == EmbeddingSlot.CROP &&
                            preparedCrop?.canReuse(project, requestedBounds, tap) != true
                        ) {
                            repository.loadCropBitmap(project, requestedBounds).let { crop ->
                                try {
                                    activeSegmenter.prepare(slot, crop.bitmap)
                                    if (request != segmentationRequest || _state.value.project?.id != project.id) {
                                        activeSegmenter.release(slot)
                                        throw CancellationException("Segmentation superseded")
                                    }
                                    preparedCrop = PreparedCrop(
                                        projectId = project.id,
                                        sourceBounds = crop.sourceBounds,
                                        width = crop.bitmap.width,
                                        height = crop.bitmap.height,
                                    )
                                } finally {
                                    crop.bitmap.recycle()
                                }
                            }
                        }
                        if (request != segmentationRequest) throw CancellationException("Segmentation superseded")
                        val activeCrop = if (slot == EmbeddingSlot.CROP) checkNotNull(preparedCrop) else null
                        val inferenceBounds = activeCrop?.sourceBounds ?: requestedBounds
                        val preparedWidth = activeCrop?.width ?: bitmap.width
                        val preparedHeight = activeCrop?.height ?: bitmap.height
                        val effectivePrompts = promptSnapshot
                            .filter { inferenceBounds.contains(SourcePoint(it.x, it.y)) }
                            .toMutableList()
                        if (effectivePrompts.none { it.positive }) {
                            existingMask?.foregroundPointInside(inferenceBounds)?.let { seed ->
                                effectivePrompts.add(0, SegmentationPrompt(seed.x, seed.y, true))
                            }
                        }
                        check(effectivePrompts.any { it.positive }) { "The decoded crop does not contain a positive point" }
                        val localPrompts = effectivePrompts.map { prompt ->
                            SegmentationPrompt(
                                x = (prompt.x - inferenceBounds.left) / inferenceBounds.width * preparedWidth,
                                y = (prompt.y - inferenceBounds.top) / inferenceBounds.height * preparedHeight,
                                positive = prompt.positive,
                            )
                        }
                        MaskRegion(activeSegmenter.segment(slot, localPrompts), inferenceBounds)
                    }
                }
            }
            if (request == segmentationRequest) {
                result.onSuccess { inferred ->
                    val latest = _state.value.project?.takeIf { it.id == project.id } ?: return@onSuccess
                    val maskRegion = existingMask?.replaceInside(inferred) ?: inferred.trimmed()
                    if (!maskRegion.mask.hasForeground()) {
                        _state.update { it.copy(message = "AI returned an empty hold mask") }
                        return@onSuccess
                    }
                    commit(
                        latest.withHold(
                            RouteHold(holdId, role, maskRegion),
                            System.currentTimeMillis(),
                        ),
                    )
                    _state.update { it.copy(selectedHoldId = holdId) }
                }.onFailure {
                    if (it !is CancellationException) aiPreparationFailures.recordFailure(project.id)
                    showError(it, "Segmentation failed")
                }
                setBusy(null)
                scheduleAiPreparation(freshActivation = false)
            }
        }
    }

    private fun commit(project: RouteProject) {
        invalidateSegmentation()
        history?.apply(project)
        updateHistoryState(project)
        persist(project)
    }

    private fun updateHistoryState(project: RouteProject) {
        _state.update {
            val selected = it.selectedHoldId?.takeIf { id -> project.holds.any { hold -> hold.id == id } }
            it.copy(
                project = project,
                selectedHoldId = selected,
                canUndo = history?.canUndo == true,
                canRedo = history?.canRedo == true,
            )
        }
    }

    private fun persist(project: RouteProject) {
        val generation = checkNotNull(saveGenerations.merge(project.id, 1L, Long::plus))
        latestProjectSave = viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                if (generation != saveGenerations[project.id]) return@withLock
                runCatching { repository.save(project) }
                    .onFailure { showError(it, "Autosave failed") }
            }
        }
    }

    private fun fullImagePreparationState(projectId: String): FullImagePreparationState = when {
        preparedProjectId == projectId -> FullImagePreparationState.READY
        aiPreparationJob != null && aiPreparationStarted -> FullImagePreparationState.RUNNING
        aiPreparationJob != null -> FullImagePreparationState.SCHEDULED
        else -> FullImagePreparationState.ABSENT
    }

    private fun scheduleAiPreparation(freshActivation: Boolean) {
        val snapshot = _state.value
        val project = snapshot.project ?: return
        val bitmap = snapshot.bitmap ?: return
        if (
            preparedProjectId == project.id ||
            aiPreparationJob != null ||
            !aiPreparationFailures.allowsSpeculation(project.id)
        ) {
            return
        }
        if (!aiPreparationEligibility(snapshot, includeBusyState = false).canPrepare) return

        val generation = ++aiPreparationGeneration
        aiPreparationJob = viewModelScope.launch {
            try {
                delay(aiPreparationDelayMillis(freshActivation))
                if (generation != aiPreparationGeneration) return@launch
                val latest = _state.value
                if (
                    latest.project?.id != project.id ||
                    latest.bitmap !== bitmap ||
                    !aiPreparationEligibility(latest, includeBusyState = true).canPrepare
                ) {
                    return@launch
                }

                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        segmentationMutex.withLock {
                            coroutineContext.ensureActive()
                            if (generation != aiPreparationGeneration) return@withLock
                            val cancellation = withContext(Dispatchers.Main.immediate) {
                                if (generation != aiPreparationGeneration) {
                                    throw CancellationException("AI preparation superseded")
                                }
                                PreparationCancellation().also {
                                    activePreparationCancellation = it
                                    aiPreparationStarted = true
                                    _state.update { state -> state.copy(isPreparingAi = true) }
                                }
                            }
                            coroutineContext.ensureActive()
                            val activeSegmenter = segmenter ?: EfficientSamSegmenter(modelManager.files()).also {
                                segmenter = it
                            }
                            if (preparedProjectId != project.id) {
                                activeSegmenter.prepare(EmbeddingSlot.FULL_IMAGE, bitmap, cancellation)
                                if (
                                    generation == aiPreparationGeneration &&
                                    _state.value.project?.id == project.id
                                ) {
                                    preparedProjectId = project.id
                                } else {
                                    activeSegmenter.release(EmbeddingSlot.FULL_IMAGE)
                                }
                            }
                        }
                    }
                }
                if (
                    result.isFailure &&
                    result.exceptionOrNull() !is CancellationException &&
                    generation == aiPreparationGeneration
                ) {
                    aiPreparationFailures.recordFailure(project.id)
                }
            } finally {
                if (generation == aiPreparationGeneration) {
                    activePreparationCancellation = null
                    aiPreparationStarted = false
                    aiPreparationJob = null
                    _state.update { it.copy(isPreparingAi = false) }
                }
            }
        }
    }

    private fun aiPreparationEligibility(
        state: AppUiState,
        includeBusyState: Boolean,
    ): AiPreparationEligibility = AiPreparationEligibility(
        projectOpen = state.project != null && state.bitmap != null,
        modelReady = state.modelStatus is ModelStatus.Ready,
        appForeground = appInForeground,
        manualDraftOpen = state.manualDraft != null,
        exportPreviewOpen = state.exportPreview != null,
        foregroundWorkActive = strokeStart != null || (includeBusyState && state.busyMessage != null),
    )

    private fun cancelSpeculativePreparation(): Job? {
        val job = aiPreparationJob
        aiPreparationGeneration++
        activePreparationCancellation?.cancel()
        activePreparationCancellation = null
        job?.cancel()
        aiPreparationJob = null
        aiPreparationStarted = false
        _state.update { it.copy(isPreparingAi = false) }
        return job
    }

    private fun releaseAiResources(closeRuntime: Boolean) {
        cancelSpeculativePreparation()
        preparedProjectId = null
        preparedCrop = null
        viewModelScope.launch(Dispatchers.IO) {
            ownAiResources(segmentationMutex) {
                if (closeRuntime) {
                    segmenter?.close()
                    segmenter = null
                    preparedProjectId = null
                    preparedCrop = null
                } else {
                    if (preparedProjectId == null) segmenter?.release(EmbeddingSlot.FULL_IMAGE)
                    if (preparedCrop == null) segmenter?.release(EmbeddingSlot.CROP)
                }
            }
        }
    }

    private fun generateExportPreview(aiPreparationToStop: Job? = null) {
        val snapshot = _state.value
        val project = snapshot.project ?: return
        val key = exportCandidateKey(project, snapshot.roleColors, snapshot.appearanceSettings)
        exportPreviewJob?.cancel()
        val cleanupError = exportCandidate?.let { candidate ->
            runCatching { exporter.discard(candidate) }.exceptionOrNull()
        }
        exportCandidate = null
        exportPreviewKey = key
        val generation = ++exportPreviewGeneration
        _state.update {
            it.copy(
                exportPreview = ExportPreviewUiState.Loading,
                message = cleanupError?.let { "Temporary export preview cleanup will be retried later" } ?: it.message,
            )
        }
        exportPreviewJob = viewModelScope.launch(Dispatchers.IO) {
            var candidate: RouteExportCandidate? = null
            var bitmap: Bitmap? = null
            try {
                aiPreparationToStop?.join()
                ensureActive()
                candidate = exporter.generateCandidate(
                    project = project,
                    roleColors = key.roleColors,
                    appearanceSettings = key.appearanceSettings,
                    checkCancelled = { ensureActive() },
                )
                bitmap = exporter.decodeCandidate(candidate)
                ensureActive()
                val generated = checkNotNull(candidate)
                val decoded = checkNotNull(bitmap)
                val accepted = withContext(Dispatchers.Main.immediate) {
                    val latest = _state.value
                    val latestProject = latest.project
                    val latestKey = latestProject?.let {
                        exportCandidateKey(it, latest.roleColors, latest.appearanceSettings)
                    }
                    if (
                        generation != exportPreviewGeneration ||
                        latest.exportPreview == null ||
                        latestKey != key
                    ) {
                        false
                    } else {
                        exportCandidate = generated
                        _state.update {
                            it.copy(
                                exportPreview = ExportPreviewUiState.Ready(
                                    bitmap = decoded,
                                    displayName = generated.displayName,
                                    width = generated.width,
                                    height = generated.height,
                                    sizeBytes = generated.sizeBytes,
                                ),
                            )
                        }
                        true
                    }
                }
                if (accepted) {
                    candidate = null
                    bitmap = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation == exportPreviewGeneration && _state.value.exportPreview != null) {
                    _state.update {
                        it.copy(
                            exportPreview = ExportPreviewUiState.Failed(
                                error.message?.takeIf(String::isNotBlank) ?: "Export preview could not be created",
                            ),
                        )
                    }
                }
            } finally {
                candidate?.let { stale ->
                    runCatching { exporter.discard(stale) }
                        .onFailure {
                            if (generation == exportPreviewGeneration) {
                                _state.update { state ->
                                    state.copy(message = "Temporary export preview cleanup will be retried later")
                                }
                            }
                        }
                }
                bitmap?.recycle()
                if (generation == exportPreviewGeneration) exportPreviewJob = null
            }
        }
    }

    private fun clearExportPreview() {
        exportPreviewGeneration++
        exportPreviewJob?.cancel()
        exportPreviewJob = null
        val cleanupError = exportCandidate?.let { candidate ->
            runCatching { exporter.discard(candidate) }.exceptionOrNull()
        }
        exportCandidate = null
        exportPreviewKey = null
        _state.update {
            if (it.exportPreview == null && !it.showSettings) it
            else it.copy(
                exportPreview = null,
                showSettings = false,
                message = cleanupError?.let { "Temporary export preview cleanup will be retried later" } ?: it.message,
            )
        }
    }

    private fun setBusy(message: String?) {
        _state.update { it.copy(busyMessage = message) }
    }

    private fun showError(error: Throwable, fallback: String) {
        _state.update { it.copy(message = error.message?.takeIf(String::isNotBlank) ?: fallback) }
    }

    private fun invalidateSegmentation() {
        segmentationRequest++
        _state.update { it.copy(busyMessage = null) }
    }

    private fun acceptsStroke(state: AppUiState): Boolean = when {
        state.manualDraft != null -> true
        state.editorMode == EditorMode.ADD -> state.addMethod == AddMethod.MANUAL_PAINT
        state.editorMode == EditorMode.EDIT ->
            state.selectedHoldId != null && (state.editAction == EditAction.PAINT || state.editAction == EditAction.ERASE)
        else -> false
    }

    private fun applyManualStroke(project: RouteProject, previewMask: MaskRegion?) {
        var session = manualDraftSession
        if (session == null) {
            val firstPoint = strokePoints.first()
            val existing = project.holdAt(firstPoint)
            if (existing != null) {
                _state.update {
                    it.copy(
                        editorMode = EditorMode.EDIT,
                        editAction = EditAction.SELECT,
                        selectedHoldId = existing.id,
                    )
                }
                return
            }
            session = ManualDraftSession.start(
                role = _state.value.activeRole,
                firstPoint = firstPoint,
                sourceWidth = project.sourceWidth,
                sourceHeight = project.sourceHeight,
            ).also { manualDraftSession = it }
            _state.update { it.copy(selectedHoldId = null) }
        }
        if (previewMask != null) {
            session.applyMask(previewMask)
        } else {
            session.applyStroke(strokePoints, strokeRadius, strokeValue)
        }
        updateDraftState()
    }

    private fun updateDraftState() {
        val session = manualDraftSession ?: return
        _state.update {
            it.copy(
                manualDraft = session.draft,
                canUndo = session.canUndo,
                canRedo = session.canRedo,
            )
        }
    }

    private fun requireResolvedDraft(): Boolean {
        if (manualDraftSession == null) return true
        _state.update { it.copy(message = "Finish or cancel the manual hold first") }
        return false
    }

    private fun scheduleStrokePreview(state: AppUiState) {
        val project = strokeStart ?: return
        var base = strokePreviewBase
        if (base == null && state.editorMode == EditorMode.ADD && state.addMethod == AddMethod.MANUAL_PAINT) {
            val firstPoint = strokePoints.firstOrNull() ?: return
            if (project.holdAt(firstPoint) != null) return
            val emptyDraft = ManualDraftSession.start(
                role = state.activeRole,
                firstPoint = firstPoint,
                sourceWidth = project.sourceWidth,
                sourceHeight = project.sourceHeight,
            ).draft
            base = StrokePreview(null, emptyDraft.role, emptyDraft.maskRegion).also { strokePreviewBase = it }
        }
        base ?: return
        if (strokePreviewJob?.isActive == true) return
        val generation = strokeGeneration
        val radius = strokeRadius
        val paint = strokeValue
        strokePreviewJob = viewModelScope.launch {
            try {
                val previewBase = checkNotNull(strokePreviewBase)
                var workingMask = previewBase.maskRegion
                var processedPoints = 0
                var lastPublishNanos = strokePreviewLastPublishNanos
                while (strokeStart != null && generation == strokeGeneration) {
                    val pointCount = strokePoints.size
                    if (pointCount == processedPoints) break
                    val start = (processedPoints - 1).coerceAtLeast(0)
                    val pendingPoints = strokePoints.subList(start, pointCount).toList()
                    workingMask = withContext(Dispatchers.Default) {
                        strokePreviewMutex.withLock {
                            workingMask.paintSourceCircles(
                                centers = pendingPoints,
                                radius = radius,
                                value = paint,
                                sourceWidth = project.sourceWidth,
                                sourceHeight = project.sourceHeight,
                                checkCancelled = { ensureActive() },
                            )
                        }
                    }
                    if (strokeStart == null || generation != strokeGeneration) return@launch
                    processedPoints = pointCount
                    val remaining = strokePreviewDelayNanos(lastPublishNanos, System.nanoTime())
                    if (remaining > 0L) {
                        delay((remaining + 999_999L) / 1_000_000L)
                    }
                    lastPublishNanos = System.nanoTime()
                    strokePreviewLastPublishNanos = lastPublishNanos
                    val preview = withContext(Dispatchers.Default) {
                        strokePreviewMutex.withLock {
                            val rejected = previewBase.holdId != null && !paint && !workingMask.mask.hasForeground()
                            val displayMask = if (rejected) previewBase.maskRegion else workingMask
                            previewBase.copy(
                                maskRegion = displayMask,
                                outline = displayMask.outerContour { ensureActive() },
                                raster = displayMask.alphaRaster { ensureActive() },
                                pointCount = pointCount,
                                rejected = rejected,
                            )
                        }
                    }
                    if (strokeStart == null || generation != strokeGeneration) return@launch
                    _state.update { it.copy(strokePreview = preview) }
                }
            } finally {
                if (generation == strokeGeneration) strokePreviewJob = null
            }
        }
    }

    private fun clearStrokePreview() {
        strokeGeneration++
        strokePreviewJob?.cancel()
        strokePreviewJob = null
        strokePreviewBase = null
        if (_state.value.strokePreview != null) _state.update { it.copy(strokePreview = null) }
    }

    override fun onCleared() {
        cancelSpeculativePreparation()
        exportPreviewGeneration++
        exportPreviewJob?.cancel()
        (_state.value.exportPreview as? ExportPreviewUiState.Ready)?.bitmap?.let { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        exportCandidate?.let { runCatching { exporter.discard(it) } }
        if (segmentationMutex.tryLock()) {
            try {
                segmenter?.close()
                segmenter = null
            } finally {
                segmentationMutex.unlock()
            }
        } else {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                segmentationMutex.withLock {
                    segmenter?.close()
                    segmenter = null
                }
            }
        }
        super.onCleared()
    }

    companion object {
        private const val MAX_PROMPTS = 6
        private const val MAX_CONCURRENT_THUMBNAILS = 2
    }

    private data class ThumbnailJobKey(
        val projectId: String,
        val signature: ThumbnailSignature,
    )

    private data class PreparedCrop(
        val projectId: String,
        val sourceBounds: SourceRect,
        val width: Int,
        val height: Int,
    ) {
        fun canReuse(project: RouteProject, requestedBounds: SourceRect, tap: SourcePoint): Boolean =
            projectId == project.id && canReuseDetailCrop(
                preparedBounds = sourceBounds,
                requestedBounds = requestedBounds,
                tap = tap,
                sourceWidth = project.sourceWidth,
                sourceHeight = project.sourceHeight,
            )
    }
}
