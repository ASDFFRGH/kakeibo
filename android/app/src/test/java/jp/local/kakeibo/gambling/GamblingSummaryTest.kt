package jp.local.kakeibo.gambling

import jp.local.kakeibo.data.GamblingRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

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

    @Test
    fun `game type summaries filter to month and group active records`() {
        val records =
            listOf(
                record(uuid = "horse", date = "2026-09-04", stake = 1_000, payout = 1_500, gameType = "競馬"),
                record(uuid = "slot", date = "2026-09-05", stake = 2_000, payout = 0, gameType = "スロット"),
                record(uuid = "august", date = "2026-08-31", stake = 10_000, payout = 20_000, gameType = "競馬"),
                record(uuid = "deleted", date = "2026-09-06", stake = 9_000, payout = 0, gameType = "競馬", deletedAt = "deleted"),
            )

        val summaries = calculateGamblingGameTypeSummaries(records, YearMonth.of(2026, 9))

        assertEquals(listOf("スロット", "競馬"), summaries.map { it.gameType })
        assertEquals(2_000, summaries[0].totals.totalStake)
        assertEquals(-2_000, summaries[0].totals.balance)
        assertEquals(1_000, summaries[1].totals.totalStake)
        assertEquals(1_500, summaries[1].totals.totalPayout)
    }

    @Test
    fun `month summaries are newest first and exclude deleted and invalid dates`() {
        val records =
            listOf(
                record(uuid = "sep", date = "2026-09-04", stake = 1_000, payout = 1_500),
                record(uuid = "aug", date = "2026-08-31", stake = 2_000, payout = 0),
                record(uuid = "invalid", date = "not-a-date", stake = 9_000, payout = 0),
                record(uuid = "deleted", date = "2026-10-01", stake = 9_000, payout = 0, deletedAt = "deleted"),
            )

        val summaries = calculateGamblingMonthSummaries(records)

        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8)), summaries.map { it.month })
        assertEquals(1_500, summaries.first().totals.totalPayout)
        assertEquals(2_000, summaries.last().totals.totalStake)
    }

    private fun record(
        uuid: String,
        date: String = "2026-09-04",
        stake: Long,
        payout: Long,
        gameType: String = "競馬",
        deletedAt: String? = null,
    ) = GamblingRecordEntity(
        uuid = uuid,
        date = date,
        stakeAmount = stake,
        payoutAmount = payout,
        gameType = gameType,
        memo = "",
        createdAt = "created",
        updatedAt = "updated",
        deletedAt = deletedAt,
    )
}
