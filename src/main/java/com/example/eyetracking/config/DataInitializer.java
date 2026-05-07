package com.example.eyetracking.config;

import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.UserRepository;
import com.example.eyetracking.service.TrainingLevelService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner createDefaultData(
            UserRepository userRepository,
            TrainingLevelService trainingLevelService,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username:admin}") String username,
            @Value("${app.admin.password:admin}") String password,
            @Value("${app.admin.name:系统管理员}") String name,
            @Value("${app.admin.email:admin@example.com}") String email) {
        return args -> {
            trainingLevelService.seedDefaultLevels();

            if (!userRepository.findByUsername(username).isPresent()) {
                User admin = new User();
                admin.setUsername(username);
                admin.setPassword(passwordEncoder.encode(password));
                admin.setName(name);
                admin.setEmail(email);
                admin.setRole("ADMIN");
                userRepository.save(admin);
            }
        };
    }
}
