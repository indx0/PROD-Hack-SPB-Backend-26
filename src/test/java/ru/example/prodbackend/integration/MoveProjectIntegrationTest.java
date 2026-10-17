package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.event.repository.ProjectEventRepository;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.move.dto.response.MoveProjectResponse;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.entity.TaskStage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Реальные HTTP-сценарии через MockMvc, Security, сервис и PostgreSQL, без моков. */
class MoveProjectIntegrationTest extends AuthIntegrationTestSupport {

    private static final String URL = "/api/move-projects";
    private static final Instant MOVE_DATE = Instant.parse("2026-11-01T09:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ProjectEventRepository eventRepository;

    @Test
    void createPersistsProjectForCurrentUserAndGetReturnsIt() throws Exception {
        String token = registerUser().getAccessToken();
        MoveProjectResponse project = createProject(token, "  Мой переезд  ");

        assertEquals("Мой переезд", project.getName());
        assertNotNull(project.getId());
        assertNotNull(project.getCreatedAt());
        assertNotNull(project.getUpdatedAt());
        var saved = moveProjectRepository.findById(project.getId()).orElseThrow();
        assertEquals(userRepository.findByEmail(EMAIL).orElseThrow().getId(), saved.getOwner().getId());
        assertEquals(MOVE_DATE, saved.getMoveDate());
        assertEquals("Москва", saved.getOriginAddress());
        assertEquals("Санкт-Петербург", saved.getDestinationAddress());

        mockMvc.perform(get(URL + "/" + project.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(project.getId().toString()))
                .andExpect(jsonPath("$.name").value("Мой переезд"))
                .andExpect(jsonPath("$.owner").doesNotExist());

        assertEquals(1L, eventRepository.count());
        var event = eventRepository.findAll().get(0);
        assertEquals(ProjectEventType.PROJECT_CREATED, event.getEventType());
        assertEquals(EventObjectType.PROJECT, event.getObjectType());
        assertEquals(project.getId(), event.getProject().getId());
        assertEquals(project.getId(), event.getObjectId());
        assertEquals(saved.getOwner().getId(), event.getActor().getId());
        assertNotNull(event.getOccurredAt());
        assertTrue(event.getDescription().contains("Мой переезд"));
    }

    @Test
    void listReturnsOnlyOwnProjectsWithStablePagination() throws Exception {
        String token = registerUser().getAccessToken();
        MoveProjectResponse first = createProject(token, "Первый");
        MoveProjectResponse second = createProject(token, "Второй");
        createProject(registerUser("other@example.com").getAccessToken(), "Чужой");

        for (int repeat = 0; repeat < 2; repeat++) {
            mockMvc.perform(get(URL).param("page", "0").param("size", "1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(second.getId().toString()))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }
        mockMvc.perform(get(URL).param("page", "1").param("size", "1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(first.getId().toString()));
    }

    @Test
    void patchPersistsChangesClearsAddressAndPreservesOmittedFields() throws Exception {
        String token = registerUser().getAccessToken();
        MoveProjectResponse project = createProject(token, "Переезд");
        Instant newDate = MOVE_DATE.plusSeconds(86400);

        mockMvc.perform(patch(URL + "/" + project.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(Map.of(
                                "name", "  Новое название  ", "moveDate", newDate.toString(), "originAddress", "   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Новое название"))
                .andExpect(jsonPath("$.originAddress").isEmpty())
                .andExpect(jsonPath("$.destinationAddress").value("Санкт-Петербург"));

        var saved = moveProjectRepository.findById(project.getId()).orElseThrow();
        assertEquals("Новое название", saved.getName());
        assertEquals(newDate, saved.getMoveDate());
        assertNull(saved.getOriginAddress());
        assertEquals("Санкт-Петербург", saved.getDestinationAddress());
        assertTrue(saved.getUpdatedAt().isAfter(project.getUpdatedAt()));
    }

    @Test
    void foreignUserCannotReadChangeOrDeleteProject() throws Exception {
        String ownerToken = registerUser().getAccessToken();
        MoveProjectResponse project = createProject(ownerToken, "Личный переезд");
        String otherToken = registerUser("other@example.com").getAccessToken();
        String path = URL + "/" + project.getId();

        assertErrorResponse(mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(mockMvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Чужое изменение\"}")), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(mockMvc.perform(get(URL + "/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)), 404, "MOVE_PROJECT_NOT_FOUND");

        assertEquals(1, moveProjectRepository.count());
        assertEquals("Личный переезд", moveProjectRepository.findById(project.getId()).orElseThrow().getName());
    }

    @Test
    void deleteRemovesProjectAndAllChildrenWithoutTouchingAnotherProject() throws Exception {
        String token = registerUser().getAccessToken();
        MoveProjectResponse removed = createProject(token, "Удалить");
        MoveProjectResponse retained = createProject(token, "Сохранить");
        seedProjectContents(removed.getId());
        seedProjectContents(retained.getId());

        mockMvc.perform(delete(URL + "/" + removed.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertFalse(moveProjectRepository.existsById(removed.getId()));
        assertTrue(moveProjectRepository.existsById(retained.getId()));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String entity : new String[]{"RoomEntity", "BoxEntity", "MoveTaskEntity", "FirstDayChecklistItemEntity", "ProjectEventEntity"}) {
                assertEquals(0L, entityManager.createQuery("select count(e) from " + entity + " e where e.project.id = :id", Long.class)
                        .setParameter("id", removed.getId()).getSingleResult(), entity);
                long expected = switch (entity) {
                    case "MoveTaskEntity" -> 1L;
                    case "FirstDayChecklistItemEntity" -> 5L;
                    default -> 1L;
                };
                assertEquals(expected, entityManager.createQuery("select count(e) from " + entity + " e where e.project.id = :id", Long.class)
                        .setParameter("id", retained.getId()).getSingleResult(), entity);
            }
            assertEquals(1L, entityManager.createQuery("select count(i) from ItemEntity i where i.box.project.id = :id", Long.class)
                    .setParameter("id", retained.getId()).getSingleResult());
            assertEquals(1L, entityManager.createQuery("select count(i) from ItemEntity i", Long.class).getSingleResult());
        });
        assertErrorResponse(mockMvc.perform(get(URL + "/" + removed.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)), 404, "MOVE_PROJECT_NOT_FOUND");
    }

    @Test
    void invalidCreateReturns422AndDoesNotSaveProject() throws Exception {
        String token = registerUser().getAccessToken();

        assertErrorResponse(mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}")), 422, "VALIDATION_ERROR");

        assertEquals(0, moveProjectRepository.count());
        assertEquals(0L, eventRepository.count());
    }

    private MoveProjectResponse createProject(String token, String name) throws Exception {
        byte[] body = mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(Map.of(
                                "name", name, "moveDate", MOVE_DATE.toString(),
                                "originAddress", "  Москва  ", "destinationAddress", "Санкт-Петербург"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        return jsonMapper.readValue(body, MoveProjectResponse.class);
    }

    private void seedProjectContents(UUID projectId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var project = moveProjectRepository.findById(projectId).orElseThrow();
            var room = RoomEntity.builder().project(project).name("Кухня").build();
            entityManager.persist(room);
            var box = BoxEntity.builder().project(project).room(room).number(1).build();
            entityManager.persist(box);
            entityManager.persist(ItemEntity.builder().box(box).name("Чайник").build());
            entityManager.persist(MoveTaskEntity.builder().project(project).title("Собрать вещи").stage(TaskStage.BEFORE_MOVE).build());
            entityManager.persist(FirstDayChecklistItemEntity.builder().project(project).name("Зарядка").build());
        });
    }
}
