package jp.local.kakeibo.expense

import jp.local.kakeibo.data.ExpenseEntity
import jp.local.kakeibo.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthTest {
    @Test
    fun calendarStartsOnSundayAndAlwaysHasSixWeeks() {
        val dates = calendarDates(YearMonth.of(2026, 7))
        assertEquals(42, dates.size)
        assertNull(dates[0])
        assertEquals(LocalDate.of(2026, 7, 1), dates[3])
        assertEquals(LocalDate.of(2026, 7, 31), dates[33])
    }

    @Test
    fun totalsSeparateIncomeAndExpense() {
        val date = LocalDate.of(2026, 7, 2)
        val totals = dailyTotals(listOf(expense("a", date, 120, TransactionType.EXPENSE), expense("b", date, 300, TransactionType.INCOME)))
        assertEquals(DayTotals(expense = 120, income = 300), totals[date])
    }

    @Test
    fun dayIsCoercedWhenMovingToShorterMonth() {
        assertEquals(LocalDate.of(2026, 2, 28), YearMonth.of(2026, 2).coerceDay(31))
    }

    private fun expense(uuid: String, date: LocalDate, amount: Long, type: String) =
        ExpenseEntity(uuid, date.toString(), amount, type, "category", "", "now", "now")
}
