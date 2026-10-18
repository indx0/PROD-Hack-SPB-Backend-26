package ru.example.prodbackend.ml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.dto.ItemsDescriptionResponse;
import ru.example.prodbackend.ml.validation.PhotoValidator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItemDescriptionService {
    private final PhotoValidator photos;
    private final LlmProxyClient client;
    private final MlRateLimiter rateLimiter;

    public ItemDescriptionResponse describe(UUID userId, MultipartFile photo) {
        var validated = photos.validate(photo);
        rateLimiter.acquire(userId);
        return client.describe(validated.getBytes(), validated.getMime());
    }

    public ItemsDescriptionResponse describeItems(UUID userId, MultipartFile photo) {
        var validated = photos.validate(photo);
        rateLimiter.acquire(userId);
        return client.describeItems(validated.getBytes(), validated.getMime());
    }

}
