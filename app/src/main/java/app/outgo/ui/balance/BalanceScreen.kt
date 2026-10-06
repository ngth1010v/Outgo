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
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.repo.DefaultCategoryColor
import app.outgo.data.repo.SavingsSetting
import app.outgo.domain.AccountType
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.category.SectionHeader
import app.outgo.ui.category.SectionSwitch
import app.outgo.ui.component.BudgetProgressBlock
import app.outgo.ui.component.OutgoSegmentedButton
import app.outgo.ui.component.ColorPickerGrid
import app.outgo.ui.component.ConfirmDialog
import app.outgo.ui.component.EditorScaffold
import app.outgo.ui.component.IconPickerSheet
import app.outgo.ui.component.IconView
import app.outgo.ui.component.MonthPicker
import app.outgo.ui.component.onBlur
import app.outgo.ui.component.rememberAutoSave
import app.outgo.ui.component.PlusRow
import app.outgo.ui.component.rememberReorderState
import app.outgo.ui.component.reorderableItem
import app.outgo.ui.component.slideItem
import app.outgo.ui.component.savingsProgressColor
import app.outgo.ui.component.savingsProgressText
import app.outgo.util.Money
import app.outgo.util.MonthKey
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

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
                val target = row.monthlyTarget
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
    val row = if (accountId == null) null else accounts.firstOrNull { it.account.id == accountId } ?: return
    EditAccountScreen(row, onClose, viewModel)
}

/** The General section's inputs, saved together. */
private data class AccountGeneral(val type: Int, val name: String, val description: String, val balanceText: String, val iconId: Long?, val color: Int)

/** The Savings section's inputs for one month, as typed (the target is still text). */
private data class SavingsForm(val enabled: Boolean, val targetText: String) {
    /** On needs a target. */
    val complete: Boolean get() = !enabled || (targetText.toLongOrNull() ?: 0L) > 0

    fun toSetting() = SavingsSetting(enabled, targetText.toLongOrNull() ?: 0L)

    companion object {
        fun of(s: SavingsSetting?) = SavingsForm(s?.enabled ?: false, s?.target?.takeIf { it > 0 }?.toString().orEmpty())
    }
}

@Composable
private fun EditAccountScreen(row: AccountWithProgress?, onDismiss: () -> Unit, viewModel: BalanceViewModel) {
    val account = row?.account
    val scope = rememberCoroutineScope()
    var accountType by remember { mutableStateOf(account?.accountType ?: AccountType.NORMAL) }
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var description by remember { mutableStateOf(account?.description.orEmpty()) }
    var balanceText by remember { mutableStateOf(account?.balance?.takeIf { it != 0L }?.toString().orEmpty()) }
    // The Savings section shows (and saves) one month's target; it opens on this month's.
    var month by remember { mutableIntStateOf(MonthKey.current()) }
    var form by remember { mutableStateOf(SavingsForm.of(row?.monthlyTarget?.let { SavingsSetting(true, it) })) }
    var iconId by remember { mutableStateOf(account?.iconId) }
    var color by remember { mutableStateOf(account?.color ?: DefaultCategoryColor) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var hasTrades by remember { mutableStateOf(false) }

    LaunchedEffect(account) {
        hasTrades = account?.let { viewModel.hasTrades(it.id) } ?: false
    }

    // Editing saves as it goes: General as one, the Savings section as the shown month's target.
    val generalSave = account?.let { edited ->
        rememberAutoSave(AccountGeneral(accountType, name, description, balanceText, iconId, color)) {
            name.isNotBlank().also { ok ->
                if (ok) viewModel.update(edited.id, name, iconId, color, balanceText.toLongOrNull() ?: 0L, accountType, description.trim().ifEmpty { null })
            }
        }
    }
    val savingsSave = account?.let {
        rememberAutoSave(form) { f ->
            f.complete.also { ok -> if (ok) viewModel.setSavingsMonth(it.id, month, f.toSetting()) }
        }
    }
    fun showMonth(m: Int) {
        if (savingsSave == null || account == null) {
            month = m
            return
        }
        // The month being left keeps its pending change.
        savingsSave.flush()
        month = m
        scope.launch {
            val loaded = SavingsForm.of(viewModel.savingsAt(account.id, m))
            savingsSave.reset(loaded)
            form = loaded
        }
    }
    // The row only knows a target that is on: read this month's in full (an off one keeps its amount).
    LaunchedEffect(Unit) { if (account != null) showMonth(month) }

    // Creating: compared against the values the editor opened with, for the discard check.
    val fields = listOf(accountType, name, description, balanceText, month, form, iconId, color)
    val initialFields = remember { fields }
    val savingsShown = accountType == AccountType.SAVINGS && form.enabled
    EditorScaffold(
        title = if (account == null) stringResource(R.string.balance_create_title) else stringResource(R.string.balance_edit_title),
        onCancel = onDismiss,
        dirty = account == null && fields != initialFields,
        onSave = if (account != null) {
            null
        } else {
            {
                val savings = form.toSetting().takeIf { savingsShown }
                viewModel.create(name, iconId, color, balanceText.toLongOrNull() ?: 0L, accountType, savings, month, description.trim().ifEmpty { null })
                onDismiss()
            }
        },
        // A savings target that is on needs an amount.
        saveEnabled = name.isNotBlank() && (!savingsShown || form.complete),
        onDelete = if (account != null) { { showDeleteConfirm = true } } else null,
    ) {
        SectionHeader(stringResource(R.string.category_section_general), stringResource(R.string.balance_general_info))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconView(iconId = iconId, size = 48.dp, color = color, modifier = Modifier.padding(end = 12.dp))
            OutlinedButton(onClick = { showIconPicker = true }) { Text(stringResource(R.string.common_choose_icon)) }
        }
        Spacer(Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            OutgoSegmentedButton(
                selected = accountType == AccountType.NORMAL,
                onClick = { accountType = AccountType.NORMAL },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.balance_type_normal)) }
            OutgoSegmentedButton(
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
            isError = account != null && name.isBlank(),
            // Editing: a blank name goes back to the saved one when the field is left.
            modifier = Modifier.fillMaxWidth().onBlur { if (name.isBlank() && generalSave != null) name = generalSave.saved.name },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.common_description_hint)) },
            minLines = 1,
            maxLines = 4,
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
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.category_color_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        ColorPickerGrid(selected = color, onSelect = { color = it })

        // Only a savings account has a target; a normal one shows the switch off and disabled,
        // its monthly targets kept for if it becomes a savings account again.
        Spacer(Modifier.height(24.dp))
        val switchDescription = stringResource(R.string.balance_savings_target_switch)
        SectionHeader(stringResource(R.string.balance_savings_label), stringResource(R.string.balance_savings_info)) {
            if (accountType == AccountType.SAVINGS) MonthPicker(month, ::showMonth)
            SectionSwitch(
                checked = savingsShown,
                onCheckedChange = { form = form.copy(enabled = it) },
                enabled = accountType == AccountType.SAVINGS,
                modifier = Modifier.semantics { contentDescription = switchDescription },
            )
        }
        if (savingsShown) {
            OutlinedTextField(
                value = form.targetText,
                onValueChange = { form = form.copy(targetText = it.filter { c -> c.isDigit() }) },
                label = { Text(stringResource(R.string.balance_target_hint)) },
                singleLine = true,
                isError = account != null && !form.complete,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                // Editing: an invalid target goes back to the saved one when the field is left,
                // and the switch too if that month had none (it was off).
                modifier = Modifier.fillMaxWidth().onBlur {
                    val saved = savingsSave?.saved ?: return@onBlur
                    if (!form.complete) form = form.copy(targetText = saved.targetText).takeIf { it.complete } ?: saved
                },
            )
        }
    }

    if (showIconPicker) {
        IconPickerSheet(onIconSelected = { iconId = it; showIconPicker = false }, onDismiss = { showIconPicker = false })
    }

    if (showDeleteConfirm && account != null) {
        ConfirmDialog(
            title = if (hasTrades) stringResource(R.string.balance_archive_confirm_title) else stringResource(R.string.balance_delete_confirm_title),
            message = if (hasTrades) stringResource(R.string.balance_archive_confirm_message) else stringResource(R.string.balance_delete_confirm_message),
            onConfirm = {
                generalSave?.stop()
                savingsSave?.stop()
                viewModel.deleteOrArchive(account)
                showDeleteConfirm = false
                onDismiss()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
