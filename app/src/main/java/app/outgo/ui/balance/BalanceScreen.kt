package app.outgo.ui.balance

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.entity.AccountEntity
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.ConfirmDialog
import app.outgo.ui.component.IconPickerSheet
import app.outgo.ui.component.IconView
import app.outgo.ui.component.PlusRow
import app.outgo.util.Money
import kotlinx.coroutines.launch

@Composable
fun BalanceScreen() {
    val container = LocalAppContainer.current
    val viewModel: BalanceViewModel = viewModel(
        factory = viewModelFactory { initializer { BalanceViewModel(container.accountRepository) } },
    )
    val accounts by viewModel.accounts.collectAsState()
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    Scaffold { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            items(accounts, key = { it.id }) { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = account }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconView(iconId = account.iconId, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(account.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(Money.format(account.balance), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item { PlusRow(onClick = { showCreate = true }) }
        }
    }

    if (showCreate) {
        EditAccountSheet(
            account = null,
            onDismiss = { showCreate = false },
            viewModel = viewModel,
        )
    }
    editing?.let { account ->
        EditAccountSheet(account = account, onDismiss = { editing = null }, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAccountSheet(account: AccountEntity?, onDismiss: () -> Unit, viewModel: BalanceViewModel) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var balanceText by remember { mutableStateOf(account?.balance?.takeIf { it != 0L }?.toString().orEmpty()) }
    var iconId by remember { mutableStateOf(account?.iconId) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var hasTrades by remember { mutableStateOf(false) }

    LaunchedEffect(account) {
        hasTrades = account?.let { viewModel.hasTrades(it.id) } ?: false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (account == null) stringResource(R.string.balance_create_title) else stringResource(R.string.balance_edit_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconView(iconId = iconId, size = 48.dp, modifier = Modifier.padding(end = 12.dp))
                OutlinedButton(onClick = { showIconPicker = true }) { Text(stringResource(R.string.common_choose_icon)) }
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
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            val balance = balanceText.toLongOrNull() ?: 0L
            Button(
                onClick = {
                    if (account == null) {
                        viewModel.create(name, iconId, balance)
                    } else {
                        viewModel.update(account.id, name, iconId, balance)
                    }
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.common_save)) }

            if (account != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            }
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
                viewModel.deleteOrArchive(account)
                showDeleteConfirm = false
                onDismiss()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
