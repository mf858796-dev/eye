package com.example.eyetracking.controller;

import com.example.eyetracking.model.TrainingLevel;
import com.example.eyetracking.service.TrainingLevelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/training/levels")
public class TrainingLevelController {
    @Autowired
    private TrainingLevelService trainingLevelService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("levels", trainingLevelService.getAllLevels());
        return "training/levels";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        TrainingLevel level = new TrainingLevel();
        level.setLevelNumber(trainingLevelService.nextLevelNumber());
        level.setLanguage("java");
        level.setDifficulty("easy");
        level.setTaskType("highlight_follow");
        level.setActive(true);
        model.addAttribute("level", level);
        model.addAttribute("formTitle", "新增训练关卡");
        model.addAttribute("formAction", "/training/levels/new");
        return "training/level-form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute TrainingLevel level, RedirectAttributes redirectAttributes, Model model) {
        try {
            trainingLevelService.saveLevel(level);
            redirectAttributes.addFlashAttribute("message", "训练关卡已创建");
            return "redirect:/training/levels";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("level", level);
            model.addAttribute("formTitle", "新增训练关卡");
            model.addAttribute("formAction", "/training/levels/new");
            return "training/level-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("level", trainingLevelService.getLevel(id));
        model.addAttribute("formTitle", "编辑训练关卡");
        model.addAttribute("formAction", "/training/levels/" + id + "/edit");
        return "training/level-form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id, @ModelAttribute TrainingLevel level,
                       RedirectAttributes redirectAttributes, Model model) {
        try {
            trainingLevelService.updateLevel(id, level);
            redirectAttributes.addFlashAttribute("message", "训练关卡已更新");
            return "redirect:/training/levels";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("level", level);
            model.addAttribute("formTitle", "编辑训练关卡");
            model.addAttribute("formAction", "/training/levels/" + id + "/edit");
            return "training/level-form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        trainingLevelService.deactivateLevel(id);
        redirectAttributes.addFlashAttribute("message", "训练关卡已停用");
        return "redirect:/training/levels";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        trainingLevelService.activateLevel(id);
        redirectAttributes.addFlashAttribute("message", "训练关卡已启用");
        return "redirect:/training/levels";
    }
}
