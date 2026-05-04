package com.example.eyetracking.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "gaze_data")
public class GazeData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "training_session_id", nullable = false)
    private TrainingSession trainingSession;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private Double xCoordinate;

    @Column(nullable = false)
    private Double yCoordinate;

    @Column(nullable = false)
    private Double zCoordinate;

    @Column
    private Double yaw;

    @Column
    private Double pitch;

    @Column
    private Double roll;

    @Column
    private Double pupilDiameter;

    @Column
    private String fixationType;

    @Column
    private Integer fixationDuration;

    @Column
    private String areaOfInterest;

    @Column
    private Double gazeOriginX;

    @Column
    private Double gazeOriginY;

    @Column
    private Double gazeOriginZ;

    @Column
    private Double gazeDirectionX;

    @Column
    private Double gazeDirectionY;

    @Column
    private Double gazeDirectionZ;

    @Column
    private Double leftPupilDiameter;

    @Column
    private Double rightPupilDiameter;

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

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Double getxCoordinate() {
        return xCoordinate;
    }

    public void setxCoordinate(Double xCoordinate) {
        this.xCoordinate = xCoordinate;
    }

    public Double getX() {
        return xCoordinate;
    }

    public Double getY() {
        return yCoordinate;
    }

    public Double getyCoordinate() {
        return yCoordinate;
    }

    public void setyCoordinate(Double yCoordinate) {
        this.yCoordinate = yCoordinate;
    }

    public Double getzCoordinate() {
        return zCoordinate;
    }

    public void setzCoordinate(Double zCoordinate) {
        this.zCoordinate = zCoordinate;
    }

    public Double getYaw() {
        return yaw;
    }

    public void setYaw(Double yaw) {
        this.yaw = yaw;
    }

    public Double getPitch() {
        return pitch;
    }

    public void setPitch(Double pitch) {
        this.pitch = pitch;
    }

    public Double getRoll() {
        return roll;
    }

    public void setRoll(Double roll) {
        this.roll = roll;
    }

    public Double getPupilDiameter() {
        return pupilDiameter;
    }

    public void setPupilDiameter(Double pupilDiameter) {
        this.pupilDiameter = pupilDiameter;
    }

    public String getFixationType() {
        return fixationType;
    }

    public void setFixationType(String fixationType) {
        this.fixationType = fixationType;
    }

    public Integer getFixationDuration() {
        return fixationDuration;
    }

    public void setFixationDuration(Integer fixationDuration) {
        this.fixationDuration = fixationDuration;
    }

    public String getAreaOfInterest() {
        return areaOfInterest;
    }

    public void setAreaOfInterest(String areaOfInterest) {
        this.areaOfInterest = areaOfInterest;
    }

    public Double getGazeOriginX() {
        return gazeOriginX;
    }

    public void setGazeOriginX(Double gazeOriginX) {
        this.gazeOriginX = gazeOriginX;
    }

    public Double getGazeOriginY() {
        return gazeOriginY;
    }

    public void setGazeOriginY(Double gazeOriginY) {
        this.gazeOriginY = gazeOriginY;
    }

    public Double getGazeOriginZ() {
        return gazeOriginZ;
    }

    public void setGazeOriginZ(Double gazeOriginZ) {
        this.gazeOriginZ = gazeOriginZ;
    }

    public Double getGazeDirectionX() {
        return gazeDirectionX;
    }

    public void setGazeDirectionX(Double gazeDirectionX) {
        this.gazeDirectionX = gazeDirectionX;
    }

    public Double getGazeDirectionY() {
        return gazeDirectionY;
    }

    public void setGazeDirectionY(Double gazeDirectionY) {
        this.gazeDirectionY = gazeDirectionY;
    }

    public Double getGazeDirectionZ() {
        return gazeDirectionZ;
    }

    public void setGazeDirectionZ(Double gazeDirectionZ) {
        this.gazeDirectionZ = gazeDirectionZ;
    }

    public Double getLeftPupilDiameter() {
        return leftPupilDiameter;
    }

    public void setLeftPupilDiameter(Double leftPupilDiameter) {
        this.leftPupilDiameter = leftPupilDiameter;
    }

    public Double getRightPupilDiameter() {
        return rightPupilDiameter;
    }

    public void setRightPupilDiameter(Double rightPupilDiameter) {
        this.rightPupilDiameter = rightPupilDiameter;
    }
}
