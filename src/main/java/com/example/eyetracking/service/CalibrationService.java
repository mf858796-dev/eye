package com.example.eyetracking.service;

import com.example.eyetracking.model.CalibrationData;
import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.CalibrationDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * 眼动仪校准服务
 */
@Service
public class CalibrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(CalibrationService.class);
    
    @Autowired
    private CalibrationDataRepository calibrationDataRepository;
    
    @Autowired
    private TobiiGlassesService tobiiGlassesService;
    
    // 校准点配置 (9点校准)
    private static final int[][] CALIBRATION_POINTS = {
        {25, 25},   // 左上角
        {50, 25},   // 上中
        {75, 25},   // 右上角
        {25, 50},   // 左中
        {50, 50},   // 中心
        {75, 50},   // 右中
        {25, 75},   // 左下角
        {50, 75},   // 下中
        {75, 75}    // 右下角
    };
    
    /**
     * 开始校准
     */
    public CalibrationResult startCalibration(User user, int screenWidth, int screenHeight) {
        logger.info("开始校准...");
        
        List<CalibrationData> calibrationPoints = new ArrayList<>();
        double totalError = 0;
        int successfulPoints = 0;
        
        // 遍历每个校准点
        for (int i = 0; i < CALIBRATION_POINTS.length; i++) {
            int[] point = CALIBRATION_POINTS[i];
            double screenX = (point[0] / 100.0) * screenWidth;
            double screenY = (point[1] / 100.0) * screenHeight;
            
            logger.info("校准点 {}/{}: ({}, {})", i+1, CALIBRATION_POINTS.length, screenX, screenY);
            
            try {
                // 等待用户注视
                Thread.sleep(1000);
                
                // 采集眼动数据
                Future<TobiiGlassesService.GazeDataSample> gazeDataFuture = tobiiGlassesService.getGazeData();
                TobiiGlassesService.GazeDataSample gazeData = gazeDataFuture.get();
                
                if (gazeData != null) {
                    // 计算误差 (简化计算，实际需要更复杂的映射)
                    double error = calculateError(screenX, screenY, gazeData);
                    
                    // 创建校准数据记录
                    CalibrationData calibrationData = new CalibrationData();
                    calibrationData.setUser(user);
                    calibrationData.setCalibrationTime(LocalDateTime.now());
                    calibrationData.setStatus("SUCCESS");
                    calibrationData.setPointIndex(i + 1);
                    calibrationData.setScreenX(screenX);
                    calibrationData.setScreenY(screenY);
                    calibrationData.setGazeX(gazeData.getX());
                    calibrationData.setGazeY(gazeData.getY());
                    calibrationData.setGazeZ(gazeData.getZ());
                    calibrationData.setLeftPupilDiameter(gazeData.getLeftPupilDiameter());
                    calibrationData.setRightPupilDiameter(gazeData.getRightPupilDiameter());
                    calibrationData.setError(error);
                    
                    calibrationDataRepository.save(calibrationData);
                    calibrationPoints.add(calibrationData);
                    
                    totalError += error;
                    successfulPoints++;
                    
                    logger.info("校准点 {} 成功，误差: {:.2f}px", i+1, error);
                } else {
                    logger.warn("校准点 {} 数据采集失败", i+1);
                }
                
            } catch (Exception e) {
                logger.error("校准点 {} 处理失败: {}", i+1, e.getMessage());
            }
            
            // 间隔时间
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 计算平均误差
        double averageError = successfulPoints > 0 ? totalError / successfulPoints : Double.MAX_VALUE;
        boolean calibrationSuccessful = averageError < 10.0; // 误差小于10像素视为成功
        
        logger.info("校准完成，平均误差: {:.2f}px, 成功点数: {}/{}", 
                averageError, successfulPoints, CALIBRATION_POINTS.length);
        
        CalibrationResult result = new CalibrationResult();
        result.setSuccessful(calibrationSuccessful);
        result.setAverageError(averageError);
        result.setSuccessfulPoints(successfulPoints);
        result.setTotalPoints(CALIBRATION_POINTS.length);
        result.setCalibrationPoints(calibrationPoints);
        
        return result;
    }
    
    /**
     * 计算校准误差
     */
    private double calculateError(double screenX, double screenY, TobiiGlassesService.GazeDataSample gazeData) {
        // 简化的误差计算
        // 实际需要根据屏幕尺寸和眼动数据进行映射
        double mappedX = gazeData.getX() * 1920; // 假设屏幕宽度1920
        double mappedY = gazeData.getY() * 1080; // 假设屏幕高度1080
        
        // 计算欧几里得距离
        return Math.sqrt(Math.pow(screenX - mappedX, 2) + Math.pow(screenY - mappedY, 2));
    }
    
    /**
     * 获取用户最新的校准数据
     */
    public List<CalibrationData> getLatestCalibrationData(User user) {
        return calibrationDataRepository.findByUserOrderByCalibrationTimeDesc(user);
    }
    
    /**
     * 验证校准状态
     */
    public boolean isCalibrationValid(User user) {
        List<CalibrationData> recentCalibrations = getLatestCalibrationData(user);
        if (recentCalibrations.isEmpty()) {
            return false;
        }
        
        // 检查校准是否在24小时内
        CalibrationData latestCalibration = recentCalibrations.get(0);
        LocalDateTime calibrationTime = latestCalibration.getCalibrationTime();
        LocalDateTime now = LocalDateTime.now();
        
        return calibrationTime.plusHours(24).isAfter(now);
    }
    
    /**
     * 校准结果类
     */
    public static class CalibrationResult {
        private boolean successful;
        private double averageError;
        private int successfulPoints;
        private int totalPoints;
        private List<CalibrationData> calibrationPoints;
        
        // getters and setters
        public boolean isSuccessful() { return successful; }
        public void setSuccessful(boolean successful) { this.successful = successful; }
        public double getAverageError() { return averageError; }
        public void setAverageError(double averageError) { this.averageError = averageError; }
        public int getSuccessfulPoints() { return successfulPoints; }
        public void setSuccessfulPoints(int successfulPoints) { this.successfulPoints = successfulPoints; }
        public int getTotalPoints() { return totalPoints; }
        public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
        public List<CalibrationData> getCalibrationPoints() { return calibrationPoints; }
        public void setCalibrationPoints(List<CalibrationData> calibrationPoints) { this.calibrationPoints = calibrationPoints; }
    }
}