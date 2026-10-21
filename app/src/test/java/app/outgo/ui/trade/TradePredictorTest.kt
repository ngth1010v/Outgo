package app.outgo.ui.trade

import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class TradePredictorTest {
    private val zone = ZoneOffset.UTC
    private val hour = 3_600_000L

    // Monday 2026-10-19 08:00 UTC.
    private val monday8 = 1_792_396_800_000L

    private fun trade(category: Long, account: Long, amount: Long, note: String?, at: Long = monday8, type: Int = TradeType.EXPENSE) =
        TradeEntity(type = type, amount = amount, accountId = account, categoryId = category, occurredAt = at, monthKey = 202610, note = note, createdAt = at, updatedAt = at)

    private fun predict(history: List<TradeEntity>, amount: Long = 0, known: TradeFields = TradeFields(), note: String = "", min: Double = 0.6) =
        TradePredictor.predict(history, TradeType.EXPENSE, amount, known, note, monday8, min, zone)

    private val coffee = List(6) { trade(category = 1, account = 10, amount = 3_000, note = "Coffee") }
    private val rent = List(3) { trade(category = 2, account = 20, amount = 500_000, note = "Rent") }

    @Test
    fun nothingTyped_predictsNothing() {
        assertEquals(TradePrediction(), predict(coffee + rent))
    }

    @Test
    fun amountPicksTheMatchingTrades() {
        val p = predict(coffee + rent, amount = 3_000)
        assertEquals(1L, p.fields.categoryId)
        assertEquals(10L, p.fields.accountId)
        assertEquals("Coffee", p.fields.note)
    }

    @Test
    fun knownFieldFiltersAndIsNotPredicted() {
        val p = predict(coffee + rent, known = TradeFields(categoryId = 2))
        assertNull(p.fields.categoryId)
        assertEquals(20L, p.fields.accountId)
    }

    @Test
    fun typedNoteIsCompleted() {
        val p = predict(coffee + rent, note = "co")
        assertEquals("ffee", p.noteSuffix)
        assertNull(p.fields.note)
        assertEquals(1L, p.fields.categoryId)
    }

    @Test
    fun fewOrSplitVotesStayBelowTheLimit() {
        // One trade alone: 1 / (1 + 1) = 0.5.
        assertNull(predict(coffee.take(1), amount = 3_000).fields.categoryId)
        // An even split between two accounts.
        val split = List(4) { trade(1, 10, 3_000, null) } + List(4) { trade(1, 11, 3_000, null) }
        assertNull(predict(split, amount = 3_000).fields.accountId)
        assertEquals(1L, predict(split, amount = 3_000).fields.categoryId)
    }

    @Test
    fun timeOfDayBreaksTies() {
        val morning = List(3) { trade(1, 10, 3_000, null) }
        val evening = List(3) { trade(3, 10, 3_000, null, at = monday8 + 12 * hour) }
        assertEquals(1L, predict(evening + morning, amount = 3_000, min = 0.0).fields.categoryId)
    }
}
