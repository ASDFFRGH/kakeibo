package jp.local.kakeibo.summary

import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.GamblingRecordEntity
import jp.local.kakeibo.data.TransactionType
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Amounts and the number of source records included in one part of a combined summary. */
data class CombinedTotals(
    val totalIncome: Long,
    val totalExpense: Long,
    val balance: Long,
    val recordCount: Int,
)

data class CombinedSummaryResult(
    val period: SummaryPeriod,
    val anchorDate: LocalDate,
    val from: LocalDate,
    val to: LocalDate,
    val household: CombinedTotals,
    val gambling: CombinedTotals,
    val combined: CombinedTotals,
)

/**
 * Calculates household and gambling cash flow over the same month or year.
 *
 * A gambling record can contribute to both sides: its stake is an expense and
 * its payout is income. [CombinedTotals.recordCount] counts source
 * records, so one gambling record is counted once even when it has both amounts.
 */
object CombinedSummaryCalculator {
    fun calculate(
        period: SummaryPeriod,
        anchorDate: LocalDate,
        expenses: Iterable<ExpenseEntity>,
        gamblingRecords: Iterable<GamblingRecordEntity>,
    ): CombinedSummaryResult {
        val (from, to) = bounds(period, anchorDate)
        val household = householdMetrics(expenses, from, to)
        val gambling = gamblingMetrics(gamblingRecords, from, to)
        val combined =
            CombinedTotals(
                totalIncome = household.totalIncome + gambling.totalIncome,
                totalExpense = household.totalExpense + gambling.totalExpense,
                balance = household.balance + gambling.balance,
                recordCount = household.recordCount + gambling.recordCount,
            )

        return CombinedSummaryResult(
            period = period,
            anchorDate = anchorDate,
            from = from,
            to = to,
            household = household,
            gambling = gambling,
            combined = combined,
        )
    }

    private fun householdMetrics(
        expenses: Iterable<ExpenseEntity>,
        from: LocalDate,
        to: LocalDate,
    ): CombinedTotals {
        var income = 0L
        var expense = 0L
        var count = 0
        expenses.forEach { transaction ->
            val date = transaction.date.toLocalDateOrNull()
            if (transaction.deletedAt != null || date == null || date < from || date > to) return@forEach

            if (transaction.type == TransactionType.INCOME) income += transaction.amount else expense += transaction.amount
            count++
        }
        return metrics(income, expense, count)
    }

    private fun gamblingMetrics(
        records: Iterable<GamblingRecordEntity>,
        from: LocalDate,
        to: LocalDate,
    ): CombinedTotals {
        var income = 0L
        var expense = 0L
        var count = 0
        records.forEach { record ->
            val date = record.date.toLocalDateOrNull()
            if (record.deletedAt != null || date == null || date < from || date > to) return@forEach

            expense += record.stakeAmount
            income += record.payoutAmount
            count++
        }
        return metrics(income, expense, count)
    }

    private fun metrics(income: Long, expense: Long, count: Int) =
        CombinedTotals(
            totalIncome = income,
            totalExpense = expense,
            balance = income - expense,
            recordCount = count,
        )

    private fun bounds(period: SummaryPeriod, anchorDate: LocalDate): Pair<LocalDate, LocalDate> =
        when (period) {
            SummaryPeriod.MONTH -> {
                val from = anchorDate.withDayOfMonth(1)
                from to from.with(TemporalAdjusters.lastDayOfMonth())
            }
            SummaryPeriod.YEAR -> {
                val from = anchorDate.withDayOfYear(1)
                from to anchorDate.with(TemporalAdjusters.lastDayOfYear())
            }
        }

    private fun String.toLocalDateOrNull(): LocalDate? = runCatching(LocalDate::parse).getOrNull()
}
