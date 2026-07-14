package jp.local.kakeibo.summary

import jp.local.kakeibo.data.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SummaryCalculatorTest {
    @Test
    fun `week summary covers Monday through Sunday and keeps zero amount days`() {
        val expenses =
            listOf(
                expense("2026-07-13", 1200),
                expense("2026-07-19", 800),
                expense("2026-07-20", 5000),
            )

        val result = SummaryCalculator.calculate(SummaryPeriod.WEEK, LocalDate.parse("2026-07-15"), expenses)

        assertEquals(LocalDate.parse("2026-07-13"), result.from)
        assertEquals(LocalDate.parse("2026-07-19"), result.to)
        assertEquals(7, result.buckets.size)
        assertEquals(2000, result.totalAmount)
        assertEquals(0, result.buckets[1].amount)
    }

    @Test
    fun `month summary excludes deleted and out of range expenses`() {
        val expenses =
            listOf(
                expense("2026-02-01", 100),
                expense("2026-02-28", 200),
                expense("2026-02-14", 300, deletedAt = "2026-02-15T00:00:00Z"),
                expense("2026-03-01", 400),
            )

        val result = SummaryCalculator.calculate(SummaryPeriod.MONTH, LocalDate.parse("2026-02-10"), expenses)

        assertEquals(28, result.buckets.size)
        assertEquals(300, result.totalAmount)
        assertEquals(2, result.expenseCount)
    }

    @Test
    fun `year summary groups expenses into twelve months`() {
        val expenses =
            listOf(
                expense("2026-01-31", 1000),
                expense("2026-07-14", 2500),
                expense("2026-07-15", 500),
            )

        val result = SummaryCalculator.calculate(SummaryPeriod.YEAR, LocalDate.parse("2026-07-14"), expenses)

        assertEquals(12, result.buckets.size)
        assertEquals(1000, result.buckets[0].amount)
        assertEquals(3000, result.buckets[6].amount)
        assertEquals(4000, result.totalAmount)
    }

    @Test
    fun `day summary returns zero totals when no expenses exist`() {
        val result = SummaryCalculator.calculate(SummaryPeriod.DAY, LocalDate.parse("2026-07-14"), emptyList())

        assertEquals(0, result.totalAmount)
        assertEquals(0, result.expenseCount)
        assertEquals(1, result.buckets.size)
    }

    private fun expense(
        date: String,
        amount: Long,
        deletedAt: String? = null,
    ) = ExpenseEntity(
        uuid = "$date-$amount",
        date = date,
        amount = amount,
        categoryUuid = "10000000-0000-4000-8000-000000000001",
        memo = "test",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        deletedAt = deletedAt,
    )
}
