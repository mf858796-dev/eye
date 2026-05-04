package com.example.eyetracking.model;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 眼动仪校准数据模型
 * 用于存储校准过程中的数据点
 */
@Entity
@Table(name = "calibration_data")
public class CalibrationData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime calibrationTime;

    @Column(nullable = false)
    private String status; // SUCCESS, FAILED, PENDING

    @Column
    private Integer pointIndex; // 校准点索引 (1-9)

    @Column
    private Double screenX; // 屏幕x坐标 (像素)

    @Column
    private Double screenY; // 屏幕y坐标 (像素)

    @Column
    private Double gazeX; // 眼动x坐标 (3D, 毫米)

    @Column
    private Double gazeY; // 眼动y坐标 (3D, 毫米)

    @Column
    private Double gazeZ; // 眼动z坐标 (3D, 毫米)

    @Column
    private Double leftPupilDiameter; // 左眼瞳孔直径 (毫米)

    @Column
    private Double rightPupilDiameter; // 右眼瞳孔直径 (毫米)

    @Column
    private Double error; // 校准误差 (像素)

    @Column(length = 500)
    private String notes; // 校准备注

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

    public LocalDateTime getCalibrationTime() {
        return calibrationTime;
    }

    public void setCalibrationTime(LocalDateTime calibrationTime) {
        this.calibrationTime = calibrationTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPointIndex() {
        return pointIndex;
    }

    public void setPointIndex(Integer pointIndex) {
        this.pointIndex = pointIndex;
    }

    public Double getScreenX() {
        return screenX;
    }

    public void setScreenX(Double screenX) {
        this.screenX = screenX;
    }

    public Double getScreenY() {
        return screenY;
    }

    public void setScreenY(Double screenY) {
        this.screenY = screenY;
    }

    public Double getGazeX() {
        return gazeX;
    }

    public void setGazeX(Double gazeX) {
        this.gazeX = gazeX;
    }

    public Double getGazeY() {
        return gazeY;
    }

    public void setGazeY(Double gazeY) {
        this.gazeY = gazeY;
    }

    public Double getGazeZ() {
        return gazeZ;
    }

    public void setGazeZ(Double gazeZ) {
        this.gazeZ = gazeZ;
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

    public Double getError() {
        return error;
    }

    public void setError(Double error) {
        this.error = error;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}