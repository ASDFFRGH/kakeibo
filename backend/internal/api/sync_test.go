package api

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"kakeibo/backend/internal/model"
)

func TestSyncRoutesAreRegistered(t *testing.T) {
	handler := New(nil, "")
	for _, path := range []string{"/api/v1/sync", "/api/v1/gambling/sync"} {
		t.Run(path, func(t *testing.T) {
			request := httptest.NewRequest(http.MethodPost, path, strings.NewReader("{"))
			recorder := httptest.NewRecorder()

			handler.ServeHTTP(recorder, request)

			if recorder.Code != http.StatusBadRequest {
				t.Fatalf("POST %s status = %d, want %d; route may be missing", path, recorder.Code, http.StatusBadRequest)
			}
		})
	}
}

func TestDecodeSyncRequestAcceptsCategoryType(t *testing.T) {
	request := httptest.NewRequest(
		"POST",
		"/api/v1/sync",
		strings.NewReader(`{
			"last_synced_at": null,
			"categories": [{
				"uuid": "10000000-0000-4000-8000-000000000001",
				"name": "食費",
				"type": "expense",
				"created_at": "2026-08-01T00:00:00Z",
				"updated_at": "2026-08-01T00:00:00Z",
				"deleted_at": null
			}],
			"expenses": []
		}`),
	)
	var syncRequest model.SyncRequest

	if err := decode(request, &syncRequest); err != nil {
		t.Fatalf("decode() error = %v", err)
	}
	if got := syncRequest.Categories[0].Type; got != model.TransactionTypeExpense {
		t.Fatalf("category type = %q, want %q", got, model.TransactionTypeExpense)
	}
}

func TestValidCategoryAcceptsSupportedTypesAndLegacyEmptyType(t *testing.T) {
	for _, categoryType := range []string{
		"",
		model.TransactionTypeExpense,
		model.TransactionTypeIncome,
	} {
		category := model.Category{
			UUID: "10000000-0000-4000-8000-000000000001",
			Name: "カテゴリ",
			Type: categoryType,
		}
		if err := validCategory(category); err != nil {
			t.Fatalf("validCategory(type=%q) error = %v", categoryType, err)
		}
	}
}

func TestValidCategoryRejectsUnsupportedType(t *testing.T) {
	category := model.Category{
		UUID: "10000000-0000-4000-8000-000000000001",
		Name: "カテゴリ",
		Type: "other",
	}

	if err := validCategory(category); err == nil {
		t.Fatal("validCategory() accepted unsupported type")
	}
}
