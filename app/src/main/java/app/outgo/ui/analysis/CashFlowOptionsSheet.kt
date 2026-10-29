package app.outgo.ui.analysis

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.component.IconView
import app.outgo.ui.component.OutgoSegmentedButton
import kotlin.math.roundToInt

/** What the cash flow's settings list: every active parent account and category, with their children. */
@Immutable
data class CashTree(val accounts: List<AccountNode>, val income: List<AccountNode>, val expense: List<AccountNode>)

private enum class CashTab { ACCOUNTS, CATEGORIES, OTHER }

/** Most of the screen: each tab fills the sheet, so switching tabs doesn't make it jump. */
private const val SHEET_HEIGHT = 0.88f

/** A section's "Show sub…" row reaches near both edges of the row: label at the left, switch at the right. */
private val SubLabelInset = 4.dp

/** The fold caret's room at the header row's end. */
private val CaretRoom = 36.dp

/** Room between two sections, which have no line between them. */
private val SectionGap = 10.dp

/** A section's subaccounts or sub-categories, this many to a row. */
private const val SUB_CELLS_PER_ROW = 6

/** The switches are drawn at this share of Material's size, and take only that much room. */
private const val SWITCH_SCALE = 0.7f

/**
 * The cash flow card's settings, in three tabs switched at the top. Account: a section per parent
 * account, its switch showing or hiding it; a shown one can be drawn as its subaccounts, each then
 * switched on and off in a grid. Category: the same for the income, then the expense categories.
 * Other: transfers, balance adjustments, fit to screen, and the accounts' detail. Changes apply at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowOptionsSheet(title: String, card: ChartCard, tree: CashTree, onChange: (ChartCard) -> Unit, onDismiss: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(CashTab.ACCOUNTS) }
    // Sections opened, as "<group>-<id>" (account and category ids overlap). Every section starts
    // folded; this lives only while the sheet is open, so closing it folds them all again.
    var opened by remember { mutableStateOf(listOf<String>()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxHeight(SHEET_HEIGHT).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val tabs = listOf(
                CashTab.ACCOUNTS to stringResource(R.string.analysis_cash_account),
                CashTab.CATEGORIES to stringResource(R.string.analysis_cash_category),
                CashTab.OTHER to stringResource(R.string.analysis_cash_other),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { i, (t, label) ->
                    OutgoSegmentedButton(
                        selected = tab == t,
                        onClick = { tab = t },
                        shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(8.dp))
            val showSubaccounts = stringResource(R.string.analysis_cash_show_subaccounts)
            val showSubcategories = stringResource(R.string.analysis_cash_show_subcategories)
            val incomeTitle = stringResource(R.string.analysis_cash_income_categories)
            val expenseTitle = stringResource(R.string.analysis_cash_expense_categories)
            val switches = listOf(
                stringResource(R.string.analysis_cash_show_transfers) to (CASH_HIDE_TRANSFERS !in card.hidden),
                stringResource(R.string.analysis_cash_show_adjustments) to (CASH_HIDE_ADJUSTMENTS !in card.hidden),
                stringResource(R.string.analysis_cash_fit) to card.fitScreen,
                stringResource(R.string.analysis_cash_more_detail) to card.accountDetail,
            )
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                when (tab) {
                    CashTab.ACCOUNTS -> sections(
                        "account", tree.accounts, card.hidden, card.expandedAccounts, showSubaccounts, opened,
                        onOpened = { opened = it },
                        onHidden = { onChange(card.copy(hidden = it)) },
                        onExpanded = { onChange(card.copy(expandedAccounts = it)) },
                    )
                    CashTab.CATEGORIES -> listOf("income" to tree.income, "expense" to tree.expense).forEach { (key, nodes) ->
                        if (nodes.isEmpty()) return@forEach
                        val heading = if (key == "income") incomeTitle else expenseTitle
                        item(key = "$key-heading") { SectionTitle(heading, Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                        sections(
                            key, nodes, card.hiddenCategories, card.expandedCategories, showSubcategories, opened,
                            onOpened = { opened = it },
                            onHidden = { onChange(card.copy(hiddenCategories = it)) },
                            onExpanded = { onChange(card.copy(expandedCategories = it)) },
                        )
                    }
                    CashTab.OTHER -> switches.forEachIndexed { i, (label, on) ->
                        item(key = "other-$i") {
                            SwitchRow(label, on, Modifier.padding(vertical = 4.dp)) {
                                onChange(
                                    when (i) {
                                        0 -> card.copy(hidden = card.hidden.toggle(CASH_HIDE_TRANSFERS))
                                        1 -> card.copy(hidden = card.hidden.toggle(CASH_HIDE_ADJUSTMENTS))
                                        2 -> card.copy(fitScreen = !card.fitScreen)
                                        else -> card.copy(accountDetail = !card.accountDetail)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun List<Long>.toggle(id: Long) = if (id in this) this - id else this + id

/**
 * A section per node: a header (icon, name, show switch); while shown, a row to draw it as its
 * children; while that is on, its children as a grid of cells, ringed while shown.
 */
private fun LazyListScope.sections(
    key: String,
    nodes: List<AccountNode>,
    hidden: List<Long>,
    expanded: List<Long>,
    subLabel: String,
    opened: List<String>,
    onOpened: (List<String>) -> Unit,
    onHidden: (List<Long>) -> Unit,
    onExpanded: (List<Long>) -> Unit,
) {
    nodes.forEachIndexed { index, node ->
        val shown = node.id !in hidden
        val open = shown && node.id in expanded
        val fold = "$key-${node.id}"
        val folded = fold !in opened
        item(key = "$key-${node.id}") {
            Column {
                if (index > 0) Spacer(Modifier.height(SectionGap))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onHidden(hidden.toggle(node.id)) }.padding(vertical = 4.dp),
                ) {
                    IconView(iconId = node.iconId, size = 32.dp, color = node.color)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        node.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SmallSwitch(shown) { onHidden(hidden.toggle(node.id)) }
                    FoldCaret(folded) { onOpened(if (folded) opened + fold else opened - fold) }
                }
                if (shown && !folded) {
                    SwitchRow(subLabel, open, Modifier.padding(horizontal = SubLabelInset)) { onExpanded(expanded.toggle(node.id)) }
                }
            }
        }
        if (open && !folded && node.children.isNotEmpty()) {
            val cells = node.children.map { OptionItem(it.id, it.name, it.iconId, it.color) }
            item(key = "$key-${node.id}-pad") { Spacer(Modifier.height(4.dp)) }
            cellRows("$key-${node.id}-sub", cells, selected = { it !in hidden }, perRow = SUB_CELLS_PER_ROW) { id -> onHidden(hidden.toggle(id)) }
        }
    }
}

/**
 * A [Switch] [SWITCH_SCALE] times its usual size, laid out that small too, so rows can be tight: the
 * row around it, being clickable, keeps the touch target large.
 */
@Composable
private fun SmallSwitch(checked: Boolean, onToggle: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                val w = (p.width * SWITCH_SCALE).roundToInt()
                val h = (p.height * SWITCH_SCALE).roundToInt()
                layout(w, h) {
                    p.placeWithLayer((w - p.width) / 2, (h - p.height) / 2) {
                        scaleX = SWITCH_SCALE
                        scaleY = SWITCH_SCALE
                    }
                }
            },
        )
    }
}

/** Folds a section shut or opens it: points down while open, turns to point right when folded. */
@Composable
private fun FoldCaret(folded: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (folded) -90f else 0f, label = "foldCaret")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(start = CaretRoom - 32.dp).size(32.dp).clip(CircleShape).clickable(onClick = onClick),
    ) {
        Icon(
            painterResource(R.drawable.ph_caret_down),
            contentDescription = stringResource(if (folded) R.string.analysis_cash_unfold else R.string.analysis_cash_fold),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp).rotate(turn),
        )
    }
}

/** A label and a switch; the whole row toggles it. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        SmallSwitch(checked, onToggle)
    }
}
