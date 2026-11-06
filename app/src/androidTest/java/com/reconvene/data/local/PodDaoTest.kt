package com.reconvene.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PodDaoTest {

    private lateinit var database: ReconveneDatabase
    private lateinit var podDao: PodDao

    private val noon = Instant.parse("2026-11-10T17:00:00Z")

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReconveneDatabase::class.java,
        ).build()
        podDao = database.podDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun podsComeBackInStartOrderWithEveryField() = runTest {
        val later = pod(id = "later", startsAt = noon.plusSeconds(3600), course = null)
        val earlier = pod(id = "earlier", startsAt = noon, members = listOf("Maya", "Sam"))

        podDao.upsertAll(listOf(later, earlier).map { it.toEntity() })

        assertEquals(listOf(earlier, later), podDao.observeAll().first().map { it.toPod() })
    }

    @Test
    fun upsertReplacesPodWithSameId() = runTest {
        podDao.upsertAll(listOf(pod(id = "a1").toEntity()))
        podDao.upsertAll(listOf(pod(id = "a1", members = listOf("Maya", "Jordan")).toEntity()))

        val stored = podDao.observeAll().first().map { it.toPod() }
        assertEquals(listOf(listOf("Maya", "Jordan")), stored.map { it.memberNames })
    }

    private fun pod(
        id: String,
        startsAt: Instant = noon,
        course: String? = "CS 501",
        members: List<String> = listOf("Maya"),
    ) = Pod(
        id = id,
        title = "Study session $id",
        course = course,
        building = "Mugar Library",
        meetingPoint = "2nd floor",
        startsAt = startsAt,
        endsAt = startsAt.plusSeconds(5400),
        capacity = 4,
        hostName = "Maya",
        memberNames = members,
        mode = PodMode.TALK,
    )
}
