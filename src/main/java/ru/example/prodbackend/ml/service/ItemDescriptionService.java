package ru.example.prodbackend.ml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.validation.PhotoValidator;
import ru.example.prodbackend.move.service.ProjectAccessService;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItemDescriptionService {
    private final ProjectAccessService access;
    private final PhotoValidator photos;
    private final LlmProxyClient client;

    public ItemDescriptionResponse describe(UUID userId, UUID projectId, MultipartFile photo) {
        // The access service's read transaction ends before waiting for the external API.
        access.getOwnedProject(projectId, userId);
        var validated = photos.validate(photo);
        return client.describe(validated.bytes(), validated.mime());
    }
}
