package app.outgo.ui.trade

import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NegativeBalanceTest {

    private fun account(id: Long, balance: Long) =
        AccountEntity(id = id, parentId = 99, name = "a$id", iconId = null, balance = balance, createdAt = 0, updatedAt = 0)

    private fun trade(type: Int, amount: Long, from: Long, to: Long? = null) = TradeEntity(
        id = 1, type = type, amount = amount, accountId = from, categoryId = null, toAccountId = to,
        occurredAt = 0, monthKey = 202610, createdAt = 0, updatedAt = 0,
    )

    private val accounts = listOf(account(1, 100), account(2, 50), account(3, -20))

    private fun state(type: Int, amount: Long, from: Long, to: Long? = null, original: TradeEntity? = null) = TradeUiState(
        type = type, amount = amount, selectedAccountId = from, selectedToAccountId = to, accounts = accounts, original = original,
    )

    @Test
    fun newTrades() {
        assertNull(state(TradeType.EXPENSE, 100, 1).overdrawnBySave)
        assertEquals(1L, state(TradeType.EXPENSE, 101, 1).overdrawnBySave?.id)
        assertEquals(2L, state(TradeType.TRANSFER, 60, 2, to = 1).overdrawnBySave?.id)
        // An account already below 0 may still gain, but not lose.
        assertNull(state(TradeType.INCOME, 5, 3).overdrawnBySave)
        assertEquals(3L, state(TradeType.EXPENSE, 1, 3).overdrawnBySave?.id)
    }

    @Test
    fun edits() {
        // The balance already holds the old expense of 80: raising it to 180 leaves 0.
        assertNull(state(TradeType.EXPENSE, 180, 1, original = trade(TradeType.EXPENSE, 80, 1)).overdrawnBySave)
        assertEquals(1L, state(TradeType.EXPENSE, 181, 1, original = trade(TradeType.EXPENSE, 80, 1)).overdrawnBySave?.id)
        // Moving a transfer's "To" away from account 2 takes back what it brought in.
        assertEquals(2L, state(TradeType.TRANSFER, 60, 1, to = 3, original = trade(TradeType.TRANSFER, 60, 1, to = 2)).overdrawnBySave?.id)
    }

    @Test
    fun deletes() {
        assertEquals(2L, state(TradeType.INCOME, 60, 2, original = trade(TradeType.INCOME, 60, 2)).overdrawnByDelete?.id)
        assertNull(state(TradeType.EXPENSE, 60, 2, original = trade(TradeType.EXPENSE, 60, 2)).overdrawnByDelete)
    }
}
