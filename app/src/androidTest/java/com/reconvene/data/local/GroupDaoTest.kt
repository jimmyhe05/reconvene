package com.reconvene.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.MessageStatus
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.data.model.SessionProposal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class GroupDaoTest {

    private lateinit var database: ReconveneDatabase
    private lateinit var groupDao: GroupDao
    private lateinit var messageDao: MessageDao

    private val noon = Instant.parse("2026-11-16T17:00:00Z")
    private val maya = PodMember("maya", "Maya")
    private val sam = PodMember("sam", "Sam")

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReconveneDatabase::class.java,
        ).build()
        groupDao = database.groupDao()
        messageDao = database.messageDao()
    }

    @After
    fun closeDatabase() = database.close()

    private fun group(id: String, name: String = "Group $id") = Group(
        id = id,
        name = name,
        course = null,
        fromPodId = "pod-$id",
        hostUid = maya.uid,
        members = listOf(maya),
    )

    private fun message(id: String, groupId: String, secondsAfterNoon: Long = 0) = GroupMessage(
        id = id,
        groupId = groupId,
        authorUid = maya.uid,
        authorName = maya.name,
        text = "Hello $id",
        sentAt = noon.plusSeconds(secondsAfterNoon),
        status = MessageStatus.SENT,
    )

    @Test
    fun aGroupComesBackWithItsProposalHandoverAndInvites() = runTest {
        val full = group("g1", name = "Algorithms").copy(
            course = "CS 501",
            members = listOf(maya, sam),
            invited = listOf(PodMember("noah", "Noah")),
            hostHandover = sam,
            proposal = SessionProposal(
                id = "next",
                startsAt = noon,
                endsAt = noon.plusSeconds(5400),
                building = "Mugar Library",
                meetingPoint = "2nd floor",
                mode = PodMode.TALK,
                byUid = sam.uid,
                yes = listOf(sam),
                no = listOf(maya.uid),
            ),
        )
        val plain = group("g2", name = "Biology")

        groupDao.upsertAll(listOf(plain, full).map { it.toEntity() })

        assertEquals(listOf(full, plain), groupDao.observeAll().first().map { it.toGroup() })
    }

    @Test
    fun replacingTheListDropsOtherGroupsAndTheirChat() = runTest {
        groupDao.upsertAll(listOf(group("keep"), group("gone")).map { it.toEntity() })
        messageDao.upsertAll(listOf(message("m1", "keep"), message("m2", "gone")).map { it.toEntity() })

        groupDao.replaceAll(listOf(group("keep").toEntity()))

        assertEquals(listOf("keep"), groupDao.observeAll().first().map { it.id })
        assertEquals(listOf("m1"), messageDao.observeForGroup("keep").first().map { it.id })
        assertEquals(emptyList<MessageEntity>(), messageDao.observeForGroup("gone").first())
    }

    @Test
    fun deletingAGroupTakesItsChatAndClearEmptiesBoth() = runTest {
        groupDao.upsertAll(listOf(group("a"), group("b")).map { it.toEntity() })
        messageDao.upsertAll(listOf(message("m1", "a"), message("m2", "b")).map { it.toEntity() })

        groupDao.deleteWithMessages("a")
        assertNull(groupDao.getById("a"))
        assertNull(messageDao.getById("m1"))

        groupDao.clear()
        assertEquals(emptyList<GroupEntity>(), groupDao.observeAll().first())
        assertNull(messageDao.getById("m2"))
    }

    @Test
    fun chatReadsOldestFirstAndStatusCanChange() = runTest {
        messageDao.upsertAll(listOf(message("late", "g", 60), message("early", "g", 0)).map { it.toEntity() })

        messageDao.setStatus("late", MessageStatus.FAILED)

        val chat = messageDao.observeForGroup("g").first().map { it.toMessage() }
        assertEquals(listOf("early", "late"), chat.map { it.id })
        assertEquals(MessageStatus.FAILED, chat.last().status)
    }
}
