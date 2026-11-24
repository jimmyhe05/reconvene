package com.reconvene.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Light or dark: follow the phone (and its dark-theme schedule), or always one of them.
enum class Appearance { SYSTEM, LIGHT, DARK }

// How long before a pod starts to remind the user, or not at all.
enum class ReminderLead(val minutes: Long) { OFF(0), MINUTES_15(15), MINUTES_30(30), HOUR_1(60) }

// Choices that belong to this phone, not the account, so they stay on the device.
interface SettingsRepository {
    val appearance: Flow<Appearance>

    suspend fun setAppearance(appearance: Appearance)

    // Off until the user turns it on, since it needs permission to notify.
    val reminderLead: Flow<ReminderLead>

    suspend fun setReminderLead(lead: ReminderLead)
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {

    // An unknown value, say from a newer build, falls back to following the phone.
    override val appearance: Flow<Appearance> = store.data.map { preferences ->
        Appearance.entries.firstOrNull { it.name == preferences[APPEARANCE] } ?: Appearance.SYSTEM
    }

    override suspend fun setAppearance(appearance: Appearance) {
        store.edit { it[APPEARANCE] = appearance.name }
    }

    override val reminderLead: Flow<ReminderLead> = store.data.map { preferences ->
        ReminderLead.entries.firstOrNull { it.name == preferences[REMINDER_LEAD] } ?: ReminderLead.OFF
    }

    override suspend fun setReminderLead(lead: ReminderLead) {
        store.edit { it[REMINDER_LEAD] = lead.name }
    }

    private companion object {
        val APPEARANCE = stringPreferencesKey("appearance")
        val REMINDER_LEAD = stringPreferencesKey("reminder_lead")
    }
}
