package io.github.customroutes.app.ui

import io.github.customroutes.app.domain.BinaryMask
import io.github.customroutes.app.domain.HoldRole
import io.github.customroutes.app.domain.ManualHoldDraft
import io.github.customroutes.app.domain.MaskRegion
import io.github.customroutes.app.domain.SourceRect
import io.github.customroutes.app.ml.ModelStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiModelSetupTest {
    @Test
    fun unavailableModelDefaultsToManualPaint() {
        val unavailable = listOf(
            ModelStatus.Checking,
            ModelStatus.Missing,
            ModelStatus.Downloading(0.5f),
            ModelStatus.Failed("Network unavailable"),
        )

        unavailable.forEach { status ->
            assertEquals(AddMethod.MANUAL_PAINT, defaultAddMethod(status))
        }
        assertEquals(AddMethod.AI_TAP, defaultAddMethod(ModelStatus.Ready))
    }

    @Test
    fun completedDownloadActivatesRequestedAiTool() {
        val tap = AppUiState(
            project = projectWithSelectedHold,
            addMethod = AddMethod.MANUAL_PAINT,
            pendingAiRequest = PendingAiRequest(AiTool.TAP, "project", null),
        ).completeModelDownload()
        val include = AppUiState(
            project = projectWithSelectedHold,
            selectedHoldId = "hold",
            pendingAiRequest = PendingAiRequest(AiTool.INCLUDE, "project", "hold"),
        ).completeModelDownload()
        val exclude = AppUiState(
            project = projectWithSelectedHold,
            selectedHoldId = "hold",
            pendingAiRequest = PendingAiRequest(AiTool.EXCLUDE, "project", "hold"),
        ).completeModelDownload()

        assertEquals(AddMethod.AI_TAP, tap.addMethod)
        assertEquals(EditAction.AI_INCLUDE, include.editAction)
        assertEquals(EditAction.AI_EXCLUDE, exclude.editAction)
        assertEquals(EditorMode.EDIT, include.editorMode)
        assertEquals(EditorMode.EDIT, exclude.editorMode)
        listOf(tap, include, exclude).forEach { state ->
            assertEquals(ModelStatus.Ready, state.modelStatus)
            assertEquals("AI tools ready offline", state.message)
            assertNull(state.pendingAiRequest)
        }
    }

    @Test
    fun completedDownloadDoesNotTransferToolToAnotherProject() {
        val state = AppUiState(
            project = projectWithSelectedHold.copy(id = "other-project"),
            selectedHoldId = "other-hold",
            pendingAiRequest = PendingAiRequest(AiTool.INCLUDE, "project", "hold"),
        ).completeModelDownload()

        assertEquals(EditorMode.ADD, state.editorMode)
        assertEquals(EditAction.SELECT, state.editAction)
        assertEquals(ModelStatus.Ready, state.modelStatus)
        assertNull(state.pendingAiRequest)
    }

    @Test
    fun manualInteractionSupersedesPendingAiBeforeFirstStroke() {
        AiTool.entries.forEach { tool ->
            val paintingState = AppUiState(
                project = projectWithSelectedHold,
                addMethod = AddMethod.MANUAL_PAINT,
                pendingAiRequest = PendingAiRequest(tool, "project", "hold"),
            ).supersedePendingAiRequest()
            val state = paintingState.completeModelDownload()

            assertEquals(AddMethod.MANUAL_PAINT, state.addMethod)
            assertEquals(EditorMode.ADD, state.editorMode)
            assertEquals(EditAction.SELECT, state.editAction)
            assertEquals(ModelStatus.Ready, state.modelStatus)
            assertNull(state.pendingAiRequest)
        }
    }

    @Test
    fun manualStrokeWinsIfModelCompletionRacesWithStrokeStart() {
        val beforeStroke = AppUiState(
            project = projectWithSelectedHold,
            addMethod = AddMethod.MANUAL_PAINT,
            pendingAiRequest = PendingAiRequest(AiTool.TAP, "project", null),
        )

        val state = beforeStroke.completeModelDownload().supersedePendingAiWithStroke(StrokeTool.ADD_MANUAL)

        assertEquals(EditorMode.ADD, state.editorMode)
        assertEquals(AddMethod.MANUAL_PAINT, state.addMethod)
        assertNull(state.pendingAiRequest)
    }

    @Test
    fun editPaintWinsIfModelCompletionRacesWithStrokeStart() {
        val beforeStroke = AppUiState(
            project = projectWithSelectedHold,
            selectedHoldId = "hold",
            editorMode = EditorMode.EDIT,
            editAction = EditAction.PAINT,
            pendingAiRequest = PendingAiRequest(AiTool.INCLUDE, "project", "hold"),
        )

        val state = beforeStroke.completeModelDownload().supersedePendingAiWithStroke(StrokeTool.EDIT_PAINT)

        assertEquals(EditorMode.EDIT, state.editorMode)
        assertEquals(EditAction.PAINT, state.editAction)
        assertNull(state.pendingAiRequest)
    }

    @Test
    fun completedDownloadDoesNotInterruptExistingManualDraft() {
        val state = AppUiState(
            project = projectWithSelectedHold,
            addMethod = AddMethod.MANUAL_PAINT,
            manualDraft = ManualHoldDraft(
                HoldRole.REGULAR,
                MaskRegion(BinaryMask.empty(2, 2), SourceRect(0f, 0f, 2f, 2f)),
            ),
            pendingAiRequest = PendingAiRequest(AiTool.TAP, "project", null),
        ).completeModelDownload()

        assertEquals(AddMethod.MANUAL_PAINT, state.addMethod)
        assertEquals(ModelStatus.Ready, state.modelStatus)
        assertNull(state.pendingAiRequest)
    }

    private val projectWithSelectedHold
        get() = io.github.customroutes.app.domain.RouteProject(
            id = "project",
            name = null,
            sourceFileName = "source.jpg",
            sourceWidth = 100,
            sourceHeight = 100,
            workingWidth = 100,
            workingHeight = 100,
            holds = emptyList(),
            updatedAtEpochMillis = 0,
        )
}
