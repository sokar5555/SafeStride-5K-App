package com.example.couchto5k.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.couchto5k.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DefaultSettingsRepositoryTest {

    @Test
    fun `themeMode, audioDuckingEnabled, and countdownVoiceCuesEnabled expose expected defaults and persist updates`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val repository = DefaultSettingsRepository(dataStore)

        // Fresh-install defaults preserve white-and-blue Light theme and audio cues enabled
        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
        assertTrue(repository.audioDuckingEnabled.first())
        assertTrue(repository.countdownVoiceCuesEnabled.first())

        // Persist updates
        repository.setThemeMode(ThemeMode.DARK)
        repository.setAudioDuckingEnabled(false)
        repository.setCountdownVoiceCuesEnabled(false)
        repository.setProgramStartDate(LocalDate.of(2026, 11, 13))

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
        assertFalse(repository.audioDuckingEnabled.first())
        assertFalse(repository.countdownVoiceCuesEnabled.first())
        assertEquals(LocalDate.of(2026, 11, 13), repository.programStartDate.first())

        repository.setThemeMode(ThemeMode.SYSTEM)
        repository.clearProgramStartDate()
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        assertNull(repository.programStartDate.first())
    }

    @Test
    fun `corrupt stored themeMode value safely falls back to LIGHT`() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("theme_mode")] = "UNKNOWN_THEME"
            }
        }
        val repository = DefaultSettingsRepository(dataStore)

        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            return mutex.withLock {
                val updated = transform(state.value)
                state.value = updated
                updated
            }
        }
    }
}
