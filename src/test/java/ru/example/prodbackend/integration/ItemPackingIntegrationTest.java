package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No test transaction: requests commit independently, so reads and conflict checks see real database state.
class ItemPackingIntegrationTest extends BaseIntegrationTest {

    private static final Instant ORIGINAL_MARK = Instant.parse("2026-10-01T10:00:00Z");
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    private UUID projectId;
    private UUID boxId;
    private UUID itemId;
    private String token;
    private String strangerToken;
    private String collectionUrl;
    private String itemUrl;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UserEntity owner = createUser();
            strangerToken = jwtService.generateToken(createUser());
            token = jwtService.generateToken(owner);
            MoveProjectEntity project = MoveProjectEntity.builder().owner(owner).name("Переезд")
                    .moveDate(Instant.parse("2026-11-01T09:00:00Z")).build();
            entityManager.persist(project);
            RoomEntity room = RoomEntity.builder().project(project).name("Кухня").build();
            entityManager.persist(room);
            BoxEntity box = BoxEntity.builder().project(project).room(room).number(1).contentsDeferred(true).build();
            entityManager.persist(box);
            ItemEntity item = ItemEntity.builder().box(box).name("Тарелки").quantity(6).build();
            entityManager.persist(item);
            entityManager.flush();
            projectId = project.getId();
            boxId = box.getId();
            itemId = item.getId();
        });
        collectionUrl = "/api/move-projects/" + projectId + "/boxes/" + boxId + "/items";
        itemUrl = collectionUrl + "/" + itemId;
    }

    static Stream<Arguments> markChanges() {
        return Stream.of("packed", "unpacked").flatMap(mark -> Arrays.stream(BoxStatus.values())
                .flatMap(status -> Stream.of(false, true).flatMap(marked -> Stream.of(false, true)
                        .map(requested -> Arguments.of(mark, status, marked, requested)))));
    }

    @ParameterizedTest(name = "{0}, box={1}, current={2}, requested={3}")
    @MethodSource("markChanges")
    void enforcesStatusRulesAndAllowsNoOpRepeats(String mark, BoxStatus status, boolean marked, boolean requested) throws Exception {
        setStatus(status);
        String column = mark.equals("packed") ? "packed_at" : "unpacked_at";
        jdbc.update("UPDATE items SET " + column + " = ? WHERE id = ?", marked ? Timestamp.from(ORIGINAL_MARK) : null, itemId);
        Instant updatedAt = storedTime("updated_at");
        boolean allowed = marked == requested || status == (mark.equals("packed") ? BoxStatus.PACKING : BoxStatus.DELIVERED);
        Instant before = Instant.now();
        var result = perform(patch(itemUrl), jsonMapper.writeValueAsString(java.util.Map.of(mark, requested)))
                .andExpect(status().is(allowed ? 200 : 409));
        Instant after = Instant.now();
        if (!allowed) result.andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
        Instant saved = storedTime(column);
        boolean expected = allowed ? requested : marked;
        if (!expected) assertThat(saved).isNull();
        else if (marked) assertThat(saved).isEqualTo(ORIGINAL_MARK);
        else assertThat(saved).isBetween(before.minusMillis(1), after.plusMillis(1));
        if (marked == requested || !allowed) assertThat(storedTime("updated_at")).isEqualTo(updatedAt);
        perform(get(itemUrl), null).andExpect(status().isOk())
                .andExpect(jsonPath("$." + mark).value(expected))
                .andExpect(jsonPath("$.boxStatus").value(status.name()))
                .andExpect(jsonPath("$.quantity").value(6));
        assertThat(jdbc.queryForObject("SELECT status FROM boxes WHERE id = ?", String.class, boxId)).isEqualTo(status.name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"packed", "unpacked"})
    void repeatsPreserveExactServerTimestampAfterCommitAndClosingStage(String mark) throws Exception {
        setStatus(mark.equals("packed") ? BoxStatus.PACKING : BoxStatus.DELIVERED);
        String body = jsonMapper.writeValueAsString(java.util.Map.of(mark, true));
        String initial = perform(patch(itemUrl), body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String time = jsonMapper.readTree(initial).get(mark + "At").asText();
        assertThat(Instant.parse(time)).isEqualTo(storedTime(mark.equals("packed") ? "packed_at" : "unpacked_at"));
        perform(patch(itemUrl), body).andExpect(status().isOk()).andExpect(jsonPath("$." + mark + "At").value(time));
        setStatus(mark.equals("packed") ? BoxStatus.PACKED : BoxStatus.UNPACKED);
        perform(patch(itemUrl), body).andExpect(status().isOk()).andExpect(jsonPath("$." + mark + "At").value(time));
        perform(patch(itemUrl), jsonMapper.writeValueAsString(java.util.Map.of(mark, false))).andExpect(status().isConflict());
        perform(get(itemUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$." + mark + "At").value(time));
    }

    @Test
    void rejectedMixedPatchDoesNotPersistAnyFieldOrMark() throws Exception {
        perform(patch(itemUrl), """
                {"name":"Changed","lost":true,"quantity":7,"firstDay":true,"packed":true,"unpacked":true}
                """).andExpect(status().isConflict());
        perform(get(itemUrl), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Тарелки")).andExpect(jsonPath("$.quantity").value(6))
                .andExpect(jsonPath("$.lost").value(false)).andExpect(jsonPath("$.firstDay").value(false))
                .andExpect(jsonPath("$.packed").value(false)).andExpect(jsonPath("$.unpacked").value(false));
        assertThat(storedTime("packed_at")).isNull();
        assertThat(storedTime("unpacked_at")).isNull();
    }

    @Test
    void otherUpdatesPreserveMarksAndReadEndpointsReturnThem() throws Exception {
        jdbc.update("UPDATE items SET packed_at = ?, unpacked_at = ? WHERE id = ?",
                Timestamp.from(ORIGINAL_MARK), Timestamp.from(ORIGINAL_MARK), itemId);
        setStatus(BoxStatus.UNPACKED);
        perform(patch(itemUrl), """
                {"name":"Кружки","lost":true,"quantity":7,"firstDay":true}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.packedAt").value(ORIGINAL_MARK.toString()))
                .andExpect(jsonPath("$.unpackedAt").value(ORIGINAL_MARK.toString()));
        perform(get(collectionUrl), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].packed").value(true))
                .andExpect(jsonPath("$.content[0].packedAt").value(ORIGINAL_MARK.toString()))
                .andExpect(jsonPath("$.content[0].unpacked").value(true))
                .andExpect(jsonPath("$.content[0].unpackedAt").value(ORIGINAL_MARK.toString()));
        perform(get("/api/move-projects/" + projectId + "/items").param("q", "Кружки"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].packed").value(true))
                .andExpect(jsonPath("$.content[0].unpacked").value(true));
        assertThat(storedTime("packed_at")).isEqualTo(ORIGINAL_MARK);
        assertThat(storedTime("unpacked_at")).isEqualTo(ORIGINAL_MARK);
    }

    @Test
    void newItemsAndBothBatchFormatsStartWithoutMarks() throws Exception {
        perform(post(collectionUrl), "{\"name\":\"Книга\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.packed").value(false)).andExpect(jsonPath("$.unpacked").value(false))
                .andExpect(jsonPath("$.packedAt").isEmpty()).andExpect(jsonPath("$.unpackedAt").isEmpty());
        for (String body : new String[]{"{\"names\":[\"Кабель\"]}", "{\"items\":[{\"name\":\"Кабель\"}]}"}) {
            perform(post(collectionUrl + "/batch"), body).andExpect(status().isCreated())
                    .andExpect(jsonPath("$[0].packed").value(false)).andExpect(jsonPath("$[0].unpacked").value(false))
                    .andExpect(jsonPath("$[0].packedAt").isEmpty()).andExpect(jsonPath("$[0].unpackedAt").isEmpty());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items WHERE packed_at IS NOT NULL OR unpacked_at IS NOT NULL", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"packed", "unpacked"})
    void rejectsNullAndForeignMarkUpdates(String mark) throws Exception {
        perform(patch(itemUrl), "{\"" + mark + "\":null}").andExpect(status().isBadRequest());
        mockMvc.perform(patch(itemUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"" + mark + "\":true}"))
                .andExpect(status().isNotFound());
        assertThat(storedTime("packed_at")).isNull();
        assertThat(storedTime("unpacked_at")).isNull();
    }

    @Test
    void detailRejectsMissingItemAndWrongBoxOrProject() throws Exception {
        for (String url : new String[]{collectionUrl + "/" + UUID.randomUUID(),
                itemUrl.replace(boxId.toString(), UUID.randomUUID().toString()),
                itemUrl.replace(projectId.toString(), UUID.randomUUID().toString())}) {
            perform(get(url), null).andExpect(status().isNotFound());
        }
    }

    @Test
    void concurrentPackingRequestsReturnTheSameTimestamp() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<String> request = () -> {
                start.await();
                String body = perform(patch(itemUrl), "{\"packed\":true}").andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                return jsonMapper.readTree(body).get("packedAt").asText();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();
            String time = first.get(15, TimeUnit.SECONDS);
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(time);
            assertThat(storedTime("packed_at")).isEqualTo(Instant.parse(time));
        } finally {
            executor.shutdownNow();
        }
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private void setStatus(BoxStatus status) {
        jdbc.update("UPDATE boxes SET status = ? WHERE id = ?", status.name(), boxId);
    }

    private Instant storedTime(String column) {
        Timestamp time = jdbc.queryForObject("SELECT " + column + " FROM items WHERE id = ?", Timestamp.class, itemId);
        return time == null ? null : time.toInstant();
    }

    private UserEntity createUser() {
        UserEntity user = UserEntity.builder().email("packing-" + UUID.randomUUID()).password("test-hash").role(UserRole.USER).build();
        entityManager.persist(user);
        return user;
    }
}
