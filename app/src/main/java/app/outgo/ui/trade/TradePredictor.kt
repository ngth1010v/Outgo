package app.outgo.ui.trade

import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.TradeType
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A Trade screen field a prediction can fill. On a transfer, [CATEGORY] is the "From budget". */
enum class PredictField { AMOUNT, CATEGORY, ACCOUNT, TO_ACCOUNT, TO_BUDGET, EXEMPT, NOTE }

/**
 * Fields of a trade as ids, null where not filled. As the input: what the user filled in. As the
 * output: what [TradePredictor] guessed for the rest. [exempt] is the budget exception of an
 * expense, the saving exception otherwise.
 */
data class TradeFields(
    val amount: Long? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val toCategoryId: Long? = null,
    val exempt: Boolean? = null,
    val note: String? = null,
)

/**
 * [fields] predicts the empty fields; [noteSuffix] completes a note being typed (shown faded), and
 * [noteAccepted] is the whole note once taken: the half-typed last word gets its history spelling.
 */
data class TradePrediction(val fields: TradeFields = TradeFields(), val noteSuffix: String = "", val noteAccepted: String = "")

/**
 * Guesses a trade's empty fields from past trades: a weighted vote among the past trades of the
 * same type that agree with every field filled in. A trade's vote weighs more when it is recent,
 * near the amount typed, at a similar hour, and on the same kind of day (weekday or weekend).
 * Confidence is the winning value's weight over the total plus 1, so a guess backed by only one
 * or two trades stays low. With nothing filled in, the hour and kind of day alone pick the votes.
 * A note being typed is completed word by word, see [complete].
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
        val now = Instant.ofEpochMilli(at).atZone(zone)
        val nowHour = now.hour + now.minute / 60.0
        val nowWeekend = now.dayOfWeek.isWeekend()

        // (index in history, weight) of the trades that agree with the fields filled in.
        val base = history.mapIndexedNotNull { index, t ->
            if (t.type != type) return@mapIndexedNotNull null
            if (known.categoryId != null && t.categoryId != known.categoryId) return@mapIndexedNotNull null
            if (known.accountId != null && t.accountId != known.accountId) return@mapIndexedNotNull null
            if (known.toAccountId != null && t.toAccountId != known.toAccountId) return@mapIndexedNotNull null
            if (known.toCategoryId != null && t.toCategoryId != known.toCategoryId) return@mapIndexedNotNull null
            if (known.exempt != null && exemptOf(t) != known.exempt) return@mapIndexedNotNull null
            // Half the weight every 200 trades back.
            var w = 0.5.pow(index / 200.0)
            if (amount > 0) w *= (min(amount, t.amount).toDouble() / max(amount, t.amount).coerceAtLeast(1)).pow(3)
            val then = Instant.ofEpochMilli(t.occurredAt).atZone(zone)
            val dh = abs(nowHour - (then.hour + then.minute / 60.0)).let { min(it, 24 - it) }
            w *= 0.5 + 0.5 * exp(-dh * dh / 8.0)
            if (then.dayOfWeek.isWeekend() != nowWeekend) w *= 0.7
            index to w
        }
        val tokens = tokensOf(history)
        val typed = tokenize(typedNote)
        val partial = typedNote.isNotEmpty() && typedNote.last().isLetterOrDigit()
        // A typed note keeps the trades whose note matches it, whole or by its last segment.
        val votes = base.mapNotNull { (i, w) ->
            val f = if (typed.isEmpty()) 1.0 else noteMatch(tokens[i], typed, partial)
            if (f > 0) history[i] to w * f else null
        }
        val completion = if (typed.isEmpty()) null else complete(base.map { (i, w) -> tokens[i] to w }, typedNote, typed, partial, minConfidence)
        if (votes.isEmpty()) return TradePrediction(noteSuffix = completion?.first.orEmpty(), noteAccepted = completion?.second.orEmpty())

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
        return TradePrediction(
            fields = TradeFields(
                amount = if (amount <= 0) vote { t -> t.amount.takeIf { it > 0 } } else null,
                categoryId = if (known.categoryId == null) vote { it.categoryId } else null,
                accountId = if (known.accountId == null) vote { it.accountId } else null,
                toAccountId = if (transfer && known.toAccountId == null) vote { it.toAccountId } else null,
                toCategoryId = if (transfer && known.toCategoryId == null) vote { it.toCategoryId } else null,
                exempt = if (known.exempt == null) vote { exemptOf(it) }?.takeIf { it } else null,
                note = if (typedNote.isEmpty()) vote { t -> t.note?.takeIf { it.isNotBlank() } } else null,
            ),
            noteSuffix = completion?.first.orEmpty(),
            noteAccepted = completion?.second.orEmpty(),
        )
    }

    private fun exemptOf(t: TradeEntity) = if (t.type == TradeType.EXPENSE) t.budgetExempt else t.savingExempt

    private fun DayOfWeek.isWeekend() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

    /**
     * A word (a run of letters and digits) or one punctuation mark, which separates segments.
     * [key] is [text] folded for matching; [gap] is the whitespace before it in its note.
     */
    internal data class Tok(val key: String, val text: String, val gap: String) {
        val isSep: Boolean get() = !text[0].isLetterOrDigit()
    }

    private const val BOS = "\u0002"
    private const val EOS = "\u0003"
    private val END = Tok(EOS, "", "")
    private val MARKS = Regex("\\p{Mn}+")

    /** Lower case, no diacritics ("Tân Cảng" -> "tan cang"), always as long as [s]. */
    internal fun fold(s: String): String {
        val f = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").replace('đ', 'd').replace('Đ', 'D').lowercase()
        return if (f.length == s.length) f else s
    }

    internal fun tokenize(note: String): List<Tok> {
        val s = Normalizer.normalize(note, Normalizer.Form.NFC)
        val folded = fold(s)
        val out = ArrayList<Tok>()
        var i = 0
        var gapStart = 0
        while (i < s.length) {
            if (s[i].isWhitespace()) { i++; continue }
            val start = i
            if (s[i].isLetterOrDigit()) while (i < s.length && s[i].isLetterOrDigit()) i++ else i++
            out += Tok(folded.substring(start, i), s.substring(start, i), s.substring(gapStart, start))
            gapStart = i
        }
        return out
    }

    @Volatile private var cache: Pair<List<TradeEntity>, List<List<Tok>>>? = null

    /** The tokenized notes of [history], kept while the same list comes back (each keystroke). */
    private fun tokensOf(history: List<TradeEntity>): List<List<Tok>> {
        cache?.let { if (it.first === history) return it.second }
        return history.map { tokenize(it.note.orEmpty()) }.also { cache = history to it }
    }

    /** [t] is this typed word, or starts with it when it is still being typed. */
    private fun Tok.matches(t: Tok, half: Boolean) = if (half) t.key.startsWith(key) else t.key == key

    /** 1 when [note] starts with [typed], 0.5 when it holds [typed]'s last segment, else 0. */
    private fun noteMatch(note: List<Tok>, typed: List<Tok>, partial: Boolean): Double {
        fun at(start: Int, run: List<Tok>) = start + run.size <= note.size &&
            run.indices.all { j -> run[j].matches(note[start + j], partial && j == run.lastIndex) }
        if (at(0, typed)) return 1.0
        val seg = typed.subList(typed.indexOfLast { it.isSep } + 1, typed.size)
        return if (seg.isNotEmpty() && note.indices.any { at(it, seg) }) 0.5 else 0.0
    }

    /** Weight of a next word, and the weight of each spelling of it seen. */
    private class Vote(var weight: Double = 0.0, val shows: HashMap<Tok, Double> = HashMap()) {
        fun add(t: Tok, w: Double) {
            weight += w
            shows[t] = (shows[t] ?: 0.0) + w
        }
    }

    /**
     * Completes [typedNote] word by word, each word the confident winner of a weighted vote over
     * past [notes]. While some notes start with the text so far, they vote their next word or the
     * end, so a known note comes back whole. Once none does, the note is a new mix of known
     * pieces: the words that followed its last 3, 2 or 1 words anywhere vote, and a separator only
     * keeps the share of notes that go on to one more segment, the rest of its weight ending the
     * note. So with "An phú - DHQG" known, "Tân cảng - An" gets " phú", not " phú - DHQG".
     * Returns the faded suffix and the whole note once taken, or null.
     */
    private fun complete(notes: List<Pair<List<Tok>, Double>>, typedNote: String, typed: List<Tok>, partial: Boolean, minConfidence: Double): Pair<String, String>? {
        // ponytail: the n-gram table is rebuilt each keystroke, O(notes x words); cache it with the votes if typing lags.
        val grams = HashMap<String, HashMap<String, Vote>>()
        val segsAtLeast = HashMap<Int, Double>()
        for ((note, w) in notes) {
            if (note.isEmpty()) continue
            val keys = listOf(BOS) + note.map { it.key }
            for (i in 0..note.size) {
                val t = note.getOrElse(i) { END }
                if (t !== END) grams.getOrPut("") { HashMap() }.getOrPut(t.key) { Vote() }.add(t, w)
                for (k in 1..min(3, i + 1)) {
                    grams.getOrPut(keys.subList(i + 1 - k, i + 1).joinToString("\u0001")) { HashMap() }.getOrPut(t.key) { Vote() }.add(t, w)
                }
            }
            for (s in 0..note.count { it.isSep }) segsAtLeast[s] = (segsAtLeast[s] ?: 0.0) + w
        }

        val seq = typed.toMutableList()
        var half = if (partial) seq.removeAt(seq.lastIndex) else null
        var seps = seq.count { it.isSep }
        val suffix = StringBuilder()
        val nfc = Normalizer.normalize(typedNote, Normalizer.Form.NFC)
        val accepted = StringBuilder(if (half != null) nfc.dropLast(half.text.length) else nfc)
        repeat(8) {
            val h = half
            val votes = HashMap<String, Vote>()
            for ((note, w) in notes) {
                if (note.size < seq.size || seq.indices.any { note[it].key != seq[it].key }) continue
                val t = note.getOrElse(seq.size) { END }
                if (h != null && (t === END || !h.matches(t, true))) continue
                votes.getOrPut(t.key) { Vote() }.add(t, w)
            }
            if (votes.isEmpty()) {
                val keys = listOf(BOS) + seq.map { it.key }
                val contexts = (min(3, keys.size) downTo 1).map { keys.takeLast(it).joinToString("\u0001") } + listOfNotNull("".takeIf { h != null })
                for (ctx in contexts) {
                    grams[ctx]?.forEach { (key, v) -> if (h == null || (key != EOS && key.startsWith(h.key))) votes[key] = v }
                    if (votes.isNotEmpty()) break
                }
                val more = (segsAtLeast[seps + 1] ?: 0.0) / (segsAtLeast[seps] ?: 0.0).coerceAtLeast(1e-9)
                for ((key, v) in votes.toList()) if (key != EOS && !key[0].isLetterOrDigit()) {
                    votes[key] = Vote(v.weight * more, v.shows)
                    votes[EOS] = Vote((votes[EOS]?.weight ?: 0.0) + v.weight * (1 - more))
                }
            }
            val total = votes.values.sumOf { it.weight }
            val best = votes.maxByOrNull { it.value.weight } ?: return finish(suffix, accepted)
            if (best.key == EOS || best.value.weight / (total + 1) < minConfidence) return finish(suffix, accepted)
            val t = best.value.shows.maxBy { it.value }.key
            val shown = when {
                h != null -> t.text.substring(h.text.length)
                suffix.isEmpty() && accepted.lastOrNull()?.isWhitespace() == true -> t.text
                else -> t.gap + t.text
            }
            suffix.append(shown)
            accepted.append(if (h != null) t.text else shown)
            seq += t
            if (t.isSep) seps++
            half = null
        }
        return finish(suffix, accepted)
    }

    private fun finish(suffix: StringBuilder, accepted: StringBuilder) =
        if (suffix.isBlank()) null else suffix.toString() to accepted.toString()
}
