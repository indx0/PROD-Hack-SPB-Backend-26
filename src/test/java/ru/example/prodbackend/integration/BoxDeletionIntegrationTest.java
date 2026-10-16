package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.exception.BoxNotFoundException;
import ru.example.prodbackend.exception.MoveProjectNotFoundException;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, BoxService.class, ProjectAccessService.class})
class BoxDeletionIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BoxService boxService;

    private UserEntity owner;
    private MoveProjectEntity project;
    private RoomEntity room;

    @BeforeEach
    void setUp() {
        owner = UserEntity.builder()
                .email("box-deletion-" + UUID.randomUUID())
                .password("test-password-hash")
                .role(UserRole.USER)
                .build();
        entityManager.persist(owner);

        project = MoveProjectEntity.builder()
                .owner(owner)
                .name("Тестовый переезд")
                .moveDate(Instant.parse("2026-10-30T00:00:00Z"))
                .build();
        entityManager.persist(project);

        room = RoomEntity.builder().project(project).name("Кухня").build();
        entityManager.persist(room);
    }

    @Test
    void deletesBoxAndItsItemsButPreservesOtherBoxAndItems() {
        BoxEntity box = box(1);
        ItemEntity first = item(box, "Чайник");
        ItemEntity second = item(box, "Кружка");
        BoxEntity otherBox = box(2);
        ItemEntity otherItem = item(otherBox, "Тарелка");
        entityManager.flush();
        entityManager.clear();

        boxService.delete(owner.getId(), project.getId(), box.getId());
        entityManager.clear();

        assertThat(entityManager.find(BoxEntity.class, box.getId())).isNull();
        assertThat(entityManager.find(ItemEntity.class, first.getId())).isNull();
        assertThat(entityManager.find(ItemEntity.class, second.getId())).isNull();
        assertThat(entityManager.find(BoxEntity.class, otherBox.getId())).isNotNull();
        assertThat(entityManager.find(ItemEntity.class, otherItem.getId())).isNotNull();
    }

    @Test
    void deletesEmptyBox() {
        BoxEntity box = box(1);
        entityManager.flush();
        entityManager.clear();

        boxService.delete(owner.getId(), project.getId(), box.getId());
        entityManager.clear();

        assertThat(entityManager.find(BoxEntity.class, box.getId())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "project", "box"})
    void rejectsInvalidAccessWithoutDeletingData(String invalidField) {
        BoxEntity box = box(1);
        ItemEntity item = item(box, "Чайник");
        entityManager.flush();
        entityManager.clear();

        UUID userId = invalidField.equals("owner") ? UUID.randomUUID() : owner.getId();
        UUID projectId = invalidField.equals("project") ? UUID.randomUUID() : project.getId();
        UUID boxId = invalidField.equals("box") ? UUID.randomUUID() : box.getId();

        assertThatThrownBy(() -> boxService.delete(userId, projectId, boxId))
                .isInstanceOf(invalidField.equals("box") ? BoxNotFoundException.class : MoveProjectNotFoundException.class);
        entityManager.clear();

        assertThat(entityManager.find(BoxEntity.class, box.getId())).isNotNull();
        assertThat(entityManager.find(ItemEntity.class, item.getId())).isNotNull();
    }

    private BoxEntity box(int number) {
        BoxEntity box = BoxEntity.builder().project(project).room(room).number(number).build();
        entityManager.persist(box);
        return box;
    }

    private ItemEntity item(BoxEntity box, String name) {
        ItemEntity item = ItemEntity.builder().box(box).name(name).build();
        entityManager.persist(item);
        return item;
    }
}
