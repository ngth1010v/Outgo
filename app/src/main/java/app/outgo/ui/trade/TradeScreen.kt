package app.outgo.ui.trade

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.rememberDiscardGuard
import app.outgo.ui.component.DeleteButton
import app.outgo.ui.component.EditorTitle
import app.outgo.ui.theme.WarningDarkYellow
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.OutlinedButton
import app.outgo.ui.component.OutgoSegmentedButton
import app.outgo.ui.component.AmountField
import app.outgo.ui.component.ConfirmDialog
import app.outgo.ui.component.AccountSheet
import app.outgo.ui.component.BudgetSheet
import app.outgo.ui.component.GroupedPickerSheet
import app.outgo.ui.category.SectionHeader
import app.outgo.ui.component.SectionSwitch
import app.outgo.ui.component.ExceptionBadge
import app.outgo.ui.component.IconView
import app.outgo.ui.component.PickCell
import app.outgo.ui.component.rememberSwipeLevel
import app.outgo.ui.component.swipeNeighbor
import app.outgo.ui.component.swipeShift
import app.outgo.ui.component.swipeStep
import app.outgo.util.formatDate
import app.outgo.util.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    editingTradeId: Long?,
    onClose: () -> Unit,
    visible: Boolean = true,
    /** Edits this transaction of the draft list instead of a saved one. */
    editingDraftId: Long? = null,
    /** The Add screen's "Draft (n)" button. */
    onOpenDrafts: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val viewModel: TradeViewModel = viewModel(
        key = "trade-$editingTradeId-$editingDraftId",
        factory = viewModelFactory {
            initializer {
                TradeViewModel(
                    container.categoryRepository,
                    container.accountRepository,
                    container.tradeRepository,
                    container.budgetRepository,
                    container.settingRepository,
                    container.draftStore,
                    editingTradeId,
                    editingDraftId,
                )
            }
        },
    )
    // The tab stays composed: on coming back, re-read the quick picks so a category edited meanwhile
    // (name, icon, parent budget exception) shows as it is now. Not on first show: init loads them.
    var wasHidden by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) {
            wasHidden = true
            // Errors marked by an earlier save don't greet the user on coming back.
            viewModel.clearErrors()
        } else if (wasHidden) {
            viewModel.refreshPickers()
        }
    }
    // A new trade's time follows the clock while the form is on screen, and on coming back to it
    // (from another tab, or the app from the background) until the user picks a date or time.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(visible, lifecycle) {
        if (!visible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshTime()
                delay(TIME_REFRESH_MS)
            }
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A new trade starts at its amount: focused, keyboard up, when the tab is shown with nothing
    // typed yet. A form already holding an amount is left alone, so the keyboard doesn't cover it
    // and its save button. After the first frame, so the keyboard never holds up the cold start's
    // first paint.
    val amountFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(visible) {
        if (!visible || editingTradeId != null || editingDraftId != null) return@LaunchedEffect
        withFrameNanos { }
        val now = viewModel.state.value
        if (now.amount > 0 && PredictField.AMOUNT !in now.predicted) return@LaunchedEffect
        runCatching { amountFocus.requestFocus() }
        keyboard?.show()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAllCategories by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Expense <-> Income <-> Transfer; past either end the tab level takes the swipe.
    val typeSwipe = rememberSwipeLevel { next, _ ->
        val type = TRADE_TYPES.getOrNull(TRADE_TYPES.indexOf(state.type) + if (next) 1 else -1)
        if (state.isEditing || type == null) null else { { viewModel.onTypeChange(type) } }
    }
    // Editing only: the loaded trade's fields, to tell whether leaving would drop changes.
    val fields = listOf(state.type, state.amount, state.selectedCategory?.id, state.selectedAccountId, state.selectedToAccountId, state.occurredAt, state.note, state.effectiveToBudget?.id)
    var initialFields by remember { mutableStateOf<List<Any?>?>(null) }
    LaunchedEffect(state.isLoading) { if (!state.isLoading && initialFields == null) initialFields = fields }
    val cancel = if (state.isEditing) rememberDiscardGuard(initialFields != null && fields != initialFields, onClose) else onClose
    // The Add tab's form: Back with something typed asks before the app closes and drops it.
    var askDiscardInput by remember { mutableStateOf(false) }
    var leaveAfterDiscard by remember { mutableStateOf(false) }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    BackHandler(enabled = !state.isEditing && visible && state.hasInput) { askDiscardInput = true }
    // Once the input is gone this handler is off, so passing Back on does what Back would have done.
    LaunchedEffect(leaveAfterDiscard, state.hasInput) {
        if (leaveAfterDiscard && !state.hasInput) {
            leaveAfterDiscard = false
            backDispatcher?.onBackPressed()
        }
    }
    if (askDiscardInput) {
        ConfirmDialog(
            title = stringResource(R.string.trade_discard_input_title),
            message = stringResource(R.string.trade_discard_input_message),
            confirmLabel = stringResource(R.string.discard_confirm),
            dismissLabel = stringResource(R.string.discard_keep_editing),
            onConfirm = {
                askDiscardInput = false
                viewModel.discardInput()
                leaveAfterDiscard = true
            },
            onDismiss = { askDiscardInput = false },
        )
    }
    val savedLabel = stringResource(R.string.trade_saved)
    val undoLabel = stringResource(R.string.trade_undo)
    val draftedLabel = stringResource(R.string.draft_added)
    val resources = LocalContext.current.resources
    // The save button's sideways shake, in dp, played once per save tapped with errors.
    val saveShake = remember { Animatable(0f) }
    LaunchedEffect(state.invalidSaveCount) {
        if (state.invalidSaveCount == 0) return@LaunchedEffect
        saveShake.snapTo(0f)
        saveShake.animateTo(
            0f,
            keyframes {
                durationMillis = 250
                -5f at 40
                5f at 90
                -3f at 140
                3f at 190
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TradeEvent.Saved -> {
                    val result = snackbarHostState.showSnackbar(savedLabel, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.undo(event.tradeId)
                    }
                }
                is TradeEvent.Drafted -> {
                    val result = snackbarHostState.showSnackbar(draftedLabel, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.undoDraft(event.draftId)
                    }
                }
                is TradeEvent.Pushed -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getQuantityString(R.plurals.draft_pushed, event.count, event.count))
                }
                TradeEvent.SavedAndClose, TradeEvent.DeletedAndClose -> onClose()
                is TradeEvent.Error -> scope.launch { snackbarHostState.showSnackbar(event.message) }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .swipeStep(typeSwipe)
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = if (state.isEditing) 16.dp else 36.dp, end = 16.dp, bottom = 16.dp),
        ) {
            if (state.isEditing) {
                EditorTitle(
                    stringResource(if (state.isDraftEdit) R.string.draft_edit_title else R.string.trade_edit_title),
                    onBack = cancel,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            ExpenseIncomeToggle(type = state.type, onTypeChange = viewModel::onTypeChange, enabled = !state.isEditing)
            Box {
                @Composable
                fun form(shown: TradeUiState, modifier: Modifier, shake: () -> Float = { 0f }, focus: FocusRequester? = null) = TradeForm(
                    state = shown,
                    viewModel = viewModel,
                    amountFocus = focus,
                    onSeeAll = { showAllCategories = true },
                    onClose = cancel,
                    onDelete = { showDeleteConfirm = true },
                    onOpenDrafts = onOpenDrafts,
                    saveShake = shake,
                    modifier = modifier,
                )
                form(state, Modifier.swipeShift(typeSwipe), { saveShake.value }, amountFocus)
                // The neighbor types' forms, as the type switch would show them.
                if (typeSwipe.moving) {
                    listOf(-1, 1).forEach { page ->
                        TRADE_TYPES.getOrNull(TRADE_TYPES.indexOf(state.type) + page)?.let { type ->
                            form(
                                state.copy(type = type, selectedCategory = null, selectedParentCategory = null, showErrors = false),
                                Modifier.swipeNeighbor().swipeShift(typeSwipe, page),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAllCategories) {
        AllCategoriesSheet(
            type = state.type,
            onSelect = {
                viewModel.onCategorySelected(it)
                showAllCategories = false
            },
            onDismiss = { showAllCategories = false },
        )
    }

    if (showDeleteConfirm && state.isDraftEdit) {
        // Not in the balances yet: removing a draft needs no warning, just the question.
        ConfirmDialog(
            title = stringResource(R.string.draft_remove_confirm_title),
            message = "",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteEditingTrade()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    } else if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_confirm_title),
            // Allowed, but said first: deleting (an income, say) takes this account below 0.
            message = state.overdrawnByDelete?.let { stringResource(R.string.trade_delete_negative_warning, it.name) }.orEmpty(),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteEditingTrade()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/** How often the shown time of a new trade catches up with the clock. */
private const val TIME_REFRESH_MS = 30_000L

private val TRADE_TYPES = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME, TradeType.TRANSFER)

/** Everything below the type toggle; drawn once per type while a type swipe moves. */
@Composable
private fun TradeForm(
    state: TradeUiState,
    viewModel: TradeViewModel,
    /** The shown form's amount field; null on the neighbor forms a type swipe draws. */
    amountFocus: FocusRequester?,
    onSeeAll: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onOpenDrafts: () -> Unit,
    saveShake: () -> Float,
    modifier: Modifier = Modifier,
) {
    val errors = state.shownErrors
    @Composable
    fun errorFor(error: TradeError, message: Int): String? = if (error in errors) stringResource(message) else null
    val selfTransferBudgetError = errorFor(TradeError.SELF_TRANSFER_BUDGET, R.string.trade_error_self_transfer_budget)
    val pickedCategoryId = state.selectedCategory?.id.takeIf { PredictField.CATEGORY !in state.predicted }
    Column(modifier) {
        Spacer(Modifier.height(16.dp))

        // An automatic transfer shows its fields but takes no input: a layer on top eats the taps.
        Box {
            Column {
                AmountField(
                    // A predicted amount shows faded where the "0" goes; typing replaces it.
                    amount = if (PredictField.AMOUNT in state.predicted) 0 else state.amount,
                    predicted = if (PredictField.AMOUNT in state.predicted) state.amount else 0,
                    onAmountChange = viewModel::onAmountChange,
                    isIncome = state.type == CategoryKind.INCOME,
                    isTransfer = state.isTransfer,
                    modifier = Modifier.padding(vertical = 8.dp),
                    focusRequester = amountFocus ?: remember { FocusRequester() },
                )
                ErrorText(errorFor(TradeError.AMOUNT, R.string.trade_error_amount), centered = true)

                if (!state.isTransfer) {
                    SelectedCategoryChip(
                        state.selectedParentCategory,
                        state.selectedCategory,
                        predicted = PredictField.CATEGORY in state.predicted,
                        onDismiss = { viewModel.dismissPrediction(PredictField.CATEGORY) },
                    )
                    ErrorText(errorFor(TradeError.CATEGORY, R.string.trade_error_category), centered = true)
                    Spacer(Modifier.height(8.dp))

                    CategoryPickerSection(
                        title = stringResource(R.string.trade_recent),
                        categories = state.picker.recent,
                        selectedId = pickedCategoryId,
                        onSelect = viewModel::onCategorySelected,
                        onSeeAll = onSeeAll,
                    )
                    Spacer(Modifier.height(16.dp))
                    CategoryPickerSection(
                        title = stringResource(R.string.trade_top_used),
                        categories = state.picker.top,
                        selectedId = pickedCategoryId,
                        onSelect = viewModel::onCategorySelected,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                AccountDropdown(
                    label = if (state.isTransfer) stringResource(R.string.trade_from_account_label) else stringResource(R.string.trade_account_label),
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    onSelect = { viewModel.onAccountSelected(it.id) },
                    predicted = PredictField.ACCOUNT in state.predicted,
                    onOpen = { viewModel.dismissPrediction(PredictField.ACCOUNT) },
                    error = errorFor(TradeError.ACCOUNT, R.string.trade_error_account),
                    // Live, and only a warning: any account the trade takes below 0, the "To" one included.
                    warning = state.overdrawnBySave?.let { stringResource(R.string.trade_warning_negative_balance, it.name) },
                )
                Spacer(Modifier.height(16.dp))

                if (state.isTransfer) {
                    // A self-transfer missing both budgets marks both; the message goes under "To budget"
                    // when it shows (it always does on a self-transfer), else here.
                    BudgetDropdown(
                        // Into the same account only budget moves, so one of the two budgets is required.
                        label = stringResource(if (state.isSelfTransfer) R.string.trade_from_budget_label_self else R.string.trade_from_budget_label),
                        budgets = state.budgetOptions,
                        selected = state.selectedCategory,
                        onSelect = viewModel::onBudgetSelected,
                        predicted = PredictField.CATEGORY in state.predicted,
                        onOpen = { viewModel.dismissPrediction(PredictField.CATEGORY) },
                        isError = selfTransferBudgetError != null,
                        error = selfTransferBudgetError.takeUnless { state.showToBudget },
                    )
                    Spacer(Modifier.height(16.dp))

                    AccountDropdown(
                        label = stringResource(R.string.trade_to_account_label),
                        accounts = state.accounts,
                        selectedAccount = state.selectedToAccount,
                        onSelect = { viewModel.onToAccountSelected(it.id) },
                        predicted = PredictField.TO_ACCOUNT in state.predicted,
                        onOpen = { viewModel.dismissPrediction(PredictField.TO_ACCOUNT) },
                        error = errorFor(TradeError.TO_ACCOUNT, R.string.trade_error_to_account),
                    )
                    Spacer(Modifier.height(16.dp))
                    if (state.isSelfTransfer) {
                        Text(
                            stringResource(R.string.trade_self_transfer_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    SavingExemptRow(state, viewModel::onSavingExemptChange)

                    if (state.showToBudget) {
                        BudgetDropdown(
                            label = stringResource(if (state.isSelfTransfer) R.string.trade_to_budget_label_self else R.string.trade_to_budget_label),
                            budgets = state.toBudgetOptions,
                            selected = state.effectiveToBudget,
                            onSelect = viewModel::onToBudgetSelected,
                            predicted = PredictField.TO_BUDGET in state.predicted,
                            onOpen = { viewModel.dismissPrediction(PredictField.TO_BUDGET) },
                            isError = selfTransferBudgetError != null,
                            error = selfTransferBudgetError,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                } else if (state.type == CategoryKind.EXPENSE) {
                    BudgetExemptRow(state, viewModel::onBudgetExemptChange)
                } else {
                    SavingExemptRow(state, viewModel::onSavingExemptChange)
                }

                DateTimeRow(
                    occurredAt = state.occurredAt,
                    onDateChange = viewModel::onDateChange,
                    onTimeChange = viewModel::onTimeChange,
                )
                Spacer(Modifier.height(16.dp))

                NoteField(state, viewModel)
            }
            if (state.locked) Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
        }
        Spacer(Modifier.height(16.dp))

        if (state.locked) {
            Text(
                stringResource(R.string.trade_auto_transfer_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
            ) {
                Text(stringResource(R.string.common_close))
            }
            return@Column
        }

        // Always tappable: a save with errors marks them and shakes the buttons instead.
        @Composable
        fun saveButton(modifier: Modifier) = androidx.compose.material3.Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = modifier.height(52.dp),
        ) {
            // With a draft list started, Save adds to it too (see TradeViewModel.save).
            Text(
                stringResource(if (state.drafting) R.string.draft_add else R.string.common_save),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }

        if (state.isEditing) {
            saveButton(Modifier.offset { IntOffset(saveShake().dp.roundToPx(), 0) }.fillMaxWidth())
            // The editors' shared order (see EditorScaffold): Save, Cancel, then Delete.
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_cancel))
            }
            if (state.isDraftEdit) DeleteButton(onClick = onDelete, label = stringResource(R.string.draft_remove)) else DeleteButton(onClick = onDelete)
        } else {
            // [Add to draft] [Save], or, with a draft list started, [Draft (n) ›] [Add to draft].
            Row(
                modifier = Modifier.offset { IntOffset(saveShake().dp.roundToPx(), 0) }.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = if (state.drafting) onOpenDrafts else viewModel::addToDraft,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(
                        if (state.drafting) stringResource(R.string.draft_view, state.draftCount) else stringResource(R.string.draft_add),
                        maxLines = 1,
                    )
                }
                saveButton(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExpenseIncomeToggle(type: Int, onTypeChange: (Int) -> Unit, enabled: Boolean = true) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        OutgoSegmentedButton(
            selected = type == CategoryKind.EXPENSE,
            onClick = { onTypeChange(CategoryKind.EXPENSE) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(0, 3),
        ) { Text(stringResource(R.string.trade_expense)) }
        OutgoSegmentedButton(
            selected = type == CategoryKind.INCOME,
            onClick = { onTypeChange(CategoryKind.INCOME) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(1, 3),
        ) { Text(stringResource(R.string.trade_income)) }
        OutgoSegmentedButton(
            selected = type == TradeType.TRANSFER,
            onClick = { onTypeChange(TradeType.TRANSFER) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(2, 3),
        ) { Text(stringResource(R.string.trade_transfer)) }
    }
}

/**
 * The trade's "Budget exception" row (an expense only) and "Saving exception"
 * row (an income or a transfer). A row's switch is off and disabled where it can't matter (see
 * [TradeUiState.showBudgetExempt]), and on and disabled for a subcategory or subaccount that is a
 * parent exception (see [TradeUiState.budgetExemptLocked]).
 */
@Composable
private fun BudgetExemptRow(state: TradeUiState, onChange: (Boolean) -> Unit) {
    // Hidden, not shown disabled, where it can't apply: no budget counts this category.
    if (!state.showBudgetExempt) return
    SectionHeader(stringResource(R.string.trade_budget_exception), stringResource(R.string.trade_budget_exception_info)) {
        SectionSwitch(
            checked = state.effectiveBudgetExempt,
            onCheckedChange = onChange,
            enabled = state.showBudgetExempt && !state.budgetExemptLocked,
            modifier = Modifier.predictedAlpha(PredictField.EXEMPT in state.predicted),
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SavingExemptRow(state: TradeUiState, onChange: (Boolean) -> Unit) {
    // Hidden where it can't apply: the money doesn't go into a savings account.
    if (!state.showSavingExempt) return
    SectionHeader(stringResource(R.string.trade_saving_exception), stringResource(R.string.trade_saving_exception_info)) {
        SectionSwitch(
            checked = state.effectiveSavingExempt,
            onCheckedChange = onChange,
            enabled = state.showSavingExempt && !state.savingExemptLocked,
            modifier = Modifier.predictedAlpha(PredictField.EXEMPT in state.predicted),
        )
    }
    Spacer(Modifier.height(12.dp))
}

/** How opaque a predicted field's value is drawn. */
private const val PREDICTED_ALPHA = 0.4f

private fun Modifier.predictedAlpha(predicted: Boolean) = if (predicted) alpha(PREDICTED_ALPHA) else this

/** A predicted category is drawn faded; tapping it takes the prediction away. */
@Composable
private fun SelectedCategoryChip(parent: CategoryEntity?, category: CategoryEntity?, predicted: Boolean, onDismiss: () -> Unit) {
    if (category == null) return
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (predicted) it.clickable(onClick = onDismiss) else it }
            .predictedAlpha(predicted)
            .padding(vertical = 2.dp),
    ) {
        if (parent != null) {
            IconView(iconId = parent.iconId, size = 20.dp, color = parent.color)
            Spacer(Modifier.width(6.dp))
            Text(parent.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text("/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
        }
        Box {
            IconView(iconId = category.iconId, size = 20.dp, color = category.color)
            if (category.isExempt) ExceptionBadge(20.dp)
        }
        Spacer(Modifier.width(6.dp))
        Text(category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CategoryPickerSection(
    title: String,
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (CategoryEntity) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll) { Text(stringResource(R.string.trade_all_categories) + " ›") }
            }
        }
        if (categories.isEmpty()) {
            Text(
                stringResource(R.string.trade_no_categories),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                categories.take(5).forEach { category ->
                    CategoryCell(
                        category = category,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(5 - categories.size.coerceAtMost(5)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryCell(category: CategoryEntity, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    PickCell(category.name, category.iconId, category.color, selected, onClick, modifier, exception = category.isExempt)

/** A field's red error message under it; nothing when [message] is null. */
@Composable
private fun ErrorText(message: String?, centered: Boolean = false) {
    if (message == null) return
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = if (centered) 0.dp else 4.dp),
    )
}

/** A dropdown field's border: red while it has an error. */
@Composable
private fun fieldBorderColor(isError: Boolean) =
    if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline

/**
 * The account field: tapping it opens [AccountSheet]. Shows only the picked (sub)account's own name.
 * An [error] turns the border red and shows under the field.
 */
@Composable
private fun AccountDropdown(
    accounts: List<AccountEntity>,
    selectedAccount: AccountEntity?,
    onSelect: (AccountEntity) -> Unit,
    label: String = stringResource(R.string.trade_account_label),
    error: String? = null,
    /** Shown in amber under the field while there is no [error]; doesn't block the save. */
    warning: String? = null,
    /** [selectedAccount] is a prediction: drawn faded, and [onOpen] (the tap) takes it away. */
    predicted: Boolean = false,
    onOpen: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, fieldBorderColor(error != null), RoundedCornerShape(8.dp))
                .clickable { onOpen(); open = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.predictedAlpha(predicted)) {
                IconView(iconId = selectedAccount?.iconId, size = 24.dp, color = selectedAccount?.color)
                if (selectedAccount?.isExempt == true) ExceptionBadge(24.dp)
            }
            Spacer(Modifier.width(8.dp))
            Text(selectedAccount?.name ?: "—", modifier = Modifier.weight(1f).predictedAlpha(predicted))
            Icon(painterResource(R.drawable.ph_caret_down), contentDescription = null)
        }
        ErrorText(error)
        if (error == null && warning != null) {
            Text(
                warning,
                style = MaterialTheme.typography.bodySmall,
                color = WarningDarkYellow,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp),
            )
        }
    }
    if (open) {
        AccountSheet(
            accounts = accounts,
            selectedId = selectedAccount?.id,
            onSelect = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}

/**
 * A transfer's optional "From budget" / "To budget", styled like [AccountDropdown]: tapping it opens
 * [BudgetSheet]. [selected] is the budget's category; a budget removed since still shows by its name.
 * [isError] turns the border red; [error], if any, shows under the field.
 */
@Composable
private fun BudgetDropdown(
    label: String,
    budgets: List<BudgetWithProgress>,
    selected: CategoryEntity?,
    onSelect: (Long?) -> Unit,
    isError: Boolean = false,
    error: String? = null,
    /** [selected] is a prediction: drawn faded, and [onOpen] (the tap) takes it away. */
    predicted: Boolean = false,
    onOpen: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    val budget = budgets.find { it.categoryId == selected?.id }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, fieldBorderColor(isError), RoundedCornerShape(8.dp))
                .clickable { onOpen(); open = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).predictedAlpha(predicted), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) {
                IconView(iconId = budget?.displayIconId ?: selected.iconId, size = 24.dp, color = budget?.categoryColor ?: selected.color)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                budget?.displayName ?: selected?.name ?: stringResource(R.string.trade_no_budget),
                color = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
            }
            Icon(painterResource(R.drawable.ph_caret_down), contentDescription = null)
        }
        ErrorText(error)
    }
    if (open) {
        BudgetSheet(
            budgets = budgets,
            selectedId = selected?.id,
            onSelect = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}

/**
 * The note. A predicted whole note shows faded in the empty field (as its label, so the two never
 * overlap) until the field is tapped. While typing, the predicted rest of the note shows faded
 * after the text; the check button takes it.
 */
@Composable
private fun NoteField(state: TradeUiState, viewModel: TradeViewModel) {
    val predicted = PredictField.NOTE in state.predicted
    val ghost = MaterialTheme.colorScheme.onSurface.copy(alpha = PREDICTED_ALPHA)
    // Still focused after a save, the field never gets a new focus event: a note predicted then goes too.
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(predicted, focused) { if (predicted && focused) viewModel.dismissPrediction(PredictField.NOTE) }
    OutlinedTextField(
        value = if (predicted) "" else state.note,
        onValueChange = viewModel::onNoteChange,
        label = {
            if (predicted) Text(state.note, color = ghost, maxLines = 1) else Text(stringResource(R.string.trade_note_hint))
        },
        singleLine = true,
        visualTransformation = if (state.noteSuffix.isEmpty()) VisualTransformation.None else GhostSuffix(state.noteSuffix, ghost),
        trailingIcon = if (state.noteSuffix.isEmpty()) null else {
            {
                IconButton(onClick = viewModel::acceptNoteSuffix) {
                    Icon(painterResource(R.drawable.ph_check), contentDescription = stringResource(R.string.trade_note_accept))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
    )
}

/** Draws [suffix] in [color] after the text; the cursor never goes into it. */
private data class GhostSuffix(val suffix: String, val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = color)) { append(suffix) }
        },
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset
            override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(text.length)
        },
    )
}

@Composable
private fun DateTimeRow(occurredAt: Long, onDateChange: (Long) -> Unit, onTimeChange: (Int, Int) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlineBox(
            text = formatDate(occurredAt),
            icon = R.drawable.ph_calendar_blank,
            modifier = Modifier.weight(1f),
            onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = occurredAt }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val picked = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
                        onDateChange(picked.timeInMillis)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
        )
        OutlineBox(
            text = formatTime(occurredAt),
            icon = R.drawable.ph_clock,
            modifier = Modifier.weight(1f),
            onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = occurredAt }
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onTimeChange(hour, minute) },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true,
                ).show()
            },
        )
    }
}

@Composable
private fun OutlineBox(text: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Text(text)
    }
}

@Composable
private fun AllCategoriesSheet(type: Int, onSelect: (CategoryEntity) -> Unit, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    // Remembered: a fresh Flow per recomposition would re-run the query.
    val all by remember(type) { container.categoryRepository.observeAllOfType(type) }.collectAsStateWithLifecycle(initialValue = emptyList())
    GroupedPickerSheet(
        title = stringResource(R.string.trade_select_category_title),
        searchHint = stringResource(R.string.trade_search_category_hint),
        items = all,
        parentId = { it.parentId },
        id = { it.id },
        name = { it.name },
        selectedId = null,
        cell = { item, selected, onClick, modifier -> CategoryCell(item, selected, onClick, modifier) },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}
