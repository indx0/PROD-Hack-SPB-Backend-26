package ru.example.prodbackend.ml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.exception.BoxNotFoundException;
import ru.example.prodbackend.item.repository.ItemRepository;
import ru.example.prodbackend.ml.dto.InventoryItem;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.validation.ItemDescriptionValidator;
import ru.example.prodbackend.move.service.ProjectAccessService;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnpackingInventoryService {
    private final ProjectAccessService access;
    private final BoxRepository boxes;
    private final ItemRepository items;

    @Transactional(readOnly = true)
    public List<InventoryItem> snapshot(UUID userId, UUID projectId, UUID boxId) {
        access.getAccessibleProject(projectId, userId);
        boxes.findByIdAndProject_Id(boxId, projectId).orElseThrow(BoxNotFoundException::new);
        var page = items.findAllByBox_Id(boxId, PageRequest.of(0,
                ItemDescriptionValidator.MAX_ITEMS + 1, Sort.by("createdAt", "id")));
        if (page.isEmpty()) throw new MlException(HttpStatus.UNPROCESSABLE_CONTENT,
                "EMPTY_INVENTORY", "Для сверки заполните опись коробки");
        if (page.getTotalElements() > ItemDescriptionValidator.MAX_ITEMS) {
            throw new MlException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVENTORY_TOO_LARGE", "Сверка поддерживает описи до 100 вещей");
        }
        return page.stream().map(item -> new InventoryItem(item.getId(), item.getName())).toList();
    }
}
