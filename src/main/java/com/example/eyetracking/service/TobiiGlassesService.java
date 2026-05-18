package com.example.eyetracking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tobii Pro Glasses 3 眼动仪服务
 *
 * 参考设计模式：
 * - tobiisdk4j (https://github.com/jeangabin/tobiisdk4j)
 * - Tobii Pro Glasses 3 API
 * - 校准原理: https://developer.tobiipro.com/commonconcepts/calibration.html
 *
 * 核心特性：
 * 1. 设备发现（多播扫描）
 * 2. 事件驱动架构（连接状态、数据接收）
 * 3. 校准流程管理
 * 4. 实时数据流控制
 * 5. 资源管理与清理
 */
@Service
public class TobiiGlassesService {

    private static final Logger logger = LoggerFactory.getLogger(TobiiGlassesService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 多播发现配置
    private static final String MULTICAST_ADDRESS = "239.255.42.42";
    private static final int MULTICAST_PORT = 12345;
    private static final int DISCOVERY_TIMEOUT_MS = 3000;

    // 数据流配置
    private static final int STREAM_BUFFER_SIZE = 100;
    private static final long RECONNECT_DELAY_MS = 5000;
    private static final String DEVICE_TYPE_GLASSES = "glasses";
    private static final String DEVICE_TYPE_SCREEN = "screen";

    @Value("${tobii.glasses.base-url:http://192.168.71.50:8080}")
    private String configuredGlassesAddress;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private String glassesAddress;
    private String currentDeviceType = DEVICE_TYPE_GLASSES;
    private volatile boolean connected;
    private volatile long lastHeartbeat;
    private volatile ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;

    // 事件监听器
    private final CopyOnWriteArrayList<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<GazeDataListener> gazeDataListeners = new CopyOnWriteArrayList<>();

    // 数据流控制
    private final AtomicBoolean streaming = new AtomicBoolean(false);
    private final BlockingQueue<GazeDataSample> dataBuffer = new ArrayBlockingQueue<>(STREAM_BUFFER_SIZE);
    private Future<?> streamingTask;

    /**
     * 连接状态枚举
     */
    public enum ConnectionStatus {
        DISCONNECTED("未连接"),
        CONNECTING("连接中"),
        CONNECTED("已连接"),
        ERROR("连接错误"),
        TIMEOUT("连接超时");

        private final String description;
        ConnectionStatus(String description) {
            this.description = description;
        }
        public String getDescription() {
            return description;
        }
    }

    /**
     * 连接监听器接口
     */
    public interface ConnectionListener {
        void onConnectionStatusChanged(ConnectionStatus status, String message);
        void onDeviceDiscovered(DiscoveredDevice device);
    }

    /**
     * 眼动数据监听器接口
     */
    public interface GazeDataListener {
        void onGazeDataReceived(GazeDataSample sample);
        void onStreamError(String error);
    }

    /**
     * 设备发现结果类
     */
    public static class DiscoveredDevice {
        private String name;
        private String address;
        private String type;
        private String serialNumber;
        private int rssi;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
        public int getRssi() { return rssi; }
        public void setRssi(int rssi) { this.rssi = rssi; }
    }

    /**
     * 连接诊断信息类
     */
    public static class DiagnosticInfo {
        private String glassesAddress;
        private String configuredAddress;
        private String issue;
        private String resolution;
        private int httpStatus;
        private long responseTime;

        public String getGlassesAddress() { return glassesAddress; }
        public void setGlassesAddress(String glassesAddress) { this.glassesAddress = glassesAddress; }
        public String getConfiguredAddress() { return configuredAddress; }
        public void setConfiguredAddress(String configuredAddress) { this.configuredAddress = configuredAddress; }
        public String getIssue() { return issue; }
        public void setIssue(String issue) { this.issue = issue; }
        public String getResolution() { return resolution; }
        public void setResolution(String resolution) { this.resolution = resolution; }
        public int getHttpStatus() { return httpStatus; }
        public void setHttpStatus(int httpStatus) { this.httpStatus = httpStatus; }
        public long getResponseTime() { return responseTime; }
        public void setResponseTime(long responseTime) { this.responseTime = responseTime; }
    }

    /**
     * 异步发现设备 - 基于多播扫描
     * 参考 tobiisdk4j 的设备发现机制
     */
    public Future<List<DiscoveredDevice>> discoverDevicesAsync() {
        return executorService.submit(() -> {
            List<DiscoveredDevice> devices = new ArrayList<>();
            Set<String> foundAddresses = new HashSet<>();

            // 1. 尝试配置的地址
            if (configuredGlassesAddress != null && !configuredGlassesAddress.isEmpty()) {
                DiscoveredDevice device = testAndCreateDevice(configuredGlassesAddress);
                if (device != null) {
                    devices.add(device);
                    foundAddresses.add(configuredGlassesAddress);
                }
            }

            // 2. 常用地址扫描
            String[] commonAddresses = {
                "http://192.168.71.50:8080",
                "http://192.168.1.100:8080",
                "http://192.168.0.100:8080",
                "http://localhost:8080"
            };

            for (String address : commonAddresses) {
                if (!foundAddresses.contains(address)) {
                    DiscoveredDevice device = testAndCreateDevice(address);
                    if (device != null) {
                        devices.add(device);
                        foundAddresses.add(address);
                        notifyDeviceDiscovered(device);
                    }
                }
            }

            return devices;
        });
    }

    private DiscoveredDevice testAndCreateDevice(String address) {
        try {
            String baseUrl = normalizeBaseUrl(address);
            URL url = new URL(baseUrl + "/api/system/serial");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);

            if (connection.getResponseCode() == 200) {
                JsonNode response = objectMapper.readTree(connection.getInputStream());
                String serial = response.has("serial") ? response.get("serial").asText() : "Unknown";

                DiscoveredDevice device = new DiscoveredDevice();
                device.setName("Tobii Pro Glasses 3");
                device.setAddress(baseUrl);
                device.setType("Glasses3");
                device.setSerialNumber(serial);
                return device;
            }
        } catch (Exception e) {
            // 忽略单个地址的扫描失败
        }
        return null;
    }

    /**
     * 添加连接监听器
     */
    public void addConnectionListener(ConnectionListener listener) {
        if (listener != null) {
            connectionListeners.add(listener);
        }
    }

    /**
     * 移除连接监听器
     */
    public void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    /**
     * 添加眼动数据监听器
     */
    public void addGazeDataListener(GazeDataListener listener) {
        if (listener != null) {
            gazeDataListeners.add(listener);
        }
    }

    /**
     * 移除眼动数据监听器
     */
    public void removeGazeDataListener(GazeDataListener listener) {
        gazeDataListeners.remove(listener);
    }

    /**
     * 搜索并连接眼动仪
     */
    public boolean connect() {
        return connect(configuredGlassesAddress, currentDeviceType);
    }

    public boolean connect(String baseUrl) {
        return connect(baseUrl, currentDeviceType);
    }

    public boolean connect(String baseUrl, String deviceType) {
        try {
            updateConnectionStatus(ConnectionStatus.CONNECTING, "正在连接设备...");

            glassesAddress = normalizeBaseUrl(baseUrl);
            currentDeviceType = normalizeDeviceType(deviceType);

            if (testConnection()) {
                connected = true;
                lastHeartbeat = System.currentTimeMillis();
                updateConnectionStatus(ConnectionStatus.CONNECTED, "设备连接成功");
                return true;
            } else {
                updateConnectionStatus(ConnectionStatus.ERROR, "未找到设备或连接失败");
                return false;
            }

        } catch (Exception e) {
            updateConnectionStatus(ConnectionStatus.ERROR, "连接错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 启动眼动数据流
     */
    public void startStreaming() {
        if (!connected) {
            logger.warn("设备未连接，无法启动数据流");
            return;
        }

        if (streaming.compareAndSet(false, true)) {
            logger.info("启动眼动数据流...");
            streamingTask = executorService.submit(this::streamGazeData);
        }
    }

    /**
     * 停止眼动数据流
     */
    public void stopStreaming() {
        if (streaming.compareAndSet(true, false)) {
            logger.info("停止眼动数据流...");
            if (streamingTask != null) {
                streamingTask.cancel(true);
            }
        }
    }

    /**
     * 数据流主循环
     */
    private void streamGazeData() {
        while (streaming.get() && connected) {
            try {
                Future<GazeDataSample> future = getGazeDataInternal();
                GazeDataSample sample = future.get(3000, TimeUnit.MILLISECONDS);

                if (sample != null) {
                    // 添加到缓冲区
                    if (!dataBuffer.offer(sample, 100, TimeUnit.MILLISECONDS)) {
                        logger.warn("数据缓冲区已满，丢弃旧数据");
                        dataBuffer.poll();
                        dataBuffer.offer(sample);
                    }

                    // 通知所有监听器
                    notifyGazeDataReceived(sample);
                }

                Thread.yield();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("数据流错误: {}", e.getMessage());
                notifyStreamError(e.getMessage());

                // 尝试重新连接
                if (!connected) {
                    scheduleReconnect();
                }
            }
        }
    }

    /**
     * 定时重新连接
     */
    private void scheduleReconnect() {
        scheduler.schedule(() -> {
            if (!connected && connectionStatus != ConnectionStatus.CONNECTING) {
                logger.info("尝试重新连接...");
                connect();
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取眼动数据（内部方法）
     */
    private Future<GazeDataSample> getGazeDataInternal() {
        return executorService.submit(() -> {
            try {
                HttpURLConnection connection = openFirstAvailableConnection(
                        new String[]{"/api/gaze", "/gaze", "/api/stream/gaze"}
                );
                if (connection != null && connection.getResponseCode() == 200) {
                    updateHeartbeat();
                    JsonNode response = objectMapper.readTree(connection.getInputStream());
                    return parseGazeData(response);
                }

            } catch (Exception e) {
                logger.debug("获取眼动数据失败: {}", e.getMessage());
            }
            return null;
        });
    }

    /**
     * 解析眼动数据
     */
    private GazeDataSample parseGazeData(JsonNode response) {
        GazeDataSample sample = new GazeDataSample();

        // 获取timestamp
        if (response.has("timestamp")) {
            sample.setTimestamp(response.get("timestamp").asLong());
        } else {
            sample.setTimestamp(System.currentTimeMillis());
        }

        // 获取data对象
        JsonNode data = response.has("data") ? response.get("data") : response;

        parseDisplayGaze(data, sample);
        parseGaze3d(data, sample);

        JsonNode eyeLeft = firstPresent(data, "eyeleft", "left_eye", "leftEye");
        JsonNode eyeRight = firstPresent(data, "eyeright", "right_eye", "rightEye");
        if (eyeLeft != null) {
            parseEyeData(eyeLeft, sample, true);
        }
        if (eyeRight != null) {
            parseEyeData(eyeRight, sample, false);
        }

        parseFlatEyeFields(data, sample);

        return sample;
    }

    private void parseDisplayGaze(JsonNode data, GazeDataSample sample) {
        JsonNode combined = firstPresent(data,
                "gaze2d",
                "gaze_2d",
                "gazePoint",
                "gaze_point",
                "gaze_point_on_display_area",
                "gazePointOnDisplayArea"
        );
        double[] gaze2d = readNormalizedPair(combined);
        if (gaze2d == null) {
            double[] left = readNormalizedPair(firstPresent(data,
                    "left_gaze_point_on_display_area",
                    "leftGazePointOnDisplayArea"
            ));
            double[] right = readNormalizedPair(firstPresent(data,
                    "right_gaze_point_on_display_area",
                    "rightGazePointOnDisplayArea"
            ));
            JsonNode leftEye = firstPresent(data, "eyeleft", "left_eye", "leftEye");
            JsonNode rightEye = firstPresent(data, "eyeright", "right_eye", "rightEye");
            if (left == null && leftEye != null) {
                left = readNormalizedPair(firstPresent(leftEye,
                        "gaze_point_on_display_area",
                        "gazePointOnDisplayArea",
                        "gaze2d"
                ));
            }
            if (right == null && rightEye != null) {
                right = readNormalizedPair(firstPresent(rightEye,
                        "gaze_point_on_display_area",
                        "gazePointOnDisplayArea",
                        "gaze2d"
                ));
            }
            gaze2d = averagePairs(left, right);
        }
        if (gaze2d != null) {
            sample.setGaze2dX(gaze2d[0]);
            sample.setGaze2dY(gaze2d[1]);
            sample.setGaze2dValid(true);
        }
    }

    public Future<GazeDataSample> getGazeData() {
        return getGazeDataInternal();
    }

    public boolean testVideoStream() {
        if (isScreenDevice()) {
            return true;
        }
        try {
            HttpURLConnection connection = openFirstAvailableConnection(
                    new String[]{"/api/stream/rtsp", "/api/video", "/api/stream/video"}
            );
            return connection != null && connection.getResponseCode() == 200;
        } catch (Exception e) {
            logger.debug("视频流测试失败: {}", e.getMessage());
            return false;
        }
    }

    private void parseGaze3d(JsonNode data, GazeDataSample sample) {
        JsonNode combined = firstPresent(data,
                "gaze3d",
                "gaze_3d",
                "gaze_point_in_user_coordinate_system",
                "gazePointInUserCoordinateSystem"
        );
        double[] gaze3d = readVector3(combined);
        if (gaze3d == null) {
            double[] left = readVector3(firstPresent(data,
                    "left_gaze_point_in_user_coordinate_system",
                    "leftGazePointInUserCoordinateSystem"
            ));
            double[] right = readVector3(firstPresent(data,
                    "right_gaze_point_in_user_coordinate_system",
                    "rightGazePointInUserCoordinateSystem"
            ));
            JsonNode leftEye = firstPresent(data, "eyeleft", "left_eye", "leftEye");
            JsonNode rightEye = firstPresent(data, "eyeright", "right_eye", "rightEye");
            if (left == null && leftEye != null) {
                left = readVector3(firstPresent(leftEye,
                        "gaze_point_in_user_coordinate_system",
                        "gazePointInUserCoordinateSystem",
                        "gaze3d"
                ));
            }
            if (right == null && rightEye != null) {
                right = readVector3(firstPresent(rightEye,
                        "gaze_point_in_user_coordinate_system",
                        "gazePointInUserCoordinateSystem",
                        "gaze3d"
                ));
            }
            gaze3d = averageVectors(left, right);
        }
        if (gaze3d != null) {
            sample.setX(gaze3d[0]);
            sample.setY(gaze3d[1]);
            sample.setZ(gaze3d[2]);
            sample.setGaze3dValid(true);
        }
    }

    private void parseEyeData(JsonNode eyeNode, GazeDataSample sample, boolean isLeft) {
        // 瞳孔直径
        JsonNode pupilNode = firstPresent(eyeNode, "pupildiameter", "pupil_diameter", "pupilDiameter");
        if (pupilNode != null && pupilNode.isNumber()) {
            double diameter = pupilNode.asDouble();
            if (isLeft) {
                sample.setLeftPupilDiameter(diameter);
            } else {
                sample.setRightPupilDiameter(diameter);
            }
        }

        // 视线起点
        JsonNode origin = firstPresent(eyeNode,
                "gazeorigin",
                "gaze_origin",
                "gaze_origin_in_trackbox_coordinate_system",
                "gaze_origin_in_user_coordinate_system",
                "gazeOrigin"
        );
        if (origin != null) {
            sample.setGazeOriginX(readVectorValue(origin, 0, "x"));
            sample.setGazeOriginY(readVectorValue(origin, 1, "y"));
            sample.setGazeOriginZ(readVectorValue(origin, 2, "z"));
        }

        // 视线方向向量
        JsonNode direction = firstPresent(eyeNode, "gazedirection", "gaze_direction", "gazeDirection");
        if (direction != null) {
            sample.setGazeDirectionX(readVectorValue(direction, 0, "x"));
            sample.setGazeDirectionY(readVectorValue(direction, 1, "y"));
            sample.setGazeDirectionZ(readVectorValue(direction, 2, "z"));
        }
    }

    private void parseFlatEyeFields(JsonNode data, GazeDataSample sample) {
        Double leftPupil = readDouble(firstPresent(data, "left_pupil_diameter", "leftPupilDiameter"));
        Double rightPupil = readDouble(firstPresent(data, "right_pupil_diameter", "rightPupilDiameter"));
        if (leftPupil != null) {
            sample.setLeftPupilDiameter(leftPupil);
        }
        if (rightPupil != null) {
            sample.setRightPupilDiameter(rightPupil);
        }

        JsonNode leftOrigin = firstPresent(data,
                "left_gaze_origin_in_trackbox_coordinate_system",
                "left_gaze_origin_in_user_coordinate_system",
                "leftGazeOriginInUserCoordinateSystem"
        );
        if (leftOrigin != null) {
            sample.setGazeOriginX(readVectorValue(leftOrigin, 0, "x"));
            sample.setGazeOriginY(readVectorValue(leftOrigin, 1, "y"));
            sample.setGazeOriginZ(readVectorValue(leftOrigin, 2, "z"));
        }
    }

    /**
     * 获取缓冲的眼动数据
     */
    public GazeDataSample getBufferedGazeData() {
        return dataBuffer.poll();
    }

    /**
     * 获取缓冲数据数量
     */
    public int getBufferSize() {
        return dataBuffer.size();
    }

    /**
     * 诊断连接问题
     */
    public DiagnosticInfo diagnose() {
        DiagnosticInfo info = new DiagnosticInfo();
        info.setGlassesAddress(glassesAddress);
        info.setConfiguredAddress(configuredGlassesAddress);

        if (glassesAddress == null) {
            info.setIssue("地址未设置");
            return info;
        }

        try {
            long startTime = System.currentTimeMillis();
            HttpURLConnection connection = openFirstAvailableConnection(
                    isScreenDevice()
                            ? new String[]{"/api/status", "/status", "/api/system/version", "/api/gaze", "/gaze"}
                            : new String[]{"/api/system/version", "/api/status", "/api/gaze"}
            );

            int responseCode = connection == null ? -1 : connection.getResponseCode();
            info.setResponseTime(System.currentTimeMillis() - startTime);
            info.setHttpStatus(responseCode);

            if (responseCode == 200) {
                info.setIssue(null);
                info.setResolution("连接正常");
            } else {
                info.setIssue("HTTP错误: " + responseCode);
                info.setResolution("检查设备是否正常工作");
            }

        } catch (IOException e) {
            info.setIssue("连接失败: " + e.getMessage());
            info.setResolution("检查: 1)设备IP是否正确 2)设备网络是否正常 3)防火墙设置");
        }

        return info;
    }

    /**
     * 测试连接
     */
    private boolean testConnection() throws IOException {
        try {
            HttpURLConnection connection = openFirstAvailableConnection(
                    isScreenDevice()
                            ? new String[]{"/api/status", "/status", "/api/system/version", "/api/gaze", "/gaze"}
                            : new String[]{"/api/system/version", "/api/status", "/api/gaze"}
            );

            int responseCode = connection == null ? -1 : connection.getResponseCode();
            if (responseCode == 200) {
                updateHeartbeat();
                return true;
            }
            return false;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 更新连接状态并通知监听器
     */
    private void updateConnectionStatus(ConnectionStatus status, String message) {
        this.connectionStatus = status;

        for (ConnectionListener listener : connectionListeners) {
            try {
                listener.onConnectionStatusChanged(status, message);
            } catch (Exception e) {
                logger.warn("连接监听器通知失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 通知设备发现
     */
    private void notifyDeviceDiscovered(DiscoveredDevice device) {
        for (ConnectionListener listener : connectionListeners) {
            try {
                listener.onDeviceDiscovered(device);
            } catch (Exception e) {
                logger.warn("设备发现监听器通知失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 通知眼动数据接收
     */
    private void notifyGazeDataReceived(GazeDataSample sample) {
        for (GazeDataListener listener : gazeDataListeners) {
            try {
                listener.onGazeDataReceived(sample);
            } catch (Exception e) {
                logger.warn("眼动数据监听器通知失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 通知流错误
     */
    private void notifyStreamError(String error) {
        for (GazeDataListener listener : gazeDataListeners) {
            try {
                listener.onStreamError(error);
            } catch (Exception e) {
                logger.warn("流错误监听器通知失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取连接状态详情
     */
    public ConnectionStatus getConnectionStatus() {
        if (connected && connectionStatus == ConnectionStatus.CONNECTED) {
            if (System.currentTimeMillis() - lastHeartbeat > 30000) {
                return ConnectionStatus.TIMEOUT;
            }
        }
        return connectionStatus;
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * 获取设备信息
     */
    public GlassesInfo getDeviceInfo() {
        try {
            GlassesInfo info = new GlassesInfo();
            info.setSerialNumber(getSerialNumber());
            info.setFirmwareVersion(getFirmwareVersion());
            info.setBatteryLevel(getBatteryLevel());
            return info;
        } catch (Exception e) {
            logger.error("获取设备信息失败: {}", e.getMessage());
            return null;
        }
    }

    private String getSerialNumber() throws IOException {
        URL url = new URL(currentGlassesAddress() + "/api/system/serial");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        if (connection.getResponseCode() == 200) {
            JsonNode response = objectMapper.readTree(connection.getInputStream());
            return response.has("serial") ? response.get("serial").asText() : "Unknown";
        }
        return "Unknown";
    }

    private String getFirmwareVersion() throws IOException {
        URL url = new URL(currentGlassesAddress() + "/api/system/version");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        if (connection.getResponseCode() == 200) {
            JsonNode response = objectMapper.readTree(connection.getInputStream());
            return response.has("version") ? response.get("version").asText() : "Unknown";
        }
        return "Unknown";
    }

    private int getBatteryLevel() throws IOException {
        URL url = new URL(currentGlassesAddress() + "/api/system/battery");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        if (connection.getResponseCode() == 200) {
            JsonNode response = objectMapper.readTree(connection.getInputStream());
            return response.has("level") ? response.get("level").asInt() : 0;
        }
        return 0;
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        stopStreaming();
        connected = false;
        glassesAddress = null;
        updateConnectionStatus(ConnectionStatus.DISCONNECTED, "设备已断开连接");
    }

    /**
     * 资源清理
     */
    @PreDestroy
    public void cleanup() {
        logger.info("清理眼动仪服务资源...");
        stopStreaming();
        disconnect();
        executorService.shutdownNow();
        scheduler.shutdownNow();
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isStreaming() {
        return streaming.get();
    }

    public String getCurrentGlassesAddress() {
        return currentGlassesAddress();
    }

    public String getCurrentDeviceType() {
        return currentDeviceType;
    }

    public String getCurrentDeviceLabel() {
        return isScreenDevice() ? "笔记本屏幕式眼动仪" : "Tobii Pro Glasses 3";
    }

    private String currentGlassesAddress() {
        if (glassesAddress == null || glassesAddress.trim().isEmpty()) {
            glassesAddress = normalizeBaseUrl(configuredGlassesAddress);
        }
        return glassesAddress;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "http://192.168.71.50:8080";
        }
        String url = baseUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String normalizeDeviceType(String deviceType) {
        return DEVICE_TYPE_SCREEN.equalsIgnoreCase(deviceType) ? DEVICE_TYPE_SCREEN : DEVICE_TYPE_GLASSES;
    }

    private boolean isScreenDevice() {
        return DEVICE_TYPE_SCREEN.equals(currentDeviceType);
    }

    private HttpURLConnection openFirstAvailableConnection(String[] paths) {
        for (String path : paths) {
            try {
                URL url = new URL(currentGlassesAddress() + path);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                if (connection.getResponseCode() == 200) {
                    return connection;
                }
            } catch (Exception ignored) {
                // Try the next compatible endpoint.
            }
        }
        return null;
    }

    private double readVectorValue(JsonNode node, int index, String key) {
        if (node == null) return 0.0;
        if (node.isArray() && node.size() > index) {
            return node.get(index).asDouble();
        }
        if (node.isObject() && node.has(key)) {
            return node.get(key).asDouble();
        }
        return 0.0;
    }

    private JsonNode firstPresent(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            if (node.has(name) && !node.get(name).isNull() && !node.get(name).isMissingNode()) {
                return node.get(name);
            }
        }
        return null;
    }

    private Double readDouble(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isObject()) {
            JsonNode value = firstPresent(node, "value", "diameter");
            return readDouble(value);
        }
        return null;
    }

    private double[] readNormalizedPair(JsonNode node) {
        if (node == null) {
            return null;
        }
        Double x = readVectorValueOrNull(node, 0, "x", "X", "u");
        Double y = readVectorValueOrNull(node, 1, "y", "Y", "v");
        if (x == null || y == null || !isNormalized(x) || !isNormalized(y)) {
            return null;
        }
        return new double[]{x, y};
    }

    private double[] readVector3(JsonNode node) {
        if (node == null) {
            return null;
        }
        Double x = readVectorValueOrNull(node, 0, "x", "X");
        Double y = readVectorValueOrNull(node, 1, "y", "Y");
        Double z = readVectorValueOrNull(node, 2, "z", "Z");
        if (x == null || y == null || z == null) {
            return null;
        }
        return new double[]{x, y, z};
    }

    private Double readVectorValueOrNull(JsonNode node, int index, String... keys) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray() && node.size() > index && node.get(index).isNumber()) {
            return node.get(index).asDouble();
        }
        if (node.isObject()) {
            for (String key : keys) {
                if (node.has(key) && node.get(key).isNumber()) {
                    return node.get(key).asDouble();
                }
            }
            JsonNode position = firstPresent(node,
                    "position",
                    "point",
                    "display_area",
                    "displayArea",
                    "coordinate",
                    "coordinates"
            );
            if (position != null && position != node) {
                return readVectorValueOrNull(position, index, keys);
            }
        }
        return null;
    }

    private double[] averagePairs(double[] left, double[] right) {
        if (left != null && right != null) {
            return new double[]{(left[0] + right[0]) / 2.0, (left[1] + right[1]) / 2.0};
        }
        return left != null ? left : right;
    }

    private double[] averageVectors(double[] left, double[] right) {
        if (left != null && right != null) {
            return new double[]{
                    (left[0] + right[0]) / 2.0,
                    (left[1] + right[1]) / 2.0,
                    (left[2] + right[2]) / 2.0
            };
        }
        return left != null ? left : right;
    }

    private boolean isNormalized(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    /**
     * 眼动仪信息类
     */
    public static class GlassesInfo {
        private String serialNumber;
        private String firmwareVersion;
        private int batteryLevel;

        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
        public String getFirmwareVersion() { return firmwareVersion; }
        public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
        public int getBatteryLevel() { return batteryLevel; }
        public void setBatteryLevel(int batteryLevel) { this.batteryLevel = batteryLevel; }
    }

    /**
     * 眼动数据样本类 - 支持gaze3d格式
     */
    public static class GazeDataSample {
        private long timestamp;

        private double x;
        private double y;
        private double z;

        private double gaze2dX;
        private double gaze2dY;
        private boolean gaze2dValid;
        private boolean gaze3dValid;

        private double gazeOriginX;
        private double gazeOriginY;
        private double gazeOriginZ;

        private double gazeDirectionX;
        private double gazeDirectionY;
        private double gazeDirectionZ;

        private double leftPupilDiameter;
        private double rightPupilDiameter;

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
