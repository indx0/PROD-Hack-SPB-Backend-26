package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A visible physical object; null itemId means no confident inventory match. */
public record ObservedItem(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор вещи")
        UUID itemId,
        @Schema(example = "Белая керамическая кружка", description = "Описание предмета на фотографии")
        String photoDescription) { }
