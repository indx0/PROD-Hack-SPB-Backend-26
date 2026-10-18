package ru.example.prodbackend.integration.item;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.item.dto.MoveItemRequest;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.support.BaseIntegrationTest;
import ru.example.prodbackend.support.LegacyChecklistTestSupport;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Targeted real-database checks; each HTTP request commits its own transaction.
class ItemMoveIntegrationTest extends BaseIntegrationTest {
    private static final Instant MARK = Instant.parse("2026-10-01T10:00:00Z");
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private ItemService itemService;
    @Autowired private BoxService boxService;
    private UUID ownerId;
    private UUID projectId;
    private UUID sourceId;
    private UUID targetId;
    private UUID thirdId;
    private UUID otherProjectBoxId;
    private UUID itemId;
    private UUID secondItemId;
    private UUID pointId;
    private String token;
    private String strangerToken;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UserEntity owner = user();
            ownerId = owner.getId();
            token = jwtService.generateToken(owner);
            strangerToken = jwtService.generateToken(user());
            MoveProjectEntity project = project(owner);
            projectId = project.getId();
            BoxEntity source = box(project, 1, "Кухня");
            BoxEntity target = box(project, 2, "Спальня");
            sourceId = source.getId();
            targetId = target.getId();
            thirdId = box(project, 3, "Гостиная").getId();
            otherProjectBoxId = box(project(owner), 1, "Другая квартира").getId();
            ItemEntity item = ItemEntity.builder().box(source).name("Зарядка USB-C").quantity(6).lost(true).firstDay(true)
                    .packedAt(MARK).unpackedAt(MARK.plusSeconds(1)).build();
            entityManager.persist(item);
            itemId = item.getId();
            ItemEntity second = ItemEntity.builder().box(target).name("Книга").build();
            entityManager.persist(second);
            secondItemId = second.getId();
            var point = FirstDayChecklistItemEntity.builder().project(project).name("Первый день").item(item).completedAt(MARK).build();
            entityManager.persist(point);
            pointId = point.getId();
            entityManager.flush();
        });
        LegacyChecklistTestSupport.snapshot(jdbc);
    }

    @Test
    void movesExistingRecordUpdatesInventoryAndCountersAndPreservesChecklistArchive() throws Exception {
        String createdAt = jsonMapper.readTree(perform(get(itemUrl(sourceId, itemId)), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("createdAt").asText();
        move(sourceId, itemId, targetId).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId.toString())).andExpect(jsonPath("$.name").value("Зарядка USB-C"))
                .andExpect(jsonPath("$.quantity").value(6)).andExpect(jsonPath("$.lost").value(true))
                .andExpect(jsonPath("$.firstDay").value(true)).andExpect(jsonPath("$.packedAt").value(MARK.toString()))
                .andExpect(jsonPath("$.unpackedAt").value(MARK.plusSeconds(1).toString()))
                .andExpect(jsonPath("$.createdAt").value(createdAt)).andExpect(jsonPath("$.boxId").value(targetId.toString()))
                .andExpect(jsonPath("$.roomName").value("Спальня"));
        assertThat(location()).isEqualTo(targetId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items WHERE id = ?", Long.class, itemId)).isEqualTo(1);
        perform(get(itemUrl(sourceId, itemId)), null).andExpect(status().isNotFound());
        perform(get(itemUrl(targetId, itemId)), null).andExpect(status().isOk());
        perform(get(itemsUrl(sourceId)), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        perform(get(itemsUrl(targetId)), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        perform(get("/api/move-projects/" + projectId + "/items").param("q", "зарядка"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(itemId.toString())).andExpect(jsonPath("$.content[0].boxId").value(targetId.toString()));
        assertChecklistSnapshot();
        perform(get(boxUrl(sourceId)), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.readyToPack").value(false)).andExpect(jsonPath("$.status").value("PACKING"));
        perform(get(boxUrl(targetId)), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.packedItems").value(1)).andExpect(jsonPath("$.unpackedItems").value(1))
                .andExpect(jsonPath("$.readyToPack").value(false)).andExpect(jsonPath("$.status").value("PACKING"));
        perform(patch(boxUrl(targetId) + "/status"), Map.of("status", "PACKED")).andExpect(status().isConflict());
        perform(patch(boxUrl(sourceId) + "/status"), Map.of("status", "PACKED")).andExpect(status().isConflict());
    }

    @Test
    void repeatsAndMovingToCurrentBoxKeepTimestampEvenAfterClosing() throws Exception {
        String initial = move(sourceId, itemId, targetId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String updatedAt = jsonMapper.readTree(initial).get("updatedAt").asText();
        jdbc.update("UPDATE boxes SET status = 'PACKED' WHERE id IN (?, ?)", sourceId, targetId);
        move(sourceId, itemId, targetId).andExpect(status().isOk()).andExpect(jsonPath("$.updatedAt").value(updatedAt));
        move(targetId, itemId, targetId).andExpect(status().isOk()).andExpect(jsonPath("$.updatedAt").value(updatedAt));
        move(sourceId, itemId, thirdId).andExpect(status().isConflict());
        assertThat(location()).isEqualTo(targetId);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM items WHERE id = ?", Timestamp.class, itemId).toInstant()).isEqualTo(Instant.parse(updatedAt));
    }

    @Test
    void rejectsInvalidInputForeignAccessAndClosedBoxesWithoutMovingItem() throws Exception {
        String url = itemUrl(sourceId, itemId) + "/box";
        for (String body : List.of("{}", "{\"targetBoxId\":null}")) {
            mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().is(422));
        }
        perform(put(url), Map.of("targetBoxId", "bad-uuid")).andExpect(status().isBadRequest());
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(Map.of("targetBoxId", targetId))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken).contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("targetBoxId", targetId)))).andExpect(status().isNotFound());
        move(sourceId, itemId, otherProjectBoxId).andExpect(status().isNotFound());
        move(sourceId, itemId, UUID.randomUUID()).andExpect(status().isNotFound());
        move(sourceId, UUID.randomUUID(), targetId).andExpect(status().isNotFound());
        jdbc.update("UPDATE boxes SET status = 'PACKED' WHERE id = ?", sourceId);
        move(sourceId, itemId, targetId).andExpect(status().isConflict());
        jdbc.update("UPDATE boxes SET status = 'PACKING' WHERE id = ?", sourceId);
        jdbc.update("UPDATE boxes SET status = 'DELIVERED' WHERE id = ?", targetId);
        move(sourceId, itemId, targetId).andExpect(status().isConflict());
        assertThat(location()).isEqualTo(sourceId);
        assertChecklistSnapshot();
    }

    @Test
    void oppositeTransfersUseOneLockOrderAndBothSucceed() throws Exception {
        var statuses = concurrently(() -> move(sourceId, itemId, targetId).andReturn().getResponse().getStatus(),
                () -> move(targetId, secondItemId, sourceId).andReturn().getResponse().getStatus());
        assertThat(statuses).containsOnly(200);
        assertThat(location()).isEqualTo(targetId);
        assertThat(jdbc.queryForObject("SELECT box_id FROM items WHERE id = ?", UUID.class, secondItemId)).isEqualTo(sourceId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items WHERE box_id IN (?, ?)", Long.class, sourceId, targetId)).isEqualTo(2);
        assertChecklistSnapshot();
    }

    @Test
    void competingTransfersCannotLoseDuplicateOrTakeItemFromThirdBox() throws Exception {
        var statuses = concurrently(() -> move(sourceId, itemId, targetId).andReturn().getResponse().getStatus(),
                () -> move(sourceId, itemId, thirdId).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(location()).isIn(targetId, thirdId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items WHERE id = ?", Long.class, itemId)).isEqualTo(1);
        assertChecklistSnapshot();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void transferAndClosingCannotLeaveClosedBoxWithUnpackedItem(boolean moveFirst) throws Exception {
        jdbc.update("UPDATE items SET packed_at = NULL WHERE id = ?", itemId);
        jdbc.update("UPDATE items SET packed_at = ? WHERE id = ?", Timestamp.from(MARK), secondItemId);
        var executor = Executors.newFixedThreadPool(2);
        var done = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                if (moveFirst) itemService.move(ownerId, projectId, sourceId, itemId, MoveItemRequest.builder().targetBoxId(targetId).build());
                else boxService.changeStatus(ownerId, projectId, targetId, BoxStatus.PACKED);
                done.countDown();
                await(release);
            }));
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                if (moveFirst) perform(patch(boxUrl(targetId) + "/status"), Map.of("status", "PACKED")).andExpect(status().isConflict());
                else move(sourceId, itemId, targetId).andExpect(status().isConflict());
                return null;
            });
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(location()).isEqualTo(moveFirst ? targetId : sourceId);
            perform(get(boxUrl(targetId)), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(moveFirst ? "PACKING" : "PACKED"))
                    .andExpect(jsonPath("$.totalItems").value(moveFirst ? 2 : 1)).andExpect(jsonPath("$.packedItems").value(1));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void taskLinkWaitsForProjectAndUsesCommittedTransferLocation() throws Exception {
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO move_tasks(id,project_id,title,stage,created_at,updated_at) VALUES (?,?,'Связанная задача','BEFORE_MOVE',now(),now())", taskId, projectId);
        var executor = Executors.newFixedThreadPool(2);
        var moved = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                itemService.move(ownerId, projectId, sourceId, itemId, MoveItemRequest.builder().targetBoxId(targetId).build());
                moved.countDown();
                await(release);
            }));
            assertThat(moved.await(10, TimeUnit.SECONDS)).isTrue();
            String linkUrl = "/api/move-projects/" + projectId + "/tasks/" + taskId;
            var second = executor.submit(() -> perform(patch(linkUrl), Map.of("linkedItemId", itemId)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.boxId").value(targetId.toString())));
            // The link waits on the project before reading the item's location.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean waiting = false;
            while (System.nanoTime() < deadline && !waiting) {
                waiting = jdbc.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname = current_database()
                        AND wait_event_type = 'Lock' AND lower(query) LIKE '%from move_projects%' AND lower(query) LIKE '%for%update%')
                        """, Boolean.class);
                if (!waiting) Thread.sleep(10);
            }
            assertThat(waiting).isTrue();
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            perform(patch(linkUrl), Map.of("linkedItemId", itemId)).andExpect(status().isOk()).andExpect(jsonPath("$.boxId").value(targetId.toString()));
            assertChecklistSnapshot();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private List<Integer> concurrently(Callable<Integer> one, Callable<Integer> two) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { await(start); return one.call(); });
            var second = executor.submit(() -> { await(start); return two.call(); });
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Transaction timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void assertChecklistSnapshot() throws Exception {
        perform(get("/api/move-projects/" + projectId + "/first-day-checklist"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(pointId.toString())).andExpect(jsonPath("$.content[0].itemId").value(itemId.toString()))
                .andExpect(jsonPath("$.content[0].boxId").value(sourceId.toString())).andExpect(jsonPath("$.content[0].name").value("Первый день"));
    }

    private UUID location() {
        return jdbc.queryForObject("SELECT box_id FROM items WHERE id = ?", UUID.class, itemId);
    }

    private ResultActions move(UUID source, UUID item, UUID target) throws Exception {
        return perform(put(itemUrl(source, item) + "/box"), Map.of("targetBoxId", target));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        return mockMvc.perform(request);
    }

    private String itemsUrl(UUID box) { return "/api/move-projects/" + projectId + "/boxes/" + box + "/items"; }
    private String itemUrl(UUID box, UUID item) { return itemsUrl(box) + "/" + item; }
    private String boxUrl(UUID box) { return "/api/projects/" + projectId + "/boxes/" + box; }

    private UserEntity user() {
        UserEntity user = UserEntity.builder().email("item-move-" + UUID.randomUUID()).password("hash").role(UserRole.USER).build();
        entityManager.persist(user);
        return user;
    }

    private MoveProjectEntity project(UserEntity owner) {
        var project = MoveProjectEntity.builder().owner(owner).name("Переезд").moveDate(Instant.parse("2026-11-01T09:00:00Z")).build();
        entityManager.persist(project);
        return project;
    }

    private BoxEntity box(MoveProjectEntity project, int number, String roomName) {
        var room = RoomEntity.builder().project(project).name(roomName).build();
        entityManager.persist(room);
        var box = BoxEntity.builder().project(project).room(room).number(number).build();
        entityManager.persist(box);
        return box;
    }
}
