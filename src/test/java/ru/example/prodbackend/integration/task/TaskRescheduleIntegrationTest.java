package ru.example.prodbackend.integration.task;

import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.task.dto.response.MoveTaskResponse;
import ru.example.prodbackend.task.entity.TaskDueType;
import ru.example.prodbackend.task.repository.MoveTaskRepository;
import ru.example.prodbackend.task.repository.TaskTemplateRepository;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskRescheduleIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired private MoveTaskRepository tasks;
    @Autowired private TaskTemplateRepository templates;
    @Autowired private ProjectAccessService access;
    @Autowired private PlatformTransactionManager transactionManager;
    private String ownerToken;
    private String memberToken;
    private UUID ownerId;
    private UUID projectId;
    private String url;
    private static final String ORIGINAL = "2026-11-10T09:00:00Z";
    private static final String NEW_DATE = "2026-11-15T00:30:00+03:00";
    private static final LocalDate NEW_DAY_UTC = LocalDate.parse("2026-11-14");

    @BeforeEach
    void prepare() throws Exception {
        ownerToken = registerUser().getAccessToken();
        memberToken = registerUser("family@example.com").getAccessToken();
        ownerId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        projectId = UUID.fromString(json(perform(ownerToken, post("/api/move-projects"), Map.of(
                "name", "Переезд", "moveDate", ORIGINAL, "participants", List.of(Map.of("email", "family@example.com"))))
                .andExpect(status().isCreated())).get("id").asText());
        url = "/api/move-projects/" + projectId;
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, 0, 3})
    void relativeCreationAndRescheduleUseUtcForNegativeZeroAndPositiveOffset(int offset) throws Exception {
        var task = relative(offset);
        assertThat(task.getDueType()).isEqualTo(TaskDueType.RELATIVE);
        assertThat(task.getDueDate()).isEqualTo(LocalDate.parse("2026-11-10").plusDays(offset));
        reschedule(ownerToken, NEW_DATE, List.of(task.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].dueDate").value(NEW_DAY_UTC.plusDays(offset).toString()));
        read(task.getId()).andExpect(jsonPath("$.dueType").value("RELATIVE"))
                .andExpect(jsonPath("$.offsetDays").value(offset))
                .andExpect(jsonPath("$.dueDate").value(NEW_DAY_UTC.plusDays(offset).toString()));
    }

    @Test
    void onlySelectedTasksMoveAndRepeatPreservesAllTimestamps() throws Exception {
        var first = relative(-2);
        var second = relative(3);
        var omitted = relative(-7);
        var fixed = create(Map.of("title", "Фиксированная", "stage", "BEFORE_MOVE", "dueDate", "2026-12-01"));
        var completed = relative(0);
        perform(ownerToken, patch(url + "/tasks/" + completed.getId() + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
        var unchanged = List.of(omitted, fixed, completed).stream().map(t -> snapshot(t.getId())).toList();
        // Порядок ответа определяет запрос; повторные UUID не дублируют задачи.
        var ids = List.of(second.getId(), first.getId(), second.getId());
        var response = json(reschedule(memberToken, NEW_DATE, ids).andExpect(status().isOk()));
        assertThat(response.get("tasks").size()).isEqualTo(2);
        assertThat(response.get("tasks").get(0).get("id").asText()).isEqualTo(second.getId().toString());
        assertThat(response.get("tasks").get(1).get("id").asText()).isEqualTo(first.getId().toString());
        var firstAfter = snapshot(first.getId());
        var secondAfter = snapshot(second.getId());
        var projectAfter = projectSnapshot();
        reschedule(ownerToken, NEW_DATE, ids).andExpect(status().isOk());
        assertThat(snapshot(first.getId())).isEqualTo(firstAfter);
        assertThat(snapshot(second.getId())).isEqualTo(secondAfter);
        assertThat(projectSnapshot()).isEqualTo(projectAfter);
        assertThat(List.of(omitted, fixed, completed).stream().map(t -> snapshot(t.getId())).toList()).isEqualTo(unchanged);
        assertThat(readProject().get("moveDate").asText()).isEqualTo(Instant.parse("2026-11-14T21:30:00Z").toString());
    }

    @Test
    void dateOnlyAndOrdinaryPatchNeverImplicitlyRecalculateTasks() throws Exception {
        var task = relative(-2);
        var before = snapshot(task.getId());
        reschedule(ownerToken, NEW_DATE, List.of()).andExpect(status().isOk()).andExpect(jsonPath("$.tasks").isEmpty());
        assertThat(snapshot(task.getId())).isEqualTo(before);
        // Совпадение даты проекта не должно пропускать выбранный пересчёт.
        reschedule(ownerToken, NEW_DATE, List.of(task.getId())).andExpect(status().isOk());
        assertThat(tasks.findById(task.getId()).orElseThrow().getDueDate()).isEqualTo(NEW_DAY_UTC.minusDays(2));
        before = snapshot(task.getId());
        perform(ownerToken, patch(url), Map.of("moveDate", "2026-12-01T09:00:00Z")).andExpect(status().isOk());
        read(task.getId()).andExpect(status().isOk());
        assertThat(snapshot(task.getId())).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fixed", "completed", "missing", "foreign"})
    void oneInvalidSelectionRejectsWholeRequestEvenWhenProjectDateAlreadyMatches(String invalid) throws Exception {
        var valid = relative(-2);
        UUID invalidId;
        int expected = 409;
        switch (invalid) {
            case "fixed" -> invalidId = create(Map.of("title", "Ручная дата", "stage", "BEFORE_MOVE")).getId();
            case "completed" -> {
                invalidId = relative(1).getId();
                perform(ownerToken, patch(url + "/tasks/" + invalidId + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
            }
            case "foreign" -> {
                UUID other = UUID.fromString(json(perform(ownerToken, post("/api/move-projects"), Map.of("name", "Другой", "moveDate", ORIGINAL))
                        .andExpect(status().isCreated())).get("id").asText());
                invalidId = task(perform(ownerToken, post("/api/move-projects/" + other + "/tasks"),
                        Map.of("title", "Чужой проект", "stage", "BEFORE_MOVE", "dueType", "RELATIVE", "offsetDays", 0))
                        .andExpect(status().isCreated())).getId();
                expected = 404;
            }
            default -> { invalidId = UUID.randomUUID(); expected = 404; }
        }
        var before = snapshot(valid.getId());
        var projectBefore = projectSnapshot();
        for (String date : List.of(NEW_DATE, ORIGINAL)) {
            assertErrorResponse(reschedule(ownerToken, date, List.of(valid.getId(), invalidId)), expected, expected == 409 ? "DATA_CONFLICT" : "TASK_NOT_FOUND");
            assertThat(snapshot(valid.getId())).isEqualTo(before);
            assertThat(projectSnapshot()).isEqualTo(projectBefore);
        }
    }

    @Test
    void patchChangesDeadlineTypeAndPreservesOmittedDeadline() throws Exception {
        var task = relative(-2);
        var deadline = snapshot(task.getId()).subList(0, 3);
        perform(ownerToken, patch(url + "/tasks/" + task.getId()), Map.of("title", "Переименована")).andExpect(status().isOk());
        assertThat(snapshot(task.getId()).subList(0, 3)).isEqualTo(deadline);
        perform(ownerToken, patch(url + "/tasks/" + task.getId()), Map.of("offsetDays", 4)).andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2026-11-14"));
        perform(ownerToken, patch(url + "/tasks/" + task.getId()), Map.of("dueDate", "2026-12-05")).andExpect(status().isOk())
                .andExpect(jsonPath("$.dueType").value("FIXED")).andExpect(jsonPath("$.offsetDays").isEmpty());
        perform(ownerToken, patch(url + "/tasks/" + task.getId()), Map.of("dueType", "RELATIVE", "offsetDays", -1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2026-11-09"));
        perform(ownerToken, patch(url + "/tasks/" + task.getId()), "{\"dueDate\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.dueType").value("FIXED")).andExpect(jsonPath("$.dueDate").isEmpty()).andExpect(jsonPath("$.offsetDays").isEmpty());
        perform(ownerToken, patch(url + "/tasks/" + task.getId()), Map.of("dueType", "RELATIVE", "offsetDays", 0)).andExpect(status().isOk());
        perform(ownerToken, patch(url + "/tasks/" + task.getId()), Map.of("dueType", "FIXED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2026-11-10")).andExpect(jsonPath("$.offsetDays").isEmpty());
    }

    @Test
    void templatesCopyOffsetsAndSupportFixedDateOverrides() throws Exception {
        UUID templateId = templates.findAll().get(0).getId();
        var template = templates.findById(templateId).orElseThrow();
        deleteDefaultTemplateTask(templateId);
        var task = task(perform(ownerToken, post(url + "/tasks/from-template"), Map.of("templateId", templateId)).andExpect(status().isCreated()));
        assertThat(task.getDueType()).isEqualTo(TaskDueType.RELATIVE);
        assertThat(task.getOffsetDays()).isEqualTo(template.getDueDateOffsetDays());
        // Последующая правка шаблона не переписывает сохранённое смещение задачи.
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            var changed = templates.findById(templateId).orElseThrow();
            changed.setDueDateOffsetDays(99);
            templates.saveAndFlush(changed);
        });
        try {
            reschedule(ownerToken, NEW_DATE, List.of(task.getId())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.tasks[0].offsetDays").value(task.getOffsetDays()));
            deleteDefaultTemplateTask(templateId);
            perform(ownerToken, post(url + "/tasks/from-template"), Map.of("templateId", templateId, "dueDate", "2026-12-01"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.dueType").value("FIXED")).andExpect(jsonPath("$.offsetDays").isEmpty());
            deleteDefaultTemplateTask(templateId);
            perform(ownerToken, post(url + "/tasks/from-template"), Map.of("templateId", templateId, "offsetDays", -3))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.offsetDays").value(-3));
            deleteDefaultTemplateTask(templateId);
            perform(ownerToken, post(url + "/tasks/from-template"), "{\"templateId\":\"" + templateId + "\",\"dueDate\":null}")
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.dueType").value("FIXED")).andExpect(jsonPath("$.dueDate").isEmpty());
        } finally {
            template.setDueDateOffsetDays(task.getOffsetDays());
            templates.saveAndFlush(template);
        }
    }

    @Test
    void invalidDeadlineCombinationsAreRejectedWithoutChanges() throws Exception {
        var task = relative(-2);
        var before = snapshot(task.getId());
        for (String fields : List.of("\"dueType\":\"RELATIVE\",\"dueDate\":\"2026-12-01\",\"offsetDays\":2",
                "\"dueType\":\"FIXED\",\"offsetDays\":1")) {
            perform(ownerToken, post(url + "/tasks"), "{\"title\":\"Ошибка\",\"stage\":\"BEFORE_MOVE\"," + fields + "}").andExpect(status().isUnprocessableEntity());
            perform(ownerToken, patch(url + "/tasks/" + task.getId()), "{" + fields + "}").andExpect(status().isUnprocessableEntity());
        }
        perform(ownerToken, post(url + "/tasks"), Map.of("title", "Ошибка", "stage", "BEFORE_MOVE", "dueType", "RELATIVE"))
                .andExpect(status().isUnprocessableEntity());
        for (String fields : List.of("\"dueType\":null", "\"offsetDays\":null", "\"dueType\":\"UNKNOWN\"")) {
            perform(ownerToken, patch(url + "/tasks/" + task.getId()), "{" + fields + "}").andExpect(status().isBadRequest());
        }
        assertThat(snapshot(task.getId())).isEqualTo(before);
    }

    @Test
    void archivedOrUnavailableProjectCannotBeRescheduled() throws Exception {
        var task = relative(-2);
        String stranger = registerUser("stranger@example.com").getAccessToken();
        assertErrorResponse(reschedule(stranger, NEW_DATE, List.of(task.getId())), 404, "MOVE_PROJECT_NOT_FOUND");
        perform(ownerToken, put(url + "/archive"), null).andExpect(status().isOk());
        var before = projectSnapshot();
        assertErrorResponse(reschedule(memberToken, NEW_DATE, List.of(task.getId())), 409, "DATA_CONFLICT");
        assertThat(projectSnapshot()).isEqualTo(before);
        perform(memberToken, delete(url + "/archive"), null).andExpect(status().isOk());
        reschedule(memberToken, NEW_DATE, List.of(task.getId())).andExpect(status().isOk());
        perform(null, post(url + "/reschedule"), Map.of("moveDate", NEW_DATE, "taskIds", List.of())).andExpect(status().isUnauthorized());
    }

    @Test
    void rescheduleRequiresExplicitValidSelectionAndDate() throws Exception {
        var before = projectSnapshot();
        for (String body : List.of("{}", "{\"moveDate\":\"" + NEW_DATE + "\"}", "{\"taskIds\":[]}",
                "{\"moveDate\":\"" + NEW_DATE + "\",\"taskIds\":null}", "{\"moveDate\":\"" + NEW_DATE + "\",\"taskIds\":[null]}")) {
            perform(ownerToken, post(url + "/reschedule"), body).andExpect(status().isUnprocessableEntity());
        }
        perform(ownerToken, post(url + "/reschedule"), "{\"moveDate\":\"invalid\",\"taskIds\":[]}").andExpect(status().isBadRequest());
        assertThat(projectSnapshot()).isEqualTo(before);
    }

    @Test
    void fractionalSecondsAreNormalizedSoRepeatDoesNotChangeProjectTime() throws Exception {
        var ids = List.of(relative(0).getId());
        var date = "2026-12-01T09:00:00.123456789Z";
        var first = json(reschedule(ownerToken, date, ids).andExpect(status().isOk()));
        var second = json(reschedule(ownerToken, date, ids).andExpect(status().isOk()));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void concurrentTaskEditAndRescheduleFollowSameProjectLockAndRejectAtomically() throws Exception {
        var task = relative(-2);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var editing = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                access.getAccessibleProjectForUpdate(projectId, ownerId);
                var current = tasks.findForUpdate(task.getId(), projectId).orElseThrow();
                current.setDueType(TaskDueType.FIXED);
                current.setOffsetDays(null);
                current.setDueDate(LocalDate.parse("2026-12-20"));
                locked.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var moving = pool.submit(() -> { started.countDown(); return reschedule(memberToken, NEW_DATE, List.of(task.getId())); });
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> moving.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            editing.get(10, TimeUnit.SECONDS);
            moving.get(10, TimeUnit.SECONDS).andExpect(status().isConflict());
            assertThat(tasks.findById(task.getId()).orElseThrow().getDueDate()).isEqualTo(LocalDate.parse("2026-12-20"));
            assertThat(moveProjectRepository.findById(projectId).orElseThrow().getMoveDate()).isEqualTo(Instant.parse(ORIGINAL));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void swaggerDescribesDeadlineAndAtomicReschedule() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/move-projects/{projectId}/reschedule'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.MoveTaskResponse.properties.dueType").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateMoveTaskRequest.properties.offsetDays").exists())
                .andExpect(jsonPath("$.components.schemas.CreateMoveTaskRequest.properties.dueDatePresent").doesNotExist());
    }

    private void deleteDefaultTemplateTask(UUID templateId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> tasks.deleteAll(tasks.findAllByFilters(projectId, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 100)).stream().filter(t -> t.getTemplate() != null && t.getTemplate().getId().equals(templateId)).toList()));
    }
    private MoveTaskResponse relative(int offset) throws Exception { return create(Map.of("title", "Относительная", "stage", "BEFORE_MOVE", "dueType", "RELATIVE", "offsetDays", offset)); }
    private MoveTaskResponse create(Object body) throws Exception { return task(perform(ownerToken, post(url + "/tasks"), body).andExpect(status().isCreated())); }
    private MoveTaskResponse task(ResultActions result) throws Exception { return jsonMapper.readValue(result.andReturn().getResponse().getContentAsByteArray(), MoveTaskResponse.class); }
    private ResultActions read(UUID id) throws Exception { return perform(ownerToken, get(url + "/tasks/" + id), null).andExpect(status().isOk()); }
    private List<Object> snapshot(UUID id) {
        var t = tasks.findById(id).orElseThrow();
        return java.util.Arrays.asList(t.getDueType(), t.getOffsetDays(), t.getDueDate(), t.getCompletedAt(), t.getCreatedAt(), t.getUpdatedAt());
    }
    private List<Object> projectSnapshot() { var p = moveProjectRepository.findById(projectId).orElseThrow(); return List.of(p.getMoveDate(), p.getUpdatedAt()); }
    private JsonNode readProject() throws Exception { return json(perform(ownerToken, get(url), null).andExpect(status().isOk())); }
    private ResultActions reschedule(String token, String date, List<UUID> ids) throws Exception { return perform(token, post(url + "/reschedule"), Map.of("moveDate", date, "taskIds", ids)); }
    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsByteArray()); }
    private ResultActions perform(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String text ? text : jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }
}
