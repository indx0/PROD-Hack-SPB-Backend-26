package ru.example.prodbackend.box.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.example.prodbackend.box.dto.BoxFilter;
import ru.example.prodbackend.box.dto.BoxResponse;
import ru.example.prodbackend.box.dto.BoxSummaryResponse;
import ru.example.prodbackend.box.dto.CreateBoxRequest;
import ru.example.prodbackend.box.dto.RoomBoxSummaryResponse;
import ru.example.prodbackend.box.dto.UpdateBoxRequest;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.item.repository.ItemRepository;
import ru.example.prodbackend.room.entity.RoomEntity;

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

    /*
     * Репозитории для полной реализации (TODO: MoveProject, Room, ProjectEvent):
     *
     * 1. MoveProjectRepository: findByIdAndOwner_Id(projectId, userId).
     *    Загрузка проекта и проверка владельца для создания, списка и статистики.
     *
     * 2. RoomRepository: findById(roomId).
     *    Загрузка комнаты для создания коробки и смены комнаты;
     *    принадлежность комнаты проекту проверяется в BoxService.
     *
     * 4. ProjectEventRepository: save(ProjectEventEntity).
     *    Запись BOX_CREATED / BOX_STATUS_CHANGED в общей транзакции BoxService;
     *    повтор текущего статуса не создаёт событие.
     *
     * 5. BoxRepository (уже есть): сохранение, поиск, фильтрация,
     *    проверка уникальности номера и блокировка коробки.
     */
    private final BoxRepository boxRepository;
    private final ItemRepository itemRepository;

    @Transactional
    public BoxResponse create(
            UUID userId,
            UUID projectId,
            CreateBoxRequest request
    ) {
        // TODO Move/Room: load the owned project and a room belonging to it.
        // TODO Box: check number uniqueness, then save a box with status PACKING.
        // TODO Event: save BOX_CREATED in the same transaction.
        throw new UnsupportedOperationException(
                "Box creation requires project and room integration"
        );
    }

    public Page<BoxResponse> findAll(
            UUID userId,
            UUID projectId,
            BoxFilter filter,
            Pageable pageable
    ) {
        requireProjectAccess(userId, projectId);

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.asc("number"), Sort.Order.asc("id"))
        );

        return boxRepository.findAllByFilters(
                projectId,
                filter.getNumber(),
                filter.getRoomId(),
                filter.getStatus(),
                filter.getContentsDeferred(),
                sortedPageable
        ).map(this::toResponse);
    }

    public BoxResponse findById(UUID userId, UUID projectId, UUID boxId) {
        return toResponse(requireOwnedBox(userId, projectId, boxId, false));
    }

    @Transactional
    public BoxResponse update(
            UUID userId,
            UUID projectId,
            UUID boxId,
            UpdateBoxRequest request
    ) {
        BoxEntity box = requireOwnedBox(userId, projectId, boxId, true);

        if (!request.isUpdatePresent()) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(422), "Specify at least one field to update"
            );
        }

        if (request.getRoomId() != null
                && !request.getRoomId().equals(box.getRoom().getId())) {
            // TODO Room: load the requested room and verify its project before assigning it.
            throw new UnsupportedOperationException(
                    "Changing the room requires room integration"
            );
        }

        if (request.getNumber() != null) {
            if (request.getNumber() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.valueOf(422), "Box number must be positive"
                );
            }

            if (boxRepository.existsByProject_IdAndNumberAndIdNot(
                    projectId, request.getNumber(), boxId
            )) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Box number already exists in this project"
                );
            }

            box.setNumber(request.getNumber());
        }

        if (request.getContentsDeferred() != null) {
            box.setContentsDeferred(request.getContentsDeferred());
        }

        boxRepository.saveAndFlush(box);
        return toResponse(box);
    }

    @Transactional
    public BoxResponse changeStatus(
            UUID userId,
            UUID projectId,
            UUID boxId,
            BoxStatus status
    ) {
        BoxEntity box = requireOwnedBox(userId, projectId, boxId, true);
        BoxStatus previousStatus = box.getStatus();

        if (status == null) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(422), "Box status is required"
            );
        }

        if (!previousStatus.canTransitionTo(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid box status transition: " + previousStatus + " -> " + status
            );
        }

        if (previousStatus == status) {
            return toResponse(box);
        }

        box.changeStatus(status);
        boxRepository.saveAndFlush(box);

        // TODO Event: save BOX_STATUS_CHANGED here in the same transaction.
        // Do not create an event for an idempotent repeat of the current status.
        return toResponse(box);
    }

    @Transactional
    public void delete(UUID userId, UUID projectId, UUID boxId) {
        BoxEntity box = requireOwnedBox(userId, projectId, boxId, true);

        itemRepository.deleteByBox_Id(boxId);
        itemRepository.flush();
        boxRepository.delete(box);
        boxRepository.flush();
    }

    public BoxSummaryResponse summary(UUID userId, UUID projectId) {
        requireProjectAccess(userId, projectId);

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

    private void requireProjectAccess(UUID userId, UUID projectId) {
        // TODO Move: verify project existence and owner.id == userId; otherwise return 404.
        // A box query cannot distinguish an empty project from a missing/foreign project.
        throw new UnsupportedOperationException(
                "Project access verification requires move integration"
        );
    }

    private BoxEntity requireOwnedBox(
            UUID userId,
            UUID projectId,
            UUID boxId,
            boolean forUpdate
    ) {
        BoxEntity box = (forUpdate
                ? boxRepository.findForUpdate(boxId, projectId)
                : boxRepository.findByIdAndProject_Id(boxId, projectId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Box not found"
                ));

        if (!box.getProject().getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Box not found");
        }

        return box;
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
