package com.reconvene.ui.discover

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMode
import com.reconvene.testing.TEST_HOST
import com.reconvene.testing.testPod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PodFilterTest {

    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 11, 16, 14, 0, 0, 0, zone).toInstant()
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone).toInstant()

    private val pod = testPod(startsAt = at(16, 15)).copy(
        title = "Algorithms exam review",
        course = "CS 330",
        building = "Mugar Library",
        meetingPoint = "2nd floor study tables",
        hostName = "Rhett",
    )

    private fun PodFilter.matches(pod: Pod, viewerUid: String? = "someone-else") = matches(pod, viewerUid, now, zone)

    @Test
    fun defaultFilterMatchesEveryPodAndIsNotActive() {
        assertFalse(PodFilter().isActive)
        assertTrue(PodFilter().matches(pod))
        assertFalse(PodFilter(query = "   ").isActive)
        assertTrue(PodFilter(query = "   ").matches(pod))
    }

    @Test
    fun anyChoiceMakesTheFilterActive() {
        assertTrue(PodFilter(query = "cs").isActive)
        assertTrue(PodFilter(openSpots = true).isActive)
        assertTrue(PodFilter(today = true).isActive)
        assertTrue(PodFilter(mine = true).isActive)
        assertTrue(PodFilter(modes = setOf(PodMode.TALK)).isActive)
    }

    @Test
    fun queryFindsTopicCourseBuildingMeetingPointAndHost() {
        listOf("exam", "330", "mugar", "tables", "rhett").forEach { word ->
            assertTrue(word, PodFilter(query = word).matches(pod))
        }
        assertFalse(PodFilter(query = "chemistry").matches(pod))
    }

    @Test
    fun queryIgnoresCaseAndSpacing() {
        assertTrue(PodFilter(query = "ALGORITHMS").matches(pod))
        assertTrue(PodFilter(query = "cs330").matches(pod))
        assertTrue(PodFilter(query = "  cs   330 ").matches(pod))
        assertTrue(PodFilter(query = "CS 330").matches(pod.copy(course = "CS330")))
    }

    @Test
    fun everyWordOfTheQueryHasToMatchInAnyOrder() {
        assertTrue(PodFilter(query = "mugar algorithms").matches(pod))
        assertFalse(PodFilter(query = "mugar chemistry").matches(pod))
    }

    @Test
    fun squashedWordsDoNotMatchAcrossTwoFields() {
        // "reviewcs" would only exist if the topic and the course were glued together.
        assertFalse(PodFilter(query = "reviewcs").matches(pod))
    }

    @Test
    fun aPodWithoutACourseStillMatchesOnItsOtherText() {
        assertTrue(PodFilter(query = "mugar").matches(pod.copy(course = null)))
        assertFalse(PodFilter(query = "330").matches(pod.copy(course = null)))
    }

    @Test
    fun openSpotsHidesFullPods() {
        val full = testPod(startsAt = at(16, 15), capacity = 2, members = 2)

        assertFalse(PodFilter(openSpots = true).matches(full))
        assertTrue(PodFilter(openSpots = true).matches(pod))
    }

    @Test
    fun todayKeepsOnlyPodsStartingToday() {
        assertTrue(PodFilter(today = true).matches(testPod(startsAt = at(16, 23))))
        assertFalse(PodFilter(today = true).matches(testPod(startsAt = at(17, 9))))
    }

    @Test
    fun mineKeepsPodsTheViewerHostsOrJoined() {
        val joined = testPod(startsAt = at(16, 15), members = 2)

        assertTrue(PodFilter(mine = true).matches(joined, viewerUid = TEST_HOST.uid))
        assertTrue(PodFilter(mine = true).matches(joined, viewerUid = "member-1"))
        assertFalse(PodFilter(mine = true).matches(joined, viewerUid = "someone-else"))
        assertFalse(PodFilter(mine = true).matches(joined, viewerUid = null))
    }

    @Test
    fun modesKeepPodsWithAnyChosenMode() {
        val talk = pod.copy(mode = PodMode.TALK)

        assertTrue(PodFilter(modes = setOf(PodMode.TALK, PodMode.COORDINATE)).matches(talk))
        assertFalse(PodFilter(modes = setOf(PodMode.QUIET)).matches(talk))
    }

    @Test
    fun withModeAddsAndRemovesAMode() {
        val both = PodFilter().withMode(PodMode.QUIET, on = true).withMode(PodMode.TALK, on = true)

        assertEquals(setOf(PodMode.QUIET, PodMode.TALK), both.modes)
        assertEquals(setOf(PodMode.TALK), both.withMode(PodMode.QUIET, on = false).modes)
    }

    @Test
    fun aPodHasToPassEveryPartOfTheFilter() {
        val filter = PodFilter(query = "algorithms", openSpots = true, today = true)

        assertTrue(filter.matches(pod))
        assertFalse(filter.copy(query = "chemistry").matches(pod))
        assertFalse(filter.matches(pod.copy(startsAt = at(17, 9), endsAt = at(17, 10))))
    }
}
