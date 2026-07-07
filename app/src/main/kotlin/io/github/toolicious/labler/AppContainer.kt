package io.github.toolicious.labler

import android.content.Context
import androidx.room.Room
import io.github.toolicious.labler.ble.PrinterManager
import io.github.toolicious.labler.data.AppDatabase
import io.github.toolicious.labler.data.BackupRepository
import io.github.toolicious.labler.data.HistoryRepository
import io.github.toolicious.labler.data.MIGRATION_1_2
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

    private val database = Room.databaseBuilder(context, AppDatabase::class.java, "labler.db")
        .addMigrations(MIGRATION_1_2)
        .build()

    val settings = SettingsRepository(context)
    val templateRepository = TemplateRepository(database.templateDao(), json)
    val historyRepository = HistoryRepository(database.printHistoryDao(), json)
    val templateJson = TemplateJson(json)
    val backup = BackupRepository(templateRepository, settings, json)
    val printerManager = PrinterManager(context, settings, applicationScope)
}
