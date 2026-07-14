package api

import (
	"context"
	"encoding/json"
	"net/http/httptest"
	"testing"

	"kakeibo/backend/internal/model"
	"kakeibo/backend/internal/summary"
)

type summaryRepositoryStub struct {
	expenses []model.Expense
}

func (s summaryRepositoryStub) ListExpenses(
	_ context.Context,
	_, _, _ string,
	_ bool,
) ([]model.Expense, error) {
	return s.expenses, nil
}

func TestSummaryEndpointReturnsRequestedPeriodTotals(t *testing.T) {
	api := &API{summaryRepo: summaryRepositoryStub{expenses: []model.Expense{
		{Date: "2026-07-13", Amount: 1200},
		{Date: "2026-07-19", Amount: 800},
	}}}
	request := httptest.NewRequest("GET", "/api/v1/summaries?period=week&date=2026-07-15", nil)
	response := httptest.NewRecorder()

	api.getSummary(response, request)

	if response.Code != 200 {
		t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
	}
	var body struct {
		Success bool           `json:"success"`
		Data    summary.Result `json:"data"`
	}
	if err := json.NewDecoder(response.Body).Decode(&body); err != nil {
		t.Fatal(err)
	}
	if !body.Success || body.Data.TotalAmount != 2000 || body.Data.ExpenseCount != 2 {
		t.Fatalf("body = %#v", body)
	}
	if body.Data.From != "2026-07-13" || body.Data.To != "2026-07-19" {
		t.Fatalf("range = %s..%s", body.Data.From, body.Data.To)
	}
}

func TestSummaryEndpointRejectsInvalidPeriod(t *testing.T) {
	api := &API{summaryRepo: summaryRepositoryStub{}}
	request := httptest.NewRequest("GET", "/api/v1/summaries?period=quarter&date=2026-07-15", nil)
	response := httptest.NewRecorder()

	api.getSummary(response, request)

	if response.Code != 400 {
		t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
	}
}
