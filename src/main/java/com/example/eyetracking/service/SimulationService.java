package com.example.eyetracking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模拟模式服务 - 用于在没有眼动仪时用鼠标模拟注视点
 * 
 * 参考文档：
 * - 模拟模式实现参考了Tobii Pro Glasses 3的数据格式
 * - 坐标映射参考 https://developer.tobiipro.com/commonconcepts/calibration.html
 */
@Service
public class SimulationService {
    
    private static final Logger logger = LoggerFactory.getLogger(SimulationService.class);
    
    private final AtomicBoolean simulationEnabled = new AtomicBoolean(false);
    private volatile double mouseX = 0.5;
    private volatile double mouseY = 0.5;
    private volatile boolean mouseInsideWindow = false;
    
    private final CopyOnWriteArrayList<GazeListener> listeners = new CopyOnWriteArrayList<>();
    
    public interface GazeListener {
        void onGazeUpdate(double normalizedX, double normalizedY, long timestamp);
    }
    
    /**
     * 启用模拟模式
     */
    public void enableSimulation() {
        simulationEnabled.set(true);
        logger.info("模拟模式已启用 - 鼠标移动将作为注视点数据");
    }
    
    /**
     * 禁用模拟模式
     */
    public void disableSimulation() {
        simulationEnabled.set(false);
        logger.info("模拟模式已禁用");
    }
    
    /**
     * 检查模拟模式是否启用
     */
    public boolean isSimulationEnabled() {
        return simulationEnabled.get();
    }
    
    /**
     * 更新鼠标位置（由前端调用）
     * @param x 鼠标X坐标（像素）
     * @param y 鼠标Y坐标（像素）
     * @param windowWidth 窗口宽度（像素）
     * @param windowHeight 窗口高度（像素）
     */
    public void updateMousePosition(double x, double y, double windowWidth, double windowHeight) {
        if (!simulationEnabled.get()) {
            return;
        }
        
        if (windowWidth <= 0 || windowHeight <= 0) {
            return;
        }
        
        mouseX = Math.max(0, Math.min(1, x / windowWidth));
        mouseY = Math.max(0, Math.min(1, y / windowHeight));
        mouseInsideWindow = true;
        
        notifyListeners();
    }
    
    /**
     * 设置鼠标离开窗口
     */
    public void setMouseOutsideWindow() {
        mouseInsideWindow = false;
    }
    
    /**
     * 获取当前模拟的注视点数据
     */
    public SimulatedGazeData getCurrentGazeData() {
        SimulatedGazeData data = new SimulatedGazeData();
        data.setTimestamp(System.currentTimeMillis());
        data.setNormalizedX(mouseX);
        data.setNormalizedY(mouseY);
        data.setInsideWindow(mouseInsideWindow);
        data.setSimulationEnabled(simulationEnabled.get());
        
        double screenX = mouseX * 1920;
        double screenY = mouseY * 1080;
        data.setScreenX(screenX);
        data.setScreenY(screenY);
        
        data.setGaze3dValid(false);
        data.setGaze2dValid(mouseInsideWindow && simulationEnabled.get());
        
        return data;
    }
    
    /**
     * 添加注视点监听器
     */
    public void addGazeListener(GazeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
    
    /**
     * 移除注视点监听器
     */
    public void removeGazeListener(GazeListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyListeners() {
        long timestamp = System.currentTimeMillis();
        for (GazeListener listener : listeners) {
            try {
                listener.onGazeUpdate(mouseX, mouseY, timestamp);
            } catch (Exception e) {
                logger.warn("注视点监听器通知失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 获取校准用的模拟数据
     * 生成符合CalibrationService要求的9点校准数据
     */
    public CalibrationSimulationResult getCalibrationSimulation(int screenWidth, int screenHeight) {
        CalibrationSimulationResult result = new CalibrationSimulationResult();
        
        int[][] calibrationPoints = {
            {25, 25}, {50, 25}, {75, 25},
            {25, 50}, {50, 50}, {75, 50},
            {25, 75}, {50, 75}, {75, 75}
        };
        
        for (int i = 0; i < calibrationPoints.length; i++) {
            int[] point = calibrationPoints[i];
            double screenX = point[0] / 100.0 * screenWidth;
            double screenY = point[1] / 100.0 * screenHeight;
            
            SimulatedCalibrationPoint simulatedPoint = new SimulatedCalibrationPoint();
            simulatedPoint.setPointIndex(i + 1);
            simulatedPoint.setTargetScreenX(screenX);
            simulatedPoint.setTargetScreenY(screenY);
            simulatedPoint.setTargetNormalizedX(point[0] / 100.0);
            simulatedPoint.setTargetNormalizedY(point[1] / 100.0);
            simulatedPoint.setValidSamples(8);
            simulatedPoint.setSuccessful(true);
            
            double offsetX = (Math.random() - 0.5) * 30;
            double offsetY = (Math.random() - 0.5) * 30;
            simulatedPoint.setActualScreenX(screenX + offsetX);
            simulatedPoint.setActualScreenY(screenY + offsetY);
            simulatedPoint.setActualNormalizedX((screenX + offsetX) / screenWidth);
            simulatedPoint.setActualNormalizedY((screenY + offsetY) / screenHeight);
            
            result.addPoint(simulatedPoint);
        }
        
        result.setAverageError(calculateAverageError(result.getPoints(), screenWidth, screenHeight));
        result.setSuccessful(true);
        result.setMessage("模拟校准完成 - 平均误差: " + String.format("%.2f", result.getAverageError()) + "像素");
        
        return result;
    }
    
    private double calculateAverageError(java.util.List<SimulatedCalibrationPoint> points, int screenWidth, int screenHeight) {
        if (points.isEmpty()) {
            return Double.MAX_VALUE;
        }
        
        double totalError = 0;
        int count = 0;
        
        for (SimulatedCalibrationPoint point : points) {
            if (point.isSuccessful()) {
                double error = Math.sqrt(
                    Math.pow(point.getActualScreenX() - point.getTargetScreenX(), 2) +
                    Math.pow(point.getActualScreenY() - point.getTargetScreenY(), 2)
                );
                totalError += error;
                count++;
            }
        }
        
        return count > 0 ? totalError / count : Double.MAX_VALUE;
    }
    
    /**
     * 模拟的注视点数据类
     */
    public static class SimulatedGazeData {
        private long timestamp;
        private double normalizedX;
        private double normalizedY;
        private double screenX;
        private double screenY;
        private boolean insideWindow;
        private boolean simulationEnabled;
        private boolean gaze2dValid;
        private boolean gaze3dValid;
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public double getNormalizedX() { return normalizedX; }
        public void setNormalizedX(double normalizedX) { this.normalizedX = normalizedX; }
        public double getNormalizedY() { return normalizedY; }
        public void setNormalizedY(double normalizedY) { this.normalizedY = normalizedY; }
        public double getScreenX() { return screenX; }
        public void setScreenX(double screenX) { this.screenX = screenX; }
        public double getScreenY() { return screenY; }
        public void setScreenY(double screenY) { this.screenY = screenY; }
        public boolean isInsideWindow() { return insideWindow; }
        public void setInsideWindow(boolean insideWindow) { this.insideWindow = insideWindow; }
        public boolean isSimulationEnabled() { return simulationEnabled; }
        public void setSimulationEnabled(boolean simulationEnabled) { this.simulationEnabled = simulationEnabled; }
        public boolean isGaze2dValid() { return gaze2dValid; }
        public void setGaze2dValid(boolean gaze2dValid) { this.gaze2dValid = gaze2dValid; }
        public boolean isGaze3dValid() { return gaze3dValid; }
        public void setGaze3dValid(boolean gaze3dValid) { this.gaze3dValid = gaze3dValid; }
    }
    
    /**
     * 模拟校准点数据类
     */
    public static class SimulatedCalibrationPoint {
        private int pointIndex;
        private double targetScreenX;
        private double targetScreenY;
        private double targetNormalizedX;
        private double targetNormalizedY;
        private double actualScreenX;
        private double actualScreenY;
        private double actualNormalizedX;
        private double actualNormalizedY;
        private int validSamples;
        private boolean successful;
        
        public int getPointIndex() { return pointIndex; }
        public void setPointIndex(int pointIndex) { this.pointIndex = pointIndex; }
        public double getTargetScreenX() { return targetScreenX; }
        public void setTargetScreenX(double targetScreenX) { this.targetScreenX = targetScreenX; }
        public double getTargetScreenY() { return targetScreenY; }
        public void setTargetScreenY(double targetScreenY) { this.targetScreenY = targetScreenY; }
        public double getTargetNormalizedX() { return targetNormalizedX; }
        public void setTargetNormalizedX(double targetNormalizedX) { this.targetNormalizedX = targetNormalizedX; }
        public double getTargetNormalizedY() { return targetNormalizedY; }
        public void setTargetNormalizedY(double targetNormalizedY) { this.targetNormalizedY = targetNormalizedY; }
        public double getActualScreenX() { return actualScreenX; }
        public void setActualScreenX(double actualScreenX) { this.actualScreenX = actualScreenX; }
        public double getActualScreenY() { return actualScreenY; }
        public void setActualScreenY(double actualScreenY) { this.actualScreenY = actualScreenY; }
        public double getActualNormalizedX() { return actualNormalizedX; }
        public void setActualNormalizedX(double actualNormalizedX) { this.actualNormalizedX = actualNormalizedX; }
        public double getActualNormalizedY() { return actualNormalizedY; }
        public void setActualNormalizedY(double actualNormalizedY) { this.actualNormalizedY = actualNormalizedY; }
        public int getValidSamples() { return validSamples; }
        public void setValidSamples(int validSamples) { this.validSamples = validSamples; }
        public boolean isSuccessful() { return successful; }
        public void setSuccessful(boolean successful) { this.successful = successful; }
    }
    
    /**
     * 模拟校准结果类
     */
    public static class CalibrationSimulationResult {
        private java.util.List<SimulatedCalibrationPoint> points = new java.util.ArrayList<>();
        private double averageError;
        private boolean successful;
        private String message;
        
        public void addPoint(SimulatedCalibrationPoint point) {
            points.add(point);
        }
        
        public java.util.List<SimulatedCalibrationPoint> getPoints() { return points; }
        public double getAverageError() { return averageError; }
        public void setAverageError(double averageError) { this.averageError = averageError; }
        public boolean isSuccessful() { return successful; }
        public void setSuccessful(boolean successful) { this.successful = successful; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}