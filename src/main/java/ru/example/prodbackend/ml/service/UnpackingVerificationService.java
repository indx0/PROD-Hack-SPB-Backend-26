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
    private final MlRateLimiter rateLimiter;

    public UnpackingVerificationResponse verify(UUID userId, UUID projectId, UUID boxId, MultipartFile photo) {
        var inventory = inventories.snapshot(userId, projectId, boxId);
        var validated = photos.validate(photo);
        rateLimiter.acquire(userId);
        var observed = client.verifyUnpacking(validated.getBytes(), validated.getMime(), inventory);
        var byId = inventory.stream().collect(Collectors.toMap(item -> item.getItemId(), Function.identity()));
        var matched = new ArrayList<MatchedItem>();
        var unmatched = new ArrayList<String>();
        var seen = new HashSet<UUID>();
        for (var object : observed) {
            if (object.getItemId() == null) {
                unmatched.add(object.getPhotoDescription());
            } else {
                var item = byId.get(object.getItemId());
                seen.add(item.getItemId());
                matched.add(new MatchedItem(item.getItemId(), item.getName(), object.getPhotoDescription()));
            }
        }
        var notSeen = inventory.stream().filter(item -> !seen.contains(item.getItemId())).toList();
        return new UnpackingVerificationResponse(boxId, List.copyOf(matched), notSeen, List.copyOf(unmatched));
    }
}
