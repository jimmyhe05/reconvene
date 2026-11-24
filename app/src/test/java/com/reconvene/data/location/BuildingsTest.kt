package com.reconvene.data.location

import com.reconvene.testing.testPod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BuildingsTest {

    private val noon = Instant.parse("2026-11-24T17:00:00Z")
    private val mugar = buildingLocations.getValue("Mugar Library")

    @Test
    fun oneDegreeOfLatitudeIsAbout111Kilometers() {
        val meters = GeoPoint(42.0, -71.0).metersTo(GeoPoint(43.0, -71.0))

        assertEquals(111_195.0, meters, 5.0)
        assertEquals(0.0, mugar.metersTo(mugar), 0.0)
    }

    @Test
    fun walkingTimeAllowsForStreetsAndIsAtLeastAMinute() {
        assertEquals(1, walkingMinutes(0.0))
        // 400 m in a straight line is about 520 m on foot, 6.5 minutes, so 7.
        assertEquals(7, walkingMinutes(400.0))
    }

    @Test
    fun aPodInAKnownBuildingHasAWalkingTimeAndOthersDoNot() {
        val atMugar = testPod(id = "a1", startsAt = noon).copy(building = "Mugar Library")
        val elsewhere = atMugar.copy(building = "My dorm lounge")

        assertEquals(1, atMugar.walkingMinutesFrom(mugar))
        assertNull(elsewhere.walkingMinutesFrom(mugar))
    }

    @Test
    fun nearerBuildingsTakeLessTime() {
        val questrom = buildingLocations.getValue("Questrom")
        val toKilachand = questrom.metersTo(buildingLocations.getValue("Kilachand Center"))
        val toGsu = questrom.metersTo(buildingLocations.getValue("GSU"))

        assertTrue(toKilachand < toGsu)
    }

    @Test
    fun aKnownBuildingOpensAsAPinAndAnyOtherAsACampusSearch() {
        val atMugar = testPod(id = "a1", startsAt = noon).copy(building = "Mugar Library")

        assertEquals("geo:42.351,-71.1079?q=42.351,-71.1079(Mugar%20Library)", atMugar.mapLink())
        assertEquals(
            "geo:0,0?q=Hillel%20House%2C%20Boston%20University",
            atMugar.copy(building = "Hillel House").mapLink(),
        )
    }
}
