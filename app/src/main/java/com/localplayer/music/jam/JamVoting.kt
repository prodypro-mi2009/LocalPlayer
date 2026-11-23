package com.localplayer.music.jam

/** The host's phone takes part in a vote like any guest; this is its voter id. */
const val JAM_VOTER_HOST = "host"

/** A request for the next / previous song stays open this long. */
const val JAM_VOTE_MS = 30_000L

/** The result of a request ("approved" / "not approved") stays on the screens this long. */
const val JAM_VOTE_RESULT_MS = 6_000L

enum class JamAction(val wire: String) {
    NEXT("next"),
    PREVIOUS("prev"),
    ;

    companion object {
        fun parse(text: String?): JamAction? = entries.firstOrNull { it.wire == text }
    }
}

/**
 * An open request ("[byName] asks for the next song"). [yes] and [no] hold voter ids. It passes when more than half of
 * the people in the Jam (the guests and the host) said yes ([needed] of [voters]); it fails when that is no longer
 * possible, when the time ends, or when the song changed meanwhile.
 */
data class JamProposal(
    val id: String,
    val action: JamAction,
    val byId: String,
    val byName: String,
    /** The queue entry that was playing when it was asked: the request is void for any other song. */
    val trackId: String,
    /** Host-clock time ([JamClock.nowMs]) when the request ends. */
    val endsAtMs: Long,
    val yes: Set<String>,
    val no: Set<String>,
    val needed: Int,
    val voters: Int,
)

/** How the last request ended (shown for a few seconds). [id] is the request's id. */
data class JamVoteResult(val id: String, val action: JamAction, val byName: String, val passed: Boolean)

/** The rules of the vote, without any network or player: the host keeps one of these and feeds it. Not thread safe: lock outside. */
class JamVoting {
    var proposal: JamProposal? = null
        private set
    var result: JamVoteResult? = null
        private set

    sealed interface Outcome {
        data object Nothing : Outcome

        /** A new request is open. */
        data object Started : Outcome

        /** A vote was counted, the request is still open. */
        data object Counted : Outcome

        /** Another request (a different action) is open already. */
        data object Busy : Outcome

        data object Refused : Outcome

        data class Passed(val action: JamAction) : Outcome

        data object Failed : Outcome
    }

    fun needed(voters: Int) = voters / 2 + 1

    fun propose(
        id: String,
        action: JamAction,
        byId: String,
        byName: String,
        currentTrackId: String?,
        nowMs: Long,
        voterIds: Set<String>,
    ): Outcome {
        if (currentTrackId == null || byId !in voterIds) return Outcome.Refused
        val open = proposal
        if (open != null) {
            // Asking for the same thing again just adds the voice.
            return if (open.action == action && open.trackId == currentTrackId) vote(byId, open.id, true, voterIds) else Outcome.Busy
        }
        proposal = JamProposal(
            id, action, byId, byName, currentTrackId, nowMs + JAM_VOTE_MS,
            yes = setOf(byId), no = emptySet(), needed = needed(voterIds.size), voters = voterIds.size,
        )
        return settle(voterIds) ?: Outcome.Started
    }

    fun vote(voterId: String, proposalId: String, yes: Boolean, voterIds: Set<String>): Outcome {
        val open = proposal ?: return Outcome.Nothing
        if (open.id != proposalId || voterId !in voterIds) return Outcome.Refused
        proposal = open.copy(
            yes = if (yes) open.yes + voterId else open.yes - voterId,
            no = if (yes) open.no - voterId else open.no + voterId,
        )
        return settle(voterIds) ?: Outcome.Counted
    }

    /** Called now and then: ends a request whose time is over or whose song is gone, and recounts after people left. */
    fun tick(nowMs: Long, currentTrackId: String?, voterIds: Set<String>): Outcome {
        val open = proposal ?: return Outcome.Nothing
        if (currentTrackId != open.trackId || nowMs >= open.endsAtMs) {
            finish(open, passed = false)
            return Outcome.Failed
        }
        return settle(voterIds) ?: Outcome.Nothing
    }

    /** Removes the old result from the screens. */
    fun clearResult() {
        result = null
    }

    /** Forgets the request (the Jam ended). */
    fun clear() {
        proposal = null
        result = null
    }

    private fun settle(voterIds: Set<String>): Outcome? {
        val open = proposal ?: return null
        val voters = voterIds.size
        val needed = needed(voters)
        val yes = open.yes.count { it in voterIds }
        val no = open.no.count { it in voterIds }
        return when {
            yes >= needed -> {
                finish(open, passed = true)
                Outcome.Passed(open.action)
            }
            no > voters - needed -> {
                finish(open, passed = false)
                Outcome.Failed
            }
            else -> {
                if (open.needed != needed || open.voters != voters) proposal = open.copy(needed = needed, voters = voters)
                null
            }
        }
    }

    private fun finish(open: JamProposal, passed: Boolean) {
        proposal = null
        result = JamVoteResult(open.id, open.action, open.byName, passed)
    }
}
