package ru.example.prodbackend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.box.dto.CreateBoxRequest;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventEntity;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.event.repository.ProjectEventRepository;
import ru.example.prodbackend.event.service.ProjectEventService;
import ru.example.prodbackend.move.dto.request.CreateMoveProjectRequest;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.service.MoveProjectService;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.room.repository.RoomRepository;
import ru.example.prodbackend.user.entity.UserEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Настоящие бизнес-транзакции и PostgreSQL; сбой записи журнала имитируется только в отдельных сценариях. */
class ProjectEventIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private ProjectEventService eventService;
    @Autowired
    private MoveProjectService moveProjectService;
    @Autowired
    private BoxService boxService;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private BoxRepository boxRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoSpyBean
    private ProjectEventRepository eventRepository;

    private UserEntity owner;

    @BeforeEach
    void setUpOwner() throws Exception {
        registerUser();
        owner = userRepository.findByEmail(EMAIL).orElseThrow();
    }

    @Test
    void eventCannotBeRecordedOutsideBusinessTransaction() {
        MoveProjectEntity project = moveProjectRepository.saveAndFlush(MoveProjectEntity.builder().owner(owner)
                .name("Переезд").moveDate(Instant.parse("2026-11-01T09:00:00Z")).build());

        assertThrows(IllegalTransactionStateException.class, () -> eventService.record(project, owner, ProjectEventType.PROJECT_CREATED,
                EventObjectType.PROJECT, project.getId(), "Создан проект"));

        assertEquals(0L, eventRepository.count());
    }

    @Test
    void projectAndCreationEventRollBackTogether() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            moveProjectService.createMoveProject(request(), owner);
            eventRepository.flush();
            assertEquals(1L, moveProjectRepository.count());
            assertEquals(1L, eventRepository.count());
            status.setRollbackOnly();
        });

        assertEquals(0L, moveProjectRepository.count());
        assertEquals(0L, eventRepository.count());
    }

    @Test
    void eventWriteFailurePreventsProjectCreation() {
        failEventWrite();

        assertThrows(DataAccessResourceFailureException.class, () -> moveProjectService.createMoveProject(request(), owner));

        assertEquals(0L, moveProjectRepository.count());
        assertEquals(0L, eventRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "status"})
    void eventWriteFailureRollsBackBoxAction(String action) {
        var project = moveProjectService.createMoveProject(request(), owner);
        var room = roomRepository.saveAndFlush(RoomEntity.builder().project(moveProjectRepository.findById(project.getId()).orElseThrow()).name("Кухня").build());
        var boxRequest = CreateBoxRequest.builder().roomId(room.getId()).number(1).build();
        var box = action.equals("status") ? boxService.create(owner.getId(), project.getId(), boxRequest) : null;
        long initialBoxes = boxRepository.count();
        long initialEvents = eventRepository.count();
        failEventWrite();

        if (box == null) {
            assertThrows(DataAccessResourceFailureException.class, () -> boxService.create(owner.getId(), project.getId(), boxRequest));
        } else {
            assertThrows(DataAccessResourceFailureException.class, () -> boxService.changeStatus(owner.getId(), project.getId(), box.getId(), BoxStatus.PACKED));
            assertEquals(BoxStatus.PACKING, boxRepository.findById(box.getId()).orElseThrow().getStatus());
        }

        assertEquals(initialBoxes, boxRepository.count());
        assertEquals(initialEvents, eventRepository.count());
    }

    private CreateMoveProjectRequest request() {
        return CreateMoveProjectRequest.builder().name("Переезд").moveDate(Instant.parse("2026-11-01T09:00:00Z")).build();
    }

    private void failEventWrite() {
        doThrow(new DataAccessResourceFailureException("Тестовый сбой записи журнала")).when(eventRepository).save(any(ProjectEventEntity.class));
    }
}
