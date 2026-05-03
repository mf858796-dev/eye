package com.example.eyetracking.service;

import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.TrainingSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TrainingSessionService {
    @Autowired
    private TrainingSessionRepository trainingSessionRepository;

    // 创建训练会话
    public TrainingSession createTrainingSession(User user, String sessionName) {
        TrainingSession session = new TrainingSession();
        session.setUser(user);
        session.setSessionName(sessionName);
        session.setStartTime(LocalDateTime.now());
        session.setStatus("ACTIVE");
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
        return trainingSessionRepository.findByUser(user);
    }

    // 获取用户的训练会话列表
    public List<TrainingSession> getUserTrainingSessions(Long userId) {
        return trainingSessionRepository.findByUserId(userId);
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
}