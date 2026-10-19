package app.outgo.ui.balance

import app.outgo.ui.component.MoneyTextField
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.repo.DefaultCategoryColor
import app.outgo.data.repo.SavingsSetting
import app.outgo.data.repo.resolvePrivacy
import app.outgo.domain.AccountPrivacy
import app.outgo.domain.AccountType
import app.outgo.domain.SavingProgressHide
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.category.SectionHeader
import app.outgo.ui.category.SectionSwitch
import app.outgo.ui.component.BudgetProgressBlock
import app.outgo.ui.component.OutgoSegmentedButton
import app.outgo.ui.component.ColorPickerGrid
import app.outgo.ui.component.ConfirmDialog
import app.outgo.ui.component.EditorScaffold
import app.outgo.ui.component.IconPickerSheet
import app.outgo.ui.component.ExceptionBadge
import app.outgo.ui.component.IconView
import app.outgo.ui.component.MonthPicker
import app.outgo.ui.component.SwitchField
import app.outgo.ui.component.TreeList
import app.outgo.ui.component.savingsProgressColor
import app.outgo.ui.component.savingsProgressText
import app.outgo.util.Money
import app.outgo.util.MonthKey
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceScreen(onOpenEditor: (id: Long?, parentId: Long?) -> Unit) {
    val viewModel = balanceViewModel()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val parents = remember(accounts) { accounts.filter { it.account.isParent } }
    val childrenByParent = remember(accounts) { accounts.filter { !it.account.isParent }.groupBy { it.account.parentId!! } }
    val privacy = remember(accounts) { resolvePrivacy(accounts.map { it.account }) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    val listState = rememberLazyListState()
    // A drop waiting for the convert dialog, with the normal subaccounts it would make savings.
    var pendingMove by remember { mutableStateOf<Pair<Map<Long?, List<Long>>, List<AccountEntity>>?>(null) }
    // Bumped when such a drop is cancelled: rebuilds the list, putting the lifted row back.
    var listVersion by remember { mutableIntStateOf(0) }
    fun onReorder(lists: Map<Long?, List<Long>>) {
        val byId = accounts.associateBy { it.account.id }
        val converted = lists.filterKeys { byId[it]?.account?.accountType == AccountType.SAVINGS }
            .flatMap { (parentId, ids) -> ids.mapNotNull { byId[it]?.account }.filter { it.parentId != parentId && it.accountType != AccountType.SAVINGS } }
        if (converted.isEmpty()) viewModel.reorder(lists) else pendingMove = lists to converted
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_balance)) }) }) { padding ->
        key(listVersion) { TreeList(
            parents = parents,
            childrenByParent = childrenByParent,
            id = { it.account.id },
            listState = listState,
            expanded = expanded,
            onToggle = { id -> expanded = if (id in expanded) expanded - id else expanded + id },
            onOpen = { onOpenEditor(it.account.id, null) },
            onAddChild = { onOpenEditor(null, it.account.id) },
            onAddParent = { onOpenEditor(null, null) },
            onReorder = ::onReorder,
            row = { row, onClick, modifier, trailing ->
                AccountRow(row, privacy[row.account.id] ?: AccountPrivacy(), onClick, modifier, trailing)
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) }
    }

    pendingMove?.let { (lists, converted) ->
        ConfirmDialog(
            title = stringResource(R.string.balance_convert_subs_title),
            message = stringResource(R.string.balance_move_to_savings_message, converted.joinToString { it.name }),
            onConfirm = {
                viewModel.reorder(lists)
                pendingMove = null
            },
            onDismiss = {
                pendingMove = null
                listVersion++
            },
        )
    }
}

@Composable
private fun AccountRow(row: AccountWithProgress, privacy: AccountPrivacy, onClick: () -> Unit, modifier: Modifier, trailing: (@Composable () -> Unit)?) {
    val trades = LocalAppContainer.current.tradeRepository
    val account = row.account
    val target = row.monthlyTarget?.takeUnless { privacy.progressHidden(row.monthlyIncome, it) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).clickable(onClick = onClick),
        ) {
            Box {
                IconView(iconId = account.iconId, size = 28.dp, color = account.color)
                if (account.isExempt) ExceptionBadge(28.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                if (account.accountType == AccountType.SAVINGS) {
                    val savings = stringResource(R.string.balance_savings_label)
                    Text(
                        if (account.isExempt) savings + " · " + stringResource(R.string.balance_parent_saving_exception) else savings,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                if (privacy.hideBalance) Money.formatHidden() else Money.format(row.totalBalance),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            trailing?.invoke()
        }
        if (target != null) {
            BudgetProgressBlock(
                remainingText = savingsProgressText(row.monthlyIncome, target),
                spentOfTotalText = stringResource(
                    R.string.balance_savings_of_target,
                    Money.groupThousands(row.monthlyIncome),
                    Money.groupThousands(target),
                ),
                // A summed target can be 0 (no subaccount has one): nothing to save is done.
                progress = if (target > 0) row.monthlyIncome.toFloat() / target.toFloat() else 1f,
                color = savingsProgressColor(row.monthlyIncome, target, Color(account.color)),
                current = row.monthlyIncome,
                total = target,
                lineName = stringResource(R.string.progress_line_saved),
                loadDays = { trades.savingDaysThisMonth(account.id) },
                greenWhenLower = false,
            )
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

/** The account editor as its own screen: [accountId] edits that account, else [parentId] creates a subaccount, else a parent. */
@Composable
fun AccountEditScreen(accountId: Long?, parentId: Long?, onClose: () -> Unit) {
    val viewModel = balanceViewModel()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    // Nothing to draw for the moment a deleted account is still on screen before the pop.
    val row = if (accountId == null) null else accounts.firstOrNull { it.account.id == accountId } ?: return
    val parentKey = parentId ?: row?.account?.parentId
    val parent = if (parentKey == null) null else accounts.firstOrNull { it.account.id == parentKey }?.account ?: return
    // A parent's normal subaccounts, which making it a savings account converts.
    val normalChildren = if (row?.account?.isParent != true) 0 else {
        accounts.count { it.account.parentId == accountId && it.account.accountType != AccountType.SAVINGS }
    }
    EditAccountScreen(row, parent, normalChildren, onClose, viewModel)
}

/**
 * The Savings section's inputs for one month, as typed (the target is still text). [sumChildren]:
 * the target is the subaccounts' summed, [targetText] is kept but not used.
 */
private data class SavingsForm(val enabled: Boolean, val targetText: String, val sumChildren: Boolean = false) {
    /** On needs a target, unless summed. */
    val complete: Boolean get() = !enabled || sumChildren || Money.parseInput(targetText) > 0

    fun toSetting() = SavingsSetting(enabled, Money.parseInput(targetText), sumChildren)

    companion object {
        fun of(s: SavingsSetting?) =
            SavingsForm(s?.enabled ?: false, Money.inputText(s?.target ?: 0L), s?.sumChildren ?: false)
    }
}

@Composable
private fun EditAccountScreen(
    row: AccountWithProgress?,
    parent: AccountEntity?,
    normalChildren: Int,
    onDismiss: () -> Unit,
    viewModel: BalanceViewModel,
) {
    val account = row?.account
    // A parent's balance is its subaccounts': shown, not edited.
    val editingParent = account?.isParent == true
    // A savings parent's subaccounts are savings accounts too.
    val typeLocked = parent?.accountType == AccountType.SAVINGS
    val scope = rememberCoroutineScope()
    var accountType by remember {
        mutableStateOf(if (typeLocked) AccountType.SAVINGS else account?.accountType ?: parent?.accountType ?: AccountType.NORMAL)
    }
    var showConvertConfirm by remember { mutableStateOf(false) }
    var exempt by remember { mutableStateOf(account?.parentSavingExempt ?: false) }
    // This account's own privacy settings; the parent's, when they cover it, show instead and lock the inputs.
    var privacy by remember { mutableStateOf(account?.ownPrivacy ?: AccountPrivacy()) }
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var description by remember { mutableStateOf(account?.description.orEmpty()) }
    var balanceText by remember { mutableStateOf(Money.inputText(row?.totalBalance ?: 0L)) }
    // The Savings section shows (and saves) one month's target; it opens on this month's.
    var month by remember { mutableIntStateOf(MonthKey.current()) }
    var form by remember { mutableStateOf(SavingsForm.of(row?.monthlyTarget?.let { SavingsSetting(true, it) })) }
    // Only a parent account's target can sum its subaccounts'; creating, a parent is one with no parent.
    val canSumChildren = account?.isParent ?: (parent == null)
    // The shown month's sum of subaccount targets, read while the target sums them.
    var childSum by remember { mutableLongStateOf(0L) }
    var iconId by remember { mutableStateOf(account?.iconId) }
    var color by remember { mutableStateOf(account?.color ?: parent?.color ?: DefaultCategoryColor) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var hasTrades by remember { mutableStateOf(false) }
    var childCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(account) {
        hasTrades = account?.let { viewModel.hasTrades(it.id) } ?: false
        if (account != null && editingParent) childCount = viewModel.childCount(account.id)
    }

    // Editing saves on Save only. Each month's target as read, and as changed since: Save writes
    // every changed month, so moving between months keeps what was typed.
    val loadedForms = remember { mutableStateMapOf<Int, SavingsForm>() }
    val editedForms = remember { mutableStateMapOf<Int, SavingsForm>() }
    fun showMonth(m: Int) {
        if (account == null) {
            month = m
            return
        }
        // The month being left keeps its pending change.
        if (loadedForms[month] != null) editedForms[month] = form
        month = m
        editedForms[m]?.let {
            form = it
            return
        }
        scope.launch {
            val loaded = SavingsForm.of(viewModel.savingsAt(account.id, m))
            loadedForms[m] = loaded
            if (month == m) form = loaded
        }
    }
    // Every month whose target differs from what was read, the shown one included.
    fun changedForms(): Map<Int, SavingsForm> =
        (editedForms + (loadedForms[month]?.let { mapOf(month to form) } ?: emptyMap()))
            .filter { (m, f) -> loadedForms[m] != null && f != loadedForms[m] }
    // The row only knows a target that is on: read this month's in full (an off one keeps its amount).
    LaunchedEffect(Unit) { if (account != null) showMonth(month) }
    LaunchedEffect(month, form.sumChildren) {
        if (account != null && form.sumChildren) childSum = viewModel.childTargetSum(account.id, month)
    }

    // Compared against the values the editor opened with, for the discard check. Editing, the
    // month shown is not a change; the Savings section counts through changedForms.
    val fields = listOf(accountType, name, description, Money.parseInput(balanceText), iconId, color, exempt, privacy) +
        if (account == null) listOf(month, form) else emptyList()
    val initialFields = remember { fields }
    val changed = changedForms()
    val savingsShown = accountType == AccountType.SAVINGS && form.enabled
    EditorScaffold(
        title = when {
            account != null -> stringResource(R.string.balance_edit_title)
            parent != null -> stringResource(R.string.balance_create_sub_title)
            else -> stringResource(R.string.balance_create_title)
        },
        onCancel = onDismiss,
        dirty = fields != initialFields || changed.isNotEmpty(),
        onSave = if (account != null) {
            {
                viewModel.update(account.id, name.trim(), iconId, color, Money.parseInput(balanceText).takeIf { !editingParent }, accountType, description.trim().ifEmpty { null })
                if (exempt != account.parentSavingExempt) viewModel.setParentSavingExempt(account.id, exempt)
                if (privacy != account.ownPrivacy) viewModel.setPrivacy(account.id, privacy)
                changed.forEach { (m, f) -> viewModel.setSavingsMonth(account.id, m, f.toSetting()) }
                onDismiss()
            }
        } else {
            {
                val savings = form.toSetting().takeIf { savingsShown }
                viewModel.create(name.trim(), iconId, color, Money.parseInput(balanceText), accountType, savings, month, description.trim().ifEmpty { null }, parent?.id, exempt, privacy)
                onDismiss()
            }
        },
        // A savings target that is on needs an amount, in every month changed.
        saveEnabled = name.isNotBlank() && (!savingsShown || form.complete) && changed.values.all { it.complete },
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
                enabled = !typeLocked,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.balance_type_normal)) }
            OutgoSegmentedButton(
                selected = accountType == AccountType.SAVINGS,
                // Normal subaccounts become savings ones with it: asked first.
                onClick = { if (normalChildren > 0 && accountType != AccountType.SAVINGS) showConvertConfirm = true else accountType = AccountType.SAVINGS },
                enabled = !typeLocked,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.balance_type_savings)) }
        }
        if (typeLocked) {
            Text(
                stringResource(R.string.balance_type_locked_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.balance_name_hint)) },
            singleLine = true,
            isError = account != null && name.isBlank(),
            modifier = Modifier.fillMaxWidth(),
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
        MoneyTextField(
            text = balanceText,
            onTextChange = { balanceText = it },
            label = stringResource(R.string.balance_current_balance_hint),
            enabled = !editingParent,
            supportingText = if (editingParent) { { Text(stringResource(R.string.balance_parent_balance_note)) } } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.category_color_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        ColorPickerGrid(selected = color, onSelect = { color = it })

        // A savings parent's subaccount can stay out of its savings progress.
        if (parent?.accountType == AccountType.SAVINGS) {
            Spacer(Modifier.height(24.dp))
            SectionHeader(
                stringResource(R.string.balance_parent_saving_exception),
                stringResource(R.string.balance_parent_saving_exception_info),
            ) {
                SectionSwitch(checked = exempt, onCheckedChange = { exempt = it })
            }
        }

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
            if (canSumChildren) {
                SwitchField(
                    stringResource(R.string.balance_savings_sum_children),
                    checked = form.sumChildren,
                    onCheckedChange = { form = form.copy(sumChildren = it) },
                )
                Spacer(Modifier.height(8.dp))
            }
            // Summing: the target shows the sum and can't be typed.
            MoneyTextField(
                text = if (form.sumChildren) Money.groupThousands(childSum) else form.targetText,
                onTextChange = { form = form.copy(targetText = it) },
                label = stringResource(R.string.balance_target_hint),
                enabled = !form.sumChildren,
                isError = account != null && !form.complete,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))
        PrivacySection(privacy, parent, accountType == AccountType.SAVINGS) { privacy = it }
    }

    if (showConvertConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.balance_convert_subs_title),
            message = stringResource(R.string.balance_convert_subs_message, normalChildren),
            onConfirm = {
                accountType = AccountType.SAVINGS
                showConvertConfirm = false
            },
            onDismiss = { showConvertConfirm = false },
        )
    }

    if (showIconPicker) {
        IconPickerSheet(onIconSelected = { iconId = it; showIconPicker = false }, onDismiss = { showIconPicker = false })
    }

    if (showDeleteConfirm && account != null) {
        ConfirmDialog(
            title = if (hasTrades) stringResource(R.string.balance_archive_confirm_title) else stringResource(R.string.balance_delete_confirm_title),
            message = when {
                editingParent && childCount > 0 -> stringResource(R.string.balance_delete_parent_confirm_message, childCount)
                hasTrades -> stringResource(R.string.balance_archive_confirm_message)
                else -> stringResource(R.string.balance_delete_confirm_message)
            },
            onConfirm = {
                viewModel.deleteOrArchive(account)
                showDeleteConfirm = false
                onDismiss()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/**
 * The editor's Privacy section: [own] is the account's own settings. A parent's settings that cover
 * this subaccount show instead, locked, with a note saying so. Progress settings are for savings accounts.
 */
@Composable
private fun PrivacySection(own: AccountPrivacy, parent: AccountEntity?, savings: Boolean, onChange: (AccountPrivacy) -> Unit) {
    val fromParent = parent?.ownPrivacy
    val balanceLocked = fromParent?.hideBalance == true
    val progressLocked = savings && fromParent != null && fromParent.progressHide != SavingProgressHide.NEVER
    val progress = if (progressLocked) fromParent!! else own
    SectionHeader(stringResource(R.string.balance_privacy), stringResource(R.string.balance_privacy_info))
    SwitchField(
        stringResource(R.string.balance_hide_balance),
        checked = own.hideBalance || balanceLocked,
        onCheckedChange = { onChange(own.copy(hideBalance = it)) },
        enabled = !balanceLocked,
    )
    if (savings) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.balance_hide_progress), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        val options = listOf(
            SavingProgressHide.NEVER to R.string.balance_hide_progress_never,
            SavingProgressHide.WHEN_REACHED to R.string.balance_hide_progress_reached,
            SavingProgressHide.ALWAYS to R.string.balance_hide_progress_always,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                OutgoSegmentedButton(
                    selected = progress.progressHide == mode,
                    onClick = { onChange(own.copy(progressHide = mode)) },
                    enabled = !progressLocked,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(stringResource(label)) }
            }
        }
        // Only while the progress is hidden somewhere: it keeps it in Analysis.
        if (progress.progressHide != SavingProgressHide.NEVER) {
            Spacer(Modifier.height(8.dp))
            SwitchField(
                stringResource(R.string.balance_progress_in_analysis),
                checked = progress.progressInAnalysis,
                onCheckedChange = { onChange(own.copy(progressInAnalysis = it)) },
                enabled = !progressLocked,
            )
        }
    }
    if (balanceLocked || progressLocked) {
        Text(
            stringResource(R.string.balance_privacy_from_parent, parent!!.name),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
    }
}
