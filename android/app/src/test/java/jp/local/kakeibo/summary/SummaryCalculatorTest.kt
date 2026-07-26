package jp.local.kakeibo.summary

import jp.local.kakeibo.data.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SummaryCalculatorTest {
    @Test
    fun `month summary excludes deleted and out of range transactions`() {
        val expenses =
            listOf(
                expense("2026-02-01", 100, categoryUuid = "food"),
                expense("2026-02-28", 200, categoryUuid = "food"),
                expense("2026-02-10", 5000, type = jp.local.kakeibo.data.TransactionType.INCOME, categoryUuid = "salary"),
                expense("2026-02-14", 300, deletedAt = "2026-02-15T00:00:00Z"),
                expense("2026-03-01", 400),
            )

        val result = SummaryCalculator.calculate(SummaryPeriod.MONTH, LocalDate.parse("2026-02-10"), expenses)

        assertEquals(300, result.totalExpense)
        assertEquals(5000, result.totalIncome)
        assertEquals(2, result.expenseCount)
        assertEquals(2, result.categories.size)
        assertEquals(300, result.categories.first { it.categoryUuid == "food" }.expenseAmount)
        assertEquals(5000, result.categories.first { it.categoryUuid == "salary" }.incomeAmount)
    }

    @Test
    fun `year summary groups transactions by category`() {
        val expenses =
            listOf(
                expense("2026-01-31", 1000, categoryUuid = "food"),
                expense("2026-07-14", 2500, categoryUuid = "rent"),
                expense("2026-07-15", 500, categoryUuid = "food"),
                expense("2025-12-31", 9000, categoryUuid = "food"),
            )

        val result = SummaryCalculator.calculate(SummaryPeriod.YEAR, LocalDate.parse("2026-07-14"), expenses)

        assertEquals(4000, result.totalExpense)
        assertEquals(2, result.categories.size)
        assertEquals(1500, result.categories.first { it.categoryUuid == "food" }.expenseAmount)
        assertEquals(2500, result.categories.first { it.categoryUuid == "rent" }.expenseAmount)
    }

    @Test
    fun `summary separates income expense and balance`() {
        val transactions =
            listOf(
                expense("2026-07-14", 1000),
                expense("2026-07-14", 5000, type = jp.local.kakeibo.data.TransactionType.INCOME),
            )

        val result = SummaryCalculator.calculate(SummaryPeriod.MONTH, LocalDate.parse("2026-07-14"), transactions)

        assertEquals(1000, result.totalExpense)
        assertEquals(5000, result.totalIncome)
        assertEquals(4000, result.balance)
        assertEquals(1, result.expenseCount)
        assertEquals(1, result.incomeCount)
        assertEquals(4000, result.categories.single().balance)
    }

    private fun expense(
        date: String,
        amount: Long,
        deletedAt: String? = null,
        type: String = jp.local.kakeibo.data.TransactionType.EXPENSE,
        categoryUuid: String = "10000000-0000-4000-8000-000000000001",
    ) = ExpenseEntity(
        uuid = "$date-$amount",
        date = date,
        amount = amount,
        type = type,
        categoryUuid = categoryUuid,
        memo = "test",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        deletedAt = deletedAt,
    )
}
