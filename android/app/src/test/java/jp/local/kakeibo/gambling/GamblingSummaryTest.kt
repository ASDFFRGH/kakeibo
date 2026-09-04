package jp.local.kakeibo.gambling

import jp.local.kakeibo.data.GamblingRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GamblingSummaryTest {
    @Test
    fun `totals use payout minus stake and ignore deleted records`() {
        val records =
            listOf(
                record(uuid = "win", stake = 1_000, payout = 1_500),
                record(uuid = "loss", stake = 2_000, payout = 0),
                record(uuid = "deleted", stake = 5_000, payout = 10_000, deletedAt = "deleted"),
            )

        val totals = calculateGamblingTotals(records)

        assertEquals(3_000, totals.totalStake)
        assertEquals(1_500, totals.totalPayout)
        assertEquals(-1_500, totals.balance)
    }

    @Test
    fun `empty records have zero totals`() {
        assertEquals(
            GamblingTotals(totalStake = 0, totalPayout = 0, balance = 0),
            calculateGamblingTotals(emptyList()),
        )
    }

    private fun record(
        uuid: String,
        stake: Long,
        payout: Long,
        deletedAt: String? = null,
    ) = GamblingRecordEntity(
        uuid = uuid,
        date = "2026-09-04",
        stakeAmount = stake,
        payoutAmount = payout,
        gameType = "競馬",
        memo = "",
        createdAt = "created",
        updatedAt = "updated",
        deletedAt = deletedAt,
    )
}
