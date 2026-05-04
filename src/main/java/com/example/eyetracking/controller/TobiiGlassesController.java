package com.example.eyetracking.controller;

import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.UserRepository;
import com.example.eyetracking.service.CalibrationService;
import com.example.eyetracking.service.TobiiGlassesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 眼动仪连接测试控制器
 */
@Controller
@RequestMapping("/tobii")
public class TobiiGlassesController {
    
    @Autowired
    private TobiiGlassesService tobiiGlassesService;
    
    @Autowired
    private CalibrationService calibrationService;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 眼动仪测试页面
     */
    @GetMapping("/test")
    public String testPage(Model model) {
        model.addAttribute("connected", tobiiGlassesService.isConnected());
        return "tobii/test";
    }
    
    /**
     * 连接眼动仪
     */
    @PostMapping("/connect")
    public String connect(Model model) {
        boolean success = tobiiGlassesService.connect();
        model.addAttribute("connected", success);
        
        if (success) {
            TobiiGlassesService.GlassesInfo info = tobiiGlassesService.getDeviceInfo();
            model.addAttribute("deviceInfo", info);
            
            // 测试视频流
            boolean streamSuccess = tobiiGlassesService.testVideoStream();
            model.addAttribute("streamSuccess", streamSuccess);
        }
        
        return "tobii/test";
    }
    
    /**
     * 断开连接
     */
    @PostMapping("/disconnect")
    public String disconnect(Model model) {
        tobiiGlassesService.disconnect();
        model.addAttribute("connected", false);
        return "tobii/test";
    }
    
    /**
     * 获取眼动数据
     */
    @GetMapping("/gaze-data")
    public String getGazeData(Model model) {
        try {
            if (tobiiGlassesService.isConnected()) {
                TobiiGlassesService.GazeDataSample sample = tobiiGlassesService.getGazeData().get();
                model.addAttribute("gazeData", sample);
            }
        } catch (Exception e) {
            model.addAttribute("error", "获取眼动数据失败: " + e.getMessage());
        }
        
        model.addAttribute("connected", tobiiGlassesService.isConnected());
        return "tobii/test";
    }
    
    /**
     * 校准页面
     */
    @GetMapping("/calibration")
    public String calibrationPage(Model model) {
        model.addAttribute("connected", tobiiGlassesService.isConnected());
        model.addAttribute("now", LocalDateTime.now());
        return "tobii/calibration";
    }
    
    /**
     * 开始校准
     */
    @PostMapping("/calibration/start")
    public String startCalibration(@RequestBody Map<String, Integer> request, Model model, Principal principal) {
        try {
            if (!tobiiGlassesService.isConnected()) {
                model.addAttribute("error", "请先连接眼动仪");
                model.addAttribute("connected", false);
                return "tobii/calibration";
            }
            
            if (principal == null) {
                model.addAttribute("error", "请先登录");
                return "redirect:/user/login";
            }
            
            String username = principal.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            int screenWidth = request.getOrDefault("screenWidth", 1920);
            int screenHeight = request.getOrDefault("screenHeight", 1080);
            
            CalibrationService.CalibrationResult result = calibrationService.startCalibration(user, screenWidth, screenHeight);
            
            model.addAttribute("calibrationResult", result);
            model.addAttribute("connected", tobiiGlassesService.isConnected());
            model.addAttribute("now", LocalDateTime.now());
            
            if (result.isSuccessful()) {
                model.addAttribute("message", "校准成功！眼动仪已准备就绪");
            } else {
                model.addAttribute("error", "校准失败，请重新尝试");
            }
            
        } catch (Exception e) {
            model.addAttribute("error", "校准失败: " + e.getMessage());
            model.addAttribute("connected", tobiiGlassesService.isConnected());
        }
        
        return "tobii/calibration";
    }
    
    /**
     * 校准历史
     */
    @GetMapping("/calibration/history")
    public String calibrationHistory(Model model, Principal principal) {
        try {
            if (principal == null) {
                return "redirect:/user/login";
            }
            
            String username = principal.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            model.addAttribute("calibrationData", calibrationService.getLatestCalibrationData(user));
            model.addAttribute("connected", tobiiGlassesService.isConnected());
            
        } catch (Exception e) {
            model.addAttribute("error", "获取校准历史失败: " + e.getMessage());
        }
        
        return "tobii/calibration-history";
    }
}
