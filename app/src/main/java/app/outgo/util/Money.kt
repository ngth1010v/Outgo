package app.outgo.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
 * All money in Outgo is a [Long] in hundredths of the currency unit (schema 21+), so 4.50 $ is
 * 450 and 45,000 ₫ is 4,500,000. Never use Float/Double for money to avoid rounding drift.
 * The decimals are only written when they are not zero: 45,000 stays "45,000".
 */
object Money {
    /** Stored units per currency unit. */
    const val SCALE = 100L

    /** Most digits typed after the decimal separator. */
    private const val FRACTION_DIGITS = 2

    const val DEFAULT_SYMBOL = "$"

    /**
     * Currency symbol appended to every formatted amount. Snapshot state, so changing it
     * recomposes every screen showing an amount without threading it through their state.
     * Loaded from the `setting` table at startup; see SettingRepository.observeCurrency.
     */
    var symbol: String by mutableStateOf(DEFAULT_SYMBOL)

    /** Thousands/decimal separators; snapshot state like [symbol]. See SettingRepository.observeNumberStyle. */
    var style: NumberStyle by mutableStateOf(NumberStyle.DEFAULT)

    /** 123456789 -> "1,234,567.89 $" */
    fun format(amount: Long): String = "${groupThousands(amount)} $symbol"

    /** Masked amount for when the user has hidden a balance, e.g. "--- $". */
    fun formatHidden(): String = "--- $symbol"

    /** Same as [format] but with an explicit sign for positive amounts, e.g. "+50,000 $". */
    fun formatSigned(amount: Long): String = "${formatSignedNoCurrency(amount)} $symbol"

    /** Same as [formatSigned] but without the currency symbol, e.g. "+50,000". */
    fun formatSignedNoCurrency(amount: Long): String {
        val sign = if (amount > 0) "+" else if (amount < 0) "-" else ""
        return sign + groupThousands(kotlin.math.abs(amount))
    }

    /** 123456789 -> "1,234,567.89", 4500000 -> "45,000" (no currency symbol). */
    fun groupThousands(amount: Long): String {
        val negative = amount < 0
        val abs = if (amount == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(amount)
        val whole = abs / SCALE
        val fraction = abs % SCALE
        val sb = StringBuilder(if (negative) "-" else "")
        sb.append(group(whole.toString()))
        if (fraction != 0L) sb.append(style.decimal).append(fraction.toString().padStart(FRACTION_DIGITS, '0'))
        return sb.toString()
    }

    /** An amount as an input field starts out: grouped, decimals only when set, "" for 0. */
    fun inputText(amount: Long): String = if (amount == 0L) "" else groupThousands(amount)

    /**
     * What an amount field shows after the user typed [new] over [old] (both with the cursor at the
     * end): digits regrouped, at most one decimal separator and two decimals. A '.' or ',' typed at
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
        if (decimalAt < 0) return sign + group(whole)
        val fraction = body.substring(decimalAt + 1).filter(Char::isDigit)
        if (fraction.length > FRACTION_DIGITS) return old
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
            fraction.take(FRACTION_DIGITS).padEnd(FRACTION_DIGITS, '0').toBigInteger())
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
