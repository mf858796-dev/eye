package com.example.eyetracking.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CodeRepositoryService {
    // 代码示例类
    public static class CodeExample {
        private Long id;
        private String title;
        private String description;
        private String code;
        private String language;
        private String difficulty;

        public CodeExample(Long id, String title, String description, String code, String language, String difficulty) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.code = code;
            this.language = language;
            this.difficulty = difficulty;
        }

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
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
    }

    // 模拟代码库数据
    private List<CodeExample> codeExamples;

    public CodeRepositoryService() {
        codeExamples = new ArrayList<>();
        initializeCodeExamples();
    }

    // 初始化代码示例
    private void initializeCodeExamples() {
        // Java 代码示例
        codeExamples.add(new CodeExample(
            1L,
            "Hello World",
            "简单的Java Hello World程序",
            "public class HelloWorld {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}",
            "java",
            "easy"
        ));

        codeExamples.add(new CodeExample(
            2L,
            "计算斐波那契数列",
            "使用递归计算斐波那契数列",
            "public class Fibonacci {\n    public static int fibonacci(int n) {\n        if (n <= 1)\n            return n;\n        return fibonacci(n-1) + fibonacci(n-2);\n    }\n    public static void main(String[] args) {\n        int n = 10;\n        System.out.println(\"斐波那契数列的第\" + n + \"项是：\" + fibonacci(n));\n    }\n}",
            "java",
            "medium"
        ));

        // Python 代码示例
        codeExamples.add(new CodeExample(
            3L,
            "列表推导式",
            "使用列表推导式生成平方数列表",
            "# 生成1到10的平方数列表\nsquares = [x**2 for x in range(1, 11)]\nprint(squares)",
            "python",
            "easy"
        ));

        codeExamples.add(new CodeExample(
            4L,
            "快速排序",
            "实现快速排序算法",
            "def quick_sort(arr):\n    if len(arr) <= 1:\n        return arr\n    pivot = arr[len(arr) // 2]\n    left = [x for x in arr if x < pivot]\n    middle = [x for x in arr if x == pivot]\n    right = [x for x in arr if x > pivot]\n    return quick_sort(left) + middle + quick_sort(right)\n\nprint(quick_sort([3,6,8,10,1,2,1]))",
            "python",
            "medium"
        ));

        // JavaScript 代码示例
        codeExamples.add(new CodeExample(
            5L,
            "DOM 操作",
            "使用JavaScript操作DOM元素",
            "// 创建一个新的div元素\nconst newDiv = document.createElement('div');\n// 添加文本内容\nconst newContent = document.createTextNode('Hello, DOM!');\n// 添加文本节点到div元素\nnewDiv.appendChild(newContent);\n// 获取现有元素\nconst currentDiv = document.getElementById('div1');\n// 在现有元素前插入新元素\ndocument.body.insertBefore(newDiv, currentDiv);",
            "javascript",
            "medium"
        ));
    }

    // 获取所有代码示例
    public List<CodeExample> getAllCodeExamples() {
        return codeExamples;
    }

    // 根据ID获取代码示例
    public CodeExample getCodeExampleById(Long id) {
        return codeExamples.stream()
                .filter(example -> example.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // 根据语言获取代码示例
    public List<CodeExample> getCodeExamplesByLanguage(String language) {
        return codeExamples.stream()
                .filter(example -> example.getLanguage().equals(language))
                .collect(Collectors.toList());
    }

    // 根据难度获取代码示例
    public List<CodeExample> getCodeExamplesByDifficulty(String difficulty) {
        return codeExamples.stream()
                .filter(example -> example.getDifficulty().equals(difficulty))
                .collect(Collectors.toList());
    }
}