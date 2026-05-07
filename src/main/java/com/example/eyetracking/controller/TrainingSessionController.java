package com.example.eyetracking.controller;

import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.UserRepository;
import com.example.eyetracking.service.TrainingSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/training")
public class TrainingSessionController {
    @Autowired
    private TrainingSessionService trainingSessionService;
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/list")
    public String listSessions(Model model, Principal principal) {
        User user = getCurrentUser(principal);
        if (user == null) {
            return "redirect:/user/login";
        }

        List<TrainingSession> sessions = trainingSessionService.getUserTrainingSessions(user);
        model.addAttribute("sessions", sessions);
        return "training/list";
    }

    @GetMapping("/create")
    public String createSession(Principal principal, RedirectAttributes redirectAttributes) {
        if (getCurrentUser(principal) == null) {
            return "redirect:/user/login";
        }
        redirectAttributes.addFlashAttribute("message", "请选择一个训练关卡开始，会话会自动创建");
        return "redirect:/training/code-examples";
    }

    @PostMapping("/create")
    public String createSessionSubmit(@RequestParam String sessionName, Principal principal,
                                      RedirectAttributes redirectAttributes) {
        if (getCurrentUser(principal) == null) {
            return "redirect:/user/login";
        }
        redirectAttributes.addFlashAttribute("message", "请选择一个训练关卡开始，会话会自动创建");
        return "redirect:/training/code-examples";
    }

    @GetMapping("/detail/{id}")
    public String sessionDetail(@PathVariable Long id, Model model, Principal principal) {
        TrainingSession session = getOwnedSession(id, principal);
        if (session == null) {
            return "redirect:/training/list";
        }
        model.addAttribute("trainingSession", session);
        return "training/detail";
    }

    @PostMapping("/start/{id}")
    public String startSession(@PathVariable Long id, Model model, Principal principal) {
        TrainingSession session = getOwnedSession(id, principal);
        if (session == null) {
            return "redirect:/training/list";
        }
        if (session.getTrainingLevelId() != null) {
            return "redirect:/training/resume/" + id;
        }
        session = trainingSessionService.updateTrainingSessionStatus(id, "ACTIVE");
        model.addAttribute("trainingSession", session);
        model.addAttribute("message", "训练会话已开始");
        return "training/detail";
    }

    @PostMapping("/end/{id}")
    public String endSession(@PathVariable Long id, Model model, Principal principal) {
        TrainingSession ownedSession = getOwnedSession(id, principal);
        if (ownedSession == null) {
            return "redirect:/training/list";
        }
        TrainingSession session = trainingSessionService.endTrainingSession(id);
        model.addAttribute("trainingSession", session);
        model.addAttribute("message", "训练会话已结束");
        return "training/detail";
    }

    @PostMapping("/delete/{id}")
    public String deleteSession(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        TrainingSession session = getOwnedSession(id, principal);
        if (session == null) {
            return "redirect:/training/list";
        }
        trainingSessionService.deleteTrainingSession(id);
        redirectAttributes.addFlashAttribute("message", "训练会话已删除");
        return "redirect:/training/list";
    }

    private User getCurrentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByUsername(principal.getName()).orElse(null);
    }

    private TrainingSession getOwnedSession(Long id, Principal principal) {
        User user = getCurrentUser(principal);
        if (user == null) {
            return null;
        }

        TrainingSession session;
        try {
            session = trainingSessionService.getTrainingSessionById(id);
        } catch (RuntimeException e) {
            return null;
        }
        if (session.getUser() == null || session.getUser().getId() == null || !session.getUser().getId().equals(user.getId())) {
            return null;
        }
        return session;
    }
}
