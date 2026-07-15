package api

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"kakeibo/backend/internal/model"
)

type trashRepositoryStub struct {
	categories       []model.Category
	expenses         []model.Expense
	restoredCategory string
	restoredExpense  string
}

func (s *trashRepositoryStub) ListDeletedCategories(context.Context) ([]model.Category, error) {
	return s.categories, nil
}
func (s *trashRepositoryStub) ListDeletedExpenses(context.Context) ([]model.Expense, error) {
	return s.expenses, nil
}
func (s *trashRepositoryStub) RestoreCategory(_ context.Context, id string) error {
	s.restoredCategory = id
	return nil
}
func (s *trashRepositoryStub) RestoreExpense(_ context.Context, id string) error {
	s.restoredExpense = id
	return nil
}

func TestListTrashReturnsDeletedRecords(t *testing.T) {
	deletedAt := time.Now()
	stub := &trashRepositoryStub{
		categories: []model.Category{{UUID: "category", Name: "食費", DeletedAt: &deletedAt}},
		expenses:   []model.Expense{{UUID: "expense", Date: "2026-07-15", DeletedAt: &deletedAt}},
	}
	a := &API{trashRepo: stub}
	recorder := httptest.NewRecorder()

	a.listTrash(recorder, httptest.NewRequest(http.MethodGet, "/api/v1/trash", nil))

	if recorder.Code != http.StatusOK {
		t.Fatalf("status = %d", recorder.Code)
	}
	var body struct {
		Success bool            `json:"success"`
		Data    model.TrashData `json:"data"`
	}
	if err := json.NewDecoder(recorder.Body).Decode(&body); err != nil {
		t.Fatal(err)
	}
	if !body.Success || len(body.Data.Categories) != 1 || len(body.Data.Expenses) != 1 {
		t.Fatalf("unexpected response: %+v", body)
	}
}

func TestRestoreHandlersPassPathUUID(t *testing.T) {
	stub := &trashRepositoryStub{}
	a := &API{trashRepo: stub}

	categoryRequest := httptest.NewRequest(http.MethodPost, "/api/v1/categories/category-id/restore", nil)
	categoryRequest.SetPathValue("uuid", "category-id")
	categoryRecorder := httptest.NewRecorder()
	a.restoreCategory(categoryRecorder, categoryRequest)

	expenseRequest := httptest.NewRequest(http.MethodPost, "/api/v1/expenses/expense-id/restore", nil)
	expenseRequest.SetPathValue("uuid", "expense-id")
	expenseRecorder := httptest.NewRecorder()
	a.restoreExpense(expenseRecorder, expenseRequest)

	if categoryRecorder.Code != http.StatusNoContent || stub.restoredCategory != "category-id" {
		t.Fatalf("category status = %d, id = %q", categoryRecorder.Code, stub.restoredCategory)
	}
	if expenseRecorder.Code != http.StatusNoContent || stub.restoredExpense != "expense-id" {
		t.Fatalf("expense status = %d, id = %q", expenseRecorder.Code, stub.restoredExpense)
	}
}
