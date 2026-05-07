package com.example.eyetracking.service;

import com.example.eyetracking.model.GazeData;
import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.repository.GazeDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GazeDataService {
    @Autowired
    private GazeDataRepository gazeDataRepository;

    // 保存眼动数据
    public GazeData saveGazeData(GazeData gazeData) {
        return gazeDataRepository.save(gazeData);
    }

    // 批量保存眼动数据
    public List<GazeData> saveBatchGazeData(List<GazeData> gazeDataList) {
        if (gazeDataList == null || gazeDataList.isEmpty()) {
            return Collections.emptyList();
        }
        return gazeDataRepository.saveAll(gazeDataList);
    }

    // 获取训练会话的眼动数据
    public List<GazeData> getGazeDataByTrainingSession(TrainingSession trainingSession) {
        return gazeDataRepository.findByTrainingSession(trainingSession);
    }

    // 获取训练会话的眼动数据
    public List<GazeData> getGazeDataByTrainingSessionId(Long trainingSessionId) {
        return gazeDataRepository.findByTrainingSessionId(trainingSessionId);
    }

    // 处理眼动数据，检测fixation
    public List<GazeData> processGazeData(List<GazeData> rawGazeData) {
        List<GazeData> processedData = new java.util.ArrayList<>();

        if (rawGazeData == null || rawGazeData.isEmpty()) {
            return processedData;
        }

        List<GazeData> validData = rawGazeData.stream()
                .filter(this::isValidPoint)
                .sorted(Comparator.comparing(GazeData::getTimestamp))
                .collect(Collectors.toList());

        if (validData.isEmpty()) {
            return processedData;
        }

        GazeData fixationStart = validData.get(0);
        GazeData previousData = fixationStart;
        int fixationCount = 1;
        double sumX = fixationStart.getxCoordinate();
        double sumY = fixationStart.getyCoordinate();
        double sumGaze3dX = valueOrDefault(fixationStart.getGaze3dX());
        double sumGaze3dY = valueOrDefault(fixationStart.getGaze3dY());
        double sumGaze3dZ = valueOrDefault(fixationStart.getGaze3dZ());
        int gaze3dCount = hasValidGaze3d(fixationStart) ? 1 : 0;

        for (int i = 1; i < validData.size(); i++) {
            GazeData currentData = validData.get(i);

            // 计算时间差（毫秒）
            long timeDiff = Duration.between(previousData.getTimestamp(), currentData.getTimestamp()).toMillis();
            
            // 计算距离
            double distance = calculateGazeDistance(currentData, previousData);
            double fixationRadius = hasValidGaze3d(currentData) && hasValidGaze3d(previousData) ? 80.0 : 60.0;

            // 如果时间差小于阈值且距离小于阈值，则认为是同一个fixation
            if (timeDiff <= 150 && distance <= fixationRadius) {
                sumX += currentData.getxCoordinate();
                sumY += currentData.getyCoordinate();
                if (hasValidGaze3d(currentData)) {
                    sumGaze3dX += currentData.getGaze3dX();
                    sumGaze3dY += currentData.getGaze3dY();
                    sumGaze3dZ += currentData.getGaze3dZ();
                    gaze3dCount++;
                }
                fixationCount++;
            } else {
                processedData.add(toFixation(fixationStart, previousData, sumX, sumY,
                        sumGaze3dX, sumGaze3dY, sumGaze3dZ, gaze3dCount, fixationCount));

                // 开始新的fixation
                fixationStart = currentData;
                sumX = currentData.getxCoordinate();
                sumY = currentData.getyCoordinate();
                sumGaze3dX = valueOrDefault(currentData.getGaze3dX());
                sumGaze3dY = valueOrDefault(currentData.getGaze3dY());
                sumGaze3dZ = valueOrDefault(currentData.getGaze3dZ());
                gaze3dCount = hasValidGaze3d(currentData) ? 1 : 0;
                fixationCount = 1;
            }
            previousData = currentData;
        }

        // 添加最后一个fixation
        processedData.add(toFixation(fixationStart, previousData, sumX, sumY,
                sumGaze3dX, sumGaze3dY, sumGaze3dZ, gaze3dCount, fixationCount));

        return processedData;
    }

    // 计算注意力分数
    public double calculateAttentionScore(List<GazeData> gazeDataList) {
        if (gazeDataList == null || gazeDataList.isEmpty()) {
            return 0.0;
        }

        List<GazeData> fixationData = gazeDataList.stream()
                .filter(data -> "FIXATION".equals(data.getFixationType()))
                .collect(Collectors.toList());

        long fixationCount = fixationData.size();

        int totalFixationDuration = fixationData.stream()
                .mapToInt(data -> data.getFixationDuration() == null ? 0 : data.getFixationDuration())
                .sum();

        // 计算平均fixation持续时间
        double avgFixationDuration = fixationCount > 0 ? (double) totalFixationDuration / fixationCount : 0;

        long totalDuration = calculateTotalDurationMillis(gazeDataList);
        double fixationRate = totalDuration > 0 ? Math.min(1.0, totalFixationDuration / (double) totalDuration) : 0.0;
        double durationScore = Math.min(100, avgFixationDuration / 600.0 * 100);
        double fixationScore = fixationRate * 100;
        double coverageScore = Math.min(100, fixationCount / 20.0 * 100);

        return Math.round((durationScore * 0.45 + fixationScore * 0.4 + coverageScore * 0.15) * 100.0) / 100.0;
    }

    private boolean isValidPoint(GazeData data) {
        return data != null
                && data.getTimestamp() != null
                && data.getxCoordinate() != null
                && data.getyCoordinate() != null
                && data.getxCoordinate() >= 0
                && data.getyCoordinate() >= 0;
    }

    private GazeData toFixation(GazeData fixationStart, GazeData fixationEnd,
                                double sumX, double sumY,
                                double sumGaze3dX, double sumGaze3dY, double sumGaze3dZ,
                                int gaze3dCount, int count) {
        fixationStart.setFixationType("FIXATION");
        fixationStart.setFixationDuration(calculateDurationMillis(fixationStart.getTimestamp(), fixationEnd.getTimestamp(), count));
        fixationStart.setxCoordinate(sumX / count);
        fixationStart.setyCoordinate(sumY / count);
        if (gaze3dCount > 0) {
            fixationStart.setGaze3dX(sumGaze3dX / gaze3dCount);
            fixationStart.setGaze3dY(sumGaze3dY / gaze3dCount);
            fixationStart.setGaze3dZ(sumGaze3dZ / gaze3dCount);
            fixationStart.setzCoordinate(fixationStart.getGaze3dZ());
        }
        if (fixationStart.getAreaOfInterest() == null) {
            fixationStart.setAreaOfInterest("code");
        }
        return fixationStart;
    }

    private double calculateGazeDistance(GazeData currentData, GazeData previousData) {
        if (hasValidGaze3d(currentData) && hasValidGaze3d(previousData)) {
            return Math.sqrt(
                    Math.pow(currentData.getGaze3dX() - previousData.getGaze3dX(), 2) +
                    Math.pow(currentData.getGaze3dY() - previousData.getGaze3dY(), 2) +
                    Math.pow(currentData.getGaze3dZ() - previousData.getGaze3dZ(), 2)
            );
        }
        return Math.sqrt(
                Math.pow(currentData.getxCoordinate() - previousData.getxCoordinate(), 2) +
                Math.pow(currentData.getyCoordinate() - previousData.getyCoordinate(), 2)
        );
    }

    private boolean hasValidGaze3d(GazeData data) {
        return data != null
                && data.getGaze3dX() != null
                && data.getGaze3dY() != null
                && data.getGaze3dZ() != null
                && Math.abs(data.getGaze3dZ()) >= 1.0;
    }

    private double valueOrDefault(Double value) {
        return value == null ? 0.0 : value;
    }

    private int calculateDurationMillis(LocalDateTime start, LocalDateTime end, int count) {
        long duration = Duration.between(start, end).toMillis();
        if (duration <= 0 && count > 1) {
            duration = count * 50L;
        }
        return (int) Math.max(50, Math.min(duration, Integer.MAX_VALUE));
    }

    private long calculateTotalDurationMillis(List<GazeData> gazeDataList) {
        List<GazeData> validData = gazeDataList.stream()
                .filter(this::isValidPoint)
                .sorted(Comparator.comparing(GazeData::getTimestamp))
                .collect(Collectors.toList());
        if (validData.size() < 2) {
            return 0L;
        }
        return Duration.between(validData.get(0).getTimestamp(), validData.get(validData.size() - 1).getTimestamp()).toMillis();
    }
}
