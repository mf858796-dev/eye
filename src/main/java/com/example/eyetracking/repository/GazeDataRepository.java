package com.example.eyetracking.repository;

import com.example.eyetracking.model.GazeData;
import com.example.eyetracking.model.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GazeDataRepository extends JpaRepository<GazeData, Long> {
    List<GazeData> findByTrainingSession(TrainingSession trainingSession);
    List<GazeData> findByTrainingSessionId(Long trainingSessionId);
}