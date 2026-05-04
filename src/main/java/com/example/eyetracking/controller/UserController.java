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

import java.security.Principal;

@Controller
@RequestMapping("/user")
public class UserController {
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
    public String registerSubmit(@ModelAttribute User user, Model model) {
        // 检查用户名是否已存在
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            model.addAttribute("error", "用户名已存在");
            return "user/register";
        }

        // 检查邮箱是否已存在
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            model.addAttribute("error", "邮箱已存在");
            return "user/register";
        }

        // 加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 设置默认角色
        user.setRole("USER");

        // 保存用户
        userRepository.save(user);

        model.addAttribute("message", "注册成功");
        return "user/login";
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", "用户名或密码错误");
        }
        return "user/login";
    }

    @GetMapping("/profile")
    public String profile(Model model, Principal principal) {
        // 从会话中获取当前用户
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElse(new User());
        model.addAttribute("user", user);
        return "user/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@ModelAttribute User user, Model model, Principal principal) {
        // 从会话中获取当前用户的用户名
        String username = principal.getName();
        User existingUser = userRepository.findByUsername(username).orElse(null);
        if (existingUser != null) {
            // 更新用户信息
            // 检查用户名是否已存在（只有当用户尝试修改用户名时才检查）
            String newUsername = user.getUsername();
            if (newUsername != null && !newUsername.isEmpty() && !existingUser.getUsername().equals(newUsername)) {
                if (userRepository.findByUsername(newUsername).isPresent()) {
                    model.addAttribute("error", "用户名已存在");
                    model.addAttribute("user", existingUser);
                    return "user/profile";
                }
                existingUser.setUsername(newUsername);
            }

            // 检查邮箱是否已存在
            if (user.getEmail() != null && !existingUser.getEmail().equals(user.getEmail())) {
                if (userRepository.findByEmail(user.getEmail()).isPresent()) {
                    model.addAttribute("error", "邮箱已存在");
                    model.addAttribute("user", existingUser);
                    return "user/profile";
                }
                existingUser.setEmail(user.getEmail());
            }
            
            // 更新姓名
            if (user.getName() != null && !user.getName().isEmpty()) {
                existingUser.setName(user.getName());
            }

            userRepository.save(existingUser);
            model.addAttribute("message", "个人资料更新成功");
        }
        model.addAttribute("user", existingUser);
        return "user/profile";
    }

    @GetMapping("/logout")
    public String logout() {
        return "redirect:/";
    }
}