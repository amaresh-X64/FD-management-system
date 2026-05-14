package main

import (
	"fd-shield/go-risk-engine/handlers"
	"log"

	"github.com/gin-gonic/gin"
)

func main() {
	r := gin.Default()

	r.GET("/health", handlers.HealthCheck)
	r.POST("/risk/analyze", handlers.AnalyzeRisk)

	log.Println("Go Risk Engine starting on :8081")
	if err := r.Run(":8081"); err != nil {
		log.Fatalf("Failed to start: %v", err)
	}
}
