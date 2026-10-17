package ru.example.prodbackend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.move.service.ProjectParticipantService;
import ru.example.prodbackend.support.AuthIntegrationTestSupport;
import ru.example.prodbackend.support.config.JdbcCaptureConfig;
import ru.example.prodbackend.user.entity.UserEntity;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JdbcCaptureConfig.class)
class TaskAssignmentIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ProjectParticipantService participants;
    @Autowired private JdbcCaptureConfig.JdbcCapture capture;
    private UserEntity owner, member, other, stranger;
    private String ownerToken, memberToken, otherToken, strangerToken;
    private UUID projectId;
    private String projectUrl, tasksUrl, participantsUrl;

    @BeforeEach
    void setUp() throws Exception {
        owner = account(); member = account(); other = account(); stranger = account();
        ownerToken = authenticateUser(owner.getEmail()).getAccessToken();
        memberToken = authenticateUser(member.getEmail()).getAccessToken();
        otherToken = authenticateUser(other.getEmail()).getAccessToken();
        strangerToken = authenticateUser(stranger.getEmail()).getAccessToken();
        projectId = id(call(ownerToken, post("/api/move-projects"), Map.of("name", "Общий проект",
                "moveDate", "2026-11-01T09:00:00Z", "includeFirstDayChecklist", false,
                "participants", List.of(Map.of("userId", member.getId()), Map.of("userId", other.getId())))).andExpect(status().isCreated()));
        projectUrl = "/api/move-projects/" + projectId;
        tasksUrl = projectUrl + "/tasks";
        participantsUrl = projectUrl + "/participants";
    }

    @Test
    void sameNamesRemainDistinctAndCurrentProfileIsVisibleOnAnotherSessionAndAfterLogin() throws Exception {
        UUID first = task(member.getId()), second = task(other.getId());
        assertAssignment(call(ownerToken, get(tasksUrl + "/" + first), null), member.getId(), "Alice Smith");
        assertAssignment(call(otherToken, get(tasksUrl + "/" + second), null), other.getId(), "Alice Smith");
        call(memberToken, patch("/api/user/me"), Map.of("firstName", "Анна", "lastName", "Новая"))
                .andExpect(status().isOk());
        String fresh = authenticateUser(owner.getEmail()).getAccessToken();
        assertAssignment(call(fresh, get(tasksUrl + "/" + first), null), member.getId(), "Анна Новая");
        var page = json(call(otherToken, get(tasksUrl), null).andExpect(status().isOk()));
        assertThat(textFields(page.path("content"), "assigneeId"))
                .containsExactly(member.getId().toString(), other.getId().toString());
        // Empty profile names use the current email instead of a stale name.
        jdbc.update("UPDATE users SET first_name = NULL, last_name = '  ' WHERE id = ?", member.getId());
        assertAssignment(call(fresh, get(tasksUrl + "/" + first), null), member.getId(), member.getEmail());
    }

    @Test
    void patchPreservesOrClearsAndExplicitIdHasPriorityInAnyJsonOrder() throws Exception {
        UUID task = task(member.getId());
        assertAssignment(call(ownerToken, patch(tasksUrl + "/" + task), Map.of("title", "Новое название")), member.getId(), "Alice Smith");
        assertAssignment(call(memberToken, patch(tasksUrl + "/" + task),
                "{\"assigneeName\":\"Игнорировать\",\"assigneeId\":\"" + owner.getId() + "\"}"), owner.getId(), "Alice Smith");
        assertAssignment(call(ownerToken, patch(tasksUrl + "/" + task),
                "{\"assigneeId\":null,\"assigneeName\":\"Игнорировать\"}"), null, null);
        assertAssignment(call(ownerToken, patch(tasksUrl + "/" + task), Map.of("assigneeId", member.getId())), member.getId(), "Alice Smith");
        assertAssignment(call(ownerToken, patch(tasksUrl + "/" + task), Map.of("assigneeName", "  Свободное имя  ")), null, "Свободное имя");
        assertAssignment(call(ownerToken, patch(tasksUrl + "/" + task), "{\"assigneeName\":null}"), null, null);
        call(ownerToken, patch(tasksUrl + "/" + task), "{}").andExpect(status().isUnprocessableContent());
        call(ownerToken, patch(tasksUrl + "/" + task), Map.of("assigneeId", "bad-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void creationAndTemplatesShareAssignmentAndLegacyNameRules() throws Exception {
        var created = call(ownerToken, post(tasksUrl), Map.of("title", "ID", "stage", "BEFORE_MOVE",
                "assigneeId", owner.getId(), "assigneeName", "Игнорировать")).andExpect(status().isCreated());
        assertAssignment(created, owner.getId(), "Alice Smith");
        var cleared = call(ownerToken, post(tasksUrl), "{\"title\":\"Пусто\",\"stage\":\"BEFORE_MOVE\",\"assigneeName\":\"Игнорировать\",\"assigneeId\":null}")
                .andExpect(status().isCreated());
        assertAssignment(cleared, null, null);
        assertAssignment(call(ownerToken, post(tasksUrl), Map.of("title", "Имя", "stage", "BEFORE_MOVE", "assigneeName", "  Имя  "))
                .andExpect(status().isCreated()), null, "Имя");
        UUID template = jdbc.queryForObject("SELECT id FROM task_templates WHERE owner_id IS NULL ORDER BY id LIMIT 1", UUID.class);
        assertAssignment(call(memberToken, post(tasksUrl + "/from-template"), Map.of("templateId", template,
                "assigneeId", other.getId(), "assigneeName", "Игнорировать")).andExpect(status().isCreated()), other.getId(), "Alice Smith");
        call(ownerToken, delete(tasksUrl + "/" + id(created)), null).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"existing-stranger", "missing-user"})
    void invalidAssigneeRollsBackCreatePatchAndTemplate(String scenario) throws Exception {
        UUID invalid = scenario.equals("existing-stranger") ? stranger.getId() : UUID.randomUUID();
        UUID task = task(member.getId());
        var before = json(call(ownerToken, get(tasksUrl + "/" + task), null).andExpect(status().isOk()));
        for (var request : List.of(
                call(ownerToken, patch(tasksUrl + "/" + task), Map.of("title", "Не сохранить", "assigneeId", invalid)),
                call(ownerToken, post(tasksUrl), Map.of("title", "Не создать", "stage", "BEFORE_MOVE", "assigneeId", invalid)),
                call(ownerToken, post(tasksUrl + "/from-template"), Map.of("templateId", jdbc.queryForObject(
                        "SELECT id FROM task_templates WHERE owner_id IS NULL ORDER BY id LIMIT 1", UUID.class), "assigneeId", invalid)))) {
            request.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("assigneeId"));
        }
        assertThat(json(call(ownerToken, get(tasksUrl + "/" + task), null).andExpect(status().isOk()))).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM move_tasks WHERE project_id = ?", Long.class, projectId)).isEqualTo(1);
    }

    @Test
    void completionAndRescheduleReturnTheSameCurrentAssignment() throws Exception {
        UUID task = id(call(ownerToken, post(tasksUrl), Map.of("title", "Относительная задача", "stage", "BEFORE_MOVE",
                "dueType", "RELATIVE", "offsetDays", -2, "assigneeId", member.getId())).andExpect(status().isCreated()));
        var rescheduled = json(call(otherToken, post(projectUrl + "/reschedule"), Map.of("moveDate", "2026-11-10T09:00:00Z", "taskIds", List.of(task)))
                .andExpect(status().isOk()));
        assertThat(rescheduled.path("tasks").path(0).path("assigneeId").asText()).isEqualTo(member.getId().toString());
        assertThat(rescheduled.path("tasks").path(0).path("assigneeName").asText()).isEqualTo("Alice Smith");
        assertAssignment(call(memberToken, patch(tasksUrl + "/" + task + "/completion"), Map.of("completed", true)), member.getId(), "Alice Smith");
    }

    @Test
    void removalClearsAllLinkedTasksAndPreservesDataOtherProjectsAndLegacyNames() throws Exception {
        UUID active = task(member.getId()), completed = task(member.getId()), untouched = task(other.getId());
        UUID free = id(call(ownerToken, post(tasksUrl), Map.of("title", "Свободное имя", "stage", "BEFORE_MOVE", "assigneeName", "Alice Smith"))
                .andExpect(status().isCreated()));
        call(memberToken, patch(tasksUrl + "/" + completed + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
        var before = jdbc.queryForList("SELECT id,title,description,stage,due_date,completed_at,created_at FROM move_tasks WHERE project_id = ? ORDER BY id", projectId);
        var timestamp = jdbc.queryForObject("SELECT updated_at FROM move_tasks WHERE id = ?", java.sql.Timestamp.class, active).toInstant();
        UUID foreign = id(call(memberToken, post("/api/move-projects"), Map.of("name", "Другой проект", "moveDate", "2026-11-01T09:00:00Z"))
                .andExpect(status().isCreated()));
        String foreignTasks = "/api/move-projects/" + foreign + "/tasks";
        UUID foreignTask = id(call(memberToken, post(foreignTasks), Map.of("title", "Другая задача", "stage", "BEFORE_MOVE", "assigneeId", member.getId()))
                .andExpect(status().isCreated()));
        UUID room = id(call(memberToken, post(projectUrl + "/rooms"), Map.of("name", "Кухня")).andExpect(status().isCreated()));
        UUID box = id(call(memberToken, post("/api/projects/" + projectId + "/boxes"), Map.of("number", 1, "roomId", room)).andExpect(status().isCreated()));
        String items = projectUrl + "/boxes/" + box + "/items";
        UUID item = id(call(memberToken, post(items), Map.of("name", "Книга")).andExpect(status().isCreated()));
        var events = jdbc.queryForList("SELECT * FROM project_events WHERE project_id = ? ORDER BY id", projectId);
        call(otherToken, delete(participantsUrl + "/" + member.getId()), null).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertAssignment(call(ownerToken, get(tasksUrl + "/" + active), null), null, null);
        assertAssignment(call(ownerToken, get(tasksUrl + "/" + completed), null), null, null);
        assertAssignment(call(ownerToken, get(tasksUrl + "/" + untouched), null), other.getId(), "Alice Smith");
        assertAssignment(call(ownerToken, get(tasksUrl + "/" + free), null), null, "Alice Smith");
        assertAssignment(call(memberToken, get(foreignTasks + "/" + foreignTask), null), member.getId(), "Alice Smith");
        assertThat(jdbc.queryForList("SELECT id,title,description,stage,due_date,completed_at,created_at FROM move_tasks WHERE project_id = ? ORDER BY id", projectId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM move_tasks WHERE id = ?", java.sql.Timestamp.class, active).toInstant()).isAfter(timestamp);
        assertThat(jdbc.queryForList("SELECT * FROM project_events WHERE project_id = ? ORDER BY id", projectId)).isEqualTo(events);
        call(otherToken, get(items + "/" + item), null).andExpect(status().isOk());
        var list = json(call(ownerToken, get(participantsUrl), null).andExpect(status().isOk()));
        assertThat(textFields(list, "userId")).containsExactly(owner.getId().toString(), other.getId().toString());
        call(ownerToken, delete(participantsUrl + "/" + member.getId()), null).andExpect(status().isNoContent());
        call(ownerToken, post(participantsUrl), Map.of("userId", member.getId())).andExpect(status().isOk());
        assertAssignment(call(memberToken, get(tasksUrl + "/" + active), null), null, null);
    }

    @Test
    void removalRevokesBothSessionsAndNewLoginAcrossAllProjectResources() throws Exception {
        UUID task = task(member.getId());
        String secondDevice = authenticateUser(member.getEmail()).getAccessToken();
        call(ownerToken, delete(participantsUrl + "/" + member.getId()), null).andExpect(status().isNoContent());
        String freshLogin = authenticateUser(member.getEmail()).getAccessToken();
        for (String token : List.of(memberToken, secondDevice, freshLogin)) {
            for (String path : List.of(projectUrl, participantsUrl, projectUrl + "/rooms", "/api/projects/" + projectId + "/boxes",
                    tasksUrl, tasksUrl + "/" + task, projectUrl + "/first-day-checklist", projectUrl + "/events",
                    "/api/projects/" + projectId + "/progress/tasks", "/api/projects/" + projectId + "/progress/boxes")) {
                call(token, get(path), null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MOVE_PROJECT_NOT_FOUND"));
            }
            call(token, get("/api/move-projects"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            call(token, get(projectUrl + "/items").param("q", "Книга"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            call(token, patch(tasksUrl + "/" + task), Map.of("title", "Запрещено")).andExpect(status().isNotFound());
            call(token, post(participantsUrl), Map.of("userId", member.getId())).andExpect(status().isNotFound());
        }
    }

    @Test
    void selfExitCreatorArchiveAndInvalidRequestsFollowContract() throws Exception {
        call(memberToken, delete(participantsUrl + "/" + owner.getId()), null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
        call(ownerToken, delete(participantsUrl + "/" + owner.getId()), null).andExpect(status().isConflict());
        call(strangerToken, delete(participantsUrl + "/" + member.getId()), null).andExpect(status().isNotFound());
        call(ownerToken, delete(participantsUrl + "/bad-uuid"), null).andExpect(status().isBadRequest());
        mockMvc.perform(delete(participantsUrl + "/" + member.getId())).andExpect(status().isUnauthorized());
        call(ownerToken, delete(participantsUrl + "/" + UUID.randomUUID()), null).andExpect(status().isNoContent());
        call(ownerToken, put(projectUrl + "/archive"), null).andExpect(status().isOk());
        call(memberToken, delete(participantsUrl + "/" + member.getId()), null).andExpect(status().isConflict());
        call(ownerToken, delete(participantsUrl + "/" + UUID.randomUUID()), null).andExpect(status().isConflict());
        call(ownerToken, post(tasksUrl), Map.of("title", "Архив", "stage", "BEFORE_MOVE", "assigneeId", member.getId())).andExpect(status().isConflict());
        call(ownerToken, delete(projectUrl + "/archive"), null).andExpect(status().isOk());
        call(memberToken, delete(participantsUrl + "/" + member.getId()), null).andExpect(status().isNoContent());
        call(memberToken, delete(participantsUrl + "/" + member.getId()), null).andExpect(status().isNotFound());
        call(ownerToken, get(projectUrl), null).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"revoked-writer", "assignment"})
    void requestWaitingForProjectLockRechecksAccessAndCannotLeaveRemovedAssignee(String operation) throws Exception {
        UUID task = task(member.getId());
        var executor = Executors.newSingleThreadExecutor();
        Future<?>[] pending = new Future<?>[1];
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                // Hold the same row lock used by removal, before the other statement takes its snapshot.
                moveProjectRepository.findForUpdate(projectId, owner.getId()).orElseThrow();
                pending[0] = executor.submit(() -> {
                    String token = operation.equals("revoked-writer") ? memberToken : ownerToken;
                    Object body = operation.equals("revoked-writer") ? Map.of("title", "Запрещено") : Map.of("assigneeId", member.getId());
                    return call(token, patch(tasksUrl + "/" + task), body).andExpect(status().is(operation.equals("revoked-writer") ? 404 : 422));
                });
                awaitBlockedProjectStatement();
                participants.remove(projectId, owner.getId(), member.getId());
            });
            pending[0].get(10, TimeUnit.SECONDS);
            assertAssignment(call(ownerToken, get(tasksUrl + "/" + task), null), null, null);
            assertThat(jdbc.queryForObject("SELECT title FROM move_tasks WHERE id = ?", String.class, task)).isEqualTo("Задача");
        } finally { executor.shutdownNow(); }
    }

    @Test
    void assignmentFinishingBeforeRemovalIsAlsoCleared() throws Exception {
        UUID task = task(owner.getId());
        var executor = Executors.newSingleThreadExecutor();
        Future<?>[] pending = new Future<?>[1];
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                moveProjectRepository.findForUpdate(projectId, owner.getId()).orElseThrow();
                pending[0] = executor.submit(() -> call(ownerToken, delete(participantsUrl + "/" + member.getId()), null).andExpect(status().isNoContent()));
                awaitBlockedProjectStatement();
                try {
                    call(ownerToken, patch(tasksUrl + "/" + task), Map.of("assigneeId", member.getId())).andExpect(status().isOk());
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            pending[0].get(10, TimeUnit.SECONDS);
            assertAssignment(call(ownerToken, get(tasksUrl + "/" + task), null), null, null);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void taskListFetchesAssignedProfilesWithoutPerTaskQueries() throws Exception {
        task(member.getId());
        int single = listQueryCount();
        task(other.getId()); task(owner.getId());
        assertThat(listQueryCount()).isEqualTo(single);
    }

    private int listQueryCount() throws Exception {
        capture.start();
        List<JdbcCaptureConfig.Execution> executions;
        try { call(ownerToken, get(tasksUrl), null).andExpect(status().isOk()); }
        finally { executions = capture.stop(); }
        return executions.size();
    }

    private void awaitBlockedProjectStatement() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            // pg_stat_activity has a transaction-local snapshot; clear it on each observation.
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid() AND wait_event_type = 'Lock' AND query LIKE '%move_projects%')", Boolean.class))) return;
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
        throw new AssertionError("Request did not wait for the project lock");
    }

    private UserEntity account() throws Exception {
        String email = "assignment-" + UUID.randomUUID() + "@example.com";
        registerUser(email);
        return userRepository.findByEmail(email).orElseThrow();
    }

    private UUID task(UUID assignee) throws Exception {
        return id(call(ownerToken, post(tasksUrl), Map.of("title", "Задача", "stage", "BEFORE_MOVE", "assigneeId", assignee))
                .andExpect(status().isCreated()));
    }

    private void assertAssignment(ResultActions response, UUID assignee, String name) throws Exception {
        var body = json(response.andExpect(status().is2xxSuccessful()));
        if (assignee == null) assertThat(body.path("assigneeId").isNull()).isTrue();
        else assertThat(body.path("assigneeId").asText()).isEqualTo(assignee.toString());
        if (name == null) assertThat(body.path("assigneeName").isNull()).isTrue();
        else assertThat(body.path("assigneeName").asText()).isEqualTo(name);
    }

    private List<String> textFields(JsonNode array, String field) {
        var values = new java.util.ArrayList<String>();
        for (var element : array) values.add(element.path(field).asText());
        return values;
    }

    private UUID id(ResultActions response) throws Exception { return UUID.fromString(json(response).path("id").asText()); }
    private JsonNode json(ResultActions response) throws Exception { return jsonMapper.readTree(response.andReturn().getResponse().getContentAsString()); }
    private ResultActions call(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String text ? text : jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }
}
