package api

import (
	"net/http/httptest"
	"strings"
	"testing"
)

func TestCreateExpensesBatchRejectsInvalidRequests(t *testing.T) {
	tests := []struct {
		name string
		body string
	}{
		{
			name: "empty batch",
			body: `{"expenses":[]}`,
		},
		{
			name: "invalid expense",
			body: `{"expenses":[{"uuid":"not-a-uuid","date":"2026-08-31","amount":1000,"type":"expense","category_uuid":"10000000-0000-4000-8000-000000000001","memo":"monthly"}]}`,
		},
		{
			name: "unknown field",
			body: `{"expenses":[],"unexpected":true}`,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			request := httptest.NewRequest("POST", "/api/v1/expenses/batch", strings.NewReader(test.body))
			response := httptest.NewRecorder()

			(&API{}).createExpensesBatch(response, request)

			if response.Code != 400 {
				t.Fatalf("status = %d, want 400", response.Code)
			}
		})
	}
}
