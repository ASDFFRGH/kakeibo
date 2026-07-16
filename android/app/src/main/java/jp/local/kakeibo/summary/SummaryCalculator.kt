package jp.local.kakeibo.summary

import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class SummaryPeriod(
    val displayName: String,
) {
    DAY("日別"),
    WEEK("週別"),
    MONTH("月別"),
    YEAR("年別"),
    ;

    fun shift(
        anchor: LocalDate,
        amount: Long,
    ): LocalDate =
        when (this) {
            DAY -> anchor.plusDays(amount)
            WEEK -> anchor.plusWeeks(amount)
            MONTH -> anchor.plusMonths(amount)
            YEAR -> anchor.plusYears(amount)
        }
}

data class SummaryBucket(
    val key: String,
    val from: LocalDate,
    val to: LocalDate,
    val expenseAmount: Long = 0,
    val incomeAmount: Long = 0,
    val balance: Long = 0,
    val expenseCount: Int = 0,
    val incomeCount: Int = 0,
)

data class SummaryResult(
    val period: SummaryPeriod,
    val anchorDate: LocalDate,
    val from: LocalDate,
    val to: LocalDate,
    val totalExpense: Long,
    val totalIncome: Long,
    val balance: Long,
    val expenseCount: Int,
    val incomeCount: Int,
    val buckets: List<SummaryBucket>,
)

object SummaryCalculator {
    fun calculate(
        period: SummaryPeriod,
        anchorDate: LocalDate,
        expenses: List<ExpenseEntity>,
    ): SummaryResult {
        val (from, to) = bounds(period, anchorDate)
        val buckets = emptyBuckets(period, from, to).toMutableList()
        val bucketIndexes = buckets.mapIndexed { index, bucket -> bucket.key to index }.toMap()
        var totalExpense = 0L
        var totalIncome = 0L
        var expenseCount = 0
        var incomeCount = 0

        expenses.forEach { expense ->
            val date = runCatching { LocalDate.parse(expense.date) }.getOrNull()
            if (date == null || expense.deletedAt != null || date < from || date > to) {
                return@forEach
            }
            val key = if (period == SummaryPeriod.YEAR) expense.date.take(7) else expense.date
            val index = bucketIndexes[key] ?: return@forEach
            val bucket = buckets[index]
            if (expense.type == TransactionType.INCOME) {
                val incomeAmount = bucket.incomeAmount + expense.amount
                buckets[index] =
                    bucket.copy(
                        incomeAmount = incomeAmount,
                        balance = incomeAmount - bucket.expenseAmount,
                        incomeCount = bucket.incomeCount + 1,
                    )
                totalIncome += expense.amount
                incomeCount++
            } else {
                val expenseAmount = bucket.expenseAmount + expense.amount
                buckets[index] =
                    bucket.copy(
                        expenseAmount = expenseAmount,
                        balance = bucket.incomeAmount - expenseAmount,
                        expenseCount = bucket.expenseCount + 1,
                    )
                totalExpense += expense.amount
                expenseCount++
            }
        }

        return SummaryResult(
            period = period,
            anchorDate = anchorDate,
            from = from,
            to = to,
            totalExpense = totalExpense,
            totalIncome = totalIncome,
            balance = totalIncome - totalExpense,
            expenseCount = expenseCount,
            incomeCount = incomeCount,
            buckets = buckets,
        )
    }

    private fun bounds(
        period: SummaryPeriod,
        anchorDate: LocalDate,
    ): Pair<LocalDate, LocalDate> =
        when (period) {
            SummaryPeriod.DAY -> anchorDate to anchorDate
            SummaryPeriod.WEEK -> {
                val from = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                from to from.plusDays(6)
            }
            SummaryPeriod.MONTH -> {
                val from = anchorDate.withDayOfMonth(1)
                from to from.with(TemporalAdjusters.lastDayOfMonth())
            }
            SummaryPeriod.YEAR -> {
                val from = anchorDate.withDayOfYear(1)
                from to anchorDate.with(TemporalAdjusters.lastDayOfYear())
            }
        }

    private fun emptyBuckets(
        period: SummaryPeriod,
        from: LocalDate,
        to: LocalDate,
    ): List<SummaryBucket> =
        if (period == SummaryPeriod.YEAR) {
            (1..12).map { month ->
                val monthFrom = from.withMonth(month).withDayOfMonth(1)
                SummaryBucket(
                    key = monthFrom.toString().take(7),
                    from = monthFrom,
                    to = monthFrom.with(TemporalAdjusters.lastDayOfMonth()),
                )
            }
        } else {
            generateSequence(from) { current -> current.plusDays(1).takeIf { it <= to } }
                .map { date -> SummaryBucket(key = date.toString(), from = date, to = date) }
                .toList()
        }
}
