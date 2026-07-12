FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN go mod tidy
RUN CGO_ENABLED=0 go build -o /server ./cmd/server

FROM alpine:3.20
RUN adduser -D app
USER app
COPY --from=build /server /server
EXPOSE 8080
ENTRYPOINT ["/server"]
