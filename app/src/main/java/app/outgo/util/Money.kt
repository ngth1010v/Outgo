package app.outgo.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** How amounts are written: which character groups thousands and which starts the decimals. */
enum class NumberStyle(val key: String, val grouping: Char, val decimal: Char) {
    /** 1,234.56 */
    COMMA_DOT("comma_dot", ',', '.'),

    /** 1.234,56 */
    DOT_COMMA("dot_comma", '.', ',');

    companion object {
        val DEFAULT = COMMA_DOT
        fun fromKey(key: String?): NumberStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * All money in Outgo is a [Long] in hundredths of the currency unit (schema 21+), so 4.50$ is
 * 450 and 45,000₫ is 4,500,000. Never use Float/Double for money to avoid rounding drift.
 * Amounts are written with exactly [decimals] decimals, zeros included: with 2, 45,000 is "45,000.00".
 */
object Money {
    /** Stored units per currency unit. */
    const val SCALE = 100L

    /** Decimals the stored amounts hold, so the most [decimals] can be. */
    const val MAX_DECIMALS = 2

    /** A fresh install writes whole amounts, like before the setting existed. */
    const val DEFAULT_DECIMALS = 0

    const val DEFAULT_SYMBOL = "$"

    /**
     * The currency a fresh install starts with: the one of the device's region (₫ in Vietnam,
     * € in Germany), [DEFAULT_SYMBOL] when the region's currency isn't one the picker offers.
     */
    fun regionalSymbol(locale: Locale = Locale.getDefault()): String {
        val code = runCatching { java.util.Currency.getInstance(locale).currencyCode }.getOrNull()
            ?: if (locale.language == "vi") "VND" else null
        return RegionalSymbols[code] ?: DEFAULT_SYMBOL
    }

    /** Currency code to the symbol stored for it: the same symbols as the Setting tab's currency list. */
    private val RegionalSymbols = mapOf(
        "USD" to "$", "EUR" to "€", "JPY" to "¥", "GBP" to "£", "CNY" to "CN¥", "AUD" to "A$", "CAD" to "C$",
        "CHF" to "Fr", "HKD" to "HK$", "SGD" to "S$", "SEK" to "kr", "KRW" to "₩", "NOK" to "Nkr", "NZD" to "NZ$",
        "INR" to "₹", "MXN" to "Mex$", "TWD" to "NT$", "ZAR" to "R", "BRL" to "R$", "VND" to "₫",
    )

    /**
     * Currency symbol appended to every formatted amount. Snapshot state, so changing it
     * recomposes every screen showing an amount without threading it through their state.
     * Loaded from the `setting` table at startup; see SettingRepository.observeCurrency.
     */
    var symbol: String by mutableStateOf(DEFAULT_SYMBOL)

    /** Thousands/decimal separators; snapshot state like [symbol]. See SettingRepository.observeNumberStyle. */
    var style: NumberStyle by mutableStateOf(NumberStyle.DEFAULT)

    /**
     * Decimals every amount is written with (0 to [MAX_DECIMALS]), and the most an amount field
     * takes; snapshot state like [symbol]. Fewer than the stored two round half up: 4.50 with 0 is "5".
     * See SettingRepository.observeDecimals.
     */
    var decimals: Int by mutableStateOf(DEFAULT_DECIMALS)

    /** 123456789 -> "1,234,567.89$": the symbol always follows the number, with no space, whatever the currency. */
    fun format(amount: Long): String = "${groupThousands(amount)}$symbol"

    /** Masked amount for when the user has hidden a balance, e.g. "---$". */
    fun formatHidden(): String = "---$symbol"

    /** Same as [format] but with an explicit sign for positive amounts, e.g. "+50,000$". */
    fun formatSigned(amount: Long): String = "${formatSignedNoCurrency(amount)}$symbol"

    /** Same as [formatSigned] but without the currency symbol, e.g. "+50,000". */
    fun formatSignedNoCurrency(amount: Long): String {
        val sign = if (amount > 0) "+" else if (amount < 0) "-" else ""
        return sign + groupThousands(kotlin.math.abs(amount))
    }

    /** With 2 [decimals]: 123456789 -> "1,234,567.89", 4500000 -> "45,000.00" (no currency symbol). */
    fun groupThousands(amount: Long): String = write(amount, trimZeros = false)

    /**
     * An amount as an input field starts out: grouped, rounded to [decimals] but without trailing
     * zero decimals (so typing on goes on from the last digit), "" for 0.
     */
    fun inputText(amount: Long): String = if (amount == 0L) "" else write(amount, trimZeros = true)

    private fun write(amount: Long, trimZeros: Boolean): String {
        val digits = decimals.coerceIn(0, MAX_DECIMALS)
        val negative = amount < 0
        val abs = roundTo(if (amount == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(amount), digits)
        val sb = StringBuilder(if (negative && abs != 0L) "-" else "")
        sb.append(group((abs / SCALE).toString()))
        var fraction = (abs % SCALE).toString().padStart(MAX_DECIMALS, '0').take(digits)
        if (trimZeros) fraction = fraction.trimEnd('0')
        if (fraction.isNotEmpty()) sb.append(style.decimal).append(fraction)
        return sb.toString()
    }

    /** [abs] (hundredths, not negative) rounded half up to [digits] decimals. */
    private fun roundTo(abs: Long, digits: Int): Long {
        var unit = 1L
        repeat(MAX_DECIMALS - digits) { unit *= 10 }
        if (unit == 1L || abs > Long.MAX_VALUE - unit) return abs
        return (abs + unit / 2) / unit * unit
    }

    /**
     * What an amount field shows after the user typed [new] over [old] (both with the cursor at the
     * end): digits regrouped, at most one decimal separator and [decimals] decimals (none with 0: the
     * separator is dropped). A '.' or ',' typed at
     * the end starts the decimals whatever the style, so it works with any keyboard; the grouping
     * separators are always ours. [allowNegative] lets a leading '-' through (account balances).
     */
    fun cleanInput(old: String, new: String, allowNegative: Boolean = false): String {
        val sign = if (allowNegative && new.trimStart().startsWith("-")) "-" else ""
        val body = new.filter { it.isDigit() || it == '.' || it == ',' }
        val decimalAt = when {
            style.decimal in old -> body.lastIndexOf(style.decimal)
            body.endsWith('.') || body.endsWith(',') -> body.length - 1
            else -> -1
        }
        val whole = (if (decimalAt < 0) body else body.substring(0, decimalAt)).filter(Char::isDigit).trimStart('0')
        if (decimalAt < 0 || decimals <= 0) return sign + group(whole)
        val fraction = body.substring(decimalAt + 1).filter(Char::isDigit)
        if (fraction.length > decimals.coerceAtMost(MAX_DECIMALS)) return old
        return sign + group(whole).ifEmpty { "0" } + style.decimal + fraction
    }

    /** The amount an input field's text (see [cleanInput]) stands for; 0 when empty. */
    fun parseInput(text: String): Long {
        val negative = text.trimStart().startsWith("-")
        val body = text.filter { it.isDigit() || it == style.decimal }
        val decimalAt = body.indexOf(style.decimal)
        val whole = (if (decimalAt < 0) body else body.substring(0, decimalAt)).ifEmpty { "0" }
        val fraction = if (decimalAt < 0) "" else body.substring(decimalAt + 1).filter(Char::isDigit)
        val value = (whole.toBigInteger() * SCALE.toBigInteger() +
            fraction.take(MAX_DECIMALS).padEnd(MAX_DECIMALS, '0').toBigInteger())
            .min(Long.MAX_VALUE.toBigInteger()).toLong()
        return if (negative) -value else value
    }

    private fun group(digits: String): String {
        val sb = StringBuilder()
        for ((index, ch) in digits.reversed().withIndex()) {
            if (index != 0 && index % 3 == 0) sb.append(style.grouping)
            sb.append(ch)
        }
        return sb.reverse().toString()
    }
}
