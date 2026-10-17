package ru.example.prodbackend.integration.box;

import ru.example.prodbackend.support.BaseIntegrationTest;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.auth.service.JwtService;
import ru.example.prodbackend.box.dto.CorrectBoxStatusRequest;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.sql.Timestamp;
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

// Each request commits its own business transaction; reads verify the actual PostgreSQL state.
class BoxStatusCorrectionIntegrationTest extends BaseIntegrationTest {
    private static final Instant MARK = Instant.parse("2026-10-01T10:00:00Z");
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BoxService boxService;
    private UUID ownerId;
    private UUID projectId;
    private UUID otherProjectId;
    private UUID boxId;
    private UUID itemId;
    private String token;
    private String strangerToken;
    private String boxUrl;
    private String correctionUrl;
    private String itemsUrl;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var owner = user();
            ownerId = owner.getId();
            token = jwtService.generateToken(owner);
            strangerToken = jwtService.generateToken(user());
            var project = project(owner);
            projectId = project.getId();
            otherProjectId = project(owner).getId();
            var room = RoomEntity.builder().project(project).name("Кухня").iconKey("kitchen").build();
            entityManager.persist(room);
            var box = BoxEntity.builder().project(project).room(room).number(3).status(BoxStatus.UNPACKED)
                    .title("Посуда").fragile(true).firstDay(true).carryWithMe(true).contentsDeferred(true).build();
            entityManager.persist(box);
            boxId = box.getId();
            var item = ItemEntity.builder().box(box).name("Тарелки").quantity(6).lost(true).firstDay(true)
                    .packedAt(MARK).unpackedAt(MARK.plusSeconds(1)).build();
            entityManager.persist(item);
            itemId = item.getId();
            entityManager.persist(ItemEntity.builder().box(box).name("Кружки").quantity(2)
                    .packedAt(MARK.plusSeconds(2)).unpackedAt(MARK.plusSeconds(3)).build());
            entityManager.persist(FirstDayChecklistItemEntity.builder().project(project).name("Первый день").item(item).completedAt(MARK.plusSeconds(5)).build());
        });
        boxUrl = "/api/projects/" + projectId + "/boxes/" + boxId;
        correctionUrl = boxUrl + "/status/correction";
        itemsUrl = "/api/move-projects/" + projectId + "/boxes/" + boxId + "/items";
    }

    @ParameterizedTest
    @CsvSource({"PACKED, PACKING", "LOADED, PACKING", "LOADED, PACKED", "DELIVERED, PACKING", "DELIVERED, PACKED",
            "DELIVERED, LOADED", "UNPACKED, PACKING", "UNPACKED, PACKED", "UNPACKED, LOADED", "UNPACKED, DELIVERED"})
    void correctsEveryBackwardPairPreservesDataAndRecordsStructuredAudit(BoxStatus source, BoxStatus target) throws Exception {
        setStatus(source);
        var items = jdbc.queryForList("SELECT id, name, quantity, lost, first_day, packed_at, created_at FROM items WHERE box_id = ? ORDER BY id", boxId);
        var marks = jdbc.queryForList("SELECT id, unpacked_at, updated_at FROM items WHERE box_id = ? ORDER BY id", boxId);
        var checklist = jdbc.queryForList("SELECT * FROM first_day_checklist_items WHERE project_id = ?", projectId);
        boolean keepUnpacked = target == BoxStatus.DELIVERED;
        correct(source, target).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(target.name()))
                .andExpect(jsonPath("$.packedItems").value(2)).andExpect(jsonPath("$.unpackedItems").value(keepUnpacked ? 2 : 0))
                .andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.readyToPack").value(target == BoxStatus.PACKING))
                .andExpect(jsonPath("$.readyToUnpack").value(keepUnpacked)).andExpect(jsonPath("$.title").value("Посуда"))
                .andExpect(jsonPath("$.fragile").value(true)).andExpect(jsonPath("$.firstDay").value(true))
                .andExpect(jsonPath("$.carryWithMe").value(true)).andExpect(jsonPath("$.contentsDeferred").value(true));
        assertThat(jdbc.queryForList("SELECT id, name, quantity, lost, first_day, packed_at, created_at FROM items WHERE box_id = ? ORDER BY id", boxId)).isEqualTo(items);
        assertThat(jdbc.queryForList("SELECT * FROM first_day_checklist_items WHERE project_id = ?", projectId)).isEqualTo(checklist);
        if (keepUnpacked) assertThat(jdbc.queryForList("SELECT id, unpacked_at, updated_at FROM items WHERE box_id = ? ORDER BY id", boxId)).isEqualTo(marks);
        else assertThat(jdbc.queryForObject("SELECT count(*) FROM items WHERE box_id = ? AND unpacked_at IS NOT NULL", Long.class, boxId)).isZero();
        assertThat(savedStatus()).isEqualTo(target.name());
        assertThat(eventCount()).isEqualTo(1);
        var event = jdbc.queryForMap("SELECT * FROM project_events WHERE project_id = ?", projectId);
        assertThat(event).containsEntry("event_type", "BOX_STATUS_CORRECTED").containsEntry("previous_status", source.name())
                .containsEntry("new_status", target.name()).containsEntry("actor_id", ownerId).containsEntry("object_id", boxId).containsEntry("object_type", "BOX");
        assertThat(event.get("occurred_at")).isNotNull();
        assertThat(event.get("description").toString()).contains(source.name(), target.name(), keepUnpacked ? "сохранены" : "сброшены");
        perform(get("/api/move-projects/" + projectId + "/events"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("BOX_STATUS_CORRECTED"))
                .andExpect(jsonPath("$.content[0].previousStatus").value(source.name())).andExpect(jsonPath("$.content[0].newStatus").value(target.name()))
                .andExpect(jsonPath("$.content[0].actorId").value(ownerId.toString())).andExpect(jsonPath("$.content[0].occurredAt").isNotEmpty());
    }

    @Test
    void repeatIgnoresStaleExpectedStatusWithoutChangingItemsTimeOrEvents() throws Exception {
        String first = correct(BoxStatus.UNPACKED, BoxStatus.PACKING).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var items = jdbc.queryForList("SELECT * FROM items WHERE box_id = ? ORDER BY id", boxId);
        var events = jdbc.queryForList("SELECT * FROM project_events WHERE project_id = ?", projectId);
        String repeat = correct(BoxStatus.UNPACKED, BoxStatus.PACKING).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(jsonMapper.readTree(repeat)).isEqualTo(jsonMapper.readTree(first));
        assertThat(jdbc.queryForList("SELECT * FROM items WHERE box_id = ? ORDER BY id", boxId)).isEqualTo(items);
        assertThat(jdbc.queryForList("SELECT * FROM project_events WHERE project_id = ?", projectId)).isEqualTo(events);
        perform(post(correctionUrl), Map.of("expectedStatus", "UNPACKED", "targetStatus", "PACKING", "confirmed", false)).andExpect(status().is(422));
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void deliveredCorrectionKeepsMarksAndAllowsUnmarkingBeforeNormalUnpacking() throws Exception {
        correct(BoxStatus.UNPACKED, BoxStatus.DELIVERED).andExpect(status().isOk());
        perform(patch(itemsUrl + "/" + itemId), Map.of("unpacked", false)).andExpect(status().isOk());
        perform(patch(boxUrl + "/status"), Map.of("status", "UNPACKED")).andExpect(status().isConflict());
        perform(patch(itemsUrl + "/" + itemId), Map.of("unpacked", true)).andExpect(status().isOk());
        perform(patch(boxUrl + "/status"), Map.of("status", "UNPACKED")).andExpect(status().isOk());
        assertThat(eventCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_events WHERE event_type = 'BOX_STATUS_CHANGED' AND previous_status IS NULL AND new_status IS NULL", Long.class)).isEqualTo(1);
    }

    @Test
    void packingCorrectionReopensContentEditingAndNormalClosingStillRequiresAllMarks() throws Exception {
        correct(BoxStatus.UNPACKED, BoxStatus.PACKING).andExpect(status().isOk());
        perform(post(itemsUrl), Map.of("name", "Новая вещь")).andExpect(status().isCreated());
        perform(patch(itemsUrl + "/" + itemId), Map.of("quantity", 7)).andExpect(status().isOk()).andExpect(jsonPath("$.packed").value(false));
        perform(patch(boxUrl + "/status"), Map.of("status", "PACKED")).andExpect(status().isConflict());
        assertThat(savedStatus()).isEqualTo("PACKING");
        assertThat(eventCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"PACKED, false", "LOADED, false", "PACKED, true", "LOADED, true"})
    void readinessFailurePreservesEverythingAndPackingRemainsAvailable(BoxStatus target, boolean empty) throws Exception {
        if (empty) jdbc.update("DELETE FROM items WHERE box_id = ?", boxId);
        else jdbc.update("UPDATE items SET packed_at = NULL WHERE id = ?", itemId);
        var before = jdbc.queryForList("SELECT * FROM items WHERE box_id = ? ORDER BY id", boxId);
        Timestamp updatedAt = boxUpdatedAt();
        correct(BoxStatus.UNPACKED, target).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
        assertThat(savedStatus()).isEqualTo("UNPACKED");
        assertThat(boxUpdatedAt()).isEqualTo(updatedAt);
        assertThat(jdbc.queryForList("SELECT * FROM items WHERE box_id = ? ORDER BY id", boxId)).isEqualTo(before);
        assertThat(eventCount()).isZero();
        correct(BoxStatus.UNPACKED, BoxStatus.PACKING).andExpect(status().isOk());
    }

    @Test
    void staleExpectedStatusAndForwardCorrectionsAreRejectedWithoutChanges() throws Exception {
        var before = jdbc.queryForList("SELECT * FROM items WHERE box_id = ? ORDER BY id", boxId);
        Timestamp updatedAt = boxUpdatedAt();
        correct(BoxStatus.DELIVERED, BoxStatus.PACKING).andExpect(status().isConflict());
        assertThat(savedStatus()).isEqualTo("UNPACKED");
        assertThat(boxUpdatedAt()).isEqualTo(updatedAt);
        setStatus(BoxStatus.PACKING);
        for (BoxStatus target : List.of(BoxStatus.PACKED, BoxStatus.LOADED, BoxStatus.DELIVERED, BoxStatus.UNPACKED)) {
            correct(BoxStatus.PACKING, target).andExpect(status().isConflict());
        }
        assertThat(savedStatus()).isEqualTo("PACKING");
        assertThat(jdbc.queryForList("SELECT * FROM items WHERE box_id = ? ORDER BY id", boxId)).isEqualTo(before);
        assertThat(eventCount()).isZero();
    }

    @Test
    void rejectsMissingConfirmationNullFieldsMalformedEnumsAndBody() throws Exception {
        for (String body : List.of("{}", "{\"expectedStatus\":\"UNPACKED\",\"targetStatus\":\"PACKING\"}",
                "{\"expectedStatus\":\"UNPACKED\",\"targetStatus\":\"PACKING\",\"confirmed\":null}",
                "{\"expectedStatus\":\"UNPACKED\",\"targetStatus\":\"PACKING\",\"confirmed\":false}",
                "{\"expectedStatus\":null,\"targetStatus\":\"PACKING\",\"confirmed\":true}",
                "{\"expectedStatus\":\"UNPACKED\",\"targetStatus\":null,\"confirmed\":true}")) {
            raw(post(correctionUrl), body).andExpect(status().is(422)).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        raw(post(correctionUrl), "{\"expectedStatus\":\"UNKNOWN\",\"targetStatus\":\"PACKING\",\"confirmed\":true}").andExpect(status().isBadRequest());
        raw(post(correctionUrl), "{").andExpect(status().isBadRequest());
        assertThat(savedStatus()).isEqualTo("UNPACKED");
        assertThat(eventCount()).isZero();
    }

    @Test
    void hidesForeignAndWrongProjectBoxesAndRequiresAuthentication() throws Exception {
        String body = "{\"expectedStatus\":\"UNPACKED\",\"targetStatus\":\"PACKING\",\"confirmed\":true}";
        mockMvc.perform(post(correctionUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        raw(post("/api/projects/" + otherProjectId + "/boxes/" + boxId + "/status/correction"), body).andExpect(status().isNotFound());
        raw(post("/api/projects/" + projectId + "/boxes/" + UUID.randomUUID() + "/status/correction"), body).andExpect(status().isNotFound());
        mockMvc.perform(post(correctionUrl).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        assertThat(savedStatus()).isEqualTo("UNPACKED");
        assertThat(eventCount()).isZero();
    }

    @Test
    void ordinaryEndpointStillRejectsEveryBackwardStep() throws Exception {
        for (BoxStatus target : List.of(BoxStatus.PACKING, BoxStatus.PACKED, BoxStatus.LOADED, BoxStatus.DELIVERED)) {
            perform(patch(boxUrl + "/status"), Map.of("status", target)).andExpect(status().isConflict());
        }
        assertThat(savedStatus()).isEqualTo("UNPACKED");
        assertThat(eventCount()).isZero();
    }

    @Test
    void concurrentRepeatsProduceOneCorrectionEvent() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var request = CorrectBoxStatusRequest.builder().expectedStatus(BoxStatus.UNPACKED).targetStatus(BoxStatus.PACKING).confirmed(true).build();
            var first = executor.submit(() -> { start.await(); return boxService.correctStatus(ownerId, projectId, boxId, request); });
            var second = executor.submit(() -> { start.await(); return boxService.correctStatus(ownerId, projectId, boxId, request); });
            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertThat(a.getStatus()).isEqualTo(BoxStatus.PACKING);
            assertThat(b.getStatus()).isEqualTo(BoxStatus.PACKING);
            assertThat(a.getUpdatedAt()).isEqualTo(b.getUpdatedAt());
            assertThat(eventCount()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    private ResultActions correct(BoxStatus expected, BoxStatus target) throws Exception {
        return perform(post(correctionUrl), Map.of("expectedStatus", expected, "targetStatus", target, "confirmed", true));
    }

    private void setStatus(BoxStatus status) { jdbc.update("UPDATE boxes SET status = ? WHERE id = ?", status.name(), boxId); }
    private String savedStatus() { return jdbc.queryForObject("SELECT status FROM boxes WHERE id = ?", String.class, boxId); }
    private Timestamp boxUpdatedAt() { return jdbc.queryForObject("SELECT updated_at FROM boxes WHERE id = ?", Timestamp.class, boxId); }
    private long eventCount() { return jdbc.queryForObject("SELECT count(*) FROM project_events WHERE project_id = ?", Long.class, projectId); }

    private ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return raw(request, body == null ? null : jsonMapper.writeValueAsString(body));
    }

    private ResultActions raw(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private UserEntity user() {
        var user = UserEntity.builder().email("correction-" + UUID.randomUUID()).password("hash").role(UserRole.USER).build();
        entityManager.persist(user);
        return user;
    }

    private MoveProjectEntity project(UserEntity owner) {
        var project = MoveProjectEntity.builder().owner(owner).name("Переезд").moveDate(Instant.now()).build();
        entityManager.persist(project);
        return project;
    }
}
