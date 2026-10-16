package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class BoxRepositoryIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BoxRepository boxRepository;

    private MoveProjectEntity project;
    private MoveProjectEntity otherProject;
    private RoomEntity kitchen;
    private RoomEntity bedroom;
    private RoomEntity otherRoom;
    private BoxEntity first;

    @BeforeEach
    void setUp() {
        UserEntity owner = UserEntity.builder()
                .email("box-repository-" + UUID.randomUUID())
                .password("test-password-hash").role(UserRole.USER).build();
        entityManager.persist(owner);
        project = project(owner);
        otherProject = project(owner);
        kitchen = room(project, "Кухня");
        bedroom = room(project, "Спальня");
        otherRoom = room(otherProject, "Кухня");

        box(project, kitchen, 3, BoxStatus.PACKING, false);
        first = box(project, bedroom, 1, BoxStatus.PACKED, true);
        box(project, bedroom, 2, BoxStatus.PACKING, false);
        box(otherProject, otherRoom, 1, BoxStatus.PACKED, true);
        entityManager.flush();
        entityManager.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "all", "number", "room", "status", "deferred-true", "deferred-false",
            "combined", "missing-number", "foreign-room", "conflicting-filters"
    })
    void filtersWithinProjectAndCombinesConditions(String scenario) {
        Integer number = null;
        UUID roomId = null;
        BoxStatus status = null;
        Boolean deferred = null;
        List<Integer> expected = switch (scenario) {
            case "all" -> List.of(1, 2, 3);
            case "number", "combined" -> List.of(1);
            case "room" -> List.of(3);
            case "deferred-false" -> List.of(2, 3);
            case "status", "deferred-true" -> List.of(1);
            default -> List.of();
        };

        switch (scenario) {
            case "number" -> number = 1;
            case "room" -> roomId = kitchen.getId();
            case "status" -> status = BoxStatus.PACKED;
            case "deferred-true" -> deferred = true;
            case "deferred-false" -> deferred = false;
            case "combined" -> {
                number = 1;
                roomId = bedroom.getId();
                status = BoxStatus.PACKED;
                deferred = true;
            }
            case "missing-number" -> number = 100;
            case "foreign-room" -> roomId = otherRoom.getId();
            case "conflicting-filters" -> {
                number = 1;
                status = BoxStatus.PACKING;
            }
            default -> { }
        }

        Page<BoxEntity> result = boxRepository.findAllByFilters(
                project.getId(), number, roomId, status, deferred,
                PageRequest.of(0, 20, Sort.by("number", "id"))
        );

        assertThat(result.getContent()).extracting(BoxEntity::getNumber)
                .containsExactlyElementsOf(expected);
        assertThat(result.getTotalElements()).isEqualTo(expected.size());
        assertThat(result.getContent()).allSatisfy(box -> {
            assertThat(box.getProject().getId()).isEqualTo(project.getId());
            assertThat(Persistence.getPersistenceUtil().isLoaded(box, "room")).isTrue();
        });
    }

    @Test
    void paginatesWithAccurateTotalAndStableOrder() {
        Page<BoxEntity> page = boxRepository.findAllByFilters(
                project.getId(), null, null, null, null,
                PageRequest.of(1, 1, Sort.by("number", "id"))
        );

        assertThat(page.getContent()).extracting(BoxEntity::getNumber).containsExactly(2);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(3);

        Page<BoxEntity> beyondLast = boxRepository.findAllByFilters(
                project.getId(), null, null, null, null, PageRequest.of(3, 1)
        );
        assertThat(beyondLast.getContent()).isEmpty();
        assertThat(beyondLast.getTotalElements()).isEqualTo(3);
    }

    @Test
    void scopesLookupAndNumberChecksToProjectAndExcludesCurrentBox() {
        assertThat(boxRepository.findByIdAndProject_Id(first.getId(), project.getId())).isPresent();
        assertThat(boxRepository.findByIdAndProject_Id(first.getId(), otherProject.getId())).isEmpty();
        assertThat(boxRepository.findForUpdate(first.getId(), otherProject.getId())).isEmpty();
        assertThat(boxRepository.existsByProject_IdAndNumber(project.getId(), 1)).isTrue();
        assertThat(boxRepository.existsByProject_IdAndNumber(otherProject.getId(), 3)).isFalse();
        assertThat(boxRepository.existsByProject_IdAndNumberAndIdNot(
                project.getId(), 1, first.getId())).isFalse();
        assertThat(boxRepository.existsByProject_IdAndNumberAndIdNot(
                project.getId(), 2, first.getId())).isTrue();
        assertThat(boxRepository.findAllByProject_IdOrderByNumberAscIdAsc(project.getId()))
                .extracting(BoxEntity::getNumber).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsRoomFromAnotherProjectAtDatabaseLevel() {
        BoxEntity box = boxRepository.findById(first.getId()).orElseThrow();
        box.setRoom(entityManager.getReference(RoomEntity.class, otherRoom.getId()));

        assertThatThrownBy(entityManager::flush)
                .hasStackTraceContaining("fk_boxes_room_project")
                .hasRootCauseInstanceOf(SQLException.class)
                .rootCause()
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23503");
    }

    private MoveProjectEntity project(UserEntity owner) {
        MoveProjectEntity project = MoveProjectEntity.builder().owner(owner)
                .name("Переезд").moveDate(Instant.parse("2026-10-30T00:00:00Z")).build();
        entityManager.persist(project);
        return project;
    }

    private RoomEntity room(MoveProjectEntity project, String name) {
        RoomEntity room = RoomEntity.builder().project(project).name(name).build();
        entityManager.persist(room);
        return room;
    }

    private BoxEntity box(MoveProjectEntity project, RoomEntity room, int number,
                          BoxStatus status, boolean deferred) {
        BoxEntity box = BoxEntity.builder().project(project).room(room).number(number)
                .status(status).contentsDeferred(deferred).build();
        entityManager.persist(box);
        return box;
    }
}
