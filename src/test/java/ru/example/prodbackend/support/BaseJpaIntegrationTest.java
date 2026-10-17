package ru.example.prodbackend.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.event.service.ProjectEventService;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.support.config.TestcontainersConfig;

/** Общий JPA-контекст; DataJpaTest откатывает каждый сценарий отдельной транзакцией. */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, BoxService.class, ProjectAccessService.class, ProjectEventService.class})
public abstract class BaseJpaIntegrationTest {
}
