package com.example.eyetracking.service;

import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.TrainingSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TrainingSessionService {
    @Autowired
    private TrainingSessionRepository trainingSessionRepository;

    // 创建训练会话
    public TrainingSession createTrainingSession(User user, String sessionName) {
        return createTrainingSession(user, sessionName, null, null);
    }

    public TrainingSession createTrainingSession(User user, String sessionName, Long trainingLevelId, String taskType) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("训练用户不能为空");
        }
        if (sessionName == null || sessionName.trim().isEmpty()) {
            throw new IllegalArgumentException("训练会话名称不能为空");
        }

        TrainingSession session = new TrainingSession();
        session.setUser(user);
        session.setSessionName(sessionName.trim());
        session.setTrainingLevelId(trainingLevelId);
        session.setTaskType(taskType);
        session.setStartTime(LocalDateTime.now());
        session.setStatus("ACTIVE");
        session.setCompletionRate(0.0);
        session.setAccuracy(0.0);
        session.setTargetHits(0);
        session.setTargetSamples(0);
        return trainingSessionRepository.save(session);
    }

    // 结束训练会话
    public TrainingSession endTrainingSession(Long sessionId) {
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("训练会话不存在"));
        
        session.setEndTime(LocalDateTime.now());
        session.setStatus("COMPLETED");
        
        // 计算持续时间（分钟）
        long durationMinutes = java.time.Duration.between(session.getStartTime(), session.getEndTime()).toMinutes();
        session.setDuration((int) durationMinutes);
        
        return trainingSessionRepository.save(session);
    }

    // 获取用户的训练会话列表
    public List<TrainingSession> getUserTrainingSessions(User user) {
        return trainingSessionRepository.findByUser(user).stream()
                .sorted(Comparator.comparing(TrainingSession::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    // 获取用户的训练会话列表
    public List<TrainingSession> getUserTrainingSessions(Long userId) {
        return trainingSessionRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(TrainingSession::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    // 获取训练会话详情
    public TrainingSession getTrainingSessionById(Long sessionId) {
        return trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("训练会话不存在"));
    }

    // 更新训练会话状态
    public TrainingSession updateTrainingSessionStatus(Long sessionId, String status) {
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("训练会话不存在"));
        session.setStatus(status);
        return trainingSessionRepository.save(session);
    }

    public TrainingSession updateTrainingResult(Long sessionId, double completionRate, double accuracy,
                                                int targetHits, int targetSamples) {
        TrainingSession session = trainingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("训练会话不存在"));
        session.setCompletionRate(round(completionRate));
        session.setAccuracy(round(accuracy));
        session.setTargetHits(targetHits);
        session.setTargetSamples(targetSamples);
        return trainingSessionRepository.save(session);
    }

    // 删除训练会话
    public void deleteTrainingSession(Long sessionId) {
        if (!trainingSessionRepository.existsById(sessionId)) {
            throw new RuntimeException("训练会话不存在");
        }
        trainingSessionRepository.deleteById(sessionId);
    }

    private double round(double value) {
        return Math.round(Math.max(0.0, Math.min(100.0, value)) * 100.0) / 100.0;
    }
}
