package ru.example.prodbackend.box.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.box.dto.BoxFilter;
import ru.example.prodbackend.box.dto.BoxResponse;
import ru.example.prodbackend.box.dto.BoxSummaryResponse;
import ru.example.prodbackend.box.dto.CreateBoxRequest;
import ru.example.prodbackend.box.dto.RoomBoxSummaryResponse;
import ru.example.prodbackend.box.dto.UpdateBoxRequest;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.event.service.ProjectEventService;
import ru.example.prodbackend.exception.BoxNotFoundException;
import ru.example.prodbackend.exception.RequestValidationException;
import ru.example.prodbackend.exception.ResourceConflictException;
import ru.example.prodbackend.exception.RoomNotFoundException;
import ru.example.prodbackend.item.repository.ItemRepository;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.room.repository.RoomRepository;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoxService {

    private final BoxRepository boxRepository;
    private final ItemRepository itemRepository;
    private final ProjectAccessService projectAccessService;
    private final RoomRepository roomRepository;
    private final ProjectEventService projectEventService;

    @Transactional
    public BoxResponse create(UUID userId, UUID projectId, CreateBoxRequest request) {
        var project = projectAccessService.getOwnedProject(projectId, userId);
        RoomEntity room = requireProjectRoom(request.getRoomId(), projectId);

        if (request.getNumber() == null || request.getNumber() <= 0) {
            throw new RequestValidationException("number", "Номер коробки должен быть положительным");
        }
        if (boxRepository.existsByProject_IdAndNumber(projectId, request.getNumber())) {
            throw new ResourceConflictException("Номер коробки уже занят в этом проекте");
        }

        BoxEntity box = BoxEntity.builder().project(project).room(room).number(request.getNumber())
                .contentsDeferred(request.isContentsDeferred()).build();
        boxRepository.saveAndFlush(box);
        recordEvent(box, ProjectEventType.BOX_CREATED, "Создана коробка №" + box.getNumber() + ", комната: " + room.getName());
        return toResponse(box);
    }

    public PageResponse<BoxResponse> findAll(UUID userId, UUID projectId, BoxFilter filter, PaginationRequest pagination) {
        projectAccessService.getOwnedProject(projectId, userId);

        PageRequest pageable = PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by("number", "id"));

        Page<BoxEntity> boxes = boxRepository.findAllByFilters(projectId, filter.getNumber(), filter.getRoomId(), filter.getStatus(), filter.getContentsDeferred(), pageable);
        return PageResponse.from(boxes.map(this::toResponse));
    }

    public BoxResponse findById(UUID userId, UUID projectId, UUID boxId) {
        return toResponse(requireOwnedBox(userId, projectId, boxId, false));
    }

    @Transactional
    public BoxResponse update(UUID userId, UUID projectId, UUID boxId, UpdateBoxRequest request) {
        BoxEntity box = requireOwnedBox(userId, projectId, boxId, true);

        if (!request.isUpdatePresent()) {
            throw new RequestValidationException("request", "Укажите хотя бы одно поле для изменения");
        }

        RoomEntity room = box.getRoom();
        if (request.getRoomId() != null && !request.getRoomId().equals(room.getId())) {
            room = requireProjectRoom(request.getRoomId(), projectId);
        }

        if (request.getNumber() != null) {
            if (request.getNumber() <= 0) {
                throw new RequestValidationException("number", "Номер коробки должен быть положительным");
            }

            if (boxRepository.existsByProject_IdAndNumberAndIdNot(projectId, request.getNumber(), boxId)) {
                throw new ResourceConflictException("Номер коробки уже занят в этом проекте");
            }

            box.setNumber(request.getNumber());
        }

        box.setRoom(room);
        if (request.getContentsDeferred() != null) {
            box.setContentsDeferred(request.getContentsDeferred());
        }

        boxRepository.saveAndFlush(box);
        return toResponse(box);
    }

    @Transactional
    public BoxResponse changeStatus(UUID userId, UUID projectId, UUID boxId, BoxStatus status) {
        BoxEntity box = requireOwnedBox(userId, projectId, boxId, true);
        BoxStatus previousStatus = box.getStatus();

        if (status == null) {
            throw new RequestValidationException("status", "Статус коробки обязателен");
        }

        if (!previousStatus.canTransitionTo(status)) {
            throw new ResourceConflictException("Недопустимый переход статуса: " + previousStatus + " -> " + status);
        }

        if (previousStatus == status) {
            return toResponse(box);
        }

        box.changeStatus(status);
        boxRepository.saveAndFlush(box);

        recordEvent(box, ProjectEventType.BOX_STATUS_CHANGED, "Коробка №" + box.getNumber() + ": " + previousStatus + " → " + status);
        return toResponse(box);
    }

    @Transactional
    public void delete(UUID userId, UUID projectId, UUID boxId) {
        BoxEntity box = requireOwnedBox(userId, projectId, boxId, true);

        itemRepository.deleteByBox_Id(boxId);
        boxRepository.delete(box);
        boxRepository.flush();
    }

    public BoxSummaryResponse summary(UUID userId, UUID projectId) {
        projectAccessService.getOwnedProject(projectId, userId);

        List<BoxEntity> boxes =
                boxRepository.findAllByProject_IdOrderByNumberAscIdAsc(projectId);

        Map<UUID, List<BoxEntity>> boxesByRoom = boxes.stream()
                .collect(Collectors.groupingBy(
                        box -> box.getRoom().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<RoomBoxSummaryResponse> rooms = boxesByRoom.values().stream()
                .map(this::toRoomSummary)
                .toList();

        return BoxSummaryResponse.builder()
                .totalBoxes(boxes.size())
                .countsByStatus(countByStatus(boxes))
                .deferredContentsCount(boxes.stream()
                        .filter(BoxEntity::isContentsDeferred)
                        .count())
                .rooms(rooms)
                .build();
    }

    private BoxEntity requireOwnedBox(UUID userId, UUID projectId, UUID boxId, boolean forUpdate) {
        projectAccessService.getOwnedProject(projectId, userId);
        var box = forUpdate ? boxRepository.findForUpdate(boxId, projectId) : boxRepository.findByIdAndProject_Id(boxId, projectId);
        return box.orElseThrow(BoxNotFoundException::new);
    }

    private RoomEntity requireProjectRoom(UUID roomId, UUID projectId) {
        if (roomId == null) {
            throw new RequestValidationException("roomId", "Комната обязательна");
        }
        // Та же блокировка используется при удалении комнаты: её нельзя удалить во время назначения коробке.
        return roomRepository.findForUpdate(roomId, projectId).orElseThrow(() -> new RoomNotFoundException("Комната не найдена в этом проекте"));
    }

    private void recordEvent(BoxEntity box, ProjectEventType type, String description) {
        // Доступ к коробке разрешён только владельцу проекта; событие сохраняется в транзакции изменения коробки.
        projectEventService.record(box.getProject(), box.getProject().getOwner(), type, EventObjectType.BOX, box.getId(), description);
    }

    private BoxResponse toResponse(BoxEntity box) {
        return BoxResponse.builder()
                .id(box.getId())
                .projectId(box.getProject().getId())
                .number(box.getNumber())
                .roomId(box.getRoom().getId())
                .roomName(box.getRoom().getName())
                .status(box.getStatus())
                .contentsDeferred(box.isContentsDeferred())
                .createdAt(box.getCreatedAt())
                .updatedAt(box.getUpdatedAt())
                .build();
    }

    private RoomBoxSummaryResponse toRoomSummary(List<BoxEntity> boxes) {
        RoomEntity room = boxes.get(0).getRoom();
        Map<BoxStatus, Long> counts = countByStatus(boxes);
        long unpackedBoxes = counts.get(BoxStatus.UNPACKED);

        return RoomBoxSummaryResponse.builder()
                .roomId(room.getId())
                .roomName(room.getName())
                .totalBoxes(boxes.size())
                .countsByStatus(counts)
                .unpackedBoxes(unpackedBoxes)
                .unpackingProgressPercent((int) Math.round(
                        100.0 * unpackedBoxes / boxes.size()))
                .build();
    }

    private Map<BoxStatus, Long> countByStatus(List<BoxEntity> boxes) {
        Map<BoxStatus, Long> counts = new EnumMap<>(BoxStatus.class);

        for (BoxStatus status : BoxStatus.values()) {
            counts.put(status, 0L);
        }

        for (BoxEntity box : boxes) {
            counts.merge(box.getStatus(), 1L, Long::sum);
        }

        return counts;
    }
}
