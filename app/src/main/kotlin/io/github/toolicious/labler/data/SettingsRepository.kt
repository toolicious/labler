package io.github.toolicious.labler.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.toolicious.labler.model.LabelFont
import io.github.toolicious.labler.printer.DeviceNames
import io.github.toolicious.labler.printer.PrinterFamily
import io.github.toolicious.labler.printer.ProtocolTuning
import io.github.toolicious.labler.printer.Tunable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class SavedPrinter(
    val address: String,
    val name: String,
    /** Absent for a printer paired before the app knew about families, hence the default. */
    val family: PrinterFamily = PrinterFamily.DEFAULT,
)

/** How the saved labels are laid out on the overview. */
enum class TemplateViewMode {
    /** Adaptive grid of cards, each with a large preview. */
    GRID,

    /** One row per label, which fits more of them on the screen. */
    LIST,
    ;

    companion object {
        val DEFAULT = GRID

        /** [valueOf] that falls back instead of throwing, for values read from storage. */
        fun ofName(name: String?): TemplateViewMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** What the overview orders the saved labels by. */
enum class TemplateSort(
    /** Where a fresh pick starts, which is the reading most people want first. */
    val ascendingByDefault: Boolean,
) {
    /** Alphabetical, so A to Z is the way up. */
    NAME(ascendingByDefault = true),

    /** Last edited, where the newest first is the way most lists read. */
    UPDATED(ascendingByDefault = false),

    /** Labels printed from it, most used first. */
    PRINTS(ascendingByDefault = false),
    ;

    companion object {
        val DEFAULT = UPDATED

        /** [valueOf] that falls back instead of throwing, for values read from storage. */
        fun ofName(name: String?): TemplateSort = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * How the overview presents its labels. One bundle rather than three settings read one by one,
 * because they are all needed before the first frame can be drawn.
 */
data class OverviewPrefs(
    val viewMode: TemplateViewMode = TemplateViewMode.DEFAULT,
    val sort: TemplateSort = TemplateSort.DEFAULT,
    val ascending: Boolean = TemplateSort.DEFAULT.ascendingByDefault,
)

/** Face last picked for a bar code caption, so the next code element can start with it. */
data class CaptionFont(
    val font: LabelFont = LabelFont.SANS,
    /** A font of the user's own, which wins over [font]. */
    val custom: String? = null,
)

/**
 * More than fit on a row, a tablet in landscape included, so that removing one always has
 * something left to slide up into its place.
 */
private const val MAX_RECENT_ICONS = 32

class SettingsRepository(private val context: Context) {

    private object Keys {
        val PRINTER_ADDRESS = stringPreferencesKey("printer_address")
        val PRINTER_NAME = stringPreferencesKey("printer_name")
        val PRINTER_FAMILY = stringPreferencesKey("printer_family")
        val DEFAULT_TAPE_WIDTH = intPreferencesKey("default_tape_width_mm")
        val DEFAULT_LENGTH = intPreferencesKey("default_length_mm")
        val DEFAULT_DIE_CUT = booleanPreferencesKey("default_die_cut")
        val LAST_SYMBOL_TAB = intPreferencesKey("last_symbol_tab")
        val PRINT_DENSITY = intPreferencesKey("print_density")
        val CUSTOM_FONTS = stringPreferencesKey("custom_fonts")
        val RECENT_ICONS = stringPreferencesKey("recent_icons")
        val TEMPLATE_VIEW_MODE = stringPreferencesKey("template_view_mode")
        val TEMPLATE_SORT = stringPreferencesKey("template_sort")
        val TEMPLATE_SORT_ASC = booleanPreferencesKey("template_sort_asc")
        val CAPTION_FONT = stringPreferencesKey("caption_font")
        val CAPTION_CUSTOM_FONT = stringPreferencesKey("caption_custom_font")

        /**
         * One key per family and tunable, so a value a future printer needs fits without
         * touching the storage. Everything is kept as text and parsed where it is used.
         */
        fun tuning(family: PrinterFamily, tunable: Tunable) =
            stringPreferencesKey("tuning_${family.name}_${tunable.name}")

        /** The measurement a calibration came from, kept so it can be shown back. */
        fun calibrationMeasurement(family: PrinterFamily) =
            stringPreferencesKey("calibration_measured_${family.name}")
    }

    val savedPrinter: Flow<SavedPrinter?> = context.dataStore.data.map { prefs ->
        val address = prefs[Keys.PRINTER_ADDRESS] ?: return@map null
        // Cleaned on the way out, so a name stored before that cleaning existed is fixed too.
        SavedPrinter(
            address = address,
            name = prefs[Keys.PRINTER_NAME]?.let(DeviceNames::clean) ?: address,
            family = PrinterFamily.ofName(prefs[Keys.PRINTER_FAMILY]),
        )
    }

    /**
     * Calibration overrides per printer family. Only a development build ever writes any, and
     * a family with nothing stored is left at what its protocol declares.
     */
    val protocolTuning: Flow<Map<PrinterFamily, ProtocolTuning>> = context.dataStore.data.map { prefs ->
        PrinterFamily.entries.associateWith { family ->
            ProtocolTuning(
                Tunable.entries.mapNotNull { tunable ->
                    prefs[Keys.tuning(family, tunable)]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { tunable to it }
                }.toMap()
            )
        }.filterValues { !it.isEmpty }
    }

    /** Stores one calibration value, or clears it when [value] is null or blank. */
    suspend fun saveTuning(family: PrinterFamily, tunable: Tunable, value: String?) {
        val key = Keys.tuning(family, tunable)
        context.dataStore.edit {
            if (value.isNullOrBlank()) it.remove(key) else it[key] = value
        }
    }

    /** What was typed into the calibration dialog, per family, purely for display. */
    val calibrationMeasurements: Flow<Map<PrinterFamily, String>> =
        context.dataStore.data.map { prefs ->
            PrinterFamily.entries.mapNotNull { family ->
                prefs[Keys.calibrationMeasurement(family)]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { family to it }
            }.toMap()
        }

    suspend fun saveCalibrationMeasurement(family: PrinterFamily, value: String?) {
        val key = Keys.calibrationMeasurement(family)
        context.dataStore.edit {
            if (value.isNullOrBlank()) it.remove(key) else it[key] = value
        }
    }

    /** Puts every family back on the values its protocol declares. */
    suspend fun clearTuning(family: PrinterFamily) {
        context.dataStore.edit { prefs ->
            Tunable.entries.forEach { prefs.remove(Keys.tuning(family, it)) }
        }
    }

    val defaultTapeWidthMm: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_TAPE_WIDTH] ?: 12 }
    val defaultLengthMm: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_LENGTH] ?: 40 }
    val defaultDieCut: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEFAULT_DIE_CUT] ?: true }

    suspend fun savePrinter(address: String, name: String, family: PrinterFamily) {
        context.dataStore.edit {
            it[Keys.PRINTER_ADDRESS] = address
            it[Keys.PRINTER_NAME] = name
            it[Keys.PRINTER_FAMILY] = family.name
        }
    }

    suspend fun forgetPrinter() {
        context.dataStore.edit {
            it.remove(Keys.PRINTER_ADDRESS)
            it.remove(Keys.PRINTER_NAME)
            it.remove(Keys.PRINTER_FAMILY)
        }
    }

    suspend fun saveDefaultLabel(tapeWidthMm: Int, lengthMm: Int, dieCut: Boolean) {
        context.dataStore.edit {
            it[Keys.DEFAULT_TAPE_WIDTH] = tapeWidthMm
            it[Keys.DEFAULT_LENGTH] = lengthMm
            it[Keys.DEFAULT_DIE_CUT] = dieCut
        }
    }

    /**
     * Last used tab in the symbol picker. The numbers are identities, not positions, see
     * SymbolPickerSheet; 2 is the icon tab, which is where the picker opens until a choice is made.
     */
    val lastSymbolTab: Flow<Int> = context.dataStore.data.map { it[Keys.LAST_SYMBOL_TAB] ?: 2 }

    suspend fun saveLastSymbolTab(tab: Int) {
        context.dataStore.edit { it[Keys.LAST_SYMBOL_TAB] = tab }
    }

    /**
     * Font last picked for a bar code caption. A font that is no longer installed reads back as
     * it was stored; the renderer falls back on its own, and the picker shows the name.
     */
    val captionFont: Flow<CaptionFont> = context.dataStore.data.map { prefs ->
        CaptionFont(
            font = LabelFont.entries.firstOrNull { it.name == prefs[Keys.CAPTION_FONT] }
                ?: LabelFont.SANS,
            custom = prefs[Keys.CAPTION_CUSTOM_FONT]?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun saveCaptionFont(caption: CaptionFont) {
        context.dataStore.edit {
            it[Keys.CAPTION_FONT] = caption.font.name
            if (caption.custom.isNullOrBlank()) it.remove(Keys.CAPTION_CUSTOM_FONT)
            else it[Keys.CAPTION_CUSTOM_FONT] = caption.custom
        }
    }

    /**
     * Layout and order of the overview. The enums are stored by name, so the constants keep their
     * spelling; filters are deliberately not here, they are meant to last only as long as the
     * screen does.
     */
    val overviewPrefs: Flow<OverviewPrefs> = context.dataStore.data.map { prefs ->
        val sort = TemplateSort.ofName(prefs[Keys.TEMPLATE_SORT])
        OverviewPrefs(
            viewMode = TemplateViewMode.ofName(prefs[Keys.TEMPLATE_VIEW_MODE]),
            sort = sort,
            ascending = prefs[Keys.TEMPLATE_SORT_ASC] ?: sort.ascendingByDefault,
        )
    }

    suspend fun saveOverviewPrefs(prefs: OverviewPrefs) {
        context.dataStore.edit {
            it[Keys.TEMPLATE_VIEW_MODE] = prefs.viewMode.name
            it[Keys.TEMPLATE_SORT] = prefs.sort.name
            it[Keys.TEMPLATE_SORT_ASC] = prefs.ascending
        }
    }

    /**
     * Icons picked most recently, newest first. Stored as one comma-separated line, which is safe
     * because an icon name is only lower case letters, digits and underscores. Rather more are kept
     * than fit on the row, so that removing one reveals the next instead of leaving a gap.
     */
    val recentIcons: Flow<List<String>> = context.dataStore.data.map { splitIcons(it[Keys.RECENT_ICONS]) }

    suspend fun addRecentIcon(name: String) {
        context.dataStore.edit { prefs ->
            val kept = splitIcons(prefs[Keys.RECENT_ICONS]).filter { it != name }
            prefs[Keys.RECENT_ICONS] = (listOf(name) + kept).take(MAX_RECENT_ICONS).joinToString(",")
        }
    }

    suspend fun removeRecentIcon(name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RECENT_ICONS] = splitIcons(prefs[Keys.RECENT_ICONS])
                .filter { it != name }
                .joinToString(",")
        }
    }

    private fun splitIcons(raw: String?): List<String> =
        raw.orEmpty().split(',').filter { it.isNotEmpty() }

    /**
     * Experimental 0x1F print density: 0 = off (default protocol, print path unchanged),
     * 1..15 = darkness level sent as the 1F 70 01 n command. Not verified on the P15.
     */
    val printDensity: Flow<Int> = context.dataStore.data.map { it[Keys.PRINT_DENSITY] ?: 0 }

    suspend fun savePrintDensity(level: Int) {
        context.dataStore.edit { it[Keys.PRINT_DENSITY] = level.coerceIn(0, 15) }
    }

    /**
     * Serialized list of the fonts the user added. It lives here rather than in its own
     * DataStore because a second Preferences instance on the same file would fail at runtime;
     * CustomFontRepository owns the encoding and the font files themselves.
     */
    val customFontsJson: Flow<String> = context.dataStore.data.map { it[Keys.CUSTOM_FONTS] ?: "[]" }

    suspend fun saveCustomFonts(raw: String) {
        context.dataStore.edit { it[Keys.CUSTOM_FONTS] = raw }
    }
}
