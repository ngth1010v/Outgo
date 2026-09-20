package app.outgo.ui.setting

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.outgo.R

/** The 20 most traded currencies, plus VND. Only [symbol] is stored; [code] is just the list key. */
internal data class Currency(val code: String, val symbol: String, @StringRes val regionRes: Int)

internal val DefaultCurrencies = listOf(
    Currency("USD", "$", R.string.currency_region_usd),
    Currency("EUR", "€", R.string.currency_region_eur),
    Currency("JPY", "¥", R.string.currency_region_jpy),
    Currency("GBP", "£", R.string.currency_region_gbp),
    Currency("CNY", "CN¥", R.string.currency_region_cny),
    Currency("AUD", "A$", R.string.currency_region_aud),
    Currency("CAD", "C$", R.string.currency_region_cad),
    Currency("CHF", "Fr", R.string.currency_region_chf),
    Currency("HKD", "HK$", R.string.currency_region_hkd),
    Currency("SGD", "S$", R.string.currency_region_sgd),
    Currency("SEK", "kr", R.string.currency_region_sek),
    Currency("KRW", "₩", R.string.currency_region_krw),
    Currency("NOK", "Nkr", R.string.currency_region_nok),
    Currency("NZD", "NZ$", R.string.currency_region_nzd),
    Currency("INR", "₹", R.string.currency_region_inr),
    Currency("MXN", "Mex$", R.string.currency_region_mxn),
    Currency("TWD", "NT$", R.string.currency_region_twd),
    Currency("ZAR", "R", R.string.currency_region_zar),
    Currency("BRL", "R$", R.string.currency_region_brl),
    Currency("VND", "₫", R.string.currency_region_vnd),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrencyPickerSheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    // Anything not in the list is a symbol the user typed in themselves.
    var custom by remember { mutableStateOf(if (DefaultCurrencies.none { it.symbol == selected }) selected else "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text(stringResource(R.string.setting_currency), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(DefaultCurrencies, key = { it.code }) { currency ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(currency.symbol) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = currency.symbol == selected, onClick = { onSelect(currency.symbol) })
                        Text(
                            stringResource(currency.regionRes),
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        Text(currency.symbol, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                singleLine = true,
                label = { Text(stringResource(R.string.setting_currency_custom)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.setting_currency_custom_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onSelect(custom.trim()) },
                enabled = custom.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.common_save)) }
        }
    }
}
