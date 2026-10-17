package ru.example.prodbackend.ml.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.example.prodbackend.ml.dto.InventoryItem;
import ru.example.prodbackend.ml.dto.ObservedItem;
import ru.example.prodbackend.ml.exception.MlException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UnpackingVerificationValidator {
    private final ItemDescriptionValidator envelopes;

    public List<ObservedItem> validate(String body, List<InventoryItem> inventory) {
        var result = envelopes.content(body);
        if (!result.isObject() || result.size() != 1 || !result.path("items").isArray()
                || result.path("items").size() > ItemDescriptionValidator.MAX_ITEMS) throw invalid();
        if (result.path("items").isEmpty()) throw new MlException(HttpStatus.UNPROCESSABLE_CONTENT,
                "IMAGE_NOT_RECOGNIZED", "Не удалось распознать предметы на фотографии");
        var byId = inventory.stream().collect(Collectors.toMap(InventoryItem::itemId, Function.identity()));
        var rawIds = new HashSet<UUID>();
        var assigned = new HashSet<UUID>();
        var observed = new ArrayList<ObservedItem>();
        for (var node : result.path("items")) {
            if (!node.isObject() || node.size() != 2 || !node.has("itemId")
                    || !node.path("photoDescription").isString()) throw invalid();
            String description = node.path("photoDescription").asText().strip();
            if (description.isEmpty() || description.length() > 255) throw invalid();
            UUID id = null;
            if (!node.path("itemId").isNull()) {
                if (!node.path("itemId").isString()) throw invalid();
                String value = node.path("itemId").asText();
                try { id = UUID.fromString(value); }
                catch (IllegalArgumentException e) { throw invalid(); }
                if (!id.toString().equalsIgnoreCase(value) || !byId.containsKey(id) || !rawIds.add(id)) throw invalid();
                String name = byId.get(id).name();
                id = inventory.stream().filter(item -> item.name().equals(name) && !assigned.contains(item.itemId()))
                        .map(InventoryItem::itemId).findFirst().orElseThrow(this::invalid);
                assigned.add(id);
            }
            observed.add(new ObservedItem(id, description));
        }
        return List.copyOf(observed);
    }

    private MlException invalid() {
        return new MlException(HttpStatus.BAD_GATEWAY, "LLM_INVALID_RESPONSE", "Модель вернула некорректный ответ");
    }
}
