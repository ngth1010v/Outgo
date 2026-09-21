package app.outgo.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import app.outgo.data.db.OutgoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface RestoreError {
    data object NotAnOutgoBackup : RestoreError
    data object NewerAppRequired : RestoreError
    data object Corrupted : RestoreError
    data class Unknown(val message: String?) : RestoreError
}

/**
 * Exports the whole database as one self-contained .sqlite file (via
 * `VACUUM INTO`, so it's a single consistent snapshot with no separate
 * -wal/-shm files) and restores from one, matching architecture.md §9.
 */
class BackupManager(
    private val context: Context,
    private val databaseProvider: () -> OutgoDatabase,
    private val closeDatabase: () -> Unit,
) {
    private val dbFile: File get() = context.getDatabasePath(OutgoDatabase.FILE_NAME)

    suspend fun export(destination: Uri): Result<Long> = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "outgo-export-${System.currentTimeMillis()}.sqlite")
        try {
            val writable = databaseProvider().openHelper.writableDatabase
            writable.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            writable.execSQL(vacuumIntoSql(tmp))

            if (!isIntegrityOk(tmp)) return@withContext Result.failure(IllegalStateException("integrity check failed"))

            context.contentResolver.openOutputStream(destination)?.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            } ?: return@withContext Result.failure(IllegalStateException("could not open destination"))

            Result.success(tmp.length())
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            tmp.delete()
        }
    }

    suspend fun validate(source: Uri): Result<File> = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "outgo-restore-${System.currentTimeMillis()}.sqlite")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return@withContext Result.failure(IllegalStateException("could not open source"))

            val ro = SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY)
            val appId = ro.use { db -> db.rawQuery("PRAGMA application_id", null).use { c -> c.moveToFirst(); c.getInt(0) } }
            if (appId != OutgoDatabase.APPLICATION_ID) {
                tmp.delete()
                return@withContext Result.failure(RestoreException(RestoreError.NotAnOutgoBackup))
            }
            val userVersion = SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA user_version", null).use { c -> c.moveToFirst(); c.getInt(0) }
            }
            if (userVersion > OutgoDatabase.SCHEMA_VERSION) {
                tmp.delete()
                return@withContext Result.failure(RestoreException(RestoreError.NewerAppRequired))
            }
            if (!isIntegrityOk(tmp)) {
                tmp.delete()
                return@withContext Result.failure(RestoreException(RestoreError.Corrupted))
            }
            Result.success(tmp)
        } catch (t: Throwable) {
            tmp.delete()
            Result.failure(RestoreException(RestoreError.Unknown(t.message)))
        }
    }

    /** Call only after [validate] succeeded. Replaces the live database and restarts the app process. */
    suspend fun applyAndRestart(validatedFile: File) = withContext(Dispatchers.IO) {
        val writable = databaseProvider().openHelper.writableDatabase
        writable.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        closeDatabase()

        // Safety copy of the data being replaced, in case the restore was a mistake.
        val safetyDir = File(context.filesDir, "pre_restore_backups").apply { mkdirs() }
        val safetyFile = File(safetyDir, "before-restore-${System.currentTimeMillis()}.sqlite")
        if (dbFile.exists()) dbFile.copyTo(safetyFile, overwrite = true)
        pruneOldBackups(safetyDir, keep = 3)

        dbFile.parentFile?.mkdirs()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        dbFile.delete()
        validatedFile.copyTo(dbFile, overwrite = true)
        validatedFile.delete()

        restartProcess()
    }

    private fun pruneOldBackups(dir: File, keep: Int) {
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { it.delete() }
    }

    private fun isIntegrityOk(file: File): Boolean = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
        }
    } catch (t: Throwable) {
        false
    }

    private fun vacuumIntoSql(target: File): String =
        "VACUUM INTO '${target.path.replace("'", "''")}'"

    private fun restartProcess() {
        // Start the new task directly while we're still in the foreground: an alarm-fired
        // PendingIntent is inexact and blocked by background-activity-launch rules on newer
        // Android, which left the user on the launcher after a restore.
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        val restartIntent = Intent.makeRestartActivityTask(launchIntent.component)
            .putExtra(EXTRA_RESTORED, true)
        context.startActivity(restartIntent)
        Runtime.getRuntime().exit(0)
    }

    companion object {
        /** Set on the relaunch intent after a restore so MainActivity reopens the Setting screen. */
        const val EXTRA_RESTORED = "app.outgo.extra.RESTORED"
    }
}

class RestoreException(val error: RestoreError) : Exception()
