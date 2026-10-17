package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.example.prodbackend.item.entity.ItemFlag;
import java.util.List;

public record ItemDescriptionResponse(
        @Schema(example = "Белая керамическая кружка", maxLength = 255) String name,
        List<ItemFlag> flags) { }
