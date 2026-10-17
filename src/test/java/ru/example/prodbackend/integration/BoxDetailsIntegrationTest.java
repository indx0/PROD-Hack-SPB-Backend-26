package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No test transaction: reads verify data committed by preceding HTTP requests.
class BoxDetailsIntegrationTest extends BaseIntegrationTest {
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    private UUID projectId;
    private UUID roomId;
    private UUID secondRoomId;
    private UUID boxId;
    private UUID linkedItemId;
    private UUID ordinaryItemId;
    private UUID pointId;
    private String token;
    private String baseUrl;
    private String boxUrl;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var owner = UserEntity.builder().email("box-details-" + UUID.randomUUID()).password("hash").role(UserRole.USER).build();
            entityManager.persist(owner);
            token = jwtService.generateToken(owner);
            var project = MoveProjectEntity.builder().owner(owner).name("Переезд").moveDate(Instant.now()).build();
            entityManager.persist(project);
            projectId = project.getId();
            var room = RoomEntity.builder().project(project).name("Кухня").build();
            var secondRoom = RoomEntity.builder().project(project).name("Спальня").build();
            entityManager.persist(room);
            entityManager.persist(secondRoom);
            roomId = room.getId();
            secondRoomId = secondRoom.getId();
            var box = BoxEntity.builder().project(project).room(room).number(1).build();
            entityManager.persist(box);
            boxId = box.getId();
            var linked = ItemEntity.builder().box(box).name("Зарядка").firstDay(true).packedAt(Instant.now()).unpackedAt(Instant.now()).build();
            var ordinary = ItemEntity.builder().box(box).name("Тарелки").packedAt(Instant.now()).unpackedAt(Instant.now()).build();
            entityManager.persist(linked);
            entityManager.persist(ordinary);
            linkedItemId = linked.getId();
            ordinaryItemId = ordinary.getId();
            var point = FirstDayChecklistItemEntity.builder().project(project).name("Взять зарядку").item(linked).build();
            entityManager.persist(point);
            pointId = point.getId();
        });
        baseUrl = "/api/projects/" + projectId + "/boxes";
        boxUrl = baseUrl + "/" + boxId;
    }

    @Test
    void createsDefaultsAndCustomDetailsAndReadsCommittedValues() throws Exception {
        var defaults = perform(post(baseUrl), Map.of("number", 2, "roomId", roomId)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("")).andExpect(jsonPath("$.fragile").value(false))
                .andExpect(jsonPath("$.firstDay").value(false)).andExpect(jsonPath("$.carryWithMe").value(false));
        String defaultId = id(defaults);
        assertThat(jdbc.queryForObject("SELECT title FROM boxes WHERE id = ?", String.class, UUID.fromString(defaultId))).isEmpty();
        for (int number : List.of(3, 4)) {
            var created = perform(post(baseUrl), Map.of("number", number, "roomId", roomId, "title", "  Посуда для кухни  ",
                    "fragile", true, "firstDay", true, "carryWithMe", true)).andExpect(status().isCreated());
            String url = baseUrl + "/" + id(created);
            assertDetails(perform(get(url), null), "Посуда для кухни", true, true, true);
        }
        perform(get(baseUrl), null).andExpect(status().isOk()).andExpect(jsonPath("$.content[2].title").value("Посуда для кухни"))
                .andExpect(jsonPath("$.content[2].fragile").value(true)).andExpect(jsonPath("$.content[2].firstDay").value(true))
                .andExpect(jsonPath("$.content[2].carryWithMe").value(true));
    }

    @Test
    void patchesEachFieldSeparatelyAndClearsFlagsAndTitle() throws Exception {
        perform(patch(boxUrl), Map.of("title", "  Хрупкая посуда  ")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Хрупкая посуда"));
        for (String flag : List.of("fragile", "firstDay", "carryWithMe")) {
            perform(patch(boxUrl), Map.of(flag, true)).andExpect(status().isOk());
        }
        assertDetails(perform(get(boxUrl), null), "Хрупкая посуда", true, true, true);
        perform(patch(boxUrl), Map.of("fragile", false)).andExpect(status().isOk());
        assertDetails(perform(get(boxUrl), null), "Хрупкая посуда", false, true, true);
        perform(patch(boxUrl), Map.of("firstDay", false)).andExpect(status().isOk());
        assertDetails(perform(get(boxUrl), null), "Хрупкая посуда", false, false, true);
        perform(patch(boxUrl), Map.of("carryWithMe", false)).andExpect(status().isOk());
        perform(patch(boxUrl), Map.of("title", "")).andExpect(status().isOk());
        assertDetails(perform(get(boxUrl), null), "", false, false, false);
        perform(patch(boxUrl), Map.of("title", "  \t  ")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value(""));
    }

    @Test
    void preservesDetailsWhenOtherMetadataAndStatusesChangeAndAllowsEditingAtEveryStage() throws Exception {
        perform(patch(boxUrl), Map.of("title", "Посуда", "fragile", true, "firstDay", true, "carryWithMe", true)).andExpect(status().isOk());
        assertDetails(perform(patch(boxUrl), Map.of("number", 9, "roomId", secondRoomId, "contentsDeferred", true)), "Посуда", true, true, true)
                .andExpect(jsonPath("$.number").value(9)).andExpect(jsonPath("$.roomId").value(secondRoomId.toString()));
        String previousTitle = "Посуда";
        for (String next : List.of("PACKING", "PACKED", "LOADED", "DELIVERED", "UNPACKED")) {
            assertDetails(perform(patch(boxUrl + "/status"), Map.of("status", next)), previousTitle, true, true, true);
            assertDetails(perform(patch(boxUrl), Map.of("title", "Этап " + next, "fragile", false, "firstDay", false, "carryWithMe", false)),
                    "Этап " + next, false, false, false).andExpect(jsonPath("$.status").value(next));
            perform(patch(boxUrl), Map.of("fragile", true, "firstDay", true, "carryWithMe", true)).andExpect(status().isOk());
            previousTitle = "Этап " + next;
        }
        assertDetails(perform(get(boxUrl), null), "Этап UNPACKED", true, true, true);
    }

    @Test
    void filtersTrueFalseAndCombinationsAndCountsPagesWithinProject() throws Exception {
        create(2, true, true, false);
        create(3, true, false, true);
        create(4, false, true, true);
        create(5, true, true, true);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var original = entityManager.find(MoveProjectEntity.class, projectId);
            var other = MoveProjectEntity.builder().owner(original.getOwner()).name("Другой переезд").moveDate(Instant.now()).build();
            entityManager.persist(other);
            var room = RoomEntity.builder().project(other).name("Другая кухня").build();
            entityManager.persist(room);
            entityManager.persist(BoxEntity.builder().project(other).room(room).number(6).fragile(true).firstDay(true).carryWithMe(true).build());
        });
        assertNumbers("fragile=true", 2, 3, 5);
        assertNumbers("fragile=false", 1, 4);
        assertNumbers("firstDay=true", 2, 4, 5);
        assertNumbers("firstDay=false", 1, 3);
        assertNumbers("carryWithMe=true", 3, 4, 5);
        assertNumbers("carryWithMe=false", 1, 2);
        assertNumbers("fragile=true&firstDay=true&carryWithMe=true", 5);
        assertNumbers("fragile=true&firstDay=false&carryWithMe=true", 3);
        assertNumbers("fragile=false&firstDay=false&carryWithMe=false", 1);
        assertNumbers("number=2&roomId=" + roomId + "&status=PACKING&contentsDeferred=false&fragile=true", 2);
        perform(get(baseUrl + "?fragile=true&page=1&size=1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(3)).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3));
        perform(get(baseUrl + "?fragile=true&page=3&size=1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void rejectsExplicitNullOnCreateAndPatchWithoutChangingStoredData() throws Exception {
        for (String field : List.of("title", "fragile", "firstDay", "carryWithMe")) {
            raw(post(baseUrl), "{\"number\":2,\"roomId\":\"" + roomId + "\",\"" + field + "\":null}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
            raw(patch(boxUrl), "{\"number\":9,\"" + field + "\":null}").andExpect(status().isBadRequest());
        }
        assertDetails(perform(get(boxUrl), null), "", false, false, false).andExpect(jsonPath("$.number").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM boxes WHERE project_id = ?", Long.class, projectId)).isEqualTo(1);
    }

    @Test
    void enforcesTitleLengthOnCreateAndPatch() throws Exception {
        perform(post(baseUrl), Map.of("number", 2, "roomId", roomId, "title", "я".repeat(255))).andExpect(status().isCreated());
        perform(patch(boxUrl), Map.of("title", "я".repeat(255))).andExpect(status().isOk());
        perform(post(baseUrl), Map.of("number", 3, "roomId", roomId, "title", "я".repeat(256))).andExpect(status().is(422));
        perform(patch(boxUrl), Map.of("title", "я".repeat(256), "fragile", true)).andExpect(status().is(422));
        assertDetails(perform(get(boxUrl), null), "я".repeat(255), false, false, false);
    }

    @Test
    void boxFirstDayNeverChangesItemsOrChecklistLinks() throws Exception {
        for (boolean firstDay : List.of(true, false, true)) {
            perform(patch(boxUrl), Map.of("firstDay", firstDay)).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT first_day FROM items WHERE id = ?", Boolean.class, linkedItemId)).isTrue();
            assertThat(jdbc.queryForObject("SELECT first_day FROM items WHERE id = ?", Boolean.class, ordinaryItemId)).isFalse();
            assertThat(jdbc.queryForObject("SELECT item_id FROM first_day_checklist_items WHERE id = ?", UUID.class, pointId)).isEqualTo(linkedItemId);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_items WHERE project_id = ?", Long.class, projectId)).isEqualTo(1);
        }
    }

    private void create(int number, boolean fragile, boolean firstDay, boolean carryWithMe) throws Exception {
        perform(post(baseUrl), Map.of("number", number, "roomId", roomId, "fragile", fragile, "firstDay", firstDay, "carryWithMe", carryWithMe))
                .andExpect(status().isCreated());
    }

    private void assertNumbers(String query, Integer... numbers) throws Exception {
        var result = perform(get(baseUrl + "?" + query), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(numbers.length));
        List<Integer> actual = new ArrayList<>();
        for (var box : jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("content")) actual.add(box.get("number").asInt());
        assertThat(actual).containsExactly(numbers);
    }

    private ResultActions assertDetails(ResultActions result, String title, boolean fragile, boolean firstDay, boolean carryWithMe) throws Exception {
        return result.andExpect(status().isOk()).andExpect(jsonPath("$.title").value(title)).andExpect(jsonPath("$.fragile").value(fragile))
                .andExpect(jsonPath("$.firstDay").value(firstDay)).andExpect(jsonPath("$.carryWithMe").value(carryWithMe));
    }

    private String id(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return raw(request, body == null ? null : jsonMapper.writeValueAsString(body));
    }

    private ResultActions raw(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }
}
