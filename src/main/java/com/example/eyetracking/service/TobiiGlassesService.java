package com.example.eyetracking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Tobii Pro Glasses 3 眼动仪服务
 * 基于GitHub项目 https://github.com/mf858796-dev/bishe 的Python实现
 * 支持gaze3d数据和双眼瞳孔直径
 */
@Service
public class TobiiGlassesService {
    
    private static final Logger logger = LoggerFactory.getLogger(TobiiGlassesService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    @Value("${tobii.glasses.base-url:http://192.168.71.50:8080}")
    private String configuredGlassesAddress;

    private String glassesAddress;
    private volatile boolean connected;
    
    /**
     * 搜索并连接眼动仪
     * @return 连接是否成功
     */
    public boolean connect() {
        return connect(configuredGlassesAddress);
    }

    public boolean connect(String baseUrl) {
        try {
            logger.info("[1/4] 正在搜索Tobii Pro Glasses 3设备...");
            
            glassesAddress = normalizeBaseUrl(baseUrl);
            
            // 测试连接
            if (testConnection()) {
                connected = true;
                logger.info("✅ 设备连接成功");
                return true;
            } else {
                logger.error("❌ 未找到设备或连接失败");
                return false;
            }
            
        } catch (Exception e) {
            logger.error("❌ 连接错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 测试连接
     */
    private boolean testConnection() throws IOException {
        try {
            URL url = new URL(glassesAddress + "/api/system/version");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            return responseCode == 200;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取设备信息
     */
    public GlassesInfo getDeviceInfo() {
        try {
            logger.info("[2/4] 获取设备信息...");
            
            // 获取序列号
            String serial = getSerialNumber();
            // 获取固件版本
            String version = getFirmwareVersion();
            // 获取电池电量
            int battery = getBatteryLevel();
            
            GlassesInfo info = new GlassesInfo();
            info.setSerialNumber(serial);
            info.setFirmwareVersion(version);
            info.setBatteryLevel(battery);
            
            logger.info("✅ 序列号: {}", serial);
            logger.info("✅ 固件版本: {}", version);
            logger.info("✅ 电池电量: {}%", battery);
            
            return info;
            
        } catch (Exception e) {
            logger.error("❌ 获取设备信息失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取序列号
     */
    private String getSerialNumber() throws IOException {
        URL url = new URL(currentGlassesAddress() + "/api/system/serial");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        if (connection.getResponseCode() == 200) {
            JsonNode response = objectMapper.readTree(connection.getInputStream());
            return response.get("serial").asText();
        }
        return "Unknown";
    }
    
    /**
     * 获取固件版本
     */
    private String getFirmwareVersion() throws IOException {
        URL url = new URL(currentGlassesAddress() + "/api/system/version");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        if (connection.getResponseCode() == 200) {
            JsonNode response = objectMapper.readTree(connection.getInputStream());
            return response.get("version").asText();
        }
        return "Unknown";
    }
    
    /**
     * 获取电池电量
     */
    private int getBatteryLevel() throws IOException {
        URL url = new URL(currentGlassesAddress() + "/api/system/battery");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        if (connection.getResponseCode() == 200) {
            JsonNode response = objectMapper.readTree(connection.getInputStream());
            return response.get("level").asInt();
        }
        return 0;
    }
    
    /**
     * 测试视频流
     */
    public boolean testVideoStream() {
        try {
            logger.info("[3/4] 测试视频流...");
            
            // 测试RTSP流连接
            // 这里简化实现，实际需要使用RTSP客户端
            URL url = new URL(currentGlassesAddress() + "/api/stream/rtsp");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            if (connection.getResponseCode() == 200) {
                logger.info("✅ RTSP流连接成功");
                return true;
            } else {
                logger.error("❌ RTSP流连接失败");
                return false;
            }
            
        } catch (Exception e) {
            logger.error("❌ 视频流测试失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取眼动数据 - 支持gaze3d格式
     * 基于Tobii Pro Glasses 3 Developer Guide v1.6
     */
    public Future<GazeDataSample> getGazeData() {
        return executorService.submit(() -> {
            try {
                URL url = new URL(currentGlassesAddress() + "/api/gaze");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                
                if (connection.getResponseCode() == 200) {
                    JsonNode response = objectMapper.readTree(connection.getInputStream());
                    
                    GazeDataSample sample = new GazeDataSample();
                    
                    // 获取timestamp
                    if (response.has("timestamp")) {
                        sample.setTimestamp(response.get("timestamp").asLong());
                    } else {
                        sample.setTimestamp(System.currentTimeMillis());
                    }
                    
                    // 获取data对象
                    JsonNode data = response.get("data");
                    if (data == null) {
                        data = response;
                    }
                    
                    // 获取gaze2d数据 (2D归一化坐标)
                    if (data.has("gaze2d")) {
                        JsonNode gaze2d = data.get("gaze2d");
                        sample.setGaze2dX(readVectorValue(gaze2d, 0, "x"));
                        sample.setGaze2dY(readVectorValue(gaze2d, 1, "y"));
                        sample.setGaze2dValid(true);
                    }
                    
                    // 获取gaze3d数据 (3D坐标，单位：毫米)
                    if (data.has("gaze3d")) {
                        JsonNode gaze3d = data.get("gaze3d");
                        sample.setX(readVectorValue(gaze3d, 0, "x"));
                        sample.setY(readVectorValue(gaze3d, 1, "y"));
                        sample.setZ(readVectorValue(gaze3d, 2, "z"));
                        sample.setGaze3dValid(true);
                    }
                    
                    // 获取左眼数据
                    if (data.has("eyeleft")) {
                        JsonNode eyeLeft = data.get("eyeleft");
                        
                        // 左眼瞳孔直径 (mm)
                        if (eyeLeft.has("pupildiameter")) {
                            sample.setLeftPupilDiameter(eyeLeft.get("pupildiameter").asDouble());
                        }
                        
                        // 左眼视线起点
                        if (eyeLeft.has("gazeorigin")) {
                            JsonNode origin = eyeLeft.get("gazeorigin");
                            sample.setGazeOriginX(readVectorValue(origin, 0, "x"));
                            sample.setGazeOriginY(readVectorValue(origin, 1, "y"));
                            sample.setGazeOriginZ(readVectorValue(origin, 2, "z"));
                        }
                        
                        // 左眼视线方向向量
                        if (eyeLeft.has("gazedirection")) {
                            JsonNode direction = eyeLeft.get("gazedirection");
                            sample.setGazeDirectionX(readVectorValue(direction, 0, "x"));
                            sample.setGazeDirectionY(readVectorValue(direction, 1, "y"));
                            sample.setGazeDirectionZ(readVectorValue(direction, 2, "z"));
                        }
                    }
                    
                    // 获取右眼数据
                    if (data.has("eyeright")) {
                        JsonNode eyeRight = data.get("eyeright");
                        
                        // 右眼瞳孔直径 (mm)
                        if (eyeRight.has("pupildiameter")) {
                            sample.setRightPupilDiameter(eyeRight.get("pupildiameter").asDouble());
                        }
                    }
                    
                    return sample;
                }
                
            } catch (Exception e) {
                logger.error("获取眼动数据失败: {}", e.getMessage());
            }
            return null;
        });
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        connected = false;
        glassesAddress = null;
        logger.info("设备已断开连接");
    }
    
    /**
     * 设备是否已连接
     */
    public boolean isConnected() {
        return connected;
    }

    public String getCurrentGlassesAddress() {
        return currentGlassesAddress();
    }

    private String currentGlassesAddress() {
        if (glassesAddress == null || glassesAddress.trim().isEmpty()) {
            glassesAddress = normalizeBaseUrl(configuredGlassesAddress);
        }
        return glassesAddress;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl == null || baseUrl.trim().isEmpty()
                ? "http://192.168.71.50:8080"
                : baseUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private double readVectorValue(JsonNode node, int index, String key) {
        if (node == null) {
            return 0.0;
        }
        if (node.isArray() && node.size() > index) {
            return node.get(index).asDouble();
        }
        if (node.isObject() && node.has(key)) {
            return node.get(key).asDouble();
        }
        return 0.0;
    }
    
    /**
     * 眼动仪信息类
     */
    public static class GlassesInfo {
        private String serialNumber;
        private String firmwareVersion;
        private int batteryLevel;
        
        // getters and setters
        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
        public String getFirmwareVersion() { return firmwareVersion; }
        public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
        public int getBatteryLevel() { return batteryLevel; }
        public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }
    }
    
    /**
     * 眼动数据样本类 - 支持gaze3d格式
     * 包含：gaze2d/gaze3d坐标、gaze_origin、gaze_direction、双眼瞳孔直径
     */
    public static class GazeDataSample {
        private long timestamp;
        
        // 2D/3D坐标
        private double x;
        private double y;
        private double z;

        private double gaze2dX;
        private double gaze2dY;
        private boolean gaze2dValid;
        private boolean gaze3dValid;
        
        // 视线起点 (gaze origin) - 毫米
        private double gazeOriginX;
        private double gazeOriginY;
        private double gazeOriginZ;
        
        // 视线方向向量 (gaze direction) - 单位向量
        private double gazeDirectionX;
        private double gazeDirectionY;
        private double gazeDirectionZ;
        
        // 双眼瞳孔直径 - 毫米
        private double leftPupilDiameter;
        private double rightPupilDiameter;
        
        // getters and setters
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        
        public double getZ() { return z; }
        public void setZ(double z) { this.z = z; }

        public double getGaze2dX() { return gaze2dX; }
        public void setGaze2dX(double gaze2dX) { this.gaze2dX = gaze2dX; }

        public double getGaze2dY() { return gaze2dY; }
        public void setGaze2dY(double gaze2dY) { this.gaze2dY = gaze2dY; }

        public boolean isGaze2dValid() { return gaze2dValid; }
        public void setGaze2dValid(boolean gaze2dValid) { this.gaze2dValid = gaze2dValid; }

        public boolean isGaze3dValid() { return gaze3dValid; }
        public void setGaze3dValid(boolean gaze3dValid) { this.gaze3dValid = gaze3dValid; }
        
        public double getGazeOriginX() { return gazeOriginX; }
        public void setGazeOriginX(double gazeOriginX) { this.gazeOriginX = gazeOriginX; }
        
        public double getGazeOriginY() { return gazeOriginY; }
        public void setGazeOriginY(double gazeOriginY) { this.gazeOriginY = gazeOriginY; }
        
        public double getGazeOriginZ() { return gazeOriginZ; }
        public void setGazeOriginZ(double gazeOriginZ) { this.gazeOriginZ = gazeOriginZ; }
        
        public double getGazeDirectionX() { return gazeDirectionX; }
        public void setGazeDirectionX(double gazeDirectionX) { this.gazeDirectionX = gazeDirectionX; }
        
        public double getGazeDirectionY() { return gazeDirectionY; }
        public void setGazeDirectionY(double gazeDirectionY) { this.gazeDirectionY = gazeDirectionY; }
        
        public double getGazeDirectionZ() { return gazeDirectionZ; }
        public void setGazeDirectionZ(double gazeDirectionZ) { this.gazeDirectionZ = gazeDirectionZ; }
        
        public double getLeftPupilDiameter() { return leftPupilDiameter; }
        public void setLeftPupilDiameter(double leftPupilDiameter) { this.leftPupilDiameter = leftPupilDiameter; }
        
        public double getRightPupilDiameter() { return rightPupilDiameter; }
        public void setRightPupilDiameter(double rightPupilDiameter) { this.rightPupilDiameter = rightPupilDiameter; }
    }
}
