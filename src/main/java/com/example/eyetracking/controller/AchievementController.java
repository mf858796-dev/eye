package com.example.eyetracking.controller;

import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.UserRepository;
import com.example.eyetracking.service.AchievementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class AchievementController {
    @Autowired
    private AchievementService achievementService;
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/achievements")
    public String achievements(Model model, Principal principal) {
        User user = getCurrentUser(principal);
        if (user == null) {
            return "redirect:/user/login";
        }
        model.addAttribute("achievements", achievementService.getUserAchievements(user));
        return "achievements";
    }

    private User getCurrentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByUsername(principal.getName()).orElse(null);
    }
}
