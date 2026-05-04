package com.example.eyetracking.repository;

import com.example.eyetracking.model.Report;
import com.example.eyetracking.model.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByTrainingSession(TrainingSession trainingSession);
    List<Report> findByTrainingSessionId(Long trainingSessionId);
}