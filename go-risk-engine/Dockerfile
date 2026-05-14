FROM golang:1.22-alpine AS build
WORKDIR /app
COPY go.mod ./
COPY . .
RUN go mod tidy && go build -o risk-engine ./main.go

FROM alpine:latest
WORKDIR /app
COPY --from=build /app/risk-engine .
EXPOSE 8081
CMD ["./risk-engine"]
