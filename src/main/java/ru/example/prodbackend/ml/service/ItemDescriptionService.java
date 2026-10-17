package ru.example.prodbackend.ml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.dto.ItemsDescriptionResponse;
import ru.example.prodbackend.ml.validation.PhotoValidator;

@Service
@RequiredArgsConstructor
public class ItemDescriptionService {
    private final PhotoValidator photos;
    private final LlmProxyClient client;

    public ItemDescriptionResponse describe(MultipartFile photo) {
        var validated = photos.validate(photo);
        return client.describe(validated.bytes(), validated.mime());
    }

    public ItemsDescriptionResponse describeItems(MultipartFile photo) {
        var validated = photos.validate(photo);
        return client.describeItems(validated.bytes(), validated.mime());
    }

}
