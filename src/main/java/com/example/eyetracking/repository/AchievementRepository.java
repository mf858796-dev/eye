package com.example.eyetracking.repository;

import com.example.eyetracking.model.Achievement;
import com.example.eyetracking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, Long> {
    List<Achievement> findByUserOrderByUnlockedAtDesc(User user);
    boolean existsByUserAndBadgeCode(User user, String badgeCode);
    long countByUser(User user);
}
