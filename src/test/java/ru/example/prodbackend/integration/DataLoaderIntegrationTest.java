package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.service.RefreshTokenService;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.config.DataLoader;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class DataLoaderIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void loadsDemoMoveForUser1() {
        // Run inside the test transaction so demo data is rolled back, including in the shared CI database.
        DataLoader loader = new DataLoader(entityManager, userRepository, passwordEncoder, refreshTokenService);
        ReflectionTestUtils.setField(loader, "adminPassword", "test-admin-password");
        ReflectionTestUtils.setField(loader, "user1Password", "test-user-password");
        LocalDate beforeLoad = LocalDate.now(ZoneOffset.UTC);
        loader.run(new DefaultApplicationArguments(new String[0]));
        LocalDate afterLoad = LocalDate.now(ZoneOffset.UTC);
        entityManager.flush();
        entityManager.clear();

        MoveProjectEntity project = entityManager.createQuery("from MoveProjectEntity", MoveProjectEntity.class)
                .getSingleResult();
        assertThat(project.getOwner().getEmail()).isEqualTo("user1");
        LocalDate moveDate = project.getMoveDate().atOffset(ZoneOffset.UTC).toLocalDate();
        assertThat(moveDate).isBetween(beforeLoad.plusDays(14), afterLoad.plusDays(14));
        assertThat(project.getMoveDate().atOffset(ZoneOffset.UTC).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(count("UserEntity")).isEqualTo(2);
        assertThat(entityManager.createQuery(
                "select count(t) from RefreshTokenEntity t group by t.user.id", Long.class).getResultList())
                .containsExactlyInAnyOrder(1L, 1L);
        assertThat(count("RoomEntity")).isEqualTo(4);
        assertThat(count("ItemEntity")).isEqualTo(12);
        assertThat(count("MoveTaskEntity")).isEqualTo(9);
        assertThat(count("FirstDayChecklistItemEntity")).isEqualTo(6);
        assertThat(count("ProjectEventEntity")).isEqualTo(18);
        assertThat(entityManager.createQuery("from BoxEntity", BoxEntity.class).getResultList())
                .hasSize(5)
                .allSatisfy(box -> {
                    assertThat(box.getProject().getId()).isEqualTo(project.getId());
                    assertThat(box.getRoom().getProject().getId()).isEqualTo(project.getId());
                })
                .extracting(BoxEntity::getStatus).containsExactlyInAnyOrder(BoxStatus.values());
        assertThat(entityManager.createQuery("from BoxEntity", BoxEntity.class).getResultList())
                .extracting(BoxEntity::getNumber).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        assertTaskDueDate("Выбрать перевозчика", moveDate.minusDays(10));
        assertTaskDueDate("Встретить грузчиков", moveDate);
        assertTaskDueDate("Подключить интернет", moveDate.plusDays(2));
    }

    private void assertTaskDueDate(String title, LocalDate expected) {
        MoveTaskEntity task = entityManager.createQuery(
                        "from MoveTaskEntity t where t.title = :title", MoveTaskEntity.class)
                .setParameter("title", title).getSingleResult();
        assertThat(task.getDueDate()).isEqualTo(expected);
    }

    private long count(String entity) {
        return entityManager.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }
}
