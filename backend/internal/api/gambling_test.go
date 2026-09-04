package api

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"kakeibo/backend/internal/model"
)

const gamblingRecordUUID = "40000000-0000-4000-8000-000000000001"

type gamblingRepositoryStub struct {
	records       []model.GamblingRecord
	saved         model.GamblingRecord
	deleted       string
	restored      string
	syncCalled    bool
	syncRecords   []model.GamblingRecord
	syncSince     time.Time
	syncCutoff    time.Time
	syncCanonical []model.GamblingRecord
	err           error
}

func (s *gamblingRepositoryStub) ListGamblingRecords(context.Context, string, string, string, bool) ([]model.GamblingRecord, error) {
	return s.records, s.err
}

func (s *gamblingRepositoryStub) GetGamblingRecord(_ context.Context, id string) (model.GamblingRecord, error) {
	for _, record := range s.records {
		if record.UUID == id {
			return record, s.err
		}
	}
	return model.GamblingRecord{}, errors.New("unexpected id")
}

func (s *gamblingRepositoryStub) SaveGamblingRecord(_ context.Context, record model.GamblingRecord) (model.GamblingRecord, error) {
	s.saved = record
	return record, s.err
}

func (s *gamblingRepositoryStub) DeleteGamblingRecord(_ context.Context, id string) error {
	s.deleted = id
	return s.err
}

func (s *gamblingRepositoryStub) ListDeletedGamblingRecords(context.Context) ([]model.GamblingRecord, error) {
	return s.records, s.err
}

func (s *gamblingRepositoryStub) RestoreGamblingRecord(_ context.Context, id string) error {
	s.restored = id
	return s.err
}

func (s *gamblingRepositoryStub) SyncGamblingRecords(
	_ context.Context,
	records []model.GamblingRecord,
	since time.Time,
) (time.Time, []string, model.GamblingSyncData, error) {
	s.syncCalled = true
	s.syncRecords = records
	s.syncSince = since
	ids := make([]string, len(records))
	for i := range records {
		ids[i] = records[i].UUID
	}
	return s.syncCutoff, ids, model.GamblingSyncData{Records: s.syncCanonical}, s.err
}

func validGamblingRecordFixture() model.GamblingRecord {
	return model.GamblingRecord{
		UUID:         gamblingRecordUUID,
		Date:         "2026-09-04",
		StakeAmount:  1000,
		PayoutAmount: 1500,
		GameType:     "競馬",
		Memo:         "テスト",
	}
}

func TestValidGamblingRecord(t *testing.T) {
	tests := []struct {
		name    string
		mutate  func(*model.GamblingRecord)
		wantErr bool
	}{
		{name: "valid"},
		{name: "stake only", mutate: func(x *model.GamblingRecord) { x.PayoutAmount = 0 }},
		{name: "payout only", mutate: func(x *model.GamblingRecord) { x.StakeAmount = 0 }},
		{name: "malformed uuid", mutate: func(x *model.GamblingRecord) { x.UUID = "40000000-0000-4000-8000-00000000000z" }, wantErr: true},
		{name: "malformed date", mutate: func(x *model.GamblingRecord) { x.Date = "2026-9-4" }, wantErr: true},
		{name: "negative stake", mutate: func(x *model.GamblingRecord) { x.StakeAmount = -1 }, wantErr: true},
		{name: "negative payout", mutate: func(x *model.GamblingRecord) { x.PayoutAmount = -1 }, wantErr: true},
		{name: "both amounts zero", mutate: func(x *model.GamblingRecord) { x.StakeAmount, x.PayoutAmount = 0, 0 }, wantErr: true},
		{name: "blank game type", mutate: func(x *model.GamblingRecord) { x.GameType = " \t" }, wantErr: true},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			record := validGamblingRecordFixture()
			if test.mutate != nil {
				test.mutate(&record)
			}
			err := validGamblingRecord(record)
			if (err != nil) != test.wantErr {
				t.Fatalf("validGamblingRecord() error = %v, wantErr = %v", err, test.wantErr)
			}
		})
	}
}

func TestCreateGamblingRecordTrimsGameType(t *testing.T) {
	stub := &gamblingRepositoryStub{}
	handler := (&API{gamblingRepo: stub}).routes()
	body := `{
		"uuid":"40000000-0000-4000-8000-000000000001",
		"date":"2026-09-04",
		"stake_amount":1000,
		"payout_amount":1500,
		"game_type":" 競馬 ",
		"memo":"テスト",
		"created_at":"2026-09-04T01:00:00Z",
		"updated_at":"2026-09-04T01:00:00Z",
		"deleted_at":null
	}`
	request := httptest.NewRequest(http.MethodPost, "/api/v1/gambling/records", strings.NewReader(body))
	response := httptest.NewRecorder()

	handler.ServeHTTP(response, request)

	if response.Code != http.StatusCreated {
		t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
	}
	if stub.saved.GameType != "競馬" {
		t.Fatalf("saved game_type = %q", stub.saved.GameType)
	}
}

func TestGamblingSyncValidatesAllRecordsBeforeSaving(t *testing.T) {
	stub := &gamblingRepositoryStub{}
	handler := (&API{gamblingRepo: stub}).routes()
	body := `{
		"last_synced_at":null,
		"records":[
			{"uuid":"40000000-0000-4000-8000-000000000001","date":"2026-09-04","stake_amount":1000,"payout_amount":1500,"game_type":"競馬","memo":"","created_at":"2026-09-04T01:00:00Z","updated_at":"2026-09-04T01:00:00Z","deleted_at":null},
			{"uuid":"40000000-0000-4000-8000-000000000002","date":"2026-09-04","stake_amount":0,"payout_amount":0,"game_type":"競馬","memo":"","created_at":"2026-09-04T01:00:00Z","updated_at":"2026-09-04T01:00:00Z","deleted_at":null}
		]
	}`
	request := httptest.NewRequest(http.MethodPost, "/api/v1/gambling/sync", strings.NewReader(body))
	response := httptest.NewRecorder()

	handler.ServeHTTP(response, request)

	if response.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
	}
	if stub.syncCalled {
		t.Fatal("repository sync was called for a partially invalid upload")
	}
}

func TestGamblingSyncReturnsIndependentEnvelope(t *testing.T) {
	cutoff := time.Date(2026, 9, 4, 2, 0, 0, 0, time.UTC)
	canonical := validGamblingRecordFixture()
	canonical.UpdatedAt = cutoff.Add(-time.Minute)
	stub := &gamblingRepositoryStub{syncCutoff: cutoff, syncCanonical: []model.GamblingRecord{canonical}}
	handler := (&API{gamblingRepo: stub}).routes()
	body := `{
		"last_synced_at":"2026-09-01T00:00:00Z",
		"records":[{"uuid":"40000000-0000-4000-8000-000000000001","date":"2026-09-04","stake_amount":1000,"payout_amount":1500,"game_type":"競馬","memo":"","created_at":"2026-09-04T01:00:00Z","updated_at":"2026-09-04T01:00:00Z","deleted_at":null}]
	}`
	request := httptest.NewRequest(http.MethodPost, "/api/v1/gambling/sync", strings.NewReader(body))
	response := httptest.NewRecorder()

	handler.ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
	}
	var decoded struct {
		Success    bool                   `json:"success"`
		ServerTime time.Time              `json:"server_time"`
		Synced     []string               `json:"synced"`
		Data       model.GamblingSyncData `json:"data"`
	}
	if err := json.NewDecoder(response.Body).Decode(&decoded); err != nil {
		t.Fatal(err)
	}
	if !decoded.Success || !decoded.ServerTime.Equal(cutoff) {
		t.Fatalf("unexpected envelope: %+v", decoded)
	}
	if len(decoded.Synced) != 1 || decoded.Synced[0] != gamblingRecordUUID {
		t.Fatalf("synced = %#v", decoded.Synced)
	}
	if len(decoded.Data.Records) != 1 || decoded.Data.Records[0].UUID != gamblingRecordUUID {
		t.Fatalf("data = %#v", decoded.Data)
	}
	if stub.syncSince.Format(time.RFC3339) != "2026-09-01T00:00:00Z" {
		t.Fatalf("since = %s", stub.syncSince)
	}
}

func TestGamblingDeleteAndRestoreRoutes(t *testing.T) {
	stub := &gamblingRepositoryStub{}
	handler := (&API{gamblingRepo: stub}).routes()

	deleteResponse := httptest.NewRecorder()
	handler.ServeHTTP(deleteResponse, httptest.NewRequest(
		http.MethodDelete, "/api/v1/gambling/records/"+gamblingRecordUUID, nil,
	))
	restoreResponse := httptest.NewRecorder()
	handler.ServeHTTP(restoreResponse, httptest.NewRequest(
		http.MethodPost, "/api/v1/gambling/records/"+gamblingRecordUUID+"/restore", nil,
	))

	if deleteResponse.Code != http.StatusNoContent || stub.deleted != gamblingRecordUUID {
		t.Fatalf("delete status = %d, id = %q", deleteResponse.Code, stub.deleted)
	}
	if restoreResponse.Code != http.StatusNoContent || stub.restored != gamblingRecordUUID {
		t.Fatalf("restore status = %d, id = %q", restoreResponse.Code, stub.restored)
	}
}
