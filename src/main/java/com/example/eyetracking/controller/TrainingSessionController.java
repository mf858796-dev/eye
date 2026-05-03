package com.example.eyetracking.controller;

import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.model.User;
import com.example.eyetracking.service.TrainingSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/training")
public class TrainingSessionController {
    @Autowired
    private TrainingSessionService trainingSessionService;

    @GetMapping("/list")
    public String listSessions(Model model) {
        // 这里应该从会话中获取当前用户
        // 暂时使用模拟数据
        Long userId = 1L; // 假设用户ID为1
        List<TrainingSession> sessions = trainingSessionService.getUserTrainingSessions(userId);
        model.addAttribute("sessions", sessions);
        return "training/list";
    }

    @GetMapping("/create")
    public String createSession() {
        return "training/create";
    }

    @PostMapping("/create")
    public String createSessionSubmit(@RequestParam String sessionName, Model model) {
        // 这里应该从会话中获取当前用户
        // 暂时使用模拟数据
        User user = new User();
        user.setId(1L); // 假设用户ID为1
        
        TrainingSession session = trainingSessionService.createTrainingSession(user, sessionName);
        model.addAttribute("message", "训练会话创建成功");
        model.addAttribute("trainingSession", session);
        return "training/detail";
    }

    @GetMapping("/detail/{id}")
    public String sessionDetail(@PathVariable Long id, Model model) {
        TrainingSession session = trainingSessionService.getTrainingSessionById(id);
        model.addAttribute("trainingSession", session);
        return "training/detail";
    }

    @GetMapping("/start/{id}")
    public String startSession(@PathVariable Long id, Model model) {
        TrainingSession session = trainingSessionService.getTrainingSessionById(id);
        session = trainingSessionService.updateTrainingSessionStatus(id, "ACTIVE");
        model.addAttribute("trainingSession", session);
        model.addAttribute("message", "训练会话已开始");
        return "training/detail";
    }

    @GetMapping("/end/{id}")
    public String endSession(@PathVariable Long id, Model model) {
        TrainingSession session = trainingSessionService.endTrainingSession(id);
        model.addAttribute("trainingSession", session);
        model.addAttribute("message", "训练会话已结束");
        return "training/detail";
    }

    @GetMapping("/delete/{id}")
    public String deleteSession(@PathVariable Long id, Model model) {
        // 这里应该添加删除逻辑
        model.addAttribute("message", "训练会话已删除");
        return "redirect:/training/list";
    }
}