package jp.local.kakeibo.summary

import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.GamblingRecordEntity
import jp.local.kakeibo.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CombinedSummaryCalculatorTest {
    @Test
    fun `month summary combines household and gambling cash flow`() {
        val result =
            CombinedSummaryCalculator.calculate(
                period = SummaryPeriod.MONTH,
                anchorDate = LocalDate.parse("2026-02-12"),
                expenses =
                    listOf(
                        expense("food", "2026-02-01", 1_200),
                        expense("salary", "2026-02-10", 50_000, TransactionType.INCOME),
                    ),
                gamblingRecords =
                    listOf(
                        gambling("win", "2026-02-11", stake = 2_000, payout = 3_500),
                        gambling("loss", "2026-02-12", stake = 800, payout = 0),
                    ),
            )

        assertEquals(LocalDate.parse("2026-02-01"), result.from)
        assertEquals(LocalDate.parse("2026-02-28"), result.to)
        assertEquals(50_000, result.household.totalIncome)
        assertEquals(1_200, result.household.totalExpense)
        assertEquals(2, result.household.recordCount)
        assertEquals(3_500, result.gambling.totalIncome)
        assertEquals(2_800, result.gambling.totalExpense)
        assertEquals(700, result.gambling.balance)
        assertEquals(2, result.gambling.recordCount)
        assertEquals(53_500, result.combined.totalIncome)
        assertEquals(4_000, result.combined.totalExpense)
        assertEquals(49_500, result.combined.balance)
        assertEquals(4, result.combined.recordCount)
    }

    @Test
    fun `year summary excludes deleted invalid and outside dates from both sources`() {
        val result =
            CombinedSummaryCalculator.calculate(
                period = SummaryPeriod.YEAR,
                anchorDate = LocalDate.parse("2026-07-01"),
                expenses =
                    listOf(
                        expense("active", "2026-01-01", 100),
                        expense("deleted", "2026-03-01", 1_000, deletedAt = "deleted"),
                        expense("invalid", "not-a-date", 2_000),
                        expense("outside", "2025-12-31", 3_000),
                    ),
                gamblingRecords =
                    listOf(
                        gambling("active", "2026-12-31", stake = 400, payout = 700),
                        gambling("deleted", "2026-06-01", stake = 4_000, payout = 7_000, deletedAt = "deleted"),
                        gambling("invalid", "2026-02-30", stake = 5_000, payout = 9_000),
                        gambling("outside", "2027-01-01", stake = 6_000, payout = 9_000),
                    ),
            )

        assertEquals(LocalDate.parse("2026-01-01"), result.from)
        assertEquals(LocalDate.parse("2026-12-31"), result.to)
        assertEquals(100, result.household.totalExpense)
        assertEquals(1, result.household.recordCount)
        assertEquals(700, result.gambling.totalIncome)
        assertEquals(400, result.gambling.totalExpense)
        assertEquals(1, result.gambling.recordCount)
        assertEquals(700, result.combined.totalIncome)
        assertEquals(500, result.combined.totalExpense)
        assertEquals(200, result.combined.balance)
        assertEquals(2, result.combined.recordCount)
    }

    private fun expense(
        uuid: String,
        date: String,
        amount: Long,
        type: String = TransactionType.EXPENSE,
        deletedAt: String? = null,
    ) =
        ExpenseEntity(
            uuid = uuid,
            date = date,
            amount = amount,
            type = type,
            categoryUuid = "category",
            memo = "",
            createdAt = "created",
            updatedAt = "updated",
            deletedAt = deletedAt,
        )

    private fun gambling(
        uuid: String,
        date: String,
        stake: Long,
        payout: Long,
        deletedAt: String? = null,
    ) =
        GamblingRecordEntity(
            uuid = uuid,
            date = date,
            stakeAmount = stake,
            payoutAmount = payout,
            gameType = "競馬",
            memo = "",
            createdAt = "created",
            updatedAt = "updated",
            deletedAt = deletedAt,
        )
}
