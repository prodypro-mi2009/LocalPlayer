package com.localplayer.music.jam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamVotingTest {
    private val voters = setOf(JAM_VOTER_HOST, "a", "b", "c") // 4 people: 3 yes needed

    private fun JamVoting.ask(by: String = "a", action: JamAction = JamAction.NEXT, ids: Set<String> = voters) =
        propose("p1", action, by, "Name-$by", "t1", 1_000L, ids)

    @Test
    fun theAskerCountsAsYesAndMoreThanHalfPasses() {
        val v = JamVoting()
        assertEquals(JamVoting.Outcome.Started, v.ask())
        assertEquals(3, v.proposal?.needed)
        assertEquals(JamVoting.Outcome.Counted, v.vote("b", "p1", true, voters))
        assertEquals(JamVoting.Outcome.Passed(JamAction.NEXT), v.vote(JAM_VOTER_HOST, "p1", true, voters))
        assertNull(v.proposal)
        assertTrue(v.result!!.passed)
        assertEquals("Name-a", v.result!!.byName)
    }

    @Test
    fun failsWhenItCannotPassAnymore() {
        val v = JamVoting()
        v.ask()
        assertEquals(JamVoting.Outcome.Counted, v.vote("b", "p1", false, voters))
        assertEquals(JamVoting.Outcome.Failed, v.vote("c", "p1", false, voters))
        assertFalse(v.result!!.passed)
        assertNull(v.proposal)
    }

    @Test
    fun aVoteCanBeChanged() {
        val v = JamVoting()
        v.ask()
        v.vote("b", "p1", false, voters)
        v.vote("b", "p1", true, voters)
        assertEquals(setOf("a", "b"), v.proposal!!.yes)
        assertTrue(v.proposal!!.no.isEmpty())
    }

    @Test
    fun aSecondDifferentRequestIsBusyAndTheSameOneAddsAVoice() {
        val v = JamVoting()
        v.ask()
        assertEquals(JamVoting.Outcome.Busy, v.propose("p2", JamAction.PREVIOUS, "b", "B", "t1", 1_100L, voters))
        assertEquals(JamVoting.Outcome.Counted, v.propose("p3", JamAction.NEXT, "b", "B", "t1", 1_100L, voters))
        assertEquals(setOf("a", "b"), v.proposal!!.yes)
    }

    @Test
    fun aLoneGuestAndHostDecideBetweenThem() {
        val two = setOf(JAM_VOTER_HOST, "a") // 2 people: 2 needed
        val v = JamVoting()
        assertEquals(JamVoting.Outcome.Started, v.ask(ids = two))
        assertEquals(JamVoting.Outcome.Passed(JamAction.NEXT), v.vote(JAM_VOTER_HOST, "p1", true, two))
    }

    @Test
    fun timeOutAndChangedSongEndTheRequest() {
        val v = JamVoting()
        v.ask()
        assertEquals(JamVoting.Outcome.Nothing, v.tick(2_000L, "t1", voters))
        assertEquals(JamVoting.Outcome.Failed, v.tick(1_000L + JAM_VOTE_MS, "t1", voters))
        assertNull(v.proposal)

        val w = JamVoting()
        w.ask()
        assertEquals(JamVoting.Outcome.Failed, w.tick(2_000L, "t2", voters))
    }

    @Test
    fun peopleWhoLeftNoLongerCount() {
        val v = JamVoting()
        v.ask()
        v.vote("b", "p1", false, voters)
        // c and the host leave... only a and b remain: a yes, b no, 2 needed -> cannot pass
        assertEquals(JamVoting.Outcome.Failed, v.tick(2_000L, "t1", setOf("a", "b")))
    }

    @Test
    fun strangersAndOldRequestsAreRefused() {
        val v = JamVoting()
        assertEquals(JamVoting.Outcome.Refused, v.ask(by = "x"))
        assertEquals(JamVoting.Outcome.Refused, v.propose("p9", JamAction.NEXT, "a", "A", null, 0L, voters))
        v.ask()
        assertEquals(JamVoting.Outcome.Refused, v.vote("b", "other", true, voters))
        assertEquals(JamVoting.Outcome.Refused, v.vote("x", "p1", true, voters))
        assertNotNull(v.proposal)
    }

    @Test
    fun theProposalSurvivesTheJson() {
        val v = JamVoting()
        v.ask()
        val state = JamState(
            rev = 5, hostName = "H", members = listOf(JamMember("a", "A")), queue = emptyList(), currentIndex = 0,
            playing = false, positionMs = 0, anchorMs = 0, proposal = v.proposal,
            result = JamVoteResult("r", JamAction.PREVIOUS, "B", true),
        )
        val back = JamJson.decodeState(JamJson.encode(state, "a"))!!
        assertEquals(state.proposal, back.proposal)
        assertEquals(state.result, back.result)
        assertEquals("a", back.me)
    }
}
