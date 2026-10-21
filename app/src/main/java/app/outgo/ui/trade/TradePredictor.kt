package app.outgo.ui.trade

import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.TradeType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A Trade screen field a prediction can fill. On a transfer, [CATEGORY] is the "From budget". */
enum class PredictField { CATEGORY, ACCOUNT, TO_ACCOUNT, TO_BUDGET, EXEMPT, NOTE }

/**
 * Fields of a trade as ids, null where not filled. As the input: what the user filled in. As the
 * output: what [TradePredictor] guessed for the rest. [exempt] is the budget exception of an
 * expense, the saving exception otherwise.
 */
data class TradeFields(
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val toCategoryId: Long? = null,
    val exempt: Boolean? = null,
    val note: String? = null,
)

/** [fields] predicts the empty fields; [noteSuffix] completes a note being typed. */
data class TradePrediction(val fields: TradeFields = TradeFields(), val noteSuffix: String = "")

/**
 * Guesses a trade's empty fields from past trades: a weighted vote among the past trades of the
 * same type that agree with every field filled in. A trade's vote weighs more when it is recent,
 * near the amount typed, at a similar hour, and on the same kind of day (weekday or weekend).
 * Confidence is the winning value's weight over the total plus 1, so a guess backed by only one
 * or two trades stays low.
 */
object TradePredictor {

    fun predict(
        history: List<TradeEntity>,
        type: Int,
        amount: Long,
        known: TradeFields,
        typedNote: String,
        at: Long,
        minConfidence: Double,
        zone: ZoneId = ZoneId.systemDefault(),
    ): TradePrediction {
        val hasInput = amount > 0 || typedNote.isNotEmpty() || known != TradeFields()
        if (!hasInput) return TradePrediction()
        val now = Instant.ofEpochMilli(at).atZone(zone)
        val nowHour = now.hour + now.minute / 60.0
        val nowWeekend = now.dayOfWeek.isWeekend()

        val votes = history.mapIndexedNotNull { index, t ->
            if (t.type != type) return@mapIndexedNotNull null
            if (known.categoryId != null && t.categoryId != known.categoryId) return@mapIndexedNotNull null
            if (known.accountId != null && t.accountId != known.accountId) return@mapIndexedNotNull null
            if (known.toAccountId != null && t.toAccountId != known.toAccountId) return@mapIndexedNotNull null
            if (known.toCategoryId != null && t.toCategoryId != known.toCategoryId) return@mapIndexedNotNull null
            if (known.exempt != null && exemptOf(t) != known.exempt) return@mapIndexedNotNull null
            if (typedNote.isNotEmpty() && t.note?.startsWith(typedNote, ignoreCase = true) != true) return@mapIndexedNotNull null
            // Half the weight every 200 trades back.
            var w = 0.5.pow(index / 200.0)
            if (amount > 0) w *= (min(amount, t.amount).toDouble() / max(amount, t.amount).coerceAtLeast(1)).pow(3)
            val then = Instant.ofEpochMilli(t.occurredAt).atZone(zone)
            val dh = abs(nowHour - (then.hour + then.minute / 60.0)).let { min(it, 24 - it) }
            w *= 0.5 + 0.5 * exp(-dh * dh / 8.0)
            if (then.dayOfWeek.isWeekend() != nowWeekend) w *= 0.7
            t to w
        }
        if (votes.isEmpty()) return TradePrediction()

        fun <T> vote(value: (TradeEntity) -> T?): T? {
            val sums = HashMap<T?, Double>()
            var total = 0.0
            for ((t, w) in votes) {
                val v = value(t)
                sums[v] = (sums[v] ?: 0.0) + w
                total += w
            }
            val best = sums.maxBy { it.value }
            return best.key.takeIf { best.value / (total + 1) >= minConfidence }
        }

        val transfer = type == TradeType.TRANSFER
        val note = vote { t -> t.note?.takeIf { it.isNotBlank() } }
        return TradePrediction(
            fields = TradeFields(
                categoryId = if (known.categoryId == null) vote { it.categoryId } else null,
                accountId = if (known.accountId == null) vote { it.accountId } else null,
                toAccountId = if (transfer && known.toAccountId == null) vote { it.toAccountId } else null,
                toCategoryId = if (transfer && known.toCategoryId == null) vote { it.toCategoryId } else null,
                exempt = if (known.exempt == null) vote { exemptOf(it) }?.takeIf { it } else null,
                note = note.takeIf { typedNote.isEmpty() },
            ),
            noteSuffix = if (typedNote.isNotEmpty() && note != null && note.length > typedNote.length) note.substring(typedNote.length) else "",
        )
    }

    private fun exemptOf(t: TradeEntity) = if (t.type == TradeType.EXPENSE) t.budgetExempt else t.savingExempt

    private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY
}
