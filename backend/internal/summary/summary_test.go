package summary

import (
	"testing"
	"time"

	"kakeibo/backend/internal/model"
)

func TestBuildWeekIncludesMondayToSundayAndZeroAmountDays(t *testing.T) {
	expenses := []model.Expense{
		{Date: "2026-07-13", Amount: 1200},
		{Date: "2026-07-19", Amount: 800},
		{Date: "2026-07-20", Amount: 5000},
	}

	result := Build(Week, mustDate(t, "2026-07-15"), expenses)

	if result.From != "2026-07-13" || result.To != "2026-07-19" {
		t.Fatalf("week range = %s..%s", result.From, result.To)
	}
	if result.TotalExpense != 2000 || result.ExpenseCount != 2 {
		t.Fatalf("total = %d, count = %d", result.TotalExpense, result.ExpenseCount)
	}
	if len(result.Buckets) != 7 || result.Buckets[1].ExpenseAmount != 0 {
		t.Fatalf("buckets = %#v", result.Buckets)
	}
}

func TestBuildMonthIncludesEveryDayAndExcludesDeletedExpenses(t *testing.T) {
	deletedAt := time.Now()
	expenses := []model.Expense{
		{Date: "2026-02-01", Amount: 100},
		{Date: "2026-02-28", Amount: 200},
		{Date: "2026-02-14", Amount: 300, DeletedAt: &deletedAt},
		{Date: "2026-03-01", Amount: 400},
	}

	result := Build(Month, mustDate(t, "2026-02-10"), expenses)

	if len(result.Buckets) != 28 {
		t.Fatalf("bucket count = %d", len(result.Buckets))
	}
	if result.TotalExpense != 300 || result.ExpenseCount != 2 {
		t.Fatalf("total = %d, count = %d", result.TotalExpense, result.ExpenseCount)
	}
}

func TestBuildYearGroupsExpensesIntoTwelveMonths(t *testing.T) {
	expenses := []model.Expense{
		{Date: "2026-01-31", Amount: 1000},
		{Date: "2026-07-14", Amount: 2500},
		{Date: "2026-07-15", Amount: 500},
	}

	result := Build(Year, mustDate(t, "2026-07-14"), expenses)

	if len(result.Buckets) != 12 {
		t.Fatalf("bucket count = %d", len(result.Buckets))
	}
	if result.Buckets[0].ExpenseAmount != 1000 || result.Buckets[6].ExpenseAmount != 3000 {
		t.Fatalf("buckets = %#v", result.Buckets)
	}
	if result.TotalExpense != 4000 || result.ExpenseCount != 3 {
		t.Fatalf("total = %d, count = %d", result.TotalExpense, result.ExpenseCount)
	}
}

func TestBuildSeparatesIncomeExpenseAndBalance(t *testing.T) {
	expenses := []model.Expense{
		{Date: "2026-07-14", Amount: 1000, Type: model.TransactionTypeExpense},
		{Date: "2026-07-14", Amount: 5000, Type: model.TransactionTypeIncome},
	}

	result := Build(Day, mustDate(t, "2026-07-14"), expenses)

	if result.TotalExpense != 1000 || result.TotalIncome != 5000 || result.Balance != 4000 {
		t.Fatalf("summary = %#v", result)
	}
	if result.ExpenseCount != 1 || result.IncomeCount != 1 || result.Buckets[0].Balance != 4000 {
		t.Fatalf("summary = %#v", result)
	}
}

func TestParseRejectsUnsupportedPeriodAndInvalidDate(t *testing.T) {
	if _, _, err := Parse("quarter", "2026-07-14"); err == nil {
		t.Fatal("unsupported period was accepted")
	}
	if _, _, err := Parse("month", "2026-02-30"); err == nil {
		t.Fatal("invalid date was accepted")
	}
}

func mustDate(t *testing.T, value string) time.Time {
	t.Helper()
	date, err := time.Parse("2006-01-02", value)
	if err != nil {
		t.Fatal(err)
	}
	return date
}
