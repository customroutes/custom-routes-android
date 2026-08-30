package io.github.customroutes.app.domain

class EditorHistory<T>(initial: T, private val maxDepth: Int = 20) {
    init {
        require(maxDepth > 0)
    }

    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun apply(next: T) {
        if (next == current) return
        if (undo.size == maxDepth) undo.removeFirst()
        undo.addLast(current)
        current = next
        redo.clear()
    }

    fun undo(): T {
        if (undo.isEmpty()) return current
        redo.addLast(current)
        current = undo.removeLast()
        return current
    }

    fun redo(): T {
        if (redo.isEmpty()) return current
        undo.addLast(current)
        current = redo.removeLast()
        return current
    }
}
