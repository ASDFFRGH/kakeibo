package jp.local.kakeibo.summary

import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class SummaryPeriod(
    val displayName: String,
) {
    MONTH("月別"),
    YEAR("年別"),
    ;

    fun shift(
        anchor: LocalDate,
        amount: Long,
    ): LocalDate =
        when (this) {
            MONTH -> anchor.plusMonths(amount)
            YEAR -> anchor.plusYears(amount)
        }
}

data class CategorySummary(
    val categoryUuid: String,
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
    val categories: List<CategorySummary>,
)

object SummaryCalculator {
    fun calculate(
        period: SummaryPeriod,
        anchorDate: LocalDate,
        expenses: List<ExpenseEntity>,
        categoryUuids: List<String> = emptyList(),
    ): SummaryResult {
        val (from, to) = bounds(period, anchorDate)
        val categories =
            categoryUuids
                .distinct()
                .associateWithTo(linkedMapOf()) { CategorySummary(categoryUuid = it) }
        var totalExpense = 0L
        var totalIncome = 0L
        var expenseCount = 0
        var incomeCount = 0

        expenses.forEach { expense ->
            val date = runCatching { LocalDate.parse(expense.date) }.getOrNull()
            if (date == null || expense.deletedAt != null || date < from || date > to) {
                return@forEach
            }
            val category =
                categories.getOrPut(expense.categoryUuid) {
                    CategorySummary(categoryUuid = expense.categoryUuid)
                }
            if (expense.type == TransactionType.INCOME) {
                val incomeAmount = category.incomeAmount + expense.amount
                categories[expense.categoryUuid] =
                    category.copy(
                        incomeAmount = incomeAmount,
                        balance = incomeAmount - category.expenseAmount,
                        incomeCount = category.incomeCount + 1,
                    )
                totalIncome += expense.amount
                incomeCount++
            } else {
                val expenseAmount = category.expenseAmount + expense.amount
                categories[expense.categoryUuid] =
                    category.copy(
                        expenseAmount = expenseAmount,
                        balance = category.incomeAmount - expenseAmount,
                        expenseCount = category.expenseCount + 1,
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
            categories = categories.values.toList(),
        )
    }

    private fun bounds(
        period: SummaryPeriod,
        anchorDate: LocalDate,
    ): Pair<LocalDate, LocalDate> =
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
}
