package com.example.eyetracking.controller;

import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.UserRepository;
import com.example.eyetracking.service.AppSettingsService;
import com.example.eyetracking.service.CalibrationService;
import com.example.eyetracking.service.CoordinateMapperService;
import com.example.eyetracking.service.TobiiGlassesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 眼动仪连接控制器
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

    @Autowired
    private AppSettingsService appSettingsService;

    @Autowired
    private CoordinateMapperService coordinateMapperService;

    @ModelAttribute
    public void addCommonAttributes(Model model, HttpSession session) {
        AppSettingsService.UserSettings settings = appSettingsService.load(session);
        model.addAttribute("glassesAddress", settings.getDeviceBaseUrl());
        model.addAttribute("deviceType", settings.getDeviceType());
        model.addAttribute("deviceLabel", settings.getDeviceLabel());
        model.addAttribute("calibrationPointCount", settings.getCalibrationPointCount());
        model.addAttribute("calibrationPoints", calibrationService.getCalibrationPointCoordinates(settings.getCalibrationPointCount()));
    }

    /**
     * 眼动仪连接页面
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
    public String connect(HttpSession session, RedirectAttributes redirectAttributes) {
        AppSettingsService.UserSettings settings = appSettingsService.load(session);
        boolean success = tobiiGlassesService.connect(settings.getDeviceBaseUrl(), settings.getDeviceType());

        if (success) {
            TobiiGlassesService.GlassesInfo info = tobiiGlassesService.getDeviceInfo();
            redirectAttributes.addFlashAttribute("deviceInfo", info);

            if ("screen".equals(settings.getDeviceType())) {
                redirectAttributes.addFlashAttribute("message", "笔记本屏幕式眼动仪连接成功，可以进行屏幕坐标校准");
            } else {
                boolean streamSuccess = tobiiGlassesService.testVideoStream();
                redirectAttributes.addFlashAttribute("streamSuccess", streamSuccess);
                redirectAttributes.addFlashAttribute("message", streamSuccess
                        ? "眼动仪连接成功，视频流可用"
                        : "眼动仪连接成功，视频流暂不可用");
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "连接失败，请检查设备 IP、端口和网络状态");
        }

        return "redirect:/tobii/test";
    }

    /**
     * 断开连接
     */
    @PostMapping("/disconnect")
    public String disconnect(RedirectAttributes redirectAttributes) {
        tobiiGlassesService.disconnect();
        redirectAttributes.addFlashAttribute("message", "眼动仪已断开连接");
        return "redirect:/tobii/test";
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

    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Object> status(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        response.put("connected", tobiiGlassesService.isConnected());
        response.put("glassesAddress", tobiiGlassesService.isConnected()
                ? tobiiGlassesService.getCurrentGlassesAddress()
                : appSettingsService.resolveDeviceBaseUrl(session));
        response.put("deviceType", tobiiGlassesService.isConnected()
                ? tobiiGlassesService.getCurrentDeviceType()
                : appSettingsService.load(session).getDeviceType());
        response.put("deviceLabel", tobiiGlassesService.isConnected()
                ? tobiiGlassesService.getCurrentDeviceLabel()
                : appSettingsService.load(session).getDeviceLabel());
        return response;
    }

    @GetMapping("/api/gaze")
    @ResponseBody
    public Map<String, Object> gazeData() {
        Map<String, Object> response = new HashMap<>();
        response.put("connected", tobiiGlassesService.isConnected());
        if (!tobiiGlassesService.isConnected()) {
            response.put("error", "眼动仪未连接");
            return response;
        }

        try {
            TobiiGlassesService.GazeDataSample sample = tobiiGlassesService.getGazeData().get();
            if (sample == null) {
                response.put("error", "未获取到眼动数据");
                return response;
            }
            response.put("timestamp", sample.getTimestamp());
            response.put("data", toTobiiData(sample));
        } catch (Exception e) {
            response.put("error", "获取眼动数据失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 校准页面
     */
    @GetMapping("/calibration")
    public String calibrationPage(Model model, HttpSession session) {
        if (!tobiiGlassesService.isConnected()) {
            model.addAttribute("connected", false);
            model.addAttribute("error", "请先连接眼动仪，再进行校准");
            return "tobii/test";
        }
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

    @PostMapping("/calibration/point")
    @ResponseBody
    public Map<String, Object> captureCalibrationPoint(@RequestBody Map<String, Object> request, Principal principal) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!tobiiGlassesService.isConnected()) {
                response.put("successful", false);
                response.put("message", "请先连接眼动仪");
                return response;
            }

            User user = getCurrentUser(principal);
            if (user == null) {
                response.put("successful", false);
                response.put("message", "请先登录");
                return response;
            }

            int pointIndex = readInt(request.get("pointIndex"), 1);
            int pointCount = readInt(request.get("pointCount"), 9);
            int screenWidth = readInt(request.get("screenWidth"), 1920);
            int screenHeight = readInt(request.get("screenHeight"), 1080);
            CalibrationService.CalibrationPointResult result =
                    calibrationService.collectCalibrationPoint(user, pointIndex, pointCount, screenWidth, screenHeight);

            response.put("successful", result.isSuccessful());
            response.put("calibrationPointId", result.getCalibrationPointId());
            response.put("pointIndex", result.getPointIndex());
            response.put("validSamples", result.getValidSamples());
            response.put("requiredSamples", result.getRequiredSamples());
            response.put("error", result.getError());
            response.put("message", result.getMessage());
        } catch (Exception e) {
            response.put("successful", false);
            response.put("message", "校准点采样失败: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/calibration/finish")
    public String finishCalibration(@RequestBody Map<String, Object> request, Model model, Principal principal) {
        try {
            if (!tobiiGlassesService.isConnected()) {
                model.addAttribute("error", "请先连接眼动仪");
                model.addAttribute("connected", false);
                return "tobii/calibration";
            }

            User user = getCurrentUser(principal);
            if (user == null) {
                return "redirect:/user/login";
            }

            int screenWidth = readInt(request.get("screenWidth"), 1920);
            int screenHeight = readInt(request.get("screenHeight"), 1080);
            int pointCount = readInt(request.get("pointCount"), 9);
            List<Long> pointIds = readLongList(request.get("calibrationPointIds"));
            CalibrationService.CalibrationResult result =
                    calibrationService.finishCalibration(user, pointIds, pointCount, screenWidth, screenHeight);

            model.addAttribute("calibrationResult", result);
            model.addAttribute("connected", tobiiGlassesService.isConnected());
            model.addAttribute("now", LocalDateTime.now());
            if (result.isSuccessful()) {
                model.addAttribute("message", result.getMessage());
            } else {
                model.addAttribute("error", result.getMessage());
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
            if (!tobiiGlassesService.isConnected()) {
                model.addAttribute("connected", false);
                model.addAttribute("error", "请先连接眼动仪，再查看校准历史");
                return "tobii/test";
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

    private Map<String, Object> toTobiiData(TobiiGlassesService.GazeDataSample sample) {
        Map<String, Object> data = new HashMap<>();
        data.put("gaze2d", new double[]{sample.getGaze2dX(), sample.getGaze2dY()});
        data.put("gaze3d", new double[]{sample.getX(), sample.getY(), sample.getZ()});
        data.put("gaze2dValid", sample.isGaze2dValid());
        data.put("gaze3dValid", sample.isGaze3dValid());
        data.put("deviceType", tobiiGlassesService.getCurrentDeviceType());

        Double gaze3dX = sample.isGaze3dValid() ? sample.getX() : null;
        Double gaze3dY = sample.isGaze3dValid() ? sample.getY() : null;
        Double gaze3dZ = sample.isGaze3dValid() ? sample.getZ() : null;
        Double fallbackU = sample.isGaze2dValid() ? sample.getGaze2dX() : null;
        Double fallbackV = sample.isGaze2dValid() ? sample.getGaze2dY() : null;
        CoordinateMapperService.ScreenCoordinate mapped = coordinateMapperService.processGaze3d(
                gaze3dX,
                gaze3dY,
                gaze3dZ,
                fallbackU,
                fallbackV
        );
        Map<String, Object> mappedGaze = new HashMap<>();
        mappedGaze.put("screenX", mapped.getX());
        mappedGaze.put("screenY", mapped.getY());
        mappedGaze.put("normalizedX", mapped.getNormalizedU());
        mappedGaze.put("normalizedY", mapped.getNormalizedV());
        mappedGaze.put("source", sample.isGaze3dValid() ? "gaze3d" : "gaze2d-fallback");
        data.put("gaze", mappedGaze);

        Map<String, Object> eyeLeft = new HashMap<>();
        eyeLeft.put("gazeorigin", new double[]{
                sample.getGazeOriginX(),
                sample.getGazeOriginY(),
                sample.getGazeOriginZ()
        });
        eyeLeft.put("gazedirection", new double[]{
                sample.getGazeDirectionX(),
                sample.getGazeDirectionY(),
                sample.getGazeDirectionZ()
        });
        eyeLeft.put("pupildiameter", sample.getLeftPupilDiameter());
        data.put("eyeleft", eyeLeft);

        Map<String, Object> eyeRight = new HashMap<>();
        eyeRight.put("pupildiameter", sample.getRightPupilDiameter());
        data.put("eyeright", eyeRight);
        return data;
    }

    private User getCurrentUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userRepository.findByUsername(principal.getName()).orElse(null);
    }

    private int readInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private List<Long> readLongList(Object value) {
        List<Long> ids = new ArrayList<>();
        if (!(value instanceof Iterable)) {
            return ids;
        }
        for (Object item : (Iterable<?>) value) {
            if (item instanceof Number) {
                ids.add(((Number) item).longValue());
            } else if (item instanceof String) {
                try {
                    ids.add(Long.parseLong(((String) item).trim()));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed ids from the browser.
                }
            }
        }
        return ids;
    }
}
