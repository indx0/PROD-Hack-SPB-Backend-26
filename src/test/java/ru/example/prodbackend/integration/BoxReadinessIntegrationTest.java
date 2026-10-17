package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.item.dto.CreateItemRequest;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BoxReadinessIntegrationTest extends BaseIntegrationTest {
    private static final Instant MARK = Instant.parse("2026-10-01T10:00:00Z");
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BoxRepository boxRepository;
    @Autowired private BoxService boxService;
    @Autowired private ItemService itemService;
    private UUID ownerId;
    private UUID projectId;
    private UUID boxId;
    private String token;
    private String boxUrl;
    private String itemsUrl;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UserEntity owner = UserEntity.builder().email("readiness-" + UUID.randomUUID()).password("hash").role(UserRole.USER).build();
            entityManager.persist(owner);
            MoveProjectEntity project = MoveProjectEntity.builder().owner(owner).name("Переезд")
                    .moveDate(Instant.parse("2026-11-01T09:00:00Z")).build();
            entityManager.persist(project);
            RoomEntity room = RoomEntity.builder().project(project).name("Кухня").build();
            entityManager.persist(room);
            BoxEntity box = BoxEntity.builder().project(project).room(room).number(1).contentsDeferred(true).build();
            entityManager.persist(box);
            entityManager.flush();
            ownerId = owner.getId();
            projectId = project.getId();
            boxId = box.getId();
            token = jwtService.generateToken(owner);
        });
        boxUrl = "/api/projects/" + projectId + "/boxes/" + boxId;
        itemsUrl = "/api/move-projects/" + projectId + "/boxes/" + boxId + "/items";
    }

    @ParameterizedTest
    @CsvSource({"PACKING, PACKED", "DELIVERED, UNPACKED"})
    void emptyBoxCannotCloseEvenWithDeferredContents(BoxStatus current, BoxStatus next) throws Exception {
        setStatus(current);
        Instant updatedAt = boxRepository.findById(boxId).orElseThrow().getUpdatedAt();
        changeStatus(next).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("пустую коробку")));
        assertThat(boxRepository.findById(boxId).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(eventCount()).isZero();
        perform(get(boxUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(current.name()))
                .andExpect(jsonPath("$.totalItems").value(0)).andExpect(jsonPath("$.packedItems").value(0))
                .andExpect(jsonPath("$.unpackedItems").value(0)).andExpect(jsonPath("$.readyToPack").value(false))
                .andExpect(jsonPath("$.readyToUnpack").value(false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void checksLostUnmarkedItemBeyondFirstPageThenAllowsClosing(boolean packing) throws Exception {
        perform(post(itemsUrl + "/batch"), jsonMapper.writeValueAsString(Map.of("names",
                IntStream.range(0, 25).mapToObj(i -> "Вещь " + i).toList()))).andExpect(status().isCreated());
        jdbc.update("UPDATE items SET packed_at = ? WHERE box_id = ?", Timestamp.from(MARK), boxId);
        String column = packing ? "packed_at" : "unpacked_at";
        String flag = packing ? "packed" : "unpacked";
        String counter = packing ? "packedItems" : "unpackedItems";
        String ready = packing ? "readyToPack" : "readyToUnpack";
        if (!packing) jdbc.update("UPDATE items SET unpacked_at = ? WHERE box_id = ?", Timestamp.from(MARK), boxId);
        setStatus(packing ? BoxStatus.PACKING : BoxStatus.DELIVERED);
        UUID hidden = jdbc.queryForObject("SELECT id FROM items WHERE box_id = ? ORDER BY created_at, id OFFSET 20 LIMIT 1", UUID.class, boxId);
        jdbc.update("UPDATE items SET " + column + " = NULL, lost = true WHERE id = ?", hidden);
        perform(get(itemsUrl).param("size", "20"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20))).andExpect(jsonPath("$.content[*]." + flag, everyItem(is(true))));
        perform(get(boxUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$." + counter).value(24)).andExpect(jsonPath("$." + ready).value(false));
        BoxStatus next = packing ? BoxStatus.PACKED : BoxStatus.UNPACKED;
        changeStatus(next).andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("24 из 25")));
        assertThat(eventCount()).isZero();
        perform(patch(itemsUrl + "/" + hidden), jsonMapper.writeValueAsString(Map.of(flag, true))).andExpect(status().isOk());
        perform(get(boxUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$." + ready).value(true));
        changeStatus(next).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(25))
                .andExpect(jsonPath("$." + counter).value(25)).andExpect(jsonPath("$." + ready).value(false));
        Instant updatedAt = boxRepository.findById(boxId).orElseThrow().getUpdatedAt();
        changeStatus(next).andExpect(status().isOk());
        assertThat(boxRepository.findById(boxId).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void countersCountRecordsAndListMatchesCardIncludingEmptyBoxes() throws Exception {
        UUID first = createItem("Тарелки", 6);
        createItem("Кружки", 2);
        perform(patch(itemsUrl + "/" + first), "{\"packed\":true}").andExpect(status().isOk());
        String create = perform(post("/api/projects/" + projectId + "/boxes"), jsonMapper.writeValueAsString(Map.of(
                "number", 2, "roomId", boxRepository.findById(boxId).orElseThrow().getRoom().getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.readyToPack").value(false)).andReturn().getResponse().getContentAsString();
        UUID empty = UUID.fromString(jsonMapper.readTree(create).get("id").asText());
        for (String url : new String[]{boxUrl, "/api/projects/" + projectId + "/boxes?size=1"}) {
            String prefix = url.equals(boxUrl) ? "$" : "$.content[0]";
            perform(get(url), null).andExpect(status().isOk()).andExpect(jsonPath(prefix + ".totalItems").value(2))
                    .andExpect(jsonPath(prefix + ".packedItems").value(1)).andExpect(jsonPath(prefix + ".unpackedItems").value(0))
                    .andExpect(jsonPath(prefix + ".readyToPack").value(false)).andExpect(jsonPath(prefix + ".readyToUnpack").value(false));
        }
        perform(get("/api/projects/" + projectId + "/boxes").param("page", "1").param("size", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(empty.toString()))
                .andExpect(jsonPath("$.content[0].totalItems").value(0)).andExpect(jsonPath("$.content[0].readyToPack").value(false));
    }

    @ParameterizedTest
    @EnumSource(BoxStatus.class)
    void contentChangesAreAllowedOnlyWhilePacking(BoxStatus boxStatus) throws Exception {
        UUID item = createItem("Тарелки", 6);
        jdbc.update("UPDATE items SET packed_at = ? WHERE id = ?", Timestamp.from(MARK), item);
        setStatus(boxStatus);
        boolean packing = boxStatus == BoxStatus.PACKING;
        perform(post(itemsUrl), "{\"name\":\"Книга\"}").andExpect(status().is(packing ? 201 : 409));
        perform(post(itemsUrl + "/batch"), "{\"names\":[\"Книга\"]}").andExpect(status().is(packing ? 201 : 409));
        perform(post(itemsUrl + "/batch"), "{\"items\":[{\"name\":\"Книга\"}]}").andExpect(status().is(packing ? 201 : 409));
        perform(patch(itemsUrl + "/" + item), "{\"quantity\":7,\"name\":\"Changed\"}").andExpect(status().is(packing ? 200 : 409));
        perform(delete(itemsUrl + "/" + item), null).andExpect(status().is(packing ? 204 : 409));
        if (!packing) {
            perform(get(itemsUrl + "/" + item), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Тарелки")).andExpect(jsonPath("$.quantity").value(6))
                    .andExpect(jsonPath("$.packedAt").value(MARK.toString()));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM items WHERE box_id = ?", Long.class, boxId)).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @EnumSource(BoxStatus.class)
    void sameQuantityAndMetadataUpdatesPreserveMarksInEveryStatus(BoxStatus boxStatus) throws Exception {
        UUID item = createItem("Тарелки", 6);
        jdbc.update("UPDATE items SET packed_at = ? WHERE id = ?", Timestamp.from(MARK), item);
        setStatus(boxStatus);
        perform(patch(itemsUrl + "/" + item), "{\"quantity\":6,\"name\":\"Кружки\",\"lost\":true,\"firstDay\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.packedAt").value(MARK.toString()))
                .andExpect(jsonPath("$.name").value("Кружки")).andExpect(jsonPath("$.lost").value(true))
                .andExpect(jsonPath("$.firstDay").value(true));
    }

    @Test
    void changedQuantityClearsPackingMarkEvenWhenPackedIsSentTogether() throws Exception {
        UUID item = createItem("Тарелки", 6);
        perform(patch(itemsUrl + "/" + item), "{\"packed\":true}").andExpect(status().isOk());
        perform(get(boxUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.readyToPack").value(true));
        perform(patch(itemsUrl + "/" + item), "{\"quantity\":7,\"packed\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(7)).andExpect(jsonPath("$.packed").value(false))
                .andExpect(jsonPath("$.packedAt").isEmpty());
        perform(get(boxUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.packedItems").value(0))
                .andExpect(jsonPath("$.readyToPack").value(false));
        changeStatus(BoxStatus.PACKED).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @EnumSource(BoxStatus.class)
    void repeatingCurrentStatusSkipsReadinessChecks(BoxStatus boxStatus) throws Exception {
        setStatus(boxStatus); // Includes old closed boxes without item marks.
        Instant updatedAt = boxRepository.findById(boxId).orElseThrow().getUpdatedAt();
        changeStatus(boxStatus).andExpect(status().isOk());
        assertThat(boxRepository.findById(boxId).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(eventCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void addingAndClosingAreSerializedInBothOrders(boolean addFirst) throws Exception {
        UUID item = createItem("Тарелки", 6);
        perform(patch(itemsUrl + "/" + item), "{\"packed\":true}").andExpect(status().isOk());
        var executor = Executors.newFixedThreadPool(2);
        var firstDone = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                moveProjectRepository.findForUpdate(projectId, ownerId).orElseThrow();
                boxRepository.findForUpdate(boxId, projectId).orElseThrow();
                if (addFirst) itemService.create(ownerId, projectId, boxId, CreateItemRequest.builder().name("Новая вещь").build());
                else boxService.changeStatus(ownerId, projectId, boxId, BoxStatus.PACKED);
                firstDone.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("First transaction timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(firstDone.await(15, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                secondStarted.countDown();
                if (addFirst) changeStatus(BoxStatus.PACKED).andExpect(status().isConflict());
                else perform(post(itemsUrl), "{\"name\":\"Новая вещь\"}").andExpect(status().isConflict());
                return null;
            });
            assertThat(secondStarted.await(15, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            perform(get(boxUrl), null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(addFirst ? "PACKING" : "PACKED"))
                    .andExpect(jsonPath("$.totalItems").value(addFirst ? 2 : 1))
                    .andExpect(jsonPath("$.packedItems").value(1));
            assertThat(eventCount()).isEqualTo(addFirst ? 0 : 1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private UUID createItem(String name, int quantity) throws Exception {
        String body = perform(post(itemsUrl), jsonMapper.writeValueAsString(Map.of("name", name, "quantity", quantity)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(jsonMapper.readTree(body).get("id").asText());
    }

    private ResultActions changeStatus(BoxStatus next) throws Exception {
        return perform(patch(boxUrl + "/status"), jsonMapper.writeValueAsString(Map.of("status", next)));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private void setStatus(BoxStatus status) {
        jdbc.update("UPDATE boxes SET status = ? WHERE id = ?", status.name(), boxId);
    }

    private long eventCount() {
        return jdbc.queryForObject("SELECT count(*) FROM project_events WHERE object_id = ?", Long.class, boxId);
    }
}
