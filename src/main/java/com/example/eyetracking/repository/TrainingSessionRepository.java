package com.example.eyetracking.repository;

import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    List<TrainingSession> findByUser(User user);
    List<TrainingSession> findByUserId(Long userId);
}