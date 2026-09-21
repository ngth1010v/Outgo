package app.outgo.ui.component

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.outgo.R
import app.outgo.data.icon.BuiltinIcons
import app.outgo.data.icon.IconStore
import app.outgo.ui.LocalAppContainer
import kotlinx.coroutines.launch

private const val COLUMNS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(
    onIconSelected: (iconId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Remembered: a fresh Flow per recomposition would restart the query every time.
    val userIconIds by remember { container.database.iconDao().observeUserIconIds() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var importError by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val iconId = runCatching {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("decode failed")
                val png = IconStore.normalizeToPng(bitmap)
                container.iconStore.ensureUserIcon(png)
            }.getOrNull()
            if (iconId != null) onIconSelected(iconId) else importError = true
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.icon_picker_title), style = MaterialTheme.typography.titleMedium)
            TextButton(
                onClick = {
                    importLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(painterResource(R.drawable.ph_image), contentDescription = null)
                Text("  " + stringResource(R.string.icon_picker_import))
            }
            if (importError) {
                Text(
                    stringResource(R.string.icon_picker_import_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (userIconIds.isNotEmpty()) {
                Text(stringResource(R.string.setting_section_icons), style = MaterialTheme.typography.labelLarge)
                IconGrid(userIconIds, onClick = onIconSelected) { id -> IconView(iconId = id, size = 44.dp) }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            IconGrid(
                items = BuiltinIcons.ALL,
                onClick = { assetKey -> scope.launch { onIconSelected(container.iconStore.ensureBuiltin(assetKey)) } },
            ) { assetKey -> BuiltinIconImage(assetKey = assetKey, size = 44.dp) }
        }
    }
}

/** Simple non-lazy grid — the item counts here are small (a few dozen at most), so no virtualization is needed. */
@Composable
private fun <T> IconGrid(items: List<T>, onClick: (T) -> Unit, cell: @Composable (T) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        items.chunked(COLUMNS).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { item ->
                    Column(modifier = Modifier.padding(4.dp).clickable { onClick(item) }) {
                        cell(item)
                    }
                }
                repeat(COLUMNS - row.size) {
                    Spacer(Modifier.size(52.dp))
                }
            }
        }
    }
}
