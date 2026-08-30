package io.github.customroutes.app.ml

import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val FRESH_AI_PREPARATION_DELAY_MILLIS = 100L
internal const val IDLE_AI_PREPARATION_DELAY_MILLIS = 1_000L

internal fun embeddingSlot(improveDetailWhenZoomed: Boolean, zoom: Float): EmbeddingSlot =
    if (improveDetailWhenZoomed && zoom >= 2f) EmbeddingSlot.CROP else EmbeddingSlot.FULL_IMAGE

internal enum class FullImagePreparationState { ABSENT, SCHEDULED, RUNNING, READY }

internal enum class ForegroundPreparationOrder {
    REQUEST_DIRECTLY,
    WAIT_FOR_FULL_IMAGE,
    USE_PREPARED_FULL_IMAGE,
}

internal fun foregroundPreparationOrder(
    fullImageState: FullImagePreparationState,
    requestedSlot: EmbeddingSlot,
): ForegroundPreparationOrder = when {
    fullImageState == FullImagePreparationState.RUNNING -> ForegroundPreparationOrder.WAIT_FOR_FULL_IMAGE
    fullImageState == FullImagePreparationState.READY && requestedSlot == EmbeddingSlot.FULL_IMAGE ->
        ForegroundPreparationOrder.USE_PREPARED_FULL_IMAGE
    else -> ForegroundPreparationOrder.REQUEST_DIRECTLY
}

internal fun aiPreparationDelayMillis(freshActivation: Boolean): Long =
    if (freshActivation) FRESH_AI_PREPARATION_DELAY_MILLIS else IDLE_AI_PREPARATION_DELAY_MILLIS

internal data class AiPreparationEligibility(
    val projectOpen: Boolean,
    val modelReady: Boolean,
    val appForeground: Boolean,
    val manualDraftOpen: Boolean,
    val exportPreviewOpen: Boolean,
    val foregroundWorkActive: Boolean,
) {
    val canPrepare: Boolean
        get() = projectOpen && modelReady && appForeground && !manualDraftOpen &&
            !exportPreviewOpen && !foregroundWorkActive
}

internal enum class MemoryPressure { NORMAL, LOW, CRITICAL }
internal enum class MemoryCleanup { NONE, EMBEDDINGS, RUNTIME }

internal fun memoryCleanup(pressure: MemoryPressure): MemoryCleanup = when (pressure) {
    MemoryPressure.NORMAL -> MemoryCleanup.NONE
    MemoryPressure.LOW -> MemoryCleanup.EMBEDDINGS
    MemoryPressure.CRITICAL -> MemoryCleanup.RUNTIME
}

internal class AiPreparationFailureLatch {
    private var failedProjectId: String? = null

    fun allowsSpeculation(projectId: String): Boolean = failedProjectId != projectId

    fun recordFailure(projectId: String) {
        failedProjectId = projectId
    }

    fun foregroundTakesOwnership(projectId: String) {
        if (failedProjectId == projectId) failedProjectId = null
    }

    fun clear() {
        failedProjectId = null
    }
}

internal suspend fun <T> awaitPreparationAndOwnResources(
    preparation: Job?,
    mutex: Mutex,
    operation: suspend () -> T,
): T {
    preparation?.join()
    return mutex.withLock { operation() }
}

internal suspend fun <T> ownAiResources(
    mutex: Mutex,
    operation: suspend () -> T,
): T = mutex.withLock { operation() }

class PreparationCancellation {
    private val lock = Any()
    private var cancelled = false
    private var terminate: (() -> Unit)? = null

    fun cancel() {
        val action = synchronized(lock) {
            cancelled = true
            terminate
        }
        action?.invoke()
    }

    internal fun attach(action: () -> Unit) {
        val terminateNow = synchronized(lock) {
            terminate = action
            cancelled
        }
        if (terminateNow) action()
    }

    internal fun detach() {
        synchronized(lock) { terminate = null }
    }
}
