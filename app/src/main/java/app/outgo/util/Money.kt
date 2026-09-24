package app.outgo.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * All money in Outgo is a [Long] in the smallest unit of the currency
 * (VND has no subunit in everyday use, so 1 unit == 1 đồng). Never use
 * Float/Double for money to avoid rounding drift.
 */
object Money {
    private val groupingSeparator = DecimalFormatSymbols(Locale.US).groupingSeparator

    const val DEFAULT_SYMBOL = "$"

    /**
     * Currency symbol appended to every formatted amount. Snapshot state, so changing it
     * recomposes every screen showing an amount without threading it through their state.
     * Loaded from the `setting` table at startup; see SettingRepository.observeCurrency.
     */
    var symbol: String by mutableStateOf(DEFAULT_SYMBOL)

    /** "1234567" -> "1.234.567 $" */
    fun format(amount: Long): String = "${groupThousands(amount)} $symbol"

    /** Masked amount for when the user has hidden a balance, e.g. "--- $". */
    fun formatHidden(): String = "--- $symbol"

    /** Same as [format] but with an explicit sign for positive amounts, e.g. "+50.000 $". */
    fun formatSigned(amount: Long): String {
        val sign = if (amount > 0) "+" else if (amount < 0) "-" else ""
        return "$sign${groupThousands(kotlin.math.abs(amount))} $symbol"
    }

    /** Same as [formatSigned] but without the currency symbol, e.g. "+50.000". */
    fun formatSignedNoCurrency(amount: Long): String {
        val sign = if (amount > 0) "+" else if (amount < 0) "-" else ""
        return "$sign${groupThousands(kotlin.math.abs(amount))}"
    }

    /** "1234567" -> "1.234.567" (no currency symbol), used inside the amount input field. */
    fun groupThousands(amount: Long): String {
        val negative = amount < 0
        val digits = kotlin.math.abs(amount).toString()
        val sb = StringBuilder()
        for ((index, ch) in digits.reversed().withIndex()) {
            if (index != 0 && index % 3 == 0) sb.append(groupingSeparator)
            sb.append(ch)
        }
        return (if (negative) "-" else "") + sb.reverse().toString()
    }

    /** Strips everything but digits, e.g. from raw keyboard input. Never negative. */
    fun parseDigits(raw: String): Long {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return 0L
        return digits.toLongOrNull() ?: Long.MAX_VALUE
    }
}
