package com.example.eyetracking.repository;

import com.example.eyetracking.model.CalibrationData;
import com.example.eyetracking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 校准数据仓库接口
 */
@Repository
public interface CalibrationDataRepository extends JpaRepository<CalibrationData, Long> {
    
    /**
     * 根据用户查询校准数据
     */
    List<CalibrationData> findByUser(User user);
    
    /**
     * 根据用户查询最新的校准数据
     */
    List<CalibrationData> findByUserOrderByCalibrationTimeDesc(User user);
    
    /**
     * 根据用户和状态查询校准数据
     */
    List<CalibrationData> findByUserAndStatus(User user, String status);
}