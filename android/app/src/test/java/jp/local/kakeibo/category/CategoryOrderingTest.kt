package jp.local.kakeibo.category

import jp.local.kakeibo.data.CategoryEntity
import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOrderingTest {
    @Test
    fun `categories are ordered by active expense count descending`() {
        val categories =
            listOf(
                category("daily", "日用品"),
                category("food", "食費"),
                category("travel", "交通費"),
            )
        val expenses =
            listOf(
                expense("1", "food"),
                expense("2", "travel"),
                expense("3", "food"),
                expense("4", "food", deletedAt = "2026-07-15T00:00:00Z"),
            )

        val result = categories.orderedByExpenseFrequency(expenses)

        assertEquals(listOf("food", "travel", "daily"), result.map { it.uuid })
    }

    @Test
    fun `original order is retained when frequencies are equal`() {
        val categories =
            listOf(
                category("daily", "日用品"),
                category("food", "食費"),
                category("travel", "交通費"),
            )
        val expenses = listOf(expense("1", "food"), expense("2", "daily"))

        val result = categories.orderedByExpenseFrequency(expenses)

        assertEquals(listOf("daily", "food", "travel"), result.map { it.uuid })
    }

    @Test
    fun `categories are filtered by transaction type`() {
        val categories =
            listOf(
                category("food", "食費"),
                category("salary", "給与", TransactionType.INCOME),
            )

        assertEquals(
            listOf("food"),
            categories.forTransactionType(TransactionType.EXPENSE).map { it.uuid },
        )
        assertEquals(
            listOf("salary"),
            categories.forTransactionType(TransactionType.INCOME).map { it.uuid },
        )
    }

    private fun category(
        uuid: String,
        name: String,
        type: String = TransactionType.EXPENSE,
    ) = CategoryEntity(
        uuid = uuid,
        name = name,
        type = type,
        createdAt = "2026-07-15T00:00:00Z",
        updatedAt = "2026-07-15T00:00:00Z",
    )

    private fun expense(
        uuid: String,
        categoryUuid: String,
        deletedAt: String? = null,
    ) = ExpenseEntity(
        uuid = uuid,
        date = "2026-07-15",
        amount = 100,
        categoryUuid = categoryUuid,
        memo = "",
        createdAt = "2026-07-15T00:00:00Z",
        updatedAt = "2026-07-15T00:00:00Z",
        deletedAt = deletedAt,
    )
}
