package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import java.util.List;

public record ItemsDescriptionResponse(
        @ArraySchema(minItems = 1, maxItems = 100, arraySchema = @Schema(
                description = "Распознанные предметы в порядке модели; названия могут повторяться"))
        List<ItemDescriptionResponse> items) { }
