package handlers

import (
	"fd-management/go-risk-engine/models"
	"fd-management/go-risk-engine/services"
	"net/http"
	"github.com/gin-gonic/gin"
)

func AnalyzeRisk(c *gin.Context) {
	var req models.RiskRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	result := services.AnalyzeRisk(req)
	c.JSON(http.StatusOK, result)
}

func HealthCheck(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"status": "Go Risk Engine is running"})
}
