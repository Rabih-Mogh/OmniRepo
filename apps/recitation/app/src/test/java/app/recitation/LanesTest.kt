package app.recitation

import app.recitation.prepare.Lanes
import app.recitation.prepare.Lanes.Waiting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What each lane of the background work takes up next. */
class LanesTest {
    private val waiting = listOf(Waiting("a", hasSound = false), Waiting("b", hasSound = true), Waiting("c", hasSound = false), Waiting("d", hasSound = true))

    @Test
    fun theSoundsAreFetchedInOrderTheOpenOneFirst() {
        assertEquals("a", Lanes.toFetch(waiting, open = null, online = true, refused = false))
        assertEquals("c", Lanes.toFetch(waiting, open = "c", online = true, refused = false))
        // The open one has its sound already: the rest in order.
        assertEquals("a", Lanes.toFetch(waiting, open = "d", online = true, refused = false))
    }

    @Test
    fun nothingIsFetchedWithNoConnectionOrWhileYouTubeRefuses() {
        assertNull(Lanes.toFetch(waiting, open = "c", online = false, refused = false))
        assertNull(Lanes.toFetch(waiting, open = "c", online = true, refused = true))
        assertNull(Lanes.toFetch(waiting.filter { it.hasSound }, open = null, online = true, refused = false))
    }

    @Test
    fun onlyWhatHasItsSoundIsListenedToTheOpenOneFirst() {
        assertEquals("b", Lanes.toHear(waiting, open = null, online = true, fetching = null))
        assertEquals("d", Lanes.toHear(waiting, open = "d", online = true, fetching = null))
        // The open one's sound isn't here yet: the listening carries on with the rest until it is.
        assertEquals("b", Lanes.toHear(waiting, open = "a", online = true, fetching = "a"))
        assertNull(Lanes.toHear(waiting.filter { !it.hasSound }, open = "a", online = true, fetching = "a"))
    }

    @Test
    fun aSoundThatHasOnlyJustArrivedIsLeftToTheFetchingUntilItIsDoneWithIt() {
        assertEquals("d", Lanes.toHear(waiting, open = "b", online = true, fetching = "b"))
    }

    @Test
    fun withNoConnectionOnlyWhatNeedsNothingMoreFetchedIsListenedTo() {
        val some = listOf(Waiting("a", hasSound = true, needsFetched = true), Waiting("b", hasSound = true))
        assertEquals("b", Lanes.toHear(some, open = "a", online = false, fetching = null))
        assertEquals("a", Lanes.toHear(some, open = "a", online = true, fetching = null))
    }
}
