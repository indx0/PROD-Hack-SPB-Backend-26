package ru.example.prodbackend.integration.system;

import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Один контракт пагинации для проектов и коробок на настоящем PostgreSQL. */
class PaginationIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String token;
    private UUID projectId;
    private UUID emptyProjectId;
    private UUID foreignProjectId;
    private UUID kitchenId;

    @BeforeEach
    void setUpProjectsAndBoxes() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var owner = userRepository.save(UserEntity.builder().email(EMAIL).password("test-hash").role(UserRole.USER).build());
            var stranger = userRepository.save(UserEntity.builder().email("other@example.com").password("test-hash").role(UserRole.USER).build());
            var project = project(owner, "Первый");
            projectId = project.getId();
            emptyProjectId = project(owner, "Второй").getId();
            var foreignProject = project(stranger, "Чужой");
            foreignProjectId = foreignProject.getId();
            var kitchen = room(project, "Кухня");
            kitchenId = kitchen.getId();
            box(project, kitchen, 3, BoxStatus.PACKING, false);
            box(project, room(project, "Спальня"), 2, BoxStatus.PACKED, false);
            box(project, kitchen, 1, BoxStatus.PACKING, true);
            box(foreignProject, room(foreignProject, "Кухня"), 1, BoxStatus.PACKING, true);
        });
        token = signedAccessToken(EMAIL, Instant.now().plusSeconds(300));
    }

    @ParameterizedTest
    @ValueSource(strings = {"projects", "boxes"})
    void defaultsAndJsonEnvelopeAreIdentical(String resource) throws Exception {
        int total = resource.equals("projects") ? 2 : 3;
        var result = list(resource, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(total))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(total))
                .andExpect(jsonPath("$.totalPages").value(1));
        Map<?, ?> json = jsonMapper.readValue(result.andReturn().getResponse().getContentAsByteArray(), Map.class);
        assertEquals(Set.of("content", "page", "size", "totalElements", "totalPages"), json.keySet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"projects", "boxes"})
    void pageBeyondLastIsEmptyAndKeepsTotals(String resource) throws Exception {
        int total = resource.equals("projects") ? 2 : 3;
        list(resource, Map.of("page", "100", "size", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(100))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(total))
                .andExpect(jsonPath("$.totalPages").value(total));
    }

    @ParameterizedTest
    @ValueSource(strings = {"projects", "boxes"})
    void maximumPageSizeIsAccepted(String resource) throws Exception {
        list(resource, Map.of("size", "100")).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @ParameterizedTest
    @CsvSource({"projects,page,-1", "projects,size,0", "projects,size,101", "projects,page,text", "projects,size,text",
            "boxes,page,-1", "boxes,size,0", "boxes,size,101", "boxes,page,text", "boxes,size,text"})
    void invalidPaginationReturnsSame422Error(String resource, String parameter, String value) throws Exception {
        assertErrorResponse(list(resource, Map.of(parameter, value)), 422, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.fieldErrors[0].field").value(parameter));
    }

    @Test
    void boxesHaveStableNumberOrderAcrossPages() throws Exception {
        for (int repeat = 0; repeat < 2; repeat++) {
            list("boxes", Map.of("page", "0", "size", "1")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].number").value(1))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(3));
        }
        list("boxes", Map.of("page", "1", "size", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(2));
    }

    @Test
    void boxFiltersAreCombinedAndTotalsDescribeFilteredResult() throws Exception {
        list("boxes", Map.of("roomId", kitchenId.toString(), "status", "PACKING", "contentsDeferred", "true", "size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].number").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        list("boxes", Map.of("number", "999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void ownEmptyProjectReturnsEmptyPageButForeignAndMissingProjectsReturn404() throws Exception {
        mockMvc.perform(get(boxesUrl(emptyProjectId)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        for (UUID id : new UUID[]{foreignProjectId, UUID.randomUUID()}) {
            assertErrorResponse(mockMvc.perform(get(boxesUrl(id)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)), 404, "MOVE_PROJECT_NOT_FOUND");
        }
    }

    private ResultActions list(String resource, Map<String, String> parameters) throws Exception {
        var request = get(resource.equals("projects") ? "/api/move-projects" : boxesUrl(projectId));
        parameters.forEach(request::param);
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String boxesUrl(UUID id) {
        return "/api/projects/" + id + "/boxes";
    }

    private MoveProjectEntity project(UserEntity owner, String name) {
        var project = MoveProjectEntity.builder().owner(owner).name(name).moveDate(Instant.parse("2026-11-01T09:00:00Z")).build();
        entityManager.persist(project);
        return project;
    }

    private RoomEntity room(MoveProjectEntity project, String name) {
        var room = RoomEntity.builder().project(project).name(name).build();
        entityManager.persist(room);
        return room;
    }

    private void box(MoveProjectEntity project, RoomEntity room, int number, BoxStatus status, boolean deferred) {
        entityManager.persist(BoxEntity.builder().project(project).room(room).number(number).status(status).contentsDeferred(deferred).build());
    }
}
