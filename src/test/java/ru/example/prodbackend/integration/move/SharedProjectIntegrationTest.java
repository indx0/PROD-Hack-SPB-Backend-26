package ru.example.prodbackend.integration.move;

import ru.example.prodbackend.support.BaseIntegrationTest;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.auth.service.JwtService;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// All HTTP requests commit independently: different users observe the same persisted project.
class SharedProjectIntegrationTest extends BaseIntegrationTest {
    private static final List<String> OPERATIONS = List.of("project-update", "project-delete", "room-create", "room-update", "room-delete",
            "box-create", "box-update", "box-delete", "box-status", "box-correct", "item-create", "item-batch", "item-batch-objects",
            "item-update", "item-delete", "item-move", "task-create", "task-update", "task-complete", "task-delete", "task-template",
            "checklist-create", "checklist-update", "checklist-delete", "checklist-link", "checklist-unlink", "participant-add");
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    private UserEntity owner;
    private UserEntity member;
    private UserEntity nextMember;
    private UserEntity stranger;
    private String ownerToken;
    private String memberToken;
    private String nextToken;
    private String strangerToken;
    private UUID projectId;
    private UUID foreignProjectId;
    private UUID roomId;
    private UUID emptyRoomId;
    private UUID boxId;
    private UUID targetId;
    private UUID itemId;
    private UUID taskId;
    private UUID pointId;
    private UUID templateId;
    private String projectUrl;
    private String participantsUrl;
    private String boxesUrl;
    private String itemsUrl;
    private String tasksUrl;
    private String checklistUrl;

    @BeforeEach
    void setUp() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            owner = user(); member = user(); nextMember = user(); stranger = user();
            ownerToken = jwtService.generateToken(owner);
            memberToken = jwtService.generateToken(member);
            nextToken = jwtService.generateToken(nextMember);
            strangerToken = jwtService.generateToken(stranger);
        });
        projectId = id(perform(ownerToken, post("/api/move-projects"), createRequest(List.of(Map.of("email", member.getEmail())))).andExpect(status().isCreated()));
        foreignProjectId = id(perform(strangerToken, post("/api/move-projects"), createRequest(List.of())).andExpect(status().isCreated()));
        projectUrl = "/api/move-projects/" + projectId;
        participantsUrl = projectUrl + "/participants";
        boxesUrl = "/api/projects/" + projectId + "/boxes";
        tasksUrl = projectUrl + "/tasks";
        checklistUrl = projectUrl + "/first-day-checklist";
        roomId = id(perform(ownerToken, post(projectUrl + "/rooms"), Map.of("name", "Кухня")).andExpect(status().isCreated()));
        UUID bedroom = id(perform(ownerToken, post(projectUrl + "/rooms"), Map.of("name", "Спальня")).andExpect(status().isCreated()));
        emptyRoomId = id(perform(ownerToken, post(projectUrl + "/rooms"), Map.of("name", "Пустая комната")).andExpect(status().isCreated()));
        boxId = id(perform(ownerToken, post(boxesUrl), Map.of("number", 1, "roomId", roomId)).andExpect(status().isCreated()));
        targetId = id(perform(ownerToken, post(boxesUrl), Map.of("number", 2, "roomId", bedroom)).andExpect(status().isCreated()));
        itemsUrl = projectUrl + "/boxes/" + boxId + "/items";
        itemId = id(perform(ownerToken, post(itemsUrl), Map.of("name", "Зарядка USB-C", "quantity", 2)).andExpect(status().isCreated()));
        perform(ownerToken, patch(itemsUrl + "/" + itemId), Map.of("packed", true)).andExpect(status().isOk());
        taskId = id(perform(ownerToken, post(tasksUrl), Map.of("title", "Собрать вещи", "stage", "BEFORE_MOVE")).andExpect(status().isCreated()));
        pointId = UUID.randomUUID();
        jdbc.update("INSERT INTO first_day_checklist_items(id,project_id,name,item_id,created_at,updated_at) VALUES (?,?,'Первый день',?,now(),now())", pointId, projectId, itemId);
        ru.example.prodbackend.support.LegacyChecklistTestSupport.snapshot(jdbc);
        templateId = jdbc.queryForObject("SELECT id FROM task_templates ORDER BY id LIMIT 1", UUID.class);
        jdbc.update("DELETE FROM move_tasks WHERE project_id = ? AND template_id = ?", projectId, templateId);
    }

    @Test
    void createsWithEmailAndIdDeduplicatesAndMakesProjectVisibleImmediately() throws Exception {
        var result = perform(ownerToken, post("/api/move-projects"), createRequest(List.of(Map.of("userId", owner.getId()), Map.of("email", owner.getEmail()),
                Map.of("email", member.getEmail()), Map.of("userId", member.getId()), Map.of("userId", nextMember.getId()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerId").value(owner.getId().toString()));
        UUID createdId = id(result);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_participants WHERE project_id = ?", Long.class, createdId)).isEqualTo(2);
        perform(nextToken, get("/api/move-projects/" + createdId), null).andExpect(status().isOk());
        perform(nextToken, get("/api/move-projects"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(createdId.toString()));
        var participants = json(perform(memberToken, get("/api/move-projects/" + createdId + "/participants"), null).andExpect(status().isOk()));
        assertThat(participants.size()).isEqualTo(3);
        assertThat(participants.get(0).get("creator").asBoolean()).isTrue();
        assertThat(participants.get(0).get("userId").asText()).isEqualTo(owner.getId().toString());
        for (var participant : participants) {
            assertThat(participant.has("password")).isFalse();
            assertThat(participant.has("role")).isFalse();
        }
    }

    @Test
    void participantCanAddAnotherUserAndRepeatedEmailOrIdDoesNotDuplicate() throws Exception {
        var first = json(perform(memberToken, post(participantsUrl), Map.of("userId", nextMember.getId())).andExpect(status().isOk()));
        var repeated = json(perform(ownerToken, post(participantsUrl), Map.of("email", "  " + nextMember.getEmail() + "  ")).andExpect(status().isOk()));
        assertThat(repeated).isEqualTo(first);
        perform(nextToken, patch(projectUrl), Map.of("name", "Название от участника")).andExpect(status().isOk());
        perform(ownerToken, get(projectUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Название от участника"));
        perform(memberToken, post(participantsUrl), Map.of("userId", owner.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.creator").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_participants WHERE project_id = ?", Long.class, projectId)).isEqualTo(2);
        assertThat(json(perform(nextToken, get(participantsUrl), null).andExpect(status().isOk())).size()).isEqualTo(3);
    }

    @Test
    void allReadPathsSearchSuggestionsAndChecklistWorkForParticipant() throws Exception {
        for (String path : List.of(projectUrl, participantsUrl, projectUrl + "/rooms", boxesUrl, boxesUrl + "/" + boxId, boxesUrl + "/summary",
                itemsUrl, itemsUrl + "/" + itemId, projectUrl + "/items?q=Зарядка", projectUrl + "/items/" + itemId + "/suggestions",
                tasksUrl, tasksUrl + "/" + taskId, tasksUrl + "/templates", checklistUrl, projectUrl + "/events",
                "/api/projects/" + projectId + "/progress/boxes", "/api/projects/" + projectId + "/progress/tasks")) {
            perform(memberToken, get(path), null).andExpect(status().isOk());
        }
        perform(memberToken, get(projectUrl + "/items").param("q", "Зарядка"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        perform(memberToken, get(projectUrl + "/items/" + itemId + "/suggestions"), null).andExpect(status().isOk()).andExpect(jsonPath("$[0].boxId").value(boxId.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"project-update", "project-delete", "room-create", "room-update", "room-delete", "box-create", "box-update", "box-delete",
            "box-status", "box-correct", "item-create", "item-batch", "item-batch-objects", "item-update", "item-delete", "item-move",
            "task-create", "task-update", "task-complete", "task-delete", "task-template", "checklist-create", "checklist-update", "checklist-delete",
            "checklist-link", "checklist-unlink", "participant-add"})
    void participantHasSameMutationRightsAsCreator(String operation) throws Exception {
        if (operation.equals("box-correct")) jdbc.update("UPDATE boxes SET status = 'DELIVERED' WHERE id = ?", boxId);
        int expected = operation.endsWith("delete") ? 204 : operation.endsWith("create") || operation.startsWith("item-batch") || operation.equals("task-template") ? 201 : 200;
        if (operation.startsWith("checklist-")) expected = 410;
        mutate(memberToken, operation).andExpect(status().is(expected));
        if (operation.equals("project-delete")) {
            perform(ownerToken, get(projectUrl), null).andExpect(status().isNotFound());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project_participants WHERE project_id = ?", Long.class, projectId)).isZero();
        } else perform(ownerToken, get(projectUrl), null).andExpect(status().isOk());
        String eventType = switch (operation) {
            case "box-create" -> "BOX_CREATED";
            case "box-status" -> "BOX_STATUS_CHANGED";
            case "box-correct" -> "BOX_STATUS_CORRECTED";
            case "task-complete" -> "TASK_COMPLETED";
            default -> null;
        };
        if (eventType != null) assertThat(jdbc.queryForObject("SELECT actor_id FROM project_events WHERE project_id = ? AND event_type = ? ORDER BY occurred_at DESC, id DESC LIMIT 1", UUID.class, projectId, eventType)).isEqualTo(member.getId());
    }

    @Test
    void archiveRulesApplyToEveryoneAndAnyParticipantCanArchiveRestoreAndManageUsers() throws Exception {
        var archived = json(perform(memberToken, put(projectUrl + "/archive"), null).andExpect(status().isOk()));
        assertThat(json(perform(ownerToken, put(projectUrl + "/archive"), null).andExpect(status().isOk()))).isEqualTo(archived);
        for (String operation : OPERATIONS) mutate(memberToken, operation).andExpect(status().is(operation.startsWith("checklist-") ? 410 : 409))
                .andExpect(jsonPath("$.code").value(operation.startsWith("checklist-") ? "FIRST_DAY_CHECKLIST_RETIRED" : "DATA_CONFLICT"));
        perform(memberToken, get(participantsUrl), null).andExpect(status().isOk());
        perform(memberToken, get("/api/move-projects").param("archived", "true"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        perform(memberToken, delete(projectUrl + "/archive"), null).andExpect(status().isOk());
        perform(memberToken, post(participantsUrl), Map.of("userId", nextMember.getId())).andExpect(status().isOk());
        perform(nextToken, post(tasksUrl), Map.of("title", "Общая задача", "stage", "BEFORE_MOVE")).andExpect(status().isCreated());
        var actors = jdbc.queryForList("SELECT actor_id FROM project_events WHERE project_id = ? AND event_type IN ('PROJECT_ARCHIVED', 'PROJECT_RESTORED')", UUID.class, projectId);
        assertThat(actors).containsExactly(member.getId(), member.getId());
    }

    @Test
    void outsidersCannotReadOrModifyProjectOrAddThemselves() throws Exception {
        for (String path : List.of(projectUrl, participantsUrl, projectUrl + "/rooms", boxesUrl, boxesUrl + "/" + boxId,
                itemsUrl, itemsUrl + "/" + itemId, projectUrl + "/items/" + itemId + "/suggestions", tasksUrl, tasksUrl + "/templates",
                checklistUrl, projectUrl + "/events", "/api/projects/" + projectId + "/progress/boxes", "/api/projects/" + projectId + "/progress/tasks")) {
            perform(strangerToken, get(path), null).andExpect(status().isNotFound());
        }
        for (String operation : OPERATIONS) mutate(strangerToken, operation).andExpect(status().isNotFound());
        perform(strangerToken, post(participantsUrl), Map.of("userId", stranger.getId())).andExpect(status().isNotFound());
        perform(strangerToken, put(projectUrl + "/archive"), null).andExpect(status().isNotFound());
        perform(strangerToken, delete(projectUrl + "/archive"), null).andExpect(status().isNotFound());
        perform(strangerToken, get(projectUrl + "/items").param("q", "Зарядка"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_participants WHERE project_id = ?", Long.class, projectId)).isEqualTo(1);
        mockMvc.perform(get(participantsUrl)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(participantsUrl).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + stranger.getId() + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void sharingIsScopedToOneProjectAndDoesNotGrantAccessToOtherMembersProjects() throws Exception {
        perform(memberToken, post(participantsUrl), Map.of("userId", stranger.getId())).andExpect(status().isOk());
        perform(strangerToken, get(projectUrl), null).andExpect(status().isOk());
        perform(memberToken, get("/api/move-projects/" + foreignProjectId), null).andExpect(status().isNotFound());
        perform(memberToken, post("/api/move-projects/" + foreignProjectId + "/participants"), Map.of("userId", member.getId())).andExpect(status().isNotFound());
        perform(memberToken, get("/api/move-projects"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        perform(strangerToken, get("/api/move-projects"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void sharedListHasNoDuplicatesAndArchiveFilterHasAccuratePagination() throws Exception {
        perform(ownerToken, post(participantsUrl), Map.of("userId", member.getId())).andExpect(status().isOk());
        perform(ownerToken, post(participantsUrl), Map.of("email", member.getEmail())).andExpect(status().isOk());
        UUID personal = id(perform(memberToken, post("/api/move-projects"), createRequest(List.of())).andExpect(status().isCreated()));
        UUID otherShared = id(perform(ownerToken, post("/api/move-projects"), createRequest(List.of(Map.of("userId", member.getId())))).andExpect(status().isCreated()));
        jdbc.update("UPDATE move_projects SET created_at = '2026-10-01T10:00:00Z' WHERE id = ?", projectId);
        jdbc.update("UPDATE move_projects SET created_at = '2026-10-02T10:00:00Z' WHERE id = ?", personal);
        jdbc.update("UPDATE move_projects SET created_at = '2026-10-03T10:00:00Z' WHERE id = ?", otherShared);
        perform(ownerToken, put(projectUrl + "/archive"), null).andExpect(status().isOk());
        perform(memberToken, get("/api/move-projects").param("size", "1").param("page", "1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.content[0].id").value(personal.toString()));
        perform(memberToken, get("/api/move-projects").param("archived", "false").param("size", "1").param("page", "1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].id").value(personal.toString()));
        perform(memberToken, get("/api/move-projects").param("archived", "true"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(projectId.toString()));
        perform(memberToken, get("/api/move-projects").param("size", "1").param("page", "3"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void validatesSelectorsAndRollsBackCreationIfAnyUserIsMissing() throws Exception {
        for (Object request : List.of(Map.of(), Map.of("email", "bad-email"), Map.of("email", ""), Map.of("email", " "),
                Map.of("email", member.getEmail(), "userId", member.getId()))) {
            perform(ownerToken, post(participantsUrl), request).andExpect(status().is(422));
            perform(ownerToken, post("/api/move-projects"), createRequest(List.of(request))).andExpect(status().is(422));
        }
        long projects = moveProjectRepository.count();
        long tasks = jdbc.queryForObject("SELECT count(*) FROM move_tasks", Long.class);
        long checklist = jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_items", Long.class);
        long events = jdbc.queryForObject("SELECT count(*) FROM project_events", Long.class);
        perform(ownerToken, post(participantsUrl), Map.of("userId", UUID.randomUUID())).andExpect(status().isNotFound());
        perform(ownerToken, post(participantsUrl), Map.of("email", "absent@example.com")).andExpect(status().isNotFound());
        perform(ownerToken, post("/api/move-projects"), createRequest(List.of(Map.of("userId", member.getId()), Map.of("userId", UUID.randomUUID())))).andExpect(status().isNotFound());
        var tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) tooMany.add(Map.of("userId", member.getId()));
        perform(ownerToken, post("/api/move-projects"), createRequest(tooMany)).andExpect(status().is(422));
        assertThat(moveProjectRepository.count()).isEqualTo(projects);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM move_tasks", Long.class)).isEqualTo(tasks);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_items", Long.class)).isEqualTo(checklist);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_events", Long.class)).isEqualTo(events);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_participants WHERE project_id = ?", Long.class, projectId)).isEqualTo(1);
    }

    @Test
    void movedItemStaysAccessibleAndHistoricalChecklistKeepsSnapshotForBothUsers() throws Exception {
        perform(memberToken, put(itemsUrl + "/" + itemId + "/box"), Map.of("targetBoxId", targetId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId.toString())).andExpect(jsonPath("$.quantity").value(2));
        for (String token : List.of(ownerToken, memberToken)) {
            perform(token, get(projectUrl + "/items").param("q", "Зарядка"), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].boxId").value(targetId.toString())).andExpect(jsonPath("$.content[0].roomName").value("Спальня"));
            var points = json(perform(token, get(checklistUrl), null).andExpect(status().isOk())).get("content");
            boolean found = false;
            for (var point : points) {
                if (!point.get("id").asText().equals(pointId.toString())) continue;
                assertThat(point.get("boxId").asText()).isEqualTo(boxId.toString());
                found = true;
            }
            assertThat(found).isTrue();
        }
    }

    @Test
    void concurrentAddsByDifferentUsersCreateOnlyOneMembership() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return json(perform(ownerToken, post(participantsUrl), Map.of("userId", nextMember.getId())).andExpect(status().isOk())); });
            var second = executor.submit(() -> { start.await(); return json(perform(memberToken, post(participantsUrl), Map.of("email", nextMember.getEmail())).andExpect(status().isOk())); });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM project_participants WHERE project_id = ? AND user_id = ?", Long.class, projectId, nextMember.getId())).isEqualTo(1);
            perform(nextToken, get(projectUrl), null).andExpect(status().isOk());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void differentParticipantsCannotAllocateTheSameBoxNumber() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var body = Map.of("number", 7, "roomId", roomId);
            var first = executor.submit(() -> { start.await(); return perform(ownerToken, post(boxesUrl), body).andReturn().getResponse().getStatus(); });
            var second = executor.submit(() -> { start.await(); return perform(memberToken, post(boxesUrl), body).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM boxes WHERE project_id = ? AND number = 7", Long.class, projectId)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    private ResultActions mutate(String token, String operation) throws Exception {
        return switch (operation) {
            case "project-update" -> perform(token, patch(projectUrl), Map.of("name", "Новое название"));
            case "project-delete" -> perform(token, delete(projectUrl), null);
            case "room-create" -> perform(token, post(projectUrl + "/rooms"), Map.of("name", "Новая комната"));
            case "room-update" -> perform(token, patch(projectUrl + "/rooms/" + roomId), Map.of("iconKey", "bed"));
            case "room-delete" -> perform(token, delete(projectUrl + "/rooms/" + emptyRoomId), null);
            case "box-create" -> perform(token, post(boxesUrl), Map.of("number", 3, "roomId", roomId));
            case "box-update" -> perform(token, patch(boxesUrl + "/" + boxId), Map.of("title", "Новая подпись"));
            case "box-delete" -> perform(token, delete(boxesUrl + "/" + targetId), null);
            case "box-status" -> perform(token, patch(boxesUrl + "/" + boxId + "/status"), Map.of("status", "PACKED"));
            case "box-correct" -> perform(token, post(boxesUrl + "/" + boxId + "/status/correction"), Map.of("expectedStatus", "DELIVERED", "targetStatus", "PACKING", "confirmed", true));
            case "item-create" -> perform(token, post(itemsUrl), Map.of("name", "Новая вещь"));
            case "item-batch" -> perform(token, post(itemsUrl + "/batch"), Map.of("names", List.of("Книга", "Тетрадь")));
            case "item-batch-objects" -> perform(token, post(itemsUrl + "/batch"), Map.of("items", List.of(Map.of("name", "Тарелки", "quantity", 6))));
            case "item-update" -> perform(token, patch(itemsUrl + "/" + itemId), Map.of("name", "Новое имя", "quantity", 3, "firstDay", false));
            case "item-delete" -> perform(token, delete(itemsUrl + "/" + itemId), null);
            case "item-move" -> perform(token, put(itemsUrl + "/" + itemId + "/box"), Map.of("targetBoxId", targetId));
            case "task-create" -> perform(token, post(tasksUrl), Map.of("title", "Новая задача", "stage", "BEFORE_MOVE"));
            case "task-update" -> perform(token, patch(tasksUrl + "/" + taskId), Map.of("title", "Новое название"));
            case "task-complete" -> perform(token, patch(tasksUrl + "/" + taskId + "/completion"), Map.of("completed", true));
            case "task-delete" -> perform(token, delete(tasksUrl + "/" + taskId), null);
            case "task-template" -> perform(token, post(tasksUrl + "/from-template"), Map.of("templateId", templateId));
            case "checklist-create" -> perform(token, post(checklistUrl), Map.of("name", "Документы"));
            case "checklist-update" -> perform(token, patch(checklistUrl + "/" + pointId), Map.of("name", "Новый пункт", "completed", true));
            case "checklist-delete" -> perform(token, delete(checklistUrl + "/" + pointId), null);
            case "checklist-link" -> perform(token, put(checklistUrl + "/" + pointId + "/item"), Map.of("itemId", itemId));
            case "checklist-unlink" -> perform(token, delete(checklistUrl + "/" + pointId + "/item"), null);
            case "participant-add" -> perform(token, post(participantsUrl), Map.of("userId", nextMember.getId()));
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private Map<String, Object> createRequest(List<?> participants) {
        return Map.of("name", "Совместный переезд", "moveDate", "2026-11-01T09:00:00Z", "participants", participants);
    }

    private UUID id(ResultActions result) throws Exception { return UUID.fromString(json(result).get("id").asText()); }
    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()); }

    private ResultActions perform(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }

    private UserEntity user() {
        var user = UserEntity.builder().email("shared-" + UUID.randomUUID() + "@example.com").password("hash").role(UserRole.USER)
                .firstName("Участник").lastName("Переезда").build();
        entityManager.persist(user);
        return user;
    }
}
