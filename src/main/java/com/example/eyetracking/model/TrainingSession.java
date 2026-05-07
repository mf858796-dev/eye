package com.example.eyetracking.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "training_sessions")
public class TrainingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String sessionName;

    @Column
    private Long trainingLevelId;

    @Column
    private String taskType;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column
    private LocalDateTime endTime;

    @Column
    private Integer duration; // 单位：分钟

    @Column
    private String status;

    @Column
    private Double completionRate;

    @Column
    private Double accuracy;

    @Column
    private Integer targetHits;

    @Column
    private Integer targetSamples;

    @OneToMany(mappedBy = "trainingSession", cascade = CascadeType.ALL)
    private List<GazeData> gazeDataList;

    @OneToMany(mappedBy = "trainingSession", cascade = CascadeType.ALL)
    private List<Report> reports;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public Long getTrainingLevelId() {
        return trainingLevelId;
    }

    public void setTrainingLevelId(Long trainingLevelId) {
        this.trainingLevelId = trainingLevelId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(Double completionRate) {
        this.completionRate = completionRate;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    public Integer getTargetHits() {
        return targetHits;
    }

    public void setTargetHits(Integer targetHits) {
        this.targetHits = targetHits;
    }

    public Integer getTargetSamples() {
        return targetSamples;
    }

    public void setTargetSamples(Integer targetSamples) {
        this.targetSamples = targetSamples;
    }

    public List<GazeData> getGazeDataList() {
        return gazeDataList;
    }

    public void setGazeDataList(List<GazeData> gazeDataList) {
        this.gazeDataList = gazeDataList;
    }

    public List<Report> getReports() {
        return reports;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
    }
}
