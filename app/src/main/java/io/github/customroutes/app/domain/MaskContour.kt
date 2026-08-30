package io.github.customroutes.app.domain

fun MaskRegion.outerContour(checkCancelled: () -> Unit = {}): List<SourcePoint> {
    val gridPoints = removeCollinear(largestComponentOutline(mask, checkCancelled), checkCancelled)
    val maskPoints = ArrayList<SourcePoint>(gridPoints.size)
    gridPoints.forEachIndexed { index, point ->
        if (index % 4096 == 0) checkCancelled()
        maskPoints += SourcePoint(point.x.toFloat(), point.y.toFloat())
    }
    val smoothed = smoothOnce(maskPoints, checkCancelled)
    return ArrayList<SourcePoint>(smoothed.size).apply {
        smoothed.forEachIndexed { index, point ->
            if (index % 4096 == 0) checkCancelled()
            add(maskToSource(point))
        }
    }
}

private data class GridPoint(val x: Int, val y: Int)

/**
 * Row-compressed representation of the largest 4-connected component.
 * Membership queries binary-search the runs of one row, so neither labeling
 * nor boundary tracing ever allocates per-pixel structures.
 */
private class ComponentRows(
    val rowIndices: IntArray,
    val rowRuns: Array<IntArray>,
    val filledCellCount: Long,
) {
    val firstRow: Int get() = rowIndices[0]
    val firstRunStart: Int get() = rowRuns[0][0]

    fun isFilled(x: Int, y: Int): Boolean {
        val row = rowIndices.binarySearch(y)
        if (row < 0) return false
        val runs = rowRuns[row]
        var low = 0
        var high = runs.size / 2 - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val start = runs[middle * 2]
            val end = runs[middle * 2 + 1]
            when {
                x < start -> high = middle - 1
                x >= end -> low = middle + 1
                else -> return true
            }
        }
        return false
    }
}

private fun largestComponentRows(mask: BinaryMask, checkCancelled: () -> Unit): ComponentRows? {
    val flatRuns = mask.toRuns(checkCancelled)
    if (flatRuns.isEmpty()) return null

    val rowIndices = ArrayList<Int>()
    val rowRunLists = ArrayList<ArrayList<Int>>()
    var runIndex = 0
    while (runIndex < flatRuns.size) {
        checkCancelled()
        var start = flatRuns[runIndex]
        val end = start + flatRuns[runIndex + 1]
        while (start < end) {
            val row = start / mask.width
            val rowLimit = (row + 1) * mask.width
            val segmentEnd = minOf(end, rowLimit)
            if (rowIndices.isEmpty() || rowIndices.last() != row) {
                rowIndices += row
                rowRunLists.add(ArrayList())
            }
            rowRunLists.last().apply {
                add(start - row * mask.width)
                add(segmentEnd - row * mask.width)
            }
            start = segmentEnd
        }
        runIndex += 2
    }

    val firstRunOfRow = IntArray(rowIndices.size)
    var totalRuns = 0
    for (row in rowIndices.indices) {
        firstRunOfRow[row] = totalRuns
        totalRuns += rowRunLists[row].size / 2
    }

    val parent = IntArray(totalRuns) { it }
    fun find(node: Int): Int {
        var root = node
        while (parent[root] != root) root = parent[root]
        var current = node
        while (parent[current] != current) {
            val next = parent[current]
            parent[current] = root
            current = next
        }
        return root
    }

    for (row in 1 until rowIndices.size) {
        checkCancelled()
        if (rowIndices[row] - rowIndices[row - 1] != 1) continue
        val upper = rowRunLists[row - 1]
        val lower = rowRunLists[row]
        var upperRun = 0
        var lowerRun = 0
        while (upperRun * 2 < upper.size && lowerRun * 2 < lower.size) {
            val upperStart = upper[upperRun * 2]
            val upperEnd = upper[upperRun * 2 + 1]
            val lowerStart = lower[lowerRun * 2]
            val lowerEnd = lower[lowerRun * 2 + 1]
            if (upperStart < lowerEnd && lowerStart < upperEnd) {
                val upperRoot = find(firstRunOfRow[row - 1] + upperRun)
                val lowerRoot = find(firstRunOfRow[row] + lowerRun)
                if (upperRoot != lowerRoot) parent[upperRoot] = lowerRoot
            }
            if (upperEnd <= lowerEnd) upperRun++ else lowerRun++
        }
    }

    val componentSizes = LongArray(totalRuns)
    for (row in rowIndices.indices) {
        checkCancelled()
        val runs = rowRunLists[row]
        for (run in 0 until runs.size / 2) {
            componentSizes[find(firstRunOfRow[row] + run)] += runs[run * 2 + 1] - runs[run * 2]
        }
    }

    var largestRoot = 0
    var largestSize = 0L
    for (node in 0 until totalRuns) {
        if (node % 4096 == 0) checkCancelled()
        val root = find(node)
        if (componentSizes[root] > largestSize) {
            largestSize = componentSizes[root]
            largestRoot = root
        }
    }

    val winningRows = ArrayList<Int>()
    val winningRuns = ArrayList<IntArray>()
    for (row in rowIndices.indices) {
        checkCancelled()
        val runs = rowRunLists[row]
        val kept = ArrayList<Int>()
        for (run in 0 until runs.size / 2) {
            if (find(firstRunOfRow[row] + run) == largestRoot) {
                kept += runs[run * 2]
                kept += runs[run * 2 + 1]
            }
        }
        if (kept.isNotEmpty()) {
            winningRows += rowIndices[row]
            winningRuns.add(kept.toIntArray())
        }
    }
    return ComponentRows(winningRows.toIntArray(), winningRuns.toTypedArray(), largestSize)
}

private const val DIRECTION_RIGHT = 0
private const val DIRECTION_DOWN = 1
private const val DIRECTION_LEFT = 2
private const val DIRECTION_UP = 3
private val DIRECTION_X = intArrayOf(1, 0, -1, 0)
private val DIRECTION_Y = intArrayOf(0, 1, 0, -1)

/**
 * Traces the outer boundary of the largest component by walking the lattice
 * between filled and empty cells, keeping the component on the right of the
 * walk. Work scales with boundary length rather than repeatedly scanning edges.
 */
private fun largestComponentOutline(mask: BinaryMask, checkCancelled: () -> Unit): List<GridPoint> {
    val component = largestComponentRows(mask, checkCancelled) ?: return emptyList()
    val startX = component.firstRunStart
    val startY = component.firstRow
    val outline = ArrayList<GridPoint>()
    outline += GridPoint(startX, startY)
    var x = startX
    var y = startY
    var direction = DIRECTION_UP
    val maxMoves = component.filledCellCount * 4 + 4
    var moves = 0L
    while (true) {
        direction = nextOutlineDirection(x, y, direction, component)
        x += DIRECTION_X[direction]
        y += DIRECTION_Y[direction]
        if (x == startX && y == startY) break
        outline += GridPoint(x, y)
        moves++
        if (moves % 4096L == 0L) checkCancelled()
        if (moves > maxMoves) return emptyList()
    }
    return outline
}

private fun nextOutlineDirection(x: Int, y: Int, incoming: Int, component: ComponentRows): Int {
    val right = (incoming + 1) and 3
    if (isBoundaryEdge(x, y, right, component)) return right
    if (isBoundaryEdge(x, y, incoming, component)) return incoming
    val left = (incoming + 3) and 3
    if (isBoundaryEdge(x, y, left, component)) return left
    return (incoming + 2) and 3
}

private fun isBoundaryEdge(x: Int, y: Int, direction: Int, component: ComponentRows): Boolean {
    val filledOnRight: Boolean
    val filledOnLeft: Boolean
    when (direction) {
        DIRECTION_RIGHT -> {
            filledOnRight = component.isFilled(x, y)
            filledOnLeft = component.isFilled(x, y - 1)
        }
        DIRECTION_DOWN -> {
            filledOnRight = component.isFilled(x - 1, y)
            filledOnLeft = component.isFilled(x, y)
        }
        DIRECTION_LEFT -> {
            filledOnRight = component.isFilled(x - 1, y - 1)
            filledOnLeft = component.isFilled(x - 1, y)
        }
        else -> {
            filledOnRight = component.isFilled(x, y - 1)
            filledOnLeft = component.isFilled(x - 1, y - 1)
        }
    }
    return filledOnRight && !filledOnLeft
}

private fun removeCollinear(points: List<GridPoint>, checkCancelled: () -> Unit): List<GridPoint> =
    points.filterIndexed { index, point ->
        if (index % 4096 == 0) checkCancelled()
        val before = points[(index - 1 + points.size) % points.size]
        val after = points[(index + 1) % points.size]
        (point.x - before.x) * (after.y - point.y) != (point.y - before.y) * (after.x - point.x)
    }

private fun smoothOnce(points: List<SourcePoint>, checkCancelled: () -> Unit): List<SourcePoint> {
    if (points.size < 3) return points
    return buildList(points.size * 2) {
        points.indices.forEach { index ->
            if (index % 4096 == 0) checkCancelled()
            val previous = points[(index - 1 + points.size) % points.size]
            val current = points[index]
            val next = points[(index + 1) % points.size]
            val incomingX = current.x - previous.x
            val incomingY = current.y - previous.y
            val outgoingX = next.x - current.x
            val outgoingY = next.y - current.y
            if (incomingX * outgoingY - incomingY * outgoingX > 0f) {
                add(SourcePoint(current.x * 0.75f + previous.x * 0.25f, current.y * 0.75f + previous.y * 0.25f))
                add(SourcePoint(current.x * 0.75f + next.x * 0.25f, current.y * 0.75f + next.y * 0.25f))
            } else {
                add(current)
            }
        }
    }
}
