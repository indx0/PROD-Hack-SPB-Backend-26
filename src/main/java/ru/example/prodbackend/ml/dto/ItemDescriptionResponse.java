package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import ru.example.prodbackend.item.entity.ItemFlag;

public record ItemDescriptionResponse(
        @Schema(description = "Предложенное название распознанного предмета", example = "Белая керамическая кружка", maxLength = 255) String name,
        @Schema(description = "Метки вещи: fragile — хрупкое, first_day — первый день, carry_personally — везти с собой")
        List<ItemFlag> flags) { }
