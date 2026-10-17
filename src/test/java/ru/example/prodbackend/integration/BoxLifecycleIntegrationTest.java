package ru.example.prodbackend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.box.dto.BoxResponse;
import ru.example.prodbackend.box.dto.CreateBoxRequest;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.event.repository.ProjectEventRepository;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.dto.CreateItemsRequest;
import ru.example.prodbackend.item.repository.ItemRepository;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.room.repository.RoomRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Полный цикл Box: настоящие транзакции запросов, комнаты, журнал и PostgreSQL. */
class BoxLifecycleIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private BoxRepository boxRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ItemService itemService;
    @Autowired
    private ProjectEventRepository eventRepository;
    @Autowired
    private BoxService boxService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private String token;
    private MoveProjectEntity project;
    private RoomEntity room;

    @BeforeEach
    void setUpProject() throws Exception {
        token = registerUser().getAccessToken();
        project = project();
        room = room(project, "Кухня");
    }

    @Test
    void createsPackingBoxAndEventAndRepeatedReadsDoNotCreateDuplicates() throws Exception {
        BoxResponse box = create(project.getId(), room.getId(), 1, true);

        assertEquals(project.getId(), box.getProjectId());
        assertEquals(room.getId(), box.getRoomId());
        assertEquals("Кухня", box.getRoomName());
        assertEquals(BoxStatus.PACKING, box.getStatus());
        assertTrue(box.isContentsDeferred());
        assertNotNull(box.getCreatedAt());
        assertNotNull(box.getUpdatedAt());
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get(url(project.getId()) + "/" + box.getId()).header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(box.getId().toString()));
        }
        assertEquals(1L, boxRepository.count());
        var event = eventRepository.findAll().get(0);
        assertEquals(1L, eventRepository.count());
        assertEquals(ProjectEventType.BOX_CREATED, event.getEventType());
        assertEquals(EventObjectType.BOX, event.getObjectType());
        assertEquals(box.getId(), event.getObjectId());
        assertEquals(project.getId(), event.getProject().getId());
        assertEquals(project.getOwner().getId(), event.getActor().getId());
        assertNotNull(event.getOccurredAt());
        assertTrue(event.getDescription().contains("№1"));
    }

    @Test
    void contentsDeferredDefaultsToFalse() throws Exception {
        postBox(project.getId(), jsonMapper.writeValueAsString(Map.of("number", 1, "roomId", room.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contentsDeferred").value(false));
        assertErrorResponse(postBox(project.getId(), "{}"), 422, "VALIDATION_ERROR");
        assertErrorResponse(postBox(project.getId(), "{\"number\":2,\"roomId\":\"" + room.getId() + "\",\"contentsDeferred\":null}"), 400, "INVALID_REQUEST_BODY");
        assertEquals(1L, boxRepository.count());
        assertEquals(1L, eventRepository.count());
    }

    @Test
    void duplicateNumberConflictsOnlyWithinProjectWithoutCreatingEvent() throws Exception {
        create(project.getId(), room.getId(), 1, false);
        assertErrorResponse(postBox(project.getId(), body(room.getId(), 1, true)), 409, "DATA_CONFLICT");
        assertEquals(1L, boxRepository.count());
        assertEquals(1L, eventRepository.count());

        MoveProjectEntity anotherProject = project();
        RoomEntity anotherRoom = room(anotherProject, "Спальня");
        create(anotherProject.getId(), anotherRoom.getId(), 1, false);
        assertEquals(2L, boxRepository.count());
        assertEquals(2L, eventRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "another-project", "foreign-owner"})
    void missingOrUnrelatedRoomRejectsCreateAndUpdateWithoutChanges(String scenario) throws Exception {
        BoxResponse box = create(project.getId(), room.getId(), 1, false);
        UUID wrongRoomId = UUID.randomUUID();
        if (!scenario.equals("missing")) {
            MoveProjectEntity anotherProject = project();
            if (scenario.equals("foreign-owner")) {
                registerUser("other@example.com");
                anotherProject.setOwner(userRepository.findByEmail("other@example.com").orElseThrow());
                anotherProject = moveProjectRepository.saveAndFlush(anotherProject);
            }
            wrongRoomId = room(anotherProject, "Другая комната").getId();
        }

        assertErrorResponse(postBox(project.getId(), body(wrongRoomId, 2, false)), 404, "ROOM_NOT_FOUND");
        assertErrorResponse(update(box.getId(), jsonMapper.writeValueAsString(Map.of("roomId", wrongRoomId, "number", 3))), 404, "ROOM_NOT_FOUND");

        var saved = boxRepository.findById(box.getId()).orElseThrow();
        assertEquals(room.getId(), saved.getRoom().getId());
        assertEquals(1, saved.getNumber());
        assertEquals(1L, boxRepository.count());
        assertEquals(1L, eventRepository.count());
    }

    @Test
    void changesRoomButPreservesContentsStatusAndCreationTime() throws Exception {
        BoxResponse box = create(project.getId(), room.getId(), 1, true);
        Instant createdAt = boxRepository.findById(box.getId()).orElseThrow().getCreatedAt();
        RoomEntity bedroom = room(project, "Спальня");
        var item = readyItem(box);
        changeStatus(box.getId(), BoxStatus.PACKED).andExpect(status().isOk());

        update(box.getId(), jsonMapper.writeValueAsString(Map.of("roomId", bedroom.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roomId").value(bedroom.getId().toString()))
                .andExpect(jsonPath("$.roomName").value("Спальня")).andExpect(jsonPath("$.status").value("PACKED"))
                .andExpect(jsonPath("$.contentsDeferred").value(true)).andExpect(jsonPath("$.number").value(1));

        var saved = boxRepository.findById(box.getId()).orElseThrow();
        assertEquals(bedroom.getId(), saved.getRoom().getId());
        assertEquals(createdAt, saved.getCreatedAt());
        assertEquals(box.getId(), itemRepository.findById(item.getId()).orElseThrow().getBox().getId());
        assertEquals(2L, eventRepository.count());
        mockMvc.perform(get(url(project.getId()) + "/summary").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rooms[0].roomId").value(bedroom.getId().toString()));
    }

    @Test
    void conflictingNumberRollsBackRoomAndDeferredChanges() throws Exception {
        BoxResponse box = create(project.getId(), room.getId(), 1, true);
        create(project.getId(), room.getId(), 2, false);
        RoomEntity bedroom = room(project, "Спальня");

        assertErrorResponse(update(box.getId(), jsonMapper.writeValueAsString(Map.of("roomId", bedroom.getId(), "number", 2, "contentsDeferred", false))), 409, "DATA_CONFLICT");

        var saved = boxRepository.findById(box.getId()).orElseThrow();
        assertEquals(room.getId(), saved.getRoom().getId());
        assertEquals(1, saved.getNumber());
        assertTrue(saved.isContentsDeferred());
        assertEquals(2L, eventRepository.count());
    }

    @Test
    void recordsOnlyActualStatusTransitions() throws Exception {
        BoxResponse box = create(project.getId(), room.getId(), 1, false);
        readyItem(box);
        long expectedEvents = 1;
        for (BoxStatus next : BoxStatus.values()) {
            changeStatus(box.getId(), next).andExpect(status().isOk());
            if (next != BoxStatus.PACKING) expectedEvents++;
            Instant updatedAt = boxRepository.findById(box.getId()).orElseThrow().getUpdatedAt();
            changeStatus(box.getId(), next).andExpect(status().isOk());
            assertEquals(expectedEvents, eventRepository.count());
            assertEquals(updatedAt, boxRepository.findById(box.getId()).orElseThrow().getUpdatedAt());
        }
        var transitions = eventRepository.findAll().stream().filter(event -> event.getEventType() == ProjectEventType.BOX_STATUS_CHANGED).toList();
        assertEquals(4, transitions.size());
        assertTrue(transitions.stream().allMatch(event -> event.getObjectId().equals(box.getId()) && event.getDescription().contains(" → ")));
        assertErrorResponse(changeStatus(box.getId(), BoxStatus.PACKING), 409, "DATA_CONFLICT");
        assertEquals(expectedEvents, eventRepository.count());
        assertEquals(BoxStatus.UNPACKED, boxRepository.findById(box.getId()).orElseThrow().getStatus());
    }

    @Test
    void transactionRollbackRemovesBothBoxAndItsEventAndRevertsStatusWithEvent() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            boxService.create(project.getOwner().getId(), project.getId(), CreateBoxRequest.builder().number(1).roomId(room.getId()).build());
            boxRepository.flush();
            eventRepository.flush();
            assertEquals(1L, eventRepository.count());
            status.setRollbackOnly();
        });
        assertEquals(0L, boxRepository.count());
        assertEquals(0L, eventRepository.count());

        BoxResponse box = create(project.getId(), room.getId(), 1, false);
        readyItem(box);
        transaction.executeWithoutResult(status -> {
            boxService.changeStatus(project.getOwner().getId(), project.getId(), box.getId(), BoxStatus.PACKED);
            eventRepository.flush();
            assertEquals(2L, eventRepository.count());
            status.setRollbackOnly();
        });
        assertEquals(BoxStatus.PACKING, boxRepository.findById(box.getId()).orElseThrow().getStatus());
        assertEquals(1L, eventRepository.count());
    }

    @Test
    void rollsBackBatchCreationAndBulkBoxDeletion() throws Exception {
        BoxResponse box = create(project.getId(), room.getId(), 1, false);
        ItemEntity preserved = itemRepository.saveAndFlush(ItemEntity.builder()
                .box(boxRepository.findById(box.getId()).orElseThrow()).name("Сохранить").build());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            itemService.createBatch(project.getOwner().getId(), project.getId(), box.getId(),
                    CreateItemsRequest.builder().names(List.of("A", "B")).build());
            assertEquals(3L, itemRepository.count());
            status.setRollbackOnly();
        });
        assertEquals(1L, itemRepository.count());

        transaction.executeWithoutResult(status -> {
            boxService.delete(project.getOwner().getId(), project.getId(), box.getId());
            assertEquals(0L, itemRepository.count());
            assertFalse(boxRepository.existsById(box.getId()));
            status.setRollbackOnly();
        });
        assertTrue(boxRepository.existsById(box.getId()));
        assertTrue(itemRepository.existsById(preserved.getId()));
    }

    private ItemEntity readyItem(BoxResponse box) {
        return itemRepository.saveAndFlush(ItemEntity.builder().box(boxRepository.findById(box.getId()).orElseThrow())
                .name("Чайник").packedAt(Instant.now()).unpackedAt(Instant.now()).build());
    }

    private MoveProjectEntity project() {
        return moveProjectRepository.saveAndFlush(MoveProjectEntity.builder().owner(userRepository.findByEmail(EMAIL).orElseThrow())
                .name("Переезд").moveDate(Instant.parse("2026-11-01T09:00:00Z")).build());
    }

    private RoomEntity room(MoveProjectEntity parent, String name) {
        return roomRepository.saveAndFlush(RoomEntity.builder().project(parent).name(name).build());
    }

    private BoxResponse create(UUID projectId, UUID roomId, int number, boolean deferred) throws Exception {
        byte[] result = postBox(projectId, body(roomId, number, deferred)).andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION)).andReturn().getResponse().getContentAsByteArray();
        BoxResponse box = jsonMapper.readValue(result, BoxResponse.class);
        assertNotNull(box.getId());
        return box;
    }

    private String body(UUID roomId, int number, boolean deferred) throws Exception {
        return jsonMapper.writeValueAsString(Map.of("roomId", roomId, "number", number, "contentsDeferred", deferred));
    }

    private ResultActions postBox(UUID projectId, String body) throws Exception {
        return mockMvc.perform(post(url(projectId)).header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(UUID boxId, String body) throws Exception {
        return mockMvc.perform(patch(url(project.getId()) + "/" + boxId).header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions changeStatus(UUID boxId, BoxStatus next) throws Exception {
        return mockMvc.perform(patch(url(project.getId()) + "/" + boxId + "/status").header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(Map.of("status", next))));
    }

    private String url(UUID projectId) {
        return "/api/projects/" + projectId + "/boxes";
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
