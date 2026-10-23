package com.localplayer.music.playback

import java.util.concurrent.atomic.AtomicBoolean

/** One unit of work for the player controller. It is resolved EXACTLY once: [run] or [cancel], never both. */
class Command<C>(private val onRun: (C) -> Unit, private val onCancel: () -> Unit) {
    private val resolved = AtomicBoolean(false)
    val isResolved: Boolean get() = resolved.get()
    fun run(controller: C) { if (resolved.compareAndSet(false, true)) onRun(controller) }
    fun cancel() { if (resolved.compareAndSet(false, true)) onCancel() }
}

/**
 * Connection bookkeeping of the UI-side controller, free of Android classes (unit-tested on the JVM).
 * Main thread only.
 *
 *  - Commands issued while there is no controller wait in [pending].
 *  - Every connection attempt has a token. A result for an older token (released or replaced attempt) is
 *    refused, so an old future can never install a controller or interfere with a newer attempt.
 *  - [release] and [failed] CANCEL everything still waiting: nothing stays suspended forever and nothing
 *    obsolete runs after a later reconnect.
 */
class ControllerLifecycle<C : Any> {
    private var generation = 0L
    private var controller: C? = null
    private var connecting = false
    private val pending = ArrayList<Command<C>>()

    val current: C? get() = controller
    val pendingCount: Int get() = pending.size

    /** Runs [command] now if connected (returns true) or queues it (false). */
    fun submit(command: Command<C>): Boolean {
        val c = controller
        if (c != null) {
            command.run(c)
            return true
        }
        pending.add(command)
        return false
    }

    /** Forgets a waiting command (its caller was cancelled). */
    fun discard(command: Command<C>) { pending.remove(command) }

    /** Token of a new attempt, or -1 when already connected or connecting. */
    fun beginConnect(): Long {
        if (controller != null || connecting) return -1L
        connecting = true
        return generation
    }

    /**
     * Accepts [c] only for the current attempt. [onAccepted] runs first (attach listeners), then the waiting
     * commands run in order. False: the attempt is obsolete and the caller must release [c].
     */
    fun connected(token: Long, c: C, onAccepted: (C) -> Unit = {}): Boolean {
        if (token != generation || !connecting) return false
        connecting = false
        controller = c
        onAccepted(c)
        val toRun = pending.toList()
        pending.clear()
        toRun.forEach { it.run(c) }
        return true
    }

    /** The current attempt failed: cancel what waits and allow a new attempt. Older tokens are ignored. */
    fun failed(token: Long) {
        if (token != generation || !connecting) return
        connecting = false
        cancelPending()
    }

    /** Abandons the connection. Returns the controller to release (if any). */
    fun release(): C? {
        generation++
        connecting = false
        val c = controller
        controller = null
        cancelPending()
        return c
    }

    private fun cancelPending() {
        val toCancel = pending.toList()
        pending.clear()
        toCancel.forEach { it.cancel() }
    }
}
