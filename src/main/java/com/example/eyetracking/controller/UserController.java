package com.example.eyetracking.controller;

import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/user")
public class UserController {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new User());
        return "user/register";
    }

    @PostMapping("/register")
    public String registerSubmit(@ModelAttribute User user, Model model, RedirectAttributes redirectAttributes) {
        user.setUsername(clean(user.getUsername()));
        user.setEmail(clean(user.getEmail()));
        user.setName(clean(user.getName()));

        String validationError = validateRegistration(user);
        if (validationError != null) {
            model.addAttribute("error", validationError);
            model.addAttribute("user", user);
            return "user/register";
        }

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            model.addAttribute("error", "用户名已存在");
            model.addAttribute("user", user);
            return "user/register";
        }

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            model.addAttribute("error", "邮箱已被使用");
            model.addAttribute("user", user);
            return "user/register";
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("USER");
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("message", "注册成功，请登录");
        return "redirect:/user/login";
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model) {
        if (error != null) {
            model.addAttribute("error", "用户名或密码错误");
        }
        if (logout != null) {
            model.addAttribute("message", "已安全退出");
        }
        return "user/login";
    }

    @GetMapping("/profile")
    public String profile(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/user/login";
        }

        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) {
            return "redirect:/user/login";
        }
        model.addAttribute("user", user);
        return "user/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@ModelAttribute User user, Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/user/login";
        }

        User existingUser = userRepository.findByUsername(principal.getName()).orElse(null);
        if (existingUser == null) {
            return "redirect:/user/login";
        }

        String newName = clean(user.getName());
        String newEmail = clean(user.getEmail());

        if (isBlank(newName) || isBlank(newEmail)) {
            model.addAttribute("error", "姓名和邮箱不能为空");
            model.addAttribute("user", existingUser);
            return "user/profile";
        }
        if (!EMAIL_PATTERN.matcher(newEmail).matches()) {
            model.addAttribute("error", "邮箱格式不正确");
            model.addAttribute("user", existingUser);
            return "user/profile";
        }

        if (!newEmail.equalsIgnoreCase(existingUser.getEmail())
                && userRepository.findByEmail(newEmail).isPresent()) {
            model.addAttribute("error", "邮箱已被使用");
            model.addAttribute("user", existingUser);
            return "user/profile";
        }

        existingUser.setName(newName);
        existingUser.setEmail(newEmail);
        userRepository.save(existingUser);

        model.addAttribute("message", "个人资料已更新");
        model.addAttribute("user", existingUser);
        return "user/profile";
    }

    private String validateRegistration(User user) {
        if (isBlank(user.getUsername()) || isBlank(user.getPassword())
                || isBlank(user.getName()) || isBlank(user.getEmail())) {
            return "请完整填写注册信息";
        }
        if (user.getUsername().length() < 3 || user.getUsername().length() > 30) {
            return "用户名长度需要在 3 到 30 个字符之间";
        }
        if (user.getPassword().length() < 6) {
            return "密码至少需要 6 位";
        }
        if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            return "邮箱格式不正确";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    @GetMapping("/logout")
    public String logout() {
        return "redirect:/";
    }
}
