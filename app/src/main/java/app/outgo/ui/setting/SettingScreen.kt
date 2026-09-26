package app.outgo.ui.setting

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.backup.RestoreError
import app.outgo.data.backup.RestoreException
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.ConfirmDialog
import app.outgo.util.LocalePrefs
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen() {
    val container = LocalAppContainer.current
    val viewModel: SettingViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingViewModel(container.backupManager, container.settingRepository) } },
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.8.1"
    }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<File?>(null) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    val locale by viewModel.locale.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

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

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_setting)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)) {
            SectionLabel(stringResource(R.string.setting_section_general))
            SettingRow(
                title = stringResource(R.string.setting_language),
                subtitle = languageLabel(locale),
                onClick = { showLanguagePicker = true },
            )
            SettingRow(
                title = stringResource(R.string.setting_currency),
                subtitle = currency,
                onClick = { showCurrencyPicker = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

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

    if (showCurrencyPicker) {
        CurrencyPickerSheet(
            selected = currency,
            onSelect = { symbol ->
                showCurrencyPicker = false
                viewModel.setCurrency(symbol)
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    if (showLanguagePicker) {
        LanguagePickerSheet(
            selected = locale,
            onSelect = { tag ->
                showLanguagePicker = false
                // Write the SharedPreferences mirror synchronously before recreating —
                // viewModel.setLocale's DB write is async and attachBaseContext (which
                // runs synchronously inside recreate()) would otherwise still see the
                // previous value, applying the opposite of what was just picked.
                LocalePrefs.set(context, tag)
                viewModel.setLocale(tag)
                (context as? Activity)?.recreate()
            },
            onDismiss = { showLanguagePicker = false },
        )
    }
}

/** BCP-47 language tag ("en", "vi"), or "" to follow the system locale. */
private val LanguageOptions = listOf("", "en", "vi")

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    "en" -> stringResource(R.string.setting_language_english)
    "vi" -> stringResource(R.string.setting_language_vietnamese)
    else -> stringResource(R.string.setting_language_system)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text(stringResource(R.string.setting_language), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LanguageOptions.forEach { tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(tag) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = tag == selected, onClick = { onSelect(tag) })
                    Text(languageLabel(tag), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
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
