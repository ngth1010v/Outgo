package app.outgo.ui.balance

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.repo.CategoryColorPalette
import app.outgo.domain.AccountType
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.BudgetProgressBlock
import app.outgo.ui.component.ColorPickerGrid
import app.outgo.ui.component.ConfirmDialog
import app.outgo.ui.component.EditorScaffold
import app.outgo.ui.component.IconPickerSheet
import app.outgo.ui.component.IconView
import app.outgo.ui.component.PlusRow
import app.outgo.ui.component.rememberReorderState
import app.outgo.ui.component.reorderableItem
import app.outgo.ui.component.slideItem
import app.outgo.ui.component.savingsProgressColor
import app.outgo.ui.component.savingsProgressText
import app.outgo.util.Money
import androidx.compose.ui.graphics.Color

/** The "add account" row's key; account rows are keyed by id. */
private const val AddKey = "add"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceScreen(onOpenEditor: (accountId: Long?) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = balanceViewModel()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    // The order a drag is working on; the database's next emission replaces it.
    var order by remember(accounts) { mutableStateOf(accounts) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState)
    reorder.update(
        keys = order.map { it.account.id } + AddKey,
        canDrag = { it != AddKey },
        isSlot = { _, after -> after != null },
        onMove = { key, to ->
            val others = order.filter { it.account.id != key }
            order = others.toMutableList().apply { add(to, order.first { it.account.id == key }) }
        },
        onDrop = { viewModel.reorder(order.map { it.account.id }) },
    )

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_balance)) }) }) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(order, key = { it.account.id }) { row ->
                val account = row.account
                val target = account.savingsTarget?.takeIf { it > 0 && account.accountType == AccountType.SAVINGS }
                Column(
                    modifier = reorderableItem(reorder, account.id)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        // A lifted row's release also ends a tap on it: that tap must not open the editor.
                        .clickable { if (reorder.draggingKey == null) onOpenEditor(account.id) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconView(iconId = account.iconId, size = 32.dp, color = account.color)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                                if (account.accountType == AccountType.SAVINGS) {
                                    Text(
                                        stringResource(R.string.balance_savings_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(Money.format(account.balance), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (target != null) {
                        BudgetProgressBlock(
                            remainingText = savingsProgressText(row.monthlyIncome, target),
                            spentOfTotalText = stringResource(
                                R.string.balance_savings_of_target,
                                Money.groupThousands(row.monthlyIncome),
                                Money.groupThousands(target),
                            ),
                            progress = row.monthlyIncome.toFloat() / target.toFloat(),
                            color = savingsProgressColor(row.monthlyIncome, target, Color(account.color)),
                            current = row.monthlyIncome,
                            total = target,
                            lineName = stringResource(R.string.progress_line_saved),
                            loadDays = { container.tradeRepository.savingDaysThisMonth(account.id) },
                            greenWhenLower = false,
                        )
                    }
                }
            }
            item(key = AddKey) {
                PlusRow(
                    onClick = { onOpenEditor(null) },
                    modifier = slideItem().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

/**
 * The Accounts tab's own ViewModel, from the activity's store: the tab lives outside the NavHost,
 * and the editor route shares it so a save outlives the editor being popped.
 */
@Composable
private fun balanceViewModel(): BalanceViewModel {
    val container = LocalAppContainer.current
    return viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
        factory = viewModelFactory { initializer { BalanceViewModel(container.accountRepository) } },
    )
}

/** The account editor as its own screen: [accountId] edits that account, null creates one. */
@Composable
fun AccountEditScreen(accountId: Long?, onClose: () -> Unit) {
    val viewModel = balanceViewModel()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    // Nothing to draw for the moment a deleted account is still on screen before the pop.
    val account = if (accountId == null) null else accounts.firstOrNull { it.account.id == accountId }?.account ?: return
    EditAccountScreen(account, onClose, viewModel)
}

@Composable
private fun EditAccountScreen(account: AccountEntity?, onDismiss: () -> Unit, viewModel: BalanceViewModel) {
    val scope = rememberCoroutineScope()
    var accountType by remember { mutableStateOf(account?.accountType ?: AccountType.NORMAL) }
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var balanceText by remember { mutableStateOf(account?.balance?.takeIf { it != 0L }?.toString().orEmpty()) }
    var targetText by remember { mutableStateOf(account?.savingsTarget?.toString().orEmpty()) }
    var iconId by remember { mutableStateOf(account?.iconId) }
    var color by remember { mutableStateOf(account?.color ?: CategoryColorPalette[0]) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var hasTrades by remember { mutableStateOf(false) }

    LaunchedEffect(account) {
        hasTrades = account?.let { viewModel.hasTrades(it.id) } ?: false
    }

    // Compared against the values the editor opened with.
    val fields = listOf(accountType, name, balanceText, targetText, iconId, color)
    val initialFields = remember { fields }
    EditorScaffold(
        title = if (account == null) stringResource(R.string.balance_create_title) else stringResource(R.string.balance_edit_title),
        onCancel = onDismiss,
        dirty = fields != initialFields,
        onSave = {
            val balance = balanceText.toLongOrNull() ?: 0L
            val target = targetText.toLongOrNull()
            if (account == null) {
                viewModel.create(name, iconId, color, balance, accountType, target)
            } else {
                viewModel.update(account.id, name, iconId, color, balance, accountType, target)
            }
            onDismiss()
        },
        saveEnabled = name.isNotBlank(),
        onDelete = if (account != null) { { showDeleteConfirm = true } } else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconView(iconId = iconId, size = 48.dp, color = color, modifier = Modifier.padding(end = 12.dp))
            OutlinedButton(onClick = { showIconPicker = true }) { Text(stringResource(R.string.common_choose_icon)) }
        }
        Spacer(Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = accountType == AccountType.NORMAL,
                onClick = { accountType = AccountType.NORMAL },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.balance_type_normal)) }
            SegmentedButton(
                selected = accountType == AccountType.SAVINGS,
                onClick = { accountType = AccountType.SAVINGS },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.balance_type_savings)) }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.balance_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = balanceText,
            onValueChange = { balanceText = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.balance_current_balance_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (accountType == AccountType.SAVINGS) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.balance_target_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))


        Text(stringResource(R.string.category_color_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        ColorPickerGrid(selected = color, onSelect = { color = it })
    }

    if (showIconPicker) {
        IconPickerSheet(onIconSelected = { iconId = it; showIconPicker = false }, onDismiss = { showIconPicker = false })
    }

    if (showDeleteConfirm && account != null) {
        ConfirmDialog(
            title = if (hasTrades) stringResource(R.string.balance_archive_confirm_title) else stringResource(R.string.balance_delete_confirm_title),
            message = if (hasTrades) stringResource(R.string.balance_archive_confirm_message) else stringResource(R.string.balance_delete_confirm_message),
            onConfirm = {
                viewModel.deleteOrArchive(account)
                showDeleteConfirm = false
                onDismiss()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
