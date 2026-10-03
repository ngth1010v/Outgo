package app.outgo.ui.component

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.outgo.R
import app.outgo.data.icon.BuiltinIcons
import app.outgo.data.icon.IconStore
import app.outgo.data.icon.TablerIcons
import app.outgo.ui.LocalAppContainer
import kotlinx.coroutines.launch

private const val COLUMNS = 5

/** A titled block of the gallery: [headerIndex] is its header's position in the grid. */
private class Section(@StringRes val title: Int, val headerIndex: Int)

/**
 * Icon gallery: imported icons, the bundled PNGs, and with [categoryIcons] the Tabler groups
 * after them, each under a header that a row of chips jumps to. Only categories get the Tabler
 * icons; accounts keep the original set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(
    onIconSelected: (iconId: Long) -> Unit,
    onDismiss: () -> Unit,
    categoryIcons: Boolean = false,
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
    if (categoryIcons) {
        // Drawn while the sheet slides up and the bundled icons show, so a jump to a Tabler
        // section finds its icons ready instead of blank circles filling in one by one.
        LaunchedEffect(Unit) { container.iconStore.preloadAssets(TablerIcons.GROUPS.flatMap { it.keys }) }
    }
    val pickAsset: (String) -> Unit = { assetKey -> scope.launch { onIconSelected(container.iconStore.ensureBuiltin(assetKey)) } }

    // Section header positions after the imported icons' block, so a chip can scroll to its
    // section and the chip row can follow the scroll. Mirrors the item order in the grid below.
    val sections = remember(categoryIcons) {
        if (!categoryIcons) return@remember emptyList()
        buildList {
            add(Section(R.string.icon_group_builtin, 0))
            var index = 1 + BuiltinIcons.ALL.size
            TablerIcons.GROUPS.forEach { group ->
                add(Section(group.title, index))
                index += 1 + group.keys.size
            }
        }
    }
    // The imported icons' header and cells come first.
    val offset = if (userIconIds.isEmpty()) 0 else 1 + userIconIds.size
    val gridState = rememberLazyGridState()
    val current by remember(sections, offset) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            sections.indexOfLast { offset + it.headerIndex <= first }.coerceAtLeast(0)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
            if (sections.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(sections) { i, section ->
                        FilterChip(
                            selected = i == current,
                            onClick = {
                                scope.launch {
                                    gridState.animateScrollToItem(offset + section.headerIndex)
                                }
                            },
                            label = { Text(stringResource(section.title)) },
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = gridState,
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (userIconIds.isNotEmpty()) {
                    header(R.string.setting_section_icons)
                    items(userIconIds, key = { "user_$it" }) { id ->
                        IconCell(onClick = { onIconSelected(id) }) { IconView(iconId = id, size = 44.dp) }
                    }
                }
                if (categoryIcons) header(R.string.icon_group_builtin) else if (userIconIds.isNotEmpty()) divider()
                assetItems(BuiltinIcons.ALL, pickAsset)
                if (categoryIcons) {
                    TablerIcons.GROUPS.forEach { group ->
                        header(group.title)
                        assetItems(group.keys, pickAsset)
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.header(@StringRes title: Int) {
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

/** Separates the imported icons from the bundled ones when there are no section headers. */
private fun LazyGridScope.divider() {
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

private fun LazyGridScope.assetItems(keys: List<String>, onClick: (String) -> Unit) {
    items(keys, key = { it }, contentType = { "asset" }) { key ->
        IconCell(onClick = { onClick(key) }) { BuiltinIconImage(assetKey = key, size = 44.dp) }
    }
}

@Composable
private fun IconCell(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
        Box(modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)) { content() }
    }
}
