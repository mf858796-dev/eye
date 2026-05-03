package com.example.eyetracking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private static final int HEATMAP_GRID_WIDTH = 50;
    private static final int HEATMAP_GRID_HEIGHT = 30;

    private List<GazePoint> gazeData;
    private AttentionService.AttentionMetrics trainingMetrics;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String currentSessionLabel;

    public ReportService() {
        this.gazeData = new ArrayList<>();
        this.trainingMetrics = null;
        this.startTime = null;
        this.endTime = null;
        this.currentSessionLabel = null;
    }

    public void addGazePoint(double x, double y) {
        if (gazeData == null) {
            gazeData = new ArrayList<>();
        }
        gazeData.add(new GazePoint(x, y));
    }

    public void resetSession(String sessionLabel) {
        gazeData = new ArrayList<>();
        trainingMetrics = null;
        startTime = LocalDateTime.now();
        endTime = null;
        currentSessionLabel = sessionLabel;
    }

    public void setMetrics(AttentionService.AttentionMetrics metrics) {
        this.trainingMetrics = metrics;
    }

    public void setTrainingTime(LocalDateTime start, LocalDateTime end) {
        this.startTime = start;
        this.endTime = end != null ? end : LocalDateTime.now();
    }

    public Map<String, Object> generateHeatmapData() {
        Map<String, Object> result = new HashMap<>();

        if (gazeData == null || gazeData.isEmpty()) {
            result.put("success", false);
            result.put("message", "No gaze data available");
            return result;
        }

        int[][] grid = new int[HEATMAP_GRID_HEIGHT][HEATMAP_GRID_WIDTH];
        int maxCount = 0;

        int screenWidth = 1920;
        int screenHeight = 1080;
        double cellWidth = (double) screenWidth / HEATMAP_GRID_WIDTH;
        double cellHeight = (double) screenHeight / HEATMAP_GRID_HEIGHT;

        for (GazePoint point : gazeData) {
            int gridX = (int) (point.x / cellWidth);
            int gridY = (int) (point.y / cellHeight);

            gridX = Math.max(0, Math.min(HEATMAP_GRID_WIDTH - 1, gridX));
            gridY = Math.max(0, Math.min(HEATMAP_GRID_HEIGHT - 1, gridY));

            grid[gridY][gridX]++;
            maxCount = Math.max(maxCount, grid[gridY][gridX]);
        }

        List<List<Double>> normalizedHeatmap = new ArrayList<>();
        for (int y = 0; y < HEATMAP_GRID_HEIGHT; y++) {
            List<Double> row = new ArrayList<>();
            for (int x = 0; x < HEATMAP_GRID_WIDTH; x++) {
                double normalized = maxCount > 0 ? (double) grid[y][x] / maxCount : 0.0;
                row.add(normalized);
            }
            normalizedHeatmap.add(row);
        }

        result.put("success", true);
        result.put("heatmap", normalizedHeatmap);
        result.put("maxCount", maxCount);
        result.put("totalPoints", gazeData.size());
        result.put("gridWidth", HEATMAP_GRID_WIDTH);
        result.put("gridHeight", HEATMAP_GRID_HEIGHT);

        return result;
    }

    public Map<String, Object> generateTrajectoryData() {
        Map<String, Object> result = new HashMap<>();

        if (gazeData == null || gazeData.isEmpty()) {
            result.put("success", false);
            result.put("message", "No gaze data available");
            return result;
        }

        List<Map<String, Object>> points = new ArrayList<>();
        int sampleRate = Math.max(1, gazeData.size() / 100);

        for (int i = 0; i < gazeData.size(); i++) {
            if (i % sampleRate == 0 || i == gazeData.size() - 1) {
                Map<String, Object> point = new HashMap<>();
                point.put("x", gazeData.get(i).x);
                point.put("y", gazeData.get(i).y);
                point.put("index", i);
                point.put("isStart", i == 0);
                point.put("isEnd", i == gazeData.size() - 1);
                points.add(point);
            }
        }

        result.put("success", true);
        result.put("points", points);
        result.put("totalPoints", gazeData.size());
        result.put("sampledPoints", points.size());

        return result;
    }

    public Map<String, Object> generateStatisticsChart() {
        Map<String, Object> result = new HashMap<>();

        if (trainingMetrics == null) {
            result.put("success", false);
            result.put("message", "No metrics available");
            return result;
        }

        List<Map<String, Object>> chartData = new ArrayList<>();

        Map<String, Object> fixationData = new HashMap<>();
        fixationData.put("label", "有效注视率");
        fixationData.put("value", trainingMetrics.getEffectiveFixationRate() * 100);
        fixationData.put("maxValue", 100.0);
        chartData.add(fixationData);

        Map<String, Object> regressionData = new HashMap<>();
        regressionData.put("label", "回视次数");
        regressionData.put("value", trainingMetrics.getRegressionCount());
        chartData.add(regressionData);

        Map<String, Object> entropyData = new HashMap<>();
        entropyData.put("label", "扫视熵");
        entropyData.put("value", trainingMetrics.getSaccadeEntropy());
        entropyData.put("maxValue", 1.0);
        chartData.add(entropyData);

        Map<String, Object> qualityData = new HashMap<>();
        qualityData.put("label", "数据质量");
        qualityData.put("value", trainingMetrics.getDataQualityScore());
        qualityData.put("maxValue", 1.0);
        chartData.add(qualityData);

        result.put("success", true);
        result.put("charts", chartData);

        return result;
    }

    public Map<String, Object> generateFullReport() {
        Map<String, Object> report = new HashMap<>();

        LocalDateTime reportTime = LocalDateTime.now();
        String timestamp = reportTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        report.put("timestamp", timestamp);
        report.put("reportTime", reportTime);
        report.put("sessionLabel", currentSessionLabel);

        if (startTime != null && endTime != null) {
            long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
            report.put("duration", durationSeconds);
            report.put("durationMinutes", durationSeconds / 60.0);
        }

        if (trainingMetrics != null) {
            report.put("effectiveFixationRate", trainingMetrics.getEffectiveFixationRate());
            report.put("regressionCount", trainingMetrics.getRegressionCount());
            report.put("saccadeEntropy", trainingMetrics.getSaccadeEntropy());
            report.put("averageFixationDuration", trainingMetrics.getAverageFixationDuration());
            report.put("saccadePathLength", trainingMetrics.getSaccadePathLength());
            report.put("dataQualityScore", trainingMetrics.getDataQualityScore());
        }

        Map<String, Object> heatmapData = generateHeatmapData();
        if (Boolean.TRUE.equals(heatmapData.get("success"))) {
            report.put("heatmapData", heatmapData.get("heatmap"));
            report.put("heatmapTotalPoints", heatmapData.get("totalPoints"));
        }

        Map<String, Object> trajectoryData = generateTrajectoryData();
        if (Boolean.TRUE.equals(trajectoryData.get("success"))) {
            report.put("trajectoryPoints", trajectoryData.get("points"));
            report.put("trajectoryTotalPoints", trajectoryData.get("totalPoints"));
        }

        return report;
    }

    public List<GazePoint> getGazeData() {
        return gazeData;
    }

    public void setGazeData(List<GazePoint> gazeData) {
        this.gazeData = gazeData;
    }

    public AttentionService.AttentionMetrics getTrainingMetrics() {
        return trainingMetrics;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getCurrentSessionLabel() {
        return currentSessionLabel;
    }

    public static class GazePoint {
        public double x;
        public double y;

        public GazePoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
