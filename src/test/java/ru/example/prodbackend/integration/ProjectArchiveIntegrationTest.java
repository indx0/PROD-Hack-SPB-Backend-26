package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.item.dto.UpdateItemRequest;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.service.MoveProjectService;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.repository.TaskTemplateRepository;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No test transaction: each request commits before assertions and later requests.
class ProjectArchiveIntegrationTest extends BaseIntegrationTest {
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private MoveProjectService projectService;
    @Autowired private ItemService itemService;
    @Autowired private TaskTemplateRepository templateRepository;
    private UserEntity owner;
    private UserEntity stranger;
    private UUID projectId;
    private UUID roomId;
    private UUID emptyRoomId;
    private UUID boxId;
    private UUID targetBoxId;
    private UUID itemId;
    private UUID taskId;
    private UUID pointId;
    private UUID templateId;
    private String token;
    private String projectUrl;
    private String archiveUrl;
    private String boxesUrl;
    private String itemsUrl;
    private String tasksUrl;
    private String checklistUrl;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            owner = user();
            stranger = user();
            token = jwtService.generateToken(owner);
            var project = project(owner, "Переезд");
            projectId = project.getId();
            var room = RoomEntity.builder().project(project).name("Кухня").iconKey("kitchen").build();
            var emptyRoom = RoomEntity.builder().project(project).name("Пустая комната").build();
            entityManager.persist(room);
            entityManager.persist(emptyRoom);
            roomId = room.getId();
            emptyRoomId = emptyRoom.getId();
            var box = BoxEntity.builder().project(project).room(room).number(1).title("Посуда").fragile(true).build();
            var target = BoxEntity.builder().project(project).room(room).number(2).build();
            entityManager.persist(box);
            entityManager.persist(target);
            boxId = box.getId();
            targetBoxId = target.getId();
            var item = ItemEntity.builder().box(box).name("Зарядка USB-C").quantity(2).firstDay(true).packedAt(Instant.now()).build();
            entityManager.persist(item);
            itemId = item.getId();
            var task = MoveTaskEntity.builder().project(project).title("Собрать вещи").stage(TaskStage.BEFORE_MOVE).build();
            entityManager.persist(task);
            taskId = task.getId();
            var point = FirstDayChecklistItemEntity.builder().project(project).name("Зарядка первого дня").item(item).build();
            entityManager.persist(point);
            pointId = point.getId();
            templateId = templateRepository.findAll().get(0).getId();
        });
        projectUrl = "/api/move-projects/" + projectId;
        archiveUrl = projectUrl + "/archive";
        boxesUrl = "/api/projects/" + projectId + "/boxes";
        itemsUrl = projectUrl + "/boxes/" + boxId + "/items";
        tasksUrl = projectUrl + "/tasks";
        checklistUrl = projectUrl + "/first-day-checklist";
    }

    @Test
    void archivesRestoresAndRepeatsWithoutChangingContentsOrDuplicatingEvents() throws Exception {
        var contents = contentSnapshot();
        perform(get(projectUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
        var archived = json(perform(put(archiveUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty()));
        assertThat(json(perform(get(projectUrl), null).andExpect(status().isOk()))).isEqualTo(archived);
        assertThat(json(perform(put(archiveUrl), null).andExpect(status().isOk()))).isEqualTo(archived);
        assertThat(contentSnapshot()).isEqualTo(contents);
        assertThat(eventCount()).isEqualTo(1);
        var restored = json(perform(delete(archiveUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false)));
        assertThat(restored.get("archivedAt").isNull()).isTrue();
        assertThat(json(perform(get(projectUrl), null).andExpect(status().isOk()))).isEqualTo(restored);
        assertThat(json(perform(delete(archiveUrl), null).andExpect(status().isOk()))).isEqualTo(restored);
        assertThat(contentSnapshot()).isEqualTo(contents);
        assertThat(eventCount()).isEqualTo(2);
        var events = jdbc.queryForList("SELECT * FROM project_events WHERE project_id = ? ORDER BY occurred_at, id", projectId);
        assertThat(events).extracting(event -> event.get("event_type")).containsExactly("PROJECT_ARCHIVED", "PROJECT_RESTORED");
        assertThat(events).allSatisfy(event -> {
            assertThat(event).containsEntry("actor_id", owner.getId()).containsEntry("object_id", projectId).containsEntry("object_type", "PROJECT");
            assertThat(event.get("occurred_at")).isNotNull();
        });
    }

    @ParameterizedTest
    @EnumSource(BoxStatus.class)
    void allowsArchivingAtEveryStageWithUnfinishedTasks(BoxStatus boxStatus) throws Exception {
        jdbc.update("UPDATE boxes SET status = ? WHERE id = ?", boxStatus.name(), boxId);
        perform(put(archiveUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        assertThat(jdbc.queryForObject("SELECT status FROM boxes WHERE id = ?", String.class, boxId)).isEqualTo(boxStatus.name());
        assertThat(jdbc.queryForObject("SELECT completed_at FROM move_tasks WHERE id = ?", java.sql.Timestamp.class, taskId)).isNull();
    }

    @Test
    void archivedProjectRemainsReadableAcrossAllSections() throws Exception {
        perform(put(archiveUrl), null).andExpect(status().isOk());
        for (String url : List.of(projectUrl, "/api/move-projects", projectUrl + "/rooms", boxesUrl, boxesUrl + "/" + boxId,
                boxesUrl + "/summary", itemsUrl, itemsUrl + "/" + itemId, projectUrl + "/items?q=Зарядка",
                projectUrl + "/items/" + itemId + "/suggestions", tasksUrl, tasksUrl + "/" + taskId,
                "/api/projects/" + projectId + "/progress/tasks", "/api/projects/" + projectId + "/progress/boxes",
                tasksUrl + "/templates", checklistUrl, projectUrl + "/events")) {
            perform(get(url), null).andExpect(status().isOk());
        }
        perform(get(projectUrl + "/items").param("q", "Зарядка"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        perform(get(checklistUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].itemId").value(itemId.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"project-update", "project-delete", "room-create", "room-update", "room-delete",
            "box-create", "box-update", "box-delete", "box-status", "box-correct", "item-create", "item-batch", "item-batch-objects",
            "item-update", "item-delete", "item-move", "task-create", "task-update", "task-complete", "task-delete", "task-template",
            "checklist-create", "checklist-update", "checklist-delete", "checklist-link", "checklist-unlink"})
    void rejectsEveryMutationInArchiveAndAllowsItAfterRestore(String operation) throws Exception {
        if (operation.equals("box-correct")) jdbc.update("UPDATE boxes SET status = 'DELIVERED' WHERE id = ?", boxId);
        perform(put(archiveUrl), null).andExpect(status().isOk());
        var before = contentSnapshot();
        var project = jdbc.queryForMap("SELECT * FROM move_projects WHERE id = ?", projectId);
        var events = jdbc.queryForList("SELECT * FROM project_events WHERE project_id = ?", projectId);
        mutate(operation).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("архиве")));
        assertThat(contentSnapshot()).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT * FROM move_projects WHERE id = ?", projectId)).isEqualTo(project);
        assertThat(jdbc.queryForList("SELECT * FROM project_events WHERE project_id = ?", projectId)).isEqualTo(events);
        perform(delete(archiveUrl), null).andExpect(status().isOk());
        int expected = operation.endsWith("delete") ? 204
                : operation.endsWith("create") || operation.startsWith("item-batch") || operation.equals("task-template") ? 201 : 200;
        mutate(operation).andExpect(status().is(expected));
    }

    @Test
    void filtersInDatabaseBeforePaginationAndNeverListsForeignProjects() throws Exception {
        var ids = new TransactionTemplate(transactionManager).execute(status -> {
            var archived = project(entityManager.find(UserEntity.class, owner.getId()), "Ещё архивный");
            var active1 = project(entityManager.find(UserEntity.class, owner.getId()), "Активный 1");
            var active2 = project(entityManager.find(UserEntity.class, owner.getId()), "Активный 2");
            var foreign = project(entityManager.find(UserEntity.class, stranger.getId()), "Чужой архивный");
            foreign.setArchivedAt(Instant.now());
            return List.of(archived.getId(), active1.getId(), active2.getId());
        });
        var ordered = List.of(projectId, ids.get(0), ids.get(1), ids.get(2));
        for (int i = 0; i < ordered.size(); i++) jdbc.update("UPDATE move_projects SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-10-01T10:00:00Z").plusSeconds(i)), ordered.get(i));
        perform(put(archiveUrl), null).andExpect(status().isOk());
        perform(put("/api/move-projects/" + ids.get(0) + "/archive"), null).andExpect(status().isOk());
        perform(get("/api/move-projects"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        for (boolean archived : List.of(true, false)) {
            perform(get("/api/move-projects").param("archived", Boolean.toString(archived)).param("size", "1").param("page", "1"), null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.content[0].archived").value(archived))
                    .andExpect(jsonPath("$.content[0].id").value((archived ? projectId : ids.get(1)).toString()));
        }
        perform(get("/api/move-projects").param("archived", "true").param("size", "1").param("page", "2"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content").isEmpty());
        perform(get("/api/move-projects").param("archived", "invalid"), null).andExpect(status().isBadRequest());
    }

    @Test
    void archiveAndRestoreHideForeignAndMissingProjectsAndRequireAuthentication() throws Exception {
        for (boolean archive : List.of(true, false)) {
            var foreign = archive ? put(archiveUrl) : delete(archiveUrl);
            mockMvc.perform(foreign.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(stranger)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MOVE_PROJECT_NOT_FOUND"));
            String missing = "/api/move-projects/" + UUID.randomUUID() + "/archive";
            perform(archive ? put(missing) : delete(missing), null).andExpect(status().isNotFound());
            mockMvc.perform(archive ? put(archiveUrl) : delete(archiveUrl)).andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT archived_at FROM move_projects WHERE id = ?", java.sql.Timestamp.class, projectId)).isNull();
        assertThat(eventCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void archiveAndItemWriteAreSerializedInBothOrders(boolean archiveFirst) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var firstReady = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                if (archiveFirst) projectService.archiveMoveProject(projectId, owner);
                else {
                    var update = new UpdateItemRequest();
                    update.setName("Запись до архива");
                    itemService.update(owner.getId(), projectId, boxId, itemId, update);
                }
                firstReady.countDown();
                await(release);
            }));
            assertThat(firstReady.await(15, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> archiveFirst
                    ? perform(patch(itemsUrl + "/" + itemId), Map.of("name", "Запрещённая запись")).andExpect(status().isConflict())
                    : perform(put(archiveUrl), null).andExpect(status().isOk()));
            waitForProjectLock();
            assertThat(second.isDone()).isFalse();
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT name FROM items WHERE id = ?", String.class, itemId))
                    .isEqualTo(archiveFirst ? "Зарядка USB-C" : "Запись до архива");
            perform(get(projectUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
            perform(patch(itemsUrl + "/" + itemId), Map.of("lost", true)).andExpect(status().isConflict());
            assertThat(eventCount()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentArchiveOrRestoreRepeatsKeepOneEventAndTimestamp(boolean archive) throws Exception {
        if (!archive) perform(put(archiveUrl), null).andExpect(status().isOk());
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { await(start); return json(perform(archive ? put(archiveUrl) : delete(archiveUrl), null).andExpect(status().isOk())); });
            var second = executor.submit(() -> { await(start); return json(perform(archive ? put(archiveUrl) : delete(archiveUrl), null).andExpect(status().isOk())); });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
            assertThat(eventCount()).isEqualTo(archive ? 1 : 2);
        } finally { executor.shutdownNow(); }
    }

    private ResultActions mutate(String operation) throws Exception {
        return switch (operation) {
            case "project-update" -> perform(patch(projectUrl), Map.of("name", "Новое название"));
            case "project-delete" -> perform(delete(projectUrl), null);
            case "room-create" -> perform(post(projectUrl + "/rooms"), Map.of("name", "Новая комната"));
            case "room-update" -> perform(patch(projectUrl + "/rooms/" + roomId), Map.of("iconKey", "bed"));
            case "room-delete" -> perform(delete(projectUrl + "/rooms/" + emptyRoomId), null);
            case "box-create" -> perform(post(boxesUrl), Map.of("number", 3, "roomId", roomId));
            case "box-update" -> perform(patch(boxesUrl + "/" + boxId), Map.of("title", "Новая подпись"));
            case "box-delete" -> perform(delete(boxesUrl + "/" + targetBoxId), null);
            case "box-status" -> perform(patch(boxesUrl + "/" + boxId + "/status"), Map.of("status", "PACKED"));
            case "box-correct" -> perform(post(boxesUrl + "/" + boxId + "/status/correction"), Map.of("expectedStatus", "DELIVERED", "targetStatus", "PACKING", "confirmed", true));
            case "item-create" -> perform(post(itemsUrl), Map.of("name", "Новая вещь"));
            case "item-batch" -> perform(post(itemsUrl + "/batch"), Map.of("names", List.of("Книга", "Тетрадь")));
            case "item-batch-objects" -> perform(post(itemsUrl + "/batch"), Map.of("items", List.of(Map.of("name", "Тарелки", "quantity", 6))));
            case "item-update" -> perform(patch(itemsUrl + "/" + itemId), Map.of("name", "Новое имя", "quantity", 3, "firstDay", false));
            case "item-delete" -> perform(delete(itemsUrl + "/" + itemId), null);
            case "item-move" -> perform(put(itemsUrl + "/" + itemId + "/box"), Map.of("targetBoxId", targetBoxId));
            case "task-create" -> perform(post(tasksUrl), Map.of("title", "Новая задача", "stage", "BEFORE_MOVE"));
            case "task-update" -> perform(patch(tasksUrl + "/" + taskId), Map.of("title", "Новое название"));
            case "task-complete" -> perform(patch(tasksUrl + "/" + taskId + "/completion"), Map.of("completed", true));
            case "task-delete" -> perform(delete(tasksUrl + "/" + taskId), null);
            case "task-template" -> perform(post(tasksUrl + "/from-template"), Map.of("templateId", templateId));
            case "checklist-create" -> perform(post(checklistUrl), Map.of("name", "Документы"));
            case "checklist-update" -> perform(patch(checklistUrl + "/" + pointId), Map.of("name", "Новый пункт", "completed", true));
            case "checklist-delete" -> perform(delete(checklistUrl + "/" + pointId), null);
            case "checklist-link" -> perform(put(checklistUrl + "/" + pointId + "/item"), Map.of("itemId", itemId));
            case "checklist-unlink" -> perform(delete(checklistUrl + "/" + pointId + "/item"), null);
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private Map<String, Object> contentSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String table : List.of("rooms", "boxes", "items", "move_tasks", "first_day_checklist_items")) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return result;
    }

    private void waitForProjectLock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname = current_database()
                    AND wait_event_type = 'Lock' AND lower(query) LIKE '%from move_projects%' AND lower(query) LIKE '%for%update%')
                    """, Boolean.class))) return;
            Thread.sleep(10);
        }
        throw new AssertionError("The concurrent request did not wait on the project lock");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Transaction timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()); }
    private long eventCount() { return jdbc.queryForObject("SELECT count(*) FROM project_events WHERE project_id = ?", Long.class, projectId); }

    private ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }

    private UserEntity user() {
        var user = UserEntity.builder().email("archive-" + UUID.randomUUID()).password("hash").role(UserRole.USER).build();
        entityManager.persist(user);
        return user;
    }

    private MoveProjectEntity project(UserEntity user, String name) {
        var project = MoveProjectEntity.builder().owner(user).name(name).moveDate(Instant.now()).build();
        entityManager.persist(project);
        return project;
    }
}
