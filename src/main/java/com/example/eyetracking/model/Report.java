package com.example.eyetracking.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "training_session_id", nullable = false)
    private TrainingSession trainingSession;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    @Column(columnDefinition = "TEXT")
    private String attentionScore;

    @Column(columnDefinition = "TEXT")
    private String focusPattern;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Column(columnDefinition = "TEXT")
    private String detailedAnalysis;

    @Column(columnDefinition = "TEXT")
    private String effectiveFixationRate;

    @Column(columnDefinition = "TEXT")
    private String regressionCount;

    @Column(columnDefinition = "TEXT")
    private String saccadeEntropy;

    @Column(columnDefinition = "TEXT")
    private String dataQualityScore;

    @Column(columnDefinition = "TEXT")
    private String averageFixationDuration;

    @Column(columnDefinition = "TEXT")
    private String saccadePathLength;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TrainingSession getTrainingSession() {
        return trainingSession;
    }

    public void setTrainingSession(TrainingSession trainingSession) {
        this.trainingSession = trainingSession;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getAttentionScore() {
        return attentionScore;
    }

    public void setAttentionScore(String attentionScore) {
        this.attentionScore = attentionScore;
    }

    public String getFocusPattern() {
        return focusPattern;
    }

    public void setFocusPattern(String focusPattern) {
        this.focusPattern = focusPattern;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }

    public String getDetailedAnalysis() {
        return detailedAnalysis;
    }

    public void setDetailedAnalysis(String detailedAnalysis) {
        this.detailedAnalysis = detailedAnalysis;
    }

    public String getEffectiveFixationRate() {
        return effectiveFixationRate;
    }

    public void setEffectiveFixationRate(String effectiveFixationRate) {
        this.effectiveFixationRate = effectiveFixationRate;
    }

    public String getRegressionCount() {
        return regressionCount;
    }

    public void setRegressionCount(String regressionCount) {
        this.regressionCount = regressionCount;
    }

    public String getSaccadeEntropy() {
        return saccadeEntropy;
    }

    public void setSaccadeEntropy(String saccadeEntropy) {
        this.saccadeEntropy = saccadeEntropy;
    }

    public String getDataQualityScore() {
        return dataQualityScore;
    }

    public void setDataQualityScore(String dataQualityScore) {
        this.dataQualityScore = dataQualityScore;
    }

    public String getAverageFixationDuration() {
        return averageFixationDuration;
    }

    public void setAverageFixationDuration(String averageFixationDuration) {
        this.averageFixationDuration = averageFixationDuration;
    }

    public String getSaccadePathLength() {
        return saccadePathLength;
    }

    public void setSaccadePathLength(String saccadePathLength) {
        this.saccadePathLength = saccadePathLength;
    }
}