package ru.example.prodbackend.item.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.checklist.repository.FirstDayChecklistRepository;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.ItemNotFoundException;
import ru.example.prodbackend.exception.ResourceConflictException;
import ru.example.prodbackend.exception.MoveProjectNotFoundException;
import ru.example.prodbackend.move.service.ProjectAccessService;
import ru.example.prodbackend.item.dto.CreateItemRequest;
import ru.example.prodbackend.item.dto.CreateItemsRequest;
import ru.example.prodbackend.item.dto.ItemBoxSuggestionResponse;
import ru.example.prodbackend.item.dto.ItemResponse;
import ru.example.prodbackend.item.dto.UpdateItemRequest;
import ru.example.prodbackend.item.dto.MoveItemRequest;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.repository.ItemRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final BoxRepository boxRepository;
    private final FirstDayChecklistRepository checklistRepository;
    private final ProjectAccessService projectAccessService;

    public ItemResponse findById(UUID userId, UUID projectId, UUID boxId, UUID itemId) {
        requireAccessibleBox(userId, projectId, boxId, false);
        return toResponse(requireItem(boxId, itemId));
    }

    public PageResponse<ItemResponse> findAll(UUID userId, UUID projectId, UUID boxId, PaginationRequest pagination) {
        requireAccessibleBox(userId, projectId, boxId, false);
        Pageable ordered = PageRequest.of(pagination.getPage(), pagination.getSize(),
                Sort.by("createdAt", "id"));
        return PageResponse.from(itemRepository.findAllByBox_Id(boxId, ordered).map(this::toResponse));
    }

    @Transactional
    public ItemResponse create(UUID userId, UUID projectId, UUID boxId, CreateItemRequest request) {
        requireWritableProject(userId, projectId);
        BoxEntity box = requireAccessibleBox(userId, projectId, boxId, true);
        requirePacking(box, "добавлять вещи");

        ItemEntity item = ItemEntity.builder().box(box).name(request.getName().strip())
                .quantity(request.getQuantity()).firstDay(request.getFirstDay()).build();

        return toResponse(itemRepository.saveAndFlush(item));
    }

    @Transactional
    public List<ItemResponse> createBatch(UUID userId, UUID projectId, UUID boxId, CreateItemsRequest request) {
        requireWritableProject(userId, projectId);
        BoxEntity box = requireAccessibleBox(userId, projectId, boxId, true);
        requirePacking(box, "добавлять вещи");

        List<CreateItemRequest> entries = request.getItems() != null ? request.getItems()
                : request.getNames().stream().map(name -> CreateItemRequest.builder().name(name).build()).toList();

        List<ItemEntity> items = entries.stream()
                .map(entry -> ItemEntity.builder().box(box).name(entry.getName().strip())
                        .quantity(entry.getQuantity()).firstDay(entry.getFirstDay()).build()).toList();

        return itemRepository.saveAllAndFlush(items).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ItemResponse update(UUID userId, UUID projectId, UUID boxId, UUID itemId, UpdateItemRequest request) {
        requireWritableProject(userId, projectId);
        BoxEntity box = requireAccessibleBox(userId, projectId, boxId, true);
        ItemEntity item = requireItem(boxId, itemId);
        boolean quantityChanged = request.getQuantity() != null && request.getQuantity() != item.getQuantity();
        if (quantityChanged) requirePacking(box, "изменять количество");
        Instant packedAt = updateMark(request.getPacked(), item.getPackedAt(), box.getStatus(), BoxStatus.PACKING, "упаковки");
        Instant unpackedAt = updateMark(request.getUnpacked(), item.getUnpackedAt(), box.getStatus(), BoxStatus.DELIVERED, "распаковки");
        if (quantityChanged) packedAt = null;
        if (request.getName() != null) item.setName(request.getName().strip());
        if (request.getLost() != null) item.setLost(request.getLost());
        if (request.getQuantity() != null) item.setQuantity(request.getQuantity());
        if (request.getFirstDay() != null) item.setFirstDay(request.getFirstDay());
        if (Boolean.FALSE.equals(request.getFirstDay())) {
            checklistRepository.findForUpdateByItemId(itemId).ifPresent(checklist -> checklist.setItem(null));
        }
        item.setPackedAt(packedAt);
        item.setUnpackedAt(unpackedAt);
        return toResponse(itemRepository.saveAndFlush(item));
    }


    @Transactional
    public ItemResponse move(UUID userId, UUID projectId, UUID sourceBoxId, UUID itemId, MoveItemRequest request) {
        requireWritableProject(userId, projectId);
        UUID targetBoxId = request.getTargetBoxId();
        boolean sourceFirst = sourceBoxId.compareTo(targetBoxId) <= 0;
        // Every transfer locks the pair in the same UUID order, including opposite transfers.
        BoxEntity first = requireAccessibleBox(userId, projectId, sourceFirst ? sourceBoxId : targetBoxId, true);
        BoxEntity second = sourceBoxId.equals(targetBoxId) ? first
                : requireAccessibleBox(userId, projectId, sourceFirst ? targetBoxId : sourceBoxId, true);
        BoxEntity source = sourceFirst ? first : second;
        BoxEntity target = sourceFirst ? second : first;
        ItemEntity item = itemRepository.findAccessibleById(itemId, projectId, userId)
                .orElseThrow(() -> new ItemNotFoundException("Вещь не найдена в этом проекте"));
        UUID currentBoxId = item.getBox().getId();
        if (currentBoxId.equals(targetBoxId)) return toResponse(item);
        if (!currentBoxId.equals(sourceBoxId)) {
            throw new ResourceConflictException("Расположение вещи уже изменилось: она находится в другой коробке");
        }
        requirePacking(source, "переносить вещи");
        requirePacking(target, "переносить вещи");
        item.setBox(target);
        return toResponse(itemRepository.saveAndFlush(item));
    }

    @Transactional
    public void delete(UUID userId, UUID projectId, UUID boxId, UUID itemId) {
        requireWritableProject(userId, projectId);
        BoxEntity box = requireAccessibleBox(userId, projectId, boxId, true);
        ItemEntity item = requireItem(boxId, itemId);
        requirePacking(box, "удалять вещи");
        itemRepository.delete(item);
        itemRepository.flush();
    }

    public PageResponse<ItemResponse> search(UUID userId, UUID projectId, String query, Boolean lost,
                                           PaginationRequest pagination) {
        Pageable ordered = PageRequest.of(pagination.getPage(), pagination.getSize());
        return PageResponse.from(itemRepository.search(projectId, userId, searchPattern(query), lost, ordered)
                .map(this::toResponse));
    }

    public List<ItemBoxSuggestionResponse> suggestions(UUID userId, UUID projectId, UUID itemId, int limit) {
        ItemEntity item = itemRepository.findAccessibleById(itemId, projectId, userId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));
        UUID roomId = item.getBox().getRoom().getId();
        Set<UUID> matchingBoxes = new HashSet<>(itemRepository.findMatchingBoxIds(
                projectId, userId, searchPattern(item.getName())));

        return boxRepository.findSuggestions(projectId, userId, roomId, matchingBoxes, PageRequest.of(0, limit)).stream()
                .map(box -> ItemBoxSuggestionResponse.builder()
                        .boxId(box.getId()).boxNumber(box.getNumber())
                        .roomId(box.getRoom().getId()).roomName(box.getRoom().getName())
                        .roomIconKey(box.getRoom().getIconKey())
                        .status(box.getStatus()).contentsMatch(matchingBoxes.contains(box.getId()))
                        .sameRoom(box.getRoom().getId().equals(roomId)).build())
                .toList();
    }

    private void requirePacking(BoxEntity box, String action) {
        if (box.getStatus() != BoxStatus.PACKING) {
            throw new ResourceConflictException("Можно " + action + " только в коробке со статусом PACKING");
        }
    }

    private Instant updateMark(Boolean requested, Instant current, BoxStatus status, BoxStatus allowed, String mark) {
        if (requested == null || requested == (current != null)) return current;
        if (status != allowed) {
            throw new ResourceConflictException("Изменять отметку " + mark + " можно только в статусе " + allowed);
        }
        // PostgreSQL stores microseconds: return the same timestamp before and after reloading.
        return requested ? Instant.now().truncatedTo(ChronoUnit.MICROS) : null;
    }

    private void requireWritableProject(UUID userId, UUID projectId) {
        try {
            projectAccessService.getAccessibleProjectForUpdate(projectId, userId);
        } catch (MoveProjectNotFoundException exception) {
            // Preserve the existing item API's generic 404 for unavailable resources.
            throw new ItemNotFoundException("Box not found");
        }
    }

    private BoxEntity requireAccessibleBox(UUID userId, UUID projectId, UUID boxId, boolean forUpdate) {
        BoxEntity box = (forUpdate ? boxRepository.findForUpdate(boxId, projectId)
                : boxRepository.findByIdAndProject_Id(boxId, projectId))
                .orElseThrow(() -> new ItemNotFoundException("Box not found"));
        if (!forUpdate) {
            try {
                projectAccessService.getAccessibleProject(projectId, userId);
            } catch (MoveProjectNotFoundException exception) {
                throw new ItemNotFoundException("Box not found");
            }
        }
        return box;
    }

    private ItemEntity requireItem(UUID boxId, UUID itemId) {
        return itemRepository.findByIdAndBox_Id(itemId, boxId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));
    }

    private String searchPattern(String query) {
        String escaped = query.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private ItemResponse toResponse(ItemEntity item) {
        BoxEntity box = item.getBox();
        return ItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .lost(item.isLost())
                .packed(item.getPackedAt() != null).packedAt(item.getPackedAt())
                .unpacked(item.getUnpackedAt() != null).unpackedAt(item.getUnpackedAt())
                .quantity(item.getQuantity())
                .firstDay(item.isFirstDay())
                .projectId(box.getProject().getId())
                .boxId(box.getId())
                .boxNumber(box.getNumber())
                .roomId(box.getRoom().getId())
                .roomName(box.getRoom().getName())
                .roomIconKey(box.getRoom().getIconKey())
                .boxStatus(box.getStatus())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
