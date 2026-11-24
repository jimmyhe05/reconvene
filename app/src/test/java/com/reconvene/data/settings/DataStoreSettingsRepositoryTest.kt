package com.reconvene.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        File(folder.root, "settings.preferences_pb")
    }

    @Test
    fun appearanceFollowsThePhoneUntilChosen() = runTest(UnconfinedTestDispatcher()) {
        val settings = DataStoreSettingsRepository(store())
        assertEquals(Appearance.SYSTEM, settings.appearance.first())

        settings.setAppearance(Appearance.DARK)
        assertEquals(Appearance.DARK, settings.appearance.first())
    }

    @Test
    fun anUnknownAppearanceFollowsThePhone() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.edit { it[stringPreferencesKey("appearance")] = "SEPIA" }

        assertEquals(Appearance.SYSTEM, DataStoreSettingsRepository(store).appearance.first())
    }

    @Test
    fun remindersAreOffUntilChosen() = runTest(UnconfinedTestDispatcher()) {
        val settings = DataStoreSettingsRepository(store())
        assertEquals(ReminderLead.OFF, settings.reminderLead.first())

        settings.setReminderLead(ReminderLead.MINUTES_30)
        assertEquals(ReminderLead.MINUTES_30, settings.reminderLead.first())
        assertEquals(Appearance.SYSTEM, settings.appearance.first())
    }
}
