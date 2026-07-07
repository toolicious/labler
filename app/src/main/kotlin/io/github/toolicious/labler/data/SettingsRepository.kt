package io.github.toolicious.labler.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class SavedPrinter(val address: String, val name: String)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val PRINTER_ADDRESS = stringPreferencesKey("printer_address")
        val PRINTER_NAME = stringPreferencesKey("printer_name")
        val DEFAULT_TAPE_WIDTH = intPreferencesKey("default_tape_width_mm")
        val DEFAULT_LENGTH = intPreferencesKey("default_length_mm")
        val DEFAULT_DIE_CUT = booleanPreferencesKey("default_die_cut")
        val LAST_SYMBOL_TAB = intPreferencesKey("last_symbol_tab")
    }

    val savedPrinter: Flow<SavedPrinter?> = context.dataStore.data.map { prefs ->
        val address = prefs[Keys.PRINTER_ADDRESS] ?: return@map null
        SavedPrinter(address, prefs[Keys.PRINTER_NAME] ?: address)
    }

    val defaultTapeWidthMm: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_TAPE_WIDTH] ?: 12 }
    val defaultLengthMm: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_LENGTH] ?: 40 }
    val defaultDieCut: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEFAULT_DIE_CUT] ?: true }

    suspend fun savePrinter(address: String, name: String) {
        context.dataStore.edit {
            it[Keys.PRINTER_ADDRESS] = address
            it[Keys.PRINTER_NAME] = name
        }
    }

    suspend fun forgetPrinter() {
        context.dataStore.edit {
            it.remove(Keys.PRINTER_ADDRESS)
            it.remove(Keys.PRINTER_NAME)
        }
    }

    suspend fun saveDefaultLabel(tapeWidthMm: Int, lengthMm: Int, dieCut: Boolean) {
        context.dataStore.edit {
            it[Keys.DEFAULT_TAPE_WIDTH] = tapeWidthMm
            it[Keys.DEFAULT_LENGTH] = lengthMm
            it[Keys.DEFAULT_DIE_CUT] = dieCut
        }
    }

    /** Last used tab in the symbol/emoji dialog (0 = symbols, 1 = emojis). */
    val lastSymbolTab: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_SYMBOL_TAB] ?: 0 }

    suspend fun saveLastSymbolTab(tab: Int) {
        context.dataStore.edit { it[Keys.LAST_SYMBOL_TAB] = tab }
    }
}
