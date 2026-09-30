package app.outgo.data.repo

import app.outgo.data.db.dao.BudgetMonthSpend
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.domain.BudgetOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetCarryTest {

    private fun budget(
        categoryId: Long,
        limit: Long,
        over: Long? = null,
        under: Long? = null,
        from: Int? = 202607,
        fromAccount: Long? = null,
        toAccount: Long? = null,
    ) =
        BudgetWithProgress(
            id = categoryId, kind = 0, name = null, iconId = null, categoryId = categoryId, limitAmount = limit,
            sortOrder = 0, spent = 0, prevSpent = 0, categoryName = null, categoryIconId = null, categoryColor = null,
            overTarget = over, underTarget = under, carryFrom = from,
            underFromAccount = fromAccount, underToAccount = toAccount, settledMonth = null, carry = 0,
        )

    private fun spend(categoryId: Long, month: Int, spent: Long) = BudgetMonthSpend(categoryId, month, spent)

    @Test
    fun overspendShrinksThisBudget() {
        val carry = budgetCarry(listOf(budget(1, 100, over = BudgetOffset.SELF)), listOf(spend(1, 202608, 150)), 202609)
        assertEquals(mapOf(1L to -50L), carry)
    }

    @Test
    fun offsetAbsorbedByNextMonthIsNotCountedTwice() {
        // Jul over by 50 -> Aug limit 50, Aug spends exactly 50 -> nothing left for Sep.
        val b = budget(1, 100, over = BudgetOffset.SELF, under = BudgetOffset.SELF)
        val carry = budgetCarry(listOf(b), listOf(spend(1, 202607, 150), spend(1, 202608, 50)), 202609)
        assertEquals(emptyMap<Long, Long>(), carry)
    }

    @Test
    fun underspendGoesToOtherBudgetAcrossYearEnd() {
        val budgets = listOf(budget(1, 100, under = 2, from = 202612), budget(2, 300))
        val carry = budgetCarry(budgets, listOf(spend(1, 202612, 30)), 202701)
        assertEquals(mapOf(2L to 70L), carry)
    }

    @Test
    fun noActionMissingTargetOrBeforeCarryFromDropsOffset() {
        val budgets = listOf(budget(1, 100), budget(2, 100, over = 9), budget(3, 100, over = BudgetOffset.SELF, from = 202609))
        val spend = listOf(spend(1, 202608, 500), spend(2, 202608, 500), spend(3, 202608, 500))
        assertEquals(emptyMap<Long, Long>(), budgetCarry(budgets, spend, 202609))
    }

    @Test
    fun accountOffsetReportsEachUnderMonthAndCarriesNothing() {
        // Over in Jul goes to its own limit; Aug's lower limit then leaves 30 for the account.
        val b = budget(1, 100, over = BudgetOffset.SELF, fromAccount = 5, toAccount = 6)
        val moves = ArrayList<Pair<Int, Long>>()
        val carry = budgetCarry(listOf(b), listOf(spend(1, 202607, 120), spend(1, 202608, 50)), 202609) { _, month, left ->
            moves += month to left
        }
        assertEquals(listOf(202608 to 30L), moves)
        assertEquals(emptyMap<Long, Long>(), carry)
    }
}
