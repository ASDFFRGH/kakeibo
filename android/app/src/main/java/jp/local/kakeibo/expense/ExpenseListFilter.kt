package jp.local.kakeibo.expense

import jp.local.kakeibo.data.ExpenseEntity
import java.time.LocalDate
import java.time.YearMonth

fun List<ExpenseEntity>.monthlyExpenses(
    month: YearMonth,
    categoryUuid: String? = null,
): List<ExpenseEntity> =
    filter { expense ->
        YearMonth.from(LocalDate.parse(expense.date)) == month &&
            (categoryUuid == null || expense.categoryUuid == categoryUuid)
    }

fun YearMonth.defaultExpenseDate(today: LocalDate = LocalDate.now()): LocalDate =
    if (this == YearMonth.from(today)) {
        today
    } else {
        atDay(today.dayOfMonth.coerceAtMost(lengthOfMonth()))
    }
