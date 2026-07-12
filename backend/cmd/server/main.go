package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"time"

	"kakeibo/backend/internal/api"
	"kakeibo/backend/internal/database"
	"kakeibo/backend/internal/repository"
)

func main() {
	ctx := context.Background()
	db, err := database.Open(ctx, env("DATABASE_URL", "postgres://kakeibo:kakeibo@localhost:5432/kakeibo?sslmode=disable"))
	if err != nil {
		log.Fatal(err)
	}
	defer db.Close()

	repo := repository.New(db)
	handler := api.New(repo, os.Getenv("CORS_ORIGIN"))
	server := &http.Server{Addr: ":" + env("PORT", "8080"), Handler: handler, ReadHeaderTimeout: 5 * time.Second}
	log.Printf("INFO API listening on %s", server.Addr)
	log.Fatal(server.ListenAndServe())
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
