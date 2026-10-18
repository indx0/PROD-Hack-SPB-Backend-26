package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Value;

/** Immutable entry from the inventory snapshot, also used in the LLM request. */
@Value
public class InventoryItem {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор вещи")
    private final UUID itemId;

    @Schema(example = "Посуда", description = "Название")
    private final String name;
}
