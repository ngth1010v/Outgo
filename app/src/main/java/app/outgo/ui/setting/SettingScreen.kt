package app.outgo.ui.setting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.backup.RestoreError
import app.outgo.data.backup.RestoreException
import app.outgo.data.repo.ThemeMode
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.ConfirmDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingScreen() {
    val container = LocalAppContainer.current
    val viewModel: SettingViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingViewModel(container.settingRepository, container.backupManager) } },
    )
    val themeMode by viewModel.themeMode.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.0"
    }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<File?>(null) }

    val exportSuccessTemplate = stringResource(R.string.backup_export_success)
    val exportError = stringResource(R.string.backup_export_failed)
    val invalidFile = stringResource(R.string.backup_restore_invalid_file)
    val newerAppRequired = stringResource(R.string.backup_restore_newer_app_required)
    val corrupted = stringResource(R.string.backup_restore_corrupted)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.export(uri)
                .onSuccess { bytes -> statusMessage = exportSuccessTemplate.format("${bytes / 1024} KB") }
                .onFailure { statusMessage = exportError }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.validateRestore(uri)
                .onSuccess { file -> pendingRestore = file }
                .onFailure { t ->
                    statusMessage = when ((t as? RestoreException)?.error) {
                        RestoreError.NotAnOutgoBackup -> invalidFile
                        RestoreError.NewerAppRequired -> newerAppRequired
                        RestoreError.Corrupted -> corrupted
                        else -> t.message
                    }
                }
        }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            SectionLabel(stringResource(R.string.setting_section_data))
            SettingRow(
                title = stringResource(R.string.setting_export_backup),
                subtitle = stringResource(R.string.setting_export_backup_desc),
                onClick = {
                    val name = "outgo-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.sqlite"
                    exportLauncher.launch(name)
                },
            )
            SettingRow(
                title = stringResource(R.string.setting_import_backup),
                subtitle = stringResource(R.string.setting_import_backup_desc),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )
            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionLabel(stringResource(R.string.setting_section_display))
            ThemeOption(ThemeMode.SYSTEM, stringResource(R.string.setting_theme_system), themeMode, viewModel::setThemeMode)
            ThemeOption(ThemeMode.LIGHT, stringResource(R.string.setting_theme_light), themeMode, viewModel::setThemeMode)
            ThemeOption(ThemeMode.DARK, stringResource(R.string.setting_theme_dark), themeMode, viewModel::setThemeMode)

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionLabel(stringResource(R.string.setting_section_about))
            SettingRow(title = stringResource(R.string.setting_version), subtitle = versionName, onClick = null)
            SettingRow(title = stringResource(R.string.setting_licenses), subtitle = stringResource(R.string.setting_licenses_body), onClick = null)
        }
    }

    pendingRestore?.let { file ->
        ConfirmDialog(
            title = stringResource(R.string.backup_restore_confirm_title),
            message = stringResource(R.string.backup_restore_confirm_message),
            onConfirm = {
                pendingRestore = null
                viewModel.applyRestore(file)
            },
            onDismiss = {
                file.delete()
                pendingRestore = null
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThemeOption(mode: ThemeMode, label: String, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = current == mode, onClick = { onSelect(mode) })
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
