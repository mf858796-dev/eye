package com.example.eyetracking.service;

import com.example.eyetracking.model.Achievement;
import com.example.eyetracking.model.Report;
import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.AchievementRepository;
import com.example.eyetracking.repository.TrainingSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AchievementService {
    @Autowired
    private AchievementRepository achievementRepository;
    @Autowired
    private TrainingSessionRepository trainingSessionRepository;

    public List<Achievement> getUserAchievements(User user) {
        if (user == null) {
            return new ArrayList<>();
        }
        return achievementRepository.findByUserOrderByUnlockedAtDesc(user);
    }

    public long countUserAchievements(User user) {
        if (user == null) {
            return 0;
        }
        return achievementRepository.countByUser(user);
    }

    public List<Achievement> evaluateAndUnlock(User user, TrainingSession session, Report report) {
        List<Achievement> unlocked = new ArrayList<>();
        if (user == null || session == null || report == null) {
            return unlocked;
        }

        unlocked.addAll(unlock(user, "FIRST_TRAINING", "初次完成", "完成第一次代码阅读训练。"));

        double score = parseDouble(report.getAttentionScore());
        if (score >= 80) {
            unlocked.addAll(unlock(user, "HIGH_SCORE", "专注达人", "单次训练注意力分数达到 80 分以上。"));
        }
        if (score >= 90) {
            unlocked.addAll(unlock(user, "EXCELLENT_SCORE", "极致专注", "单次训练注意力分数达到 90 分以上。"));
        }

        double accuracy = session.getAccuracy() == null ? 0.0 : session.getAccuracy();
        if (accuracy >= 80) {
            unlocked.addAll(unlock(user, "TARGET_MASTER", "目标锁定", "目标代码行命中率达到 80% 以上。"));
        }

        long completedCount = trainingSessionRepository.findByUser(user).stream()
                .filter(item -> "COMPLETED".equals(item.getStatus()))
                .count();
        if (completedCount >= 3) {
            unlocked.addAll(unlock(user, "THREE_SESSIONS", "连续练习", "累计完成 3 次训练。"));
        }
        if (completedCount >= 10) {
            unlocked.addAll(unlock(user, "TEN_SESSIONS", "稳定训练者", "累计完成 10 次训练。"));
        }

        if ("bug_hunt".equals(session.getTaskType())) {
            unlocked.addAll(unlock(user, "BUG_HUNTER", "Bug 捕手", "完成一次找 Bug 视线追踪训练。"));
        }

        return unlocked;
    }

    private List<Achievement> unlock(User user, String code, String name, String description) {
        List<Achievement> result = new ArrayList<>();
        if (achievementRepository.existsByUserAndBadgeCode(user, code)) {
            return result;
        }

        Achievement achievement = new Achievement();
        achievement.setUser(user);
        achievement.setBadgeCode(code);
        achievement.setBadgeName(name);
        achievement.setDescription(description);
        result.add(achievementRepository.save(achievement));
        return result;
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
}
