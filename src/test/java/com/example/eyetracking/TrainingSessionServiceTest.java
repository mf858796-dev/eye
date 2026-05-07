package com.example.eyetracking;

import com.example.eyetracking.model.TrainingSession;
import com.example.eyetracking.model.User;
import com.example.eyetracking.repository.UserRepository;
import com.example.eyetracking.service.TrainingSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TrainingSessionServiceTest {

    @Autowired
    private TrainingSessionService trainingSessionService;
    @Autowired
    private UserRepository userRepository;

    @Test
    public void testCreateTrainingSession() {
        // 创建测试用户
        User user = createTestUser();

        // 创建训练会话
        TrainingSession session = trainingSessionService.createTrainingSession(user, "Test Session");

        // 验证会话创建成功
        assertNotNull(session);
        assertEquals("Test Session", session.getSessionName());
        assertEquals("ACTIVE", session.getStatus());
        assertNotNull(session.getStartTime());
        assertNull(session.getEndTime());
        assertNull(session.getDuration());
    }

    @Test
    public void testEndTrainingSession() {
        // 创建测试用户
        User user = createTestUser();

        // 创建训练会话
        TrainingSession session = trainingSessionService.createTrainingSession(user, "Test Session");
        Long sessionId = session.getId();

        // 模拟等待一段时间
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 结束训练会话
        session = trainingSessionService.endTrainingSession(sessionId);

        // 验证会话结束成功
        assertNotNull(session);
        assertEquals("COMPLETED", session.getStatus());
        assertNotNull(session.getEndTime());
        assertNotNull(session.getDuration());
        assertTrue(session.getDuration() >= 0);
    }

    @Test
    public void testGetTrainingSessionById() {
        // 创建测试用户
        User user = createTestUser();

        // 创建训练会话
        TrainingSession session = trainingSessionService.createTrainingSession(user, "Test Session");
        Long sessionId = session.getId();

        // 获取训练会话
        TrainingSession retrievedSession = trainingSessionService.getTrainingSessionById(sessionId);

        // 验证会话获取成功
        assertNotNull(retrievedSession);
        assertEquals(sessionId, retrievedSession.getId());
        assertEquals("Test Session", retrievedSession.getSessionName());
    }

    private User createTestUser() {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setUsername("testuser-" + suffix);
        user.setName("Test User");
        user.setEmail("test-" + suffix + "@example.com");
        user.setPassword("password");
        user.setRole("USER");
        return userRepository.save(user);
    }
}
