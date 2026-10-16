package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.move.entity.MoveProjectEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"ADMIN_PASSWORD=test-admin-password", "USER1_PASSWORD=test-user-password"})
@ActiveProfiles({"dev", "test"})
@Import(TestcontainersConfig.class)
@Transactional
class DataLoaderIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void loadsDemoMoveForUser1() {
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
