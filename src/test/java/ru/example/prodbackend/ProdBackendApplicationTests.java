package ru.example.prodbackend;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import ru.example.prodbackend.auth.entity.RefreshTokenEntity;
import ru.example.prodbackend.integration.BaseIntegrationTest;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProdBackendApplicationTests extends BaseIntegrationTest {

    @Test
    void contextLoads() throws Exception {
        mockMvc.perform(get("/api/demo/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @RepeatedTest(2)
    void databaseIsCleanBeforeEachTest() {
        assertEquals(0L, userRepository.count());
        assertEquals(0L, refreshTokenRepository.count());

        UserEntity user = userRepository.saveAndFlush(UserEntity.builder()
                .email("test-fixture@example.com")
                .password("test-fixture-password")
                .role(UserRole.USER)
                .build());

        Instant now = Instant.now();
        refreshTokenRepository.saveAndFlush(RefreshTokenEntity.builder()
                .tokenHash("a".repeat(64))
                .user(user)
                .createdAt(now)
                .expiryAt(now.plusSeconds(60))
                .build());

        // Записи остаются после теста: следующая итерация проверит их очистку.
        assertEquals(1L, userRepository.count());
        assertEquals(1L, refreshTokenRepository.count());
    }
}
