package ru.example.prodbackend.integration.checklist;

import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import ru.example.prodbackend.checklist.dto.response.ChecklistItemResponse;
import ru.example.prodbackend.checklist.repository.FirstDayChecklistRepository;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.move.dto.response.MoveProjectResponse;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FirstDayChecklistIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private FirstDayChecklistRepository checklistRepository;

    private String token;
    private UUID projectId;

    @BeforeEach
    void setUpProject() throws Exception {
        token = registerUser().getAccessToken();
        projectId = createProject();
    }

    @Test
    void existingLegacyFixturesSupportCreationAndSharedPagination() throws Exception {
        var defaults = checklistRepository.findAllByProject_Id(projectId, PageRequest.of(0, 100)).getContent();
        assertEquals(Set.of("Зубные щётки", "Зарядки", "Чайник", "Постельное бельё"), defaults.stream().map(item -> item.getName()).collect(Collectors.toSet()));
        assertTrue(defaults.stream().allMatch(item -> item.getCompletedAt() == null));
        call("GET", projectId, "?page=1&size=2", "", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.totalPages").value(2));
        var response = call("POST", projectId, "", "{\"name\":\"  Аптечка  \"}", token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Аптечка")).andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.project").doesNotExist()).andReturn().getResponse();
        var item = jsonMapper.readValue(response.getContentAsByteArray(), ChecklistItemResponse.class);
        assertEquals(projectId, item.getProjectId());
        assertEquals("Аптечка", checklistRepository.findById(item.getId()).orElseThrow().getName());
        call("GET", projectId, "?page=100&size=2", "", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void renameCompletionAndRepeatedOrEmptyPatchArePredictable() throws Exception {
        UUID itemId = firstItem();
        String path = "/" + itemId;
        call("PATCH", projectId, path, "{\"name\":\"  Дорожная аптечка  \",\"completed\":true}", token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Дорожная аптечка")).andExpect(jsonPath("$.completed").value(true));
        var saved = checklistRepository.findById(itemId).orElseThrow();
        var completedAt = saved.getCompletedAt();
        var updatedAt = saved.getUpdatedAt();
        assertNotNull(completedAt);
        for (String body : new String[]{"{\"completed\":true}", "{}", "{\"name\":null,\"completed\":null}"}) {
            call("PATCH", projectId, path, body, token).andExpect(status().isOk());
            saved = checklistRepository.findById(itemId).orElseThrow();
            assertEquals(completedAt, saved.getCompletedAt());
            assertEquals(updatedAt, saved.getUpdatedAt());
            assertEquals("Дорожная аптечка", saved.getName());
        }
        call("PATCH", projectId, path, "{\"completed\":false}", token).andExpect(status().isOk()).andExpect(jsonPath("$.completedAt").isEmpty());
        assertNull(checklistRepository.findById(itemId).orElseThrow().getCompletedAt());
    }

    @Test
    void invalidInputDoesNotChangeChecklist() throws Exception {
        UUID itemId = firstItem();
        String originalName = checklistRepository.findById(itemId).orElseThrow().getName();
        for (String name : new String[]{"", "   ", "x".repeat(256)}) {
            String body = jsonMapper.writeValueAsString(Map.of("name", name));
            assertErrorResponse(call("POST", projectId, "", body, token), 422, "VALIDATION_ERROR");
            assertErrorResponse(call("PATCH", projectId, "/" + itemId, body, token), 422, "VALIDATION_ERROR");
        }
        assertErrorResponse(call("POST", projectId, "", "{}", token), 422, "VALIDATION_ERROR");
        assertErrorResponse(call("PATCH", projectId, "/" + itemId, "{\"completed\":{}}", token), 400, "INVALID_REQUEST_BODY");
        assertErrorResponse(call("DELETE", projectId, "/not-a-uuid", "", token), 400, "INVALID_REQUEST_PARAMETER");
        for (String query : new String[]{"page=-1", "size=0", "size=101"}) {
            assertErrorResponse(call("GET", projectId, "?" + query, "", token), 422, "VALIDATION_ERROR");
        }
        assertEquals(4L, checklistRepository.count());
        assertEquals(originalName, checklistRepository.findById(itemId).orElseThrow().getName());
    }

    @Test
    void foreignUserAndWrongProjectCannotAccessChecklist() throws Exception {
        UUID itemId = firstItem();
        String otherToken = registerUser("other@example.com").getAccessToken();
        for (String method : new String[]{"GET", "POST", "PATCH", "DELETE"}) {
            String suffix = method.equals("PATCH") || method.equals("DELETE") ? "/" + itemId : "";
            assertErrorResponse(call(method, projectId, suffix, "{\"name\":\"Чужое изменение\"}", otherToken), 404, "MOVE_PROJECT_NOT_FOUND");
        }
        UUID otherProject = createProject();
        assertErrorResponse(call("PATCH", otherProject, "/" + itemId, "{\"completed\":true}", token), 404, "CHECKLIST_ITEM_NOT_FOUND");
        assertErrorResponse(call("DELETE", otherProject, "/" + itemId, "", token), 404, "CHECKLIST_ITEM_NOT_FOUND");
        assertErrorResponse(call("DELETE", projectId, "/" + UUID.randomUUID(), "", token), 404, "CHECKLIST_ITEM_NOT_FOUND");
        assertErrorResponse(call("GET", UUID.randomUUID(), "", "", token), 404, "MOVE_PROJECT_NOT_FOUND");
        assertNull(checklistRepository.findById(itemId).orElseThrow().getCompletedAt());
        assertEquals(8L, checklistRepository.count());
    }

    @Test
    void deleteRemovesOnlySelectedItemAndRepeatedDeleteReturns404() throws Exception {
        UUID itemId = firstItem();
        call("DELETE", projectId, "/" + itemId, "", token).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertFalse(checklistRepository.existsById(itemId));
        assertEquals(3L, checklistRepository.count());
        assertErrorResponse(call("DELETE", projectId, "/" + itemId, "", token), 404, "CHECKLIST_ITEM_NOT_FOUND");
    }

    @Test
    void allChecklistOperationsRequireAccessToken() throws Exception {
        for (String method : new String[]{"GET", "POST", "PATCH", "DELETE"}) {
            String suffix = method.equals("PATCH") || method.equals("DELETE") ? "/" + firstItem() : "";
            assertErrorResponse(call(method, projectId, suffix, "{\"name\":\"Аптечка\"}", null), 401, "UNAUTHORIZED");
        }
    }

    private UUID firstItem() {
        return checklistRepository.findAllByProject_Id(projectId, PageRequest.of(0, 1)).getContent().get(0).getId();
    }

    private UUID createProject() throws Exception {
        byte[] body = mockMvc.perform(post("/api/move-projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Чек-лист\",\"moveDate\":\"2026-11-01T09:00:00Z\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        UUID id = jsonMapper.readValue(body, MoveProjectResponse.class).getId();
        // Existing legacy records remain supported until the separate retirement/backfill task.
        var project = moveProjectRepository.getReferenceById(id);
        checklistRepository.saveAllAndFlush(List.of("Зубные щётки", "Зарядки", "Чайник", "Постельное бельё").stream()
                .map(name -> FirstDayChecklistItemEntity.builder().project(project).name(name).build()).toList());
        return id;
    }

    private ResultActions call(String method, UUID project, String suffix, String body, String accessToken) throws Exception {
        var request = request(HttpMethod.valueOf(method), "/api/move-projects/" + project + "/first-day-checklist" + suffix)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (accessToken != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        return mockMvc.perform(request);
    }
}
