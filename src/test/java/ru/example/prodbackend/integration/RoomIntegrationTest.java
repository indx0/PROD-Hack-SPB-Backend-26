package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.move.dto.response.MoveProjectResponse;
import ru.example.prodbackend.room.dto.response.RoomResponse;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.room.repository.RoomRepository;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Базовые сценарии комнат с настоящими Security, сервисами и PostgreSQL. */
class RoomIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String token;
    private UUID projectId;

    @BeforeEach
    void setUpProject() throws Exception {
        token = registerUser().getAccessToken();
        projectId = createProject();
    }

    @Test
    void createNormalizesNameAndListUsesSharedPagination() throws Exception {
        RoomResponse bedroom = createRoom(projectId, "Спальня");
        RoomResponse kitchen = createRoom(projectId, "  Кухня  ");

        assertEquals("Кухня", kitchen.getName());
        assertEquals(projectId, kitchen.getProjectId());
        assertNotNull(kitchen.getCreatedAt());
        assertNotNull(kitchen.getUpdatedAt());
        assertEquals("Кухня", roomRepository.findById(kitchen.getId()).orElseThrow().getName());

        mockMvc.perform(get(url(projectId)).header(HttpHeaders.AUTHORIZATION, bearer(token)).param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(kitchen.getId().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get(url(projectId)).header(HttpHeaders.AUTHORIZATION, bearer(token)).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(bedroom.getId().toString()));
        mockMvc.perform(get(url(projectId)).header(HttpHeaders.AUTHORIZATION, bearer(token)).param("page", "100").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void renamePersistsAndEmptyOrNullPatchPreservesName() throws Exception {
        RoomResponse room = createRoom(projectId, "Кухня");

        update(room.getId(), "{\"name\":\"  Гостиная  \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Гостиная"));
        var saved = roomRepository.findById(room.getId()).orElseThrow();
        assertEquals("Гостиная", saved.getName());
        assertTrue(saved.getUpdatedAt().isAfter(room.getUpdatedAt()));

        for (String body : new String[]{"{}", "{\"name\":null}"}) {
            update(room.getId(), body).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Гостиная"));
        }
        assertEquals("Гостиная", roomRepository.findById(room.getId()).orElseThrow().getName());
    }

    @Test
    void duplicateNamesConflictOnlyWithinSameProject() throws Exception {
        createRoom(projectId, "Кухня");
        RoomResponse bedroom = createRoom(projectId, "Спальня");

        assertErrorResponse(postRoom(projectId, "{\"name\":\"  Кухня  \"}"), 409, "DATA_CONFLICT");
        assertErrorResponse(update(bedroom.getId(), "{\"name\":\"Кухня\"}"), 409, "DATA_CONFLICT");
        assertEquals("Спальня", roomRepository.findById(bedroom.getId()).orElseThrow().getName());

        UUID anotherProject = createProject();
        assertEquals("Кухня", createRoom(anotherProject, "Кухня").getName());
        assertEquals(3L, roomRepository.count());
    }

    @Test
    void invalidNamesAndPaginationReturn422WithoutChangingData() throws Exception {
        RoomResponse room = createRoom(projectId, "Кухня");
        for (String name : new String[]{"", "   ", "x".repeat(256)}) {
            String body = jsonMapper.writeValueAsString(Map.of("name", name));
            assertErrorResponse(postRoom(projectId, body), 422, "VALIDATION_ERROR");
            assertErrorResponse(update(room.getId(), body), 422, "VALIDATION_ERROR");
        }
        assertErrorResponse(postRoom(projectId, "{}"), 422, "VALIDATION_ERROR");
        assertErrorResponse(mockMvc.perform(get(url(projectId)).header(HttpHeaders.AUTHORIZATION, bearer(token)).param("size", "0")), 422, "VALIDATION_ERROR");

        assertEquals(1L, roomRepository.count());
        assertEquals("Кухня", roomRepository.findById(room.getId()).orElseThrow().getName());
    }

    @Test
    void foreignUserAndWrongProjectCannotAccessOrModifyRoom() throws Exception {
        RoomResponse room = createRoom(projectId, "Кухня");
        String otherToken = registerUser("other@example.com").getAccessToken();

        for (String method : new String[]{"GET", "POST", "PATCH", "DELETE"}) {
            String path = url(projectId) + (method.equals("PATCH") || method.equals("DELETE") ? "/" + room.getId() : "");
            assertErrorResponse(mockMvc.perform(request(HttpMethod.valueOf(method), path).header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Чужое изменение\"}")), 404, "MOVE_PROJECT_NOT_FOUND");
        }
        UUID anotherProject = createProject();
        assertErrorResponse(mockMvc.perform(patch(url(anotherProject) + "/" + room.getId()).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Нельзя изменить\"}")), 404, "ROOM_NOT_FOUND");
        assertErrorResponse(update(UUID.randomUUID(), "{\"name\":\"Нет комнаты\"}"), 404, "ROOM_NOT_FOUND");
        assertErrorResponse(mockMvc.perform(delete(url(projectId) + "/abc").header(HttpHeaders.AUTHORIZATION, bearer(token))), 400, "INVALID_REQUEST_PARAMETER");

        assertEquals(1L, roomRepository.count());
        assertEquals("Кухня", roomRepository.findById(room.getId()).orElseThrow().getName());
    }

    @Test
    void deleteEmptyRoomReturns204AndRepeatedDeleteReturns404() throws Exception {
        RoomResponse removed = createRoom(projectId, "Кухня");
        RoomResponse retained = createRoom(projectId, "Спальня");
        String path = url(projectId) + "/" + removed.getId();

        mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        assertFalse(roomRepository.existsById(removed.getId()));
        assertTrue(roomRepository.existsById(retained.getId()));
        assertErrorResponse(mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, bearer(token))), 404, "ROOM_NOT_FOUND");
    }

    @Test
    void deleteRoomWithBoxReturns409AndPreservesRoomBoxAndItem() throws Exception {
        RoomResponse room = createRoom(projectId, "Кухня");
        UUID[] contents = new TransactionTemplate(transactionManager).execute(status -> {
            var box = BoxEntity.builder().project(moveProjectRepository.findById(projectId).orElseThrow())
                    .room(entityManager.getReference(RoomEntity.class, room.getId())).number(1).build();
            entityManager.persist(box);
            var item = ItemEntity.builder().box(box).name("Чайник").build();
            entityManager.persist(item);
            return new UUID[]{box.getId(), item.getId()};
        });

        assertErrorResponse(mockMvc.perform(delete(url(projectId) + "/" + room.getId()).header(HttpHeaders.AUTHORIZATION, bearer(token))), 409, "DATA_CONFLICT");

        assertTrue(roomRepository.existsById(room.getId()));
        assertNotNull(contents);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertNotNull(entityManager.find(BoxEntity.class, contents[0]));
            assertNotNull(entityManager.find(ItemEntity.class, contents[1]));
        });
    }

    @Test
    void allRoomEndpointsRequireAccessToken() throws Exception {
        for (String method : new String[]{"GET", "POST", "PATCH", "DELETE"}) {
            String path = url(projectId) + (method.equals("PATCH") || method.equals("DELETE") ? "/" + UUID.randomUUID() : "");
            assertErrorResponse(mockMvc.perform(request(HttpMethod.valueOf(method), path)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Кухня\"}")), 401, "UNAUTHORIZED");
        }
    }

    private UUID createProject() throws Exception {
        byte[] body = mockMvc.perform(post("/api/move-projects").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Тест комнат\",\"moveDate\":\"2026-11-01T09:00:00Z\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        return jsonMapper.readValue(body, MoveProjectResponse.class).getId();
    }

    private RoomResponse createRoom(UUID project, String name) throws Exception {
        byte[] body = postRoom(project, jsonMapper.writeValueAsString(Map.of("name", name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        return jsonMapper.readValue(body, RoomResponse.class);
    }

    private ResultActions postRoom(UUID project, String body) throws Exception {
        return mockMvc.perform(post(url(project)).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(UUID roomId, String body) throws Exception {
        return mockMvc.perform(patch(url(projectId) + "/" + roomId).header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String url(UUID project) {
        return "/api/move-projects/" + project + "/rooms";
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
