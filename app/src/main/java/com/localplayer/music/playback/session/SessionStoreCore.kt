package com.localplayer.music.playback.session

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Disk side of the playback session. All disk work runs on [executor], which MUST run jobs one at a time in
 * submission order (a single-thread executor); callers never touch the disk themselves.
 *
 * Safety rules:
 *  - an empty queue is never written, so cleanup of the live queue cannot replace a valid snapshot;
 *  - every write is atomic (temp file, fsync, rename), so a crash leaves the old or the new file, never half;
 *  - [invalidate] (an intentional clear) drops every write still waiting, so a late write cannot bring the
 *    cleared session back; only writes requested after it count.
 */
class SessionStoreCore(
    private val dir: File,
    private val executor: Executor,
    /** Receives short diagnostics (no audio URIs, no file names of the music library). */
    private val log: (String) -> Unit = {},
    /** Test hook: replaces the disk write. Must throw on failure. */
    private val writer: ((File, ByteArray) -> Unit)? = null,
) {
    /** Result of waiting for the writer: confirmed on disk, still running, or a write failed. */
    enum class SaveOutcome { DURABLE, TIMED_OUT, FAILED }

    private val queueFile = File(dir, "queue.bin")
    private val stateFile = File(dir, "state.bin")
    private val tickets = AtomicLong(0)
    @Volatile private var floor = 0L
    private val pendingQueue = AtomicReference<Pair<Long, SavedQueue>?>(null)
    private val pendingState = AtomicReference<Pair<Long, SavedState>?>(null)
    /** Ticket of the newest write that reached disk, per file (touched only on the executor thread). */
    @Volatile private var queueWritten = 0L
    @Volatile private var stateWritten = 0L
    /** True while the LAST write of that file failed (the old, valid file is still on disk). */
    @Volatile private var queueFailed = false
    @Volatile private var stateFailed = false

    fun writeQueue(queue: SavedQueue) {
        if (queue.entries.isEmpty()) return
        pendingQueue.set(tickets.incrementAndGet() to queue)
        executor.execute { flushQueue() }
    }

    fun writeState(state: SavedState) {
        pendingState.set(tickets.incrementAndGet() to state)
        executor.execute { flushState() }
    }

    /** Intentional clear: forget the saved session and everything still waiting to be written. */
    fun invalidate() {
        val ticket = tickets.incrementAndGet()
        floor = ticket
        pendingQueue.set(null)
        pendingState.set(null)
        executor.execute {
            // A file already rewritten with data requested AFTER this clear belongs to the new session: keep it.
            if (queueWritten < ticket) runCatching { queueFile.delete() }
            if (stateWritten < ticket) runCatching { stateFile.delete() }
        }
    }

    /**
     * Waits (bounded) until everything submitted so far has been written. Does no disk I/O itself.
     * DURABLE = everything requested is on disk; TIMED_OUT = the writer is still busy (the write may still land);
     * FAILED = a write failed (the previous valid snapshot is untouched).
     */
    fun flushBlocking(timeoutMs: Long): SaveOutcome {
        val done = CountDownLatch(1)
        executor.execute { done.countDown() }
        if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) return SaveOutcome.TIMED_OUT
        return if (queueFailed || stateFailed) SaveOutcome.FAILED else SaveOutcome.DURABLE
    }

    /** Blocking read: call from a background thread. Null when there is nothing valid to restore. */
    fun load(): SavedSession? {
        val queue = runCatching { queueFile.takeIf { it.isFile }?.readBytes() }.getOrNull()
            ?.let { SessionCodec.decodeQueue(it) }
            ?.takeIf { it.entries.isNotEmpty() } ?: return null
        val state = runCatching { stateFile.takeIf { it.isFile }?.readBytes() }.getOrNull()
            ?.let { SessionCodec.decodeState(it) }
        return SavedSession(queue, state)
    }

    private fun flushQueue() {
        val job = pendingQueue.getAndSet(null) ?: return
        if (job.first < floor) return
        runCatching { write(queueFile, SessionCodec.encodeQueue(job.second)) }
            .onSuccess { queueWritten = job.first; queueFailed = false }
            .onFailure { queueFailed = true; log("queue write failed: ${it.javaClass.simpleName}") }
    }

    private fun flushState() {
        val job = pendingState.getAndSet(null) ?: return
        if (job.first < floor) return
        runCatching { write(stateFile, SessionCodec.encodeState(job.second)) }
            .onSuccess { stateWritten = job.first; stateFailed = false }
            .onFailure { stateFailed = true; log("state write failed: ${it.javaClass.simpleName}") }
    }

    private fun write(target: File, bytes: ByteArray) {
        val custom = writer
        if (custom != null) custom(target, bytes) else atomicWriteTo(dir, target, bytes)
    }

    companion object {
        /**
         * Temp file + fsync + read-back check + atomic rename. If anything fails the temp file is removed and the
         * previous file is left exactly as it was: a valid snapshot is never replaced by an incomplete one.
         */
        fun atomicWriteTo(dir: File, target: File, bytes: ByteArray) {
            val tmp = File(dir, target.name + ".tmp")
            try {
                if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw java.io.IOException("no directory")
                FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
                if (!tmp.readBytes().contentEquals(bytes)) throw java.io.IOException("verification failed")
                try {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (t: Throwable) {
                runCatching { tmp.delete() }
                throw t
            }
        }
    }
}
