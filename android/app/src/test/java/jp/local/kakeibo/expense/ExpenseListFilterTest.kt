package jp.local.kakeibo.expense

import jp.local.kakeibo.data.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpenseListFilterTest {
    @Test
    fun selectedMonthOnlyIsShown() {
        val result =
            listOf(
                expense("july", "2026-07-31", "food", 1_200),
                expense("august", "2026-08-01", "food", 800),
            ).monthlyExpenses(YearMonth.of(2026, 7))

        assertEquals(listOf("july"), result.map { it.uuid })
        assertEquals(1_200, result.sumOf { it.amount })
    }

    @Test
    fun selectedCategoryIsAppliedWithinMonth() {
        val result =
            listOf(
                expense("food", "2026-07-10", "food", 1_000),
                expense("travel", "2026-07-11", "travel", 2_000),
                expense("old-food", "2026-06-30", "food", 3_000),
            ).monthlyExpenses(YearMonth.of(2026, 7), "food")

        assertEquals(listOf("food"), result.map { it.uuid })
    }

    @Test
    fun monthWithoutExpensesIsEmpty() {
        val result =
            listOf(expense("july", "2026-07-01", "food", 500))
                .monthlyExpenses(YearMonth.of(2026, 8))

        assertEquals(emptyList<ExpenseEntity>(), result)
    }

    @Test
    fun selectedMonthBecomesNewExpenseDate() {
        val today = LocalDate.of(2026, 7, 31)

        assertEquals(today, YearMonth.of(2026, 7).defaultExpenseDate(today))
        assertEquals(
            LocalDate.of(2026, 2, 28),
            YearMonth.of(2026, 2).defaultExpenseDate(today),
        )
    }

    private fun expense(
        uuid: String,
        date: String,
        categoryUuid: String,
        amount: Long,
    ) = ExpenseEntity(
        uuid = uuid,
        date = date,
        amount = amount,
        categoryUuid = categoryUuid,
        memo = "",
        createdAt = "2026-07-01T00:00:00Z",
        updatedAt = "2026-07-01T00:00:00Z",
    )
}
