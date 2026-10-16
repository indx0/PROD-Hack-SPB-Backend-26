package ru.example.prodbackend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.auth.repository.RefreshTokenRepository;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.user.repository.UserRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Общая основа интеграционных тестов с настоящими сервисами, Security и PostgreSQL.
 * Транзакциями запросов управляют сервисы, а не тестовый класс.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    protected void cleanDatabase() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            refreshTokenRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
        });
    }
}
