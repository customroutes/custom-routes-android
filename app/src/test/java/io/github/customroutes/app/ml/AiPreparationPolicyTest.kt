package io.github.customroutes.app.ml

import io.github.customroutes.app.data.loadImproveAiDetailWhenZoomed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPreparationPolicyTest {
    @Test
    fun zoomDetailIsEnabledWhenNoPreferenceWasStored() {
        assertTrue(loadImproveAiDetailWhenZoomed { _, default -> default })
        assertFalse(loadImproveAiDetailWhenZoomed { _, _ -> false })
    }

    @Test
    fun detailPolicyAppliesToEveryAiAction() {
        assertEquals(EmbeddingSlot.FULL_IMAGE, embeddingSlot(true, 1.99f))
        assertEquals(EmbeddingSlot.CROP, embeddingSlot(true, 2f))
        assertEquals(EmbeddingSlot.FULL_IMAGE, embeddingSlot(false, 8f))
    }

    @Test
    fun foregroundRequestsJoinOnlyWorkThatAlreadyStarted() {
        assertEquals(
            ForegroundPreparationOrder.REQUEST_DIRECTLY,
            foregroundPreparationOrder(FullImagePreparationState.SCHEDULED, EmbeddingSlot.CROP),
        )
        assertEquals(
            ForegroundPreparationOrder.WAIT_FOR_FULL_IMAGE,
            foregroundPreparationOrder(FullImagePreparationState.RUNNING, EmbeddingSlot.CROP),
        )
        assertEquals(
            ForegroundPreparationOrder.WAIT_FOR_FULL_IMAGE,
            foregroundPreparationOrder(FullImagePreparationState.RUNNING, EmbeddingSlot.FULL_IMAGE),
        )
        assertEquals(
            ForegroundPreparationOrder.USE_PREPARED_FULL_IMAGE,
            foregroundPreparationOrder(FullImagePreparationState.READY, EmbeddingSlot.FULL_IMAGE),
        )
    }

    @Test
    fun freshActivationAndIdleRetryUseDifferentDelays() {
        assertEquals(100L, aiPreparationDelayMillis(freshActivation = true))
        assertEquals(1_000L, aiPreparationDelayMillis(freshActivation = false))
    }

    @Test
    fun preparationRequiresAnIdleForegroundEditor() {
        val eligible = AiPreparationEligibility(
            projectOpen = true,
            modelReady = true,
            appForeground = true,
            manualDraftOpen = false,
            exportPreviewOpen = false,
            foregroundWorkActive = false,
        )
        assertTrue(eligible.canPrepare)
        assertFalse(eligible.copy(appForeground = false).canPrepare)
        assertFalse(eligible.copy(manualDraftOpen = true).canPrepare)
        assertFalse(eligible.copy(exportPreviewOpen = true).canPrepare)
        assertFalse(eligible.copy(foregroundWorkActive = true).canPrepare)
    }

    @Test
    fun memoryCleanupIsTiered() {
        assertEquals(MemoryCleanup.NONE, memoryCleanup(MemoryPressure.NORMAL))
        assertEquals(MemoryCleanup.EMBEDDINGS, memoryCleanup(MemoryPressure.LOW))
        assertEquals(MemoryCleanup.RUNTIME, memoryCleanup(MemoryPressure.CRITICAL))
    }

    @Test
    fun cancellationTerminatesAttachedAndFutureRuns() {
        val attached = PreparationCancellation()
        var attachedTerminations = 0
        attached.attach { attachedTerminations++ }
        attached.cancel()
        assertEquals(1, attachedTerminations)

        val cancelledFirst = PreparationCancellation()
        var futureTerminations = 0
        cancelledFirst.cancel()
        cancelledFirst.attach { futureTerminations++ }
        assertEquals(1, futureTerminations)
    }

    @Test
    fun silentFailureWaitsForForegroundOwnershipOrAProjectChange() {
        val failures = AiPreparationFailureLatch()

        failures.recordFailure("first")
        assertFalse(failures.allowsSpeculation("first"))
        assertTrue(failures.allowsSpeculation("second"))

        failures.foregroundTakesOwnership("first")
        assertTrue(failures.allowsSpeculation("first"))

        failures.recordFailure("first")
        failures.clear()
        assertTrue(failures.allowsSpeculation("first"))
    }

    @Test
    fun foregroundCropActuallyWaitsForRunningFullImagePreparation() = runBlocking {
        val mutex = Mutex()
        val fullStarted = CompletableDeferred<Unit>()
        val finishFull = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val fullPreparation = launch {
            ownAiResources(mutex) {
                events += "full-started"
                fullStarted.complete(Unit)
                finishFull.await()
                events += "full-finished"
            }
        }
        fullStarted.await()

        val crop = launch {
            awaitPreparationAndOwnResources(fullPreparation, mutex) {
                events += "crop"
            }
        }
        yield()
        assertEquals(listOf("full-started"), events)

        finishFull.complete(Unit)
        crop.join()
        assertEquals(listOf("full-started", "full-finished", "crop"), events)
    }

    @Test
    fun cropBeforePreparationStartsCancelsFullImageAndRunsDirectly() = runBlocking {
        val events = mutableListOf<String>()
        val pendingFull = launch(start = CoroutineStart.LAZY) { events += "full" }

        pendingFull.cancel()
        awaitPreparationAndOwnResources(null, Mutex()) { events += "crop" }
        pendingFull.join()

        assertEquals(listOf("crop"), events)
    }

    @Test
    fun projectCleanupAndModelDeletionWaitForCurrentAiWork() = runBlocking {
        val mutex = Mutex()
        val workStarted = CompletableDeferred<Unit>()
        val finishWork = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val foregroundWork = launch {
            ownAiResources(mutex) {
                events += "work"
                workStarted.complete(Unit)
                finishWork.await()
            }
        }
        workStarted.await()

        val cleanup = launch {
            ownAiResources(mutex) { events += "cleanup" }
        }
        yield()
        assertEquals(listOf("work"), events)

        finishWork.complete(Unit)
        foregroundWork.join()
        cleanup.join()
        assertEquals(listOf("work", "cleanup"), events)
    }
}
