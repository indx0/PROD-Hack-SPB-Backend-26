package ru.example.prodbackend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void creationWithoutSelectionCreatesNoTasksButKeepsChecklistAndEvent() throws Exception {
        long catalogSize = templateRepository.count();
        UUID projectId = createProject();
        assertEquals(0L, taskRepository.countByProject_Id(projectId));
        assertEquals(catalogSize, templateRepository.count());
        var checklist = checklistRepository.findAllByProject_Id(projectId, PageRequest.of(0, 100)).getContent();
        assertEquals(Set.of("Зубные щётки", "Зарядки", "Чайник", "Постельное бельё"), checklist.stream().map(item -> item.getName()).collect(Collectors.toSet()));
        assertEquals(1L, eventRepository.count());
        assertEquals(ProjectEventType.PROJECT_CREATED, eventRepository.findAll().get(0).getEventType());
        assertEquals(projectId, eventRepository.findAll().get(0).getObjectId());
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
        assertEquals(4L, checklistRepository.count());
        assertEquals(1L, eventRepository.count());
    }

    private UUID createProject() throws Exception {
        byte[] body = mockMvc.perform(post("/api/move-projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Переезд\",\"moveDate\":\"2026-11-01T00:30:00+03:00\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        return jsonMapper.readValue(body, MoveProjectResponse.class).getId();
    }

    private Map<UUID, LocalDate> dueDates(UUID projectId) {
        return taskRepository.findAllByFilters(projectId, null, null, PageRequest.of(0, 100)).stream()
                .collect(Collectors.toMap(MoveTaskEntity::getId, MoveTaskEntity::getDueDate));
    }
}
