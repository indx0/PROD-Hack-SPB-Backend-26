package ru.example.prodbackend.ml.client;

import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;

public interface LlmProxyClient {
    ItemDescriptionResponse describe(byte[] image, String mime);
}
