package ru.example.prodbackend.integration.task;

import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.event.repository.ProjectEventRepository;
import ru.example.prodbackend.move.dto.response.MoveProjectResponse;
import ru.example.prodbackend.task.config.TaskTemplateInitializer;
import ru.example.prodbackend.task.dto.request.CreateMoveTaskRequest;
import ru.example.prodbackend.task.dto.response.MoveTaskResponse;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.repository.MoveTaskRepository;
import ru.example.prodbackend.task.repository.TaskTemplateRepository;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Базовые сценарии задач через настоящий API, Security, бизнес-транзакции и PostgreSQL. */
class MoveTaskIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private MoveTaskRepository taskRepository;
    @Autowired
    private TaskTemplateRepository templateRepository;
    @Autowired
    private TaskTemplateInitializer templateInitializer;
    @Autowired
    private ProjectEventRepository eventRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private String token;
    private UUID projectId;

    @BeforeEach
    void setUpProject() throws Exception {
        // JPA slice-тесты могут пересоздать схему общей тестовой базы: восстанавливаем справочник явно.
        templateInitializer.run(new DefaultApplicationArguments(new String[0]));
        token = registerUser().getAccessToken();
        projectId = createProject();
    }

    @Test
    void createPersistsNormalizedTaskAndGetReturnsScalarResponse() throws Exception {
        MoveTaskResponse task = createTask(projectId, "  Заказать машину  ", TaskStage.BEFORE_MOVE);

        assertEquals(projectId, task.getProjectId());
        assertEquals("Заказать машину", task.getTitle());
        assertEquals("Описание", task.getDescription());
        assertEquals("Анна", task.getAssigneeName());
        assertEquals(LocalDate.parse("2026-11-01"), task.getDueDate());
        assertFalse(task.isCompleted());
        assertNull(task.getCompletedAt());
        assertNull(task.getTemplateId());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
        assertEquals(task.getTitle(), taskRepository.findById(task.getId()).orElseThrow().getTitle());

        call("GET", projectId, "/" + task.getId(), "", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(task.getId().toString()))
                .andExpect(jsonPath("$.project").doesNotExist()).andExpect(jsonPath("$.template").doesNotExist());
        call("POST", projectId, "", "{\"title\":\"Минимальная задача\",\"stage\":\"MOVING_DAY\"}", token)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.dueDate").isEmpty()).andExpect(jsonPath("$.assigneeName").isEmpty());
    }

    @Test
    void patchPreservesOmittedFieldsAndExplicitNullClearsOptionalFields() throws Exception {
        MoveTaskResponse task = createTask(projectId, "Задача", TaskStage.BEFORE_MOVE);
        var createdAt = taskRepository.findById(task.getId()).orElseThrow().getCreatedAt();

        call("PATCH", projectId, "/" + task.getId(), "{\"title\":\"  Новое название  \",\"stage\":\"AFTER_MOVE\"}", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Новое название"))
                .andExpect(jsonPath("$.stage").value("AFTER_MOVE")).andExpect(jsonPath("$.description").value("Описание"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-01")).andExpect(jsonPath("$.assigneeName").value("Анна"));
        call("PATCH", projectId, "/" + task.getId(), "{\"description\":null,\"dueDate\":null,\"assigneeName\":null}", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.dueDate").isEmpty()).andExpect(jsonPath("$.assigneeName").isEmpty());

        var saved = taskRepository.findById(task.getId()).orElseThrow();
        assertNull(saved.getDescription());
        assertNull(saved.getDueDate());
        assertNull(saved.getAssigneeName());
        assertNull(saved.getCompletedAt());
        assertEquals(createdAt, saved.getCreatedAt());
        assertEquals("Новое название", saved.getTitle());
    }

    @Test
    void listCombinesFiltersUsesSharedPaginationAndDoesNotMixProjects() throws Exception {
        MoveTaskResponse first = createTask(projectId, "Первая", TaskStage.BEFORE_MOVE);
        MoveTaskResponse second = createTask(projectId, "Вторая", TaskStage.BEFORE_MOVE);
        MoveTaskResponse movingDay = createTask(projectId, "Третья", TaskStage.MOVING_DAY);
        complete(first.getId(), true).andExpect(status().isOk());
        complete(movingDay.getId(), true).andExpect(status().isOk());
        createTask(createProject(), "Другой проект", TaskStage.BEFORE_MOVE);

        call("GET", projectId, "", "", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(1));
        for (int repeat = 0; repeat < 2; repeat++) {
            call("GET", projectId, "?stage=BEFORE_MOVE&completed=true&size=1", "", token)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(first.getId().toString()))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
        call("GET", projectId, "?stage=BEFORE_MOVE&completed=false", "", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second.getId().toString()));
        call("GET", projectId, "?page=1&size=1", "", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(second.getId().toString()))
                .andExpect(jsonPath("$.totalPages").value(3));
        call("GET", projectId, "?page=100&size=1", "", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void completionIsIdempotentAndRecompletionCreatesOnlyOneNewEvent() throws Exception {
        MoveTaskResponse task = createTask(projectId, "Выполнить", TaskStage.BEFORE_MOVE);
        complete(task.getId(), true).andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
        var saved = taskRepository.findById(task.getId()).orElseThrow();
        var completedAt = saved.getCompletedAt();
        var updatedAt = saved.getUpdatedAt();
        assertNotNull(completedAt);

        complete(task.getId(), true).andExpect(status().isOk());
        saved = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(completedAt, saved.getCompletedAt());
        assertEquals(updatedAt, saved.getUpdatedAt());
        assertEquals(1L, completedEvents());
        var event = eventRepository.findAll().stream().filter(e -> e.getEventType() == ProjectEventType.TASK_COMPLETED).findFirst().orElseThrow();
        assertEquals(task.getId(), event.getObjectId());
        assertEquals(EventObjectType.TASK, event.getObjectType());
        assertEquals(projectId, event.getProject().getId());
        assertEquals(userRepository.findByEmail(EMAIL).orElseThrow().getId(), event.getActor().getId());

        complete(task.getId(), false).andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false));
        assertNull(taskRepository.findById(task.getId()).orElseThrow().getCompletedAt());
        updatedAt = taskRepository.findById(task.getId()).orElseThrow().getUpdatedAt();
        complete(task.getId(), false).andExpect(status().isOk());
        assertEquals(updatedAt, taskRepository.findById(task.getId()).orElseThrow().getUpdatedAt());
        assertEquals(1L, completedEvents());
        complete(task.getId(), true).andExpect(status().isOk());
        assertEquals(2L, completedEvents());
    }

    @Test
    void templatesSupportPaginationApplicationAndDuplicateProtectionWithinProject() throws Exception {
        call("GET", projectId, "/templates?stage=BEFORE_MOVE&page=1&size=2", "", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].stage").value("BEFORE_MOVE"))
                .andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.totalPages").value(3));
        var template = templateRepository.findAll().get(0);
        String body = jsonMapper.writeValueAsString(Map.of("templateId", template.getId(), "dueDate", "2026-11-02", "assigneeName", "  Анна  "));
        var response = call("POST", projectId, "/from-template", body, token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateId").value(template.getId().toString()))
                .andExpect(jsonPath("$.title").value(template.getTitle())).andExpect(jsonPath("$.description").value(template.getDescription()))
                .andExpect(jsonPath("$.stage").value(template.getStage().name())).andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.dueDate").value("2026-11-02")).andExpect(jsonPath("$.assigneeName").value("Анна"))
                .andReturn().getResponse();
        var task = jsonMapper.readValue(response.getContentAsByteArray(), MoveTaskResponse.class);

        assertErrorResponse(call("POST", projectId, "/from-template", body, token), 409, "DATA_CONFLICT");
        assertEquals(1L, taskRepository.count());
        call("POST", createProject(), "/from-template", body, token).andExpect(status().isCreated());
        call("DELETE", projectId, "/" + task.getId(), "", token).andExpect(status().isNoContent());
        call("POST", projectId, "/from-template", body, token).andExpect(status().isCreated());
        assertEquals(2L, taskRepository.count());
        assertErrorResponse(call("POST", projectId, "/from-template", "{\"templateId\":\"" + UUID.randomUUID() + "\"}", token), 404, "TASK_TEMPLATE_NOT_FOUND");
    }

    @Test
    void validationAndMalformedRequestsReturnUnifiedErrorsWithoutChangingTask() throws Exception {
        MoveTaskResponse task = createTask(projectId, "Сохранить", TaskStage.BEFORE_MOVE);
        for (String body : new String[]{"{}", "{\"title\":\"   \",\"stage\":\"BEFORE_MOVE\"}", "{\"title\":\"Нет этапа\"}",
                jsonMapper.writeValueAsString(Map.of("title", "x".repeat(256), "stage", "BEFORE_MOVE"))}) {
            assertErrorResponse(call("POST", projectId, "", body, token), 422, "VALIDATION_ERROR");
        }
        for (String body : new String[]{"{}", "{\"title\":\"   \"}"}) {
            assertErrorResponse(call("PATCH", projectId, "/" + task.getId(), body, token), 422, "VALIDATION_ERROR");
        }
        for (String body : new String[]{"{\"title\":null}", "{\"stage\":null}", "{\"completed\":true}", "{\"dueDate\":\"not-a-date\"}"}) {
            assertErrorResponse(call("PATCH", projectId, "/" + task.getId(), body, token), 400, "INVALID_REQUEST_BODY");
        }
        assertErrorResponse(call("PATCH", projectId, "/" + task.getId() + "/completion", "{}", token), 422, "VALIDATION_ERROR");
        assertErrorResponse(call("POST", projectId, "/from-template", "{}", token), 422, "VALIDATION_ERROR");
        for (String query : new String[]{"page=-1", "size=0", "size=101", "stage=UNKNOWN", "completed=invalid"}) {
            assertErrorResponse(call("GET", projectId, "?" + query, "", token), 422, "VALIDATION_ERROR");
        }
        assertErrorResponse(call("GET", projectId, "/templates?stage=UNKNOWN", "", token), 400, "INVALID_REQUEST_PARAMETER");
        assertErrorResponse(call("GET", projectId, "/not-a-uuid", "", token), 400, "INVALID_REQUEST_PARAMETER");
        assertEquals(1L, taskRepository.count());
        assertEquals("Сохранить", taskRepository.findById(task.getId()).orElseThrow().getTitle());
        assertEquals(0L, completedEvents());
    }

    @Test
    void foreignUserAndWrongProjectCannotReadOrModifyTasks() throws Exception {
        MoveTaskResponse task = createTask(projectId, "Личная задача", TaskStage.BEFORE_MOVE);
        String otherToken = registerUser("other@example.com").getAccessToken();
        for (String[] operation : operations(task.getId())) {
            assertErrorResponse(call(operation[0], projectId, operation[1], operation[2], otherToken), 404, "MOVE_PROJECT_NOT_FOUND");
        }
        UUID anotherProject = createProject();
        for (String[] operation : operations(task.getId())) {
            if (operation[1].startsWith("/" + task.getId())) {
                assertErrorResponse(call(operation[0], anotherProject, operation[1], operation[2], token), 404, "TASK_NOT_FOUND");
            }
        }
        assertErrorResponse(call("GET", projectId, "/" + UUID.randomUUID(), "", token), 404, "TASK_NOT_FOUND");
        assertErrorResponse(call("GET", UUID.randomUUID(), "", "", token), 404, "MOVE_PROJECT_NOT_FOUND");
        assertEquals(1L, taskRepository.count());
        assertEquals("Личная задача", taskRepository.findById(task.getId()).orElseThrow().getTitle());
        assertEquals(0L, completedEvents());
    }

    @Test
    void deleteRemovesOnlySelectedTaskAndPreservesHistory() throws Exception {
        MoveTaskResponse removed = createTask(projectId, "Удалить", TaskStage.BEFORE_MOVE);
        MoveTaskResponse retained = createTask(projectId, "Оставить", TaskStage.MOVING_DAY);
        complete(removed.getId(), true).andExpect(status().isOk());

        call("DELETE", projectId, "/" + removed.getId(), "", token).andExpect(status().isNoContent()).andExpect(content().string(""));

        assertFalse(taskRepository.existsById(removed.getId()));
        assertTrue(taskRepository.existsById(retained.getId()));
        assertEquals(1L, completedEvents());
        assertErrorResponse(call("DELETE", projectId, "/" + removed.getId(), "", token), 404, "TASK_NOT_FOUND");
    }

    @Test
    void allEightEndpointsRequireAccessToken() throws Exception {
        for (String[] operation : operations(UUID.randomUUID())) {
            assertErrorResponse(call(operation[0], projectId, operation[1], operation[2], null), 401, "UNAUTHORIZED");
        }
        assertEquals(0L, taskRepository.count());
    }

    @Test
    void initializerCreatesElevenTemplatesWithoutDuplicatesOnRepeatedRun() {
        var ids = templateRepository.findAll().stream().map(template -> template.getId()).sorted().toList();
        templateInitializer.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(11, ids.size());
        assertEquals(ids, templateRepository.findAll().stream().map(template -> template.getId()).sorted().toList());
        for (TaskStage stage : TaskStage.values()) {
            assertEquals(stage == TaskStage.BEFORE_MOVE ? 5L : 3L, templateRepository.findAll().stream().filter(template -> template.getStage() == stage).count());
        }
    }

    private UUID createProject() throws Exception {
        byte[] body = mockMvc.perform(post("/api/move-projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Тест задач\",\"moveDate\":\"2026-11-01T09:00:00Z\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        UUID id = jsonMapper.readValue(body, MoveProjectResponse.class).getId();
        // Эти тесты проверяют собственные задачи и ручное применение шаблонов на пустом плане.
        // Добровольный выбор при создании отдельно проверяют MoveProjectBootstrapIntegrationTest и TaskSetIntegrationTest.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> moveProjectRepository.deleteProjectTasks(id));
        return id;
    }

    private MoveTaskResponse createTask(UUID project, String title, TaskStage stage) throws Exception {
        var request = CreateMoveTaskRequest.builder().title(title).stage(stage).description("  Описание  ")
                .dueDate(LocalDate.parse("2026-11-01")).assigneeName("  Анна  ").build();
        byte[] body = call("POST", project, "", jsonMapper.writeValueAsString(request), token)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        return jsonMapper.readValue(body, MoveTaskResponse.class);
    }

    private ResultActions complete(UUID taskId, boolean completed) throws Exception {
        return call("PATCH", projectId, "/" + taskId + "/completion", "{\"completed\":" + completed + "}", token);
    }

    private ResultActions call(String method, UUID project, String suffix, String body, String accessToken) throws Exception {
        var request = request(HttpMethod.valueOf(method), "/api/move-projects/" + project + "/tasks" + suffix)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (accessToken != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        return mockMvc.perform(request);
    }

    private long completedEvents() {
        return eventRepository.findAll().stream().filter(event -> event.getEventType() == ProjectEventType.TASK_COMPLETED).count();
    }

    private String[][] operations(UUID taskId) {
        UUID templateId = templateRepository.findAll().get(0).getId();
        return new String[][]{
                {"GET", "", ""},
                {"POST", "", "{\"title\":\"Задача\",\"stage\":\"BEFORE_MOVE\"}"},
                {"GET", "/" + taskId, ""},
                {"PATCH", "/" + taskId, "{\"title\":\"Изменить\"}"},
                {"PATCH", "/" + taskId + "/completion", "{\"completed\":true}"},
                {"DELETE", "/" + taskId, ""},
                {"GET", "/templates", ""},
                {"POST", "/from-template", "{\"templateId\":\"" + templateId + "\"}"}
        };
    }
}
