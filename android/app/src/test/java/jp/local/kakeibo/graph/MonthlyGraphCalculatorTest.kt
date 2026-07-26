package jp.local.kakeibo.graph

import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyGraphCalculatorTest {
    @Test
    fun `calculate fills all months and separates income expense and balance`() {
        val result =
            MonthlyGraphCalculator.calculate(
                year = 2026,
                expenses =
                    listOf(
                        expense("2026-01-10", 1_000, TransactionType.EXPENSE, "food"),
                        expense("2026-01-20", 5_000, TransactionType.INCOME, "salary"),
                        expense("2026-03-01", 700, TransactionType.EXPENSE, "food"),
                    ),
            )

        assertEquals(12, result.size)
        assertEquals(1_000, result[0].expense)
        assertEquals(5_000, result[0].income)
        assertEquals(4_000, result[0].balance)
        assertEquals(0, result[1].expense)
        assertEquals(700, result[2].expense)
    }

    @Test
    fun `calculate filters category year deleted and invalid transactions`() {
        val result =
            MonthlyGraphCalculator.calculate(
                year = 2026,
                categoryUuid = "food",
                expenses =
                    listOf(
                        expense("2026-07-01", 1_000, TransactionType.EXPENSE, "food"),
                        expense("2026-07-02", 2_000, TransactionType.EXPENSE, "rent"),
                        expense("2025-07-03", 3_000, TransactionType.EXPENSE, "food"),
                        expense("invalid", 4_000, TransactionType.EXPENSE, "food"),
                        expense(
                            "2026-07-05",
                            5_000,
                            TransactionType.EXPENSE,
                            "food",
                            deletedAt = "2026-07-06T00:00:00Z",
                        ),
                    ),
            )

        assertEquals(1_000, result[6].expense)
        assertEquals(1_000, result.sumOf { it.expense })
    }

    private fun expense(
        date: String,
        amount: Long,
        type: String,
        categoryUuid: String,
        deletedAt: String? = null,
    ) = ExpenseEntity(
        uuid = "$date-$amount-$categoryUuid",
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
