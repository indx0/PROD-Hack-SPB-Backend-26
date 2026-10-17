package ru.example.prodbackend.integration.move;

import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.example.prodbackend.checklist.repository.FirstDayChecklistRepository;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.event.repository.ProjectEventRepository;
import ru.example.prodbackend.move.dto.response.MoveProjectResponse;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.entity.TaskDueType;
import ru.example.prodbackend.task.repository.MoveTaskRepository;
import ru.example.prodbackend.task.repository.TaskTemplateRepository;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MoveProjectBootstrapIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private MoveTaskRepository taskRepository;
    @Autowired
    private TaskTemplateRepository templateRepository;
    @Autowired
    private FirstDayChecklistRepository checklistRepository;
    @Autowired
    private ProjectEventRepository eventRepository;

    private String token;

    @BeforeEach
    void setUpUser() throws Exception {
        token = registerUser().getAccessToken();
    }

    @Test
    void creationWithoutSelectionCreatesNoTasksOrChecklistButKeepsEvent() throws Exception {
        long catalogSize = templateRepository.count();
        UUID projectId = createProject();
        assertEquals(0L, taskRepository.countByProject_Id(projectId));
        assertEquals(catalogSize, templateRepository.count());
        var checklist = checklistRepository.findAllByProject_Id(projectId, PageRequest.of(0, 100)).getContent();
        assertTrue(checklist.isEmpty());
        assertEquals(1L, eventRepository.count());
        assertEquals(ProjectEventType.PROJECT_CREATED, eventRepository.findAll().get(0).getEventType());
        assertEquals(projectId, eventRepository.findAll().get(0).getObjectId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void initialChecklistChoicePersistsWithoutReadSideEffects(boolean include) throws Exception {
        byte[] response = mockMvc.perform(post("/api/move-projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Выбор чек-листа\",\"moveDate\":\"2026-11-01T09:00:00Z\",\"includeFirstDayChecklist\":" + include + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        var id = jsonMapper.readValue(response, MoveProjectResponse.class).getId();
        String anotherDevice = authenticateUser(EMAIL).getAccessToken();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/move-projects/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + anotherDevice)).andExpect(status().isOk());
            mockMvc.perform(get("/api/move-projects/" + id + "/first-day-checklist").header(HttpHeaders.AUTHORIZATION, "Bearer " + anotherDevice))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(get("/api/move-projects/" + id + "/tasks").header(HttpHeaders.AUTHORIZATION, "Bearer " + anotherDevice))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(include ? 4 : 0));
            mockMvc.perform(get("/api/move-projects/" + id + "/task-folders").header(HttpHeaders.AUTHORIZATION, "Bearer " + anotherDevice))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(include ? 1 : 0));
        }
        assertEquals(0, checklistRepository.count());
        assertEquals(include ? 4 : 0, taskRepository.count());
        assertEquals(1, eventRepository.count());
    }

    @Test
    void explicitNullChecklistChoiceRejectsCreationEntirely() throws Exception {
        mockMvc.perform(post("/api/move-projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Выбор\",\"moveDate\":\"2026-11-01T09:00:00Z\",\"includeFirstDayChecklist\":null}"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(0, moveProjectRepository.count());
        assertEquals(0, checklistRepository.count());
        assertEquals(0, eventRepository.count());
    }

    @Test
    void changingProjectDateAndReadingAgainNeverReschedulesOrDuplicatesPlan() throws Exception {
        UUID projectId = createProject();
        var template = templateRepository.findAll().get(0);
        byte[] created = mockMvc.perform(post("/api/move-projects/" + projectId + "/tasks/from-template")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"" + template.getId() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        UUID taskId = UUID.fromString(jsonMapper.readTree(created).get("id").asText());
        mockMvc.perform(patch("/api/move-projects/" + projectId + "/tasks/" + taskId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\":\"2026-12-12\"}"))
                .andExpect(status().isOk());
        var before = dueDates(projectId);
        mockMvc.perform(patch("/api/move-projects/" + projectId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"moveDate\":\"2026-12-01T12:00:00Z\"}"))
                .andExpect(status().isOk());
        for (int repeat = 0; repeat < 2; repeat++) {
            mockMvc.perform(get("/api/move-projects/" + projectId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
        }
        assertEquals(before, dueDates(projectId));
        assertEquals(LocalDate.parse("2026-12-12"), dueDates(projectId).get(taskId));
        assertEquals(1L, taskRepository.count());
        assertEquals(0L, checklistRepository.count());
        assertEquals(1L, eventRepository.count());
    }

    private UUID createProject() throws Exception {
        byte[] body = mockMvc.perform(post("/api/move-projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Переезд\",\"moveDate\":\"2026-11-01T00:30:00+03:00\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        return jsonMapper.readValue(body, MoveProjectResponse.class).getId();
    }

    private Map<UUID, LocalDate> dueDates(UUID projectId) {
        return taskRepository.findAllByFilters(projectId, null, null, null, PageRequest.of(0, 100)).stream()
                .collect(Collectors.toMap(MoveTaskEntity::getId, MoveTaskEntity::getDueDate));
    }
}
