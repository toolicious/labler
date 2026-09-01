package io.github.toolicious.labler.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room

/**
 * Bends an existing database into the shape this build expects. Development only.
 *
 * Two branches worked on at the same time both add their column and both call the result schema 5,
 * because a schema version is a single counter and neither knows about the other. Installing one
 * build over the other then leaves Room with a database that says it is already at the version it
 * wants, so no migration runs, and a column it needs is simply not there. Room refuses to open it,
 * which costs the test data on the phone every time the branch is switched.
 *
 * So the database is fitted to the build instead. Nothing here runs unless Room has already
 * refused, and nothing here runs in a release build, where a missing migration is a real fault and
 * has to stay loud.
 *
 * The shape to aim for is not written down anywhere: it is read from a throwaway in-memory
 * database that Room builds from the very entities this build carries, so it cannot drift.
 */
object DevSchemaRepair {

    private const val TAG = "DevSchemaRepair"

    /** A column as SQLite reports it. */
    private data class Column(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val default: String?,
    )

    /**
     * Repairs [dbName] in place. [rebuild] false adds what is missing and leaves everything else
     * alone, which keeps every column both builds share; true makes each table exactly what this
     * build declares, which is what it takes when a column the build does not know about is in the
     * way. Returns whether anything was changed.
     */
    fun run(context: Context, dbName: String, rebuild: Boolean): Boolean {
        val file = context.getDatabasePath(dbName)
        if (!file.exists()) return false
        return runCatching { repair(context, file.path, rebuild) }
            .onFailure { Log.w(TAG, "could not repair the database", it) }
            .getOrDefault(false)
    }

    private fun repair(context: Context, path: String, rebuild: Boolean): Boolean {
        // What this build expects, straight from its own entities.
        val reference = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val ref = reference.openHelper.writableDatabase
            val version = ref.version
            val wanted = ref.query(
                "SELECT name, sql FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata')"
            ).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            }
            val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                var changed = false
                db.beginTransaction()
                try {
                    for ((table, createSql) in wanted) {
                        val want = columnsOf(ref, table)
                        val have = columnsOf(db, table)
                        changed = fit(db, ref, table, createSql, want, have, rebuild) || changed
                    }
                    // The version is the counter that lied in the first place, so it is set to what
                    // this build believes. Room then migrates nothing and validates what is there.
                    db.version = version
                    // Dropped rather than rewritten: without it Room checks the tables itself and
                    // writes a fresh identity once they hold up, which saves knowing its hash.
                    db.execSQL("DROP TABLE IF EXISTS room_master_table")
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                return changed
            } finally {
                db.close()
            }
        } finally {
            reference.close()
        }
    }

    /** Brings one table into line, and reports whether it had to touch it. */
    private fun fit(
        db: SQLiteDatabase,
        ref: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        createSql: String,
        want: List<Column>,
        have: List<Column>,
        rebuild: Boolean,
    ): Boolean {
        if (have.isEmpty()) {
            db.execSQL(createSql)
            copyIndices(db, ref, table)
            Log.i(TAG, "created missing table $table")
            return true
        }
        val haveNames = have.map { it.name }.toSet()
        val missing = want.filterNot { it.name in haveNames }
        // A NOT NULL column without a default cannot be added to a table that already has rows,
        // so that case goes the long way round as well.
        val addable = missing.none { it.notNull && it.default == null }
        if (!rebuild && addable) {
            missing.forEach { db.execSQL("ALTER TABLE `$table` ADD COLUMN ${definition(it)}") }
            if (missing.isNotEmpty()) Log.i(TAG, "added to $table: ${missing.map { it.name }}")
            return missing.isNotEmpty()
        }
        if (want == have) return false
        val shared = want.map { it.name }.filter { it in haveNames }.joinToString { "`$it`" }
        val old = "${table}_dev_old"
        db.execSQL("ALTER TABLE `$table` RENAME TO `$old`")
        db.execSQL(createSql)
        if (shared.isNotEmpty()) {
            db.execSQL("INSERT INTO `$table` ($shared) SELECT $shared FROM `$old`")
        }
        db.execSQL("DROP TABLE `$old`")
        copyIndices(db, ref, table)
        Log.i(TAG, "rebuilt $table, kept $shared")
        return true
    }

    private fun copyIndices(
        db: SQLiteDatabase,
        ref: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ) {
        ref.query(
            "SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL",
            arrayOf(table),
        ).use { c -> while (c.moveToNext()) db.execSQL(c.getString(0)) }
    }

    private fun definition(c: Column): String = buildString {
        append("`${c.name}` ${c.type}")
        if (c.notNull) append(" NOT NULL")
        c.default?.let { append(" DEFAULT $it") }
    }

    private fun columnsOf(db: SQLiteDatabase, table: String): List<Column> =
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { read(it) }

    private fun columnsOf(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): List<Column> =
        db.query("PRAGMA table_info(`$table`)").use { read(it) }

    private fun read(c: android.database.Cursor): List<Column> = buildList {
        val name = c.getColumnIndex("name")
        val type = c.getColumnIndex("type")
        val notNull = c.getColumnIndex("notnull")
        val default = c.getColumnIndex("dflt_value")
        while (c.moveToNext()) {
            add(
                Column(
                    name = c.getString(name),
                    type = c.getString(type),
                    notNull = c.getInt(notNull) != 0,
                    default = if (c.isNull(default)) null else c.getString(default),
                )
            )
        }
    }
}
