package ru.example.prodbackend.integration.move;

import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.box.dto.BoxResponse;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.checklist.repository.FirstDayChecklistRepository;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.event.repository.ProjectEventRepository;
import ru.example.prodbackend.event.service.ProjectEventService;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.repository.ItemRepository;
import ru.example.prodbackend.move.dto.response.MoveProjectResponse;
import ru.example.prodbackend.room.dto.response.RoomResponse;
import ru.example.prodbackend.task.repository.MoveTaskRepository;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MoveWorkflowIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private BoxRepository boxRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private MoveTaskRepository taskRepository;
    @Autowired
    private FirstDayChecklistRepository checklistRepository;
    @Autowired
    private ProjectEventRepository eventRepository;
    @Autowired
    private ProjectEventService eventService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private String token;
    private UUID projectId;

    @BeforeEach
    void setUpMove() throws Exception {
        token = registerUser().getAccessToken();
        byte[] body = call("POST", "/api/move-projects", Map.of("name", "Сквозной сценарий", "moveDate", "2026-11-01T09:00:00Z"), token)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        projectId = jsonMapper.readValue(body, MoveProjectResponse.class).getId();
    }

    @Test
    void fullMoveScenarioPreservesInventoryAndProducesPredictableJournalAndProgress() throws Exception {
        UUID kitchen = createRoom("Кухня");
        UUID bedroom = createRoom("Спальня");
        String boxes = "/api/projects/" + projectId + "/boxes";
        byte[] body = call("POST", boxes, Map.of("number", 1, "roomId", kitchen, "contentsDeferred", true), token)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        var box = jsonMapper.readValue(body, BoxResponse.class);
        var item = itemRepository.saveAndFlush(ItemEntity.builder().box(boxRepository.getReferenceById(box.getId())).name("Чайник").build());
        String card = boxes + "/" + box.getId();
        for (int repeat = 0; repeat < 2; repeat++) call("GET", card, null, token).andExpect(status().isOk());
        assertEquals(1L, boxRepository.count());
        call("PATCH", card, Map.of("roomId", bedroom, "contentsDeferred", false), token).andExpect(status().isOk());
        String itemCard = "/api/move-projects/" + projectId + "/boxes/" + box.getId() + "/items/" + item.getId();
        call("PATCH", itemCard, Map.of("packed", true), token).andExpect(status().isOk());
        for (String status : new String[]{"PACKED", "LOADED", "DELIVERED", "UNPACKED"}) {
            if (status.equals("UNPACKED")) call("PATCH", itemCard, Map.of("unpacked", true), token).andExpect(status().isOk());
            call("PATCH", card + "/status", Map.of("status", status), token).andExpect(status().isOk());
            call("PATCH", card + "/status", Map.of("status", status), token).andExpect(status().isOk());
        }
        assertEquals(box.getId(), itemRepository.findById(item.getId()).orElseThrow().getBox().getId());
        assertEquals(bedroom, boxRepository.findById(box.getId()).orElseThrow().getRoom().getId());
        UUID taskId = UUID.fromString(jsonMapper.readTree(call("POST", "/api/move-projects/" + projectId + "/tasks",
                Map.of("title", "Собрать вещи", "stage", "BEFORE_MOVE"), token).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray()).get("id").asText());
        String task = "/api/move-projects/" + projectId + "/tasks/" + taskId + "/completion";
        call("PATCH", task, Map.of("completed", true), token).andExpect(status().isOk());
        call("PATCH", task, Map.of("completed", true), token).andExpect(status().isOk());
        UUID checklistItem = UUID.fromString(jsonMapper.readTree(call("POST", "/api/move-projects/" + projectId + "/first-day-checklist",
                Map.of("name", "Документы"), token).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray()).get("id").asText());
        call("PATCH", "/api/move-projects/" + projectId + "/first-day-checklist/" + checklistItem, Map.of("completed", true), token).andExpect(status().isOk());
        call("GET", boxes + "/summary", null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.countsByStatus.UNPACKED").value(1)).andExpect(jsonPath("$.totalBoxes").value(1))
                .andExpect(jsonPath("$.rooms[0].roomId").value(bedroom.toString())).andExpect(jsonPath("$.rooms[0].unpackingProgressPercent").value(100));
        for (int repeat = 0; repeat < 2; repeat++) {
            call("GET", events() + "?page=0&size=2", null, token).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(7)).andExpect(jsonPath("$.totalPages").value(4))
                    .andExpect(jsonPath("$.content[0].eventType").value("TASK_COMPLETED"))
                    .andExpect(jsonPath("$.content[0].objectId").value(taskId.toString()))
                    .andExpect(jsonPath("$.content[1].eventType").value("BOX_STATUS_CHANGED"));
        }
        assertEquals(1L, countEvents(ProjectEventType.PROJECT_CREATED));
        assertEquals(1L, countEvents(ProjectEventType.BOX_CREATED));
        assertEquals(4L, countEvents(ProjectEventType.BOX_STATUS_CHANGED));
        assertEquals(1L, countEvents(ProjectEventType.TASK_COMPLETED));
        call("DELETE", card, null, token).andExpect(status().isNoContent());
        assertFalse(itemRepository.existsById(item.getId()));
        call("GET", events(), null, token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(7));
    }

    @Test
    void journalEnforcesOwnershipAuthenticationAndPagination() throws Exception {
        assertErrorResponse(call("GET", events(), null, null), 401, "UNAUTHORIZED");
        String otherToken = registerUser("other@example.com").getAccessToken();
        assertErrorResponse(call("GET", events(), null, otherToken), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(call("GET", "/api/move-projects/" + UUID.randomUUID() + "/events", null, token), 404, "MOVE_PROJECT_NOT_FOUND");
        assertErrorResponse(call("GET", "/api/move-projects/not-a-uuid/events", null, token), 400, "INVALID_REQUEST_PARAMETER");
        for (String query : new String[]{"page=-1", "size=0", "size=101"}) {
            assertErrorResponse(call("GET", events() + "?" + query, null, token), 422, "VALIDATION_ERROR");
        }
        call("GET", events() + "?page=50&size=1", null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void journalHandlesSystemActorAndDoesNotExposeUserOrProjectSecrets() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var project = moveProjectRepository.findById(projectId).orElseThrow();
            eventService.record(project, null, ProjectEventType.TASK_COMPLETED, EventObjectType.TASK, UUID.randomUUID(), "Системная запись");
        });
        call("GET", events(), null, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorId").isEmpty()).andExpect(jsonPath("$.content[0].description").value("Системная запись"))
                .andExpect(jsonPath("$.content[1].actorId").value(userRepository.findByEmail(EMAIL).orElseThrow().getId().toString()))
                .andExpect(jsonPath("$.content[1].actor").doesNotExist()).andExpect(jsonPath("$.content[1].project").doesNotExist())
                .andExpect(jsonPath("$.content[1].email").doesNotExist()).andExpect(jsonPath("$.content[1].password").doesNotExist());
    }

    private long countEvents(ProjectEventType type) {
        return eventRepository.findAll().stream().filter(event -> event.getEventType() == type).count();
    }

    private String events() {
        return "/api/move-projects/" + projectId + "/events";
    }

    private UUID createRoom(String name) throws Exception {
        byte[] body = call("POST", "/api/move-projects/" + projectId + "/rooms", Map.of("name", name), token)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        return jsonMapper.readValue(body, RoomResponse.class).getId();
    }

    private ResultActions call(String method, String path, Object body, String accessToken) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : jsonMapper.writeValueAsString(body));
        if (accessToken != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        return mockMvc.perform(request);
    }
}
