package com.example.eyetracking.service;

import com.example.eyetracking.model.GazeData;
import com.example.eyetracking.model.Report;
import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.repository.ReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AttentionModelService {
    @Autowired
    private GazeDataService gazeDataService;
    @Autowired
    private ReportRepository reportRepository;

    // 生成注意力分析报告
    public Report generateAttentionReport(TrainingSession trainingSession) {
        if (trainingSession == null) {
            throw new IllegalArgumentException("训练会话不能为空");
        }

        List<GazeData> gazeDataList = gazeDataService.getGazeDataByTrainingSession(trainingSession);
        List<GazeData> processedData = gazeDataService.processGazeData(gazeDataList);

        // 计算注意力分数
        double attentionScore = gazeDataService.calculateAttentionScore(processedData);

        // 分析注意力模式
        String focusPattern = analyzeFocusPattern(processedData);

        // 计算详细的注意力指标
        AttentionService attentionService = new AttentionService();
        for (GazeData data : processedData) {
            attentionService.addGazePoint(data);
        }

        AttentionService.AttentionMetrics metrics = attentionService.getMetrics();

        // 生成建议
        String recommendations = generateRecommendations(attentionScore, focusPattern);

        // 生成详细分析
        String detailedAnalysis = generateDetailedAnalysis(processedData, attentionScore, focusPattern);

        // 创建报告
        Report report = new Report();
        report.setTrainingSession(trainingSession);
        report.setGeneratedAt(LocalDateTime.now());
        report.setAttentionScore(String.format("%.2f", attentionScore));
        report.setFocusPattern(focusPattern);
        report.setRecommendations(recommendations);
        report.setDetailedAnalysis(detailedAnalysis);

        // 设置详细指标
        if (metrics != null) {
            report.setEffectiveFixationRate(String.format("%.2f", metrics.getEffectiveFixationRate() * 100));
            report.setRegressionCount(String.valueOf(metrics.getRegressionCount()));
            report.setSaccadeEntropy(String.format("%.4f", metrics.getSaccadeEntropy()));
            report.setDataQualityScore(String.format("%.4f", metrics.getDataQualityScore()));
            report.setAverageFixationDuration(String.format("%.2f", metrics.getAverageFixationDuration() * 1000));
            report.setSaccadePathLength(String.format("%.2f", metrics.getSaccadePathLength()));
        } else {
            report.setEffectiveFixationRate("0.00");
            report.setRegressionCount("0");
            report.setSaccadeEntropy("0.0000");
            report.setDataQualityScore("0.0000");
            report.setAverageFixationDuration("0.00");
            report.setSaccadePathLength("0.00");
        }

        // 保存报告
        return reportRepository.save(report);
    }

    // 分析注意力模式
    private String analyzeFocusPattern(List<GazeData> processedData) {
        if (processedData.isEmpty()) {
            return "无数据";
        }

        // 统计不同区域的注视次数
        Map<String, Long> areaCountMap = processedData.stream()
                .collect(Collectors.groupingBy(this::getAreaOfInterest, Collectors.counting()));

        // 找出注视次数最多的区域
        String mostFocusedArea = areaCountMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("未知区域");

        // 分析注视模式
        if (areaCountMap.size() == 1) {
            return "集中型：仅关注" + mostFocusedArea;
        } else if (areaCountMap.size() > 5) {
            return "分散型：关注多个区域";
        } else {
            return "平衡型：关注" + String.join(", ", areaCountMap.keySet());
        }
    }

    // 生成建议
    private String generateRecommendations(double attentionScore, String focusPattern) {
        StringBuilder recommendations = new StringBuilder();

        if (attentionScore < 50) {
            recommendations.append("1. 建议增加训练时间，提高注意力持续时间\n");
            recommendations.append("2. 减少训练环境中的干扰因素\n");
            recommendations.append("3. 尝试使用番茄工作法，每25分钟休息5分钟\n");
        } else if (attentionScore < 80) {
            recommendations.append("1. 继续保持当前的训练强度\n");
            recommendations.append("2. 尝试增加训练难度，挑战更高水平\n");
            recommendations.append("3. 注意保持良好的坐姿和用眼习惯\n");
        } else {
            recommendations.append("1. 注意力水平优秀，继续保持\n");
            recommendations.append("2. 可以尝试更复杂的编程任务\n");
            recommendations.append("3. 分享你的学习方法和经验\n");
        }

        if (focusPattern.contains("分散型")) {
            recommendations.append("4. 建议练习专注于单一任务，减少多任务处理\n");
        } else if (focusPattern.contains("集中型")) {
            recommendations.append("4. 建议扩展关注范围，培养全局思维\n");
        }

        return recommendations.toString();
    }

    // 生成详细分析
    private String generateDetailedAnalysis(List<GazeData> processedData, double attentionScore, String focusPattern) {
        StringBuilder analysis = new StringBuilder();

        analysis.append("# 注意力分析报告\n\n");
        analysis.append("## 基本信息\n");
        analysis.append("- 注意力分数：").append(String.format("%.2f", attentionScore)).append("/100\n");
        analysis.append("- 注意力模式：").append(focusPattern).append("\n");
        analysis.append("- 数据点数量：").append(processedData.size()).append("\n\n");

        analysis.append("## 详细分析\n");
        
        // 统计fixation数量和平均持续时间
        long fixationCount = processedData.stream()
                .filter(data -> "FIXATION".equals(data.getFixationType()))
                .count();

        int totalFixationDuration = processedData.stream()
                .filter(data -> "FIXATION".equals(data.getFixationType()))
                .mapToInt(data -> data.getFixationDuration() == null ? 0 : data.getFixationDuration())
                .sum();

        double avgFixationDuration = fixationCount > 0 ? (double) totalFixationDuration / fixationCount : 0;

        analysis.append("- Fixation数量：").append(fixationCount).append("\n");
        analysis.append("- 平均Fixation持续时间：").append(String.format("%.2f", avgFixationDuration)).append("ms\n\n");

        // 分析区域分布
        Map<String, Long> areaCountMap = processedData.stream()
                .collect(Collectors.groupingBy(this::getAreaOfInterest, Collectors.counting()));

        analysis.append("## 区域分布\n");
        areaCountMap.forEach((area, count) -> {
            analysis.append("- " + area + "：" + count + "次（" + String.format("%.2f", (double) count / processedData.size() * 100) + "%）\n");
        });

        return analysis.toString();
    }

    private String getAreaOfInterest(GazeData data) {
        if (data.getAreaOfInterest() == null || data.getAreaOfInterest().trim().isEmpty()) {
            return "code";
        }
        return data.getAreaOfInterest();
    }

    // 获取用户的历史注意力数据
    public List<Report> getHistoricalReports(TrainingSession trainingSession) {
        return reportRepository.findByTrainingSession(trainingSession);
    }
}
