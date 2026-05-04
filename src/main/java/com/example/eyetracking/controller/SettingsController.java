package com.example.eyetracking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    @GetMapping
    public String settings(HttpSession session, Model model) {
        // 获取保存的设置或使用默认值
        String glassesAddress = (String) session.getAttribute("glassesAddress");
        if (glassesAddress == null) glassesAddress = "192.168.1.10";
        
        Integer glassesPort = (Integer) session.getAttribute("glassesPort");
        if (glassesPort == null) glassesPort = 80;
        
        String dataRate = (String) session.getAttribute("dataRate");
        if (dataRate == null) dataRate = "60";
        
        Integer timeout = (Integer) session.getAttribute("timeout");
        if (timeout == null) timeout = 10;
        
        Integer defaultDuration = (Integer) session.getAttribute("defaultDuration");
        if (defaultDuration == null) defaultDuration = 30;
        
        String defaultDifficulty = (String) session.getAttribute("defaultDifficulty");
        if (defaultDifficulty == null) defaultDifficulty = "medium";
        
        Boolean autoStart = (Boolean) session.getAttribute("autoStart");
        if (autoStart == null) autoStart = true;
        
        Boolean showHeatmap = (Boolean) session.getAttribute("showHeatmap");
        if (showHeatmap == null) showHeatmap = true;
        
        Integer keepDataDays = (Integer) session.getAttribute("keepDataDays");
        if (keepDataDays == null) keepDataDays = 30;
        
        String exportFormat = (String) session.getAttribute("exportFormat");
        if (exportFormat == null) exportFormat = "json";
        
        // 模拟模式设置
        Boolean simulationMode = (Boolean) session.getAttribute("simulationMode");
        if (simulationMode == null) simulationMode = false;
        
        String simulationSpeed = (String) session.getAttribute("simulationSpeed");
        if (simulationSpeed == null) simulationSpeed = "normal";

        model.addAttribute("glassesAddress", glassesAddress);
        model.addAttribute("glassesPort", glassesPort);
        model.addAttribute("dataRate", dataRate);
        model.addAttribute("timeout", timeout);
        model.addAttribute("defaultDuration", defaultDuration);
        model.addAttribute("defaultDifficulty", defaultDifficulty);
        model.addAttribute("autoStart", autoStart);
        model.addAttribute("showHeatmap", showHeatmap);
        model.addAttribute("keepDataDays", keepDataDays);
        model.addAttribute("exportFormat", exportFormat);
        model.addAttribute("simulationMode", simulationMode);
        model.addAttribute("simulationSpeed", simulationSpeed);

        return "settings";
    }

    @PostMapping
    public String saveSettings(
            @RequestParam String glassesAddress,
            @RequestParam Integer glassesPort,
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
            Model model) {

        // 保存设置到session
        session.setAttribute("glassesAddress", glassesAddress);
        session.setAttribute("glassesPort", glassesPort);
        session.setAttribute("dataRate", dataRate);
        session.setAttribute("timeout", timeout);
        session.setAttribute("defaultDuration", defaultDuration);
        session.setAttribute("defaultDifficulty", defaultDifficulty);
        session.setAttribute("autoStart", autoStart != null && autoStart);
        session.setAttribute("showHeatmap", showHeatmap != null && showHeatmap);
        session.setAttribute("keepDataDays", keepDataDays);
        session.setAttribute("exportFormat", exportFormat);
        session.setAttribute("simulationMode", simulationMode != null && simulationMode);
        session.setAttribute("simulationSpeed", simulationSpeed);

        model.addAttribute("message", "设置保存成功");
        
        // 重新加载设置到模型
        return settings(session, model);
    }
}