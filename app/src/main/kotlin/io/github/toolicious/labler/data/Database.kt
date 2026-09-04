package io.github.toolicious.labler.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tapeWidthMm: Int,
    val lengthMm: Int,
    val media: String,
    /**
     * Continuous tape whose length follows the content; lengthMm is then the minimum.
     * The default is declared here as well, so the exported schema matches what MIGRATION_2_3
     * adds and Room's validation cannot trip over a differing default.
     */
    @ColumnInfo(defaultValue = "0")
    val autoLength: Boolean = false,
    /** Manual mode plus the blank tape in front of the content; see LabelSpec. */
    @ColumnInfo(defaultValue = "0")
    val manualEdges: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val leadingMm: Int = 0,
    /** Blank tape in dots between the content and an edge the app places itself; see LabelSpec. */
    @ColumnInfo(defaultValue = "8")
    val marginPx: Int = 8,
    /** Printer family the label was designed for; see MIGRATION_5_6 for the default. */
    @ColumnInfo(defaultValue = "PHOMEMO")
    val family: String = "PHOMEMO",
    val elementsJson: String,
    val schemaVersion: Int,
    val favorite: Boolean,
    val counterValue: Int,
    /** Labels printed from this template, copies counted; see MIGRATION_6_7. */
    @ColumnInfo(defaultValue = "0")
    val printCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface TemplateDao {

    @Query("SELECT * FROM templates ORDER BY favorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: String): TemplateEntity?

    @Upsert
    suspend fun upsert(entity: TemplateEntity)

    @Query("UPDATE templates SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    /**
     * Raises the print counter. Deliberately its own statement rather than a full save, so that
     * printing leaves updatedAt alone and does not read as an edit.
     */
    @Query("UPDATE templates SET printCount = printCount + :by WHERE id = :id")
    suspend fun addPrints(id: String, by: Int)

    @Query("UPDATE templates SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE templates SET counterValue = :value WHERE id = :id")
    suspend fun setCounter(id: String, value: Int)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM templates")
    suspend fun getAllOnce(): List<TemplateEntity>

    @Query("DELETE FROM templates")
    suspend fun deleteAll()
}

@Entity(tableName = "print_history")
data class PrintHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: String?,
    val templateName: String,
    val tapeWidthMm: Int,
    val lengthMm: Int,
    val media: String,
    @ColumnInfo(defaultValue = "PHOMEMO")
    val family: String = "PHOMEMO",
    /** Elements AFTER placeholder resolution, so that reprinting reproduces exactly. */
    val elementsJson: String,
    val copies: Int,
    val printedAt: Long,
)

@Dao
interface PrintHistoryDao {

    @Query("SELECT * FROM print_history ORDER BY printedAt DESC LIMIT 50")
    fun observeAll(): Flow<List<PrintHistoryEntity>>

    @Insert
    suspend fun insert(entry: PrintHistoryEntity)

    @Query(
        "DELETE FROM print_history WHERE id NOT IN " +
            "(SELECT id FROM print_history ORDER BY printedAt DESC LIMIT 50)"
    )
    suspend fun prune()

    @Query("DELETE FROM print_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM print_history")
    suspend fun clear()
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `print_history` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`templateId` TEXT, " +
                "`templateName` TEXT NOT NULL, " +
                "`tapeWidthMm` INTEGER NOT NULL, " +
                "`lengthMm` INTEGER NOT NULL, " +
                "`media` TEXT NOT NULL, " +
                "`elementsJson` TEXT NOT NULL, " +
                "`copies` INTEGER NOT NULL, " +
                "`printedAt` INTEGER NOT NULL)"
        )
    }
}

/** Auto-length tape (issue #2). Existing templates keep their exact length, hence the 0 default. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `templates` ADD COLUMN `autoLength` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Manually placed label edges (issue #2). Existing templates are on one of the other two modes. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `templates` ADD COLUMN `manualEdges` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `templates` ADD COLUMN `leadingMm` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Configurable margin at an automatic label edge (issue #2). Eight dots is the millimeter it was fixed at. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `templates` ADD COLUMN `marginPx` INTEGER NOT NULL DEFAULT 8")
    }
}

/**
 * Printer family per label (issue #19). Everything that exists was designed on the one printer
 * the app knew, hence the constant default rather than a lookup.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `templates` ADD COLUMN `family` TEXT NOT NULL DEFAULT 'PHOMEMO'")
        db.execSQL("ALTER TABLE `print_history` ADD COLUMN `family` TEXT NOT NULL DEFAULT 'PHOMEMO'")
    }
}

/**
 * Per-template print counter (issue #22, sorting by prints). It cannot be reconstructed, because
 * print_history only keeps the last 50 entries and the user can delete them, so everything that
 * exists starts at zero.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `templates` ADD COLUMN `printCount` INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [TemplateEntity::class, PrintHistoryEntity::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun printHistoryDao(): PrintHistoryDao
}
