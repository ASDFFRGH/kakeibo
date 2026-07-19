package jp.local.kakeibo.expense

import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DayTotals(
    val expense: Long = 0,
    val income: Long = 0,
)

fun calendarDates(month: YearMonth): List<LocalDate?> {
    val leadingBlanks = month.atDay(1).dayOfWeek.value % DayOfWeek.SUNDAY.value
    val dates = MutableList<LocalDate?>(42) { null }
    repeat(month.lengthOfMonth()) { index -> dates[leadingBlanks + index] = month.atDay(index + 1) }
    return dates
}

fun dailyTotals(expenses: List<ExpenseEntity>): Map<LocalDate, DayTotals> =
    expenses.groupBy { LocalDate.parse(it.date) }.mapValues { (_, items) ->
        DayTotals(
            expense = items.filter { it.type != TransactionType.INCOME }.sumOf { it.amount },
            income = items.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
        )
    }

fun YearMonth.coerceDay(day: Int): LocalDate = atDay(day.coerceIn(1, lengthOfMonth()))
