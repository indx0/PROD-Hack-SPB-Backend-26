package ru.example.prodbackend.ml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.UnpackingVerificationResponse;
import ru.example.prodbackend.ml.dto.UnpackingVerificationResponse.MatchedItem;
import ru.example.prodbackend.ml.validation.PhotoValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnpackingVerificationService {
    private final UnpackingInventoryService inventories;
    private final PhotoValidator photos;
    private final LlmProxyClient client;

    public UnpackingVerificationResponse verify(UUID userId, UUID projectId, UUID boxId, MultipartFile photo) {
        // The snapshot transaction ends before the external request; no managed entities escape it.
        var inventory = inventories.snapshot(userId, projectId, boxId);
        var validated = photos.validate(photo);
        var observed = client.verifyUnpacking(validated.bytes(), validated.mime(), inventory);
        var byId = inventory.stream().collect(Collectors.toMap(item -> item.itemId(), Function.identity()));
        var matched = new ArrayList<MatchedItem>();
        var unmatched = new ArrayList<String>();
        var seen = new HashSet<UUID>();
        for (var object : observed) {
            if (object.itemId() == null) {
                unmatched.add(object.photoDescription());
            } else {
                var item = byId.get(object.itemId());
                seen.add(item.itemId());
                matched.add(new MatchedItem(item.itemId(), item.name(), object.photoDescription()));
            }
        }
        var notSeen = inventory.stream().filter(item -> !seen.contains(item.itemId())).toList();
        return new UnpackingVerificationResponse(boxId, List.copyOf(matched), notSeen, List.copyOf(unmatched));
    }
}
