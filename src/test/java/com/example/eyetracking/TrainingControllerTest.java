package com.example.eyetracking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.example.eyetracking.repository.UserRepository;
import com.example.eyetracking.service.AchievementService;
import com.example.eyetracking.service.AppSettingsService;
import com.example.eyetracking.service.AttentionModelService;
import com.example.eyetracking.service.CodeRepositoryService;
import com.example.eyetracking.service.CoordinateMapperService;
import com.example.eyetracking.service.GazeDataService;
import com.example.eyetracking.service.TrainingSessionService;

import java.util.Collections;

import static org.mockito.Mockito.when;

@WebMvcTest(com.example.eyetracking.controller.TrainingController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TrainingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CodeRepositoryService codeRepositoryService;

    @MockBean
    private TrainingSessionService trainingSessionService;

    @MockBean
    private GazeDataService gazeDataService;

    @MockBean
    private AttentionModelService attentionModelService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private AppSettingsService appSettingsService;

    @MockBean
    private AchievementService achievementService;

    @MockBean
    private CoordinateMapperService coordinateMapperService;

    @Test
    public void testCodeExamples() throws Exception {
        when(codeRepositoryService.getAllCodeExamples()).thenReturn(Collections.singletonList(
                new CodeRepositoryService.CodeExample(1L, "Hello", "Demo", "class Demo {}", "java", "easy")
        ));

        mockMvc.perform(MockMvcRequestBuilders.get("/training/code-examples"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.view().name("training/code-examples"));
    }

    @Test
    public void testCodeExamplesWithFilters() throws Exception {
        when(codeRepositoryService.getAllCodeExamples()).thenReturn(Collections.singletonList(
                new CodeRepositoryService.CodeExample(1L, "Hello", "Demo", "class Demo {}", "java", "easy")
        ));

        mockMvc.perform(MockMvcRequestBuilders.get("/training/code-examples")
                        .param("language", "java")
                        .param("difficulty", "easy"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.model().attribute("resultCount", 1))
                .andExpect(MockMvcResultMatchers.model().attribute("selectedLanguage", "java"))
                .andExpect(MockMvcResultMatchers.model().attribute("selectedDifficulty", "easy"))
                .andExpect(MockMvcResultMatchers.view().name("training/code-examples"));
    }

    @Test
    public void testStartTraining() throws Exception {
        when(codeRepositoryService.getCodeExampleById(1L)).thenReturn(
                new CodeRepositoryService.CodeExample(1L, "Hello", "Demo", "class Demo {}", "java", "easy")
        );

        mockMvc.perform(MockMvcRequestBuilders.get("/training/start/code/1"))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/user/login"));
    }
}
