package com.example.eyetracking.controller;

import com.example.eyetracking.service.AppSettingsService;
import com.example.eyetracking.service.TobiiGlassesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/settings")
public class SettingsController {
    @Autowired
    private AppSettingsService appSettingsService;
    @Autowired
    private TobiiGlassesService tobiiGlassesService;

    @GetMapping
    public String settings(HttpSession session, Model model) {
        addSettingsAttributes(model, appSettingsService.load(session));
        return "settings";
    }

    @PostMapping
    public String saveSettings(
            @RequestParam String deviceType,
            @RequestParam String glassesAddress,
            @RequestParam Integer glassesPort,
            @RequestParam Integer calibrationPointCount,
            @RequestParam String dataRate,
            @RequestParam Integer timeout,
            @RequestParam Integer defaultDuration,
            @RequestParam String defaultDifficulty,
            @RequestParam(required = false) Boolean autoStart,
            @RequestParam(required = false) Boolean showHeatmap,
            @RequestParam Integer keepDataDays,
            @RequestParam String exportFormat,
            @RequestParam(required = false) Boolean simulationMode,
            @RequestParam String simulationSpeed,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        AppSettingsService.UserSettings settings = new AppSettingsService.UserSettings();
        settings.setDeviceType(deviceType);
        settings.setGlassesAddress(glassesAddress);
        settings.setGlassesPort(glassesPort);
        settings.setCalibrationPointCount(calibrationPointCount);
        settings.setDataRate(dataRate);
        settings.setTimeout(timeout);
        settings.setDefaultDuration(defaultDuration);
        settings.setDefaultDifficulty(defaultDifficulty);
        settings.setAutoStart(autoStart != null && autoStart);
        settings.setShowHeatmap(showHeatmap != null && showHeatmap);
        settings.setKeepDataDays(keepDataDays);
        settings.setExportFormat(exportFormat);
        settings.setSimulationMode(simulationMode != null && simulationMode);
        settings.setSimulationSpeed(simulationSpeed);
        appSettingsService.save(session, settings);

        redirectAttributes.addFlashAttribute("message", "设置保存成功");
        return "redirect:/settings";
    }

    private void addSettingsAttributes(Model model, AppSettingsService.UserSettings settings) {
        model.addAttribute("deviceType", settings.getDeviceType());
        model.addAttribute("deviceLabel", settings.getDeviceLabel());
        model.addAttribute("glassesAddress", settings.getGlassesAddress());
        model.addAttribute("glassesPort", settings.getGlassesPort());
        model.addAttribute("calibrationPointCount", settings.getCalibrationPointCount());
        model.addAttribute("dataRate", settings.getDataRate());
        model.addAttribute("timeout", settings.getTimeout());
        model.addAttribute("defaultDuration", settings.getDefaultDuration());
        model.addAttribute("defaultDifficulty", settings.getDefaultDifficulty());
        model.addAttribute("autoStart", settings.isAutoStart());
        model.addAttribute("showHeatmap", settings.isShowHeatmap());
        model.addAttribute("keepDataDays", settings.getKeepDataDays());
        model.addAttribute("exportFormat", settings.getExportFormat());
        model.addAttribute("simulationMode", settings.isSimulationMode());
        model.addAttribute("simulationSpeed", settings.getSimulationSpeed());
        model.addAttribute("tobiiConnected", tobiiGlassesService.isConnected());
    }
}
