package com.example.nativecppapp.core.history

class ProjectHistoryManager(private val maxHistory: Int = 30) {

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var isMuted = false

    val canUndo: Boolean
        get() = undoStack.size > 1

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun recordSnapshot(json: String) {
        if (isMuted) return
        if (json.isBlank()) return

        // Do not add duplicate state if identical to top
        if (undoStack.isNotEmpty() && undoStack.last() == json) {
            return
        }

        undoStack.addLast(json)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(): String? {
        if (!canUndo) return null
        isMuted = true
        return try {
            val currentState = undoStack.removeLast()
            redoStack.addLast(currentState)
            undoStack.lastOrNull()
        } finally {
            isMuted = false
        }
    }

    fun redo(): String? {
        if (!canRedo) return null
        isMuted = true
        return try {
            val nextState = redoStack.removeLast()
            undoStack.addLast(nextState)
            nextState
        } finally {
            isMuted = false
        }
    }

    fun resetWithInitialState(initialJson: String) {
        undoStack.clear()
        redoStack.clear()
        if (initialJson.isNotBlank()) {
            undoStack.addLast(initialJson)
        }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
