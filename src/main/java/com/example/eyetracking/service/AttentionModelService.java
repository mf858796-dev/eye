package com.example.eyetracking.service;

import com.example.eyetracking.model.GazeData;
import com.example.eyetracking.model.Report;
import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.repository.ReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AttentionModelService {
    @Autowired
    private GazeDataService gazeDataService;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private CodeRepositoryService codeRepositoryService;

    private static final int MIN_READ_LINE_FOCUS_MS = 500;

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

        ReadingMetrics readingMetrics = calculateReadingMetrics(trainingSession, processedData);

        // 生成建议
        String recommendations = generateRecommendations(attentionScore, focusPattern, readingMetrics);

        // 生成详细分析
        String detailedAnalysis = generateDetailedAnalysis(processedData, attentionScore, focusPattern, readingMetrics);

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
    private String generateRecommendations(double attentionScore, String focusPattern, ReadingMetrics readingMetrics) {
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

        if (readingMetrics.sequentialCompletionRate < 80.0) {
            recommendations.append("5. 代码阅读建议按从上到下的顺序完成一遍，避免直接跳到后半段\n");
        }
        if (readingMetrics.regressionRate > 25.0) {
            recommendations.append("6. 回视比例偏高，可先通读结构，再回到关键行细看\n");
        }
        if (readingMetrics.outsideCodeRate > 20.0) {
            recommendations.append("7. 视线离开代码区域较多，建议重新校准或调整坐姿/屏幕距离\n");
        }

        return recommendations.toString();
    }

    // 生成详细分析
    private String generateDetailedAnalysis(List<GazeData> processedData, double attentionScore, String focusPattern, ReadingMetrics readingMetrics) {
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

        analysis.append("## 代码阅读路径\n");
        analysis.append("- 顺序阅读完成度：").append(String.format("%.2f", readingMetrics.sequentialCompletionRate)).append("%\n");
        analysis.append("- 代码行覆盖率：").append(String.format("%.2f", readingMetrics.lineCoverageRate)).append("%\n");
        analysis.append("- 回视比例：").append(String.format("%.2f", readingMetrics.regressionRate)).append("%\n");
        analysis.append("- 离开代码区域比例：").append(String.format("%.2f", readingMetrics.outsideCodeRate)).append("%\n");
        analysis.append("- 平均行注视时长：").append(String.format("%.2f", readingMetrics.averageLineFixationMs)).append("ms\n\n");

        // 分析区域分布
        Map<String, Long> areaCountMap = processedData.stream()
                .collect(Collectors.groupingBy(this::getAreaOfInterest, Collectors.counting()));

        analysis.append("## 区域分布\n");
        areaCountMap.forEach((area, count) -> {
            analysis.append("- " + area + "：" + count + "次（" + String.format("%.2f", (double) count / processedData.size() * 100) + "%）\n");
        });

        return analysis.toString();
    }

    private ReadingMetrics calculateReadingMetrics(TrainingSession trainingSession, List<GazeData> processedData) {
        ReadingMetrics metrics = new ReadingMetrics();
        if (processedData == null || processedData.isEmpty()) {
            return metrics;
        }

        List<Integer> readableLines = readableLineNumbers(trainingSession, processedData);
        Set<Integer> readableLineSet = new LinkedHashSet<>(readableLines);
        Set<Integer> observedLines = new LinkedHashSet<>();
        Map<Integer, Integer> focusMsByLine = new java.util.HashMap<>();

        List<GazeData> ordered = processedData.stream()
                .sorted(Comparator.comparing(GazeData::getTimestamp, Comparator.nullsLast(LocalDateTime::compareTo)))
                .collect(Collectors.toList());

        int outsideCount = 0;
        int lineFixationCount = 0;
        int totalLineFixationMs = 0;
        int transitions = 0;
        int regressions = 0;
        Integer previousLine = null;
        int nextLineIndex = 0;

        for (GazeData data : ordered) {
            Integer lineNumber = data.getLineNumber();
            int fixationDuration = data.getFixationDuration() == null ? 100 : Math.max(0, data.getFixationDuration());
            if (lineNumber == null || lineNumber <= 0) {
                outsideCount++;
                continue;
            }

            observedLines.add(lineNumber);
            lineFixationCount++;
            totalLineFixationMs += fixationDuration;

            if (previousLine != null && !previousLine.equals(lineNumber)) {
                transitions++;
                if (lineNumber < previousLine) {
                    regressions++;
                }
            }
            previousLine = lineNumber;

            if (nextLineIndex < readableLines.size()
                    && readableLineSet.contains(lineNumber)
                    && lineNumber.equals(readableLines.get(nextLineIndex))) {
                int accumulated = focusMsByLine.getOrDefault(lineNumber, 0) + fixationDuration;
                focusMsByLine.put(lineNumber, accumulated);
                if (accumulated >= MIN_READ_LINE_FOCUS_MS) {
                    nextLineIndex++;
                }
            }
        }

        metrics.sequentialCompletionRate = readableLines.isEmpty()
                ? 0.0
                : nextLineIndex * 100.0 / readableLines.size();
        if (trainingSession != null && trainingSession.getCompletionRate() != null) {
            metrics.sequentialCompletionRate = trainingSession.getCompletionRate();
        }
        metrics.lineCoverageRate = readableLines.isEmpty()
                ? 0.0
                : observedLines.stream().filter(readableLineSet::contains).count() * 100.0 / readableLines.size();
        metrics.regressionRate = transitions == 0 ? 0.0 : regressions * 100.0 / transitions;
        metrics.outsideCodeRate = ordered.isEmpty() ? 0.0 : outsideCount * 100.0 / ordered.size();
        metrics.averageLineFixationMs = lineFixationCount == 0 ? 0.0 : totalLineFixationMs * 1.0 / lineFixationCount;
        return metrics;
    }

    private List<Integer> readableLineNumbers(TrainingSession trainingSession, List<GazeData> processedData) {
        if (trainingSession != null && trainingSession.getTrainingLevelId() != null) {
            CodeRepositoryService.CodeExample codeExample =
                    codeRepositoryService.getCodeExampleById(trainingSession.getTrainingLevelId());
            if (codeExample != null && codeExample.getCode() != null) {
                List<Integer> lines = new ArrayList<>();
                String[] codeLines = codeExample.getCode().split("\\r?\\n", -1);
                for (int i = 0; i < codeLines.length; i++) {
                    if (!codeLines[i].trim().isEmpty()) {
                        lines.add(i + 1);
                    }
                }
                if (!lines.isEmpty()) {
                    return lines;
                }
            }
        }

        return processedData.stream()
                .map(GazeData::getLineNumber)
                .filter(line -> line != null && line > 0)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private String getAreaOfInterest(GazeData data) {
        if (data.getAreaOfInterest() == null || data.getAreaOfInterest().trim().isEmpty()) {
            return "code";
        }
        return data.getAreaOfInterest();
    }

    private static class ReadingMetrics {
        private double sequentialCompletionRate;
        private double lineCoverageRate;
        private double regressionRate;
        private double outsideCodeRate;
        private double averageLineFixationMs;
    }

    // 获取用户的历史注意力数据
    public List<Report> getHistoricalReports(TrainingSession trainingSession) {
        return reportRepository.findByTrainingSession(trainingSession);
    }
}
