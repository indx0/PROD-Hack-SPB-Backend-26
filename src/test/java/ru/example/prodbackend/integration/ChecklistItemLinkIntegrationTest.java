package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.auth.service.JwtService;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.checklist.service.FirstDayChecklistService;
import ru.example.prodbackend.item.dto.UpdateItemRequest;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChecklistItemLinkIntegrationTest extends BaseIntegrationTest {
    private static final Instant COMPLETED = Instant.parse("2026-10-01T10:00:00Z");
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private BoxRepository boxRepository;
    @Autowired private ItemService itemService;
    @Autowired private FirstDayChecklistService checklistService;
    private UUID ownerId;
    private UUID projectId;
    private UUID roomId;
    private UUID boxId;
    private UUID itemId;
    private UUID secondItemId;
    private UUID otherProjectItemId;
    private UUID foreignItemId;
    private UUID pointId;
    private UUID secondPointId;
    private String token;
    private String foreignToken;
    private String collectionUrl;
    private String pointUrl;
    private String itemUrl;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UserEntity owner = createUser();
            UserEntity stranger = createUser();
            ownerId = owner.getId();
            token = jwtService.generateToken(owner);
            foreignToken = jwtService.generateToken(stranger);
            BoxEntity box = createBox(owner);
            projectId = box.getProject().getId();
            roomId = box.getRoom().getId();
            boxId = box.getId();
            itemId = createItem(box).getId();
            secondItemId = createItem(box).getId();
            otherProjectItemId = createItem(createBox(owner)).getId();
            foreignItemId = createItem(createBox(stranger)).getId();
            FirstDayChecklistItemEntity point = FirstDayChecklistItemEntity.builder().project(box.getProject())
                    .name("Зарядка для телефона").completedAt(COMPLETED).build();
            entityManager.persist(point);
            pointId = point.getId();
            FirstDayChecklistItemEntity second = FirstDayChecklistItemEntity.builder().project(box.getProject()).name("Запасная зарядка").build();
            entityManager.persist(second);
            secondPointId = second.getId();
            entityManager.flush();
        });
        collectionUrl = "/api/move-projects/" + projectId + "/first-day-checklist";
        pointUrl = collectionUrl + "/" + pointId;
        itemUrl = "/api/move-projects/" + projectId + "/boxes/" + boxId + "/items/" + itemId;
    }

    @Test
    void linksAndRepeatsWithoutDuplicatesOrChangingPointNameAndCompletion() throws Exception {
        link(pointId, itemId).andExpect(status().isOk()).andExpect(jsonPath("$.itemId").value(itemId.toString()))
                .andExpect(jsonPath("$.itemName").value("Зарядка USB-C"))
                .andExpect(jsonPath("$.boxId").value(boxId.toString())).andExpect(jsonPath("$.boxNumber").value(1))
                .andExpect(jsonPath("$.roomId").value(roomId.toString())).andExpect(jsonPath("$.roomName").value("Спальня"));
        var updatedAt = jdbc.queryForObject("SELECT updated_at FROM first_day_checklist_items WHERE id = ?", java.sql.Timestamp.class, pointId);
        for (int repeat = 0; repeat < 2; repeat++) link(pointId, itemId).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT updated_at FROM first_day_checklist_items WHERE id = ?", java.sql.Timestamp.class, pointId)).isEqualTo(updatedAt);
        assertThat(pointCount()).isEqualTo(2);
        assertThat(firstDay(itemId)).isTrue();
        assertThat(firstDay(secondItemId)).isFalse();
        assertPointPreserved(readPoint(pointId));
        assertThat(readPoint(pointId).get("itemId").asText()).isEqualTo(itemId.toString());
    }

    @Test
    void replacementKeepsOldFirstDayAndConflictPreservesPreviousLink() throws Exception {
        link(pointId, itemId).andExpect(status().isOk());
        link(pointId, secondItemId).andExpect(status().isOk()).andExpect(jsonPath("$.itemId").value(secondItemId.toString()));
        assertThat(firstDay(itemId)).isTrue();
        assertThat(firstDay(secondItemId)).isTrue();
        link(secondPointId, itemId).andExpect(status().isOk());
        link(pointId, itemId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
        assertThat(readPoint(pointId).get("itemId").asText()).isEqualTo(secondItemId.toString());
        assertThat(readPoint(secondPointId).get("itemId").asText()).isEqualTo(itemId.toString());
        assertThat(pointCount()).isEqualTo(2);
    }

    @Test
    void unlinkAndItsRepeatKeepPointCompletionAndFirstDay() throws Exception {
        link(pointId, itemId).andExpect(status().isOk());
        for (int repeat = 0; repeat < 2; repeat++) {
            String body = perform(delete(pointUrl + "/item"), null).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertUnlinked(jsonMapper.readTree(body));
        }
        assertPointPreserved(readPoint(pointId));
        assertThat(firstDay(itemId)).isTrue();
        assertThat(pointCount()).isEqualTo(2);
    }

    @Test
    void creatingWithOptionalItemLinksImmediatelyAndRejectsDuplicate() throws Exception {
        perform(post(collectionUrl), Map.of("name", "Отдельный пункт")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemId").isEmpty()).andExpect(jsonPath("$.boxNumber").isEmpty());
        perform(post(collectionUrl), Map.of("name", "Другая подпись", "itemId", itemId)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Другая подпись")).andExpect(jsonPath("$.itemId").value(itemId.toString()));
        assertThat(firstDay(itemId)).isTrue();
        perform(post(collectionUrl), Map.of("name", "Дубль", "itemId", itemId)).andExpect(status().isConflict());
        assertThat(pointCount()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "other-project", "foreign-user"})
    void rejectsUnavailableItemsForLinkAndCreation(String scenario) throws Exception {
        UUID target = switch (scenario) {
            case "other-project" -> otherProjectItemId;
            case "foreign-user" -> foreignItemId;
            default -> UUID.randomUUID();
        };
        link(pointId, target).andExpect(status().isNotFound());
        perform(post(collectionUrl), Map.of("name", "Не создавать", "itemId", target)).andExpect(status().isNotFound());
        assertUnlinked(readPoint(pointId));
        assertThat(pointCount()).isEqualTo(2);
        assertThat(firstDay(otherProjectItemId)).isFalse();
        assertThat(firstDay(foreignItemId)).isFalse();
    }

    @Test
    void itemRenameAndBoxChangesAreReadFromCurrentData() throws Exception {
        link(pointId, itemId).andExpect(status().isOk());
        perform(patch(itemUrl), Map.of("name", "Новая зарядка")).andExpect(status().isOk());
        perform(patch("/api/projects/" + projectId + "/boxes/" + boxId), Map.of("number", 9)).andExpect(status().isOk());
        jdbc.update("UPDATE rooms SET name = 'Кабинет' WHERE id = ?", roomId);
        JsonNode point = readPoint(pointId);
        assertThat(point.get("itemName").asText()).isEqualTo("Новая зарядка");
        assertThat(point.get("boxNumber").asInt()).isEqualTo(9);
        assertThat(point.get("roomName").asText()).isEqualTo("Кабинет");
        assertPointPreserved(point);
    }

    @Test
    void clearingFirstDayUnlinksWithoutDeletingPointAndTrueDoesNotCreateOne() throws Exception {
        link(pointId, itemId).andExpect(status().isOk());
        perform(patch(itemUrl), Map.of("firstDay", false)).andExpect(status().isOk());
        assertUnlinked(readPoint(pointId));
        assertPointPreserved(readPoint(pointId));
        assertThat(firstDay(itemId)).isFalse();
        perform(patch(itemUrl), Map.of("firstDay", true)).andExpect(status().isOk());
        assertUnlinked(readPoint(pointId));
        assertThat(firstDay(itemId)).isTrue();
        assertThat(pointCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"item", "box"})
    void deletingInventoryLeavesThePointAndClearsAllLinkFields(String target) throws Exception {
        link(pointId, itemId).andExpect(status().isOk());
        String url = target.equals("item") ? itemUrl : "/api/projects/" + projectId + "/boxes/" + boxId;
        perform(delete(url), null).andExpect(status().isNoContent());
        assertUnlinked(readPoint(pointId));
        assertPointPreserved(readPoint(pointId));
        assertThat(pointCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items WHERE id = ?", Long.class, itemId)).isZero();
    }

    @Test
    void deletingPointDoesNotDeleteItemOrClearItsFirstDay() throws Exception {
        link(pointId, itemId).andExpect(status().isOk());
        perform(delete(pointUrl), null).andExpect(status().isNoContent());
        assertThat(firstDay(itemId)).isTrue();
        assertThat(pointCount()).isEqualTo(1);
    }

    @Test
    void projectDeletionStillWorksWithLinkedPoints() throws Exception {
        link(pointId, itemId).andExpect(status().isOk());
        perform(delete("/api/move-projects/" + projectId), null).andExpect(status().isNoContent());
        assertThat(pointCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items WHERE id = ?", Long.class, otherProjectItemId)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(BoxStatus.class)
    void linkingIsAllowedInEveryBoxStatus(BoxStatus boxStatus) throws Exception {
        jdbc.update("UPDATE boxes SET status = ? WHERE id = ?", boxStatus.name(), boxId);
        link(pointId, itemId).andExpect(status().isOk());
        assertThat(firstDay(itemId)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"itemId\":null}"})
    void linkRequiresAnItemId(String body) throws Exception {
        mockMvc.perform(put(pointUrl + "/item").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(422));
        assertUnlinked(readPoint(pointId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void linkEndpointsEnforceAuthenticationOwnershipAndPointProject(String method) throws Exception {
        String body = jsonMapper.writeValueAsString(Map.of("itemId", itemId));
        mockMvc.perform(request(HttpMethod.valueOf(method), pointUrl + "/item").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(HttpMethod.valueOf(method), pointUrl + "/item").contentType(MediaType.APPLICATION_JSON).content(body)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken)).andExpect(status().isNotFound());
        perform(request(HttpMethod.valueOf(method), collectionUrl + "/" + UUID.randomUUID() + "/item"), Map.of("itemId", itemId))
                .andExpect(status().isNotFound());
        assertUnlinked(readPoint(pointId));
        assertThat(firstDay(itemId)).isFalse();
    }

    @Test
    void linkAndFirstDayRollBackTogether() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            checklistService.link(projectId, pointId, itemId, ownerId);
            status.setRollbackOnly();
        });
        assertUnlinked(readPoint(pointId));
        assertThat(firstDay(itemId)).isFalse();
    }

    @Test
    void twoPointsCannotConcurrentlyClaimTheSameItem() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return link(pointId, itemId).andReturn().getResponse().getStatus(); });
            var second = executor.submit(() -> { start.await(); return link(secondPointId, itemId).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_items WHERE item_id = ?", Long.class, itemId)).isEqualTo(1);
            assertThat(firstDay(itemId)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void linkAndClearingFirstDayAreSerializedInBothOrders(boolean linkFirst) throws Exception {
        race(linkFirst ? "link" : "clear", linkFirst ? "clear" : "link");
        JsonNode point = readPoint(pointId);
        if (linkFirst) assertUnlinked(point);
        else assertThat(point.get("itemId").asText()).isEqualTo(itemId.toString());
        assertThat(firstDay(itemId)).isEqualTo(!linkFirst);
        assertPointPreserved(point);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void linkAndDeletionAreSerializedInBothOrders(boolean linkFirst) throws Exception {
        race(linkFirst ? "link" : "delete", linkFirst ? "delete" : "link");
        assertUnlinked(readPoint(pointId));
        assertPointPreserved(readPoint(pointId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items WHERE id = ?", Long.class, itemId)).isZero();
    }

    private void race(String firstAction, String secondAction) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var firstDone = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                moveProjectRepository.findForUpdate(projectId, ownerId).orElseThrow();
                boxRepository.findForUpdate(boxId, projectId).orElseThrow();
                switch (firstAction) {
                    case "link" -> checklistService.link(projectId, pointId, itemId, ownerId);
                    case "delete" -> itemService.delete(ownerId, projectId, boxId, itemId);
                    case "clear" -> {
                        var update = new UpdateItemRequest();
                        update.setFirstDay(false);
                        itemService.update(ownerId, projectId, boxId, itemId, update);
                    }
                }
                firstDone.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Transaction timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(firstDone.await(15, TimeUnit.SECONDS)).isTrue();
            var started = new CountDownLatch(1);
            var second = executor.submit(() -> {
                started.countDown();
                switch (secondAction) {
                    case "link" -> link(pointId, itemId).andExpect(status().is(firstAction.equals("delete") ? 404 : 200));
                    case "delete" -> perform(delete(itemUrl), null).andExpect(status().isNoContent());
                    case "clear" -> perform(patch(itemUrl), Map.of("firstDay", false)).andExpect(status().isOk());
                }
                return null;
            });
            assertThat(started.await(15, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private ResultActions link(UUID point, UUID item) throws Exception {
        return perform(put(collectionUrl + "/" + point + "/item"), Map.of("itemId", item));
    }

    private JsonNode readPoint(UUID id) throws Exception {
        String body = perform(get(collectionUrl).param("size", "100"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode point : jsonMapper.readTree(body).get("content")) {
            if (point.get("id").asText().equals(id.toString())) return point;
        }
        throw new AssertionError("Checklist point not found: " + id);
    }

    private void assertPointPreserved(JsonNode point) {
        assertThat(point.get("name").asText()).isEqualTo("Зарядка для телефона");
        assertThat(point.get("completed").asBoolean()).isTrue();
        assertThat(Instant.parse(point.get("completedAt").asText())).isEqualTo(COMPLETED);
    }

    private void assertUnlinked(JsonNode point) {
        for (String field : List.of("itemId", "itemName", "boxId", "boxNumber", "roomId", "roomName")) {
            assertThat(point.has(field)).as(field + " exists").isTrue();
            assertThat(point.get(field).isNull()).as(field + " is null").isTrue();
        }
    }

    private boolean firstDay(UUID id) {
        return jdbc.queryForObject("SELECT first_day FROM items WHERE id = ?", Boolean.class, id);
    }

    private long pointCount() {
        return jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_items WHERE project_id = ?", Long.class, projectId);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }

    private UserEntity createUser() {
        UserEntity user = UserEntity.builder().email("checklist-link-" + UUID.randomUUID()).password("hash").role(UserRole.USER).build();
        entityManager.persist(user);
        return user;
    }

    private BoxEntity createBox(UserEntity owner) {
        MoveProjectEntity project = MoveProjectEntity.builder().owner(owner).name("Переезд").moveDate(Instant.parse("2026-11-01T09:00:00Z")).build();
        entityManager.persist(project);
        RoomEntity room = RoomEntity.builder().project(project).name("Спальня").build();
        entityManager.persist(room);
        BoxEntity box = BoxEntity.builder().project(project).room(room).number(1).build();
        entityManager.persist(box);
        return box;
    }

    private ItemEntity createItem(BoxEntity box) {
        ItemEntity item = ItemEntity.builder().box(box).name("Зарядка USB-C").quantity(2).build();
        entityManager.persist(item);
        return item;
    }
}
