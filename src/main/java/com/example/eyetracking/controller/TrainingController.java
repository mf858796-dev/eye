package com.example.eyetracking.controller;

import com.example.eyetracking.model.GazeData;
import com.example.eyetracking.model.Report;
import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.UserRepository;
import com.example.eyetracking.service.AttentionModelService;
import com.example.eyetracking.service.CodeRepositoryService;
import com.example.eyetracking.service.GazeDataService;
import com.example.eyetracking.service.TrainingSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/training")
public class TrainingController {
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

    // 代码示例列表
    @GetMapping("/code-examples")
    public String codeExamples(Model model) {
        List<CodeRepositoryService.CodeExample> codeExamples = codeRepositoryService.getAllCodeExamples();
        model.addAttribute("codeExamples", codeExamples);
        return "training/code-examples";
    }

    // 开始训练
    @GetMapping("/start/code/{codeId}")
    public String startTraining(@PathVariable Long codeId, Model model, Principal principal) {
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
            
            String username = principal.getName();
            com.example.eyetracking.model.User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // 创建训练会话
            TrainingSession session = trainingSessionService.createTrainingSession(user, codeExample.getTitle());

            model.addAttribute("trainingSession", session);
            model.addAttribute("codeExample", codeExample);
            return "training/training";
        } catch (Exception e) {
            model.addAttribute("error", "训练开始失败: " + e.getMessage());
            return "redirect:/training/code-examples";
        }
    }

    // 提交眼动数据
    @PostMapping("/submit-gaze-data")
    public String submitGazeData(@RequestParam Long sessionId, @RequestParam String gazeDataJson, Model model) {
        try {
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

            // 验证gazeDataJson
            if (gazeDataJson == null || gazeDataJson.trim().isEmpty()) {
                model.addAttribute("error", "眼动数据为空");
                return "redirect:/training/code-examples";
            }

            // 解析JSON格式的眼动数据
            ObjectMapper objectMapper = new ObjectMapper();
            List<Map<String, Object>> gazeDataList;
            try {
                gazeDataList = objectMapper.readValue(gazeDataJson, 
                    new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                model.addAttribute("error", "眼动数据格式错误: " + e.getMessage());
                return "redirect:/training/code-examples";
            }

            // 如果没有数据，使用模拟数据
            if (gazeDataList.isEmpty()) {
                for (int i = 0; i < 50; i++) {
                    GazeData gazeData = new GazeData();
                    gazeData.setTrainingSession(session);
                    gazeData.setTimestamp(LocalDateTime.now().plusSeconds(i));
                    gazeData.setxCoordinate(Math.random() * 1920);
                    gazeData.setyCoordinate(Math.random() * 1080);
                    gazeData.setzCoordinate(Math.random() * 100);
                    gazeData.setPupilDiameter(3.0 + Math.random() * 2.0);
                    gazeData.setFixationType("FIXATION");
                    gazeData.setFixationDuration(100 + (int)(Math.random() * 200));
                    gazeData.setAreaOfInterest("code");
                    gazeDataService.saveGazeData(gazeData);
                }
            } else {
                // 保存眼动数据
                for (Map<String, Object> data : gazeDataList) {
                    GazeData gazeData = new GazeData();
                    gazeData.setTrainingSession(session);
                    
                    // 时间戳
                    gazeData.setTimestamp(LocalDateTime.now());
                    
                    // 3D坐标
                    if (data.containsKey("x")) {
                        gazeData.setxCoordinate(((Number) data.get("x")).doubleValue());
                    } else if (data.containsKey("gaze2dX")) {
                        gazeData.setxCoordinate(((Number) data.get("gaze2dX")).doubleValue() * 1920);
                    } else {
                        gazeData.setxCoordinate(Math.random() * 1920);
                    }
                    
                    if (data.containsKey("y")) {
                        gazeData.setyCoordinate(((Number) data.get("y")).doubleValue());
                    } else if (data.containsKey("gaze2dY")) {
                        gazeData.setyCoordinate(((Number) data.get("gaze2dY")).doubleValue() * 1080);
                    } else {
                        gazeData.setyCoordinate(Math.random() * 1080);
                    }
                    
                    if (data.containsKey("z")) {
                        gazeData.setzCoordinate(((Number) data.get("z")).doubleValue());
                    } else {
                        gazeData.setzCoordinate(Math.random() * 100);
                    }
                    
                    // 瞳孔直径
                    if (data.containsKey("leftPupil")) {
                        gazeData.setPupilDiameter(((Number) data.get("leftPupil")).doubleValue());
                    } else if (data.containsKey("rightPupil")) {
                        gazeData.setPupilDiameter(((Number) data.get("rightPupil")).doubleValue());
                    } else {
                        gazeData.setPupilDiameter(3.0 + Math.random() * 2.0);
                    }
                    
                    // Fixation信息
                    if (data.containsKey("fixationType")) {
                        gazeData.setFixationType((String) data.get("fixationType"));
                    } else {
                        gazeData.setFixationType("FIXATION");
                    }
                    
                    if (data.containsKey("fixationDuration")) {
                        gazeData.setFixationDuration(((Number) data.get("fixationDuration")).intValue());
                    } else {
                        gazeData.setFixationDuration(100 + (int)(Math.random() * 200));
                    }
                    
                    gazeData.setAreaOfInterest("code");
                    gazeDataService.saveGazeData(gazeData);
                }
            }

            // 结束训练会话
            session = trainingSessionService.endTrainingSession(sessionId);

            // 生成报告
            Report report = attentionModelService.generateAttentionReport(session);

            if (report == null) {
                model.addAttribute("error", "报告生成失败");
                return "redirect:/training/code-examples";
            }

            // 预先计算进度条值，避免Thymeleaf表达式问题
            double effectiveFixationRate = Double.parseDouble(report.getEffectiveFixationRate());
            int regressionCount = Integer.parseInt(report.getRegressionCount());
            double saccadeEntropy = Double.parseDouble(report.getSaccadeEntropy());
            double dataQualityScore = Double.parseDouble(report.getDataQualityScore());
            
            model.addAttribute("fixationBarWidth", Math.min(100, effectiveFixationRate));
            model.addAttribute("regressionBarWidth", Math.min(100, regressionCount * 5));
            model.addAttribute("entropyBarWidth", saccadeEntropy * 100);
            model.addAttribute("qualityBarWidth", dataQualityScore * 100);
            model.addAttribute("entropyDisplay", String.format("%.2f", saccadeEntropy));
            model.addAttribute("qualityDisplay", String.format("%.1f", dataQualityScore * 100));

            model.addAttribute("trainingSession", session);
            model.addAttribute("report", report);
            return "training/report";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "数据提交失败: " + e.getMessage());
            return "redirect:/training/code-examples";
        }
    }

    // 查看报告
    @GetMapping("/report/{sessionId}")
    public String viewReport(@PathVariable Long sessionId, Model model) {
        try {
            TrainingSession session = trainingSessionService.getTrainingSessionById(sessionId);
            List<Report> reports = attentionModelService.getHistoricalReports(session);
            if (!reports.isEmpty()) {
                model.addAttribute("report", reports.get(0));
            }
            model.addAttribute("trainingSession", session);
            return "training/report";
        } catch (Exception e) {
            model.addAttribute("error", "报告查看失败: " + e.getMessage());
            return "redirect:/training/list";
        }
    }
}