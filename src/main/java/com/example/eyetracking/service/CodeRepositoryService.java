package com.example.eyetracking.service;

import com.example.eyetracking.model.TrainingLevel;
import com.example.eyetracking.repository.TrainingLevelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CodeRepositoryService {
    @Autowired
    private TrainingLevelRepository trainingLevelRepository;

    public static class CodeExample {
        private Long id;
        private Integer levelNumber;
        private String title;
        private String description;
        private String code;
        private String language;
        private String difficulty;
        private String taskType;
        private String targetLines;
        private String guidance;

        public CodeExample(Long id, String title, String description, String code, String language, String difficulty) {
            this(id, null, title, description, code, language, difficulty, "highlight_follow", "", "");
        }

        public CodeExample(Long id, Integer levelNumber, String title, String description, String code,
                           String language, String difficulty, String taskType, String targetLines, String guidance) {
            this.id = id;
            this.levelNumber = levelNumber;
            this.title = title;
            this.description = description;
            this.code = code;
            this.language = language;
            this.difficulty = difficulty;
            this.taskType = taskType;
            this.targetLines = targetLines;
            this.guidance = guidance;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Integer getLevelNumber() { return levelNumber; }
        public void setLevelNumber(Integer levelNumber) { this.levelNumber = levelNumber; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public String getTargetLines() { return targetLines; }
        public void setTargetLines(String targetLines) { this.targetLines = targetLines; }
        public String getGuidance() { return guidance; }
        public void setGuidance(String guidance) { this.guidance = guidance; }
    }

    public List<CodeExample> getAllCodeExamples() {
        return trainingLevelRepository.findByActiveTrueOrderByLevelNumberAsc().stream()
                .map(this::toCodeExample)
                .collect(Collectors.toList());
    }

    public CodeExample getCodeExampleById(Long id) {
        return trainingLevelRepository.findById(id)
                .filter(TrainingLevel::isActive)
                .map(this::toCodeExample)
                .orElse(null);
    }

    public List<CodeExample> getCodeExamplesByLanguage(String language) {
        String normalizedLanguage = normalize(language);
        return getAllCodeExamples().stream()
                .filter(example -> normalizedLanguage.equals(example.getLanguage()))
                .collect(Collectors.toList());
    }

    public List<CodeExample> getCodeExamplesByDifficulty(String difficulty) {
        String normalizedDifficulty = normalize(difficulty);
        return getAllCodeExamples().stream()
                .filter(example -> normalizedDifficulty.equals(example.getDifficulty()))
                .collect(Collectors.toList());
    }

    private CodeExample toCodeExample(TrainingLevel level) {
        return new CodeExample(
                level.getId(),
                level.getLevelNumber(),
                level.getTitle(),
                level.getDescription(),
                level.getCodeContent(),
                level.getLanguage(),
                level.getDifficulty(),
                level.getTaskType(),
                level.getTargetLines(),
                level.getGuidance()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
