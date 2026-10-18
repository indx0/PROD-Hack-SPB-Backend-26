package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Value;

/** A visible physical object; null itemId means no confident inventory match. */
@Value
public class ObservedItem {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор вещи")
    private final UUID itemId;

    @Schema(example = "Белая керамическая кружка", description = "Описание предмета на фотографии")
    private final String photoDescription;
}
