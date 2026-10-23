package com.localplayer.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerLifecycleTest {
    private class Probe {
        val ran = ArrayList<String>()
        val cancelled = ArrayList<String>()
        fun command(name: String) = Command<String>({ c -> ran += "$name@$c" }, { cancelled += name })
    }

    @Test fun commandsWaitUntilConnectedThenRunInOrderExactlyOnce() {
        val lc = ControllerLifecycle<String>(); val p = Probe()
        val token = lc.beginConnect()
        assertFalse(lc.submit(p.command("a")))
        assertFalse(lc.submit(p.command("b")))
        assertTrue(lc.connected(token, "ctl1"))
        assertEquals(listOf("a@ctl1", "b@ctl1"), p.ran)
        assertTrue(lc.submit(p.command("c"))) // connected: runs immediately
        assertEquals(listOf("a@ctl1", "b@ctl1", "c@ctl1"), p.ran)
        assertEquals(0, lc.pendingCount)
    }

    @Test fun releaseBeforeConnectionCompletesCancelsPendingAndRefusesTheLateController() {
        val lc = ControllerLifecycle<String>(); val p = Probe()
        val token = lc.beginConnect()
        lc.submit(p.command("a"))
        assertNull(lc.release())
        assertEquals(listOf("a"), p.cancelled)
        assertFalse("late result of the abandoned attempt", lc.connected(token, "late"))
        assertTrue(p.ran.isEmpty())
        assertNull(lc.current)
    }

    @Test fun obsoleteCommandsNeverRunAfterReconnect() {
        val lc = ControllerLifecycle<String>(); val p = Probe()
        val old = lc.beginConnect()
        lc.submit(p.command("old"))
        lc.release()
        val fresh = lc.beginConnect()
        lc.submit(p.command("new"))
        assertFalse(lc.connected(old, "oldCtl"))
        assertTrue(lc.connected(fresh, "newCtl"))
        assertEquals(listOf("new@newCtl"), p.ran)
        assertEquals(listOf("old"), p.cancelled)
    }

    @Test fun failedConnectionCancelsWaitersAndAllowsANewAttempt() {
        val lc = ControllerLifecycle<String>(); val p = Probe()
        val token = lc.beginConnect()
        lc.submit(p.command("a"))
        lc.failed(token)
        assertEquals(listOf("a"), p.cancelled)
        val again = lc.beginConnect()
        assertTrue(again >= 0)
        lc.submit(p.command("b"))
        assertTrue(lc.connected(again, "ctl"))
        assertEquals(listOf("b@ctl"), p.ran)
    }

    @Test fun anOldFailureCannotDisturbANewerAttempt() {
        val lc = ControllerLifecycle<String>(); val p = Probe()
        val old = lc.beginConnect()
        lc.release()
        val fresh = lc.beginConnect()
        lc.submit(p.command("a"))
        lc.failed(old) // late failure of the abandoned attempt
        assertEquals(1, lc.pendingCount)
        assertTrue(p.cancelled.isEmpty())
        assertTrue(lc.connected(fresh, "ctl"))
        assertEquals(listOf("a@ctl"), p.ran)
    }

    @Test fun aDiscardedCommandIsNotRunLater() {
        val lc = ControllerLifecycle<String>(); val p = Probe()
        val token = lc.beginConnect()
        val a = p.command("a"); val b = p.command("b")
        lc.submit(a); lc.submit(b)
        lc.discard(a) // its coroutine was cancelled
        lc.connected(token, "ctl")
        assertEquals(listOf("b@ctl"), p.ran)
    }

    @Test fun aSecondUserCommandAfterReconnectRuns() {
        val lc = ControllerLifecycle<String>(); val p = Probe()
        val t1 = lc.beginConnect(); lc.connected(t1, "ctl1")
        assertEquals("ctl1", lc.release())
        val t2 = lc.beginConnect()
        lc.submit(p.command("again"))
        lc.connected(t2, "ctl2")
        assertEquals(listOf("again@ctl2"), p.ran)
    }

    @Test fun beginConnectIsRefusedWhileConnectingOrConnected() {
        val lc = ControllerLifecycle<String>()
        val t = lc.beginConnect()
        assertEquals(-1L, lc.beginConnect())
        lc.connected(t, "ctl")
        assertEquals(-1L, lc.beginConnect())
    }

    @Test fun aCommandIsResolvedOnlyOnce() {
        val p = Probe(); val c = p.command("x")
        c.run("1"); c.cancel(); c.run("2")
        assertEquals(listOf("x@1"), p.ran)
        assertTrue(p.cancelled.isEmpty())
    }

    @Test fun acceptedHookRunsBeforeTheWaitingCommands() {
        val lc = ControllerLifecycle<String>(); val order = ArrayList<String>()
        val t = lc.beginConnect()
        lc.submit(Command<String>({ order += "command" }, {}))
        lc.connected(t, "ctl") { order += "listenerAttached" }
        assertEquals(listOf("listenerAttached", "command"), order)
    }
}
