package io.github.toolicious.labler

import android.content.Context
import androidx.room.Room
import io.github.toolicious.labler.ble.PrinterManager
import io.github.toolicious.labler.data.AppDatabase
import io.github.toolicious.labler.data.BackupRepository
import io.github.toolicious.labler.data.CustomFontRepository
import io.github.toolicious.labler.data.DevSchemaRepair
import io.github.toolicious.labler.data.HistoryRepository
import io.github.toolicious.labler.data.MIGRATION_1_2
import io.github.toolicious.labler.data.MIGRATION_2_3
import io.github.toolicious.labler.data.MIGRATION_3_4
import io.github.toolicious.labler.data.MIGRATION_4_5
import io.github.toolicious.labler.data.SettingsRepository
import io.github.toolicious.labler.data.TemplateJson
import io.github.toolicious.labler.data.TemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/** Manual dependency root (deliberately without a DI framework). */
class AppContainer(context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
        // Fall back unknown enum values (e.g. removed fonts in old templates) to
        // the property's default value instead of failing.
        coerceInputValues = true
    }

    private val database = openDatabase(context)

    /**
     * The database, and in a development build a second and third go at it.
     *
     * Branches under development take turns on one phone and each brings its own unreleased
     * schema, so Room regularly meets a database that carries someone else's columns and refuses
     * to open it. [DevSchemaRepair] fits it to this build instead, first by adding what is missing
     * and then, if that was not enough, by making the tables exactly what this build declares.
     *
     * A release build gets none of that: there a database Room cannot open means a migration is
     * missing, and that has to fail loudly rather than be papered over.
     */
    private fun openDatabase(context: Context): AppDatabase {
        fun build() = Room.databaseBuilder(context, AppDatabase::class.java, "labler.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        if (!BuildConfig.DEBUG) return build()
        // Opened here and not on first use, so a repair happens before anything reads.
        fun openOrNull(db: AppDatabase): AppDatabase? = runCatching {
            db.openHelper.writableDatabase
            db
        }.getOrElse {
            runCatching { db.close() }
            null
        }
        openOrNull(build())?.let { return it }
        DevSchemaRepair.run(context, "labler.db", rebuild = false)
        openOrNull(build())?.let { return it }
        DevSchemaRepair.run(context, "labler.db", rebuild = true)
        return build()
    }

    val settings = SettingsRepository(context)
    val customFonts = CustomFontRepository(context, settings, json, applicationScope)
    val templateRepository = TemplateRepository(database.templateDao(), json)
    val historyRepository = HistoryRepository(database.printHistoryDao(), json)
    val templateJson = TemplateJson(json)
    val backup = BackupRepository(templateRepository, settings, json)
    val printerManager = PrinterManager(context, settings, applicationScope)
}
