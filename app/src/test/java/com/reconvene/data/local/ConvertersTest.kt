package com.reconvene.data.local

import com.reconvene.data.model.PodMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun vibesAreStoredByName() {
        PodMode.entries.forEach { mode ->
            assertEquals(mode, converters.nameToMode(converters.modeToName(mode)))
        }
    }

    @Test
    fun anOlderCoordinateRowIsOpenToChatAndAnUnknownOneIsQuiet() {
        assertEquals(PodMode.TALK, converters.nameToMode("COORDINATE"))
        assertEquals(PodMode.QUIET, converters.nameToMode("LOUD"))
    }
}
