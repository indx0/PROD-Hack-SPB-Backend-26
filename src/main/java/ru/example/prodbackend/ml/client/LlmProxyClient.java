package ru.example.prodbackend.ml.client;

import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.dto.ItemsDescriptionResponse;

public interface LlmProxyClient {
    ItemDescriptionResponse describe(byte[] image, String mime);
    ItemsDescriptionResponse describeItems(byte[] image, String mime);
}
