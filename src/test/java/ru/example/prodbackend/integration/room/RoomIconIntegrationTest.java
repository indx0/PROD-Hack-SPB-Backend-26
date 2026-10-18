package ru.example.prodbackend.integration.room;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import ru.example.prodbackend.room.dto.request.UpdateRoomRequest;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.room.service.RoomService;
import ru.example.prodbackend.support.BaseIntegrationTest;
import ru.example.prodbackend.support.LegacyChecklistTestSupport;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Requests run in separate committed transactions, so subsequent reads see persisted values.
class RoomIconIntegrationTest extends BaseIntegrationTest {
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RoomService roomService;
    private UUID ownerId;
    private UUID projectId;
    private UUID otherProjectId;
    private UUID roomId;
    private UUID secondRoomId;
    private UUID boxId;
    private UUID itemId;
    private String token;
    private String strangerToken;
    private String projectUrl;
    private String roomsUrl;
    private String roomUrl;
    private String boxesUrl;

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
            var room = RoomEntity.builder().project(project).name("Мастерская").iconKey("kitchen").build();
            var secondRoom = RoomEntity.builder().project(project).name("Вторая комната").iconKey("kitchen").build();
            entityManager.persist(room);
            entityManager.persist(secondRoom);
            roomId = room.getId();
            secondRoomId = secondRoom.getId();
            var box = BoxEntity.builder().project(project).room(room).number(1).build();
            entityManager.persist(box);
            boxId = box.getId();
            var item = ItemEntity.builder().box(box).name("Зарядка USB-C").firstDay(true).lost(true).build();
            entityManager.persist(item);
            itemId = item.getId();
            entityManager.persist(FirstDayChecklistItemEntity.builder().project(project).name("Зарядка первого дня").item(item).build());
            entityManager.persist(FirstDayChecklistItemEntity.builder().project(project).name("Документы с собой").build());
        });
        projectUrl = "/api/move-projects/" + projectId;
        roomsUrl = projectUrl + "/rooms";
        roomUrl = roomsUrl + "/" + roomId;
        boxesUrl = "/api/projects/" + projectId + "/boxes";
        LegacyChecklistTestSupport.snapshot(jdbc);
    }

    @Test
    void createsWithMissingNullKnownAndFutureKeysAndPersistsThem() throws Exception {
        for (String suffix : List.of("", ",\"iconKey\":null")) {
            var result = raw(post(roomsUrl), "{\"name\":\"Комната " + UUID.randomUUID() + "\"" + suffix + "}").andExpect(status().isCreated());
            var body = jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
            assertThat(body.has("iconKey")).isTrue();
            assertThat(body.get("iconKey").isNull()).isTrue();
            assertThat(savedIcon(UUID.fromString(body.get("id").asText()))).isNull();
        }
        for (String key : List.of("kitchen", "bed", "sofa", "bath", "book", "key", "heart", "sun", "home", "future-client-icon")) {
            var result = perform(post(roomsUrl), Map.of("name", "Выбран " + key, "iconKey", key)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.iconKey").value(key));
            var id = UUID.fromString(jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
            assertThat(savedIcon(id)).isEqualTo(key);
        }
        perform(get(roomsUrl).param("size", "100"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(14));
    }

    @Test
    void updatesIconSeparatelyAndRenamingAndNoOpPatchesPreserveChoice() throws Exception {
        perform(patch(roomUrl), Map.of("iconKey", "book")).andExpect(status().isOk())
                .andExpect(jsonPath("$.iconKey").value("book")).andExpect(jsonPath("$.name").value("Мастерская"))
                .andExpect(jsonPath("$.iconKeyPresent").doesNotExist());
        perform(patch(roomUrl), Map.of("name", "  Кабинет  ")).andExpect(status().isOk()).andExpect(jsonPath("$.iconKey").value("book"));
        var updatedAt = jdbc.queryForObject("SELECT updated_at FROM rooms WHERE id = ?", Timestamp.class, roomId);
        for (String body : List.of("{}", "{\"name\":null}", "{\"iconKeyPresent\":true}")) {
            raw(patch(roomUrl), body).andExpect(status().isOk()).andExpect(jsonPath("$.iconKey").value("book"));
        }
        assertThat(savedIcon(roomId)).isEqualTo("book");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM rooms WHERE id = ?", Timestamp.class, roomId)).isEqualTo(updatedAt);
        roomService.updateRoom(projectId, roomId, UpdateRoomRequest.builder().name("Рабочая комната").build(), ownerId);
        assertThat(savedIcon(roomId)).isEqualTo("book");
        roomService.updateRoom(projectId, roomId, UpdateRoomRequest.builder().iconKey("sun").build(), ownerId);
        assertThat(savedIcon(roomId)).isEqualTo("sun");
        roomService.updateRoom(projectId, roomId, UpdateRoomRequest.builder().iconKey(null).build(), ownerId);
        assertThat(savedIcon(roomId)).isNull();
    }

    @Test
    void explicitNullClearsIconAndRepeatedClearPreservesNameAndTime() throws Exception {
        raw(patch(roomUrl), "{\"iconKey\":null,\"name\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Мастерская"));
        assertThat(savedIcon(roomId)).isNull();
        var updatedAt = jdbc.queryForObject("SELECT updated_at FROM rooms WHERE id = ?", Timestamp.class, roomId);
        raw(patch(roomUrl), "{\"iconKey\":null}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT updated_at FROM rooms WHERE id = ?", Timestamp.class, roomId)).isEqualTo(updatedAt);
        assertRoomIconInList(null);
    }

    @Test
    void rejectsEmptyBlankAndLongKeysAndAccepts64CharactersWithoutPartialUpdate() throws Exception {
        for (String key : List.of("", " \t\n ", "\u2003", "k".repeat(65))) {
            perform(post(roomsUrl), Map.of("name", "Не сохранять", "iconKey", key)).andExpect(status().is(422))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            perform(patch(roomUrl), Map.of("name", "Не переименовывать", "iconKey", key)).andExpect(status().is(422));
            assertThat(savedIcon(roomId)).isEqualTo("kitchen");
            assertThat(jdbc.queryForObject("SELECT name FROM rooms WHERE id = ?", String.class, roomId)).isEqualTo("Мастерская");
        }
        perform(post(roomsUrl), Map.of("name", "Длинный ключ", "iconKey", "k".repeat(64))).andExpect(status().isCreated());
        perform(patch(roomUrl), Map.of("iconKey", "k".repeat(64))).andExpect(status().isOk());
        assertThat(savedIcon(roomId)).hasSize(64);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rooms WHERE project_id = ?", Long.class, projectId)).isEqualTo(3);
    }

    @Test
    void foreignUserAndWrongProjectCannotChangeIcon() throws Exception {
        mockMvc.perform(patch(roomUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"iconKey\":\"sun\"}")).andExpect(status().isNotFound());
        raw(patch("/api/move-projects/" + otherProjectId + "/rooms/" + roomId), "{\"iconKey\":null}").andExpect(status().isNotFound());
        mockMvc.perform(patch(roomUrl).contentType(MediaType.APPLICATION_JSON).content("{\"iconKey\":null}")).andExpect(status().isUnauthorized());
        assertThat(savedIcon(roomId)).isEqualTo("kitchen");
    }

    @Test
    void allRelatedResponsesReadCurrentRoomIconAfterChangeRenameAndClear() throws Exception {
        assertRelatedIcons("kitchen");
        perform(patch(roomUrl), Map.of("iconKey", "heart", "name", "Детская")).andExpect(status().isOk());
        assertRelatedIcons("heart");
        raw(patch(roomUrl), "{\"iconKey\":null}").andExpect(status().isOk());
        assertRelatedIcons(null);
    }

    @Test
    void changingRoomUpdatesRelatedIconAndFilteringUsesRoomId() throws Exception {
        perform(get(boxesUrl).param("roomId", secondRoomId.toString()), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        perform(patch(roomsUrl + "/" + secondRoomId), Map.of("iconKey", "bed")).andExpect(status().isOk());
        perform(patch(boxesUrl + "/" + boxId), Map.of("roomId", secondRoomId)).andExpect(status().isOk()).andExpect(jsonPath("$.roomIconKey").value("bed"));
        assertRelatedIcons("bed", false);
        perform(get(boxesUrl).param("roomId", roomId.toString()), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        perform(get(boxesUrl).param("roomId", secondRoomId.toString()), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    private void assertRelatedIcons(String expected) throws Exception { assertRelatedIcons(expected, true); }

    private void assertRelatedIcons(String expected, boolean checkOriginalRoom) throws Exception {
        if (checkOriginalRoom) assertRoomIconInList(expected);
        String itemUrl = projectUrl + "/boxes/" + boxId + "/items";
        for (String url : List.of(boxesUrl + "/" + boxId, itemUrl + "/" + itemId)) {
            var body = jsonMapper.readTree(perform(get(url), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(body.has("roomIconKey")).isTrue();
            assertThat(body.get("roomIconKey").isNull() ? null : body.get("roomIconKey").asText()).isEqualTo(expected);
        }
        for (String url : List.of(boxesUrl, itemUrl, projectUrl + "/items?q=Зарядка")) {
            var content = jsonMapper.readTree(perform(get(url), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("content");
            assertThat(content.size()).isEqualTo(1);
            assertThat(content.get(0).get("roomIconKey").isNull() ? null : content.get(0).get("roomIconKey").asText()).isEqualTo(expected);
        }
        var summary = jsonMapper.readTree(perform(get(boxesUrl + "/summary"), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("rooms");
        assertThat(summary.size()).isEqualTo(1);
        assertThat(summary.get(0).get("roomIconKey").isNull() ? null : summary.get(0).get("roomIconKey").asText()).isEqualTo(expected);
        var suggestions = jsonMapper.readTree(perform(get(projectUrl + "/items/" + itemId + "/suggestions"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(suggestions.size()).isEqualTo(1);
        assertThat(suggestions.get(0).get("roomIconKey").isNull() ? null : suggestions.get(0).get("roomIconKey").asText()).isEqualTo(expected);
        var checklist = jsonMapper.readTree(perform(get(projectUrl + "/first-day-checklist"), null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("content");
        assertThat(checklist.size()).isEqualTo(2);
        for (var point : checklist) {
            var icon = point.get("roomIconKey");
            assertThat(icon.isNull() ? null : icon.asText()).isEqualTo(point.get("itemId").isNull() ? null : "kitchen");
        }
    }

    private void assertRoomIconInList(String expected) throws Exception {
        var rooms = jsonMapper.readTree(perform(get(roomsUrl), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("content");
        boolean found = false;
        for (var room : rooms) {
            if (!room.get("id").asText().equals(roomId.toString())) continue;
            assertThat(room.get("iconKey").isNull() ? null : room.get("iconKey").asText()).isEqualTo(expected);
            found = true;
        }
        assertThat(found).isTrue();
    }

    private String savedIcon(UUID id) { return jdbc.queryForObject("SELECT icon_key FROM rooms WHERE id = ?", String.class, id); }

    private ResultActions perform(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return raw(request, body == null ? null : jsonMapper.writeValueAsString(body));
    }

    private ResultActions raw(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private UserEntity user() {
        var user = UserEntity.builder().email("room-icon-" + UUID.randomUUID()).password("hash").role(UserRole.USER).build();
        entityManager.persist(user);
        return user;
    }

    private MoveProjectEntity project(UserEntity owner) {
        var project = MoveProjectEntity.builder().owner(owner).name("Переезд").moveDate(Instant.now()).build();
        entityManager.persist(project);
        return project;
    }
}
