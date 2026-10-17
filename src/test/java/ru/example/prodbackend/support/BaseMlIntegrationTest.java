package ru.example.prodbackend.support;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.example.prodbackend.ml.client.LlmProxyClient;

/** Реальные HTTP, Security и PostgreSQL; подменяется только внешний ML-клиент. */
public abstract class BaseMlIntegrationTest extends BaseIntegrationTest {
    @MockitoBean
    protected LlmProxyClient llmClient;
}
