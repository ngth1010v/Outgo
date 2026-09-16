package app.outgo.ui.setting

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.backup.BackupManager
import kotlinx.coroutines.launch
import java.io.File

class SettingViewModel(
    private val backupManager: BackupManager,
) : ViewModel() {

    suspend fun export(uri: Uri): Result<Long> = backupManager.export(uri)

    suspend fun validateRestore(uri: Uri): Result<File> = backupManager.validate(uri)

    fun applyRestore(file: File) {
        // Kills and restarts the process — nothing after this call runs.
        viewModelScope.launch { backupManager.applyAndRestart(file) }
    }
}
