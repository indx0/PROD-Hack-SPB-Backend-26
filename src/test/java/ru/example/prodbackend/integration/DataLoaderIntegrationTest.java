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
import ru.example.prodbackend.user.repository.UserRepository;

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
        loader.run(new DefaultApplicationArguments(new String[0]));
        entityManager.flush();
        entityManager.clear();

        MoveProjectEntity project = entityManager.createQuery("from MoveProjectEntity", MoveProjectEntity.class)
                .getSingleResult();
        assertThat(project.getOwner().getEmail()).isEqualTo("user1");
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
    }

    private long count(String entity) {
        return entityManager.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }
}
