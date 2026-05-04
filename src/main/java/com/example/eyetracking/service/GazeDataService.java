package com.example.eyetracking.service;

import com.example.eyetracking.model.GazeData;
import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.repository.GazeDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        // 实现fixation检测算法
        // 这里使用简单的时间阈值和距离阈值方法
        List<GazeData> processedData = new java.util.ArrayList<>();
        
        if (rawGazeData.isEmpty()) {
            return processedData;
        }

        GazeData currentFixation = rawGazeData.get(0);
        int fixationCount = 1;
        double sumX = currentFixation.getxCoordinate();
        double sumY = currentFixation.getyCoordinate();

        for (int i = 1; i < rawGazeData.size(); i++) {
            GazeData currentData = rawGazeData.get(i);
            GazeData previousData = rawGazeData.get(i - 1);

            // 计算时间差（毫秒）
            long timeDiff = java.time.Duration.between(previousData.getTimestamp(), currentData.getTimestamp()).toMillis();
            
            // 计算距离
            double distance = Math.sqrt(
                Math.pow(currentData.getxCoordinate() - previousData.getxCoordinate(), 2) +
                Math.pow(currentData.getyCoordinate() - previousData.getyCoordinate(), 2)
            );

            // 如果时间差小于阈值且距离小于阈值，则认为是同一个fixation
            if (timeDiff < 100 && distance < 50) {
                sumX += currentData.getxCoordinate();
                sumY += currentData.getyCoordinate();
                fixationCount++;
            } else {
                // 完成一个fixation
                currentFixation.setFixationType("FIXATION");
                currentFixation.setFixationDuration((int) timeDiff * fixationCount);
                currentFixation.setxCoordinate(sumX / fixationCount);
                currentFixation.setyCoordinate(sumY / fixationCount);
                processedData.add(currentFixation);

                // 开始新的fixation
                currentFixation = currentData;
                sumX = currentData.getxCoordinate();
                sumY = currentData.getyCoordinate();
                fixationCount = 1;
            }
        }

        // 添加最后一个fixation
        if (currentFixation != null) {
            processedData.add(currentFixation);
        }

        return processedData;
    }

    // 计算注意力分数
    public double calculateAttentionScore(List<GazeData> gazeDataList) {
        if (gazeDataList.isEmpty()) {
            return 0.0;
        }

        // 统计fixation的数量和持续时间
        long fixationCount = gazeDataList.stream()
                .filter(data -> "FIXATION".equals(data.getFixationType()))
                .count();

        int totalFixationDuration = gazeDataList.stream()
                .filter(data -> "FIXATION".equals(data.getFixationType()))
                .mapToInt(GazeData::getFixationDuration)
                .sum();

        // 计算平均fixation持续时间
        double avgFixationDuration = fixationCount > 0 ? (double) totalFixationDuration / fixationCount : 0;

        // 计算注意力分数（0-100）
        // 这里使用简单的算法：平均fixation持续时间越长，注意力分数越高
        double attentionScore = Math.min(100, avgFixationDuration * 0.1);

        return attentionScore;
    }
}