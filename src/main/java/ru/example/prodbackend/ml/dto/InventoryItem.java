package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Immutable entry from the inventory snapshot, also used in the LLM request. */
public record InventoryItem(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор вещи")
        UUID itemId,
        @Schema(example = "Посуда", description = "Название")
        String name) { }
