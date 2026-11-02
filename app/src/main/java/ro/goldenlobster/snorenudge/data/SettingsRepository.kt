package ro.goldenlobster.snorenudge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    suspend fun reset() {
        store.edit { it.clear() }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            sensitivity = this[SENSITIVITY]?.let { n -> Sensitivity.entries.firstOrNull { it.name == n } } ?: d.sensitivity,
            cooldownSec = this[COOLDOWN]?.takeIf { it in AppSettings.COOLDOWNS } ?: d.cooldownSec,
            startDelayMin = this[START_DELAY]?.takeIf { it in AppSettings.START_DELAYS } ?: d.startDelayMin,
            eventsToTrigger = this[EVENTS]?.takeIf { it in AppSettings.EVENTS } ?: d.eventsToTrigger,
            autoStopMinutes = this[AUTO_STOP]?.takeIf { it in AppSettings.AUTO_STOPS } ?: d.autoStopMinutes,
            strength = this[STRENGTH]?.let { n -> VibrationStrength.entries.firstOrNull { it.name == n } } ?: d.strength,
            maxNudges = this[MAX_NUDGES]?.takeIf { it in AppSettings.MAX_NUDGES } ?: d.maxNudges,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: AppSettings) {
        this[SENSITIVITY] = s.sensitivity.name
        this[COOLDOWN] = s.cooldownSec
        this[START_DELAY] = s.startDelayMin
        this[EVENTS] = s.eventsToTrigger
        this[AUTO_STOP] = s.autoStopMinutes
        this[STRENGTH] = s.strength.name
        this[MAX_NUDGES] = s.maxNudges
    }

    private companion object {
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val COOLDOWN = intPreferencesKey("cooldown_sec")
        val START_DELAY = intPreferencesKey("start_delay_min")
        val EVENTS = intPreferencesKey("events_to_trigger")
        val AUTO_STOP = intPreferencesKey("auto_stop_minutes")
        val STRENGTH = stringPreferencesKey("vibration_strength")
        val MAX_NUDGES = intPreferencesKey("max_nudges")
    }
}
