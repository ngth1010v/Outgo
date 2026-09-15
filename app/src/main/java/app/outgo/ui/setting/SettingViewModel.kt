package app.outgo.ui.setting

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.backup.BackupManager
import app.outgo.data.repo.SettingRepository
import app.outgo.data.repo.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingViewModel(
    private val settingRepository: SettingRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingRepository.observeThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingRepository.setThemeMode(mode) }
    }

    suspend fun export(uri: Uri): Result<Long> = backupManager.export(uri)

    suspend fun validateRestore(uri: Uri): Result<File> = backupManager.validate(uri)

    fun applyRestore(file: File) {
        // Kills and restarts the process — nothing after this call runs.
        viewModelScope.launch { backupManager.applyAndRestart(file) }
    }
}
