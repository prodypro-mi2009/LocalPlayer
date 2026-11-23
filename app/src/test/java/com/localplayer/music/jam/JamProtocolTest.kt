package com.localplayer.music.jam

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamProtocolTest {
    @Test
    fun namesAreCleanedAndLimited() {
        assertEquals("Nikos Maria", JamNames.clean("  Nikos \n\t Maria  "))
        assertNull(JamNames.clean("   "))
        assertNull(JamNames.clean(null))
        assertEquals(JAM_MAX_NAME, JamNames.clean("x".repeat(100))?.length)
    }

    @Test
    fun equalNamesGetANumber() {
        assertEquals("Maria", JamNames.unique("Maria", listOf("Nikos")))
        assertEquals("Maria 2", JamNames.unique("Maria", listOf("maria")))
        assertEquals("Maria 3", JamNames.unique("Maria", listOf("Maria", "Maria 2")))
        assertTrue(JamNames.unique("x".repeat(JAM_MAX_NAME), listOf("x".repeat(JAM_MAX_NAME))).length <= JAM_MAX_NAME)
    }

    @Test
    fun inviteSurvivesTheQrCode() {
        val invite = JamInvite("192.168.1.5", 8765, "483920")
        assertEquals("lpjam://192.168.1.5:8765/483920", invite.toUri())
        assertEquals(invite, JamInvite.parse(invite.toUri()))
        assertEquals("192.168.1.5:8765", invite.address())
    }

    @Test
    fun otherQrCodesAreNotInvites() {
        assertNull(JamInvite.parse("https://example.com"))
        assertNull(JamInvite.parse("lpjam://192.168.1.5:8765"))
        assertNull(JamInvite.parse("lpjam://not-an-ip:8765/483920"))
        assertNull(JamInvite.parse("lpjam://192.168.1.5:99999/483920"))
        assertNull(JamInvite.parse("lpjam://192.168.1.5:8765/12ab56"))
        assertNull(JamInvite.parse("lpjam://300.1.1.1:8765/483920"))
        assertNull(JamInvite.parse(null))
    }

    @Test
    fun typedAddressUsesTheDefaultPort() {
        assertEquals(JamInvite("10.0.0.2", JAM_DEFAULT_PORT, "123456"), JamInvite.fromParts(" 10.0.0.2 ", "123456"))
        assertEquals(JamInvite("10.0.0.2", 8766, "123456"), JamInvite.fromParts("10.0.0.2:8766", "123456"))
        assertNull(JamInvite.fromParts("10.0.0.2:abc", "123456"))
        assertNull(JamInvite.fromParts("10.0.0.2", "12345"))
    }

    @Test
    fun stateRoundTripsThroughJson() {
        val state = JamState(
            rev = 7,
            hostName = "Panagiotis",
            members = listOf(JamMember("a1", "Maria")),
            queue = listOf(JamTrack("q1", "Song \"one\"", "Artist", "Maria"), JamTrack("q2", "Τραγούδι", "", "Panagiotis")),
            currentIndex = 1,
            playing = true,
            positionMs = 91_500,
            anchorMs = 123_456_789,
        )
        assertEquals(state, JamJson.decodeState(JamJson.encode(state)))
    }

    @Test
    fun brokenJsonIsRefused() {
        assertNull(JamJson.decodeState("not json"))
        assertNull(JamJson.decodeState("{}"))
        assertNull(JamJson.decodeJoinRequest("{\"code\":\"1\"}"))
    }

    @Test
    fun joinMessagesRoundTrip() {
        val request = JamJson.decodeJoinRequest(JamJson.joinRequest("123456", "Maria", "dev-1"))
        assertNotNull(request)
        assertEquals("Maria", request?.name)
        assertEquals("dev-1", request?.deviceId)
        val reply = JamJson.decodeJoinReply(JamJson.joinReply("r1", "approved", "tok", "m1"))
        assertEquals("approved", reply?.status)
        assertEquals("tok", reply?.token)
        assertNull(JamJson.decodeJoinReply(JamJson.joinReply("r1", "pending"))?.token)
        assertEquals("full", JamJson.errorCode(JamJson.error("full")))
    }

    @Test
    fun fileExtensionsAreSafe() {
        assertEquals("mp3", safeAudioExtension("x.MP3"))
        assertEquals("audio", safeAudioExtension("x."))
        assertEquals("audio", safeAudioExtension("x.m/../../etc"))
        assertEquals("audio", safeAudioExtension("x.toolongext"))
        assertEquals("audio", safeAudioExtension(null))
    }

    @Test
    fun onlyWifiLikeInterfacesAreWelcome() {
        assertEquals(0, JamNet.rank("wlan0"))
        assertEquals(0, JamNet.rank("swlan0"))
        assertEquals(0, JamNet.rank("ap0"))
        assertEquals(-1, JamNet.rank("rmnet_data1"))
        assertEquals(-1, JamNet.rank("tun0"))
        assertEquals(1, JamNet.rank("bt-pan"))
    }

    @Test
    fun requestHeadIsParsedAndTheBodyIsLeftAlone() {
        val raw = "POST /upload?token=ab%20c&title=Caf%C3%A9 HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nHELLO"
        val request = HttpParser.parse(ByteArrayInputStream(raw.toByteArray(Charsets.ISO_8859_1)))
        assertNotNull(request)
        request!!
        assertEquals("POST", request.method)
        assertEquals("/upload", request.path)
        assertEquals("ab c", request.query["token"])
        assertEquals("Café", request.query["title"])
        assertEquals(5L, request.contentLength)
        assertEquals("HELLO", String(request.readBody(100)!!, Charsets.ISO_8859_1))
    }

    @Test
    fun badRequestsAreRefused() {
        fun parse(text: String) = HttpParser.parse(ByteArrayInputStream(text.toByteArray(Charsets.ISO_8859_1)))
        assertNull(parse("GET\r\n\r\n"))
        assertNull(parse("GET nopath HTTP/1.1\r\n\r\n"))
        assertNull(parse("GET / HTTP/1.1\r\nHost: x"))
        val big = HttpParser.parse(ByteArrayInputStream("POST / HTTP/1.1\r\nContent-Length: 50\r\n\r\n".toByteArray()))
        assertNull(big?.readBody(10))
        val short = HttpParser.parse(ByteArrayInputStream("POST / HTTP/1.1\r\nContent-Length: 50\r\n\r\nabc".toByteArray()))
        assertNull(short?.readBody(100))
    }

    @Test
    fun rangeHeadersAreUnderstood() {
        assertEquals(HttpRange.Full, HttpRange.parse(null, 1000))
        assertEquals(HttpRange.Full, HttpRange.parse("items=0-5", 1000))
        assertEquals(HttpRange.Partial(0, 999), HttpRange.parse("bytes=0-", 1000))
        assertEquals(HttpRange.Partial(100, 199), HttpRange.parse("bytes=100-199", 1000))
        assertEquals(HttpRange.Partial(900, 999), HttpRange.parse("bytes=900-5000", 1000))
        assertEquals(HttpRange.Partial(950, 999), HttpRange.parse("bytes=-50", 1000))
        assertEquals(HttpRange.Partial(0, 999), HttpRange.parse("bytes=-5000", 1000))
        assertEquals(HttpRange.Unsatisfiable, HttpRange.parse("bytes=1000-", 1000))
        assertEquals(HttpRange.Unsatisfiable, HttpRange.parse("bytes=200-100", 1000))
        assertEquals(HttpRange.Full, HttpRange.parse("bytes=0-1,5-6", 1000))
        assertEquals(HttpRange.Full, HttpRange.parse("bytes=abc-", 1000))
    }

    @Test
    fun theQuickestClockAnswerWins() {
        val slow = JamClock.sample(guestSendMs = 1000, guestReceiveMs = 1200, hostMs = 5100)
        val quick = JamClock.sample(guestSendMs = 2000, guestReceiveMs = 2010, hostMs = 2006)
        assertEquals(200L, slow.rttMs)
        assertEquals(1L, quick.offsetMs) // 2006 - 2005
        assertEquals(1L, JamClock.best(listOf(slow, quick)))
        assertNull(JamClock.best(emptyList()))
    }

    @Test
    fun theGuestFollowsTheHost() {
        val playing = JamState(1, "h", emptyList(), emptyList(), 0, true, positionMs = 10_000, anchorMs = 500)
        assertEquals(10_300L, JamSync.expectedPositionMs(playing, 800))
        assertEquals(10_000L, JamSync.expectedPositionMs(playing.copy(playing = false), 800))
        assertEquals(JamSync.Fix.Speed(1f), JamSync.fix(10_030, 10_000))
        assertEquals(JamSync.Fix.Speed(0.97f), JamSync.fix(10_100, 10_000)) // ahead: slow down
        assertEquals(JamSync.Fix.Speed(1.03f), JamSync.fix(9_900, 10_000)) // behind: speed up
        assertEquals(JamSync.Fix.Seek(10_000), JamSync.fix(11_000, 10_000))
        assertEquals(JamSync.Fix.Seek(0), JamSync.fix(5_000, -20))
    }

    @Test
    fun aGuestSongGoesRightAfterTheCurrentOneAndTheGuestSongsBeforeIt() {
        // current = 0; queue: [now, host, host]
        assertEquals(1, JamQueue.insertIndex(0, listOf(false, false, false)))
        // [now, guest, guest, host]: after the two waiting guest songs
        assertEquals(3, JamQueue.insertIndex(0, listOf(false, true, true, false)))
        // the current song is itself from a guest: it does not count as waiting
        assertEquals(1, JamQueue.insertIndex(0, listOf(true, false)))
        assertEquals(0, JamQueue.insertIndex(-1, emptyList()))
        assertEquals(2, JamQueue.insertIndex(5, listOf(false, false)))
    }

    @Test
    fun aFileIsDeletedOnlyAfterItsEntryWasSeenAndIsGone() {
        val janitor = JamFileJanitor()
        val file = File("a.mp3")
        janitor.track("o1", file)
        assertTrue(janitor.onQueue(listOf("x")).isEmpty()) // not seen yet: it may simply not be published yet
        assertTrue(janitor.onQueue(listOf("x", "o1")).isEmpty())
        assertEquals(listOf(file), janitor.onQueue(listOf("x")))
        assertFalse(janitor.onQueue(listOf("x")).contains(file)) // reported once
    }

    @Test
    fun gateWaitsForTheFirstSongsOnEveryPhone() {
        val queue = listOf("a", "b", "c", "d")
        val members = listOf("m1", "m2", "m3")
        val none = JamGate.evaluate(1, queue, 0, members, emptyMap())
        assertEquals(listOf("a"), none.wanted)
        assertEquals(2, none.needed)
        assertFalse(none.enough)
        val two = JamGate.evaluate(1, queue, 0, members, mapOf("m1" to setOf("a"), "m2" to setOf("a", "b")))
        assertEquals(2, two.ready)
        assertTrue(two.enough)
        assertFalse(two.all)
        val all = JamGate.evaluate(2, queue, 0, members, members.associateWith { setOf("a", "b") })
        assertEquals(listOf("a", "b"), all.wanted)
        assertTrue(all.all)
        val partial = JamGate.evaluate(2, queue, 0, members, members.associateWith { setOf("a") })
        assertEquals(0, partial.ready)
    }

    @Test
    fun gateSizeIsLimitedAndStartsFromTheCurrentSong() {
        val queue = listOf("a", "b", "c")
        assertEquals(listOf("b", "c"), JamGate.evaluate(9, queue, 1, listOf("m"), emptyMap()).wanted)
        assertEquals(listOf("a"), JamGate.evaluate(0, queue, 0, listOf("m"), emptyMap()).wanted)
    }

    @Test
    fun gateWithNoGuestsNeedsNobody() {
        val result = JamGate.evaluate(1, listOf("a"), 0, emptyList(), emptyMap())
        assertTrue(result.all)
        assertFalse(result.enough)
    }
}
