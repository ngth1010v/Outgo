package app.outgo.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.nav.HistoryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    type: HistoryType,
    onBack: () -> Unit,
    onOpenTrade: (Long) -> Unit,
    dayStartMillis: Long? = null,
) {
    val container = LocalAppContainer.current
    val viewModel: HistoryViewModel = viewModel(
        key = "history-${type.arg}-${dayStartMillis ?: 0L}",
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    container.tradeRepository,
                    container.accountRepository,
                    container.categoryRepository,
                    type,
                    dayStartMillis,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) { viewModel.refresh() }

    val items = state.items
    LoadMoreOnScrollEnd(listState, state.canLoadMore, viewModel::loadMore)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (type) {
                            HistoryType.EXPENSE -> stringResource(R.string.home_expense_history)
                            HistoryType.INCOME -> stringResource(R.string.home_income_history)
                            HistoryType.TRANSFER -> stringResource(R.string.home_transfer_history)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ph_caret_right), contentDescription = null, modifier = Modifier.rotate(180f))
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty() && !state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            historyItems(items, state.categoriesById, state.accountsById, onOpenTrade)
        }
    }
}
