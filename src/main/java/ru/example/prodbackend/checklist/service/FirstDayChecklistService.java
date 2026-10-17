package ru.example.prodbackend.checklist.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.checklist.dto.request.CreateChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.request.UpdateChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.response.ChecklistItemResponse;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.checklist.repository.FirstDayChecklistRepository;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.ChecklistItemNotFoundException;
import ru.example.prodbackend.exception.RequestValidationException;
import ru.example.prodbackend.exception.ItemNotFoundException;
import ru.example.prodbackend.exception.ResourceConflictException;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.repository.ItemRepository;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.service.ProjectAccessService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FirstDayChecklistService {

    private final FirstDayChecklistRepository checklistRepository;
    private final ProjectAccessService projectAccessService;
    private final ItemRepository itemRepository;
    private final BoxRepository boxRepository;

    @Transactional
    public ChecklistItemResponse create(UUID projectId, CreateChecklistItemRequest request, UUID userId) {
        var project = projectAccessService.getOwnedProject(projectId, userId);
        ItemEntity linked = request.getItemId() == null ? null : requireLinkedItem(projectId, request.getItemId(), userId);
        if (linked != null && checklistRepository.existsByItem_Id(linked.getId())) {
            throw new ResourceConflictException("Вещь уже связана с другим пунктом чек-листа");
        }
        var item = FirstDayChecklistItemEntity.builder().project(project).name(normalizeName(request.getName())).build();
        item.setItem(linked);
        if (linked != null) linked.setFirstDay(true);
        return toResponse(checklistRepository.saveAndFlush(item));
    }

    @Transactional
    public ChecklistItemResponse link(UUID projectId, UUID checklistItemId, UUID linkedItemId, UUID userId) {
        projectAccessService.getOwnedProject(projectId, userId);
        // The box lock must precede the checklist lock, as in item updates and deletes.
        ItemEntity linked = requireLinkedItem(projectId, linkedItemId, userId);
        var item = checklistRepository.findForUpdate(checklistItemId, projectId).orElseThrow(ChecklistItemNotFoundException::new);
        if (checklistRepository.existsByItem_IdAndIdNot(linked.getId(), checklistItemId)) {
            throw new ResourceConflictException("Вещь уже связана с другим пунктом чек-листа");
        }
        item.setItem(linked);
        linked.setFirstDay(true);
        return toResponse(checklistRepository.saveAndFlush(item));
    }

    @Transactional
    public ChecklistItemResponse unlink(UUID projectId, UUID checklistItemId, UUID userId) {
        var item = requireItem(projectId, checklistItemId, userId);
        item.setItem(null);
        return toResponse(checklistRepository.saveAndFlush(item));
    }

    public PageResponse<ChecklistItemResponse> findAll(UUID projectId, PaginationRequest pagination, UUID userId) {
        projectAccessService.getOwnedProject(projectId, userId);
        var pageable = PageRequest.of(pagination.getPage(), pagination.getSize(), Sort.by("createdAt", "id"));
        return PageResponse.from(checklistRepository.findAllByProject_Id(projectId, pageable).map(this::toResponse));
    }

    @Transactional
    public ChecklistItemResponse update(UUID projectId, UUID itemId, UpdateChecklistItemRequest request, UUID userId) {
        var item = requireItem(projectId, itemId, userId);
        if (request.getName() != null) item.setName(normalizeName(request.getName()));
        if (request.getCompleted() != null && (item.getCompletedAt() != null) != request.getCompleted()) {
            item.setCompletedAt(request.getCompleted() ? Instant.now() : null);
        }
        return toResponse(checklistRepository.saveAndFlush(item));
    }

    @Transactional
    public void delete(UUID projectId, UUID itemId, UUID userId) {
        var item = requireItem(projectId, itemId, userId);
        checklistRepository.delete(item);
        checklistRepository.flush();
    }

    /** Только для нового проекта и в его транзакции создания. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createDefaults(MoveProjectEntity project) {
        checklistRepository.saveAll(List.of("Зубные щётки", "Зарядки", "Чайник", "Постельное бельё").stream()
                .map(name -> FirstDayChecklistItemEntity.builder().project(project).name(name).build()).toList());
    }

    private FirstDayChecklistItemEntity requireItem(UUID projectId, UUID itemId, UUID userId) {
        projectAccessService.getOwnedProject(projectId, userId);
        return checklistRepository.findForUpdate(itemId, projectId).orElseThrow(ChecklistItemNotFoundException::new);
    }

    private ItemEntity requireLinkedItem(UUID projectId, UUID itemId, UUID userId) {
        // Read only the box ID before locking; load the item afterwards to avoid stale firstDay state.
        UUID boxId = itemRepository.findOwnedBoxId(itemId, projectId, userId)
                .orElseThrow(() -> new ItemNotFoundException("Вещь не найдена в этом проекте"));
        boxRepository.findForUpdate(boxId, projectId).orElseThrow(() -> new ItemNotFoundException("Коробка не найдена"));
        return itemRepository.findByIdAndBox_Id(itemId, boxId).orElseThrow(() -> {
            if (itemRepository.findOwnedBoxId(itemId, projectId, userId).isPresent()) {
                // Do not acquire the new box after holding the old one: that would break transfer lock ordering.
                return new ResourceConflictException("Вещь перемещена в другую коробку; повторите привязку");
            }
            return new ItemNotFoundException("Вещь не найдена");
        });
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isBlank()) throw new RequestValidationException("name", "Название пункта не должно быть пустым");
        return normalized;
    }

    private ChecklistItemResponse toResponse(FirstDayChecklistItemEntity item) {
        var response = ChecklistItemResponse.builder().id(item.getId()).projectId(item.getProject().getId()).name(item.getName())
                .completed(item.getCompletedAt() != null).completedAt(item.getCompletedAt())
                .createdAt(item.getCreatedAt()).updatedAt(item.getUpdatedAt());
        if (item.getItem() != null) {
            var linked = item.getItem();
            var box = linked.getBox();
            response.itemId(linked.getId()).itemName(linked.getName()).boxId(box.getId()).boxNumber(box.getNumber())
                    .roomId(box.getRoom().getId()).roomName(box.getRoom().getName()).roomIconKey(box.getRoom().getIconKey());
        }
        return response.build();
    }
}
