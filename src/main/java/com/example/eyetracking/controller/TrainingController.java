package com.example.eyetracking.controller;

import com.example.eyetracking.model.GazeData;
import com.example.eyetracking.model.Report;
import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.UserRepository;
import com.example.eyetracking.service.AchievementService;
import com.example.eyetracking.service.AppSettingsService;
import com.example.eyetracking.service.AttentionModelService;
import com.example.eyetracking.service.CodeRepositoryService;
import com.example.eyetracking.service.CoordinateMapperService;
import com.example.eyetracking.service.GazeDataService;
import com.example.eyetracking.service.TrainingSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpSession;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/training")
public class TrainingController {
    private static final Logger logger = LoggerFactory.getLogger(TrainingController.class);
    private static final int DEFAULT_SCREEN_WIDTH = 1920;
    private static final int DEFAULT_SCREEN_HEIGHT = 1080;

    @Autowired
    private CodeRepositoryService codeRepositoryService;
    @Autowired
    private TrainingSessionService trainingSessionService;
    @Autowired
    private GazeDataService gazeDataService;
    @Autowired
    private AttentionModelService attentionModelService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AppSettingsService appSettingsService;
    @Autowired
    private AchievementService achievementService;
    @Autowired
    private CoordinateMapperService coordinateMapperService;
    @Autowired
    private ObjectMapper objectMapper;

    // 代码示例列表
    @GetMapping("/code-examples")
    public String codeExamples(
            @RequestParam(value = "language", defaultValue = "all") String language,
            @RequestParam(value = "difficulty", defaultValue = "all") String difficulty,
            Model model) {
        List<CodeRepositoryService.CodeExample> codeExamples = codeRepositoryService.getAllCodeExamples();
        if (!"all".equalsIgnoreCase(language)) {
            codeExamples = codeExamples.stream()
                    .filter(example -> language.equalsIgnoreCase(example.getLanguage()))
                    .collect(Collectors.toList());
        }
        if (!"all".equalsIgnoreCase(difficulty)) {
            codeExamples = codeExamples.stream()
                    .filter(example -> difficulty.equalsIgnoreCase(example.getDifficulty()))
                    .collect(Collectors.toList());
        }
        model.addAttribute("codeExamples", codeExamples);
        model.addAttribute("selectedLanguage", language);
        model.addAttribute("selectedDifficulty", difficulty);
        model.addAttribute("resultCount", codeExamples.size());
        return "training/code-examples";
    }

    // 开始训练
    @GetMapping("/start/code/{codeId}")
    public String startTraining(@PathVariable Long codeId, Model model, Principal principal, HttpSession httpSession) {
        try {
            // 获取代码示例
            CodeRepositoryService.CodeExample codeExample = codeRepositoryService.getCodeExampleById(codeId);
            if (codeExample == null) {
                model.addAttribute("error", "代码示例不存在");
                return "redirect:/training/code-examples";
            }

            // 获取当前登录用户
            if (principal == null) {
                model.addAttribute("error", "请先登录");
                return "redirect:/user/login";
            }
            
            User user = getCurrentUser(principal);
            if (user == null) {
                model.addAttribute("error", "请先登录");
                return "redirect:/user/login";
            }
            
            // 创建训练会话
            TrainingSession session = trainingSessionService.createTrainingSession(
                    user,
                    codeExample.getTitle(),
                    codeExample.getId(),
                    codeExample.getTaskType()
            );

            return showTrainingSession(session, codeExample, model, httpSession);
        } catch (Exception e) {
            logger.error("Failed to export training report as JSON", e);
            model.addAttribute("error", "训练开始失败: " + e.getMessage());
            return "redirect:/training/code-examples";
        }
    }

    @GetMapping("/resume/{sessionId}")
    public String resumeTraining(@PathVariable Long sessionId, Model model, Principal principal, HttpSession httpSession) {
        try {
            User user = getCurrentUser(principal);
            if (user == null) {
                return "redirect:/user/login";
            }

            TrainingSession session = trainingSessionService.getTrainingSessionById(sessionId);
            if (!ownsSession(session, user)) {
                model.addAttribute("error", "无权继续该训练会话");
                return "redirect:/training/list";
            }
            if ("COMPLETED".equals(session.getStatus())) {
                return "redirect:/training/report/" + sessionId;
            }
            if (session.getTrainingLevelId() == null) {
                model.addAttribute("error", "该会话未关联训练关卡，请从题库重新开始");
                return "redirect:/training/code-examples";
            }

            CodeRepositoryService.CodeExample codeExample =
                    codeRepositoryService.getCodeExampleById(session.getTrainingLevelId());
            if (codeExample == null) {
                model.addAttribute("error", "训练关卡不存在或已停用");
                return "redirect:/training/code-examples";
            }
            if (!"ACTIVE".equals(session.getStatus())) {
                session = trainingSessionService.updateTrainingSessionStatus(sessionId, "ACTIVE");
            }
            return showTrainingSession(session, codeExample, model, httpSession);
        } catch (Exception e) {
            logger.error("恢复训练失败", e);
            model.addAttribute("error", "训练恢复失败: " + e.getMessage());
            return "redirect:/training/list";
        }
    }

    // 提交眼动数据
    @PostMapping("/submit-gaze-data")
    public String submitGazeData(@RequestParam Long sessionId, @RequestParam String gazeDataJson, Model model, Principal principal) {
        try {
            User currentUser = getCurrentUser(principal);
            if (currentUser == null) {
                return "redirect:/user/login";
            }

            // 验证sessionId
            if (sessionId == null || sessionId <= 0) {
                model.addAttribute("error", "无效的会话ID");
                return "redirect:/training/code-examples";
            }

            // 获取训练会话
            TrainingSession session = trainingSessionService.getTrainingSessionById(sessionId);
            if (session == null) {
                model.addAttribute("error", "训练会话不存在");
                return "redirect:/training/code-examples";
            }
            if (!ownsSession(session, currentUser)) {
                model.addAttribute("error", "无权操作该训练会话");
                return "redirect:/training/list";
            }

            // 验证gazeDataJson
            if (gazeDataJson == null || gazeDataJson.trim().isEmpty()) {
                model.addAttribute("error", "眼动数据为空");
                return "redirect:/training/code-examples";
            }

            // 解析JSON格式的眼动数据
            List<Map<String, Object>> gazeDataList;
            try {
                gazeDataList = objectMapper.readValue(gazeDataJson, 
                    new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                model.addAttribute("error", "眼动数据格式错误: " + e.getMessage());
                return "redirect:/training/code-examples";
            }

            List<GazeData> records = gazeDataList.isEmpty()
                    ? createSimulatedGazeData(session)
                    : convertToGazeData(session, gazeDataList);
            gazeDataService.saveBatchGazeData(records);
            updateTrainingProgress(session, records);

            // 结束训练会话
            session = trainingSessionService.endTrainingSession(sessionId);

            // 生成报告
            Report report = attentionModelService.generateAttentionReport(session);

            if (report == null) {
                model.addAttribute("error", "报告生成失败");
                return "redirect:/training/code-examples";
            }

            List<com.example.eyetracking.model.Achievement> unlockedAchievements =
                    achievementService.evaluateAndUnlock(currentUser, session, report);
            model.addAttribute("unlockedAchievements", unlockedAchievements);
            addReportMetrics(model, report);
            addReportVisualizationData(model, session);
            model.addAttribute("trainingSession", session);
            model.addAttribute("report", report);
            return "training/report";
        } catch (Exception e) {
            logger.error("提交眼动数据失败", e);
            model.addAttribute("error", "数据提交失败: " + e.getMessage());
            return "redirect:/training/code-examples";
        }
    }

    // 查看报告
    @GetMapping("/report/{sessionId}")
    public String viewReport(@PathVariable Long sessionId, Model model, Principal principal) {
        try {
            User currentUser = getCurrentUser(principal);
            if (currentUser == null) {
                return "redirect:/user/login";
            }

            TrainingSession session = trainingSessionService.getTrainingSessionById(sessionId);
            if (!ownsSession(session, currentUser)) {
                model.addAttribute("error", "无权查看该训练报告");
                return "redirect:/training/list";
            }
            List<Report> reports = attentionModelService.getHistoricalReports(session);
            Report report = reports.isEmpty() ? attentionModelService.generateAttentionReport(session) : reports.get(0);
            if (report == null) {
                model.addAttribute("error", "报告不存在");
                return "redirect:/training/list";
            }
            addReportMetrics(model, report);
            addReportVisualizationData(model, session);
            model.addAttribute("report", report);
            model.addAttribute("trainingSession", session);
            return "training/report";
        } catch (Exception e) {
            model.addAttribute("error", "报告查看失败: " + e.getMessage());
            return "redirect:/training/list";
        }
    }

    @GetMapping("/report/{sessionId}/export")
    public ResponseEntity<String> exportReport(
            @PathVariable Long sessionId,
            @RequestParam(value = "format", defaultValue = "json") String format,
            Principal principal) {
        User currentUser = getCurrentUser(principal);
        if (currentUser == null) {
            return ResponseEntity.status(401).body("请先登录");
        }

        TrainingSession session = trainingSessionService.getTrainingSessionById(sessionId);
        if (!ownsSession(session, currentUser)) {
            return ResponseEntity.status(403).body("无权导出该报告");
        }

        List<Report> reports = attentionModelService.getHistoricalReports(session);
        Report report = reports.isEmpty() ? attentionModelService.generateAttentionReport(session) : reports.get(0);
        List<GazeData> gazeDataList = gazeDataService.getGazeDataByTrainingSession(session).stream()
                .sorted(Comparator.comparing(GazeData::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        String normalizedFormat = format == null ? "json" : format.trim().toLowerCase();
        String body;
        String contentType;
        String extension;
        if ("csv".equals(normalizedFormat)) {
            body = buildCsvExport(session, report, gazeDataList);
            contentType = "text/csv;charset=UTF-8";
            extension = "csv";
        } else if ("xml".equals(normalizedFormat)) {
            body = buildXmlExport(session, report, gazeDataList);
            contentType = "application/xml;charset=UTF-8";
            extension = "xml";
        } else {
            body = buildJsonExport(session, report, gazeDataList);
            contentType = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";
            extension = "json";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=training-report-" + sessionId + "." + extension)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(body);
    }

    private List<GazeData> createSimulatedGazeData(TrainingSession session) {
        List<GazeData> records = new ArrayList<>();
        LocalDateTime start = LocalDateTime.now();
        for (int i = 0; i < 120; i++) {
            double progress = i / 119.0;
            double line = Math.floor(progress * 10);
            double normalizedX = clamp((360 + (progress * 900) + Math.random() * 35) / DEFAULT_SCREEN_WIDTH);
            double normalizedY = clamp((120 + line * 34 + Math.random() * 24) / DEFAULT_SCREEN_HEIGHT);
            double gaze3dZ = 600 + Math.random() * 120;
            double gaze3dX = (normalizedX - 0.5) * gaze3dZ;
            double gaze3dY = (0.5 - normalizedY) * gaze3dZ;
            CoordinateMapperService.ScreenCoordinate mapped = coordinateMapperService.processGaze3d(gaze3dX, gaze3dY, gaze3dZ);

            GazeData gazeData = new GazeData();
            gazeData.setTrainingSession(session);
            gazeData.setTimestamp(start.plusNanos(i * 50_000_000L));
            gazeData.setxCoordinate((double) mapped.getX());
            gazeData.setyCoordinate((double) mapped.getY());
            gazeData.setzCoordinate(gaze3dZ);
            gazeData.setGaze3dX(gaze3dX);
            gazeData.setGaze3dY(gaze3dY);
            gazeData.setGaze3dZ(gaze3dZ);
            gazeData.setPupilDiameter(3.0 + Math.random() * 0.8);
            gazeData.setLeftPupilDiameter(gazeData.getPupilDiameter());
            gazeData.setRightPupilDiameter(gazeData.getPupilDiameter() + (Math.random() - 0.5) * 0.2);
            gazeData.setFixationType("FIXATION");
            gazeData.setFixationDuration(160 + (int) (Math.random() * 180));
            gazeData.setAreaOfInterest("code");
            gazeData.setLineNumber((int) line + 1);
            gazeData.setTargetMatched(i % 3 != 0);
            gazeData.setTargetRegion(Boolean.TRUE.equals(gazeData.getTargetMatched()) ? "target-code" : "code");
            records.add(gazeData);
        }
        return records;
    }

    private List<GazeData> convertToGazeData(TrainingSession session, List<Map<String, Object>> rawData) {
        List<GazeData> records = new ArrayList<>();
        for (Map<String, Object> data : rawData) {
            GazeData gazeData = new GazeData();
            gazeData.setTrainingSession(session);
            gazeData.setTimestamp(readTimestamp(data));

            Double gaze3dX = firstNonNull(readDouble(data, "gaze3dX"), readDouble(data, "x"));
            Double gaze3dY = firstNonNull(readDouble(data, "gaze3dY"), readDouble(data, "y"));
            Double gaze3dZ = firstNonNull(readDouble(data, "gaze3dZ"), readDouble(data, "z"));
            Double fallbackU = firstNonNull(readDouble(data, "screenNormalizedX"), readDouble(data, "gaze2dX"));
            Double fallbackV = firstNonNull(readDouble(data, "screenNormalizedY"), readDouble(data, "gaze2dY"));
            CoordinateMapperService.ScreenCoordinate mapped = coordinateMapperService.processGaze3d(
                    gaze3dX,
                    gaze3dY,
                    gaze3dZ,
                    fallbackU,
                    fallbackV
            );

            gazeData.setxCoordinate((double) mapped.getX());
            gazeData.setyCoordinate((double) mapped.getY());
            gazeData.setzCoordinate(valueOrDefault(gaze3dZ, 0.0));
            gazeData.setGaze3dX(gaze3dX);
            gazeData.setGaze3dY(gaze3dY);
            gazeData.setGaze3dZ(gaze3dZ);

            Double leftPupil = readDouble(data, "leftPupil");
            Double rightPupil = readDouble(data, "rightPupil");
            gazeData.setLeftPupilDiameter(leftPupil);
            gazeData.setRightPupilDiameter(rightPupil);
            gazeData.setPupilDiameter(averagePupilDiameter(leftPupil, rightPupil));
            gazeData.setGazeOriginX(readDouble(data, "gazeOriginX"));
            gazeData.setGazeOriginY(readDouble(data, "gazeOriginY"));
            gazeData.setGazeOriginZ(readDouble(data, "gazeOriginZ"));
            gazeData.setGazeDirectionX(readDouble(data, "gazeDirectionX"));
            gazeData.setGazeDirectionY(readDouble(data, "gazeDirectionY"));
            gazeData.setGazeDirectionZ(readDouble(data, "gazeDirectionZ"));

            gazeData.setFixationType(readString(data, "fixationType", "FIXATION"));
            gazeData.setFixationDuration(readInt(data, "fixationDuration", 120));
            gazeData.setAreaOfInterest(readString(data, "areaOfInterest", "code"));
            gazeData.setLineNumber(readNullableInt(data, "lineNumber"));
            gazeData.setTargetMatched(readBoolean(data, "targetMatched"));
            gazeData.setTargetRegion(readString(data, "targetRegion", gazeData.getAreaOfInterest()));
            records.add(gazeData);
        }
        return records;
    }

    private LocalDateTime readTimestamp(Map<String, Object> data) {
        Double timestamp = readDouble(data, "timestamp");
        if (timestamp == null) {
            return LocalDateTime.now();
        }
        long rawTimestamp = timestamp.longValue();
        long epochMillis;
        if (rawTimestamp > 100_000_000_000_000L) {
            epochMillis = rawTimestamp / 1_000_000L;
        } else if (rawTimestamp < 100_000_000_000L) {
            epochMillis = rawTimestamp * 1000L;
        } else {
            epochMillis = rawTimestamp;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private Double readDouble(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double firstNonNull(Double... values) {
        if (values == null) {
            return null;
        }
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private int readInt(Map<String, Object> data, String key, int defaultValue) {
        Double value = readDouble(data, key);
        return value == null ? defaultValue : Math.max(0, value.intValue());
    }

    private Integer readNullableInt(Map<String, Object> data, String key) {
        Double value = readDouble(data, key);
        return value == null ? null : value.intValue();
    }

    private Boolean readBoolean(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }

    private String readString(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            return defaultValue;
        }
        return value.toString().trim();
    }

    private Double averagePupilDiameter(Double leftPupil, Double rightPupil) {
        if (leftPupil != null && rightPupil != null) {
            return (leftPupil + rightPupil) / 2.0;
        }
        if (leftPupil != null) {
            return leftPupil;
        }
        if (rightPupil != null) {
            return rightPupil;
        }
        return 3.5;
    }

    private double valueOrDefault(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    private void updateTrainingProgress(TrainingSession session, List<GazeData> records) {
        if (session == null || session.getId() == null || records == null) {
            return;
        }

        Set<Integer> targetLines = new LinkedHashSet<>();
        if (session.getTrainingLevelId() != null) {
            CodeRepositoryService.CodeExample codeExample = codeRepositoryService.getCodeExampleById(session.getTrainingLevelId());
            if (codeExample != null) {
                targetLines.addAll(parseTargetLines(codeExample.getTargetLines()));
            }
        }

        int targetSamples = 0;
        int targetHits = 0;
        Set<Integer> completedTargetLines = new LinkedHashSet<>();
        for (GazeData record : records) {
            if (record.getLineNumber() == null) {
                continue;
            }
            targetSamples++;
            if (Boolean.TRUE.equals(record.getTargetMatched())) {
                targetHits++;
                completedTargetLines.add(record.getLineNumber());
            }
        }

        double accuracy = targetSamples == 0 ? 0.0 : targetHits * 100.0 / targetSamples;
        double completionRate;
        if (targetLines.isEmpty()) {
            completionRate = records.isEmpty() ? 0.0 : 100.0;
        } else {
            completedTargetLines.retainAll(targetLines);
            completionRate = completedTargetLines.size() * 100.0 / targetLines.size();
        }
        trainingSessionService.updateTrainingResult(session.getId(), completionRate, accuracy, targetHits, targetSamples);
    }

    private void addReportMetrics(Model model, Report report) {
        double attentionScore = parseDouble(report.getAttentionScore());
        double effectiveFixationRate = parseDouble(report.getEffectiveFixationRate());
        int regressionCount = (int) parseDouble(report.getRegressionCount());
        double saccadeEntropy = parseDouble(report.getSaccadeEntropy());
        double dataQualityScore = parseDouble(report.getDataQualityScore());

        model.addAttribute("scoreLevel", attentionScore >= 80 ? "优秀" : (attentionScore >= 60 ? "良好" : "需改进"));
        model.addAttribute("scoreBadgeClass", attentionScore >= 80 ? "badge bg-success" : (attentionScore >= 60 ? "badge bg-warning" : "badge bg-danger"));
        model.addAttribute("fixationBarWidth", Math.min(100, effectiveFixationRate));
        model.addAttribute("regressionBarWidth", Math.min(100, regressionCount * 5));
        model.addAttribute("entropyBarWidth", Math.min(100, saccadeEntropy * 100));
        model.addAttribute("qualityBarWidth", Math.min(100, dataQualityScore * 100));
        model.addAttribute("entropyDisplay", String.format("%.2f", saccadeEntropy));
        model.addAttribute("qualityDisplay", String.format("%.1f", dataQualityScore * 100));
    }

    private void addReportVisualizationData(Model model, TrainingSession session) {
        List<Map<String, Object>> gazePoints = new ArrayList<>();
        Map<Integer, Integer> lineFixationCounts = new LinkedHashMap<>();
        List<GazeData> gazeDataList = gazeDataService.getGazeDataByTrainingSession(session).stream()
                .filter(data -> data.getxCoordinate() != null && data.getyCoordinate() != null)
                .sorted(Comparator.comparing(GazeData::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        for (GazeData data : gazeDataList) {
            Map<String, Object> point = new HashMap<>();
            point.put("x", clamp(data.getxCoordinate() / DEFAULT_SCREEN_WIDTH));
            point.put("y", clamp(data.getyCoordinate() / DEFAULT_SCREEN_HEIGHT));
            point.put("duration", data.getFixationDuration() == null ? 0 : data.getFixationDuration());
            point.put("area", data.getAreaOfInterest() == null ? "code" : data.getAreaOfInterest());
            point.put("lineNumber", data.getLineNumber());
            point.put("targetMatched", Boolean.TRUE.equals(data.getTargetMatched()));
            point.put("gaze3dX", data.getGaze3dX());
            point.put("gaze3dY", data.getGaze3dY());
            point.put("gaze3dZ", data.getGaze3dZ() == null ? data.getzCoordinate() : data.getGaze3dZ());
            gazePoints.add(point);
            if (data.getLineNumber() != null) {
                lineFixationCounts.merge(data.getLineNumber(), 1, Integer::sum);
            }
        }

        model.addAttribute("gazePoints", gazePoints);
        model.addAttribute("gazePointCount", gazePoints.size());
        model.addAttribute("lineFixationCounts", lineFixationCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("line", entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList()));
        model.addAttribute("targetAccuracyDisplay", session.getAccuracy() == null ? "0.0" : String.format("%.1f", session.getAccuracy()));
        model.addAttribute("completionRateDisplay", session.getCompletionRate() == null ? "0.0" : String.format("%.1f", session.getCompletionRate()));
    }

    private void addTrainingSettings(Model model, AppSettingsService.UserSettings settings) {
        model.addAttribute("glassesAddress", settings.getGlassesBaseUrl());
        model.addAttribute("autoStart", settings.isAutoStart());
        model.addAttribute("showHeatmap", settings.isShowHeatmap());
        model.addAttribute("simulationMode", settings.isSimulationMode());
        model.addAttribute("targetTrainingDuration", settings.getTargetDurationSeconds());
        model.addAttribute("gazeIntervalMs", settings.getDataIntervalMs());
        model.addAttribute("simulationIntervalMs", settings.getSimulationIntervalMs());
    }

    private String showTrainingSession(
            TrainingSession session,
            CodeRepositoryService.CodeExample codeExample,
            Model model,
            HttpSession httpSession) {
        model.addAttribute("trainingSession", session);
        model.addAttribute("codeExample", codeExample);
        addTrainingLevelAttributes(model, codeExample);
        addTrainingSettings(model, appSettingsService.load(httpSession));
        return "training/training";
    }

    private void addTrainingLevelAttributes(Model model, CodeRepositoryService.CodeExample codeExample) {
        String targetLines = codeExample.getTargetLines() == null ? "" : codeExample.getTargetLines();
        model.addAttribute("targetLinesRaw", targetLines);
        model.addAttribute("targetLineNumbers", parseTargetLines(targetLines));
        model.addAttribute("taskType", codeExample.getTaskType() == null ? "highlight_follow" : codeExample.getTaskType());
        model.addAttribute("taskGuidance", codeExample.getGuidance() == null ? "" : codeExample.getGuidance());
    }

    private User getCurrentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByUsername(principal.getName()).orElse(null);
    }

    private boolean ownsSession(TrainingSession session, User user) {
        return session != null
                && session.getUser() != null
                && user != null
                && session.getUser().getId() != null
                && session.getUser().getId().equals(user.getId());
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Set<Integer> parseTargetLines(String targetLines) {
        Set<Integer> lines = new LinkedHashSet<>();
        if (targetLines == null || targetLines.trim().isEmpty()) {
            return lines;
        }
        String[] parts = targetLines.split(",");
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.contains("-")) {
                String[] bounds = token.split("-", 2);
                Integer start = parseInteger(bounds[0]);
                Integer end = parseInteger(bounds[1]);
                if (start != null && end != null) {
                    int from = Math.min(start, end);
                    int to = Math.max(start, end);
                    for (int line = from; line <= to; line++) {
                        lines.add(line);
                    }
                }
            } else {
                Integer line = parseInteger(token);
                if (line != null) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private Integer parseInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildJsonExport(TrainingSession session, Report report, List<GazeData> gazeDataList) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("session", sessionExport(session));
        root.put("report", reportExport(report));
        root.put("gazeData", gazeDataList.stream().map(this::gazeExport).collect(Collectors.toList()));
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\":\"报告导出失败\"}";
        }
    }

    private String buildCsvExport(TrainingSession session, Report report, List<GazeData> gazeDataList) {
        StringBuilder csv = new StringBuilder();
        csv.append("section,key,value\n");
        appendCsv(csv, "session", "id", session.getId());
        appendCsv(csv, "session", "name", session.getSessionName());
        appendCsv(csv, "session", "completionRate", session.getCompletionRate());
        appendCsv(csv, "session", "accuracy", session.getAccuracy());
        appendCsv(csv, "report", "attentionScore", report.getAttentionScore());
        appendCsv(csv, "report", "effectiveFixationRate", report.getEffectiveFixationRate());
        appendCsv(csv, "report", "regressionCount", report.getRegressionCount());
        csv.append("\n");
        csv.append("timestamp,screenX,screenY,gaze3dX,gaze3dY,gaze3dZ,lineNumber,targetMatched,targetRegion,fixationDuration,leftPupil,rightPupil\n");
        for (GazeData data : gazeDataList) {
            csv.append(csvValue(data.getTimestamp())).append(',')
                    .append(csvValue(data.getxCoordinate())).append(',')
                    .append(csvValue(data.getyCoordinate())).append(',')
                    .append(csvValue(data.getGaze3dX())).append(',')
                    .append(csvValue(data.getGaze3dY())).append(',')
                    .append(csvValue(data.getGaze3dZ() == null ? data.getzCoordinate() : data.getGaze3dZ())).append(',')
                    .append(csvValue(data.getLineNumber())).append(',')
                    .append(csvValue(data.getTargetMatched())).append(',')
                    .append(csvValue(data.getTargetRegion())).append(',')
                    .append(csvValue(data.getFixationDuration())).append(',')
                    .append(csvValue(data.getLeftPupilDiameter())).append(',')
                    .append(csvValue(data.getRightPupilDiameter())).append('\n');
        }
        return csv.toString();
    }

    private String buildXmlExport(TrainingSession session, Report report, List<GazeData> gazeDataList) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<trainingReport>\n");
        xml.append("  <session>\n");
        sessionExport(session).forEach((key, value) ->
                xml.append("    <").append(key).append(">").append(xmlValue(value)).append("</").append(key).append(">\n"));
        xml.append("  </session>\n");
        xml.append("  <report>\n");
        reportExport(report).forEach((key, value) ->
                xml.append("    <").append(key).append(">").append(xmlValue(value)).append("</").append(key).append(">\n"));
        xml.append("  </report>\n");
        xml.append("  <gazeData>\n");
        for (GazeData data : gazeDataList) {
            xml.append("    <point>\n");
            gazeExport(data).forEach((key, value) ->
                    xml.append("      <").append(key).append(">").append(xmlValue(value)).append("</").append(key).append(">\n"));
            xml.append("    </point>\n");
        }
        xml.append("  </gazeData>\n</trainingReport>\n");
        return xml.toString();
    }

    private Map<String, Object> sessionExport(TrainingSession session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", session.getId());
        data.put("name", session.getSessionName());
        data.put("trainingLevelId", session.getTrainingLevelId());
        data.put("taskType", session.getTaskType());
        data.put("startTime", session.getStartTime());
        data.put("endTime", session.getEndTime());
        data.put("durationMinutes", session.getDuration());
        data.put("status", session.getStatus());
        data.put("completionRate", session.getCompletionRate());
        data.put("accuracy", session.getAccuracy());
        data.put("targetHits", session.getTargetHits());
        data.put("targetSamples", session.getTargetSamples());
        return data;
    }

    private Map<String, Object> reportExport(Report report) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", report.getGeneratedAt());
        data.put("attentionScore", report.getAttentionScore());
        data.put("focusPattern", report.getFocusPattern());
        data.put("effectiveFixationRate", report.getEffectiveFixationRate());
        data.put("regressionCount", report.getRegressionCount());
        data.put("saccadeEntropy", report.getSaccadeEntropy());
        data.put("dataQualityScore", report.getDataQualityScore());
        data.put("averageFixationDuration", report.getAverageFixationDuration());
        data.put("saccadePathLength", report.getSaccadePathLength());
        data.put("recommendations", report.getRecommendations());
        return data;
    }

    private Map<String, Object> gazeExport(GazeData data) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("timestamp", data.getTimestamp());
        point.put("screenX", data.getxCoordinate());
        point.put("screenY", data.getyCoordinate());
        point.put("gaze3dX", data.getGaze3dX());
        point.put("gaze3dY", data.getGaze3dY());
        point.put("gaze3dZ", data.getGaze3dZ() == null ? data.getzCoordinate() : data.getGaze3dZ());
        point.put("x", data.getxCoordinate());
        point.put("y", data.getyCoordinate());
        point.put("z", data.getzCoordinate());
        point.put("lineNumber", data.getLineNumber());
        point.put("targetMatched", data.getTargetMatched());
        point.put("targetRegion", data.getTargetRegion());
        point.put("areaOfInterest", data.getAreaOfInterest());
        point.put("fixationDuration", data.getFixationDuration());
        point.put("leftPupilDiameter", data.getLeftPupilDiameter());
        point.put("rightPupilDiameter", data.getRightPupilDiameter());
        return point;
    }

    private void appendCsv(StringBuilder csv, String section, String key, Object value) {
        csv.append(csvValue(section)).append(',')
                .append(csvValue(key)).append(',')
                .append(csvValue(value)).append('\n');
    }

    private String csvValue(Object value) {
        String text = value == null ? "" : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String xmlValue(Object value) {
        String text = value == null ? "" : value.toString();
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
