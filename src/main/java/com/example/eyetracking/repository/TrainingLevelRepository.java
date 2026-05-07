package com.example.eyetracking.repository;

import com.example.eyetracking.model.TrainingLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingLevelRepository extends JpaRepository<TrainingLevel, Long> {
    List<TrainingLevel> findByActiveTrueOrderByLevelNumberAsc();
    List<TrainingLevel> findByActiveOrderByLevelNumberAsc(Boolean active);
    Optional<TrainingLevel> findByLevelNumber(Integer levelNumber);
}
