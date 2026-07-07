package io.github.toolicious.labler.data

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
    val elementsJson: String,
    val schemaVersion: Int,
    val favorite: Boolean,
    val counterValue: Int,
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

@Database(
    entities = [TemplateEntity::class, PrintHistoryEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun printHistoryDao(): PrintHistoryDao
}
