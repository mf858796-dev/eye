package com.example.eyetracking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(com.example.eyetracking.controller.TrainingController.class)
public class TrainingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testCodeExamples() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/training/code-examples"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.view().name("training/code-examples"));
    }

    @Test
    public void testStartTraining() throws Exception {
        // 这里需要模拟登录用户
        // 暂时测试未登录的情况
        mockMvc.perform(MockMvcRequestBuilders.get("/training/start/code/1"))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/user/login"));
    }
}
