package app.outgo.ui.setting

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.backup.BackupManager
import app.outgo.data.repo.SettingRepository
import app.outgo.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingViewModel(
    private val backupManager: BackupManager,
    private val settingRepository: SettingRepository,
) : ViewModel() {

    /** BCP-47 language tag ("en", "vi"), or "" to follow the system locale. */
    val locale: StateFlow<String> = settingRepository.observeLocale()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Currency symbol appended to every amount. */
    val currency: StateFlow<String> = settingRepository.observeCurrency()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Money.symbol)

    fun setCurrency(symbol: String) {
        viewModelScope.launch { settingRepository.setCurrency(symbol) }
    }

    fun setLocale(languageTag: String) {
        viewModelScope.launch { settingRepository.setLocale(languageTag) }
    }

    suspend fun export(uri: Uri): Result<Long> = backupManager.export(uri)

    suspend fun validateRestore(uri: Uri): Result<File> = backupManager.validate(uri)

    fun applyRestore(file: File) {
        // Kills and restarts the process — nothing after this call runs.
        viewModelScope.launch { backupManager.applyAndRestart(file) }
    }
}
