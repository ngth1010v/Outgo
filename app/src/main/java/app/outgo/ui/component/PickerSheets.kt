package app.outgo.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.outgo.R
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.domain.CategoryKind
import app.outgo.ui.LocalAppContainer

@Composable
fun PickCell(name: String, iconId: Long?, color: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconView(iconId = iconId, size = 40.dp, color = color)
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AccountSheet(accounts: List<AccountEntity>, selectedId: Long?, onSelect: (AccountEntity) -> Unit, onDismiss: () -> Unit) =
    GroupedPickerSheet(
        title = stringResource(R.string.trade_select_account_title),
        searchHint = stringResource(R.string.trade_search_account_hint),
        items = accounts,
        parentId = { it.parentId },
        id = { it.id },
        name = { it.name },
        selectedId = selectedId,
        cell = { item, selected, onClick, modifier -> PickCell(item.name, item.iconId, item.color, selected, onClick, modifier) },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )

/** One cell (a budget, by its category id) or section header (a parent category) of [BudgetSheet]. */
private data class BudgetPick(val id: Long, val parentId: Long?, val name: String, val iconId: Long?, val color: Int)

/**
 * The budgets grouped by parent category: each section is headed by a parent and holds the
 * parent's own budget, then its subcategories' budgets. [noneLabel] above them picks no budget (null).
 */
@Composable
fun BudgetSheet(
    budgets: List<BudgetWithProgress>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
    noneLabel: String = stringResource(R.string.trade_no_budget),
) {
    val container = LocalAppContainer.current
    // Remembered: a fresh Flow per recomposition would re-run the query.
    val categories by remember { container.categoryRepository.observeAllOfType(CategoryKind.EXPENSE) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val items = remember(budgets, categories) {
        val byCategory = budgets.filter { it.categoryId != null }.associateBy { it.categoryId!! }
        categories.filter { it.parentId == null }.flatMap { parent ->
            val section = (listOf(parent) + categories.filter { it.parentId == parent.id })
                .mapNotNull { c -> byCategory[c.id]?.let { BudgetPick(c.id, parent.id, it.displayName, it.displayIconId, it.categoryColor ?: c.color) } }
            if (section.isEmpty()) emptyList() else listOf(BudgetPick(parent.id, null, parent.name, parent.iconId, parent.color)) + section
        }
    }
    GroupedPickerSheet(
        title = stringResource(R.string.trade_select_budget_title),
        searchHint = stringResource(R.string.trade_search_budget_hint),
        items = items,
        parentId = { it.parentId },
        id = { it.id },
        name = { it.name },
        selectedId = selectedId,
        cell = { item, selected, onClick, modifier -> PickCell(item.name, item.iconId, item.color, selected, onClick, modifier) },
        onSelect = { onSelect(it.id) },
        onDismiss = onDismiss,
        header = {
            TextButton(onClick = { onSelect(null) }) { Text(noneLabel) }
        },
    )
}

/** Children grouped under their parent's name, five per row, filtered by a search on the children's names. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> GroupedPickerSheet(
    title: String,
    searchHint: String,
    items: List<T>,
    parentId: (T) -> Long?,
    id: (T) -> Long,
    name: (T) -> String,
    selectedId: Long?,
    cell: @Composable (item: T, selected: Boolean, onClick: () -> Unit, modifier: Modifier) -> Unit,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val parents = remember(items) { items.filter { parentId(it) == null } }
    val childrenByParent = remember(items) { items.filter { parentId(it) != null }.groupBy { parentId(it)!! } }
    val groups = remember(parents, childrenByParent, query) {
        parents.mapNotNull { parent ->
            val children = childrenByParent[id(parent)].orEmpty()
                .filter { query.isBlank() || name(it).contains(query, ignoreCase = true) }
            if (query.isNotBlank() && children.isEmpty()) null else parent to children
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(searchHint) },
                leadingIcon = { Icon(painterResource(R.drawable.ph_magnifying_glass), contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            header?.invoke()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                groups.forEach { (parent, children) ->
                    item(key = id(parent)) {
                        Column {
                            Text(name(parent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            children.chunked(5).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    rowItems.forEach { child ->
                                        cell(child, id(child) == selectedId, { onSelect(child) }, Modifier.weight(1f))
                                    }
                                    repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
