package ru.example.prodbackend.ml.client;

import ru.example.prodbackend.ml.dto.InventoryItem;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.dto.ItemsDescriptionResponse;
import ru.example.prodbackend.ml.dto.ObservedItem;
import java.util.List;

public interface LlmProxyClient {
    ItemDescriptionResponse describe(byte[] image, String mime);
    ItemsDescriptionResponse describeItems(byte[] image, String mime);
    List<ObservedItem> verifyUnpacking(byte[] image, String mime, List<InventoryItem> inventory);
}
