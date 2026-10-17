package ru.example.prodbackend.integration.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.support.AuthIntegrationTestSupport;
import tools.jackson.databind.JsonNode;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskFolderItemIntegrationTest extends AuthIntegrationTestSupport {
    private static final List<String> LINK_FIELDS = List.of("linkedItemId", "linkedItemName", "boxId", "boxNumber", "roomId", "roomName", "roomIconKey");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ProjectAccessService projectAccess;
    private String owner;
    private String member;
    private String outsider;
    private UUID project;
    private UUID otherProject;
    private UUID folder;
    private UUID secondFolder;
    private UUID foreignFolder;
    private UUID room;
    private UUID secondRoom;
    private UUID box;
    private UUID secondBox;
    private UUID item;
    private UUID secondItem;
    private UUID foreignItem;
    private String projectUrl;
    private String tasksUrl;

    @BeforeEach
    void prepare() throws Exception {
        owner = registerUser().getAccessToken();
        member = registerUser("member@example.com").getAccessToken();
        outsider = registerUser("outsider@example.com").getAccessToken();
        project = createProject(true);
        otherProject = createProject(false);
        projectUrl = "/api/move-projects/" + project;
        tasksUrl = projectUrl + "/tasks";
        folder = createFolder(project, "Первая");
        secondFolder = createFolder(project, "Вторая");
        foreignFolder = createFolder(otherProject, "Другой переезд того же владельца");
        room = createRoom(project, "Кухня", "kitchen");
        secondRoom = createRoom(project, "Спальня", "bed");
        box = createBox(project, room, 1);
        secondBox = createBox(project, secondRoom, 2);
        item = createItem(project, box, "Чайник", false);
        secondItem = createItem(project, secondBox, "Подушка", true);
        UUID foreignRoom = createRoom(otherProject, "Другая комната", "room");
        foreignItem = createItem(otherProject, createBox(otherProject, foreignRoom, 1), "Чужая вещь", false);
    }

    @Test
    void createReturnsFolderAndLiveItemProjectionWithoutChangingInventoryAndAllowsSeveralTasksForOneItem() throws Exception {
        var inventory = inventory();
        UUID task = createTask("Независимое название", "BEFORE_MOVE", folder, item);
        call(owner, get(tasksUrl + "/" + task), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").value(folder.toString())).andExpect(jsonPath("$.linkedItemId").value(item.toString()))
                .andExpect(jsonPath("$.title").value("Независимое название")).andExpect(jsonPath("$.linkedItemName").value("Чайник"))
                .andExpect(jsonPath("$.boxId").value(box.toString())).andExpect(jsonPath("$.boxNumber").value(1))
                .andExpect(jsonPath("$.roomId").value(room.toString())).andExpect(jsonPath("$.roomName").value("Кухня"))
                .andExpect(jsonPath("$.roomIconKey").value("kitchen"));
        createTask("Другая задача о той же вещи", "MOVING_DAY", secondFolder, item);
        assertThat(inventory()).isEqualTo(inventory);
        UUID plain = id(call(member, post(tasksUrl), Map.of("title", "Без связей", "stage", "AFTER_MOVE")).andExpect(status().isCreated()));
        var noLink = read(plain);
        assertNullLinks(noLink);
        assertThat(noLink.get("folderId").isNull()).isTrue();
        UUID explicitNull = id(call(owner, post(tasksUrl), "{\"title\":\"Явный null\",\"stage\":\"AFTER_MOVE\",\"folderId\":null,\"linkedItemId\":null}").andExpect(status().isCreated()));
        assertNullLinks(read(explicitNull));
        call(owner, get(projectUrl + "/task-folders/" + folder), null).andExpect(status().isOk()).andExpect(jsonPath("$.taskCount").value(1));
        call(member, get("/api/projects/" + project + "/progress/tasks"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalTasks").value(4));
    }

    @Test
    void movingTemplateAndSetTasksChangesOnlyFolderAndPreservesIdentityCompletionDeadlinesAndOrigins() throws Exception {
        var catalog = json(call(owner, get(tasksUrl + "/templates"), null).andExpect(status().isOk()));
        String template = catalog.get("content").get(0).get("id").asText();
        UUID assignee = userRepository.findByEmail("member@example.com").orElseThrow().getId();
        UUID single = id(call(owner, post(tasksUrl + "/from-template"), Map.of("templateId", template, "assigneeId", assignee)).andExpect(status().isCreated()));
        UUID set = id(call(owner, post("/api/task-sets"), Map.of("name", "Набор", "items", List.of(Map.of(
                "title", "Из набора", "stage", "AFTER_MOVE", "description", "Описание", "offsetDays", 3)))).andExpect(status().isCreated()));
        var application = json(call(owner, put(projectUrl + "/task-sets/" + set), null).andExpect(status().isOk()));
        UUID copied = UUID.fromString(application.get("tasks").get(0).get("id").asText());
        for (UUID task : List.of(single, copied)) {
            call(owner, patch(tasksUrl + "/" + task), Map.of("linkedItemId", item)).andExpect(status().isOk());
            call(owner, patch(tasksUrl + "/" + task + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
            var preserved = taskData(task);
            for (UUID target : List.of(folder, secondFolder)) {
                call(member, patch(tasksUrl + "/" + task), Map.of("folderId", target)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(task.toString())).andExpect(jsonPath("$.folderId").value(target.toString()));
                assertThat(taskData(task)).isEqualTo(preserved);
            }
            call(member, patch(tasksUrl + "/" + task), "{\"folderId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.folderId").isEmpty());
            assertThat(taskData(task)).isEqualTo(preserved);
        }
        assertThat(read(single).get("templateId").asText()).isEqualTo(template);
        assertThat(read(copied).get("taskSetApplicationId")).isEqualTo(application.get("id"));
        call(owner, get("/api/projects/" + project + "/progress/tasks"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(2)).andExpect(jsonPath("$.completedTasks").value(2));
    }

    @Test
    void patchDistinguishesOmissionFromNullAndClearsOrReplacesEachLinkIndependently() throws Exception {
        UUID task = createTask("Первая подпись", "BEFORE_MOVE", folder, item);
        call(member, patch(tasksUrl + "/" + task), Map.of("title", "Новая подпись")).andExpect(status().isOk());
        assertThat(read(task).get("folderId").asText()).isEqualTo(folder.toString());
        assertThat(read(task).get("linkedItemId").asText()).isEqualTo(item.toString());
        call(member, patch(tasksUrl + "/" + task), "{\"folderId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.linkedItemId").value(item.toString()));
        call(member, patch(tasksUrl + "/" + task), Map.of("folderId", secondFolder, "linkedItemId", secondItem)).andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedItemName").value("Подушка")).andExpect(jsonPath("$.folderId").value(secondFolder.toString()));
        call(member, patch(tasksUrl + "/" + task), "{\"linkedItemId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.folderId").value(secondFolder.toString()));
        var unlinked = read(task);
        assertNullLinks(unlinked);
        assertThat(unlinked.get("title").asText()).isEqualTo("Новая подпись");
        var beforeRepeat = jdbc.queryForMap("SELECT * FROM move_tasks WHERE id = ?", task);
        call(member, patch(tasksUrl + "/" + task), "{\"linkedItemId\":null}").andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT * FROM move_tasks WHERE id = ?", task)).isEqualTo(beforeRepeat);
    }

    @Test
    void invalidReferencesRollbackEveryFieldOfPatchAndNeverLeavePartialNewTasks() throws Exception {
        UUID task = createTask("Сохранить", "BEFORE_MOVE", folder, item);
        call(owner, patch(tasksUrl + "/" + task + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
        var before = jdbc.queryForMap("SELECT * FROM move_tasks WHERE id = ?", task);
        var inventory = inventory();
        long count = jdbc.queryForObject("SELECT count(*) FROM move_tasks", Long.class);
        for (String field : List.of("folderId", "linkedItemId")) {
            for (UUID unavailable : List.of(UUID.randomUUID(), field.equals("folderId") ? foreignFolder : foreignItem)) {
                var update = new HashMap<String, Object>();
                update.put("title", "Не сохранить");
                update.put("description", "Не сохранить");
                update.put("stage", "AFTER_MOVE");
                update.put("dueDate", "2026-12-01");
                update.put("assigneeName", "Другой");
                update.put("folderId", secondFolder);
                update.put("linkedItemId", secondItem);
                update.put(field, unavailable);
                String code = field.equals("folderId") ? "TASK_FOLDER_NOT_FOUND" : "NOT_FOUND";
                assertErrorResponse(call(member, patch(tasksUrl + "/" + task), update), 404, code);
                assertThat(jdbc.queryForMap("SELECT * FROM move_tasks WHERE id = ?", task)).isEqualTo(before);
                assertErrorResponse(call(owner, post(tasksUrl), update), 404, code);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM move_tasks", Long.class)).isEqualTo(count);
            }
        }
        assertThat(inventory()).isEqualTo(inventory);
        call(owner, patch(tasksUrl + "/" + task), Map.of("folderId", "invalid")).andExpect(status().isBadRequest());
        call(owner, post(tasksUrl), Map.of("title", "Не создать", "stage", "BEFORE_MOVE", "linkedItemId", "invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void folderFilterCombinesWithStageAndCompletionWhileOldListKeepsEveryTaskAndPaginationOrder() throws Exception {
        UUID first = createTask("Первая", "BEFORE_MOVE", folder, item);
        UUID second = createTask("Вторая", "BEFORE_MOVE", folder, item);
        createTask("После", "AFTER_MOVE", folder, secondItem);
        createTask("В другой папке", "BEFORE_MOVE", secondFolder, item);
        call(owner, post(tasksUrl), Map.of("title", "Без папки", "stage", "BEFORE_MOVE")).andExpect(status().isCreated());
        call(owner, patch(tasksUrl + "/" + first + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
        call(member, get(tasksUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5));
        call(member, get(tasksUrl).param("folderId", folder.toString()).param("stage", "BEFORE_MOVE").param("completed", "false"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(second.toString()));
        call(owner, get(tasksUrl).param("folderId", folder.toString()).param("stage", "BEFORE_MOVE").param("completed", "true"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(first.toString()));
        call(owner, get(tasksUrl).param("folderId", folder.toString()).param("size", "1").param("page", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].id").value(second.toString()));
        call(owner, get(tasksUrl).param("folderId", folder.toString()).param("page", "100"), null).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        UUID empty = createFolder(project, "Пустая");
        call(owner, get(tasksUrl).param("folderId", empty.toString()), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        for (UUID missing : List.of(UUID.randomUUID(), foreignFolder)) assertErrorResponse(call(owner, get(tasksUrl).param("folderId", missing.toString()), null), 404, "TASK_FOLDER_NOT_FOUND");
    }

    @Test
    void projectionTracksItemRenameBoxNumberItemTransferAndRoomChangesWithoutRenamingTask() throws Exception {
        UUID task = createTask("Моя подпись", "BEFORE_MOVE", folder, item);
        call(owner, patch(itemUrl(box, item)), Map.of("name", "Новый чайник")).andExpect(status().isOk());
        call(owner, put(itemUrl(box, item) + "/box"), Map.of("targetBoxId", secondBox)).andExpect(status().isOk());
        call(owner, patch("/api/projects/" + project + "/boxes/" + secondBox), Map.of("number", 7)).andExpect(status().isOk());
        call(owner, patch(projectUrl + "/rooms/" + secondRoom), Map.of("name", "Кабинет", "iconKey", "office")).andExpect(status().isOk());
        var moved = read(task);
        assertThat(moved.get("title").asText()).isEqualTo("Моя подпись");
        assertThat(moved.get("linkedItemName").asText()).isEqualTo("Новый чайник");
        assertThat(moved.get("boxId").asText()).isEqualTo(secondBox.toString());
        assertThat(moved.get("boxNumber").asInt()).isEqualTo(7);
        assertThat(moved.get("roomId").asText()).isEqualTo(secondRoom.toString());
        assertThat(moved.get("roomName").asText()).isEqualTo("Кабинет");
        assertThat(moved.get("roomIconKey").asText()).isEqualTo("office");
        call(owner, patch("/api/projects/" + project + "/boxes/" + secondBox), Map.of("roomId", room)).andExpect(status().isOk());
        call(member, get(tasksUrl).param("folderId", folder.toString()), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roomId").value(room.toString())).andExpect(jsonPath("$.content[0].roomName").value("Кухня"))
                .andExpect(jsonPath("$.content[0].roomIconKey").value("kitchen"));
    }

    @Test
    void deletingItemClearsOnlyLinkAndDeletingFolderKeepsLinkToOtherItem() throws Exception {
        UUID task = createTask("Сохранить задачу", "BEFORE_MOVE", folder, item);
        call(member, patch(tasksUrl + "/" + task + "/completion"), Map.of("completed", true)).andExpect(status().isOk());
        var before = jdbc.queryForMap("SELECT * FROM move_tasks WHERE id = ?", task);
        before.put("linked_item_id", null);
        call(owner, delete(itemUrl(box, item)), null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForMap("SELECT * FROM move_tasks WHERE id = ?", task)).isEqualTo(before);
        assertNullLinks(read(task));
        assertThat(read(task).get("completed").asBoolean()).isTrue();
        call(member, patch(tasksUrl + "/" + task), Map.of("linkedItemId", secondItem)).andExpect(status().isOk());
        var retained = taskData(task);
        call(member, delete(projectUrl + "/task-folders/" + folder), null).andExpect(status().isNoContent());
        assertThat(taskData(task)).isEqualTo(retained);
        assertThat(read(task).get("linkedItemId").asText()).isEqualTo(secondItem.toString());
        call(owner, delete("/api/projects/" + project + "/boxes/" + secondBox), null).andExpect(status().isNoContent());
        assertNullLinks(read(task));
        assertThat(read(task).get("title").asText()).isEqualTo("Сохранить задачу");
        assertThat(read(task).get("completed").asBoolean()).isTrue();
    }

    @Test
    void completionAndLinkingNeverChangePackingFirstDayOrBoxStatusAndFirstDayEditsKeepTaskLink() throws Exception {
        jdbc.update("UPDATE items SET packed_at = '2026-10-01T01:02:03.123456Z' WHERE id = ?", item);
        var before = inventory();
        UUID task = createTask("Выполнить отдельно", "BEFORE_MOVE", folder, item);
        for (boolean completed : List.of(true, true, false, false)) call(member, patch(tasksUrl + "/" + task + "/completion"), Map.of("completed", completed)).andExpect(status().isOk());
        call(owner, patch(tasksUrl + "/" + task), Map.of("linkedItemId", secondItem)).andExpect(status().isOk());
        call(owner, patch(tasksUrl + "/" + task), "{\"linkedItemId\":null}").andExpect(status().isOk());
        assertThat(inventory()).isEqualTo(before);
        call(owner, patch(tasksUrl + "/" + task), Map.of("linkedItemId", secondItem)).andExpect(status().isOk());
        call(owner, patch(itemUrl(secondBox, secondItem)), Map.of("firstDay", false)).andExpect(status().isOk());
        assertThat(read(task).get("linkedItemId").asText()).isEqualTo(secondItem.toString());
        call(owner, patch(itemUrl(secondBox, secondItem)), Map.of("firstDay", true)).andExpect(status().isOk());
        assertThat(read(task).get("linkedItemId").asText()).isEqualTo(secondItem.toString());
    }

    @Test
    void membersCanEditLinksArchiveAllowsReadsAndRejectsWritesAndStrangersCannotAccess() throws Exception {
        UUID task = createTask("Общая", "BEFORE_MOVE", folder, item);
        call(member, patch(tasksUrl + "/" + task), Map.of("folderId", secondFolder, "linkedItemId", secondItem)).andExpect(status().isOk());
        var before = read(task);
        assertErrorResponse(call(outsider, get(tasksUrl + "/" + task), null), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(call(outsider, patch(tasksUrl + "/" + task), Map.of("folderId", folder)), 404, "MOVE_PROJECT_NOT_FOUND");
        call(member, put(projectUrl + "/archive"), null).andExpect(status().isOk());
        assertThat(read(task)).isEqualTo(before);
        call(member, get(tasksUrl).param("folderId", secondFolder.toString()), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertErrorResponse(call(member, patch(tasksUrl + "/" + task), "{\"folderId\":null,\"linkedItemId\":null}"), 409, "DATA_CONFLICT");
        assertErrorResponse(call(member, post(tasksUrl), Map.of("title", "Не создать", "stage", "BEFORE_MOVE", "folderId", folder, "linkedItemId", item)), 409, "DATA_CONFLICT");
        assertThat(read(task)).isEqualTo(before);
    }

    @Test
    void itemDeletionAndLinkingSerializeWithoutPartialPatchOrDanglingLink() throws Exception {
        UUID task = createTask("Сохранить", "BEFORE_MOVE", folder, secondItem);
        var before = jdbc.queryForMap("SELECT * FROM move_tasks WHERE id = ?", task);
        UUID userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var deleting = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                projectAccess.getAccessibleProjectForUpdate(project, userId);
                jdbc.update("DELETE FROM items WHERE id = ?", item);
                locked.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var linking = pool.submit(() -> assertErrorResponse(call(member, patch(tasksUrl + "/" + task), Map.of("title", "Не сохранить", "linkedItemId", item)), 404, "NOT_FOUND"));
            assertThatThrownBy(() -> linking.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            deleting.get(10, TimeUnit.SECONDS);
            linking.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForMap("SELECT * FROM move_tasks WHERE id = ?", task)).isEqualTo(before);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void swaggerExposesNullableLinksLocationAndFolderFilterWithoutPresenceFlags() throws Exception {
        var api = json(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        var schemas = api.get("components").get("schemas");
        for (String type : List.of("CreateMoveTaskRequest", "UpdateMoveTaskRequest", "MoveTaskResponse")) {
            var properties = schemas.get(type).get("properties");
            for (String field : List.of("folderId", "linkedItemId")) {
                assertThat(properties.get(field).get("format").asText()).isEqualTo("uuid");
                assertThat(properties.get(field).get("type").toString()).contains("null");
            }
            assertThat(properties.has("folderIdPresent")).isFalse();
            assertThat(properties.has("linkedItemIdPresent")).isFalse();
        }
        assertThat(schemas.get("MoveTaskResponse").get("properties").propertyNames()).containsAll(LINK_FIELDS);
        var list = api.get("paths").get("/api/move-projects/{projectId}/tasks").get("get");
        assertThat(list.get("parameters").toString()).contains("folderId", "stage", "completed", "page", "size");
        assertThat(list.get("responses").get("404").get("description").asText()).contains("TASK_FOLDER_NOT_FOUND");
    }

    private UUID createProject(boolean participants) throws Exception {
        var body = new HashMap<String, Object>(Map.of("name", "Переезд", "moveDate", "2026-11-01T09:00:00Z", "includeFirstDayChecklist", false));
        if (participants) body.put("participants", List.of(Map.of("email", "member@example.com")));
        return id(call(owner, post("/api/move-projects"), body).andExpect(status().isCreated()));
    }
    private UUID createFolder(UUID project, String name) throws Exception { return id(call(owner, post("/api/move-projects/" + project + "/task-folders"), Map.of("id", UUID.randomUUID(), "name", name, "colorKey", "mint")).andExpect(status().isCreated())); }
    private UUID createRoom(UUID project, String name, String icon) throws Exception { return id(call(owner, post("/api/move-projects/" + project + "/rooms"), Map.of("name", name, "iconKey", icon)).andExpect(status().isCreated())); }
    private UUID createBox(UUID project, UUID room, int number) throws Exception { return id(call(owner, post("/api/projects/" + project + "/boxes"), Map.of("number", number, "roomId", room)).andExpect(status().isCreated())); }
    private UUID createItem(UUID project, UUID box, String name, boolean firstDay) throws Exception { return id(call(owner, post("/api/move-projects/" + project + "/boxes/" + box + "/items"), Map.of("name", name, "firstDay", firstDay)).andExpect(status().isCreated())); }
    private UUID createTask(String title, String stage, UUID folder, UUID item) throws Exception { return id(call(member, post(tasksUrl), Map.of("title", title, "stage", stage, "folderId", folder, "linkedItemId", item)).andExpect(status().isCreated())); }
    private String itemUrl(UUID box, UUID item) { return projectUrl + "/boxes/" + box + "/items/" + item; }
    private Map<String, Object> inventory() { return Map.of("items", jdbc.queryForList("SELECT * FROM items ORDER BY id"), "boxes", jdbc.queryForList("SELECT * FROM boxes ORDER BY id")); }
    private Map<String, Object> taskData(UUID task) {
        var result = jdbc.queryForMap("SELECT * FROM move_tasks WHERE id = ?", task);
        result.remove("folder_id");
        result.remove("updated_at");
        return result;
    }
    private void assertNullLinks(JsonNode task) { for (String field : LINK_FIELDS) assertThat(task.has(field) && task.get(field).isNull()).as(field).isTrue(); }
    private JsonNode read(UUID task) throws Exception { return json(call(owner, get(tasksUrl + "/" + task), null).andExpect(status().isOk())); }
    private UUID id(ResultActions result) throws Exception { return UUID.fromString(json(result).get("id").asText()); }
    private JsonNode json(ResultActions result) throws Exception { return jsonMapper.readTree(result.andReturn().getResponse().getContentAsByteArray()); }
    private ResultActions call(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String text ? text : jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }
}
