package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class BoxNumberIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsHumanReadableNumbersAndKeepsUuidIdentifiers() {
        MoveProjectEntity project = project();
        RoomEntity room = room(project, "Кухня");
        BoxEntity first = box(project, room, 1);
        BoxEntity second = box(project, room, 2);
        entityManager.flush();
        entityManager.clear();

        assertThat(first.getId()).isNotNull().isNotEqualTo(second.getId());
        assertThat(entityManager.find(BoxEntity.class, first.getId()).getNumber()).isEqualTo(1);
        assertThat(entityManager.find(BoxEntity.class, second.getId()).getNumber()).isEqualTo(2);
        assertThat(entityManager.find(BoxEntity.class, first.getId()).getStatus()).isEqualTo(BoxStatus.PACKING);
    }

    @Test
    void allowsSameNumberInDifferentProjects() {
        MoveProjectEntity firstProject = project();
        MoveProjectEntity secondProject = project();
        BoxEntity first = box(firstProject, room(firstProject, "Кухня"), 1);
        BoxEntity second = box(secondProject, room(secondProject, "Кухня"), 1);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(BoxEntity.class, first.getId()).getNumber()).isEqualTo(1);
        assertThat(entityManager.find(BoxEntity.class, second.getId()).getNumber()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateNumberInSameProjectEvenAcrossDifferentRooms() {
        MoveProjectEntity project = project();
        box(project, room(project, "Кухня"), 1);
        box(project, room(project, "Спальня"), 1);

        assertThatThrownBy(entityManager::flush)
                .hasStackTraceContaining("uk_boxes_project_number")
                .hasRootCauseInstanceOf(SQLException.class)
                .rootCause()
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23505");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void validatesPositiveNumber(int number) {
        MoveProjectEntity project = project();
        RoomEntity room = room(project, "Кухня");

        assertThatThrownBy(() -> {
            box(project, room, number);
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class)
                .satisfies(error -> assertThat(((ConstraintViolationException) error).getConstraintViolations())
                        .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                                .isEqualTo("number")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void databaseAlsoRejectsNonPositiveNumberWhenValidationIsBypassed(int number) {
        MoveProjectEntity project = project();
        BoxEntity box = box(project, room(project, "Кухня"), 1);
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("update boxes set number = :number where id = :id")
                .setParameter("number", number)
                .setParameter("id", box.getId())
                .executeUpdate())
                .hasStackTraceContaining("ck_boxes_positive_number")
                .hasRootCauseInstanceOf(SQLException.class)
                .rootCause()
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23514");
    }

    private MoveProjectEntity project() {
        UserEntity owner = UserEntity.builder()
                .email("box-owner-" + UUID.randomUUID())
                .password("test-password-hash")
                .role(UserRole.USER)
                .build();
        entityManager.persist(owner);
        MoveProjectEntity project = MoveProjectEntity.builder()
                .owner(owner)
                .name("Тестовый переезд")
                .moveDate(Instant.parse("2026-10-30T00:00:00Z"))
                .build();
        entityManager.persist(project);
        return project;
    }

    private RoomEntity room(MoveProjectEntity project, String name) {
        RoomEntity room = RoomEntity.builder().project(project).name(name).build();
        entityManager.persist(room);
        return room;
    }

    private BoxEntity box(MoveProjectEntity project, RoomEntity room, int number) {
        BoxEntity box = BoxEntity.builder().project(project).room(room).number(number).build();
        entityManager.persist(box);
        return box;
    }
}
