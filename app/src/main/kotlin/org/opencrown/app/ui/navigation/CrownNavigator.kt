package org.opencrown.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * A small, interruptible back stack.
 *
 * It exists instead of a navigation framework because the shell needs precise
 * control of transition direction and because the app has no route arguments
 * that require deep-link handling.
 */
class CrownNavigator(initial: Destination) {
    private val stack: SnapshotStateList<Destination> = mutableStateListOf(initial)

    val current: Destination
        get() = stack.last()

    val canGoBack: Boolean
        get() = stack.size > 1

    val depth: Int
        get() = stack.size

    /** +1 for a push, -1 for a pop. Drives the direction of the screen transition. */
    var direction: Int by mutableStateOf(1)
        private set

    fun push(destination: Destination) {
        if (stack.lastOrNull() == destination) return
        direction = 1
        stack.add(destination)
    }

    fun pop(): Boolean {
        if (!canGoBack) return false
        direction = -1
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun popTo(destination: Destination) {
        val index = stack.indexOfLast { it == destination }
        if (index < 0) return
        while (stack.lastIndex > index) stack.removeAt(stack.lastIndex)
    }

    fun reset(destination: Destination) {
        direction = 1
        stack.clear()
        stack.add(destination)
    }

    fun contains(destination: Destination): Boolean = stack.contains(destination)
}