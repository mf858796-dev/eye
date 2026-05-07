package com.example.eyetracking.controller;

import com.example.eyetracking.model.Report;
import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.UserRepository;
import com.example.eyetracking.service.AchievementService;
import com.example.eyetracking.service.AttentionModelService;
import com.example.eyetracking.service.CodeRepositoryService;
import com.example.eyetracking.service.TobiiGlassesService;
import com.example.eyetracking.service.TrainingSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class HomeController {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrainingSessionService trainingSessionService;
    @Autowired
    private AttentionModelService attentionModelService;
    @Autowired
    private AchievementService achievementService;
    @Autowired
    private TobiiGlassesService tobiiGlassesService;
    @Autowired
    private CodeRepositoryService codeRepositoryService;

    @GetMapping("/")
    public String home(Principal principal) {
        if (principal != null) {
            return "redirect:/dashboard";
        }
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        User user = getCurrentUser(principal);
        if (user == null) {
            return "redirect:/user/login";
        }

        List<TrainingSession> sessions = trainingSessionService.getUserTrainingSessions(user);
        List<CodeRepositoryService.CodeExample> codeExamples = codeRepositoryService.getAllCodeExamples();
        int totalSessions = sessions.size();
        long completedSessions = sessions.stream()
                .filter(session -> "COMPLETED".equals(session.getStatus()))
                .count();
        int totalDurationMinutes = sessions.stream()
                .map(TrainingSession::getDuration)
                .filter(duration -> duration != null && duration > 0)
                .mapToInt(Integer::intValue)
                .sum();
        double averageAttentionScore = calculateAverageAttentionScore(sessions);
        int completionRate = totalSessions == 0 ? 0 : (int) Math.round(completedSessions * 100.0 / totalSessions);
        List<TrainingSession> recentSessions = sessions.stream()
                .sorted(Comparator.comparing(TrainingSession::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .collect(Collectors.toList());
        TrainingSession activeSession = sessions.stream()
                .filter(session -> "ACTIVE".equals(session.getStatus()))
                .findFirst()
                .orElse(null);
        CodeRepositoryService.CodeExample nextCodeExample = findNextCodeExample(sessions, codeExamples);

        model.addAttribute("displayName", displayName(user));
        model.addAttribute("totalSessions", totalSessions);
        model.addAttribute("totalDurationHours", String.format("%.1f", totalDurationMinutes / 60.0));
        model.addAttribute("averageAttentionScore", String.format("%.0f", averageAttentionScore));
        model.addAttribute("completionRate", completionRate);
        model.addAttribute("completedSessions", completedSessions);
        model.addAttribute("achievementCount", achievementService.countUserAchievements(user));
        model.addAttribute("tobiiConnected", tobiiGlassesService.isConnected());
        model.addAttribute("levelCount", codeExamples.size());
        model.addAttribute("hasTrainingData", completedSessions > 0);
        model.addAttribute("latestSession", recentSessions.isEmpty() ? null : recentSessions.get(0));
        model.addAttribute("activeSession", activeSession);
        model.addAttribute("activeSessionResumable", activeSession != null && activeSession.getTrainingLevelId() != null);
        model.addAttribute("nextCodeExample", nextCodeExample);
        model.addAttribute("recentSessions", recentSessions);
        return "dashboard";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    private double calculateAverageAttentionScore(List<TrainingSession> sessions) {
        double sum = 0.0;
        int count = 0;
        for (TrainingSession session : sessions) {
            List<Report> reports = attentionModelService.getHistoricalReports(session);
            if (reports.isEmpty()) {
                continue;
            }
            try {
                sum += Double.parseDouble(reports.get(0).getAttentionScore());
                count++;
            } catch (NumberFormatException ignored) {
                // Ignore malformed historical report values.
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private User getCurrentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByUsername(principal.getName()).orElse(null);
    }

    private CodeRepositoryService.CodeExample findNextCodeExample(
            List<TrainingSession> sessions,
            List<CodeRepositoryService.CodeExample> codeExamples) {
        if (codeExamples == null || codeExamples.isEmpty()) {
            return null;
        }

        Set<Long> completedLevelIds = sessions.stream()
                .filter(session -> "COMPLETED".equals(session.getStatus()))
                .map(TrainingSession::getTrainingLevelId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));

        return codeExamples.stream()
                .filter(example -> !completedLevelIds.contains(example.getId()))
                .findFirst()
                .orElse(codeExamples.get(0));
    }

    private String displayName(User user) {
        if (user.getName() != null && !user.getName().trim().isEmpty()) {
            return user.getName().trim();
        }
        return user.getUsername();
    }
}
