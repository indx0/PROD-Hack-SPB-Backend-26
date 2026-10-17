package ru.example.prodbackend.integration.move;

import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.checklist.repository.FirstDayChecklistRepository;
import ru.example.prodbackend.event.repository.ProjectEventRepository;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.room.repository.RoomRepository;
import ru.example.prodbackend.task.config.TaskTemplateInitializer;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.repository.MoveTaskRepository;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProgressIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired private BoxRepository boxRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private MoveTaskRepository taskRepository;
    @Autowired private FirstDayChecklistRepository checklistRepository;
    @Autowired private ProjectEventRepository eventRepository;
    @Autowired private TaskTemplateInitializer templateInitializer;

    private String token;
    private MoveProjectEntity project;

    @BeforeEach
    void setUpProject() throws Exception {
        token = registerUser().getAccessToken();
        project = emptyProject();
    }

    @Test
    void boxesSummaryMatchesExistingEndpointIncludingRoomsAndStatusCounts() throws Exception {
        RoomEntity kitchen = room("Кухня");
        RoomEntity bedroom = room("Спальня");
        room("Пустая комната");
        box(kitchen, 3, BoxStatus.PACKING, true);
        box(kitchen, 4, BoxStatus.UNPACKED, false);
        box(kitchen, 5, BoxStatus.PACKED, false);
        box(bedroom, 1, BoxStatus.LOADED, true);
        box(bedroom, 2, BoxStatus.DELIVERED, false);
        String expected = mockMvc.perform(get("/api/projects/" + project.getId() + "/boxes/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        summary("boxes").andExpect(status().isOk()).andExpect(content().json(expected))
                .andExpect(jsonPath("$.totalBoxes").value(5))
                .andExpect(jsonPath("$.deferredContentsCount").value(2))
                .andExpect(jsonPath("$.countsByStatus.PACKING").value(1))
                .andExpect(jsonPath("$.countsByStatus.PACKED").value(1))
                .andExpect(jsonPath("$.countsByStatus.LOADED").value(1))
                .andExpect(jsonPath("$.countsByStatus.DELIVERED").value(1))
                .andExpect(jsonPath("$.countsByStatus.UNPACKED").value(1))
                .andExpect(jsonPath("$.rooms.length()").value(2))
                .andExpect(jsonPath("$.rooms[0].roomId").value(bedroom.getId().toString()))
                .andExpect(jsonPath("$.rooms[1].unpackingProgressPercent").value(33));
    }

    @Test
    void emptyBoxSummaryMatchesExistingEndpoint() throws Exception {
        room("Без коробок");
        String expected = mockMvc.perform(get("/api/projects/" + project.getId() + "/boxes/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        summary("boxes").andExpect(status().isOk()).andExpect(content().json(expected))
                .andExpect(jsonPath("$.totalBoxes").value(0))
                .andExpect(jsonPath("$.deferredContentsCount").value(0))
                .andExpect(jsonPath("$.countsByStatus.UNPACKED").value(0))
                .andExpect(jsonPath("$.rooms").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"0,0", "3,0", "3,1", "3,3"})
    void countsOnlyProjectTasksAcrossAllStages(int total, int completed) throws Exception {
        for (int i = 0; i < total; i++) {
            task(project, TaskStage.values()[i % TaskStage.values().length], i < completed);
        }
        task(emptyProject(), TaskStage.BEFORE_MOVE, true);

        assertTasks(total, completed);
    }

    @Test
    void countsNoTasksWithoutExplicitSelectionAndKeepsChecklistSeparate() throws Exception {
        templateInitializer.run(new DefaultApplicationArguments(new String[0]));
        byte[] body = mockMvc.perform(post("/api/move-projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Начальный план\",\"moveDate\":\"2026-11-01T09:00:00Z\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        UUID createdProjectId = UUID.fromString(jsonMapper.readTree(body).get("id").asText());
        assertEquals(4, checklistRepository.count());

        summary(createdProjectId.toString(), "tasks", token).andExpect(status().isOk())
                .andExpect(content().json("{\"totalTasks\":0,\"completedTasks\":0}"));
    }

    @Test
    void taskCountsFollowCreationCompletionReopeningAndDeletion() throws Exception {
        assertTasks(0, 0);
        byte[] body = mockMvc.perform(post(taskUrl())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Заказать машину\",\"stage\":\"BEFORE_MOVE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        UUID taskId = UUID.fromString(jsonMapper.readTree(body).get("id").asText());
        assertTasks(1, 0);
        completion(taskId, true);
        assertTasks(1, 1);
        completion(taskId, false);
        assertTasks(1, 0);
        completion(taskId, true);
        mockMvc.perform(delete(taskUrl() + "/" + taskId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        assertTasks(0, 0);
    }

    @Test
    void repeatedReadsPreserveDataAndDoNotCreateEvents() throws Exception {
        BoxEntity box = box(room("Кухня"), 1, BoxStatus.PACKED, true);
        MoveTaskEntity task = task(project, TaskStage.AFTER_MOVE, true);
        var beforeBox = boxRepository.findById(box.getId()).orElseThrow();
        var beforeTask = taskRepository.findById(task.getId()).orElseThrow();
        var beforeProject = moveProjectRepository.findById(project.getId()).orElseThrow();
        long events = eventRepository.count();
        for (int i = 0; i < 2; i++) {
            summary("boxes").andExpect(status().isOk());
            assertTasks(1, 1);
        }

        var savedBox = boxRepository.findById(box.getId()).orElseThrow();
        var savedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(beforeBox.getStatus(), savedBox.getStatus());
        assertEquals(beforeBox.getUpdatedAt(), savedBox.getUpdatedAt());
        assertEquals(beforeTask.getCompletedAt(), savedTask.getCompletedAt());
        assertEquals(beforeTask.getUpdatedAt(), savedTask.getUpdatedAt());
        assertEquals(beforeProject.getUpdatedAt(), moveProjectRepository.findById(project.getId()).orElseThrow().getUpdatedAt());
        assertEquals(events, eventRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"boxes", "tasks"})
    void missingAndForeignProjectsReturnSharedNotFoundError(String endpoint) throws Exception {
        String strangerToken = registerUser("stranger@example.com").getAccessToken();
        assertErrorResponse(summary(project.getId().toString(), endpoint, strangerToken), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(summary(UUID.randomUUID().toString(), endpoint, token), 404, "MOVE_PROJECT_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"boxes", "tasks"})
    void requiresValidAuthentication(String endpoint) throws Exception {
        assertErrorResponse(mockMvc.perform(get(progressUrl(project.getId().toString(), endpoint))), 401, "UNAUTHORIZED");
        assertErrorResponse(summary(project.getId().toString(), endpoint, "invalid-token"), 401, "UNAUTHORIZED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"boxes", "tasks"})
    void rejectsMalformedProjectId(String endpoint) throws Exception {
        assertErrorResponse(summary("not-a-uuid", endpoint, token), 400, "INVALID_REQUEST_PARAMETER");
    }

    @Test
    void openApiDescribesTypedResponsesAuthenticationAndSharedErrors() throws Exception {
        var docs = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String endpoint : new String[]{"boxes", "tasks"}) {
            String path = "$.paths['" + progressUrl("{projectId}", endpoint) + "'].get";
            String schema = endpoint.equals("boxes") ? "BoxSummaryResponse" : "TaskSummaryResponse";
            docs.andExpect(jsonPath(path + ".responses['200'].content['application/json'].schema['$ref']")
                            .value("#/components/schemas/" + schema))
                    .andExpect(jsonPath(path + ".security[0].jwtAuth").exists());
            for (String code : new String[]{"400", "401", "404"}) {
                docs.andExpect(jsonPath(path + ".responses['" + code + "'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"));
            }
        }
        docs.andExpect(jsonPath("$.components.schemas.TaskSummaryResponse.properties.totalTasks.format").value("int64"))
                .andExpect(jsonPath("$.components.schemas.TaskSummaryResponse.properties.completedTasks.format").value("int64"));
    }

    private MoveProjectEntity emptyProject() {
        return moveProjectRepository.saveAndFlush(MoveProjectEntity.builder()
                .owner(userRepository.findByEmail(EMAIL).orElseThrow()).name("Прогресс")
                .moveDate(Instant.parse("2026-11-01T09:00:00Z")).build());
    }

    private RoomEntity room(String name) {
        return roomRepository.saveAndFlush(RoomEntity.builder().project(project).name(name).build());
    }

    private BoxEntity box(RoomEntity room, int number, BoxStatus status, boolean deferred) {
        return boxRepository.saveAndFlush(BoxEntity.builder().project(project).room(room).number(number)
                .status(status).contentsDeferred(deferred).build());
    }

    private MoveTaskEntity task(MoveProjectEntity parent, TaskStage stage, boolean completed) {
        return taskRepository.saveAndFlush(MoveTaskEntity.builder().project(parent).title("Задача").stage(stage)
                .completedAt(completed ? Instant.parse("2026-10-17T00:00:00Z") : null).build());
    }

    private void assertTasks(long total, long completed) throws Exception {
        summary("tasks").andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalTasks").value((int) total))
                .andExpect(jsonPath("$.completedTasks").value((int) completed));
    }

    private void completion(UUID taskId, boolean completed) throws Exception {
        mockMvc.perform(patch(taskUrl() + "/" + taskId + "/completion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":" + completed + "}"))
                .andExpect(status().isOk());
    }

    private String taskUrl() {
        return "/api/move-projects/" + project.getId() + "/tasks";
    }

    private ResultActions summary(String endpoint) throws Exception {
        return summary(project.getId().toString(), endpoint, token);
    }

    private ResultActions summary(String projectId, String endpoint, String accessToken) throws Exception {
        return mockMvc.perform(get(progressUrl(projectId, endpoint)).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private String progressUrl(String projectId, String endpoint) {
        return "/api/projects/" + projectId + "/progress/" + endpoint;
    }
}
