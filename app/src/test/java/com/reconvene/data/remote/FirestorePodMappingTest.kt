package com.reconvene.data.remote

import com.reconvene.data.model.PodMode
import com.reconvene.testing.testPod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.Date

class FirestorePodMappingTest {

    private val noon = Instant.parse("2026-11-10T17:00:00Z")

    @Test
    fun podSurvivesRoundTrip() {
        val pod = testPod(id = "a1", startsAt = noon, members = 2, canceled = true)

        assertEquals(pod, podFromFirestore("a1", pod.toFirestore()))
    }

    @Test
    fun documentStoresTimesAsDatesAndModeAsName() {
        val data = testPod(id = "a1", startsAt = noon).copy(course = null, mode = PodMode.TALK).toFirestore()

        assertEquals(Date.from(noon), data[PodFields.STARTS_AT])
        assertEquals("TALK", data[PodFields.MODE])
        assertEquals(4L, data[PodFields.CAPACITY])
        // course is written as null rather than left out, so the security rules can always read it
        assertNull(data[PodFields.COURSE])
        assertFalse("id" in data)
    }

    @Test
    fun missingOrMistypedFieldGivesNull() {
        val data = testPod(id = "a1", startsAt = noon).toFirestore()

        assertNull(podFromFirestore("a1", data - PodFields.TITLE))
        assertNull(podFromFirestore("a1", data + (PodFields.STARTS_AT to "tomorrow")))
        assertNull(podFromFirestore("a1", data + (PodFields.MODE to "LOUD")))
    }

    @Test
    fun missingCanceledMeansNotCanceled() {
        val data = testPod(id = "a1", startsAt = noon, canceled = true).toFirestore()

        assertFalse(podFromFirestore("a1", data - PodFields.CANCELED)!!.canceled)
    }
}
