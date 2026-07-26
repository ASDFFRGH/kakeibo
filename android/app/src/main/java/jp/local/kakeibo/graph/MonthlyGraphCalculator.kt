package jp.local.kakeibo.graph

import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import java.time.LocalDate
import java.time.YearMonth

data class MonthlyGraphPoint(
    val month: YearMonth,
    val expense: Long = 0,
    val income: Long = 0,
) {
    val balance: Long
        get() = income - expense
}

object MonthlyGraphCalculator {
    fun calculate(
        year: Int,
        expenses: List<ExpenseEntity>,
        categoryUuid: String? = null,
    ): List<MonthlyGraphPoint> {
        val totals =
            (1..12).associateWith {
                MonthlyGraphPoint(month = YearMonth.of(year, it))
            }.toMutableMap()

        expenses.forEach { expense ->
            val date = runCatching { LocalDate.parse(expense.date) }.getOrNull()
            if (
                date == null ||
                date.year != year ||
                expense.deletedAt != null ||
                (!categoryUuid.isNullOrEmpty() && expense.categoryUuid != categoryUuid)
            ) {
                return@forEach
            }

            val current = totals.getValue(date.monthValue)
            totals[date.monthValue] =
                if (expense.type == TransactionType.INCOME) {
                    current.copy(income = current.income + expense.amount)
                } else {
                    current.copy(expense = current.expense + expense.amount)
                }
        }

        return totals.values.toList()
    }
}
