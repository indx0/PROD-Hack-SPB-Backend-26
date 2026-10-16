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
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.ItemNotFoundException;
import ru.example.prodbackend.item.dto.CreateItemRequest;
import ru.example.prodbackend.item.dto.CreateItemsRequest;
import ru.example.prodbackend.item.dto.ItemBoxSuggestionResponse;
import ru.example.prodbackend.item.dto.ItemResponse;
import ru.example.prodbackend.item.dto.UpdateItemRequest;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.repository.ItemRepository;

import java.util.Comparator;
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

    public PageResponse<ItemResponse> findAll(UUID userId, UUID projectId, UUID boxId, PaginationRequest pagination) {
        requireOwnedBox(userId, projectId, boxId, false);
        Pageable ordered = PageRequest.of(pagination.getPage(), pagination.getSize(),
                Sort.by("createdAt", "id"));
        return PageResponse.from(itemRepository.findAllByBox_Id(boxId, ordered).map(this::toResponse));
    }

    @Transactional
    public ItemResponse create(UUID userId, UUID projectId, UUID boxId, CreateItemRequest request) {
        BoxEntity box = requireOwnedBox(userId, projectId, boxId, true);
        ItemEntity item = ItemEntity.builder().box(box).name(request.getName().strip()).build();
        return toResponse(itemRepository.saveAndFlush(item));
    }

    @Transactional
    public List<ItemResponse> createBatch(UUID userId, UUID projectId, UUID boxId, CreateItemsRequest request) {
        BoxEntity box = requireOwnedBox(userId, projectId, boxId, true);
        List<ItemEntity> items = request.getNames().stream()
                .map(name -> ItemEntity.builder().box(box).name(name.strip()).build())
                .toList();
        return itemRepository.saveAllAndFlush(items).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ItemResponse update(UUID userId, UUID projectId, UUID boxId, UUID itemId, UpdateItemRequest request) {
        requireOwnedBox(userId, projectId, boxId, true);
        ItemEntity item = requireItem(boxId, itemId);
        if (request.getName() != null) {
            item.setName(request.getName().strip());
        }
        if (request.getLost() != null) {
            item.setLost(request.getLost());
        }
        return toResponse(itemRepository.saveAndFlush(item));
    }

    @Transactional
    public void delete(UUID userId, UUID projectId, UUID boxId, UUID itemId) {
        requireOwnedBox(userId, projectId, boxId, true);
        itemRepository.delete(requireItem(boxId, itemId));
        itemRepository.flush();
    }

    public PageResponse<ItemResponse> search(UUID userId, UUID projectId, String query, Boolean lost,
                                           PaginationRequest pagination) {
        Pageable ordered = PageRequest.of(pagination.getPage(), pagination.getSize());
        return PageResponse.from(itemRepository.search(projectId, userId, searchPattern(query), lost, ordered)
                .map(this::toResponse));
    }

    public List<ItemBoxSuggestionResponse> suggestions(UUID userId, UUID projectId, UUID itemId, int limit) {
        ItemEntity item = itemRepository.findByIdAndBoxProject_IdAndBoxProject_OwnerId(itemId, projectId, userId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));
        UUID roomId = item.getBox().getRoom().getId();
        Set<UUID> matchingBoxes = new HashSet<>(itemRepository.findMatchingBoxIds(
                projectId, userId, searchPattern(item.getName())));

        return boxRepository.findAllByProject_IdOrderByNumberAscIdAsc(projectId).stream()
                .filter(box -> box.getStatus() != BoxStatus.UNPACKED)
                .filter(box -> matchingBoxes.contains(box.getId()) || box.getRoom().getId().equals(roomId))
                .map(box -> ItemBoxSuggestionResponse.builder()
                        .boxId(box.getId()).boxNumber(box.getNumber())
                        .roomId(box.getRoom().getId()).roomName(box.getRoom().getName())
                        .status(box.getStatus()).contentsMatch(matchingBoxes.contains(box.getId()))
                        .sameRoom(box.getRoom().getId().equals(roomId)).build())
                .sorted(Comparator.comparing(ItemBoxSuggestionResponse::isContentsMatch).reversed()
                        .thenComparing(Comparator.comparing(ItemBoxSuggestionResponse::isSameRoom).reversed())
                        .thenComparingInt(ItemBoxSuggestionResponse::getBoxNumber)
                        .thenComparing(ItemBoxSuggestionResponse::getBoxId))
                .limit(limit)
                .toList();
    }

    private BoxEntity requireOwnedBox(UUID userId, UUID projectId, UUID boxId, boolean forUpdate) {
        BoxEntity box = (forUpdate ? boxRepository.findForUpdate(boxId, projectId)
                : boxRepository.findByIdAndProject_Id(boxId, projectId))
                .orElseThrow(() -> new ItemNotFoundException("Box not found"));
        if (!box.getProject().getOwner().getId().equals(userId)) {
            throw new ItemNotFoundException("Box not found");
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
        return ItemResponse.builder().id(item.getId()).name(item.getName()).lost(item.isLost())
                .projectId(box.getProject().getId()).boxId(box.getId()).boxNumber(box.getNumber())
                .roomId(box.getRoom().getId()).roomName(box.getRoom().getName()).boxStatus(box.getStatus())
                .createdAt(item.getCreatedAt()).updatedAt(item.getUpdatedAt()).build();
    }
}
