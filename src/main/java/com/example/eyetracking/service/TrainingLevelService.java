package com.example.eyetracking.service;

import com.example.eyetracking.model.TrainingLevel;
import com.example.eyetracking.repository.TrainingLevelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class TrainingLevelService {
    @Autowired
    private TrainingLevelRepository trainingLevelRepository;

    public void seedDefaultLevels() {
        if (trainingLevelRepository.count() > 0) {
            return;
        }

        trainingLevelRepository.saveAll(Arrays.asList(
                level(1, "基础：变量识别", "跟随高亮区域，训练对变量声明和输出语句的快速定位。",
                        "public class Variables {\n" +
                                "    public static void main(String[] args) {\n" +
                                "        int count = 5;\n" +
                                "        double price = 19.9;\n" +
                                "        double total = count * price;\n" +
                                "        System.out.println(total);\n" +
                                "    }\n" +
                                "}",
                        "java", "easy", "highlight_follow", "3-6",
                        "按顺序关注变量声明、计算语句和输出语句，尽量减少无关行停留。"),
                level(2, "进阶：条件判断", "训练分支结构阅读，观察条件、分支结果和最终输出的逻辑关系。",
                        "public class ScoreLevel {\n" +
                                "    public static void main(String[] args) {\n" +
                                "        int score = 86;\n" +
                                "        if (score >= 90) {\n" +
                                "            System.out.println(\"A\");\n" +
                                "        } else if (score >= 80) {\n" +
                                "            System.out.println(\"B\");\n" +
                                "        } else {\n" +
                                "            System.out.println(\"C\");\n" +
                                "        }\n" +
                                "    }\n" +
                                "}",
                        "java", "medium", "logic_trace", "3-9",
                        "先看条件变量，再沿着条件分支判断实际会执行的输出语句。"),
                level(3, "进阶：找 Bug 视线追踪", "阅读循环代码并定位隐藏错误，训练关键条件与边界值关注。",
                        "public class SumNumbers {\n" +
                                "    public static void main(String[] args) {\n" +
                                "        int sum = 0;\n" +
                                "        for (int i = 1; i < 10; i++) {\n" +
                                "            sum += i;\n" +
                                "        }\n" +
                                "        System.out.println(sum);\n" +
                                "    }\n" +
                                "}",
                        "java", "medium", "bug_hunt", "3-5",
                        "重点检查循环边界和累加语句：如果目标是 1 到 10，总和是否包含 10？"),
                level(4, "高级：数据处理", "训练函数调用、列表过滤与结果汇总的非线性阅读能力。",
                        "def average_even(numbers):\n" +
                                "    evens = [n for n in numbers if n % 2 == 0]\n" +
                                "    if not evens:\n" +
                                "        return 0\n" +
                                "    return sum(evens) / len(evens)\n\n" +
                                "values = [3, 4, 8, 11, 14]\n" +
                                "print(average_even(values))",
                        "python", "hard", "logic_trace", "1-5,7-8",
                        "先看函数入口和过滤规则，再跳到调用数据，最后回到返回值计算。"),
                level(5, "Web：DOM 操作", "阅读 JavaScript DOM 创建、挂载与插入流程。",
                        "const panel = document.createElement('section');\n" +
                                "panel.className = 'result-panel';\n" +
                                "const title = document.createElement('h2');\n" +
                                "title.textContent = '训练完成';\n" +
                                "panel.appendChild(title);\n" +
                                "document.body.appendChild(panel);",
                        "javascript", "medium", "highlight_follow", "1-6",
                        "跟随 DOM 节点创建、属性设置、文本赋值和挂载顺序。")
        ));
    }

    public List<TrainingLevel> getActiveLevels() {
        return trainingLevelRepository.findByActiveTrueOrderByLevelNumberAsc();
    }

    public List<TrainingLevel> getAllLevels() {
        return trainingLevelRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(valueOrMax(a.getLevelNumber()), valueOrMax(b.getLevelNumber())))
                .collect(Collectors.toList());
    }

    public TrainingLevel getLevel(Long id) {
        return trainingLevelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("训练关卡不存在"));
    }

    public TrainingLevel saveLevel(TrainingLevel level) {
        TrainingLevel sanitized = sanitize(level);
        return trainingLevelRepository.save(sanitized);
    }

    public TrainingLevel updateLevel(Long id, TrainingLevel form) {
        TrainingLevel existing = getLevel(id);
        existing.setLevelNumber(form.getLevelNumber());
        existing.setTitle(form.getTitle());
        existing.setDescription(form.getDescription());
        existing.setCodeContent(form.getCodeContent());
        existing.setLanguage(form.getLanguage());
        existing.setDifficulty(form.getDifficulty());
        existing.setTaskType(form.getTaskType());
        existing.setTargetLines(form.getTargetLines());
        existing.setGuidance(form.getGuidance());
        existing.setActive(form.getActive());
        return saveLevel(existing);
    }

    public void deactivateLevel(Long id) {
        TrainingLevel existing = getLevel(id);
        existing.setActive(false);
        trainingLevelRepository.save(existing);
    }

    public void activateLevel(Long id) {
        TrainingLevel existing = getLevel(id);
        existing.setActive(true);
        trainingLevelRepository.save(existing);
    }

    public TrainingLevel sanitize(TrainingLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("训练关卡不能为空");
        }
        level.setLevelNumber(level.getLevelNumber() == null ? nextLevelNumber() : Math.max(1, level.getLevelNumber()));
        level.setTitle(required(level.getTitle(), "关卡标题不能为空"));
        level.setCodeContent(required(level.getCodeContent(), "代码内容不能为空"));
        level.setDescription(clean(level.getDescription()));
        level.setLanguage(defaulted(level.getLanguage(), "java").toLowerCase(Locale.ROOT));
        level.setDifficulty(defaulted(level.getDifficulty(), "easy").toLowerCase(Locale.ROOT));
        level.setTaskType(defaulted(level.getTaskType(), "highlight_follow").toLowerCase(Locale.ROOT));
        level.setTargetLines(clean(level.getTargetLines()));
        level.setGuidance(clean(level.getGuidance()));
        if (level.getActive() == null) {
            level.setActive(true);
        }
        return level;
    }

    public int nextLevelNumber() {
        return getAllLevels().stream()
                .map(TrainingLevel::getLevelNumber)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
    }

    private TrainingLevel level(int levelNumber, String title, String description, String code,
                                String language, String difficulty, String taskType,
                                String targetLines, String guidance) {
        TrainingLevel level = new TrainingLevel();
        level.setLevelNumber(levelNumber);
        level.setTitle(title);
        level.setDescription(description);
        level.setCodeContent(code);
        level.setLanguage(language);
        level.setDifficulty(difficulty);
        level.setTaskType(taskType);
        level.setTargetLines(targetLines);
        level.setGuidance(guidance);
        level.setActive(true);
        return level;
    }

    private String required(String value, String message) {
        String cleaned = clean(value);
        if (cleaned == null || cleaned.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return cleaned;
    }

    private String defaulted(String value, String defaultValue) {
        String cleaned = clean(value);
        return cleaned == null || cleaned.isEmpty() ? defaultValue : cleaned;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private int valueOrMax(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }
}
